#!/usr/bin/env python3
"""Run a command on the relay server over SSH.

Usage:
    PIPASS=<password> python tools/remote.py "shell command"

Host/user come from env PI_HOST / PI_USER (defaults: 8.138.112.73 / root).
Password must be supplied via the PIPASS env var, never as an argument.
"""

import os
import sys

import paramiko

HOST = os.environ.get("PI_HOST", "8.138.112.73")
USER = os.environ.get("PI_USER", "root")
PORT = int(os.environ.get("PI_PORT", "22"))


def main() -> int:
    password = os.environ.get("PIPASS")
    if not password:
        print("PIPASS env var is required", file=sys.stderr)
        return 2

    command = sys.argv[1] if len(sys.argv) > 1 else "echo no-command"

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        client.connect(
            hostname=HOST,
            port=PORT,
            username=USER,
            password=password,
            timeout=15,
            allow_agent=False,
            look_for_keys=False,
        )
    except Exception as exc:  # noqa: BLE001
        print(f"connect failed: {exc}", file=sys.stderr)
        return 1

    try:
        _, stdout, stderr = client.exec_command(command, timeout=600)
        out = stdout.read().decode("utf-8", errors="replace")
        err = stderr.read().decode("utf-8", errors="replace")
        code = stdout.channel.recv_exit_status()
        if out.strip():
            print(out.rstrip())
        if err.strip():
            print("--- stderr ---", file=sys.stderr)
            print(err.rstrip(), file=sys.stderr)
        print(f"--- exit {code} ---")
        return code
    finally:
        client.close()


if __name__ == "__main__":
    sys.exit(main())
