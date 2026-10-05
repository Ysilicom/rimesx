# Xianyu host commit follow-up — 2026-10-05

Build 41 passed UIKit-backed tests, but failed in the actual Xianyu chat field
on iPhone 15 Pro (iOS 26.7.1). With Buffer off, nine-key `64426` composed `ni'hao`;
tapping the `你好` candidate left `ni'hao你好` in the draft. No message was sent,
and the test draft was cleared. This supersedes the earlier provisional fix.

## Build 42 experiment — failed on the phone

Before `insertText`, explicitly select the entire owned marked range by calling
`setMarkedText` with its unchanged text and a full UTF-16 selection. Native
fixtures then replaced the range, including a selection-only fixture. The real
Xianyu field still appended the candidate beside the preedit. This experiment
was rejected; setting a selection through the remote proxy did not make the
subsequent insertion replace the composition in this host.

This selection-only insertion contract exists in
[Flutter's iOS UITextInput implementation](https://github.com/flutter/engine/blob/main/shell/platform/darwin/ios/framework/Source/FlutterTextInputPlugin.mm):
its insertion replaces the selected range and clears the marked range. This is
supporting implementation evidence, not a claim about Xianyu's exact framework
version. The behavior was independently observed in Xianyu on the phone.

The regression fixture retains native text editing while modeling
selection-only insertion. It reproduced build 41's leftover raw text and wrong
caret position before the fix. It checks replacement in the middle of existing
text containing an emoji, the caret after the committed word, and immediate
continuation without destroying the preceding word. Existing deferred-unmark,
interior-selection and host-reconciliation cases remain covered.

## Build 43

For an owned composition, `ProxyTextDelivery.insert` replaces its marked text
with the candidate, places the relative caret after it, and calls `unmarkText`.
Uncomposed literal and Buffer delivery continue through `insertText`.

Subsequent writes are held until the next main-queue turn so a same-key top-up
cannot coalesce the committed mark with the next preedit. The queue preserves
edit order, rechecks the visible target before each edit, and is invalidated when
ownership is abandoned. It never rewrites the host document or forces an
absolute caret position. Tests model deferred unmark completion, consecutive
commits/literal edits/deletion, changed targets and a user-moved caret. Interaction
tests await the pending native writes before checking the visible host text.

## Validation and remaining device check

- 168 hosted iOS tests, 1 existing promotional-capture skip, 0 failures.
  Result: `build/DerivedData/Logs/Test/Test-RIMES-2026.10.05_10-52-31-+0800.xcresult`.
- `scripts/verify.py` and diff whitespace checks passed.
- Release-optimized development build and deep strict signature verification
  passed. Installed over the existing apps on iPhone 15 Pro and iPhone 16e;
  device metadata confirms 1.1.0 (43) on both.
- The imported top-up test was additionally rerun with an explicit UTF-16 caret
  assertion after the continuation; it passed.
- The phone auto-locked during the investigation. The maintainer was asked to
  unlock it again for build 43 acceptance and cleanup of the second test draft.

No Xianyu pass is claimed for build 43 until that check completes. No test message
has been sent. Private app screenshots remain under the ignored build directory.

## Subsequent delivery

2026-10-05: build 44 was installed and read back on both phones for the separate candidate-controls/layout/drag-selection change. It includes the build 43 text-delivery implementation unchanged. Xianyu physical-host acceptance and cleanup of the build 42 test draft remain pending the iPhone 15 Pro unlock. See [build 44 validation](build44/README.md).

2026-10-05: build 45 was installed and read back on both phones for the longer candidate hold, stronger drag haptics and scrolling first matrix row. It still includes the same text-delivery implementation; simulator candidate acceptance does not close the pending Xianyu physical-host check. See [build 45 validation](build45/README.md).
