# Windows foundation validation — 2026-09-29

Native source: `1b2a86a0ddb87d6e0743874df6f797eccc31d364`.
Windows build 22631, Visual Studio 2022 Build Tools, bundled CMake 3.31.6,
Release x64 and x86, Python 3.12. Tests ran over authenticated SSH in Session 0.
No input-method registration, autostart change, installer or desktop input-source
change was performed.

## Passed

| Check | x64 | x86 |
|---|---|---|
| Native Release build | Passed | Passed |
| Eight CTests, including protocol regression | 8/8 | 8/8 |
| Pinned librime 1.17.0 archive hash | Matched lock | Matched lock |
| Complete product shared-data engine smoke | Passed | Passed |
| In-process TSF + real Broker + librime typing | Passed | Passed |

The product-data smoke used all 59 staged files (65,662,707 bytes, plus manifest)
and a new test user directory per run. It exercised the default scheme through
key-down/key-up, composition, candidates and Space commit; both reported maximum
composition bytes 4 and current-page candidates 9. It does not individually
certify every product schema or real application host.

The TSF test used the repository's isolated `testdata/e2e` schema and
`RimesTsfE2E.exe`: preedit, candidate contents, `nihao` + Space → `你好`, number
selection, paging and Escape. The TSF context is a **Fake TSF** host; Broker,
TextService, candidate code and librime are real. Each test-owned Broker was
stopped afterward. This is not Notepad, browser, Office or Electron acceptance.

The shared-data packager's nine regression tests passed on macOS and Windows at
`1a627909cc4cbd77c3e8f7ac71179b1eaffa566c`; those files are unchanged in the native
source above. The existing nine platform-preview tests also passed on macOS.
Shared-data inventory and hashes were checked again on Windows after the update.

## Regression found and fixed

The initial TSF test could not connect in Session 0 even with the existing
`RIMES_ALLOW_SESSION_0=1` test opt-in. Hello DTO validation rejected the valid
Windows OS session identifier `0`. The new regression reproduced that failure
before the change and passes afterward, including rejection of zero process IDs.

The protocol now represents OS Session 0. Endpoint opt-in, same-user/session
pipe verification and non-zero **Rime input-session handles** retain their checks.
Both architectures were rebuilt and retested after the fix; no security setting
or host trust check was disabled to obtain the result.

## Build and runtime setup

```powershell
./platforms/windows/scripts/Build-RimesWindows.ps1 -Architecture x64 -Configuration Release -Parallel 4
./platforms/windows/scripts/Build-RimesWindows.ps1 -Architecture x86 -Configuration Release -Parallel 4
python ./platforms/windows/scripts/prepare-native-data.py verify ./shared
```

`RimesEngineSmoke.exe` received the matching pinned DLL, staged shared-data root,
fresh user-data path and separate log path. OpenCC data came from source commit
`556ed22496d650bd0b13b6c163be9814637970ae` (1.1.9); the staging manifest records
its config/table/license hashes.

The Windows host could not download GitHub release assets directly and did not
have 7-Zip. The exact archives in `librime-windows.lock.json` were downloaded on
the Mac, hash-checked, transferred over strict SFTP, checked again on Windows,
and extracted with the installed CMake's `-E tar xf`. Source transfers were also
hashed. No dependency versions were substituted.

The [saved result](validation/2026-09-29.json) contains native artifact hashes
and test results. Eight native binaries were retrieved and each local SHA-256
matched the remote record. Full logs and task scripts are local artifacts under
`.build/validation/`; neither binaries nor user dictionaries are committed.

## Still required

Real desktop hosts, all schemes and chords, focus/secure-field handling, DPI,
sleep/lock, install/upgrade/uninstall and the macOS-equivalent Buffer/Capsule/
Mailbox remain separate acceptance work. Follow [MANUAL-TEST.md](MANUAL-TEST.md)
and [the platform roadmap](../../../PLATFORM-ROADMAP.md).
