# iOS 1.1.0 acceptance — 2026-10-04

## Keyboard follow-up (36 → 39)

The maintainer reported delayed/weak haptics in every RIMES layout on build 35.
Build 36 removed repeated package reads from typing renders and moved Delete's
pulse before text mutation. Its Release-optimized device install improved the
feel, but the maintainer still observed weak slow typing and lag at speed.
On build 38 the maintainer confirmed clear slow-typing feedback and responsive
fast typing, then explicitly accepted iOS. This is subjective device acceptance,
not a measured Taptic Engine latency benchmark.

Build 37 additionally prepares the haptic engine when the keyboard appears and
after each pulse, gives key-down feedback priority over preview/release pulses,
coalesces only near-simultaneous (8 ms) presses, and uses the normal light impact
instead of scaling it to 0.55. Haptics precede audio and typing-state updates.
Build 38 follows the maintainer's revised candidate-row request: only Settings
is hidden during composition/candidate selection; Buffer stays available on the
right. Empty rows restore Settings without moving the keys.

- Hosted iOS build 38: 163 tests, 1 existing explicit skip, 0 failures.
- Shared: 79 tests, 0 failures, including rapid presses after preview/release.
- Ten layout/plugin render cases read zero plugin packages (previously 5–6 per
  render). See [timing and scope](ios-feedback-regression.json); these are
  simulator CPU timings, not measured physical haptic latency.
- Release-optimized, development-signed 1.1.0 (38) installed on Higher's iPhone
  without clearing data; device metadata confirmed version/build. Subjective
  haptic acceptance passed via the maintainer's direct feedback.
- Build 35's external TestFlight review was withdrawn after the regression;
  App Store Connect reports `Ready to Submit`. No replacement is submitted yet.
- Explicit plugin reinstall now repairs corrupt/obsolete receipts while leaving
  the plugin disabled and rejecting downloads started before the repair.

Build 38 also adds an opt-in ordinary-keyboard setting in the app's layout page
and the keyboard's Settings menu. Hold a key for 320 ms, then slide upward at
least 18 pt and release to insert its corner label. Q–P map to 1–0; the other
letters and nine-key groups map to punctuation (Chinese/English forms follow
the input mode). Chord layouts, including their EN/Shift modes, are excluded.
Taps, early slide corrections, canceled touches and host/mode changes retain
their previous behavior. The literal path settles composition before inserting
the symbol into the host or Buffer, so a digit cannot select candidate number 4
instead of inserting `4`. App gesture edits have a separate revision so they do
not reset a keyboard-local layout choice.

Imported Rime packages now have a Delete button and confirmation in the app.
Removing the active package clears that selection; on the next presentation the
keyboard restores the prior built-in input and chord geometry. Removal moves
resources aside before updating the catalog and rolls them back if persistence
fails. Missing-resource entries can also be removed. Learned dictionaries and
unrelated packages/preferences are retained. Tests cover disk-write failure,
unowned paths, symbolic links and actual Rime fallback after package removal.

The simulator's actual keyboard touch path was also exercised: timed upward
drags on Q and A inserted exactly `1，` into the app's test text field. The app
toggle was read back enabled and the keyboard rendered the matching corner
labels. These are functional simulator checks, not physical haptic measurements.

Build 39 changes only the settings-home hero and build number after build 38's
device acceptance. The home now has the shared rhino logo on the left and RIMES
with the maintainer's two Chinese brand lines on the right, matching Android.
No keyboard implementation changed after acceptance. The Release-optimized
build 39 was installed on Higher's iPhone without clearing data; device metadata
confirmed 1.1.0 / 39. The simulator home displayed both brand lines without
truncation. Android code 11 uses the identical logo bytes and passed a physical
settings-home check on the connected Xiaomi phone.

## Earlier build 35 device coverage

Signed development build on iPhone 15 Pro, iOS 26.7.1, team 585J2TL9U6.
This is device acceptance of the app and keyboard; App Store export, upload,
and TestFlight approval are separate delivery checks.

- Physical keyboard: nine-key Pinyin `64426` produced `ni’hao`; Space committed `你好` into the app's typing field. The user's original Chord default was restored.
- Engine: Pinyin, Ziranma and Wubi completed their device smoke; key processing was 0.17–2.06 ms, initialization 31.48 ms. See the engine receipt.
- App Group and shared Keychain: the signed app wrote an ephemeral random test key, the real keyboard extension read it and wrote a reply, and the app verified the reply. All checks passed with Full Access. Temporary keys and request files were removed; existing credentials were not changed. The opt-in probe is DEBUG-only.
- Official plugins: six legacy plugins remained enabled. Disabling Polisher in the app made its keyboard button gray and inactive; tapping it produced no action. Polisher was then restored to enabled.
- Secure field: opening an empty new service's API-key SecureField exposed the Apple password keyboard in XCTest (Passwords, standard letter keys, Dictate; no RIMES AI/Buffer controls). The form was cancelled without saving or sending a request. The device screenshot omits the secure keyboard, so the accessibility observation is the evidence for keyboard replacement.
- Keyboard layout: final measured height was 252 pt, equal to the requested height, with no excess host space. Controller presentation was 540 ms and sampled memory peaked at 23.74 MB. The input metric has only one sample (62.97 ms); it is not a statistically useful P95, OS launch latency or display-frame benchmark. See the complete metrics receipt.

Acceptance covers the above explicit cases on this phone. It does not claim broad app coverage, a day-long soak, minimum-OS coverage, or Apple review approval.
