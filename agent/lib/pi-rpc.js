'use strict';

const path = require('path');
const { StringDecoder } = require('node:string_decoder');
const { execFile, spawn } = require('child_process');

// pi's RPC mode frames stdout as strict JSONL: records are LF-only and must be
// split on \n alone. Node's readline additionally splits on Unicode separators
// that are legal inside JSON strings, so lines are split manually — the same
// approach pi's own modes/rpc/jsonl.js documents for its clients.
function attachJsonlReader(stream, onLine) {
  const decoder = new StringDecoder('utf8');
  let buffer = '';
  let done = false;

  const emit = (line) => {
    onLine(line.endsWith('\r') ? line.slice(0, -1) : line);
  };

  const onData = (chunk) => {
    buffer += typeof chunk === 'string' ? chunk : decoder.write(chunk);
    for (;;) {
      const newlineIndex = buffer.indexOf('\n');
      if (newlineIndex === -1) return;
      emit(buffer.slice(0, newlineIndex));
      buffer = buffer.slice(newlineIndex + 1);
    }
  };

  const onEnd = () => {
    if (done) return;
    done = true;
    buffer += decoder.end();
    if (buffer.length > 0) emit(buffer);
  };

  stream.on('data', onData);
  stream.on('end', onEnd);
  return () => {
    done = true;
    stream.off('data', onData);
    stream.off('end', onEnd);
  };
}

/**
 * One `pi --mode rpc` child process.
 *
 * Commands go in as JSON lines on stdin; agent session events and command
 * responses come out as JSON lines on stdout. pi shuts itself down when our
 * stdin side closes, so the pipe is kept open for the child's whole lifetime.
 *
 * Responses correlate by the `id` we attach to commands sent via [request];
 * those are resolved locally. Responses to commands sent via [send] (phone
 * originated) carry the caller's own id, fall through to [onEvent], and are
 * forwarded to the phone like any other event.
 */
class PiRpc {
  constructor({ command, args, cwd, env, onEvent, onExit, onLog }) {
    this.onEvent = onEvent;
    this.onExit = onExit;
    this.onLog = onLog;
    this.pending = new Map();
    this.seq = 0;
    this.exited = false;

    this.child = spawn(command, args, {
      cwd,
      env,
      windowsHide: true,
      stdio: ['pipe', 'pipe', 'pipe'],
    });

    this.detachReader = attachJsonlReader(this.child.stdout, (line) => this._onLine(line));

    this.child.stderr.on('data', (chunk) => {
      const text = chunk.toString().trim();
      if (text) this._log(`pi stderr: ${text.split('\n')[0]}`);
    });

    this.child.on('error', (err) => {
      this._log(`spawn error: ${err.message}`);
    });

    this.child.on('exit', (code) => {
      this.exited = true;
      for (const resolve of this.pending.values()) resolve(null);
      this.pending.clear();
      if (this.onExit) this.onExit(code);
    });
  }

  get pid() {
    return this.child.pid;
  }

  _onLine(line) {
    if (!line) return;
    let msg;
    try {
      msg = JSON.parse(line);
    } catch {
      this._log(`unparseable pi line: ${line.slice(0, 160)}`);
      return;
    }
    if (msg && msg.type === 'response' && typeof msg.id === 'string' && this.pending.has(msg.id)) {
      const resolve = this.pending.get(msg.id);
      this.pending.delete(msg.id);
      resolve(msg);
      return;
    }
    if (this.onEvent) this.onEvent(msg);
  }

  /**
   * Sends a command and resolves with its response (null on timeout or exit).
   * Used for agent-internal bookkeeping such as attach snapshots.
   */
  request(cmd, timeoutMs = 15000) {
    return new Promise((resolve) => {
      if (this.exited) {
        resolve(null);
        return;
      }
      const id = `agent-${++this.seq}`;
      const timer = setTimeout(() => {
        this.pending.delete(id);
        resolve(null);
      }, timeoutMs);
      this.pending.set(id, (response) => {
        clearTimeout(timer);
        resolve(response);
      });
      this._write(Object.assign({}, cmd, { id }));
    });
  }

  /** Sends a command on behalf of the phone; its response surfaces via onEvent. */
  send(cmd) {
    if (!this.exited) this._write(cmd);
  }

  _write(obj) {
    try {
      this.child.stdin.write(JSON.stringify(obj) + '\n');
    } catch (err) {
      this._log(`stdin write failed: ${err.message}`);
    }
  }

  kill() {
    try {
      this.child.kill();
    } catch {
      // Already gone.
    }
  }

  _log(msg) {
    if (this.onLog) this.onLog(msg);
  }
}

// The JSONL protocol evolves with pi, so the agent logs which runtime it is
// bridging; a mismatch after a pi upgrade is the first thing to check.
function probePiVersion(piDir) {
  const nodeExe = path.join(piDir, 'node.exe');
  const script = path.join(
    piDir,
    'node_modules',
    '@earendil-works',
    'pi-coding-agent',
    'dist',
    'bundle',
    'cli.js',
  );
  return new Promise((resolve, reject) => {
    execFile(nodeExe, [script, '--version'], { timeout: 20000, windowsHide: true }, (err, stdout) => {
      if (err) reject(err);
      else resolve(stdout.trim());
    });
  });
}

module.exports = { PiRpc, attachJsonlReader, probePiVersion };
