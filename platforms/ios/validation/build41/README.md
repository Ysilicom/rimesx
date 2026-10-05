# iOS build 41 — host commits and independent chord Spaces

2026-10-05, version 1.1.0 (41).

**Superseded: build 41 failed the subsequent Xianyu device check.** After the
maintainer unlocked the iPhone 15 Pro, nine-key Pinyin `64426` composed `ni'hao`.
Tapping `你好` produced `ni'hao你好`: the host inserted at the selection instead
of replacing the marked range. The test draft was cleared without sending.
See the [build 42 follow-up](../xianyu-host-commit-20261005.md).

## Host text and caret

Reported in Xianyu's chat field, with Buffer off and across input schemes:
candidate selection briefly inserts text, then restores the previous text;
a separate report describes the caret returning to the beginning.

The common delivery path cleared the marked range, unmarked it, then inserted
the candidate. That exposes an intermediate cancelled composition to the host.
It now calls `insertText` directly to replace the marked range in one edit and
clears only the keyboard's ownership bookkeeping. It does not retry insertion,
rewrite the host document, or force the caret to the end of existing text.
Document identity and active-session guards are preserved.

This follows the marked-range replacement behavior in Apple's
[UITextInput example, Listing 9-5](https://developer.apple.com/library/archive/documentation/StringsTextFonts/Conceptual/TextAndWebiPhoneOS/LowerLevelText-HandlingTechnologies/LowerLevelText-HandlingTechnologies.html).
A native UIKit probe also checked replacement when the selection is inside the
marked range and immediately starting the next composition.

`testCandidateSurvivesHostReconciliationAfterCompositionEnds` failed before the
change and passed afterward. Its UITextView-backed fixture delays a host's
reconciliation of an empty composition. It checks `前😀你好后`, a caret after
`你好`, then `前😀你好吗后` with the caret after `吗`. Existing continuation,
interior-selection, imported-scheme, target-change and Buffer tests also pass.
This models a failure mechanism; it is not a reproduction inside Xianyu.

## Chord Spaces

Two separate `SpaceCursorButton` controls replace the single cap and painted
divider. They have equal widths, a 2 pt gap, independent hit targets and pressed
states, and the selected keyboard theme. Both tap to insert a space or confirm a
candidate. The left key retains Buffer selection on hold/drag; the right moves
the caret. Ordinary and numeric layouts retain their single Space.

Tests cover 320/393/852 pt widths, both chord geometries, optional system globe,
Apple/classic themes, separate hit targets and highlights, both tap actions,
left/right hold actions, and transitions through the nine-key layout. The saved
[light](build41-orthogonal-320-light.png) and
[dark](build41-splitOrthogonal-393-dark.png) simulator images were inspected.

## Validation and delivery

- Hosted iOS suite: **165 tests, 1 existing promotional-capture skip, 0 failures**.
  Result: `build/DerivedData/Logs/Test/Test-RIMES-2026.10.05_10-18-11-+0800.xcresult`.
- `scripts/verify.py` passed resource, parity, permission and privacy checks.
- Release-optimized development build succeeded. Deep strict signature
  verification passed; both app and keyboard extension report 1.1.0 (41).
- Installed over the existing apps on the iPhone 15 Pro and iPhone 16e without
  uninstalling or resetting data. Device app metadata confirms build 41 on both.
- **Xianyu device acceptance remains pending.** The iPhone 15 Pro has Xianyu,
  but its locked state prevented the UI runner from starting. The iPhone 16e
  does not have Xianyu installed. The maintainer was asked to unlock the 15 Pro.
  An additional RIMES UI check on the 16e was attempted, but XCTest's runner
  disconnected before accepting commands; no physical UI pass is claimed.

Acceptance still needed in an empty Xianyu chat draft, with Buffer off: compose
and select `你好`, then compose/select a second word; text must remain and the
caret must follow each commit. Repeat via candidate tap and Space, then delete
and continue typing. Do not send the draft. Repeat with the maintainer's imported
scheme. No App Store/TestFlight upload or review is implied by this local build.
