'use strict';

const fs = require('fs');
const path = require('path');

const { Session } = require('./session');
const { RpcSession } = require('./rpc-session');
const { listPiSessions, resolveSessionsDir, readSessionSummary, copySessionForCwd, listDirectories } = require('./pi-sessions');
const { FRAME, HEADER_SIZE, buildFrame, readHeader, bytesToUuid } = require('./protocol');

const RETIRED_TTL_MS = 10 * 60 * 1000;
const PRUNE_INTERVAL_MS = 60 * 1000;

function normalizeCwdLike(value) {
  return String(value || '').replace(/\//g, '\\').replace(/\\+$/, '').toLowerCase();
}

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

  onRelayReady() {
    this.sendSettings();
    this.sendSessions();
  }

  handleControl(msg) {
    if (!msg || typeof msg.t !== 'string') return;

    switch (msg.t) {
    case 'delete_model': {
      try {
        const os = require('os');
        const fs = require('fs');
        const p = require('path');
        const candidateDirs = [
          'C:\\Users\\Orion\\.pi\\agent',
          'C:\\Users\\Orion\\.pi',
          p.join(os.homedir(), '.pi', 'agent'),
          p.join(os.homedir(), '.pi')
        ];
        const prov = String(msg.provider || '').trim();
        const targetModelId = String(msg.modelId || '').trim();

        for (const dir of candidateDirs) {
          const mPath = p.join(dir, 'models.json');
          if (fs.existsSync(mPath)) {
            try {
              const data = JSON.parse(fs.readFileSync(mPath, 'utf8'));
              if (data && data.providers && data.providers[prov]) {
                const conf = data.providers[prov];
                if (Array.isArray(conf.models)) {
                  // 只移除目标 modelId，保留其他所有模型
                  conf.models = conf.models.filter(m => {
                    const id = typeof m === 'string' ? m : (m.id || m.name);
                    return String(id).trim() !== targetModelId;
                  });
                }
                fs.writeFileSync(mPath, JSON.stringify(data, null, 2), 'utf8');
              }
            } catch (e) {}
          }
        }
        this.sendJson({
          t: 'model_deleted',
          provider: msg.provider,
          modelId: msg.modelId,
          success: true
        });
      } catch (err) {
        this.sendJson({
          t: 'model_deleted',
          provider: msg.provider,
          modelId: msg.modelId,
          success: false,
          error: err.message
        });
      }
      break;
    }
    case 'update_auth': {
      try {
        const os = require('os');
        const fs = require('fs');
        const p = require('path');
        const candidateDirs = [
          'C:\\Users\\Orion\\.pi\\agent',
          'C:\\Users\\Orion\\.pi',
          p.join(os.homedir(), '.pi', 'agent'),
          p.join(os.homedir(), '.pi')
        ];
        const prov = String(msg.provider || '').trim();
        const apiKey = msg.apiKey ? String(msg.apiKey).trim() : '';
        const baseUrl = msg.baseUrl ? String(msg.baseUrl).trim() : null;
        const newModels = Array.isArray(msg.models) ? msg.models : (msg.modelId ? [msg.modelId] : []);

        for (const dir of candidateDirs) {
          if (!fs.existsSync(dir)) {
            try { fs.mkdirSync(dir, { recursive: true }); } catch (_) {}
          }
          if (apiKey) {
            const aPath = p.join(dir, 'auth.json');
            let aData = {};
            if (fs.existsSync(aPath)) {
              try { aData = JSON.parse(fs.readFileSync(aPath, 'utf8')) || {}; } catch (_) {}
            }
            aData[prov] = apiKey;
            try { fs.writeFileSync(aPath, JSON.stringify(aData, null, 2), 'utf8'); } catch (_) {}
          }
          const mPath = p.join(dir, 'models.json');
          let mData = { providers: {} };
          if (fs.existsSync(mPath)) {
            try { mData = JSON.parse(fs.readFileSync(mPath, 'utf8')) || { providers: {} }; } catch (_) {}
          }
          if (!mData.providers) mData.providers = {};
          if (!mData.providers[prov]) {
            mData.providers[prov] = { models: [] };
          }
          const conf = mData.providers[prov];
          if (!Array.isArray(conf.models)) conf.models = [];
          if (baseUrl) conf.baseUrl = baseUrl;
          if (apiKey && !conf.apiKey) conf.apiKey = apiKey;

          for (const m of newModels) {
            const mId = typeof m === 'string' ? m.trim() : (m.id || m.name || '').trim();
            if (!mId) continue;
            const exists = conf.models.some(item => {
              const itemId = typeof item === 'string' ? item : (item.id || item.name);
              return itemId === mId;
            });
            if (!exists) {
              conf.models.push(typeof m === 'object' ? m : { id: mId, name: mId });
            }
          }
          try { fs.writeFileSync(mPath, JSON.stringify(mData, null, 2), 'utf8'); } catch (_) {}
        }
        this.sendJson({
          t: 'auth_updated',
          provider: prov,
          success: true
        });
      } catch (err) {
        this.sendJson({
          t: 'auth_updated',
          provider: msg.provider,
          success: false,
          error: err.message
        });
      }
      break;
    }
    case 'delete_pi_session': {
      try {
        const fs = require('fs');
        if (msg.file && fs.existsSync(msg.file)) {
          fs.unlinkSync(msg.file);
        }
        this.sendJson({
          t: 'pi_session_deleted',
          file: msg.file,
          id: msg.id,
          success: true
        });
      } catch (err) {
        this.sendJson({
          t: 'pi_session_deleted',
          file: msg.file,
          id: msg.id,
          success: false,
          error: err.message
        });
      }
      break;
    }
    case 'get_models': {
      try {
        const os = require('os');
        const fs = require('fs');
        const p = require('path');
        const candidateDirs = [
          'C:\\Users\\Orion\\.pi\\agent',
          'C:\\Users\\Orion\\.pi',
          p.join(os.homedir(), '.pi', 'agent'),
          p.join(os.homedir(), '.pi')
        ];
        const resultModels = [];
        for (const dir of candidateDirs) {
          const mPath = p.join(dir, 'models.json');
          if (fs.existsSync(mPath)) {
            try {
              const data = JSON.parse(fs.readFileSync(mPath, 'utf8'));
              if (data && data.providers) {
                for (const [prov, conf] of Object.entries(data.providers)) {
                  if (Array.isArray(conf.models)) {
                    for (const m of conf.models) {
                      const id = typeof m === 'string' ? m : (m.id || m.name);
                      if (id) resultModels.push({ provider: prov, id: id, name: id });
                    }
                  }
                }
              }
            } catch (e) {}
          }
        }
        this.sendJson({
          t: 'models_list',
          models: resultModels
        });
      } catch (e) {}
      break;
    }
      case 'list':
        this.sendSessions();
        this.refreshRpcSessionNames();
        break;
      case 'client_online':
        this.log('client online, resending state');
        this.sendSettings();
        this.sendSessions();
        this.refreshRpcSessionNames();
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
      case 'agent_command': {
        const session = this.sessions.get(msg.sid);
        if (session && session.kind === 'rpc' && session.running) {
          session.writeCommand(msg.command);
        }
        break;
      }
      case 'list_pi_sessions': {
        const session = this.sessions.get(msg.sid);
        if (session && session.kind === 'rpc') {
          const cwd = String(msg.cwd || '').trim() || undefined;
          const sessions = listPiSessions({
            sessionsDir: resolveSessionsDir(this.config),
            cwd,
            limit: 60,
          });
          this.log(`pi sessions for ${session.sid}: ${sessions.length} (cwd=${cwd || 'all'})`);
          this.sendJson({ t: 'agent_event', sid: session.sid, event: { type: 'pi_sessions', sessions } });
        }
        break;
      }
      case 'change_cwd': {
        const session = this.sessions.get(msg.sid);
        if (session && session.kind === 'rpc' && session.running) {
          this.changeCwd(session, msg.cwd);
        }
        break;
      }

      // 目录与文件列表查询支持（已支持返回文件）
      case 'list_dirs': {
        const reqPath = msg.path;
        const dirs = listDirectories(reqPath) || [];
        let files = [];
        if (reqPath) {
          try {
            const scanPath = reqPath.endsWith(':') ? reqPath + '\\' : reqPath;
            const entries = fs.readdirSync(scanPath, { withFileTypes: true });
            files = entries
              .filter((e) => {
                try {
                  return e.isFile();
                } catch {
                  return false;
                }
              })
              .map((e) => e.name)
              .sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }));
          } catch (_) {}
        }
        this.sendJson({
          t: 'dir_listing',
          path: String(reqPath || ''),
          dirs,
          files,
        });
        break;
      }

      // 文件内容预览支持
      case 'read_file': {
        const filePath = msg.path;
        try {
          const stat = fs.statSync(filePath);
          if (stat.size > 512 * 1024) {
            const fd = fs.openSync(filePath, 'r');
            const buf = Buffer.alloc(512 * 1024);
            fs.readSync(fd, buf, 0, buf.length, 0);
            fs.closeSync(fd);
            this.sendJson({
              t: 'file_content',
              path: filePath,
              content: buf.toString('utf8') + '\n\n... (文件过大，仅截取前 512KB)',
            });
          } else {
            const content = fs.readFileSync(filePath, 'utf8');
            this.sendJson({
              t: 'file_content',
              path: filePath,
              content,
            });
          }
        } catch (err) {
          this.sendJson({
            t: 'file_content',
            path: filePath,
            content: '',
            error: err.message,
          });
        }
        break;
      }

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

    if (preset.type === 'rpc') {
      this._createRpc(msg, preset, cwd);
      return;
    }

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
      kind: 'pty',
      cwd: session.cwd,
      cols: session.cols,
      rows: session.rows,
    });
    this.sendSessions();
  }

  _createRpc(msg, preset, cwd) {
    let session;
    try {
      const { command, args } = this._resolveRpcLaunch(preset);
      session = new RpcSession({
        preset,
        cwd,
        command,
        args,
        envExtra: Object.assign({}, this.config.shellEnv, preset.env),
        pathPrepend: [].concat(this.config.pathPrepend || [], preset.pathPrepend || []),
        onEvent: (s, event) => this._onAgentEvent(s, event),
        onExit: (s, code) => this._onExit(s, code),
        onLog: (m) => this.log(m),
      });
    } catch (err) {
      this.log(`failed to create rpc session: ${err.message}`);
      this.sendJson({ t: 'error', code: 'spawn_failed', message: err.message });
      return;
    }

    this.sessions.set(session.sid, session);
    this.log(`rpc session created ${session.sid} preset=${preset.id} cwd=${cwd} pid=${session.pid}`);

    this.sendJson({
      t: 'created',
      sid: session.sid,
      name: session.name,
      preset: preset.id,
      kind: 'rpc',
      cwd: session.cwd,
      cols: msg.cols || 0,
      rows: msg.rows || 0,
    });
    this.sendSessions();
  }

  _resolveRpcLaunch(preset) {
    const piDir = preset.piDir || this.config.piDir;
    if (!piDir) {
      throw new Error('rpc preset requires piDir (the pi-node runtime directory)');
    }
    const command = path.join(piDir, 'node.exe');
    const script = path.join(
      piDir,
      'node_modules',
      '@earendil-works',
      'pi-coding-agent',
      'dist',
      'bundle',
      'cli.js',
    );
    if (!fs.existsSync(command)) {
      throw new Error(`node.exe not found in piDir: ${piDir}`);
    }
    if (!fs.existsSync(script)) {
      throw new Error(`pi cli.js not found: ${script}`);
    }
    return { command, args: [script].concat(preset.args || ['--mode', 'rpc']) };
  }

  attach(msg) {
    const session = this.sessions.get(msg.sid);
    if (!session) {
      this.sendJson({ t: 'error', code: 'no_session', sid: msg.sid });
      return;
    }

    for (const other of this.sessions.values()) {
      if (other !== session) other.attached = false;
    }

    if (session.kind === 'rpc') {
      this._attachRpc(session);
      return;
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

  async _attachRpc(session) {
    session.attached = false;

    let state = null;
    let entries = [];
    let snapshotError = null;
    try {
      const [stateRes, entriesRes] = await Promise.all([
        session.request({ type: 'get_state' }),
        session.request({ type: 'get_entries' }),
      ]);
      if (stateRes && stateRes.success) state = stateRes.data;
      if (entriesRes && entriesRes.success) {
        entries = (entriesRes.data && entriesRes.data.entries) || [];
      } else if (entriesRes) {
        snapshotError = entriesRes.error || 'get_entries failed';
      } else {
        snapshotError = 'pi did not answer in time';
      }
    } catch (err) {
      snapshotError = err.message;
    }

    let snapshotName;
    if (state) {
      let displayName = state.sessionName;
      if (!displayName && state.sessionFile) {
        const summary = readSessionSummary(state.sessionFile);
        if (summary) displayName = summary.name || summary.preview || undefined;
      }
      snapshotName = displayName ?? undefined;
      session.setPiIdentity(state.sessionId, displayName, state.sessionFile);
    }

    const MAX_BATCH_BYTES = 2 * 1024 * 1024;
    const batches = [];
    let batch = [];
    let batchBytes = 0;
    for (const entry of entries) {
      const size = Buffer.byteLength(JSON.stringify(entry));
      if (batch.length > 0 && batchBytes + size > MAX_BATCH_BYTES) {
        batches.push(batch);
        batch = [];
        batchBytes = 0;
      }
      batch.push(entry);
      batchBytes += size;
    }
    if (batch.length > 0 || batches.length === 0) batches.push(batch);

    batches.forEach((batchEntries, index) => {
      this.sendJson({
        t: 'agent_event',
        sid: session.sid,
        event: {
          type: index === 0 ? 'snapshot' : 'snapshot_batch',
          state,
          entries: batchEntries,
          truncated: false,
          error: snapshotError,
          name: snapshotName,
          batch: { index, total: batches.length },
        },
      });
    });

    session.attached = true;
    this.log(`attached rpc ${session.sid} entries=${entries.length} batches=${batches.length}`);
  }

  _onAgentEvent(session, event) {
    if (!session.attached) return;
    this.sendJson({ t: 'agent_event', sid: session.sid, event });
  }

  changeCwd(session, requestedCwd) {
    const newCwd = String(requestedCwd || '').trim();
    try {
      if (!newCwd || !fs.statSync(newCwd).isDirectory()) throw new Error('目录不存在');
    } catch (err) {
      this.sendJson({ t: 'error', code: 'invalid_cwd', message: `路径无效：${err.message}`, sid: session.sid });
      return;
    }
    if (normalizeCwdLike(session.cwd) === normalizeCwdLike(newCwd)) {
      return;
    }

    const preset = this.presets.find((p) => p.id === session.presetId) || this.presets[0];
    if (!preset || preset.type !== 'rpc') {
      this.sendJson({ t: 'error', code: 'invalid_cwd', message: '找不到原会话的 rpc 预设', sid: session.sid });
      return;
    }

    const agentDir = path.dirname(resolveSessionsDir(this.config));
    const sourceFile = session.piSessionFile;
    const wasAttached = session.attached;
    const oldCwd = session.cwd;
    const oldSession = session;

    let resumedFile = null;
    try {
      if (sourceFile && fs.existsSync(sourceFile)) {
        resumedFile = copySessionForCwd(sourceFile, newCwd, agentDir);
      }
    } catch (err) {
      this.log(`change_cwd copy failed: ${err.message}`);
      this.sendJson({ t: 'error', code: 'invalid_cwd', message: `复制会话失败：${err.message}`, sid: session.sid });
      return;
    }

    const { command, args } = this._resolveRpcLaunch(preset);
    const replacement = new RpcSession({
      sid: session.sid,
      preset,
      cwd: newCwd,
      command,
      args,
      envExtra: Object.assign({}, this.config.shellEnv, preset.env),
      pathPrepend: [].concat(this.config.pathPrepend || [], preset.pathPrepend || []),
      onEvent: (s, event) => this._onAgentEvent(s, event),
      onExit: (s, code) => this._onExit(s, code),
      onLog: (m) => this.log(m),
    });
    this.sessions.set(replacement.sid, replacement);
    replacement.attached = wasAttached;

    oldSession.attached = false;
    oldSession.kill();

    this.log(`change_cwd ${replacement.sid}: ${oldCwd} -> ${newCwd} (resume=${resumedFile || 'no'})`);

    void (async () => {
      try {
        const pre = await replacement.request({ type: 'get_state' }, 15000);
        const throwawayFile = pre && pre.success ? pre.data.sessionFile : null;
        if (resumedFile) {
          await replacement.request({ type: 'switch_session', sessionPath: resumedFile }, 30000);
        }
        if (throwawayFile && throwawayFile !== resumedFile) {
          try {
            const summary = readSessionSummary(throwawayFile);
            if (!summary || summary.messageCount === 0) {
              fs.rmSync(throwawayFile, { force: true });
            }
          } catch {}
        }
      } catch (err) {
        this.log(`change_cwd resume failed: ${err.message}`);
      }
      if (wasAttached) {
        await this._attachRpc(replacement);
      }
      this.sendSessions();
    })();
  }

  refreshRpcSessionNames() {
    for (const session of this.sessions.values()) {
      if (session.kind !== 'rpc' || !session.running) continue;
      if (session.piSessionName) continue;
      session
        .request({ type: 'get_state' })
        .then((res) => {
          if (!res || !res.success || !session.running) return;
          const state = res.data || {};
          let displayName = state.sessionName;
          if (!displayName && state.sessionFile) {
            const summary = readSessionSummary(state.sessionFile);
            if (summary) {
              displayName = summary.name || summary.preview || undefined;
            }
          }
          const before = session.piSessionName;
          session.setPiIdentity(state.sessionId, displayName, state.sessionFile);
          if (session.piSessionName !== before) this.sendSessions();
        })
        .catch(() => {});
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
    if (this.sessions.get(session.sid) !== session) return;
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



