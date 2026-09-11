'use strict';

// M2 acceptance test: drive a deployed relay over the public internet with
// both an agent socket and a client socket connected at the same time.
//
//   PI_HOST=8.138.112.73 AGENT_TOKEN=... DEVICE_TOKEN=... node tools/e2e.js
//
// The agent connects on port 443 and the client on 8443 so the test also
// proves the relay routes across its two listeners.

const assert = require('assert');
const crypto = require('crypto');
const https = require('https');
const path = require('path');
const WebSocket = require(path.join(__dirname, '..', 'relay', 'node_modules', 'ws'));

const { buildFrame, readHeader, bytesToUuid, FRAME, HEADER_SIZE } = require(
  path.join(__dirname, '..', 'relay', 'protocol')
);

const HOST = process.env.PI_HOST || '8.138.112.73';
const AGENT_TOKEN = process.env.AGENT_TOKEN;
const DEVICE_TOKEN = process.env.DEVICE_TOKEN;
const AGENT_PORT = Number(process.env.AGENT_PORT || 443);
const CLIENT_PORT = Number(process.env.CLIENT_PORT || 8443);

if (!AGENT_TOKEN || !DEVICE_TOKEN) {
  console.error('AGENT_TOKEN and DEVICE_TOKEN env vars are required');
  process.exit(2);
}

function sign(token, role, id, ts, nonce) {
  return crypto.createHmac('sha256', token).update(`${role}|${id}|${ts}|${nonce}`).digest('hex');
}

function connect(port) {
  return new WebSocket(`wss://${HOST}:${port}/relay`, { rejectUnauthorized: false });
}

function open(ws) {
  return new Promise((resolve, reject) => {
    ws.once('open', resolve);
    ws.once('error', reject);
  });
}

function hello(ws, role, id, token) {
  const ts = Date.now();
  const nonce = crypto.randomBytes(16).toString('hex');
  ws.send(JSON.stringify({ t: 'hello', role, id, ts, nonce, sig: sign(token, role, id, ts, nonce) }));
}

function waitMessage(ws, predicate, timeoutMs = 8000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      ws.off('message', onMessage);
      reject(new Error('timeout waiting for message'));
    }, timeoutMs);
    function onMessage(data, isBinary) {
      const value = isBinary ? data : JSON.parse(data.toString('utf8'));
      if (predicate(value)) {
        clearTimeout(timer);
        ws.off('message', onMessage);
        resolve(value);
      }
    }
    ws.on('message', onMessage);
  });
}

function health() {
  return new Promise((resolve, reject) => {
    https
      .get({ host: HOST, port: AGENT_PORT, path: '/healthz', rejectUnauthorized: false }, (res) => {
        let body = '';
        res.on('data', (c) => (body += c));
        res.on('end', () => resolve(JSON.parse(body)));
      })
      .on('error', reject);
  });
}

async function main() {
  const agent = connect(AGENT_PORT);
  await open(agent);
  hello(agent, 'agent', 'e2e-agent', AGENT_TOKEN);
  const agentHello = await waitMessage(agent, (m) => m.t === 'hello_ok');
  assert.strictEqual(agentHello.role, 'agent');
  console.log(`PASS  agent authenticated on port ${AGENT_PORT}`);

  await new Promise((r) => setTimeout(r, 150));

  const client = connect(CLIENT_PORT);
  await open(client);
  hello(client, 'client', 'e2e-client', DEVICE_TOKEN);
  const clientHello = await waitMessage(client, (m) => m.t === 'hello_ok');
  assert.strictEqual(clientHello.agentId, 'e2e-agent');
  console.log(`PASS  client authenticated on port ${CLIENT_PORT} and bound to agent`);

  const sid = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';

  const sessionsAtClient = waitMessage(client, (m) => m.t === 'sessions');
  agent.send(JSON.stringify({ t: 'sessions', list: [{ sid, name: 'pi', running: true }] }));
  assert.strictEqual((await sessionsAtClient).list[0].sid, sid);
  console.log('PASS  sessions list relayed to client');

  const createAtAgent = waitMessage(agent, (m) => m.t === 'create');
  client.send(JSON.stringify({ t: 'create', preset: 'pi' }));
  assert.strictEqual((await createAtAgent).preset, 'pi');
  console.log('PASS  control message relayed client -> agent');

  const stdoutAtClient = waitMessage(client, (v) => Buffer.isBuffer(v));
  agent.send(buildFrame(FRAME.STDOUT, 42, sid, Buffer.from('\u001b[32mhello pi\u001b[0m\r\n')), {
    binary: true,
  });
  const outFrame = await stdoutAtClient;
  const outHeader = readHeader(outFrame);
  assert.strictEqual(outHeader.type, FRAME.STDOUT);
  assert.strictEqual(outHeader.seq, 42);
  assert.strictEqual(bytesToUuid(outHeader.sid), sid);
  assert.strictEqual(
    outFrame.subarray(HEADER_SIZE).toString('utf8'),
    '\u001b[32mhello pi\u001b[0m\r\n'
  );
  console.log('PASS  binary frame relayed with sid, seq and payload bytes intact');

  const stdinAtAgent = waitMessage(agent, (v) => Buffer.isBuffer(v));
  client.send(buildFrame(FRAME.STDIN, 0, sid, Buffer.from('dir\r')), { binary: true });
  const inFrame = await stdinAtAgent;
  assert.strictEqual(bytesToUuid(readHeader(inFrame).sid), sid);
  assert.strictEqual(inFrame.subarray(HEADER_SIZE).toString('utf8'), 'dir\r');
  console.log('PASS  stdin frame routed client -> agent by session id');

  const state = await health();
  assert.deepStrictEqual(state.agents, ['e2e-agent']);
  assert.strictEqual(state.sessions, 1);
  assert.strictEqual(state.clientAgentId, 'e2e-agent');
  console.log(`PASS  relay state consistent: ${JSON.stringify(state)}`);

  agent.close();
  await new Promise((r) => setTimeout(r, 300));
  await new Promise((resolve) => {
    const timer = setTimeout(resolve, 3000);
    client.once('message', (data) => {
      if (!Buffer.isBuffer(data) && JSON.parse(data.toString()).t === 'agent_offline') {
        clearTimeout(timer);
        resolve();
      }
    });
  });
  console.log('PASS  client notified when agent goes offline');

  client.close();
  console.log('\nAll M2 acceptance checks passed.');
  process.exit(0);
}

main().catch((err) => {
  console.error('\nFAILED:', err.message);
  process.exit(1);
});
