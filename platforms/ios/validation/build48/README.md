# iOS 1.1.0 (48): restore dynamic sizing and reduce typing work

Validated 2026-10-05. **Cold-start flicker is still open.** This build replaces the
experimental build 47 on iPhone 15 Pro and iPhone 16e. Both devices report 1.1.0
(48). This is an installation receipt, not physical WeChat acceptance or a
TestFlight release.

## Sizing and the rejected experiment

Build 47 replaced UIKit's original input view with a custom self-sizing root.
The local controller could report the correct content height while the remote
container stayed shorter; Buffer was clipped. Local layout tests did not catch
this remote-extension regression. That replacement has been removed.

Build 48 retains UIKit's original input view and native post-attachment sizing
registration. Its height follows the visible rows, including Buffer and expanded
candidates. Preferences and the selected layout are prepared before the initial
content-height constraint is activated, avoiding an intermediate default-layout
height. There is no fixed-height clipping view or manual root-bounds override.

Actual extension checks in the iPhone 17 Pro / iOS 26.5 simulator, with coordinate
taps (the automation accessibility snapshot sometimes reported stale coordinates):

- [Ordinary keyboard](idle.png) shows the complete key block.
- Opening [Buffer](buffer.png) exposes both lines; tapping its settings icon opens
  [the settings panel](buffer-settings.png).
- Typing `ni` and expanding exposes [three candidate rows](matrix.png), with both
  Buffer lines and every keyboard row visible.
- Collapsing the matrix, choosing `你`, and closing Buffer returns to the
  [ordinary keyboard](closed.png). The test content stays local to the playground.

This **does not establish correct host avoidance for every transition**. In the
matrix screenshot, the playground field has not moved far enough to clear the
expanded keyboard. Native host frame notifications can lag content resizing.
The source of cold-start flicker and complete remote-host synchronization remain
unresolved; these screenshots and the passing layout tests do not close them.
The final first-presentation recording is in ignored `build/height48/cold-start.mp4`.
No physical WeChat/Xianyu interaction or successful live rotation is claimed for
this build; rotation geometries remain covered by the layout tests.

## Evidence boundary for the height tests

The normal UIKit-hosted test now checks visible content through initial focus,
Buffer opening, candidate expansion/collapse, and Buffer closing. Existing
first-mount, fitting-size, custom-layout and rotation assertions are retained.

A separate stricter diagnostic asserted that the host's keyboard-layout guide
and notifications match each size immediately. It failed. To distinguish a RIMES
layout issue from the local test environment, [UIKitHeightProbe.swift](UIKitHeightProbe.swift)
uses only a native UIInputViewController, one label and one height constraint.
It also fails on iOS 26.5: the input view reaches heights 200, 280, 346, 280, 200,
while the guide reports 200, 280, 280, 280, 280. This fixture has no RIMES engine,
Buffer or rendering logic. It describes local inputViewController behavior, not
proof that remote extensions necessarily take the identical path.

The intentionally failing diagnostic is kept here, outside the shipping test
target; it was not changed into an expected success. To rerun it, temporarily add
this file to RIMESTests and run only
`RIMESTests/UIKitHeightProbe/testNativeUIKitHeightChangeBaseline`. The captured
failure log and the complete investigation test fixture are in ignored
`build/height48/uikit-baseline.log` and `KeyboardSizingProbes.swift`.

Related first-hand reports, not an Apple acknowledgement or proof of impossibility:
[KeyboardKit issue 1041](https://github.com/KeyboardKit/KeyboardKit/issues/1041)
and [Apple Developer Forums thread 813579](https://developer.apple.com/forums/thread/813579).

## Typing work

The group video was reported as 小鹤双拼 on 1.1.0 (40), iPhone 15 Pro Max / iOS 26.6.
Its exact package and whether the lag occurs outside WeChat remain unknown. The
video has no touch markers, so highlighted-key duration is not a measured input
latency.

The controller now renders once per ordinary input scalar, avoids constructing
hidden Buffer text, reuses unchanged settings menus and key appearance, and
reuses idle candidate controls. Pressed candidate controls are retired so an old
touch cannot select a new word at a reused index. Collapsed candidate height no
longer measures every candidate string during each sizing callback.

Final Debug simulator controller + layout benchmark (synchronous work, **not**
physical touch-to-display latency):

| Scheme | Keys | P50 ms | P95 ms | Maximum ms |
| --- | ---: | ---: | ---: | ---: |
| Imported 小鹤 | 200 | 5.76 | 16.40 | 31.34 |
| Pinyin | 110 | 2.60 | 15.99 | 19.16 |
| Ziranma | 80 | 5.21 | 16.70 | 18.06 |
| Wubi86 | 60 | 2.33 | 16.82 | 17.88 |

Before these changes, Pinyin P50/P95 were 36.85/40.56 ms, Ziranma 33.52/38.27 ms,
and Wubi86 11.54/48.38 ms in the same hosted benchmark. The 小鹤 fixture is the
previously supplied repaired package `rimes-flypy-20261003.zip` (SHA-256
`29d1ccb9b913eaab3fa7eb105a7beec55bb3003b0a9ce3d3a8fab60a389281bd`).
It is copied to an isolated temporary store/user database, not activated as the
user's scheme. `录屏` and `你好` are checked as candidates and committed text.
There is no before measurement for the complete 小鹤 controller path and no
claim that the group member's reported lag is fully resolved.

## Build and installation

- Normal full suite: **200 tests, 1 existing promotional-capture skip, 0 failures**.
  These results exclude the explicitly failing UIKit diagnostic described above.
  Log: `build/height48/tests.log` (original `/tmp/rimes-build48-final-tests.log`).
- Resource/source-parity/privacy verification and `git diff --check` passed.
- Release device build and deep strict code-signature verification passed. App
  and keyboard extension both report 1.1.0 (48).
- Existing app installations were updated in place; no uninstall or preference
  reset was performed. Installation/readback receipts:
  `/tmp/rimes-build48-iphone15-{install,app}.json` and
  `/tmp/rimes-build48-iphone16-{install,app}.json`.
- No TestFlight upload, public distribution, Git commit or push was performed.

The preceding Buffer import, candidate dragging, split Space and Xianyu delivery
work is retained. Their earlier acceptance boundaries still apply.
