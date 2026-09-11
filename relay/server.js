#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const https = require('https');
const { WebSocketServer } = require('ws');

const { readHeader, bytesToUuid } = require('./protocol');
const { verifyHandshake, NonceCache } = require('./auth');

const CONFIG_PATH = process.env.PI_RELAY_CONFIG || path.join(__dirname, 'config.json');

const DEFAULTS = {
  ports: [443, 8443],
  path: '/relay',
  certFile: '/etc/pi-relay/cert.pem',
  keyFile: '/etc/pi-relay/key.pem',
  heartbeatMs: 30000,
  handshakeTimeoutMs: 10000,
  maxPayloadBytes: 4 * 1024 * 1024,
};

function loadConfig() {
  let raw = {};
  try {
    raw = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
  } catch (err) {
    if (err.code === 'ENOENT') {
      console.warn(`config file ${CONFIG_PATH} not found, falling back to defaults`);
    } else if (err instanceof SyntaxError) {
      throw new Error(`config file ${CONFIG_PATH} is not valid JSON: ${err.message}`);
    } else {
      throw new Error(`cannot read config file ${CONFIG_PATH}: ${err.message}`);
    }
  }

  const cfg = Object.assign({}, DEFAULTS, raw);
  if (!cfg.agentToken || !cfg.deviceToken) {
    throw new Error(`agentToken and deviceToken must be set in ${CONFIG_PATH}`);
  }
  cfg.tokens = { agent: cfg.agentToken, client: cfg.deviceToken };
  return cfg;
}

const cfg = loadConfig();
const nonces = new NonceCache();

const agents = new Map(); // agentId -> ws
const sessionOwner = new Map(); // sid -> agentId
const sockets = new Set();
let client = null; // single client socket (MVP: one device at a time)
let clientAgentId = null;

function log(...args) {
  console.log(new Date().toISOString(), ...args);
}

function sendJson(ws, obj) {
  if (ws && ws.readyState === ws.OPEN) ws.send(JSON.stringify(obj));
}

function notifyClient(obj) {
  sendJson(client, obj);
}

function resolveAgent(ws, sid) {
  if (sid && sessionOwner.has(sid)) {
    const owner = sessionOwner.get(sid);
    const target = agents.get(owner);
    if (target) return target;
  }
  if (ws.boundAgentId && agents.has(ws.boundAgentId)) return agents.get(ws.boundAgentId);
  if (agents.size === 1) return agents.values().next().value;
  return null;
}

function handleHello(ws, data, peer, attach) {
  let msg;
  try {
    msg = JSON.parse(data.toString('utf8'));
  } catch {
    return false;
  }
  if (!msg || msg.t !== 'hello') return false;

  const reason = verifyHandshake(msg, cfg.tokens[msg.role], nonces);
  if (reason) {
    log(`handshake rejected peer=${peer} role=${msg.role} reason=${reason}`);
    ws.close(4403, 'unauthorized');
    return false;
  }

  if (msg.role === 'client' && typeof msg.agentId === 'string' && msg.agentId) {
    ws.boundAgentId = msg.agentId;
  }
  attach(msg.role, String(msg.id));
  log(`handshake ok role=${msg.role} id=${msg.id} peer=${peer}`);
  return true;
}

function routeFromAgent(agentId, data, isBinary) {
  if (isBinary) {
    if (client) client.send(data, { binary: true });
    return;
  }

  const text = data.toString('utf8');
  let msg;
  try {
    msg = JSON.parse(text);
  } catch {
    return;
  }

  if (msg.t === 'sessions' && Array.isArray(msg.list)) {
    for (const session of msg.list) {
      if (session && session.sid) sessionOwner.set(session.sid, agentId);
    }
  } else if (msg.t === 'created' && msg.sid) {
    sessionOwner.set(msg.sid, agentId);
  } else if (msg.t === 'exit' && msg.sid) {
    sessionOwner.delete(msg.sid);
  }

  if (client) client.send(text);
}

function routeFromClient(ws, data, isBinary) {
  if (isBinary) {
    const header = readHeader(data);
    if (!header) return;
    const target = resolveAgent(ws, bytesToUuid(header.sid));
    if (target) target.send(data, { binary: true });
    return;
  }

  const text = data.toString('utf8');
  let msg;
  try {
    msg = JSON.parse(text);
  } catch {
    return;
  }

  const target = resolveAgent(ws, msg.sid || null);
  if (target) {
    target.send(text);
  } else {
    sendJson(ws, { t: 'error', code: 'no_agent', message: 'agent not connected' });
  }
}

