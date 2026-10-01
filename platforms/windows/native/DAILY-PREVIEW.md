# Windows daily-use preview implementation

Branch: `codex/windows-daily-buffer`, based on main `b02fe9c`, with Windows data and Session 0 fixes cherry-picked as `f23d4c9`, `689e84d`. macOS/iOS/Android sources are not part of this change.

## Boundaries

- Protocol v2 uses authenticated per-user/per-logon named pipes, with an independent long-poll notification connection. A target is a verified peer PID, globally unique Broker session, and TSF context/focus generation.
- TSF owns actual document edits. Ordinary snapshots apply commit/preedit/end in one edit session. Deferred edits retain their COM owners and a revocable context lease. Context removal, IME deactivation and Buffer mode changes retire old leases.
- Buffer delivery is uniquely identified, rejected on target changes, and acknowledged only after `InsertTextAtSelection` succeeds in `DoEditSession`. Enqueueing an asynchronous edit is never an acknowledgement. Lost confirmation freezes delivery; no automatic resend.
- Return ownership survives context changes until key-up. Repeats cannot leak to the host. Tap sends one block, a 1.2-second hold sends the current queue, with the next block sent only after acceptance.
- Password/private input scopes, disabled/empty TSF compartments, read-only and secure contexts are excluded. Buffer observes TSF selection changes as well as context changes. A non-activating panel cannot by itself establish a new insertion target.
- Broker serializes librime, owns a bounded in-memory Buffer, persists only settings, and runs WinHTTP on a background worker. Generation and translation share one configured endpoint; source/config/model revisions are frozen and stale chunks rejected. Incremental translation preserves original-to-result block associations.
- Package versions are immutable. x86 TSF locates the adjacent x64 Broker. Registrar has transactional keyboard and display-attribute categories. Installer checks hashes, architecture, dependencies and DLL occupancy, restores previous registration on failure, and retains user data.

## Verification layers

Baseline `689e84d`: Young Windows 11 build 22631, VS 2022 17.14 / MSVC 14.44 / SDK 10.0.26100 / CMake 3.31.6. Both x64/x86 builds, CTest, full product data deployment and fake-TSF/real-Broker typing passed in an isolated directory. Baseline reports remain in `D:\AI\rimes-windows-daily-20261001`.

Development checks include portable Buffer state-machine tests; runtime peer/acknowledgement/Return tests; protocol and SSE boundary tests; deferred-context revocation in fake TSF; a loopback WinHTTP transport suite for success, disconnect, malformed events, HTTP errors, cancellation, timeout and offline; full-data probes for five schemes and traditional output. Final results and known gaps must be recorded below after running the exact final commit.

## Acceptance status

Candidate `7c33090` passed both MSVC architectures, all 11 CTest groups per architecture, product-data probes, loopback API transport, fake-TSF integration, and 19 real installation-lifecycle checks on Young. Existing user database files and language preferences were preserved. Both TSF architectures are registered, with sign-out required because a desktop process still maps the original DLL.

A successful build, CTest or registration does **not** certify daily-use readiness. Windows App control repeatedly timed out; actual x64/x86 hosts, Notepad, Edge input/contenteditable, VS Code, WeChat draft, installed Office, DPI/multi-monitor, lock/resume, elevated windows, broker restart, a configured API provider and at least one working day of trial remain unverified. See [the dated acceptance record](ACCEPTANCE-20261001.md) for exact artifacts and evidence. No production release or signing is claimed.

## Reproduction

`Build-RimesWindows.ps1 -Architecture x64 -Configuration Release` and x86 run CTest. `RimesTsfE2E.exe` needs a real Broker using isolated e2e schemas. `RimesProductProbe.exe <rime.dll> <shared> <isolated-user> <logs>` verifies product schemas. `python tests/test_provider_transport.py <RimesProviderProbe.exe>` uses disposable loopback endpoints and dummy keys; it never reads Credential Manager.

Use `New-RimesNativePackage.ps1` with an exact source commit, verified complete shared data, pinned x64 librime and a new output directory. Package README documents installation, explicit Buffer operation, credentials, upgrade, rollback and uninstall.
