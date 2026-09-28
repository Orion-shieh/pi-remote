'use strict';

// SessionManager-level test for RPC sessions: create via a type:"rpc" preset,
// attach (structured snapshot), drive pi with agent_command, verify event
// gating while detached, and kill the process tree. Spawns the real pi
// runtime with --no-session so nothing is written to the real session list.
//
//   node agent/test/rpc-session.js [piDir]

const assert = require('assert');
const path = require('path');

const { SessionManager } = require(path.join(__dirname, '..', 'lib', 'session-manager'));

const piDir = process.argv[2] || 'C:/Users/Orion/AppData/Local/pi-node/current';

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function main() {
  const sent = [];
  const transport = {
    sendJson: (obj) => sent.push(obj),
    sendBinary: () => {},
  };

  const config = {
    maxSessions: 8,
    piDir,
    pathPrepend: [],
    presets: [
      {
        id: 'pi-gui',
        name: 'Pi Agent GUI',
        type: 'rpc',
        cwd: 'D:/Program/Pi_Agent',
        args: ['--mode', 'rpc', '--no-session'],
      },
    ],
  };

  const manager = new SessionManager({
    config,
    transport,
    shellPath: 'unused',
    log: (msg) => console.log(`[agent] ${msg}`),
  });

  const byType = (t) => sent.filter((m) => m.t === t);
  const lastOf = (t) => byType(t)[byType(t).length - 1];
  const agentEvents = () =>
    sent.filter((m) => m.t === 'agent_event').map((m) => m.event);

  const waitFor = async (predicate, timeoutMs, label) => {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      if (predicate()) return;
      await sleep(150);
    }
    throw new Error(`timeout waiting for ${label}`);
  };

  // ---- create -------------------------------------------------------------
  manager.handleControl({ t: 'create', preset: 'pi-gui', cols: 80, rows: 24 });
  const created = lastOf('created');
  assert(created && created.kind === 'rpc', `created missing rpc kind: ${JSON.stringify(created)}`);
  assert(created.sid, 'created without sid');
  const sid = created.sid;
  console.log(`[test] created rpc session ${sid}`);

  // Before attach, events must be gated.
  manager.handleControl({ t: 'agent_command', sid, command: { type: 'get_state' } });
  await sleep(2500);
  assert(agentEvents().length === 0, 'events leaked before attach');

  // ---- attach: structured snapshot ---------------------------------------
  manager.handleControl({ t: 'attach', sid, lastSeq: 0 });
  await waitFor(() => agentEvents().some((e) => e.type === 'snapshot'), 20000, 'snapshot');
  const snapshot = agentEvents().find((e) => e.type === 'snapshot');
  assert(Array.isArray(snapshot.entries), 'snapshot.entries missing');
  assert(snapshot.state && snapshot.state.sessionId, 'snapshot.state missing sessionId');
  console.log(`[test] snapshot ok, entries=${snapshot.entries.length}`);

  // ---- prompt via agent_command ------------------------------------------
  const beforeAttach = agentEvents().length;
  manager.handleControl({
    t: 'agent_command',
    sid,
    command: { type: 'prompt', message: '请只回复两个字母：ok' },
  });
  await waitFor(
    () =>
      agentEvents().some(
        (e) => e.type === 'message_end' && e.message && e.message.role === 'assistant',
      ) && agentEvents().some((e) => e.type === 'agent_settled'),
    120000,
    'assistant reply',
  );
  const assistantEnd = agentEvents()
    .filter((e) => e.type === 'message_end' && e.message.role === 'assistant')
    .pop();
  const text = (assistantEnd.message.content || [])
    .filter((b) => b.type === 'text')
    .map((b) => b.text)
    .join('');
  assert(/ok/i.test(text), `assistant reply does not contain ok: ${text.slice(0, 60)}`);
  assert(agentEvents().length > beforeAttach, 'no events forwarded while attached');
  console.log(`[test] prompt roundtrip ok, reply=${JSON.stringify(text.slice(0, 40))}`);

  // Command responses (no agent-issued id) must be forwarded as events.
  assert(
    agentEvents().some((e) => e.type === 'response' && e.command === 'prompt' && e.success),
    'prompt response not forwarded',
  );

  // ---- detach gates events again -----------------------------------------
  manager.handleControl({ t: 'detach', sid });
  await sleep(200);
  const whileDetached = agentEvents().length;
  manager.handleControl({ t: 'agent_command', sid, command: { type: 'get_state' } });
  await sleep(2500);
  assert(agentEvents().length === whileDetached, 'events leaked while detached');
  console.log('[test] detach gating ok');

  // ---- re-attach snapshot carries history --------------------------------
  manager.handleControl({ t: 'attach', sid, lastSeq: 0 });
  await waitFor(() => agentEvents().filter((e) => e.type === 'snapshot').length === 2, 20000, 'second snapshot');
  const snapshot2 = agentEvents().filter((e) => e.type === 'snapshot').pop();
  const messageEntries = snapshot2.entries.filter((e) => e.type === 'message');
  assert(messageEntries.length >= 2, `expected user+assistant entries, got ${messageEntries.length}`);
  const roles = messageEntries.map((e) => e.message.role);
  assert(roles.includes('user') && roles.includes('assistant'), `unexpected roles: ${roles}`);
  console.log(`[test] re-attach snapshot ok, message entries=${messageEntries.length}`);

  // ---- kill ---------------------------------------------------------------
  manager.handleControl({ t: 'kill', sid });
  await waitFor(() => byType('exit').length === 1, 20000, 'exit message');
  assert(lastOf('exit').sid === sid, 'exit for wrong sid');
  console.log('[test] kill ok');

  manager.shutdown();
  console.log('[test] all checks passed');
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error(`[test] FAILED: ${err.stack || err.message}`);
    process.exit(1);
  });
