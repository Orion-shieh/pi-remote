'use strict';

const fs = require('fs');
const path = require('path');

// pi stores one JSONL file per session under ~/.pi/agent/sessions/<project-slug>/.
// The RPC protocol has no "list sessions" command (pi-web scans the same files),
// so the agent walks the directory itself to power the phone's session picker.
function resolveSessionsDir(config) {
  if (config.piSessionsDir) return config.piSessionsDir;
  const profile = (config.shellEnv && config.shellEnv.USERPROFILE) || process.env.USERPROFILE;
  if (!profile) return null;
  return path.join(profile, '.pi', 'agent', 'sessions');
}

function normalizeCwd(value) {
  return String(value || '').replace(/\//g, '\\').replace(/\\+$/, '').toLowerCase();
}

// Reads one session JSONL: header line for id/cwd, then a light scan for the
// display name (last session_info entry), the first user message as preview,
// and the message count.
function readSessionSummary(file) {
  let raw;
  try {
    raw = fs.readFileSync(file, 'utf8');
  } catch {
    return null;
  }

  const lines = raw.split('\n');
  let header = null;
  let name = null;
  let preview = null;
  let messageCount = 0;
  let lastTimestamp = null;

  for (const line of lines) {
    if (!line) continue;
    let entry;
    try {
      entry = JSON.parse(line);
    } catch {
      continue;
    }
    if (!header && entry.type === 'session') {
      header = entry;
      continue;
    }
    if (entry.type === 'session_info' && entry.name) {
      name = entry.name;
    } else if (entry.type === 'message') {
      messageCount += 1;
      if (entry.timestamp) lastTimestamp = entry.timestamp;
      if (!preview && entry.message && entry.message.role === 'user') {
        const content = entry.message.content || [];
        for (const block of content) {
          if (block.type === 'text' && block.text) {
            preview = block.text.replace(/\s+/g, ' ').trim().slice(0, 80);
            break;
          }
        }
      }
    }
  }

  if (!header || !header.id) return null;
  return {
    file,
    id: header.id,
    cwd: header.cwd || '',
    name,
    preview,
    timestamp: lastTimestamp || header.timestamp || null,
    messageCount,
  };
}

/**
 * Lists pi sessions, newest first. When `cwd` is given only sessions from the
 * same project directory are returned (matching pi -r semantics).
 */
function listPiSessions({ sessionsDir, cwd, limit = 40 }) {
  if (!sessionsDir) return [];
  let files;
  try {
    files = fs.readdirSync(sessionsDir, { withFileTypes: true });
  } catch {
    return [];
  }

  const wanted = cwd ? normalizeCwd(cwd) : null;
  const candidates = [];
  for (const dirent of files) {
    if (!dirent.isDirectory()) continue;
    const dir = path.join(sessionsDir, dirent.name);
    let entries;
    try {
      entries = fs.readdirSync(dir);
    } catch {
      continue;
    }
    for (const entry of entries) {
      if (!entry.endsWith('.jsonl')) continue;
      const file = path.join(dir, entry);
      try {
        candidates.push({ file, mtime: fs.statSync(file).mtimeMs });
      } catch {
        // File vanished between readdir and stat; skip it.
      }
    }
  }

  candidates.sort((a, b) => b.mtime - a.mtime);

  const sessions = [];
  for (const candidate of candidates) {
    if (sessions.length >= limit) break;
    const summary = readSessionSummary(candidate.file);
    if (!summary) continue;
    if (wanted && normalizeCwd(summary.cwd) !== wanted) continue;
    sessions.push({
      file: summary.file,
      id: summary.id,
      cwd: summary.cwd,
      name: summary.name,
      preview: summary.preview,
      timestamp: summary.timestamp,
      messageCount: summary.messageCount,
    });
  }
  return sessions;
}

// Mirrors pi's getDefaultSessionDirPath: a cwd encodes into a safe directory
// name under ~/.pi/agent/sessions/.
function sessionDirForCwd(cwd, agentDir) {
  const resolved = String(cwd).replace(/\//g, '\\');
  const safePath = `--${resolved.replace(/^[/\\]/, '').replace(/[/\\:]/g, '-')}--`;
  return path.join(agentDir, 'sessions', safePath);
}

/**
 * Copies a pi session JSONL into the project folder of `newCwd` with the
 * session header's cwd rewritten. pi has no cwd-change command — the session
 * file's recorded cwd is what its tools use — so "move this conversation to
 * another directory" is implemented as copy-with-rewritten-header + switch.
 * The original file is left untouched. Returns the new file path.
 */
function copySessionForCwd(sourceFile, newCwd, agentDir) {
  const raw = fs.readFileSync(sourceFile, 'utf8');
  const lines = raw.split('\n');
  const header = JSON.parse(lines[0]);
  if (header.type !== 'session') {
    throw new Error('not a pi session file');
  }
  header.cwd = String(newCwd).replace(/\//g, '\\');
  lines[0] = JSON.stringify(header);

  const targetDir = sessionDirForCwd(newCwd, agentDir);
  fs.mkdirSync(targetDir, { recursive: true });
  let target = path.join(targetDir, path.basename(sourceFile));
  if (fs.existsSync(target)) {
    target = path.join(
      targetDir,
      `${path.basename(sourceFile, '.jsonl')}-${Date.now()}.jsonl`,
    );
  }
  fs.writeFileSync(target, lines.join('\n'));
  return target;
}

// Lists the immediate subdirectories of a path; at the filesystem root it
// returns the available drive letters (Windows). Used by the phone's
// working-directory picker.
function listDirectories(dirPath) {
  let clean = String(dirPath || '').trim();
  // A bare drive letter means the drive's CURRENT directory on Windows;
  // normalize to the drive root before listing.
  if (/^[a-zA-Z]:$/.test(clean)) clean += '\\';
  if (clean === '' || clean === '/' || clean === '\\') {
    const drives = [];
    for (let letter = 65; letter <= 90; letter += 1) {
      const root = `${String.fromCharCode(letter)}:\\`;
      try {
        if (fs.existsSync(root)) drives.push(root);
      } catch {
        // Unreachable drive letters are skipped.
      }
    }
    return drives;
  }
  let entries;
  try {
    entries = fs.readdirSync(clean, { withFileTypes: true });
  } catch {
    return [];
  }
  return entries
    .filter((d) => d.isDirectory())
    .map((d) => path.join(path.resolve(clean), d.name))
    .sort((a, b) => a.localeCompare(b, 'zh-Hans-CN', { numeric: true }));
}

module.exports = { listPiSessions, resolveSessionsDir, readSessionSummary, copySessionForCwd, sessionDirForCwd, listDirectories };
