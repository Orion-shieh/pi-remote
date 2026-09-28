'use strict';

// Verifies change_cwd: prompt in D:/Program/Pi_Agent, move the conversation to
// C:/Users/Orion, and check the resumed snapshot/history/-list all agree.

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const WebSocket = require(path.join(__dirname, '..', 'relay', 'node_modules', 'ws'));

const secrets = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '.secrets', 'relay.json'), 'utf8'));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const assert = (cond, msg) => { if (!cond) throw new Error(msg); };

(async () => {
  const ws = new WebSocket('wss://8.138.112.73:443/relay', { rejectUnauthorized: false });
  await new Promise((res, rej) => { ws.once('open', res); ws.once('error', rej); });
  const ts = Date.now(); const nonce = crypto.randomBytes(16).toString('hex');
  ws.send(JSON.stringify({
    t: 'hello', role: 'client', id: 'cwd-verify', ts, nonce,
    sig: crypto.createHmac('sha256', secrets.deviceToken)
      .update(`client|cwd-verify|${ts}|${nonce}`).digest('hex'),
  }));

  const control = [];
  ws.on('message', (d, isBinary) => { if (!isBinary) { try { control.push(JSON.parse(d.toString('utf8'))); } catch {} } });
  const wait = (pred, timeoutMs, label) => new Promise((resolve, reject) => {
    const deadline = Date.now() + timeoutMs;
    const t = setInterval(() => {
      const hit = pred();
      if (hit) { clearInterval(t); resolve(hit); } else if (Date.now() > deadline) { clearInterval(t); reject(new Error('timeout: ' + label)); }
    }, 150);
  });

  await sleep(1500);
  ws.send(JSON.stringify({ t: 'create', preset: 'pi-gui', cols: 100, rows: 30 }));
  const created = await wait(() => control.find((m) => m.t === 'created'), 20000, 'created');
  const sid = created.sid;
  console.log('[verify] created, cwd =', created.cwd);

  ws.send(JSON.stringify({ t: 'attach', sid, lastSeq: 0 }));
  await wait(() => control.some((m) => m.t === 'agent_event' && m.event.type === 'snapshot'), 30000, 'snapshot');
  console.log('[verify] attached; sending prompt');
  ws.send(JSON.stringify({ t: 'agent_command', sid, command: { type: 'prompt', message: '测试' } }));
  await wait(() => control.some((m) => m.t === 'agent_event' && m.event.type === 'agent_settled'), 120000, 'reply');

  const before = control.length;
  ws.send(JSON.stringify({ t: 'change_cwd', sid, cwd: 'C:/Users/Orion' }));
  await wait(() => control.slice(before).some((m) => m.t === 'agent_event' && m.event.type === 'snapshot'), 45000, 'post-change snapshot');
  const snap = control.slice(before).filter((m) => m.t === 'agent_event' && m.event.type === 'snapshot').pop();
  const file = snap.event.state.sessionFile;
  const msgs = snap.event.entries.filter((e) => e.type === 'message').length;
  const newFileOk = file.includes('--C--Users-Orion--');
  console.log('[verify] resumed file:', file);
  console.log('[verify] file in new project dir:', newFileOk, '| history messages:', msgs);
  assert(newFileOk, 'session file not in the new project dir');
  assert(msgs >= 2, 'conversation history not carried over');

  await sleep(2500);
  const entry = control.slice(before).reverse().find((m) => m.t === 'sessions').list.find((s) => s.sid === sid);
  console.log('[verify] sessions cwd:', entry.cwd, '| piName:', JSON.stringify(entry.piName));
  assert(String(entry.cwd).replace(/\\/g, '/').toLowerCase().includes('c:/users/orion'), 'sessions cwd not updated');

  ws.send(JSON.stringify({ t: 'kill', sid }));
  await sleep(1500);
  ws.close();
  try { fs.rmSync(file, { force: true }); } catch {}
  console.log('\n[verify] ALL CHECKS PASSED');
  process.exit(0);
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
