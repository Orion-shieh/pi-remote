'use strict';

// Builds the environment handed to spawned shells and pi RPC processes. When
// the agent runs as LocalSystem the inherited environment points at SYSTEM's
// profile and lacks the user's PATH, so pi would neither be found nor find its
// own settings. Normalising the PATH key also avoids emitting a duplicate
// `Path`/`PATH` pair into the child environment block.
function buildEnv(envExtra, pathPrepend) {
  const source = Object.assign({}, process.env, envExtra || {});
  const env = {};
  let existingPath = '';

  for (const [key, value] of Object.entries(source)) {
    if (key.toLowerCase() === 'path') {
      existingPath = existingPath || value;
      continue;
    }
    env[key] = value;
  }

  const parts = (pathPrepend || []).filter(Boolean);
  if (existingPath) parts.push(existingPath);
  if (parts.length > 0) env.Path = parts.join(';');
  env.TERM = 'xterm-256color';
  env.TERMUX_VERSION = '0.118.0';

  return env;
}

module.exports = { buildEnv };
