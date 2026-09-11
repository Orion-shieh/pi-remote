'use strict';

// End-to-end probe against a deployed relay.
//
//   node tools/probe.js <token> <id> [role] [port]
//
// Defaults to role=agent, port=443. Exits non-zero on any failure.

const crypto = require('crypto');
const https = require('https');
const path = require('path');
const WebSocket = require(path.join(__dirname, '..', 'relay', 'node_modules', 'ws'));

const HOST = process.env.PI_HOST || '8.138.112.73';
const token = process.argv[2];
const id = process.argv[3] || 'probe';
const role = process.argv[4] || 'agent';
const port = Number(process.argv[5] || 443);

if (!token) {
  console.error('usage: node tools/probe.js <token> <id> [role] [port]');
  process.exit(2);
}

function sign(secret, r, i, ts, nonce) {
  return crypto.createHmac('sha256', secret).update(`${r}|${i}|${ts}|${nonce}`).digest('hex');
}

function health() {
  return new Promise((resolve, reject) => {
    https
      .get({ host: HOST, port, path: '/healthz', rejectUnauthorized: false }, (res) => {
        let body = '';
        res.on('data', (c) => (body += c));
        res.on('end', () => resolve({ status: res.statusCode, body }));
      })
      .on('error', reject);
  });
}

function main() {
  const started = Date.now();
  health()
    .then((res) => {
      console.log(`health  : HTTP ${res.status} -> ${res.body}`);
      const ws = new WebSocket(`wss://${HOST}:${port}/relay`, { rejectUnauthorized: false });

      const timer = setTimeout(() => {
        console.error('FAIL    : handshake timed out after 10s');
        process.exit(1);
      }, 10000);

      ws.on('open', () => {
        console.log(`connect : wss://${HOST}:${port}/relay  (${Date.now() - started}ms)`);
        const ts = Date.now();
        const nonce = crypto.randomBytes(16).toString('hex');
        ws.send(JSON.stringify({ t: 'hello', role, id, ts, nonce, sig: sign(token, role, id, ts, nonce) }));
      });

      ws.on('message', (data, isBinary) => {
        if (isBinary) return;
        const msg = JSON.parse(data.toString('utf8'));
        if (msg.t === 'hello_ok') {
          clearTimeout(timer);
          console.log(`auth    : OK  role=${msg.role} id=${msg.id} agentId=${msg.agentId || '-'}`);
          console.log(`agents  : ${JSON.stringify(msg.agents)}`);
          console.log('PASS');
          ws.close();
          process.exit(0);
        }
      });

      ws.on('close', (code, reason) => {
        clearTimeout(timer);
        if (code === 4403 || code === 4401) {
          console.error(`FAIL    : rejected by relay (code ${code} ${reason})`);
          process.exit(1);
        }
      });

      ws.on('error', (err) => {
        clearTimeout(timer);
        console.error(`FAIL    : ${err.message}`);
        process.exit(1);
      });
    })
    .catch((err) => {
      console.error(`FAIL    : health check failed -> ${err.message}`);
      process.exit(1);
    });
}

main();
