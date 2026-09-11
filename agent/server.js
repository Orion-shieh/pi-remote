#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');

const { RelayClient } = require('./lib/relay-client');
const { SessionManager } = require('./lib/session-manager');
const { resolveShell } = require('./lib/shell');

const CONFIG_PATH = process.env.PI_AGENT_CONFIG || path.join(__dirname, 'config.json');

function log(...args) {
  console.log(new Date().toISOString(), ...args);
}

function loadConfig() {
  let raw;
  try {
    raw = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
  } catch (err) {
    if (err.code === 'ENOENT') {
      throw new Error(`config file not found: ${CONFIG_PATH} (copy config.example.json first)`);
    }
    if (err instanceof SyntaxError) {
      throw new Error(`config file ${CONFIG_PATH} is not valid JSON: ${err.message}`);
    }
    throw new Error(`cannot read config file ${CONFIG_PATH}: ${err.message}`);
  }

  const relay = raw.relay || {};
  if (!Array.isArray(relay.endpoints) || relay.endpoints.length === 0) {
    throw new Error('relay.endpoints must be a non-empty array');
  }
  if (!relay.agentId) throw new Error('relay.agentId is required');
  if (!relay.agentToken) throw new Error('relay.agentToken is required');

  if (relay.pinnedSha256) {
    relay.pinnedSha256 = relay.pinnedSha256.toUpperCase();
  } else {
    log('WARNING: relay.pinnedSha256 is not set, the relay certificate will not be pinned');
  }

  return raw;
}

function main() {
  const config = loadConfig();
  const shellPath = resolveShell();

  const identity = process.env.USERNAME || process.env.USER || '(unknown)';
  log(`running as ${identity}, USERPROFILE=${process.env.USERPROFILE || '(unset)'}`);
  if (config.shellEnv && config.shellEnv.USERPROFILE) {
    const overridden = config.shellEnv.USERPROFILE.replace(/\//g, '\\');
    const actual = (process.env.USERPROFILE || '').replace(/\//g, '\\');
    if (overridden.toLowerCase() !== actual.toLowerCase()) {
      log(`shell profile overridden to ${config.shellEnv.USERPROFILE} (service account differs from the desktop user)`);
    }
  }
  log(`shell resolved: ${shellPath}`);

  const transport = new RelayClient({
    endpoints: config.relay.endpoints,
    agentId: config.relay.agentId,
    agentToken: config.relay.agentToken,
    pinnedSha256: config.relay.pinnedSha256,
  });

  const manager = new SessionManager({ config, transport, shellPath, log });

  transport.on('log', (msg) => log(msg));
  transport.on('ready', () => manager.onRelayReady());
  transport.on('message', (msg, isBinary) => {
    if (isBinary) manager.handleBinary(msg);
    else manager.handleControl(msg);
  });

  transport.start();

  const shutdown = () => {
    log('shutting down');
    transport.stop();
    manager.shutdown();
    process.exit(0);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);

  process.on('uncaughtException', (err) => log(`uncaught: ${err.stack || err.message}`));
  process.on('unhandledRejection', (err) => log(`unhandled: ${err && err.stack ? err.stack : err}`));

  log(`agent ${config.relay.agentId} starting, ${config.presets.length} preset(s)`);
}

try {
  main();
} catch (err) {
  log(`fatal: ${err.message}`);
  process.exit(1);
}
