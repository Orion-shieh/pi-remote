'use strict';

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const LEGACY = 'C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe';
const WINDOWS_APPS = 'C:\\Program Files\\WindowsApps';

const MSI_CANDIDATES = [
  'C:\\Program Files\\PowerShell\\7\\pwsh.exe',
  'C:\\Program Files\\PowerShell\\7-preview\\pwsh.exe',
];

let cached = null;

// Scans the machine-wide package directory. Unlike Get-AppxPackage this does
// not depend on the package being registered for the account we are running
// as, which is exactly the case when the agent runs as LocalSystem.
function fromWindowsApps() {
  let entries;
  try {
    entries = fs.readdirSync(WINDOWS_APPS);
  } catch {
    return null;
  }

  const versions = entries
    .filter((name) => /^Microsoft\.PowerShell_\d/i.test(name))
    .sort((a, b) => {
      const va = a.split('_')[1] || '0';
      const vb = b.split('_')[1] || '0';
      const pa = va.split('.').map(Number);
      const pb = vb.split('.').map(Number);
      for (let i = 0; i < Math.max(pa.length, pb.length); i += 1) {
        const d = (pa[i] || 0) - (pb[i] || 0);
        if (d !== 0) return d;
      }
      return 0;
    })
    .reverse();

  for (const dir of versions) {
    const exe = path.join(WINDOWS_APPS, dir, 'pwsh.exe');
    if (fs.existsSync(exe)) return exe;
  }
  return null;
}

function fromAppxPackage() {
  try {
    const out = execFileSync(
      LEGACY,
      [
        '-NoProfile',
        '-NonInteractive',
        '-Command',
        '(Get-AppxPackage Microsoft.PowerShell | Select-Object -First 1).InstallLocation',
      ],
      { encoding: 'utf8', timeout: 20000, windowsHide: true }
    ).trim();

    if (out) {
      const exe = path.join(out, 'pwsh.exe');
      if (fs.existsSync(exe)) return exe;
    }
  } catch {
    // Appx lookup is best effort; fall through to the legacy shell.
  }
  return null;
}

// PowerShell 7 is preferred over 5.1 because 5.1 defaults to the legacy ANSI
// code page, which mangles the UTF-8 output that TUI programs like pi emit.
function resolveShell() {
  if (cached) return cached;

  for (const candidate of MSI_CANDIDATES) {
    if (fs.existsSync(candidate)) {
      cached = candidate;
      return cached;
    }
  }

  const packaged = fromWindowsApps();
  if (packaged) {
    cached = packaged;
    return cached;
  }

  const appx = fromAppxPackage();
  if (appx) {
    cached = appx;
    return cached;
  }

  if (fs.existsSync(LEGACY)) {
    cached = LEGACY;
    return cached;
  }

  throw new Error('no usable PowerShell installation found');
}

function resetCache() {
  cached = null;
}

module.exports = { resolveShell, resetCache, LEGACY, MSI_CANDIDATES, WINDOWS_APPS, fromWindowsApps };
