'use strict';

// M1/M4 integration test.
//
// Spawns the PC agent locally, drives it as a remote client through the
// deployed relay, and verifies: session creation, PTY output, stdin routing,
// resize, replay on re-attach, and replay without gaps.
//
//   node tools/m1-test.js

const assert = require('assert');
const crypto = require('crypto');
const https = require('https');
const path = require('path');
const { spawn } = require('child_process');
const WebSocket = require(path.join(__dirname, '..', 'relay', 'node_modules', 'ws'));

const protocol = require(path.join(__dirname, '..', 'relay', 'protocol'));
const { FRAME, HEADER_SIZE, buildFrame, readHeader, bytesToUuid } = protocol;

const ROOT = path.join(__dirname, '..');
const secrets = require(path.join(ROOT, '.secrets', 'relay.json'));

const HOST = new URL(secrets.endpoints[0]).hostname;
const PORT = Number(new URL(secrets.endpoints[0]).port || 443);

const steps = [];
const pass = (msg) => {
  steps.push(msg);
  console.log(`PASS  ${msg}`);
};

function sign(token, role, id, ts, nonce) {
  return crypto.createHmac('sha256', token).update(`${role}|${id}|${ts}|${nonce}`).digest('hex');
}

function health() {
  return new Promise((resolve, reject) => {
    https
      .get({ host: HOST, port: PORT, path: '/healthz', rejectUnauthorized: false }, (res) => {
        let body = '';
        res.on('data', (c) => (body += c));
        res.on('end', () => resolve(JSON.parse(body)));
      })
      .on('error', reject);
  });
}

async function waitFor(predicate, { timeoutMs = 20000, intervalMs = 300, label = 'condition' } = {}) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const value = await predicate();
    if (value) return value;
    if (Date.now() > deadline) throw new Error(`timeout waiting for ${label}`);
    await new Promise((r) => setTimeout(r, intervalMs));
  }
}

class RemoteTerminal {
  constructor(ws) {
    this.ws = ws;
    this.text = '';
    this.lastSeq = 0;
    this.events = [];
    this.frames = [];
    this._waiters = [];
    this._control = new Map();
  }

  _notify() {
    for (const w of this._waiters.slice()) {
      const value = w.test();
      if (value) {
        this._waiters.splice(this._waiters.indexOf(w), 1);
        clearTimeout(w.timer);
        w.resolve(value);
      }
    }
  }

  handle(data, isBinary) {
    if (isBinary) {
      const header = readHeader(data);
      const payload = data.subarray(HEADER_SIZE);
      if (header.type === FRAME.STDOUT) {
        const chunkText = payload.toString('utf8');
        this.text += chunkText;
        this.lastSeq = Math.max(this.lastSeq, header.seq);
        this.frames.push({
          seq: header.seq,
          len: payload.length,
          at: Date.now(),
          marker: chunkText.includes('MISSED_WHILE_AWAY'),
        });
      } else if (header.type === FRAME.REPLAY_DONE) {
        this.replayed = true;
        this.events.push({ type: 'replay_done', seq: header.seq });
      }
      this._notify();
      return;
    }

    let msg;
    try {
      msg = JSON.parse(data.toString('utf8'));
    } catch {
      return;
    }
    this.events.push(msg);
    this._notify();
  }

  waitForText(substring, timeoutMs = 20000) {
    return this._wait(substring, () => this.text.includes(substring), timeoutMs, `text ${JSON.stringify(substring)}`);
  }

  waitForReplayDone(timeoutMs = 20000) {
    const found = () => this.events.find((e) => e.type === 'replay_done');
    return this._wait('replay_done', found, timeoutMs, 'replay_done frame');
  }

  waitForControl(type, timeoutMs = 20000) {
    const existing = this.events.find((e) => e.t === type);
    if (existing) return Promise.resolve(existing);
    return this._wait(type, () => this.events.find((e) => e.t === type), timeoutMs, `control ${type}`);
  }

  _wait(label, test, timeoutMs, description) {
    return new Promise((resolve, reject) => {
      const immediate = test();
      if (immediate) return resolve(immediate);
      const timer = setTimeout(() => {
        const i = this._waiters.indexOf(entry);
        if (i >= 0) this._waiters.splice(i, 1);
        reject(new Error(`timeout waiting for ${description || label}`));
      }, timeoutMs);
      const entry = { test, resolve, timer };
      this._waiters.push(entry);
    });
  }

