#!/usr/bin/env python3
"""Compile/run capability and resource-limit checks against the built host engine."""
import os, pathlib, subprocess, tempfile
HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[2]
WORK = ROOT / 'Vendor/ios-build'
os.environ.setdefault('DEVELOPER_DIR', '/Applications/Xcode.app/Contents/Developer')
binary = WORK / 'lua-sandbox-smoke'
subprocess.run(['xcrun', 'clang++', '-std=c++17', '-I'+str(HERE/'lua-sandbox'),
                '-I'+str(WORK/'librime/src'), '-I'+str(WORK/'librime/plugins/lua/thirdparty/lua5.4'),
                str(HERE/'lua-sandbox/smoke.cc'), str(WORK/'host/rime/lib/librime.dylib'),
                '-Wl,-rpath,'+str(WORK/'host/rime/lib'), '-o', str(binary)], check=True)
with tempfile.TemporaryDirectory(prefix='rimes-lua-sandbox-') as root:
    subprocess.run([str(binary), root], check=True, timeout=20)
