'use strict';

// Direct smoke test for pi's RPC mode, bypassing the relay and the agent:
// spawns `pi --mode rpc`, checks the protocol end to end (state, prompt,
// streamed events, entries, shutdown).
//
//   node tools/pi-rpc-probe.js [piDir]
//
// Uses --no-session so nothing is written to the real session list. The prompt
// is intentionally tiny; it costs a few tokens against the configured model.

const assert = require('assert');
const path = require('path');

const { PiRpc, probePiVersion } = require(path.join(__dirname, '..', 'agent', 'lib', 'pi-rpc'));

const piDir = process.argv[2] || 'C:/Users/Orion/AppData/Local/pi-node/current';

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function main() {
  const version = await probePiVersion(piDir);
  console.log(`[probe] pi version: ${version}`);

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

  const events = [];
  let exited = false;

  const rpc = new PiRpc({
    command: nodeExe,
    args: [script, '--mode', 'rpc', '--no-session'],
    cwd: 'D:/Program/Pi_Agent',
    env: process.env,
    onEvent: (event) => {
      events.push(event);
      if (event.type !== 'message_update') {
        console.log(`[probe] event: ${event.type}${event.toolName ? ` (${event.toolName})` : ''}`);
      }
    },
    onExit: (code) => {
      exited = true;
      console.log(`[probe] exited code=${code}`);
    },
    onLog: (msg) => console.log(`[probe] ${msg}`),
  });

  try {
    const state = await rpc.request({ type: 'get_state' });
    assert(state && state.success, `get_state failed: ${state && state.error}`);
    assert(state.data.sessionId, 'no sessionId in state');
    console.log(`[probe] state ok, sessionId=${state.data.sessionId}`);

    rpc.send({ type: 'prompt', message: '请只回复两个字母：ok' });

    const deadline = Date.now() + 120000;
    while (Date.now() < deadline) {
      const settled = events.some((e) => e.type === 'agent_settled');
      const assistantDone = events.some(
        (e) => e.type === 'message_end' && e.message && e.message.role === 'assistant',
      );
      if (settled && assistantDone) break;
      await sleep(200);
    }

    const assistantEnd = events.find(
      (e) => e.type === 'message_end' && e.message && e.message.role === 'assistant',
    );
    assert(assistantEnd, 'no assistant message_end observed');
    const text = (assistantEnd.message.content || [])
      .filter((b) => b.type === 'text')
      .map((b) => b.text)
      .join('');
    console.log(`[probe] assistant text: ${JSON.stringify(text.slice(0, 80))}`);
    assert(/ok/i.test(text), 'assistant reply does not contain "ok"');
    assert(events.some((e) => e.type === 'agent_settled'), 'no agent_settled event');

    const entries = await rpc.request({ type: 'get_entries' });
    assert(entries && entries.success, `get_entries failed: ${entries && entries.error}`);
    const kinds = entries.data.entries.map((e) => e.type);
    console.log(`[probe] entries: ${kinds.join(', ')}`);
    assert(kinds.filter((t) => t === 'message').length >= 2, 'expected user+assistant entries');

    // toolcall deltas must carry id/toolName (the GUI keys its cards on them)
    const toolcallStart = events.find((e) => e.assistantMessageEvent && e.assistantMessageEvent.type === 'toolcall_start');
    if (toolcallStart) {
      assert(toolcallStart.assistantMessageEvent.toolName, 'toolcall_start without toolName');
    }

    console.log('[probe] all checks passed');
  } finally {
    rpc.kill();
    const deadline = Date.now() + 10000;
    while (!exited && Date.now() < deadline) await sleep(100);
  }
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error(`[probe] FAILED: ${err.message}`);
    process.exit(1);
  });
