'use strict';

// Focused reproduction for the "output leaks while detached" flake.
//
// Runs many detach / verify / re-attach cycles in a single process so a rare
// race reproduces quickly instead of once per full M1 run.
//
//   node tools/detach-leak-test.js [cycles]

const assert = require('assert');
const crypto = require('crypto');
const https = require('https');
const path = require('path');
const { spawn } = require('child_process');
const WebSocket = require(path.join(__dirname, '..', 'relay', 'node_modules', 'ws'));

const { FRAME, HEADER_SIZE, buildFrame, readHeader } = require(
  path.join(__dirname, '..', 'relay', 'protocol')
);

const ROOT = path.join(__dirname, '..');
const secrets = require(path.join(ROOT, '.secrets', 'relay.json'));
const CYCLES = Number(process.argv[2] || 25);

function sign(token, role, id, ts, nonce) {
  return crypto.createHmac('sha256', token).update(`${role}|${id}|${ts}|${nonce}`).digest('hex');
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  const agent = spawn(process.execPath, [path.join(ROOT, 'agent', 'server.js')], {
    cwd: ROOT,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  agent.stdout.on('data', () => {});
  agent.stderr.on('data', (d) => process.stderr.write(`[agent] ${d}`));

  const host = new URL(secrets.endpoints[0]).hostname;
  const port = Number(new URL(secrets.endpoints[0]).port || 443);

  // The client can only be bound to an agent that has already registered.
  for (let i = 0; i < 60; i += 1) {
    const online = await new Promise((resolve) => {
      https
        .get({ host, port, path: '/healthz', rejectUnauthorized: false }, (res) => {
          let body = '';
          res.on('data', (c) => (body += c));
          res.on('end', () => {
            try {
              resolve(JSON.parse(body).agents.includes(secrets.agentId));
            } catch {
              resolve(false);
            }
          });
        })
        .on('error', () => resolve(false));
    });
    if (online) break;
    if (i === 59) throw new Error('agent never registered with the relay');
    await sleep(500);
  }

  const ws = new WebSocket(secrets.endpoints[0], { rejectUnauthorized: false });
  let text = '';
  let lastSeq = 0;
  const events = [];
  const waiters = [];
  const frames = [];

  ws.on('message', (data, isBinary) => {
    if (isBinary) {
      const header = readHeader(data);
      const payload = data.subarray(HEADER_SIZE);
      if (header.type === FRAME.STDOUT) {
        const s = payload.toString('utf8');
        text += s;
        lastSeq = Math.max(lastSeq, header.seq);
        frames.push({ seq: header.seq, len: payload.length, marker: s.includes('LEAKMARK') });
      } else if (header.type === FRAME.REPLAY_DONE) {
        events.push({ t: 'replay_done' });
      }
    } else {
      try {
        events.push(JSON.parse(data.toString('utf8')));
      } catch {
        /* ignore */
      }
    }
    for (const w of waiters.slice()) {
      const v = w.test();
      if (v) {
        waiters.splice(waiters.indexOf(w), 1);
        clearTimeout(w.timer);
        w.resolve(v);
      }
    }
  });

  function waitFor(test, timeoutMs, label) {
    return new Promise((resolve, reject) => {
      const immediate = test();
      if (immediate) return resolve(immediate);
      const timer = setTimeout(() => reject(new Error(`timeout: ${label}`)), timeoutMs);
      waiters.push({ test, resolve, timer });
    });
  }

  const waitControl = (t, ms = 20000) => waitFor(() => events.find((e) => e.t === t), ms, `control ${t}`);
  const waitReplay = (ms = 20000) => waitFor(() => events.find((e) => e.t === 'replay_done'), ms, 'replay_done');

  await new Promise((resolve, reject) => {
    ws.once('open', resolve);
    ws.once('error', reject);
  });

  const ts = Date.now();
  const nonce = crypto.randomBytes(16).toString('hex');
  ws.send(
    JSON.stringify({
      t: 'hello',
      role: 'client',
      id: 'leak-test',
      ts,
      nonce,
      sig: sign(secrets.deviceToken, 'client', 'leak-test', ts, nonce),
    })
  );
  await waitControl('hello_ok');

  ws.send(JSON.stringify({ t: 'create', preset: 'ps', cols: 100, rows: 30 }));
  const created = await waitControl('created');

  ws.send(JSON.stringify({ t: 'attach', sid: created.sid, lastSeq: 0 }));
  await waitReplay();
  await sleep(800);

  let leaks = 0;
  for (let cycle = 1; cycle <= CYCLES; cycle += 1) {
    const marker = `LEAKMARK${cycle}`;

    // detach, then use `list`->`sessions` as a FIFO barrier so we know the
    // agent has processed the detach before we send anything else.
    ws.send(JSON.stringify({ t: 'detach', sid: created.sid }));
    events.length = 0;
    ws.send(JSON.stringify({ t: 'list' }));
    await waitControl('sessions');
    const seqAtDetach = lastSeq;
    const textAtDetach = text.length;

    ws.send(buildFrame(FRAME.STDIN, 0, created.sid, Buffer.from(`echo ${marker}\r`)), { binary: true });
    await sleep(1200);

    const leaked = lastSeq !== seqAtDetach || text.length !== textAtDetach;
    if (leaked) {
      leaks += 1;
      console.error(`\n*** LEAK on cycle ${cycle} ***`);
      console.error(`  seqAtDetach=${seqAtDetach} lastSeq=${lastSeq}`);
      console.error(`  textAtDetach=${textAtDetach} textNow=${text.length}`);
      const recent = frames.slice(-6);
      console.error(`  recent frames: ${recent.map((f) => `seq=${f.seq},len=${f.len}`).join(' | ')}`);
      if (text.includes(marker)) {
        const idx = text.indexOf(marker);
        console.error(`  marker at char ${idx}: ${JSON.stringify(text.slice(Math.max(0, idx - 100), idx + 30))}`);
      } else {
        console.error(`  marker text did not appear; new text: ${JSON.stringify(text.slice(textAtDetach, textAtDetach + 200))}`);
      }
    }

    ws.send(JSON.stringify({ t: 'attach', sid: created.sid, lastSeq: seqAtDetach }));
    events.length = 0;
    text = '';
    await waitReplay();
    await sleep(400);
  }

  console.log(`\n${CYCLES} cycles, ${leaks} leak(s)`);
  ws.send(JSON.stringify({ t: 'kill', sid: created.sid }));
  await sleep(500);
  ws.close();
  agent.kill();
  process.exit(leaks === 0 ? 0 : 1);
}

main().catch((err) => {
  console.error('FAILED:', err.message);
  process.exit(1);
});
