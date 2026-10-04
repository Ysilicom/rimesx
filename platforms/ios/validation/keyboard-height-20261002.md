# iOS keyboard height recurrence — build 25

The user reported the first-opening blank area again with the imported 星猫
scheme in WeChat. The earlier build 22 fix was still present in build 24.

## Change and evidence

The controller now computes its height even before its own bounds have a width,
using the attached host width or the screen before attachment. It updates the
constraint during `updateViewConstraints` and reapplies its own height constraint
after presentation, including presentations whose content height is unchanged.
UIKit's original input view is retained so Buffer and candidate expansion resize
the actual remote host. No chord geometry or scheme preferences were changed.

A targeted regression with the former zero-width early return restored failed:
the first fitting response stayed at 240 points instead of 198.9, 222, and 177 at
widths 393, 852, and 320. The final implementation passes this regression. This
demonstrates a sizing gap; it is not a captured trace of the original WeChat
failure. Development builds now record bounded geometry samples without text,
keys, or host-app identifiers.

## Verification

- 72 relevant tests: 71 passed, one intentional promotional-capture skip.
  This includes five sizing tests, custom layouts, imported schemes, input
  interactions, delivery, and native layout snapshots. The snapshot fixture
  needed isolation from the simulator's active imported scheme; its corrected
  rerun passed. No assertions were removed.
- Results: `Test-RIMES-2026.10.02_15-08-46-+0800.xcresult` and
  `Test-RIMES-2026.10.02_15-10-32-+0800.xcresult`.
- Resource verification, diff whitespace check, device Debug build, and strict
  bundle signature verification passed.
- Actual simulator extension, iPhone 17 Pro / iOS 26.5: three cold starts, a warm
  reopening, Buffer open/close, and candidate expansion preserved the complete
  keyboard. The space key remained `(188, 752, 114, 38)`; Buffer expanded upward
  by 80 points. Typing `nkhz` produced the 星猫 candidate `你好`.
- Local evidence: ignored `build/height-recurrence-validation/`, including
  `final-presentations-checks.json`, UI dumps, screenshots, and geometry samples.

## Physical phone

Installed signed version 0.1.0, build 25 on the iPhone 15 Pro. The installation
receipt reports database sequence 1800, bundle container
`FB6070D1-54BE-4038-B201-CCD0637714CC`.
Decoded extension preferences and the imported-scheme manifest matched their
pre-install values. JSON byte ordering changed; the preference values did not.
The user's existing `chord` / `orthogonal` choice remains intact.

The user retried WeChat and reported: “目前正常，没有空白”. The first physical
geometry readback after installation recorded width 393, requested height 198.9,
actual height 199, container height 199, and top inset 5.1. That sample describes
the recorded keyboard presentation, not independent proof of which scheme or
host app was active. Longer-term intermittent behavior remains observable through
the new geometry diagnostics.
