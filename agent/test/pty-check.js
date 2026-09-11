'use strict';

const pty = require('node-pty');

const SHELL = 'C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe';
const CWD = process.env.USERPROFILE || 'C:\\';

console.log('node-pty version:', require('node-pty/package.json').version);

const proc = pty.spawn(SHELL, ['-NoLogo', '-NoProfile', '-Command', 'Write-Host PTY_OK'], {
  name: 'xterm-256color',
  cols: 80,
  rows: 24,
  cwd: CWD,
  env: Object.assign({}, process.env, { TERM: 'xterm-256color' }),
});

let out = '';
proc.onData((d) => {
  out += d;
});

proc.onExit(({ exitCode }) => {
  console.log('exit code:', exitCode);
  console.log('captured:', JSON.stringify(out));
  console.log('PTY_OK present:', out.includes('PTY_OK'));
  process.exit(0);
});

setTimeout(() => {
  console.log('TIMEOUT, captured:', JSON.stringify(out));
  proc.kill();
  process.exit(1);
}, 8000);
