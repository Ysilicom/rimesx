#!/usr/bin/env python3
"""Drive Fcitx5 + RIMES Buffer over DBus and the Unix-socket control protocol."""

from __future__ import annotations

import json
import os
import socket
import struct
import sys
import time

import gi

gi.require_version("Gio", "2.0")
from gi.repository import Gio, GLib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dbus_client import Probe

KEYSYMS = {
    "n": 0x006E,
    "i": 0x0069,
    "h": 0x0068,
    "a": 0x0061,
    "o": 0x006F,
    "space": 0x0020,
    "Return": 0xFF0D,
    "B": 0x0042,
}


def socket_path() -> str:
    override = os.environ.get("RIMES_BUFFER_SOCKET")
    if override:
        return override
    runtime = os.environ.get("XDG_RUNTIME_DIR")
    if runtime:
        return os.path.join(runtime, "rimes-buffer.sock")
    return f"/tmp/rimes-buffer-{os.getuid()}.sock"


def ctl(op: str, timeout: float = 3.0) -> dict:
    path = socket_path()
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            sock.settimeout(2.0)
            sock.connect(path)
            payload = json.dumps({"v": 1, "op": op}).encode()
            sock.sendall(struct.pack(">I", len(payload)) + payload)
            header = sock.recv(4)
            if len(header) < 4:
                raise RuntimeError("short header")
            (length,) = struct.unpack(">I", header)
            body = b""
            while len(body) < length:
                chunk = sock.recv(length - len(body))
                if not chunk:
                    raise RuntimeError("short body")
                body += chunk
            sock.close()
            return json.loads(body.decode())
        except OSError as error:
            last_error = error
            time.sleep(0.1)
    raise RuntimeError(f"buffer socket failed: {last_error}")


def process(probe: Probe, ic: Gio.DBusProxy, name: str) -> None:
    keysym = KEYSYMS[name]
    ic.call_sync(
        "ProcessKeyEvent",
        GLib.Variant("(uuubu)", (keysym, 0, 0, False, 0)),
        Gio.DBusCallFlags.NO_AUTO_START,
        2000,
        None,
    )
    ic.call_sync(
        "ProcessKeyEvent",
        GLib.Variant("(uuubu)", (keysym, 0, 0, True, 0)),
        Gio.DBusCallFlags.NO_AUTO_START,
        2000,
        None,
    )
    GLib.MainContext.default().iteration(False)


def main() -> int:
    os.environ.setdefault("RIMES_BUFFER_HEADLESS", "1")
    probe = Probe()
    probe.wait_for_name()
    try:
        probe.controller().call_sync(
            "SetCurrentIM",
            GLib.Variant("(s)", ("rimes",)),
            Gio.DBusCallFlags.NO_AUTO_START,
            2000,
            None,
        )
    except GLib.Error as error:
        print(f"warning: SetCurrentIM failed: {error}", file=sys.stderr)

    _path, ic = probe.create_ic()
    snapshot = ctl("show")
    if not snapshot.get("capturing"):
        print("FAIL: show did not enable capture", file=sys.stderr)
        print(snapshot, file=sys.stderr)
        return 1

    for char in "nihao":
        process(probe, ic, char)
    process(probe, ic, "space")
    probe.pump(0.6)
    snapshot = ctl("status")
    if "你好" not in snapshot.get("staged_text", "") and not any(
        block.get("text") == "你好" for block in snapshot.get("blocks", [])
    ):
        print(f"FAIL: expected staged 你好, got {snapshot!r}", file=sys.stderr)
        return 1
    if "你好" in probe.commits:
        print("FAIL: captured commit leaked to the host", file=sys.stderr)
        return 1
    print("ok: dbus buffer staged 你好")

    snapshot = ctl("send_next")
    probe.pump(0.6)
    if "你好" not in probe.commits:
        print(f"FAIL: send_next did not commit, commits={probe.commits!r}", file=sys.stderr)
        return 1
    print("ok: dbus buffer send_next committed 你好")
    return 0


if __name__ == "__main__":
    sys.exit(main())
