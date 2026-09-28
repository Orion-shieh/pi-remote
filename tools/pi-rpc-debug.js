'use strict';

// One-off: dump the raw JSON the app would receive for get_state,
// get_available_models, get_available_thinking_levels and a prompt, so the
// Android parser can be checked against reality.

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const WebSocket = require(path.join(__dirname, '..', 'relay', 'node_modules', 'ws'));

const secrets = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '.secrets', 'relay.json'), 'utf8'));
const HOST = '8.138.112.73';
const DEVICE_TOKEN = secrets.deviceToken;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function sign(token, role, id, ts, nonce) {
  return crypto.createHmac('sha256', token).update(`${role}|${id}|${ts}|${nonce}`).digest('hex');
}

async function main() {
  const ws = new WebSocket(`wss://${HOST}:443/relay`, { rejectUnauthorized: false });
  await new Promise((resolve, reject) => { ws.once('open', resolve); ws.once('error', reject); });
  const ts = Date.now();
  const nonce = crypto.randomBytes(16).toString('hex');
  ws.send(JSON.stringify({ t: 'hello', role: 'client', id: 'debug-dump', ts, nonce, sig: sign(DEVICE_TOKEN, 'client', 'debug-dump', ts, nonce) }));

  const control = [];
  ws.on('message', (data, isBinary) => {
    if (isBinary) return;
    try { control.push(JSON.parse(data.toString('utf8'))); } catch {}
  });
  const wait = (pred, timeoutMs, label) => new Promise((resolve, reject) => {
    const deadline = Date.now() + timeoutMs;
    const t = setInterval(() => {
      const hit = pred();
      if (hit) { clearInterval(t); resolve(hit); } else if (Date.now() > deadline) { clearInterval(t); reject(new Error('timeout: ' + label)); }
    }, 150);
  });

  await wait((() => { let ok = false; return () => ok || control.some(m => m.t === 'hello_ok') && (ok = true); })(), 10000, 'hello');
  ws.send(JSON.stringify({ t: 'create', preset: 'pi-gui', cols: 100, rows: 30 }));
  const created = await wait(() => control.find(m => m.t === 'created'), 20000, 'created');
  const sid = created.sid;
  console.log('created', sid, 'kind', created.kind);

  ws.send(JSON.stringify({ t: 'attach', sid, lastSeq: 0 }));
  await wait(() => control.some(m => m.t === 'agent_event' && m.event.type === 'snapshot'), 30000, 'snapshot');
  const snap = control.find(m => m.t === 'agent_event' && m.event.type === 'snapshot');
  console.log('\n=== snapshot.event.state ===\n', JSON.stringify(snap.event.state, null, 1).slice(0, 800));

  const dumpResponse = async (command, label, timeoutMs = 15000) => {
    const before = control.length;
    ws.send(JSON.stringify({ t: 'agent_command', sid, command }));
    await wait(() => control.slice(before).some(m => m.t === 'agent_event' && m.event.type === 'response' && m.event.command === command.type), timeoutMs, label);
    const res = control.slice(before).find(m => m.t === 'agent_event' && m.event.type === 'response' && m.event.command === command.type);
    console.log(`\n=== ${label} response (success=${res.event.success}) ===\n`, JSON.stringify(res.event.data ?? res.event.error, null, 1).slice(0, 1200));
    return res;
  };

  await dumpResponse({ type: 'get_state' }, 'get_state');
  await dumpResponse({ type: 'get_available_models' }, 'get_available_models');
  await dumpResponse({ type: 'get_available_thinking_levels' }, 'get_available_thinking_levels');
  const before = control.length;
  ws.send(JSON.stringify({ t: 'agent_command', sid, command: { type: 'prompt', message: '请只回复：ok' } }));
  await wait(() => control.slice(before).some(m => m.t === 'agent_event' && m.event.type === 'agent_settled'), 120000, 'reply');
  const events = control.slice(before).filter(m => m.t === 'agent_event').map(m => m.event);
  console.log('\n=== prompt event types ===\n', events.map(e => e.type + (e.assistantMessageEvent ? '/' + e.assistantMessageEvent.type : '')).join('\n'));
  const promptRes = events.find(e => e.type === 'response' && e.command === 'prompt');
  console.log('\nprompt response:', JSON.stringify(promptRes));
  const errors = events.filter(e => e.type === 'response' && e.success === false);
  console.log('error responses:', JSON.stringify(errors));

  // set_model response shape (switch to whatever the first catalogue entry is)
  const modelsRes = control.find(m => m.t === 'agent_event' && m.event.type === 'response' && m.event.command === 'get_available_models');
  const firstModel = modelsRes && modelsRes.event.data && modelsRes.event.data.models && modelsRes.event.data.models[0];
  if (firstModel) {
    await dumpResponse({ type: 'set_model', provider: firstModel.provider, modelId: firstModel.id }, 'set_model');
    await dumpResponse({ type: 'get_state' }, 'get_state after set_model');
  }

  // Cleanup: kill this session and any other running pi-gui sessions (tests only).
  ws.send(JSON.stringify({ t: 'list' }));
  await wait(() => control.some(m => m.t === 'sessions'), 10000, 'sessions list');
  const rpcSids = control.filter(m => m.t === 'sessions')
    .flatMap(m => m.list)
    .filter(s => s.kind === 'rpc' && s.running)
    .map(s => s.sid);
  console.log('\ncleanup: killing rpc sessions:', rpcSids.length);
  for (const killSid of rpcSids) {
    ws.send(JSON.stringify({ t: 'kill', sid: killSid }));
    await sleep(400);
  }
  await wait(() => control.filter(m => m.t === 'exit').length >= rpcSids.length, 20000, 'exits');
  ws.close();
  process.exit(0);
}

main().catch((err) => { console.error('FAILED:', err.message); process.exit(1); });
