# iOS 1.1.0 (35) physical acceptance — 2026-10-04

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