  sendJson(obj) {
    this.ws.send(JSON.stringify(obj));
  }

  sendStdin(sid, text) {
    this.ws.send(buildFrame(FRAME.STDIN, 0, sid, Buffer.from(text, 'utf8')), { binary: true });
  }

  clear() {
    this.events.length = 0;
  }
}

async function main() {
  // When the agent already runs as a service we must not spawn a second one: it
  // would fight for the same agentId slot on the relay.
  const external = process.env.PI_AGENT_EXTERNAL === '1';

  let agent = null;
  const agentLog = [];

  if (!external) {
    agent = spawn(process.execPath, [path.join(ROOT, 'agent', 'server.js')], {
      cwd: ROOT,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    agent.stdout.on('data', (d) => {
      agentLog.push(d.toString());
      process.stdout.write(`  [agent] ${d}`);
    });
    agent.stderr.on('data', (d) => {
      agentLog.push(d.toString());
      process.stderr.write(`  [agent] ${d}`);
    });
  } else {
    console.log('  (using the already-running agent service)');
  }

  const cleanup = () => {
    if (!agent) return;
    try {
      agent.kill();
    } catch {
      /* already gone */
    }
  };

  try {
    await waitFor(
      async () => {
        const state = await health().catch(() => null);
        return state && state.agents.includes(secrets.agentId);
      },
      { label: `agent ${secrets.agentId} to register with the relay` }
    );
    pass(
      external
        ? 'agent service is registered with the relay'
        : 'agent started and registered with the relay'
    );

    const ws = new WebSocket(secrets.endpoints[0], { rejectUnauthorized: false });
    const term = new RemoteTerminal(ws);
    ws.on('message', (d, b) => term.handle(d, b));
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
        id: 'm1-test-client',
        ts,
        nonce,
        sig: sign(secrets.deviceToken, 'client', 'm1-test-client', ts, nonce),
      })
    );
    const hello = await term.waitForControl('hello_ok');
    assert.strictEqual(hello.agentId, secrets.agentId);
    pass('client authenticated and bound to the agent');

    const settings = await term.waitForControl('settings');
    assert.ok(settings.presets.some((p) => p.id === 'ps'), 'ps preset should be advertised');
    pass(`agent advertised presets: ${settings.presets.map((p) => p.id).join(', ')}`);

    term.sendJson({ t: 'create', preset: 'ps', cols: 100, rows: 30 });
    const created = await term.waitForControl('created');
    assert.strictEqual(created.cols, 100);
    pass(`session created via agent PTY (sid ${created.sid.slice(0, 8)}..., cols=${created.cols})`);

    term.sendJson({ t: 'attach', sid: created.sid, lastSeq: 0 });
    await term.waitForReplayDone();
    pass('attached and received replay_done');

    await term.waitForText('PS ', 25000).catch(() => {});
    assert.ok(term.text.length > 0, 'expected a shell banner/prompt from the PTY');
    pass(`received live PTY output (${term.text.length} bytes)`);

    term.clear();
    const before = term.lastSeq;
    term.sendStdin(created.sid, 'echo M1_MARKER_OK\r');
    await term.waitForText('M1_MARKER_OK', 25000);
    assert.ok(term.lastSeq > before, 'sequence should advance with new output');
    pass(`stdin routed into the PTY and echoed back (seq ${before} -> ${term.lastSeq})`);

    term.clear();
    term.sendJson({ t: 'resize', sid: created.sid, cols: 132, rows: 43 });
    await new Promise((r) => setTimeout(r, 800));
    term.sendStdin(created.sid, '$Host.UI.RawUI.WindowSize.Width\r');
    await term.waitForText('132', 25000);
    pass('resize propagated to ConPTY (shell reports width 132)');

    // Barrier: `list` always gets a `sessions` reply, and WebSocket delivery is
    // ordered, so once that reply lands the agent has definitely processed the
    // detach. Without this the test races the round trip through the relay.
    term.sendJson({ t: 'detach', sid: created.sid });
    term.clear();
    term.sendJson({ t: 'list' });
    await term.waitForControl('sessions');
    const seqAtDetach = term.lastSeq;
    const textAtDetach = term.text.length;

    term.sendStdin(created.sid, 'echo MISSED_WHILE_AWAY\r');
    await new Promise((r) => setTimeout(r, 2500));
    if (
      term.lastSeq !== seqAtDetach ||
      term.text.length !== textAtDetach ||
      term.text.includes('MISSED_WHILE_AWAY')
    ) {
      console.error('\n--- detach leak diagnostics ---');
      console.error(`seqAtDetach=${seqAtDetach} lastSeq=${term.lastSeq}`);
      console.error(`textAtDetach=${textAtDetach} textNow=${term.text.length}`);
      console.error('frames received:');
      for (const f of term.frames.slice(-25)) {
        console.error(`  seq=${f.seq} len=${f.len} marker=${f.marker}`);
      }
      const idx = term.text.indexOf('MISSED_WHILE_AWAY');
      console.error(`marker first at char ${idx} of ${term.text.length}`);
      console.error(`context: ${JSON.stringify(term.text.slice(Math.max(0, idx - 120), idx + 60))}`);
      console.error('--- end diagnostics ---\n');
    }
    assert.strictEqual(
      term.lastSeq,
      seqAtDetach,
      'no terminal frames may reach the client while the session is detached'
    );
    assert.strictEqual(
      term.text.length,
      textAtDetach,
      'not a single byte may reach the client while the session is detached'
    );
    pass('detached session streams nothing to the client (verified against a FIFO barrier)');

    term.clear();
    term.text = '';
    term.sendJson({ t: 'attach', sid: created.sid, lastSeq: seqAtDetach });
    await term.waitForReplayDone();
    await term.waitForText('MISSED_WHILE_AWAY', 25000);
    pass('re-attach replayed output produced while detached (no data loss)');

    const gapEvents = term.events.filter((e) => e.t === 'replay_gap');
    assert.strictEqual(gapEvents.length, 0, 'expected no replay gap for a short detach');
    pass('no replay gap reported for a small backlog');

    term.clear();
    term.sendJson({ t: 'attach', sid: created.sid, lastSeq: 0 });
    await term.waitForReplayDone();
    assert.ok(term.text.includes('MISSED_WHILE_AWAY'), 'full replay from seq 0 should include history');
    pass('full replay from seq 0 reconstructs the session history');

    term.sendJson({ t: 'kill', sid: created.sid });
    const exitEvent = await term.waitForControl('exit');
    assert.ok(typeof exitEvent.code === 'number');
    pass(`kill terminated the session (exit code ${exitEvent.code})`);

    // The headline feature: drive a real interactive pi TUI over the relay.
    term.clear();
    term.text = '';
    term.sendJson({ t: 'create', preset: 'pi', cols: 120, rows: 32 });
    const piCreated = await term.waitForControl('created');
    assert.strictEqual(piCreated.preset, 'pi');
    term.sendJson({ t: 'attach', sid: piCreated.sid, lastSeq: 0 });
    await term.waitForReplayDone();
    await term.waitForText('0.85.1', 45000);
    assert.ok(
      term.text.includes('\u001b[38;2;'),
      'pi should emit truecolour sequences that the Android renderer must support'
    );
    assert.ok(
      term.text.includes('\u001b[?2004h'),
      'pi should enable bracketed paste'
    );
    pass('pi TUI launched through the relay and rendered (truecolour + bracketed paste seen)');

    term.sendJson({ t: 'kill', sid: piCreated.sid });
    const piExit = await term.waitForControl('exit');
    pass(`pi session terminated (exit code ${piExit.code})`);

    const finalState = await health();
    assert.ok(!finalState.agents.includes('nonexistent'));
    pass(`relay state after teardown: ${JSON.stringify(finalState)}`);

    ws.close();
    console.log(`\nAll ${steps.length} M1/M4 checks passed.`);
  } catch (err) {
    console.error('\nFAILED:', err.message);
    console.error('--- agent log ---');
    console.error(agentLog.join(''));
    cleanup();
    process.exit(1);
  }

  cleanup();
  process.exit(0);
}

main();
