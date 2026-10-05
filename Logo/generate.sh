#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

# Export every platform from the checked-in artwork, including files consumed
# by builds on non-macOS hosts. No image-generation service is used at build time.
SWIFT_BIN="${RB_SWIFT_BIN:-$(xcrun --find swift)}"
tmpdir="$(mktemp -d "$ROOT/.generate-tmp.XXXXXX")"
trap 'rm -rf "$tmpdir"' EXIT
export SWIFT_MODULE_CACHE_PATH="$tmpdir/swift-module-cache"
export CLANG_MODULE_CACHE_PATH="$tmpdir/clang-module-cache"
"$SWIFT_BIN" "$ROOT/export-icons.swift" "$ROOT/.." "$tmpdir"

if ! iconutil -c icns AppIcon.iconset -o AppIcon.icns 2>"$tmpdir/iconutil.log"; then
  # Some command-line macOS hosts reject otherwise-valid RGBA iconsets.
  python3 - <<'PY'
from pathlib import Path
import struct
entries = [
    ("icp4", "icon_16x16.png"), ("ic11", "icon_16x16@2x.png"),
    ("icp5", "icon_32x32.png"), ("ic12", "icon_32x32@2x.png"),
    ("icp6", "icon_32x32@2x.png"), ("ic07", "icon_128x128.png"),
    ("ic13", "icon_128x128@2x.png"), ("ic08", "icon_256x256.png"),
    ("ic14", "icon_256x256@2x.png"), ("ic09", "icon_512x512.png"),
    ("ic10", "icon_512x512@2x.png"),
]
body = b""
for tag, name in entries:
    png = (Path("AppIcon.iconset") / name).read_bytes()
    body += tag.encode("ascii") + struct.pack(">I", len(png) + 8) + png
Path("AppIcon.icns").write_bytes(b"icns" + struct.pack(">I", len(body) + 8) + body)
PY
fi

# ICO is a directory of PNG payloads; image rendering happens in AppKit.
python3 - "$tmpdir" <<'PY'
from pathlib import Path
import struct
import sys
sizes = (16, 20, 24, 32, 40, 48, 64, 128, 256)
payloads = [(Path(sys.argv[1]) / f"windows-{size}.png").read_bytes() for size in sizes]
offset = 6 + 16 * len(sizes)
directory = b""
for size, png in zip(sizes, payloads):
    directory += struct.pack("<BBBBHHII", size % 256, size % 256, 0, 0, 1, 32, len(png), offset)
    offset += len(png)
target = Path("../platforms/windows/native/resources/rimes.ico")
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(struct.pack("<HHH", 0, 1, len(sizes)) + directory + b"".join(payloads))
PY

echo "Exported the shared rhino icon for macOS, iOS, Android, Windows and Linux."
