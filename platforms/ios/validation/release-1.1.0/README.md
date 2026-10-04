# iOS 1.1.0 acceptance — 2026-10-04

## Feedback regression follow-up (36 → 37)

The maintainer reported delayed/weak haptics in every RIMES layout on build 35.
Build 36 removed repeated package reads from typing renders and moved Delete's
pulse before text mutation. Its Release-optimized device install improved the
feel, but the maintainer still observed weak slow typing and lag at speed.
This is an open physical acceptance issue, not a completed performance claim.

Build 37 additionally prepares the haptic engine when the keyboard appears and
after each pulse, gives key-down feedback priority over preview/release pulses,
coalesces only near-simultaneous (8 ms) presses, and uses the normal light impact
instead of scaling it to 0.55. Haptics precede audio and typing-state updates.
Candidate mode hides the settings and Buffer side buttons and uses their width;
the controls return when the candidate/composition state ends.

- Hosted iOS: 153 tests, 1 existing explicit skip, 0 failures.
- Shared: 79 tests, 0 failures, including rapid presses after preview/release.
- Ten layout/plugin render cases read zero plugin packages (previously 5–6 per
  render). See [timing and scope](ios-feedback-regression.json); these are
  simulator CPU timings, not measured physical haptic latency.
- Release-optimized, development-signed 1.1.0 (37) installed on Higher's iPhone
  without clearing data; device metadata confirmed version/build. Subjective
  haptic acceptance is pending.
- Build 35's external TestFlight review was withdrawn after the regression;
  App Store Connect reports `Ready to Submit`. No replacement is submitted yet.
- Explicit plugin reinstall now repairs corrupt/obsolete receipts while leaving
  the plugin disabled and rejecting downloads started before the repair.

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
