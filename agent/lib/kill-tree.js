'use strict';

const { execFile } = require('child_process');

// taskkill works without a console (piped stdio, Windows service in session 0)
// and takes the whole child tree with it, unlike node-pty's kill() or a plain
// child.kill() which only reap the direct child.
//
// A non-zero exit from taskkill does not mean the tree survived: killing a deep
// process tree routinely reports partial failures for children that had already
// exited. Callers therefore only fall back to their platform-specific kill when
// the process is still running after a timeout.
function taskKillTree(pid, onLog) {
  execFile('taskkill', ['/PID', String(pid), '/T', '/F'], { windowsHide: true }, (err) => {
    if (err && onLog) onLog(`taskkill reported: ${err.message}`);
  });
}

module.exports = { taskKillTree };
