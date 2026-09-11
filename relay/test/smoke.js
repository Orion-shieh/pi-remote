'use strict';

const assert = require('assert');
const crypto = require('crypto');
const https = require('https');
const path = require('path');
const { spawn } = require('child_process');
const WebSocket = require('ws');

const { buildFrame, readHeader, bytesToUuid, FRAME, HEADER_SIZE } = require('../protocol');

const ROOT = path.join(__dirname, '..');
const CONFIG = path.join(__dirname, 'test-config.json');
const AGENT_TOKEN = 'a'.repeat(64);
const DEVICE_TOKEN = 'b'.repeat(64);
const PORT = 18443;
const URL = `wss://127.0.0.1:${PORT}/relay`;

function sign(token, role, id, ts, nonce) {
  return crypto.createHmac('sha256', token).update(`${role}|${id}|${ts}|${nonce}`).digest('hex');
}

function helloMessage(role, id, token, overrides = {}) {
  const ts = Date.now();
  const nonce = crypto.randomBytes(16).toString('hex');
  return Object.assign(
    { t: 'hello', role, id, ts, nonce, sig: sign(token, role, id, ts, nonce) },
    overrides
  );
}

function connect(url = URL) {
  return new WebSocket(url, { rejectUnauthorized: false });
}

function open(ws) {
  return new Promise((resolve, reject) => {
    ws.once('open', resolve);
    ws.once('error', reject);
  });
}

function waitMessage(ws, predicate, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      ws.off('message', onMessage);
      reject(new Error('timeout waiting for message'));
    }, timeoutMs);
    function onMessage(data, isBinary) {
      const value = isBinary ? data : JSON.parse(data.toString('utf8'));
      if (!predicate || predicate(value)) {
        clearTimeout(timer);
        ws.off('message', onMessage);
        resolve(value);
      }
    }
    ws.on('message', onMessage);
  });
}

function waitClose(ws, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('timeout waiting for close')), timeoutMs);
    ws.once('close', (code, reason) => {
      clearTimeout(timer);
      resolve({ code, reason: reason.toString() });
    });
  });
}

function waitForHealth(attempts = 40) {
  return new Promise((resolve, reject) => {
    let left = attempts;
    const tick = () => {
      const req = https.get(
        { host: '127.0.0.1', port: PORT, path: '/healthz', rejectUnauthorized: false },
        (res) => {
          res.resume();
          resolve();
        }
      );
      req.on('error', () => {
        left -= 1;
        if (left <= 0) reject(new Error('relay did not become healthy'));
        else setTimeout(tick, 250);
      });
    };
    tick();
  });
}

async function main() {
  const server = spawn(process.execPath, [path.join(ROOT, 'server.js')], {
    env: Object.assign({}, process.env, { PI_RELAY_CONFIG: CONFIG }),
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  const relayLog = [];
  server.stdout.on('data', (d) => relayLog.push(d.toString()));
  server.stderr.on('data', (d) => relayLog.push(d.toString()));

  const cleanup = () => {
    try {
      server.kill();
    } catch {
      /* already gone */
    }
  };

  try {
    await waitForHealth();
    console.log('PASS  relay healthy on port ' + PORT);

    const agent = connect();
    await open(agent);
    agent.send(JSON.stringify(helloMessage('agent', 'test-agent', AGENT_TOKEN)));
    const agentHello = await waitMessage(agent, (m) => m.t === 'hello_ok');
    assert.strictEqual(agentHello.role, 'agent');
    console.log('PASS  agent handshake accepted');

    await new Promise((r) => setTimeout(r, 100));

    const client = connect();
    await open(client);
    client.send(JSON.stringify(helloMessage('client', 'phone-1', DEVICE_TOKEN)));
    const clientHello = await waitMessage(client, (m) => m.t === 'hello_ok');
    assert.strictEqual(clientHello.agentId, 'test-agent', 'client should bind to the single agent');
    console.log('PASS  client handshake accepted and bound to agent');

    const sid = '11111111-2222-3333-4444-555555555555';

    const sessionsAtClient = waitMessage(client, (m) => m.t === 'sessions');
    agent.send(JSON.stringify({ t: 'sessions', list: [{ sid, name: 'pi' }] }));
    assert.strictEqual((await sessionsAtClient).list[0].sid, sid);
    console.log('PASS  sessions list forwarded agent -> client');

    const createAtAgent = waitMessage(agent, (m) => m.t === 'create');
    client.send(JSON.stringify({ t: 'create', preset: 'pi' }));
    assert.strictEqual((await createAtAgent).preset, 'pi');
    console.log('PASS  control message routed client -> agent');

    const stdoutAtClient = waitMessage(client, (v) => Buffer.isBuffer(v));
    agent.send(buildFrame(FRAME.STDOUT, 1, sid, Buffer.from('hello pi\r\n')), { binary: true });
    const outFrame = await stdoutAtClient;
    const outHeader = readHeader(outFrame);
    assert.strictEqual(outHeader.type, FRAME.STDOUT);
    assert.strictEqual(outHeader.seq, 1);
    assert.strictEqual(bytesToUuid(outHeader.sid), sid);
    assert.strictEqual(outFrame.subarray(HEADER_SIZE).toString('utf8'), 'hello pi\r\n');
    console.log('PASS  binary stdout frame forwarded agent -> client (sid + seq intact)');

    const stdinAtAgent = waitMessage(agent, (v) => Buffer.isBuffer(v));
    client.send(buildFrame(FRAME.STDIN, 0, sid, Buffer.from('ls -la\r')), { binary: true });
    const inFrame = await stdinAtAgent;
    assert.strictEqual(bytesToUuid(readHeader(inFrame).sid), sid);
    assert.strictEqual(inFrame.subarray(HEADER_SIZE).toString('utf8'), 'ls -la\r');
    console.log('PASS  binary stdin frame routed client -> agent by session id');

    const bad = connect();
    await open(bad);
    bad.send(JSON.stringify(helloMessage('client', 'intruder', 'f'.repeat(64))));
    const closed = await waitClose(bad);
    assert.strictEqual(closed.code, 4403);
    console.log('PASS  bad token rejected with close code 4403');

    const replayed = connect();
    await open(replayed);
    const reused = helloMessage('client', 'phone-2', DEVICE_TOKEN);
    replayed.send(JSON.stringify(reused));
    await waitMessage(replayed, (m) => m.t === 'hello_ok');
    const replayed2 = connect();
    await open(replayed2);
    replayed2.send(JSON.stringify(reused));
    const replayedClosed = await waitClose(replayed2);
    assert.strictEqual(replayedClosed.code, 4403);
    console.log('PASS  replayed nonce rejected with close code 4403');

    client.close();
    agent.close();
    replayed.close();
    await new Promise((r) => setTimeout(r, 200));
    await waitForHealth();
    console.log('PASS  relay still healthy after all clients disconnected');

    console.log('\nAll smoke tests passed.');
  } catch (err) {
    console.error('\nFAILED:', err.message);
    console.error('--- relay log ---');
    console.error(relayLog.join(''));
    cleanup();
    process.exit(1);
  }

  cleanup();
  process.exit(0);
}

main();
