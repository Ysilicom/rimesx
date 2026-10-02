# Android keyboard layout validation — 2026-10-02

Version **0.1.0-dev.4 / code 4**, package `org.scholay.rimes.android.debug`.
Application source: `889d4208ba66d11d4ef34c8a9b13dccac7bae14a`.
Branch `codex/android-chinese-input`; local commits only, no push or publication.
Previous Chinese-input acceptance is preserved in [the dev.3 report](validation/2026-10-01.md).

## Delivered layout and style

- Equal-width QWERTY letter keys, staggered rows, separate Shift/Delete, wide
  Space and editor-labelled Return. Portrait touch rows are 56 dp; landscape
  rows are 36 dp with preedit inside the toolbar to preserve host space.
- Real nine-key Pinyin with digit-derived offline Rime candidates, explicit
  syllable choices, separator/backspace behavior, and private/off-learning variants.
- Number, symbol and 30-emoji pages; existing native delivery and Buffer routing.
- 18 iOS-derived palettes with light/dark colors and visible pressed caps.
  Layout/theme choices are available in the keyboard and Setup and persist.
- Candidate changes reuse the letter-key surface. Candidate text keeps natural
  width and scrolls; the idle punctuation rail occupies the same row.

The caps draw in view coordinates, compensating for single-line TextView scrolling.
Physical screenshots caught this Android-specific drawing issue; a bitmap regression
now checks normal/pressed colors even when the label layout is scrolled.

## Automated evidence

| Check | Result |
|---|---|
| JVM | 18 tests pass; Buffer, target epochs, geometry and nine-key syllable constraints |
| Geometry | All five pages checked at 280–1,200 dp in both orientations; no intersecting or out-of-bounds touch cells; all 26 letters equal width |
| Build / Lint | APK and both test APKs built; app Lint 0 errors / 5 warnings, validation host 0 errors / 5 warnings |
| Packaged resources | 19 hashed offline files; normal/private nine-key schemas and syllable algebra verified; original notices retained |
| Native ABIs | arm64-v8a on the phone and x86_64 in the AOSP Bionic Linux container both pass `nihao → 你好`, `64426 → 你好`, `nihk → 你好`, `wq → 你` |
| Learning | Both native contracts retain learned entries across restart and use no-learning variants; x86_64 additionally compares exact exports before/after private selections |
| Packaged JNI | Chinese/non-BMP/NUL round trips, UTF-16 preedit caret, normal/private nine-key commit, password/private policy pass on the phone |
| Palette rendering | All 18 palettes checked in light/dark and pressed/unpressed states, including a scrolled label layout |
| Alignment | Both ELF LOAD segments and final APK pass 16 KB alignment checks |

The five app Lint warnings are the newer Gradle notice, pre-Android-12 backup-rule
advisory, development icon, and two programmatically constructed views with explicit
callbacks. No errors or newly suppressed warnings. CI includes the new resources,
geometry tests and four-schema native contract through its Android path trigger.
CI was updated locally; this report does not claim a hosted CI run.

## Physical Android 16 phone

Device model `25098PN5AC`, API 36, arm64-v8a, **4 KB** memory pages,
three-button navigation. Only synthetic test text was used.

- **388 assertions** with dark appearance / normal fonts.
- **388 assertions** with light appearance / **1.3× system fonts**.
- **301 assertions** for the existing native/WebView input contract.

The layout runs include actual injected touch coordinates for `nihao` and Space,
plus native accessibility activation of the real keyboard controls. The device
crash buffer has no entries for the 19:21–19:42 validation interval. They check
26-key width/height alignment on the actual display, Return above navigation,
9-key conversion, spelling selection/unpinning, numbers, symbols, non-BMP emoji
and deletion, raw settlement on layout change, Buffer preservation across layouts,
and portrait/landscape input. They do not use a text-injection IME or mock engine.

The existing contract passes first/non-first/paged candidates, all three Chinese
schemes, punctuation, raw Return, delete, fast ordered keys, Buffer isolation and
exact insertion, host selection changes, private fields, hide/reopen, orientation
and an independent WebView. The OEM secure-keyboard password case remains skipped
in this host; platform password policy is checked by JNI instrumentation. The dev.3
report separately records the earlier Setup-password UI observation.

Setup's appearance chooser was opened and scrolled through its last palette row.
Selecting nine-key and the frog palette survived a process restart (read back as
`nineKey` / `noto-1f438` and visibly shown in the keyboard). Final preferences return
to **26-key / native palette / natural-code Shuangpin / learning on**, preserving
the user's pre-test scheme and learning preference.

## Installation and artifact

`RIMES-Android-0.1.0-dev.4-889d420.apk` — 12,913,729 bytes.

SHA-256:
`81d17a40629331528b56b4797adddf84240b2f63ad794b2411ba12d1848a6c71`

Installed with the existing development signature using `adb install -r`, never
uninstalled or cleared. With the IME quiesced, all **21 user-dictionary/preference
file hashes** remained identical across installation. Earlier live-process hashes
changed only in ordinary LevelDB metadata/log rotation and are not counted as this
upgrade assertion. The installed base APK was pulled and matches the artifact
byte for byte; all 19 manifest entries were verified inside the final APK.

All six saved device settings were restored and read back: original RIMES default
IME, portrait lock, rotation, 120,000 ms screen timeout, font scale 1.0 and night
mode 2. The independent validation host was closed and its native test files under
`/data/local/tmp/rimes-dev4-engine` removed. No iOS/main-worktree files were edited.

The ignored `.build/android-dev4-validation/` directory contains the APK/hash,
`DELIVERY.json`, build/Lint references, engine/JNI/layout/input logs, upgrade and
restoration readbacks, crash log, and dark/light/large-font phone captures.
`PREVIEW.html` is a local gallery of unmodified physical-device screenshots.

## Limits

Old Android versions, other manufacturers, gesture navigation, larger-than-1.3×
fonts, screen-reader usability, x86_64 emulator UI and actual 16 KB runtime remain
unverified. The previous 30-minute dev.3 soak was **not repeated** for this layout
revision; it is not represented as dev.4 evidence. Custom layout importing, chord
and slide gestures, animated pets, full Buffer editing, AI and translation remain
outside this keyboard-layout revision.
