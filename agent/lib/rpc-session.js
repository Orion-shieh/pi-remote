'use strict';

const crypto = require('crypto');

const { buildEnv } = require('./env');
const { taskKillTree } = require('./kill-tree');
const { PiRpc } = require('./pi-rpc');

const KILL_FALLBACK_MS = 5000;

/**
 * Structured sibling of Session: same lifecycle contract (sid, running,
 * attached, describe, kill, no-op resize) but the payload is pi's structured
 * RPC event stream instead of raw ConPTY bytes. The GUI renders from these
 * events; nothing here parses terminal text.
 */
class RpcSession {
  constructor(options) {
    const {
      preset,
      cwd,
      command,
      args,
      envExtra,
      pathPrepend,
      onEvent,
      onExit,
      onLog,
      sid,
    } = options;

    this.sid = sid || crypto.randomUUID();
    this.kind = 'rpc';
    this.presetId = preset.id;
    this.name = preset.name;
    this.cwd = cwd;
    this.createdAt = Date.now();
    this.running = true;
    this.exitCode = null;
    this.attached = false;

    // Identity of the pi conversation inside this session, refreshed from
    // get_state snapshots: pi names a session only when the user set one, so
    // the agent also keeps the first user message as a display fallback. The
    // session file path enables move-to-another-directory (copy + switch).
    this.piSessionId = null;
    this.piSessionName = null;
    this.piSessionFile = null;

    this._onEvent = onEvent;
    this._onExit = onExit;
    this._onLog = onLog;
    this._killFallback = null;

    this.rpc = new PiRpc({
      command,
      args,
      cwd,
      env: buildEnv(envExtra, pathPrepend),
      onEvent: (event) => {
        if (event) this._onEvent(this, event);
      },
      onExit: (code) => {
        this.running = false;
        this.exitCode = code;
        if (this._killFallback) {
          clearTimeout(this._killFallback);
          this._killFallback = null;
        }
        this._onExit(this, code);
      },
      onLog: (msg) => this._log(msg),
    });

    this._log(`pi rpc spawned pid=${this.rpc.pid} cwd=${cwd}`);
  }

  get pid() {
    return this.rpc.pid;
  }

  /** Agent-internal request/response (attach snapshots). */
  request(cmd) {
    return this.rpc.request(cmd);
  }

  /** Forwards a phone-issued command into pi's stdin. */
  writeCommand(cmd) {
    if (!this.running || !cmd || typeof cmd !== 'object') return;
    this.rpc.send(cmd);
  }

  /** Caches the pi conversation identity (id + display name + file) for describe(). */
  setPiIdentity(sessionId, displayName, sessionFile) {
    if (sessionId) this.piSessionId = sessionId;
    if (displayName) this.piSessionName = displayName;
    if (sessionFile) this.piSessionFile = sessionFile;
  }

  // pi owns its layout in RPC mode; there is no terminal grid to resize.
  resize() {}

  kill() {
    if (!this.running) return;

    const pid = this.pid;
    if (!pid) {
      this.rpc.kill();
      return;
    }

    this._killFallback = setTimeout(() => {
      this._killFallback = null;
      if (!this.running) return;
      this._log('taskkill did not end the rpc session in 5s, falling back to child.kill()');
      this.rpc.kill();
    }, KILL_FALLBACK_MS);

    taskKillTree(pid, (msg) => this._log(msg));
  }

  describe() {
    return {
      sid: this.sid,
      name: this.name,
      preset: this.presetId,
      kind: this.kind,
      piName: this.piSessionName || undefined,
      cwd: this.cwd,
      running: this.running,
      exitCode: this.exitCode,
      pid: this.pid,
      createdAt: this.createdAt,
    };
  }

  _log(msg) {
    if (this._onLog) this._onLog(msg);
  }
}

module.exports = { RpcSession };
