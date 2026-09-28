'use strict';

// One-off: dump get_commands from a live pi-gui session to see which slash
// commands pi actually accepts via prompt in RPC mode.

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const WebSocket = require(path.join(__dirname, '..', 'relay', 'node_modules', 'ws'));

const secrets = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '.secrets', 'relay.json'), 'utf8'));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const ws = new WebSocket('wss://8.138.112.73:443/relay', { rejectUnauthorized: false });
  await new Promise((res, rej) => { ws.once('open', res); ws.once('error', rej); });
  const ts = Date.now(); const nonce = crypto.randomBytes(16).toString('hex');
  ws.send(JSON.stringify({
    t: 'hello', role: 'client', id: 'cmd-dump', ts, nonce,
    sig: crypto.createHmac('sha256', secrets.deviceToken).update(`client|cmd-dump|${ts}|${nonce}`).digest('hex'),
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

  await sleep(1200);
  ws.send(JSON.stringify({ t: 'create', preset: 'pi-gui', cols: 100, rows: 30 }));
  const created = await wait(() => control.find((m) => m.t === 'created'), 20000, 'created');
  const sid = created.sid;
  console.log('created', sid);

  // Events are gated until the session is attached.
  ws.send(JSON.stringify({ t: 'attach', sid, lastSeq: 0 }));
  await wait(() => control.some((m) => m.t === 'agent_event' && m.event.type === 'snapshot'), 30000, 'snapshot');
  console.log('snapshot ok');

  const sendCmd = async (command) => {
    const before = control.length;
    ws.send(JSON.stringify({ t: 'agent_command', sid, command }));
    const res = await wait(
      () => control.slice(before).find((m) => m.t === 'agent_event' && m.event.type === 'response' && m.event.command === command.type),
      20000,
      command.type,
    );
    return res.event;
  };

  const cmds = await sendCmd({ type: 'get_commands' });
  console.log('\n=== get_commands ===');
  for (const c of cmds.data?.commands ?? []) {
    console.log(`/${c.name}  [${c.source}]  ${c.description ?? ''}`.slice(0, 110));
  }
  if ((cmds.data?.commands ?? []).length === 0) console.log('(empty)');

  ws.send(JSON.stringify({ t: 'kill', sid }));
  await sleep(1500);
  ws.close();
  process.exit(0);
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