function handleConnection(ws, req, port) {
  const peer = `${req.socket.remoteAddress}:${req.socket.remotePort}`;
  sockets.add(ws);
  ws.isAlive = true;

  let role = null;
  let id = null;

  const handshakeTimer = setTimeout(() => {
    if (!role) {
      log(`handshake timeout peer=${peer} port=${port}`);
      ws.close(4401, 'handshake timeout');
    }
  }, cfg.handshakeTimeoutMs);

  const attach = (kind, identity) => {
    role = kind;
    id = identity;
    clearTimeout(handshakeTimer);

    if (kind === 'agent') {
      const previous = agents.get(identity);
      if (previous && previous !== ws) {
        log(`agent reconnected, replacing old socket id=${identity}`);
        previous.close(4409, 'replaced');
      }
      agents.set(identity, ws);
      log(`agent online id=${identity} peer=${peer} port=${port}`);
      notifyClient({ t: 'agent_online', id: identity });
    } else {
      if (client && client !== ws) {
        log('client replaced by newer connection');
        client.close(4409, 'replaced');
      }
      client = ws;
      clientAgentId = ws.boundAgentId || (agents.size === 1 ? agents.keys().next().value : null);
      log(`client online peer=${peer} port=${port} agent=${clientAgentId || 'none'}`);
      if (clientAgentId) {
        const boundAgent = agents.get(clientAgentId);
        if (boundAgent) sendJson(boundAgent, { t: 'client_online', id: identity });
      } else {
        notifyClient({ t: 'agent_offline' });
      }
    }

    sendJson(ws, {
      t: 'hello_ok',
      role: kind,
      id: identity,
      agentId: kind === 'client' ? clientAgentId : null,
      agents: [...agents.keys()],
    });
  };

  ws.on('pong', () => {
    ws.isAlive = true;
  });

  ws.on('message', (data, isBinary) => {
    if (!role) {
      handleHello(ws, data, peer, attach);
      return;
    }
    if (role === 'agent') routeFromAgent(id, data, isBinary);
    else routeFromClient(ws, data, isBinary);
  });

  ws.on('close', () => {
    clearTimeout(handshakeTimer);
    sockets.delete(ws);

    if (role === 'agent' && agents.get(id) === ws) {
      agents.delete(id);
      for (const [sid, owner] of sessionOwner) {
        if (owner === id) sessionOwner.delete(sid);
      }
      log(`agent offline id=${id}`);
      notifyClient({ t: 'agent_offline', id });
    }

    if (role === 'client' && client === ws) {
      const boundAgent = clientAgentId ? agents.get(clientAgentId) : null;
      client = null;
      clientAgentId = null;
      log('client offline');
      if (boundAgent) sendJson(boundAgent, { t: 'client_offline' });
    }
  });

  ws.on('error', (err) => log(`socket error peer=${peer}: ${err.message}`));
}

function start() {
  const tlsOptions = {
    key: fs.readFileSync(cfg.keyFile),
    cert: fs.readFileSync(cfg.certFile),
  };

  for (const port of cfg.ports) {
    const server = https.createServer(tlsOptions, (req, res) => {
      if (req.url === '/healthz') {
        res.writeHead(200, { 'content-type': 'application/json' });
        res.end(
          JSON.stringify({
            ok: true,
            uptime: Math.round(process.uptime()),
            agents: [...agents.keys()],
            clientAgentId,
            sessions: sessionOwner.size,
          })
        );
        return;
      }
      res.writeHead(404, { 'content-type': 'text/plain' });
      res.end('not found\n');
    });

    server.on('error', (err) => {
      log(`fatal: cannot listen on ${port}: ${err.message}`);
      process.exit(1);
    });

    const wss = new WebSocketServer({
      server,
      path: cfg.path,
      maxPayload: cfg.maxPayloadBytes,
    });
    wss.on('connection', (ws, req) => handleConnection(ws, req, port));

    server.listen(port, '0.0.0.0', () => {
      log(`listening wss://0.0.0.0:${port}${cfg.path}`);
    });
  }

  setInterval(() => {
    for (const ws of sockets) {
      if (ws.isAlive === false) {
        log('heartbeat timeout, terminating socket');
        ws.terminate();
        continue;
      }
      ws.isAlive = false;
      ws.ping();
    }
  }, cfg.heartbeatMs).unref();
}

process.on('uncaughtException', (err) => log(`uncaught: ${err.stack || err.message}`));
process.on('unhandledRejection', (err) => log(`unhandled: ${err && err.stack ? err.stack : err}`));

start();
