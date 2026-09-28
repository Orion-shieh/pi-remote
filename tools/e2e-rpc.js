'use strict';

// Production end-to-end check for RPC sessions: connects to the deployed relay
// as a client, creates a pi-gui session on the real PC agent, receives the
// structured attach snapshot, drives one tiny prompt and verifies the event
// stream, then cleans up.
//
//   node tools/e2e-rpc.js
//
// Note: the relay allows a single client, so this briefly kicks the phone's
// connection; it reconnects automatically once the script exits.

const assert = require('assert');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const WebSocket = require(path.join(__dirname, '..', 'relay', 'node_modules', 'ws'));

const secrets = JSON.parse(
  fs.readFileSync(path.join(__dirname, '..', '.secrets', 'relay.json'), 'utf8'),
);
const HOST = process.env.PI_HOST || '8.138.112.73';
const PORT = Number(process.env.PI_PORT || 443);
const DEVICE_TOKEN = process.env.DEVICE_TOKEN || secrets.deviceToken;
// Optional: pin this client to one agent (needed when several agents are
// online, e.g. a dev instance alongside the production service).
const AGENT_ID = process.env.PI_AGENT_ID || null;

if (!DEVICE_TOKEN) {
  console.error('device token not found (.secrets/relay.json or DEVICE_TOKEN env)');
  process.exit(2);
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function sign(token, role, id, ts, nonce) {
  return crypto.createHmac('sha256', token).update(`${role}|${id}|${ts}|${nonce}`).digest('hex');
}

async function main() {
  const ws = new WebSocket(`wss://${HOST}:${PORT}/relay`, { rejectUnauthorized: false });
  await new Promise((resolve, reject) => {
    ws.once('open', resolve);
    ws.once('error', reject);
  });

  const ts = Date.now();
  const nonce = crypto.randomBytes(16).toString('hex');
  const hello = {
    t: 'hello', role: 'client', id: 'e2e-rpc-client', ts, nonce,
    sig: sign(DEVICE_TOKEN, 'client', 'e2e-rpc-client', ts, nonce),
  };
  if (AGENT_ID) hello.agentId = AGENT_ID;
  ws.send(JSON.stringify(hello));

  const events = [];
  const control = [];
  let snapshot = null;

  ws.on('message', (data, isBinary) => {
    if (isBinary) return;
    let msg;
    try {
      msg = JSON.parse(data.toString('utf8'));
    } catch {
      return;
    }
    control.push(msg);
    if (msg.t === 'agent_event') {
      events.push(msg);
      if (msg.event && msg.event.type === 'snapshot') snapshot = msg.event;
    }
  });

  const wait = (predicate, timeoutMs, label) =>
    new Promise((resolve, reject) => {
      const deadline = Date.now() + timeoutMs;
      const check = () => {
        const found = predicate();
        if (found) {
          clearInterval(timer);
          resolve(found);
        } else if (Date.now() > deadline) {
          clearInterval(timer);
          reject(new Error(`timeout waiting for ${label}`));
        }
      };
      const timer = setInterval(check, 200);
      check();
    });

  const helloOk = await wait(() => control.find((m) => m.t === 'hello_ok'), 10000, 'hello_ok');
  console.log(`[e2e] authenticated, agent=${helloOk.agentId}`);
  assert(helloOk.agentId, 'no agent online');

  ws.send(JSON.stringify({ t: 'list' }));
  await wait(() => control.some((m) => m.t === 'sessions'), 10000, 'sessions');
  console.log('[e2e] session list received');

  ws.send(JSON.stringify({ t: 'create', preset: 'pi-gui', cols: 100, rows: 30 }));
  const created = await wait(() => control.find((m) => m.t === 'created'), 20000, 'created');
  assert.strictEqual(created.kind, 'rpc', `created kind: ${created.kind}`);
  const sid = created.sid;
  console.log(`[e2e] created rpc session ${sid}`);

  ws.send(JSON.stringify({ t: 'attach', sid, lastSeq: 0 }));
  await wait(() => snapshot, 30000, 'snapshot');
  assert(snapshot.state && snapshot.state.sessionId, 'snapshot.state.sessionId missing');
  console.log(
    `[e2e] snapshot ok: entries=${snapshot.entries.length} sessionId=${snapshot.state.sessionId}`,
  );
  assert(Array.isArray(snapshot.entries), 'snapshot.entries not an array');

  ws.send(JSON.stringify({
    t: 'agent_command',
    sid,
    command: { type: 'prompt', message: '请只回复两个字母：ok' },
  }));
  await wait(
    () =>
      events.some((m) => m.event.type === 'agent_settled') &&
      events.some(
        (m) => m.event.type === 'message_end' && m.event.message && m.event.message.role === 'assistant',
      ),
    120000,
    'assistant reply',
  );
  const assistantEnd = events
    .filter((m) => m.event.type === 'message_end' && m.event.message.role === 'assistant')
    .pop();
  const text = (assistantEnd.event.message.content || [])
    .filter((b) => b.type === 'text')
    .map((b) => b.text)
    .join('');
  console.log(`[e2e] assistant reply: ${JSON.stringify(text.slice(0, 60))}`);
  assert(/ok/i.test(text), 'reply does not contain ok');
  assert(
    events.some((m) => m.event.type === 'message_update'),
    'no streaming deltas observed',
  );

  ws.send(JSON.stringify({ t: 'agent_command', sid, command: { type: 'get_entries' } }));
  await wait(
    () =>
      events.filter((m) => m.event.type === 'response' && m.event.command === 'get_entries').length > 0,
    15000,
    'entries response',
  );
  const entriesRes = events
    .filter((m) => m.event.type === 'response' && m.event.command === 'get_entries')
    .pop();
  assert(entriesRes.event.success, 'get_entries failed');
  const messageEntries = entriesRes.event.data.entries.filter((e) => e.type === 'message');
  console.log(`[e2e] entries ok, message entries=${messageEntries.length}`);
  assert(messageEntries.length >= 2, 'expected user+assistant entries after prompt');

  ws.send(JSON.stringify({ t: 'kill', sid }));
  await wait(() => control.some((m) => m.t === 'exit' && m.sid === sid), 20000, 'exit');
  console.log('[e2e] session killed');

  ws.close();
  console.log('\n[e2e] all RPC production checks passed');
  process.exit(0);
}

main().catch((err) => {
  console.error(`\n[e2e] FAILED: ${err.message}`);
  process.exit(1);
});
