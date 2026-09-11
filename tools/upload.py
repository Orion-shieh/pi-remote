#!/usr/bin/env python3
"""Upload a local file or directory tree to the relay server over SFTP.

Usage:
    PIPASS=<password> python tools/upload.py <local-path> <remote-path>

Remote directories are created with `mkdir -p` over an exec channel, so the
SFTP layer only ever has to write files into directories that already exist.
"""

import os
import posixpath
import sys

import paramiko

HOST = os.environ.get("PI_HOST", "8.138.112.73")
USER = os.environ.get("PI_USER", "root")
PORT = int(os.environ.get("PI_PORT", "22"))

SKIP_DIRS = {"node_modules", ".git", "__pycache__"}


def collect(local, remote, out):
    if os.path.isdir(local):
        for name in sorted(os.listdir(local)):
            if name in SKIP_DIRS:
                continue
            collect(os.path.join(local, name), posixpath.join(remote, name), out)
    else:
        out.append((local, remote))


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2

    password = os.environ.get("PIPASS")
    if not password:
        print("PIPASS env var is required", file=sys.stderr)
        return 2

    local, remote = sys.argv[1], sys.argv[2]
    if not os.path.exists(local):
        print(f"local path not found: {local}", file=sys.stderr)
        return 2

    files = []
    collect(local, remote, files)
    if not files:
        print("nothing to upload", file=sys.stderr)
        return 1

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(
        hostname=HOST,
        port=PORT,
        username=USER,
        password=password,
        timeout=15,
        allow_agent=False,
        look_for_keys=False,
    )

    try:
        dirs = sorted({posixpath.dirname(remote_path) for _, remote_path in files})
        for directory in dirs:
            _, stdout, stderr = client.exec_command(f"mkdir -p {directory}")
            code = stdout.channel.recv_exit_status()
            if code != 0:
                print(f"mkdir -p {directory} failed: {stderr.read().decode()}", file=sys.stderr)
                return 1
        print(f"created {len(dirs)} remote directory/ies")

        sftp = client.open_sftp()
        for local_path, remote_path in files:
            sftp.put(local_path, remote_path)
            size = os.path.getsize(local_path)
            print(f"  {remote_path}  ({size} bytes)")
        sftp.close()
    finally:
        client.close()

    print(f"upload complete: {len(files)} file(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
