# iOS 1.1.0 (45): deliberate drag selection and scrolling candidate matrix

Validated on 2026-10-05. Installed over the existing app on both iPhone 15 Pro and iPhone 16e; device metadata confirms **1.1.0 (45)** on both. Physical haptic feel has not been checked. Actual touch checks used the iPhone 17 Pro simulator, iOS 26.5, with the installed RIMES keyboard extension in the app's local “Try typing” field.

## Behavior

- A candidate hold now takes **0.45 seconds**, increased from 0.22. Movement beyond the hold tolerance before recognition remains ordinary scrolling. Taps use the existing selection action.
- Entering drag selection and changing to a different candidate request selection haptics with at least the Strong setting (medium impact, intensity 0.85). Strongest stays Strongest; the existing haptics-off switch is respected. Holding still or leaving and re-entering the same target does not repeat a pulse. Selection changes add no keyboard clicks.
- The expanded matrix now scrolls **every row**, including the original first row. Only rows currently intersecting the fixed corner controls reserve space around them. Other rows use the full panel width. The stable scroll range keeps the last candidates reachable after reflow.
- Drag selection re-hits the visible candidate after scrolling or reflow. The final release selects the current highlighted word. Existing cancellation on changed composition, focus, typing, hiding or rotation remains in place.
- Both Settings and Buffer remain hidden for composition candidates and visible for associations/idle, as in build 44.

## Validation

- `xcodebuild test`: **180 tests, 1 existing promotional-capture skip, 0 failures**. Nine CandidateStrip tests cover control avoidance across widths, full-list reachability, selection feedback, horizontal/vertical edge scrolling, cancellation and release targeting. Controller tests cover host/Buffer delivery, haptics strength and the haptics-off switch.
- Log: `/tmp/rimes-build45-tests.log`; result: `build/DerivedData/Logs/Test/Test-RIMES-2026.10.05_12-14-33-+0800.xcresult`.
- Release device build: `/tmp/rimes-build45-device.log` (`BUILD SUCCEEDED`). Deep strict signature verification, `scripts/verify.py` and `git diff --check` passed.
- Install/readback evidence: `/tmp/rimes-build45-iphone15-{install,app}.json` and `/tmp/rimes-build45-iphone16-{install,app}.json`.

Actual simulator touches, without development selection hooks:

1. Type `shi`. Both auxiliary buttons hide.
2. Swipe the horizontal strip by 220 points in 160 ms. Later candidates appear; the field remains `shi`.
3. Move 72 points over 2.5 seconds, crossing the hold's movement tolerance before 0.45 seconds. The strip scrolls and the field still remains `shi`, with no accidental selection.
4. Expand, then swipe vertically in both directions. The original first row scrolls out, the later rows reflow around the collapse button, and the original row returns when scrolling back. The field remains `shi`.
5. Slowly drag from the first row to the bottom edge over 6 seconds. The held target enlarges, changes across rows, and follows automatic scrolling. Recording frame at 10.35 seconds shows final highlighted “謚”; after release the native field contains exactly “謚” and the matrix collapses.
6. Delete the local test character and verify the field is empty. No external message was sent.

The automation backend again reported extension-local accessibility frames. After a semantic key tap had no effect, touch positions were taken from fresh screenshots. Recording: `/tmp/rimes-candidate45-drag.mp4`; event log: `/tmp/rimes-candidate45-qa/sessions/candidate45/events.ndjson`.

- [Expanded matrix](expanded.png)
- [First row scrolled out](scrolled.png)
- [Drag across rows](drag-across-rows.png)
- [Final highlighted word after automatic scrolling](drag-final-target.png)
- [Committed word](committed.png)

This supersedes the pinned first row and shorter hold in [build 44](../build44/README.md). The earlier Xianyu text-delivery change is included unchanged; its separate physical-host acceptance remains pending. See [the delivery investigation](../xianyu-host-commit-20261005.md).
