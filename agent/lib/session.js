'use strict';

const crypto = require('crypto');
const { execFile } = require('child_process');
const pty = require('node-pty');

const { RingBuffer } = require('./ringbuffer');

// Builds the environment handed to the shell. When the agent runs as
// LocalSystem the inherited environment points at SYSTEM's profile and lacks
// the user's PATH, so pi would neither be found nor find its own settings.
// Normalising the PATH key also avoids emitting a duplicate `Path`/`PATH` pair
// into the child environment block.
function buildEnv(envExtra, pathPrepend) {
  const source = Object.assign({}, process.env, envExtra || {});
  const env = {};
  let existingPath = '';

  for (const [key, value] of Object.entries(source)) {
    if (key.toLowerCase() === 'path') {
      existingPath = existingPath || value;
      continue;
    }
    env[key] = value;
  }

  const parts = (pathPrepend || []).filter(Boolean);
  if (existingPath) parts.push(existingPath);
  if (parts.length > 0) env.Path = parts.join(';');
  env.TERM = 'xterm-256color';

  return env;
}

class Session {
  constructor(options) {
    const {
      preset,
      cwd,
      cols,
      rows,
      shellPath,
      args,
      initialInput,
      initialInputQuietMs,
      initialInputMaxWaitMs,
      bufferLimits,
      envExtra,
      pathPrepend,
      onOutput,
      onExit,
      onLog,
    } = options;

    this.sid = crypto.randomUUID();
    this.presetId = preset.id;
    this.name = preset.name;
    this.cwd = cwd;
    this.cols = cols;
    this.rows = rows;
    this.createdAt = Date.now();
    this.running = true;
    this.exitCode = null;
    this.attached = false;

    this.seq = 0;
    this.buffer = new RingBuffer(bufferLimits);

    this._onOutput = onOutput;
    this._onExit = onExit;
    this._onLog = onLog;
    this._initialInputTimer = null;
    this._killFallback = null;
    this._lastDataAt = 0;
    this._sawData = false;

    this.pty = pty.spawn(shellPath, args, {
      name: 'xterm-256color',
      cols,
      rows,
      cwd,
      env: buildEnv(envExtra, pathPrepend),
    });

    this.pty.onData((data) => this._handleData(data));
    this.pty.onExit(({ exitCode }) => {
      this.running = false;
      this.exitCode = exitCode;
      if (this._initialInputTimer) clearTimeout(this._initialInputTimer);
      if (this._killFallback) clearTimeout(this._killFallback);
      this._onExit(this, exitCode);
    });

    if (initialInput) {
      this._armInitialInput(initialInput, initialInputQuietMs, initialInputMaxWaitMs);
    }
  }

  // Sending a command into a freshly spawned shell on a fixed timer is racy:
  // the prompt can arrive later than any delay we pick, and input delivered
  // before the console host is reading gets dropped. Wait for the output to
  // settle instead, with a hard ceiling in case nothing is ever printed.
  _armInitialInput(text, quietMs, maxWaitMs) {
    const quiet = typeof quietMs === 'number' ? quietMs : 500;
    const ceiling = typeof maxWaitMs === 'number' ? maxWaitMs : 6000;
    const startedAt = Date.now();

    const check = () => {
      if (!this.running) {
        this._initialInputTimer = null;
        return;
      }
      const quietFor = Date.now() - this._lastDataAt;
      const settled = this._sawData && quietFor >= quiet;
      if (settled || Date.now() - startedAt >= ceiling) {
        this._initialInputTimer = null;
        this._logInitialInput(settled ? 'settled' : 'ceiling', Date.now() - startedAt);
        this.write(text);
        return;
      }
      this._initialInputTimer = setTimeout(check, 60);
    };

    this._initialInputTimer = setTimeout(check, 60);
  }

  _logInitialInput(reason, elapsedMs) {
    if (this._onLog) this._onLog(`initial input sending (${reason} after ${elapsedMs}ms)`);
  }

  _handleData(data) {
    if (!data) return;
    const chunk = Buffer.isBuffer(data) ? data : Buffer.from(data, 'utf8');
    if (chunk.length === 0) return;

    this.seq += 1;
    this._lastDataAt = Date.now();
    this._sawData = true;
    this.buffer.push(this.seq, chunk);

    if (this.attached) this._onOutput(this, this.seq, chunk);
  }

  // Synchronous by design: the replay snapshot and the switch to live output
  // happen in the same tick, so no output can be delivered out of order.
  collectReplay(lastSeq) {
    return this.buffer.since(lastSeq);
  }

  write(data) {
    if (!this.running) return;
    this.pty.write(data);
  }

  resize(cols, rows) {
    if (!this.running) return;
    if (cols < 1 || rows < 1) return;
    this.cols = cols;
    this.rows = rows;
    try {
      this.pty.resize(cols, rows);
    } catch {
      // ConPTY occasionally rejects a resize during teardown; not fatal.
    }
  }

  kill() {
    if (!this.running) return;

    // node-pty's ConPTY kill path forks a helper that calls AttachConsole to
    // enumerate console processes. That fails whenever this process has no
    // console attached (piped stdio, Windows service in session 0), and even
    // when it succeeds it only reaps the shell itself. taskkill works without
    // a console and takes the whole child tree with it.
    const pid = this.pty.pid;
    if (!pid) {
      this._killWithPty();
      return;
    }

    this._killFallback = setTimeout(() => {
      this._killFallback = null;
      if (!this.running) return;
      if (this._onLog) this._onLog('taskkill did not end the session in 5s, falling back to pty.kill()');
      this._killWithPty();
    }, 5000);

    // A non-zero exit from taskkill does not mean the session survived: killing
    // a deep process tree routinely reports partial failures for children that
    // had already exited. Treating that as a failure would make every kill also
    // trip node-pty's console-dependent helper, so only the timeout falls back.
    execFile('taskkill', ['/PID', String(pid), '/T', '/F'], { windowsHide: true }, (err) => {
      if (err && this.running) this._killError = err.message;
    });
  }

  _killWithPty() {
    if (this._onLog) this._onLog('using the node-pty kill() fallback');
    try {
      this.pty.kill();
    } catch {
      // Already gone.
    }
  }

  describe() {
    return {
      sid: this.sid,
      name: this.name,
      preset: this.presetId,
      cwd: this.cwd,
      running: this.running,
      exitCode: this.exitCode,
      pid: this.pty.pid,
      cols: this.cols,
      rows: this.rows,
      createdAt: this.createdAt,
      seq: this.seq,
      buffer: this.buffer.stats,
    };
  }
}

module.exports = { Session };
