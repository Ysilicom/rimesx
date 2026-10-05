# iOS 1.1.0 (44): candidate controls, expanded layout and drag selection

The pinned first row and hold timing below were superseded by [build 45](../build45/README.md), which also adds drag selection haptics.

Validated on 2026-10-05. Both iPhone 15 Pro and iPhone 16e report installed version **1.1.0 (44)**. The touch checks below ran in the iPhone 17 Pro simulator, iOS 26.5, using the installed keyboard extension in the RIMES app's “Try typing” field. Physical-device interaction has not yet been checked for this build.

## Behavior

- Settings and Buffer both hide during composition and restore for associations or an empty strip. This does not change whether Buffer is enabled.
- The expanded first row stays with the expand/collapse and auxiliary controls. Every lower row uses the full panel width and scrolls below the first row, with no reserved control column or overlapping fixed buttons.
- Hold a candidate for 0.22 seconds to highlight and enlarge its text by up to 8%. Drag to another candidate, across rows, or toward the scrolling viewport's edge. Horizontal and vertical edge scrolling re-evaluate the candidate under the stationary finger on every scroll update.
- Release selects the current target through the existing engine/association and text-delivery paths. Moving outside cancels selection. New composition, changed candidates, focus loss, typing, rotation or hiding the keyboard also cancel a held selection.
- Quick swipes still scroll without committing; ordinary taps retain their existing behavior.

## Validation

`xcodebuild test`: **177 tests, 1 existing promotional-capture skip, 0 failures**. Includes seven CandidateStrip tests plus controller regressions for host/Buffer delivery after scrolling, stale-gesture cancellation, association controls, layout, text sizing, expanded collapse and the existing keyboard self-sizing checks.

Test log: `/tmp/rimes-build44-tests.log`.
Device build log: `/tmp/rimes-build44-device.log` (`BUILD SUCCEEDED`).
`codesign --verify --deep --strict`: passed.
`python3 platforms/ios/scripts/verify.py` and `git diff --check`: passed.
Installation/readback JSON: `/tmp/rimes-build44-iphone15-{install,app}.json` and `/tmp/rimes-build44-iphone16-{install,app}.json`.

Actual simulator touches, without calling development selection hooks:

1. Type `shi` using keyboard taps. Both auxiliary buttons disappear.
2. Slowly drag from the first candidate “是” to the third “事”. It highlights the moving target and commits exactly “事”. Associations show both auxiliary buttons again.
3. Type `shi` again and quickly swipe the candidate strip. Visible candidates scroll; the field remains `事shi`, with no accidental commit.
4. Expand: the lower rows reach the right edge, including the space below the collapse control. Slowly drag from the pinned first row to the bottom edge. The highlight moves across rows, edge scrolling brings later words under the finger, and the final highlighted “什” commits. The field becomes `事什`, the expanded panel collapses, and the “么 / 么样” associations show Settings and Buffer.
5. Cleared the test field after verification. No external message was sent.

The automation backend reported keyboard accessibility frames in extension-local coordinates, so touch coordinates were taken from fresh screenshots after semantic taps showed no effect. Gesture recording: `/tmp/rimes-candidate44-drag.mp4`; captured frames below confirm that the last highlighted candidate matches the committed text.

- [Expanded full-width matrix](expanded.png)
- [Held selection across rows](drag-across-rows.png)
- [Target after edge scrolling](drag-scroll-target.png)
- [Committed text and restored association controls](association-controls.png)

UIKit reference: [gesture failure dependency](https://developer.apple.com/documentation/uikit/uigesturerecognizer/require(tofail:)) separates quick scrolling from an established hold; [scrollViewDidScroll](https://developer.apple.com/documentation/uikit/uiscrollviewdelegate/scrollviewdidscroll(_:)) keeps hit testing current during scrolling.

The earlier Xianyu text-delivery change is included unchanged. This candidate interaction validation does **not** complete the pending Xianyu chat acceptance; see [the delivery investigation](../xianyu-host-commit-20261005.md).
