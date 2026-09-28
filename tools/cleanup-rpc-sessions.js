'use strict';

// Kills every running rpc (pi-gui) session on the deployed agent. Test
// debris accumulates quickly while iterating, and each session holds a
// process slot and a live pi runtime.
//
//   node tools/cleanup-rpc-sessions.js

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const WebSocket = require(path.join(__dirname, '..', 'relay', 'node_modules', 'ws'));

const secrets = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '.secrets', 'relay.json'), 'utf8'));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const ws = new WebSocket('wss://8.138.112.73:443/relay', { rejectUnauthorized: false });
  await new Promise((res, rej) => { ws.once('open', res); ws.once('error', rej); });
  const ts = Date.now();
  const nonce = crypto.randomBytes(16).toString('hex');
  ws.send(JSON.stringify({
    t: 'hello', role: 'client', id: 'cleanup', ts, nonce,
    sig: crypto.createHmac('sha256', secrets.deviceToken)
      .update(`client|cleanup|${ts}|${nonce}`).digest('hex'),
  }));

  const control = [];
  ws.on('message', (d) => {
    if (!Buffer.isBuffer(d)) {
      try { control.push(JSON.parse(d.toString('utf8'))); } catch {}
    }
  });

  const list = async () => {
    const before = control.length;
    ws.send(JSON.stringify({ t: 'list' }));
    await sleep(1500);
    return control.slice(before).flatMap((m) => (m.t === 'sessions' ? m.list : []));
  };

  await sleep(1500);
  for (let round = 0; round < 3; round++) {
    const sessions = await list();
    const running = sessions.filter((s) => s.kind === 'rpc' && s.running);
    console.log(`round ${round + 1}: running rpc sessions = ${running.length}`);
    if (running.length === 0) break;
    for (const s of running) {
      console.log(`  killing ${s.sid.slice(0, 8)} (${s.preset})`);
      ws.send(JSON.stringify({ t: 'kill', sid: s.sid }));
      await sleep(500);
    }
    await sleep(2500);
  }
  ws.close();
  process.exit(0);
})().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
