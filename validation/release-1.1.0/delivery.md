# 1.1.0 delivery acceptance — 2026-10-04

The initial product packages were built from host `f9ec959d08b6dddd6b314a7d048a9456ab6aeea7`
with official plugins pinned to `c64bc83`. The local macOS preflight package uses that runtime;
the formal CI package is tracked separately.
iOS keyboard updates, the shared mobile settings hero, and Windows receipt
recovery have replacement packages; their acceptance is recorded separately below. Platform build,
installed behavior and public availability are separate.

PR [#49](https://github.com/scholay/rimes/pull/49) merged at
`3fa9e40c9cd3eb74ae9cfb44559ff14f0d5d3eff`. The required main CI and full macOS PR
packaging/upgrade rehearsal passed. The canonical `v1.1.0` tag was created by
`scripts/release.sh --yes stable` only after its dry-run gates passed.

## Public host packages

- [macOS 1.1.0 (84)](https://github.com/scholay/rimes/releases/tag/v1.1.0)
  was published as the stable Latest release on 2026-10-04. All four public
  assets were downloaded without an account token and matched their staged
  bytes, GitHub digests and sizes. The exact public PKG had already passed
  signing, notarization and installation on macOS 27.0 / Apple Silicon;
  all 227 installed payload entries matched.
- [Windows 1.1.0](https://github.com/scholay/rimes/releases/tag/windows-v1.1.0)
  was published on 2026-10-04. All five public assets were downloaded without
  an account token and matched their staged bytes, GitHub digests and sizes.
- [Android 1.1.0](https://github.com/scholay/rimes/releases/tag/android-v1.1.0)
  was published on 2026-10-04 with production build code 12. All five public
  assets were downloaded without an account token and matched their staged
  bytes, GitHub digests and sizes.
- All three tags resolve to the merge commit above. Their `BUILD-INFO.json`
  assets identify the exact runtime build commits and validation scope.
- macOS uses the verified application built by the tagged CI run, signed
  locally without recompilation under an explicit one-time maintainer approval.
  The original waiting workflow was cancelled after a documentation-only merge
  advanced `main` beyond its source; CI signing/publication is not claimed.
  The approved exception, provenance, package digest and same-package acceptance
  are recorded in [macOS acceptance](macos.md). The private local preflight
  draft remains separate; default protected CI release policy is unchanged.

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

Code 12 changes only the settings-home hero, localized brand copy, website/email
links and build number. Core tests,
Release lint, signed APK/AAB builds, APK signature and 16 KB alignment checks
passed again. `adb install -r` preserved data, and package metadata confirmed
1.1.0 / 12. The actual Xiaomi settings home displayed the shared rhino logo on
the left and RIMES plus both localized brand lines on the right; accessibility
readback confirmed both complete English lines. The website opened in the browser, and
the email link opened the system email-app chooser. The keyboard runtime is unchanged.

## iOS distribution

- App Store distribution IPA 1.1.0 (35) passed signature, profile, version, device-family and privacy checks and uploaded successfully.
- App Store Connect processed build `65483d44-bd5c-4efd-9820-3a9da775fc5e` as VALID / APP_STORE_ELIGIBLE.
- The maintainer signed in to App Store Connect. Build 35 was added to the existing RIMES Community group and submitted for external TestFlight review at 2026-10-04T08:37:22Z; UI and API both reported `WAITING_FOR_REVIEW`.
- Automatic tester notification is enabled. The existing public invitation remains https://testflight.apple.com/join/Kdj9RB4q. Submission is not approval or public availability of 1.1.0; existing approved builds remain available meanwhile.
- Follow-up: the maintainer reported weak/delayed haptics. Build 35 was removed from review on the same day; the UI now says `Ready to Submit`. Build 38 was installed with Release optimization. The maintainer confirmed clear slow-typing feedback, responsive fast typing and accepted iOS. Build 39 adds only the settings-home hero and build number; the accepted keyboard implementation is unchanged. The simulator home was visually checked and both brand lines read back. Release-optimized build 39 was installed on Higher's iPhone, with metadata confirming 1.1.0 / 39. Build 40 adds English brand copy, smaller single-line text and website/email links; it is installed with version 1.1.0 / 40. The website link opened in Safari and the bottom contact labels were read back. The exact build 40 App Store IPA passed signature, profile, version and privacy checks and uploaded successfully (delivery UUID `a01bd652-292d-4db3-bdc9-628fd1606b08`). Apple processing, external review and availability remain separate.

Build 40 subsequently completed Apple processing and was submitted to the
existing RIMES Community external group and RIMES Internal group. English and
Simplified Chinese test notes were saved and read back. The UI and API confirmed
`WAITING_FOR_BETA_REVIEW` on 2026-10-04, with automatic tester notification enabled.
Existing approved builds remain available through the same public invitation.

## Remaining user-side observations

- Young's actual Windows upgrade completed and requested a restart to finish runtime installation. No restart or sign-out was forced; daily-use validation after restart remains open.
- macOS package installation and packaged runtime smokes passed. Background GUI automation did not establish a foreground TextEdit IMK round trip, so it is not counted as real-host typing acceptance. The temporary document was closed; no user document was edited. Physical Intel testing remains open.
- Broad app coverage, minimum-OS coverage and day-long soak tests are not claimed by these focused acceptance runs.
