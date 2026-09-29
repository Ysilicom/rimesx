# RIMES Native Windows Foundation

This directory contains the native Windows implementation of RIMES. It is
separate from the Weasel data preview in the parent directory.

The in-process TSF DLL stays small:

- `RimesTsf.dll` is the Windows Text Services Framework boundary. It now
  starts an inline composition (preedit + dotted display attribute), shows a
  DPI-aware candidate window near `ITfContextView::GetTextExt`, and commits
  through a write edit session.
- `RimesBroker.exe` is a per-user process that owns the single `librime`
  instance. Missing path flags resolve to files next to the executable or to
  `%LOCALAPPDATA%\RIMES` / `%APPDATA%\RIMES`. `--install-autostart` writes a
  current-user Run key. The TSF client launches a sibling Broker on first use
  and reconnects after a crash.
- `RimesRegistrar.exe` registers or removes the TSF text service.
- `src/core` is the bounded named-pipe protocol shared by TSF and Broker.
- `librime/` pins official `rime.dll` (x64 and x86) for CI and local smokes.

TSF still must not load `librime` or do network I/O. Broker failure fail-opens.

This is not a signed Windows release. Buffer, Capsule, Mailbox, MSI, and
SmartScreen are out of scope.

## Interaction (macOS-aligned)

Where the TSF host cooperates, typing matches the macOS RIMES controller:

- Latin keys while composing stay inside the engine.
- Space commits the highlighted candidate.
- `1`–`9` select by label.
- PageDown / PageUp page the menu.
- Escape cancels and commits nothing.

The candidate panel is a `WS_EX_NOACTIVATE` topmost tool window so it cannot
steal focus from the host.

## Build

Use a Visual Studio 2022 developer environment with the x86/x64 C++ workload
and a current Windows SDK:

```powershell
cmake --preset windows-x64
cmake --build --preset windows-x64-release
ctest --preset windows-x64-release

cmake --preset windows-x86
cmake --build --preset windows-x86-release
ctest --preset windows-x86-release
```

Both architectures are required. A 64-bit TSF DLL cannot be loaded by a
32-bit application, and vice versa.

CTest covers protocol, engine unit, key translation, broker options, candidate
layout, registrar metadata, the pipe endpoint, and inferred default paths. It
does not load `rime.dll`.

## Pinned librime and typing tests

```powershell
..\scripts\Fetch-RimesLibrime.ps1 -Architecture x64
..\tests\Invoke-RimesImeE2E.ps1 -Architecture x64 -ProbeDesktop
```

`Invoke-RimesImeE2E.ps1` starts a real Broker against the isolated
`testdata/e2e` table schema and drives the real `TextService` through a Fake
TSF stack (`ITfThreadMgr` / `ITfContext` / `ITfComposition`). It asserts
preedit, candidate contents, `nihao`+Space → `你好`, number selection, paging,
and Escape.

Hosted GitHub `windows-2022` runners usually have a logon session and can
launch Notepad, but they are **not** a reliable interactive IME desktop: no
Chinese language pack, no user IME switch, and TSF often never attaches to a
SendInput host. The required CI assertions are therefore in-process. The
script records a desktop probe for honesty. Real-host coverage is
[MANUAL-TEST.md](MANUAL-TEST.md).

PR-time evidence is the non-required **Windows IME** workflow
(`.github/workflows/windows-ime.yml`). The older **Windows Native Foundation**
workflow stays schedule/manual only so it cannot join the macOS release gate.

See [librime/README.md](librime/README.md) for URLs and SHA-256.

## Daily-use layout

With no path flags, Broker uses:

| Role | Default |
| --- | --- |
| `rime.dll` | `<exe>\rime.dll`, else `%LOCALAPPDATA%\RIMES\runtime\rime.dll` |
| Shared data | `<exe>\shared`, else `%LOCALAPPDATA%\RIMES\shared` |
| User data | `%APPDATA%\RIMES` |
| Logs | `%LOCALAPPDATA%\RIMES\logs` |

```powershell
.\RimesBroker.exe --print-paths
.\RimesBroker.exe --install-autostart
.\RimesBroker.exe --remove-autostart
```

First TSF activation also launches `RimesBroker.exe` from the same directory
as `RimesTsf.dll` when the pipe is missing.

## Safety boundaries

- The TSF DLL must not perform network requests or load `librime`.
- Pipe frames and every variable-length field are bounded before allocation.
- Broker failure or protocol incompatibility must fail open for typing.
- Secure-mode TSF hosts never connect to the Broker.
- Registration identifiers in `src/tsf/Guids.h` are release identity.
- Registration changes input profiles and must only run on an authorized
  test machine.

## Later-step blockers

- Product `rime_ice` still needs the RIMES lua tree in shared data; the
  official MSVC `rime.dll` embeds librime-lua but not those scripts.
- Display-attribute underline depends on the host querying
  `ITfDisplayAttributeProvider`. The registrar does not add a new TSF
  category, so some hosts may skip the dotted underline.
- Buffer, Capsule, and Mailbox have no Windows frontend yet.
- There is no signed installer; SmartScreen will warn on downloaded binaries.
- Dual-architecture registration remains a manual/elevated step.
