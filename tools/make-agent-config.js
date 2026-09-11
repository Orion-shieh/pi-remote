#!/usr/bin/env node
'use strict';

// Generates agent/config.json by merging the deployed relay credentials in
// .secrets/relay.json with the preset definitions in agent/config.example.json.

const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const SECRETS = path.join(ROOT, '.secrets', 'relay.json');
const EXAMPLE = path.join(ROOT, 'agent', 'config.example.json');
const TARGET = path.join(ROOT, 'agent', 'config.json');

const secrets = JSON.parse(fs.readFileSync(SECRETS, 'utf8'));
const config = JSON.parse(fs.readFileSync(EXAMPLE, 'utf8'));

config.relay = {
  endpoints: secrets.endpoints,
  agentId: secrets.agentId,
  agentToken: secrets.agentToken,
  pinnedSha256: secrets.pinnedSha256,
};

fs.writeFileSync(TARGET, `${JSON.stringify(config, null, 2)}\n`);
console.log(`wrote ${TARGET}`);
console.log(`  endpoints : ${config.relay.endpoints.join(', ')}`);
console.log(`  agentId   : ${config.relay.agentId}`);
console.log(`  presets   : ${config.presets.map((p) => p.id).join(', ')}`);
