'use strict';

// Launches the `pi` preset through a real ConPTY and captures the raw byte
// stream, so we can see exactly which escape sequences the Android terminal
// emulator will have to implement.
//
//   node agent/test/pi-check.js [seconds]

const fs = require('fs');
const path = require('path');

const { Session } = require(path.join(__dirname, '..', 'lib', 'session'));
const { resolveShell } = require(path.join(__dirname, '..', 'lib', 'shell'));

const SECONDS = Number(process.argv[2] || 10);
const OUT = path.join(__dirname, 'pi-capture.bin');

const config = JSON.parse(
  fs.readFileSync(path.join(__dirname, '..', 'config.json'), 'utf8')
);
const preset = config.presets.find((p) => p.id === 'pi');

const chunks = [];
let bytes = 0;

const session = new Session({
  preset,
  cwd: preset.cwd,
  cols: 120,
  rows: 30,
  shellPath: preset.shell === 'auto' ? resolveShell() : preset.shell,
  args: preset.args,
  initialInput: preset.initialInput,
  initialInputQuietMs: preset.initialInputQuietMs,
  initialInputMaxWaitMs: preset.initialInputMaxWaitMs,
  onLog: (m) => console.log(`  [session] ${m}`),
  bufferLimits: config.buffers,
  onOutput: (_s, _seq, chunk) => {
    chunks.push(chunk);
    bytes += chunk.length;
  },
  onExit: (_s, code) => {
    console.log(`session exited early with code ${code}`);
  },
});

session.attached = true;
console.log(`launched pi preset, capturing ${SECONDS}s (pid ${session.pty.pid})`);

setTimeout(() => {
  session.attached = false;
  const raw = Buffer.concat(chunks);
  fs.writeFileSync(OUT, raw);

  const text = raw.toString('utf8');
  const checks = {
    'alternate screen (?1049h)': text.includes('\u001b[?1049h'),
    'cursor hide/show (?25)': text.includes('\u001b[?25l'),
    'erase display (CSI 2J)': text.includes('\u001b[2J'),
    'SGR colour (CSI 3x/9x m)': /\u001b\[[0-9;]*3[0-79]m/.test(text),
    '256 / truecolour (38;5;n | 38;2;r;g;b)': /\u001b\[[0-9;]*38[;:](5|2)[;:]/.test(text),
    'cursor positioning (CSI row;col H)': /\u001b\[\d+;\d+H/.test(text),
    'DEC private modes (?xx h/l)': /\u001b\[\?\d+[hl]/.test(text),
    'OSC title (ESC ] 0; )': /\u001b\]0;/.test(text),
    'scroll region (CSI r)': /\u001b\[\d*;\d*r/.test(text),
    'bracketed paste (?2004)': text.includes('\u001b[?2004h'),
    'contains non-ASCII (UTF-8 ok)': /[^\x00-\x7F]/.test(text),
  };

  console.log(`\ncaptured ${bytes} bytes -> ${OUT}`);
  console.log('\nescape sequences observed:');
  for (const [name, ok] of Object.entries(checks)) {
    console.log(`  ${ok ? 'YES' : 'no '}  ${name}`);
  }

  const unique = new Set();
  for (const m of text.matchAll(/\u001b\[[0-9;?]*[a-zA-Z]/g)) unique.add(m[0]);
  console.log(`\ndistinct CSI sequences (${unique.size}):`);
  console.log(
    [...unique]
      .sort()
      .slice(0, 60)
      .map((s) => JSON.stringify(s))
      .join(' ')
  );

  session.kill();
  setTimeout(() => process.exit(0), 500);
}, SECONDS * 1000);
