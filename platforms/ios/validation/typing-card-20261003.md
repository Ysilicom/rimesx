# Optional stats image — 2026-10-03, build 32

The user clarified that the primary design exploration is an Emoji text matrix.
PNG remains an optional export format. The existing stats-tap text insertion is
preserved; Default Buffer's gear menu opens Stats image (optional).

## Behavior

- Generate an opaque 1024×1024 PNG, with square blocks, complete drawn frame,
  aggregate metrics and the GitHub address; no pets or typed text.
- The hint spans the bottom of the keyboard preview, separate from the image and
  action column. Save to Photos is primary, Save to app secondary, Copy image in
  More. Main app also has Save to Photos, system sharing and file export.
- An explicit Photos save requests `.addOnly` authorization. Neither target asks
  for library read permission. Only successful PhotoKit completion reports saved.
  Denied/restricted access and write errors surface failures.
- Keyboard export requires Full Access. The latest explicitly saved PNG is kept
  in the App Group, excluded from backup. No typed text is written to disk.
- Preview pauses automatic insertion without consuming Buffer or session totals.
  Normal background/session privacy clearing remains active, including during
  system interruptions; an explicitly exported PNG survives in the app.

## Verification

- Simulator XCTest: 69 tests executed, 68 passed, 1 existing skip, zero failures.
  Suites: TypingStatsCardTests, KeyboardInteractionTests, KeyboardReturnTests,
  KeyboardSizingTests. Log: `/tmp/rimes-card32-tests.log`.
- Covered PNG decode/disk/clipboard round trip, invalid data, unknown/large
  metrics, authorization grant/denial/restriction, write failure, full-width
  footer at 320/393/852 pt, unchanged keyboard height, paused auto insertion,
  preserved Buffer/totals and explicit text fallback.
- Isolated the existing two-controller association test's history/defaults from
  the host app's persisted reset request, which otherwise cleared that fixture
  during its second controller mount. No production association behavior changed.
- Signed device build, deep strict signature verification, resource/privacy
  verifier, and `git diff --check` passed.
- Build 32 installed successfully on iPhone 15 Pro and iPhone 16e.
- iPhone 16e ran the real renderer and PhotoKit save: build 32 report has
  `passed: true`, `savedToPhotos: true`, PNG 81,851 bytes. Its configuration before
  and after installation is byte-identical.
- Simulator main-app UI: tapped Save to Photos, observed the system add-only
  prompt, allowed it, observed success, then opened the newly saved card in the
  actual Photos app. Screenshots and device reports are under the ignored
  `platforms/ios/build/typing-card-validation/` directory.
- iPhone 15 Pro first launch was blocked by device lock. A later launch succeeded;
  at this record's creation the Photos authorization/save result is pending. Its
  old build 31 render report is not treated as build 32 photo-save evidence.
- No chat message was sent. Saving from a real host's keyboard extension and
  selecting the image in WeChat remain distinct from main-app PhotoKit acceptance.

## Emoji text options

Two copyable drafts are in `/Users/isaac/Downloads/RIMES-Emoji方块文字-候选样式.txt`.
Both sample matrices contain eight rows of eight grapheme clusters, without pets.
Every row measures equally in the macOS system font and PingFang SC at 14 pt.
These are style candidates, not an implemented dynamic signature: larger numbers
need a width policy, and the complete clickable link sits below the matrix.
