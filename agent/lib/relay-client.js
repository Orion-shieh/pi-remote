'use strict';

const crypto = require('crypto');
const WebSocket = require('ws');

const HEARTBEAT_MS = 30000;
const HANDSHAKE_TIMEOUT_MS = 15000;
const MAX_BACKOFF_MS = 30000;

function sign(token, role, id, ts, nonce) {
  return crypto.createHmac('sha256', token).update(`${role}|${id}|${ts}|${nonce}`).digest('hex');
}

// Outbound WebSocket connection to the relay. Runs behind NAT, so it always
// dials out; nothing needs to be exposed on this machine.
class RelayClient {
  constructor({ endpoints, agentId, agentToken, pinnedSha256 }) {
    this.endpoints = endpoints;
    this.agentId = agentId;
    this.agentToken = agentToken;
    this.pinnedSha256 = pinnedSha256 ? pinnedSha256.toUpperCase() : null;
    this.endpointIndex = 0;
    this.backoffMs = 1000;
    this.ws = null;
    this.ready = false;
    this.stopping = false;
    this.handlers = { message: [], ready: [], close: [], log: [] };
    this._heartbeat = null;
  }

  on(event, fn) {
    if (!this.handlers[event]) this.handlers[event] = [];
    this.handlers[event].push(fn);
    return this;
  }

  _emit(event, ...args) {
    for (const fn of this.handlers[event] || []) fn(...args);
  }

  _log(...args) {
    this._emit('log', ...args);
  }

  get url() {
    return this.endpoints[this.endpointIndex % this.endpoints.length];
  }

  start() {
    this.stopping = false;
    this._connect();
    this._heartbeat = setInterval(() => this._tick(), HEARTBEAT_MS);
  }

  stop() {
    this.stopping = true;
    if (this._heartbeat) clearInterval(this._heartbeat);
    if (this.ws) {
      try {
        this.ws.close();
      } catch {
        // Already closed.
      }
    }
  }

  sendJson(obj) {
    if (this.ready && this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(obj));
      return true;
    }
    return false;
  }

  sendBinary(buf) {
    if (this.ready && this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(buf, { binary: true });
      return true;
    }
    return false;
  }

  _tick() {
    const ws = this.ws;
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    if (ws.isAlive === false) {
      this._log('heartbeat timeout, terminating socket');
      ws.terminate();
      return;
    }
    ws.isAlive = false;
    try {
      ws.ping();
    } catch {
      // Socket already dying.
    }
  }

  _peerFingerprint(ws) {
    try {
      const socket = ws._socket;
      if (!socket || typeof socket.getPeerCertificate !== 'function') return null;
      const cert = socket.getPeerCertificate(true);
      if (!cert || !cert.fingerprint256) return null;
      return cert.fingerprint256.toUpperCase();
    } catch {
      return null;
    }
  }

  _connect() {
    if (this.stopping) return;

    const url = this.url;
    this._log(`connecting ${url}`);

    let ws;
    try {
      ws = new WebSocket(url, { rejectUnauthorized: false, handshakeTimeout: HANDSHAKE_TIMEOUT_MS });
    } catch (err) {
      this._retry(`cannot create socket: ${err.message}`);
      return;
    }

    this.ws = ws;
    ws.isAlive = true;
    let settled = false;

    const handshakeTimer = setTimeout(() => {
      if (!this.ready) {
        this._log('handshake timeout');
        ws.terminate();
      }
    }, HANDSHAKE_TIMEOUT_MS);

    ws.on('pong', () => {
      ws.isAlive = true;
    });

    ws.on('open', () => {
      const fingerprint = this._peerFingerprint(ws);
      if (!fingerprint) {
        this._log('could not read peer certificate');
        ws.terminate();
        return;
      }
      if (this.pinnedSha256 && fingerprint !== this.pinnedSha256) {
        this._log(`certificate pin mismatch: got ${fingerprint}, expected ${this.pinnedSha256}`);
        ws.close(4000, 'pin mismatch');
        return;
      }

      const ts = Date.now();
      const nonce = crypto.randomBytes(16).toString('hex');
      ws.send(
        JSON.stringify({
          t: 'hello',
          role: 'agent',
          id: this.agentId,
          ts,
          nonce,
          sig: sign(this.agentToken, 'agent', this.agentId, ts, nonce),
        })
      );
    });

    ws.on('message', (data, isBinary) => {
      if (!this.ready) {
        if (isBinary) return;
        let msg;
        try {
          msg = JSON.parse(data.toString('utf8'));
        } catch {
          return;
        }
        if (msg.t === 'hello_ok') {
          settled = true;
          clearTimeout(handshakeTimer);
          this.ready = true;
          this.backoffMs = 1000;
          this._log(`connected as agent id=${this.agentId}`);
          this._emit('ready', msg);
        }
        return;
      }
      this._emit('message', isBinary ? data : this._parse(data), isBinary);
    });

    ws.on('close', (code, reason) => {
      clearTimeout(handshakeTimer);
      this.ready = false;
      const text = reason ? reason.toString() : '';
      if (code === 4403) {
        this._log('relay rejected our credentials (4403) - check agentToken');
      }
      this._retry(`closed (${code}${text ? ' ' + text : ''})`);
    });

    ws.on('error', (err) => {
      this._log(`socket error: ${err.message}`);
      if (!settled) {
        clearTimeout(handshakeTimer);
        try {
          ws.terminate();
        } catch {
          // Already dead.
        }
        if (!this.ready) this._retry(`connect failed: ${err.message}`);
      }
    });
  }

  _parse(data) {
    try {
      return JSON.parse(data.toString('utf8'));
    } catch {
      return null;
    }
  }

  _retry(reason) {
    if (this.stopping) return;
    if (this._retryScheduled) return;
    this._retryScheduled = true;

    this.ready = false;
    this._emit('close', reason);

    const delay = this.backoffMs;
    this.backoffMs = Math.min(this.backoffMs * 2, MAX_BACKOFF_MS);
    this.endpointIndex += 1;

    this._log(`${reason}; retrying in ${delay}ms via ${this.url}`);
    setTimeout(() => {
      this._retryScheduled = false;
      this._connect();
    }, delay);
  }
}

module.exports = { RelayClient };
