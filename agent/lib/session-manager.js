'use strict';

const fs = require('fs');
const path = require('path');

const { Session } = require('./session');
const { FRAME, HEADER_SIZE, buildFrame, readHeader, bytesToUuid } = require('./protocol');

const RETIRED_TTL_MS = 10 * 60 * 1000;
const PRUNE_INTERVAL_MS = 60 * 1000;

class SessionManager {
  constructor({ config, transport, shellPath, log }) {
    this.config = config;
    this.transport = transport;
    this.shellPath = shellPath;
    this.log = log;
    this.sessions = new Map();
    this._pruneTimer = setInterval(() => this._prune(), PRUNE_INTERVAL_MS);
  }

  get presets() {
    return this.config.presets || [];
  }

  describePresets() {
    return this.presets.map((p) => ({
      id: p.id,
      name: p.name,
      cwd: p.cwd,
      description: p.description || '',
    }));
  }

  list() {
    return [...this.sessions.values()].map((s) => s.describe());
  }

  sendJson(obj) {
    return this.transport.sendJson(obj);
  }

  sendBinary(buf) {
    return this.transport.sendBinary(buf);
  }

  sendSettings() {
    this.sendJson({ t: 'settings', presets: this.describePresets() });
  }

  sendSessions() {
    this.sendJson({ t: 'sessions', list: this.list() });
  }

  // Called by the relay client whenever the link comes back up: the client has
  // no idea what happened while it was away, so hand it the full picture.
  onRelayReady() {
    this.sendSettings();
    this.sendSessions();
  }

  handleControl(msg) {
    if (!msg || typeof msg.t !== 'string') return;

    switch (msg.t) {
      case 'list':
        this.sendSessions();
        break;
      // The relay tells us when a client shows up so we can push the current
      // state to it; anything broadcast before the client existed was dropped.
      case 'client_online':
        this.log('client online, resending state');
        this.sendSettings();
        this.sendSessions();
        break;
      case 'client_offline':
        this.log('client offline');
        for (const session of this.sessions.values()) session.attached = false;
        break;
      case 'create':
        this.create(msg);
        break;
      case 'attach':
        this.attach(msg);
        break;
      case 'detach':
        this.detach(msg);
        break;
      case 'kill':
        this.kill(msg);
        break;
      case 'resize':
        this.resize(msg);
        break;
      default:
        this.log(`unknown control message: ${msg.t}`);
    }
  }

  handleBinary(buf) {
    const header = readHeader(buf);
    if (!header) return;
    const session = this.sessions.get(bytesToUuid(header.sid));
    if (!session || !session.running) return;
    session.write(buf.subarray(HEADER_SIZE));
  }

  _resolveCwd(requested) {
    const candidates = [requested, this.config.defaultCwd, process.env.USERPROFILE];
    for (const candidate of candidates) {
      if (!candidate) continue;
      try {
        if (fs.statSync(candidate).isDirectory()) return candidate;
      } catch {
        // Try the next candidate.
      }
    }
    return process.cwd();
  }

