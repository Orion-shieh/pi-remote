'use strict';

// Regression test for the session limit.
//
// Exited sessions are kept for a while so a reconnecting client can still read
// their final output. They must not count against maxSessions, otherwise ending
// sessions never frees a slot and the user hits "too many sessions" with nothing
// actually running.
//
//   node agent/test/session-limit.js

const assert = require('assert');
const path = require('path');

const { SessionManager } = require('../lib/session-manager');
const { resolveShell } = require('../lib/shell');

const LIMIT = 2;

const sent = [];
const transport = {
  sendJson: (obj) => sent.push(obj),
  sendBinary: () => {},
};

function take(type) {
  const index = sent.findIndex((m) => m.t === type);
  if (index === -1) return null;
  return sent.splice(index, 1)[0];
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function waitFor(predicate, label, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = predicate();
    if (value) return value;
    await sleep(100);
  }
  throw new Error(`timeout waiting for ${label}`);
}

async function main() {
  const manager = new SessionManager({
    config: {
      maxSessions: LIMIT,
      defaultCols: 80,
      defaultRows: 24,
      buffers: { maxLines: 200, maxBytes: 65536 },
      presets: [{ id: 'ps', name: 'PowerShell', shell: 'auto', args: ['-NoLogo', '-NoProfile'] }],
    },
    transport,
    shellPath: resolveShell(),
    log: () => {},
  });

  const create = () => {
    sent.length = 0;
    manager.create({ preset: 'ps' });
    return take('created') || take('error');
  };

  try {
    // Fill the limit.
    const first = create();
    assert.strictEqual(first.t, 'created', `1st should be created, got ${JSON.stringify(first)}`);
    const second = create();
    assert.strictEqual(second.t, 'created', `2nd should be created, got ${JSON.stringify(second)}`);
    console.log(`PASS  ${LIMIT} sessions created while under the limit`);

    // The next one must be refused, and the message should say why.
    const refused = create();
    assert.strictEqual(refused.t, 'error');
    assert.strictEqual(refused.code, 'too_many_sessions');
    assert.match(refused.message || '', /\d+\/\d+/, 'error should report running/limit');
    console.log(`PASS  the ${LIMIT + 1}th session is refused (${refused.message})`);

    // Ending them must free the slots immediately, not after the retire TTL.
    const running = [...manager.sessions.values()].filter((s) => s.running);
    assert.strictEqual(running.length, LIMIT, 'expected both sessions to be running');
    for (const session of running) manager.kill({ sid: session.sid });

    await waitFor(
      () => [...manager.sessions.values()].every((s) => !s.running),
      'all sessions to exit',
    );
    // They are still in the table, just not running - that is the whole point.
    assert.ok(
      manager.sessions.size >= LIMIT,
      'exited sessions should still be retained for replay',
    );
    console.log(`PASS  both sessions ended but are retained (${manager.sessions.size} entries)`);

    // Taking the slots back must work right away.
    const fourth = create();
    assert.strictEqual(
      fourth.t,
      'created',
      `should be able to create again immediately, got ${JSON.stringify(fourth)}`,
    );
    console.log('PASS  a new session can be created immediately after ending the old ones');

    console.log('\nsession-limit checks passed.');
  } finally {
    manager.shutdown();
    await sleep(500);
  }
  process.exit(0);
}

main().catch((err) => {
  console.error('\nFAILED:', err.message);
  process.exit(1);
});
