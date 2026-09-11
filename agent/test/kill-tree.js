'use strict';

// Verifies that killing a session reaps the whole child process tree, and that
// it works without a console attached (the situation when the agent runs as a
// Windows service). Run with piped stdio to reproduce the no-console case:
//
//   node agent/test/kill-tree.js

const assert = require('assert');
const { execFileSync } = require('child_process');
const path = require('path');

const { Session } = require(path.join(__dirname, '..', 'lib', 'session'));
const { resolveShell } = require(path.join(__dirname, '..', 'lib', 'shell'));

const SHELL = resolveShell();

function isAlive(pid) {
  try {
    const out = execFileSync('tasklist', ['/FI', `PID eq ${pid}`, '/NH', '/FO', 'CSV'], {
      encoding: 'utf8',
      windowsHide: true,
    });
    return out.includes(`"${pid}"`);
  } catch {
    return false;
  }
}

function waitForPid(session, timeoutMs = 25000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('no child pid observed')), timeoutMs);
    const original = session._onOutput;
    let text = '';
    session._onOutput = (s, seq, chunk) => {
      text += chunk.toString('utf8');
      const match = /CHILDPID=(\d+)/.exec(text);
      if (match) {
        clearTimeout(timer);
        session._onOutput = original;
        resolve(Number(match[1]));
        return;
      }
      original(s, seq, chunk);
    };
  });
}

async function main() {
  console.log(`shell: ${SHELL}`);
  console.log(`console attached to this process: ${process.stdout.isTTY ? 'yes' : 'no'}`);

  let resolveExit;
  const exited = new Promise((resolve) => {
    resolveExit = resolve;
  });

  const session = new Session({
    preset: { id: 'test', name: 'test' },
    cwd: process.env.USERPROFILE,
    cols: 100,
    rows: 30,
    shellPath: SHELL,
    args: ['-NoLogo', '-NoProfile'],
    bufferLimits: { maxLines: 500, maxBytes: 65536 },
    onOutput: () => {},
    onExit: (_s, code) => resolveExit(code),
  });

  await new Promise((r) => setTimeout(r, 800));

  // Session only emits live output while it is attached, which is what the
  // test needs in order to observe the child pid.
  session.attached = true;

  session.write(
    "Write-Host ('CHILDPID=' + (Start-Process pwsh -ArgumentList '-NoProfile','-Command','Start-Sleep 300' -PassThru).Id)\r"
  );

  const childPid = await waitForPid(session);
  assert.ok(isAlive(childPid), `grandchild ${childPid} should be running before kill`);
  console.log(`PASS  grandchild process ${childPid} is running under the session`);

  session.kill();
  const exitCode = await Promise.race([
    exited,
    new Promise((_r, rej) => setTimeout(() => rej(new Error('session did not exit after kill')), 15000)),
  ]);
  console.log(`PASS  session exited after kill (code ${exitCode})`);

  await new Promise((r) => setTimeout(r, 1500));
  assert.ok(!isAlive(childPid), `grandchild ${childPid} should be gone after kill`);
  console.log('PASS  grandchild process was reaped by the kill (no orphan)');

  console.log('\nkill-tree checks passed.');
  process.exit(0);
}

main().catch((err) => {
  console.error('\nFAILED:', err.message);
  process.exit(1);
});
