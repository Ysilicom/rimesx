# 1.1.0 delivery acceptance — 2026-10-04

The initial product packages were built from host `f9ec959d08b6dddd6b314a7d048a9456ab6aeea7`
with official plugins pinned to `c64bc83`. macOS retains that runtime.
iOS keyboard updates, the shared mobile settings hero, and Windows receipt
recovery have replacement packages; their acceptance is recorded separately below. Platform build,
installed behavior and public availability are separate.

## Public official plugins

- Plugin PR #1 merged after macOS, iOS, Android, Windows x64/x86 and repository checks passed.
- Release `scholay/rimes-plugins` / `v1.1.0` contains 24 declarative plugin packages, a catalog, native-source archive and SHA256SUMS. All 27 published assets were downloaded from their public URLs and matched local bytes.
- The production macOS HTTPS downloader, generated catalog and installation store were compiled into an isolated acceptance executable with the production RimesCore package. Only preferences, installation directory and unused bundled-resource defaults were isolated; the transport and installer were unchanged.
- All eight optional macOS packages downloaded from the public release, passed host digest/manifest checks, initially remained disabled, loaded after explicit enablement, rejected loading after disablement and uninstalled successfully. This is real public network and store acceptance, not an installed GUI/IMK-process claim.

## Signed Android package

Code 10 keyboard acceptance:

- The long-term production certificate signed APK/AAB version 1.1.0, versionCode 10. APK signature, ZIP alignment and native 16 KB alignment checks passed.
- `adb install -r` upgraded the production package on the connected Xiaomi phone without clearing app data. Package metadata reported 1.1.0 / 10.
- In the production app's disposable typing page, real on-screen Pinyin keys composed `nihao` and selecting the `你好` candidate committed `你好` to the first field.
- Focusing password and private fields kept Buffer gray and inactive; tapping it did not open Buffer. Switching fields retained the previously committed Chinese text.
- The AI settings page exposes separate HTTPS API address, model and API-key fields. Real DeepSeek request coverage is recorded in the Android debug instrumentation report; it is not misrepresented as a new paid request from the signed release UI.
- The original default and enabled input-method settings were restored after acceptance. No phone data or credentials were cleared.

Code 11 changes only the settings-home hero and build number. Core tests,
Release lint, signed APK/AAB builds, APK signature and 16 KB alignment checks
passed again. `adb install -r` preserved data, and package metadata confirmed
1.1.0 / 11. The actual Xiaomi settings home displayed the shared rhino logo on
the left and RIMES plus both Chinese brand lines on the right; accessibility
readback confirmed both complete lines. The keyboard runtime is unchanged.

## iOS distribution

- App Store distribution IPA 1.1.0 (35) passed signature, profile, version, device-family and privacy checks and uploaded successfully.
- App Store Connect processed build `65483d44-bd5c-4efd-9820-3a9da775fc5e` as VALID / APP_STORE_ELIGIBLE.
- The maintainer signed in to App Store Connect. Build 35 was added to the existing RIMES Community group and submitted for external TestFlight review at 2026-10-04T08:37:22Z; UI and API both reported `WAITING_FOR_REVIEW`.
- Automatic tester notification is enabled. The existing public invitation remains https://testflight.apple.com/join/Kdj9RB4q. Submission is not approval or public availability of 1.1.0; existing approved builds remain available meanwhile.
- Follow-up: the maintainer reported weak/delayed haptics. Build 35 was removed from review on the same day; the UI now says `Ready to Submit`. Build 38 was installed with Release optimization. The maintainer confirmed clear slow-typing feedback, responsive fast typing and accepted iOS. Build 39 adds only the settings-home hero and build number; the accepted keyboard implementation is unchanged. The simulator home was visually checked and both brand lines read back. Release-optimized build 39 was installed on Higher's iPhone, with metadata confirming 1.1.0 / 39. The replacement archive/upload is pending.

## Remaining user-side observations

- Young's actual Windows upgrade completed and requested a restart to finish runtime installation. No restart or sign-out was forced; daily-use validation after restart remains open.
- macOS package installation and packaged runtime smokes passed. Background GUI automation did not establish a foreground TextEdit IMK round trip, so it is not counted as real-host typing acceptance. The temporary document was closed; no user document was edited. Physical Intel testing remains open.
- Broad app coverage, minimum-OS coverage and day-long soak tests are not claimed by these focused acceptance runs.