  create(msg) {
    // Count only live sessions. Exited ones are kept for RETIRED_TTL_MS so a
    // reconnecting client can still read their final output, but if they counted
    // here then ending a session would never free a slot until it aged out, and
    // the user would hit the limit with nothing actually running.
    const limit = this.config.maxSessions || 8;
    const running = [...this.sessions.values()].filter((session) => session.running).length;
    if (running >= limit) {
      this.sendJson({
        t: 'error',
        code: 'too_many_sessions',
        message: `${running}/${limit}`,
      });
      return;
    }

    const preset = this.presets.find((p) => p.id === msg.preset) || this.presets[0];
    if (!preset) {
      this.sendJson({ t: 'error', code: 'no_preset' });
      return;
    }

    const cwd = this._resolveCwd(msg.cwd || preset.cwd);
    const shellPath = preset.shell && preset.shell !== 'auto' ? preset.shell : this.shellPath;

    let session;
    try {
      session = new Session({
        preset,
        cwd,
        cols: msg.cols || this.config.defaultCols || 120,
        rows: msg.rows || this.config.defaultRows || 30,
        shellPath,
        args: preset.args || ['-NoLogo'],
        initialInput: preset.initialInput,
        initialInputQuietMs: preset.initialInputQuietMs,
        initialInputMaxWaitMs: preset.initialInputMaxWaitMs,
        bufferLimits: this.config.buffers,
        envExtra: Object.assign({}, this.config.shellEnv, preset.env),
        pathPrepend: [].concat(this.config.pathPrepend || [], preset.pathPrepend || []),
        onOutput: (s, seq, chunk) => this._onOutput(s, seq, chunk),
        onExit: (s, code) => this._onExit(s, code),
        onLog: (m) => this.log(m),
      });
    } catch (err) {
      this.log(`failed to create session: ${err.message}`);
      this.sendJson({ t: 'error', code: 'spawn_failed', message: err.message });
      return;
    }

    this.sessions.set(session.sid, session);
    this.log(`session created ${session.sid} preset=${preset.id} cwd=${cwd} pid=${session.pty.pid}`);

    this.sendJson({
      t: 'created',
      sid: session.sid,
      name: session.name,
      preset: preset.id,
      cwd: session.cwd,
      cols: session.cols,
      rows: session.rows,
    });
    this.sendSessions();
  }

  attach(msg) {
    const session = this.sessions.get(msg.sid);
    if (!session) {
      this.sendJson({ t: 'error', code: 'no_session', sid: msg.sid });
      return;
    }

    // MVP keeps a single active session per client, so whatever was streaming
    // before stops now.
    for (const other of this.sessions.values()) {
      if (other !== session) other.attached = false;
    }

    const lastSeq = Number.isInteger(msg.lastSeq) && msg.lastSeq > 0 ? msg.lastSeq : 0;

    session.attached = false;
    const { chunks, gap } = session.collectReplay(lastSeq);

    if (gap) {
      this.log(`replay gap for ${session.sid}: client at seq ${lastSeq}, buffer starts at ${session.buffer.firstSeq}`);
      this.sendJson({ t: 'replay_gap', sid: session.sid });
    }

    for (const chunk of chunks) {
      this.sendBinary(buildFrame(FRAME.STDOUT, chunk.seq, session.sid, chunk.data));
    }

    // Flipping to live only after the replay frames are queued keeps ordering
    // correct: no event can interleave inside this synchronous block.
    session.attached = true;

    this.sendBinary(buildFrame(FRAME.REPLAY_DONE, session.seq, session.sid, Buffer.alloc(0)));
    this.log(`attached ${session.sid} replayed=${chunks.length} gap=${gap} at seq ${session.seq}`);
  }

  detach(msg) {
    const session = this.sessions.get(msg.sid);
    if (session) {
      session.attached = false;
      this.log(`detached ${session.sid}`);
    }
  }

  kill(msg) {
    const session = this.sessions.get(msg.sid);
    if (!session) return;
    session.attached = false;
    if (session.running) {
      session.kill();
    } else {
      this.sessions.delete(session.sid);
      this.sendSessions();
    }
  }

  resize(msg) {
    const session = this.sessions.get(msg.sid);
    if (!session) return;
    session.resize(Number(msg.cols) || 0, Number(msg.rows) || 0);
  }

  _onOutput(session, seq, chunk) {
    this.sendBinary(buildFrame(FRAME.STDOUT, seq, session.sid, chunk));
  }

  _onExit(session, exitCode) {
    this.log(`session exited ${session.sid} code=${exitCode}`);
    session.attached = false;
    session.exitedAt = Date.now();
    this.sendJson({ t: 'exit', sid: session.sid, code: exitCode });
    this.sendSessions();
  }

  _prune() {
    const now = Date.now();
    let removed = 0;
    for (const [sid, session] of this.sessions) {
      if (!session.running && session.exitedAt && now - session.exitedAt > RETIRED_TTL_MS) {
        this.sessions.delete(sid);
        removed += 1;
      }
    }
    if (removed > 0) {
      this.log(`pruned ${removed} exited session(s)`);
      this.sendSessions();
    }
  }

  shutdown() {
    clearInterval(this._pruneTimer);
    for (const session of this.sessions.values()) session.kill();
  }
}

module.exports = { SessionManager };
