'use strict';

// End-to-end check for the session picker: create a pi-gui session, list
// historical pi conversations, switch to one, and verify the resumed
// conversation comes back through the attach snapshot.

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
    t: 'hello', role: 'client', id: 'picker-verify', ts, nonce,
    sig: crypto.createHmac('sha256', secrets.deviceToken)
      .update(`client|picker-verify|${ts}|${nonce}`).digest('hex'),
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
  console.log('[verify] created', sid, 'kind', created.kind);

  ws.send(JSON.stringify({ t: 'attach', sid, lastSeq: 0 }));
  await wait(() => control.some((m) => m.t === 'agent_event' && m.event.type === 'snapshot'), 30000, 'snapshot');

  // 1. list historical sessions
  ws.send(JSON.stringify({ t: 'list_pi_sessions', sid }));
  const listed = await wait(
    () => control.find((m) => m.t === 'agent_event' && m.event.type === 'pi_sessions'),
    30000,
    'pi_sessions',
  );
  const sessions = listed.event.sessions;
  console.log(`[verify] pi_sessions: ${sessions.length} conversation(s)`);
  assert(sessions.length >= 2, 'expected at least 2 historical conversations');
  for (const s of sessions.slice(0, 5)) {
    console.log(`  - ${s.id.slice(0, 8)} msgs=${s.messageCount} preview=${(s.preview || '').slice(0, 30)}`);
  }

  // 2. switch to a conversation that is NOT the current one
  const target = sessions.find((s) => s.messageCount > 4);
  assert(target, 'no conversation with enough messages to verify resume');
  console.log(`[verify] switching to ${target.id.slice(0, 8)} (${target.messageCount} messages)`);

  const before = control.length;
  ws.send(JSON.stringify({ t: 'agent_command', sid, command: { type: 'switch_session', sessionPath: target.file } }));
  const switched = await wait(
    () => control.slice(before).find((m) => m.t === 'agent_event' && m.event.type === 'response' && m.event.command === 'switch_session'),
    30000,
    'switch_session response',
  );
  assert(switched.event.success && !switched.event.data?.cancelled, `switch failed: ${JSON.stringify(switched.event)}`);
  console.log('[verify] switch_session ok');

  // 3. re-attach (what the phone does after a successful switch)
  ws.send(JSON.stringify({ t: 'attach', sid, lastSeq: 0 }));
  const snap2 = await wait(
    () => [...control].reverse().find((m) => m.t === 'agent_event' && m.event.type === 'snapshot'),
    30000,
    'second snapshot',
  );
  const msgs = snap2.event.entries.filter((e) => e.type === 'message');
  console.log(`[verify] resumed snapshot: entries=${snap2.event.entries.length} messages=${msgs.length}`);
  assert(snap2.event.state.sessionId === target.id, `state sessionId ${snap2.event.state.sessionId} != target ${target.id}`);
  assert(msgs.length >= 3, 'resumed conversation should carry its history');
  console.log(`[verify] state.sessionId matches target: ${snap2.event.state.sessionId.slice(0, 8)}`);

  ws.send(JSON.stringify({ t: 'kill', sid }));
  await sleep(1500);
  ws.close();
  console.log('\n[verify] all session-picker checks passed');
  process.exit(0);
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });

function assert(cond, msg) { if (!cond) throw new Error(msg); }
