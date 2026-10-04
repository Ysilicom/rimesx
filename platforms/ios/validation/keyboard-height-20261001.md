# iOS keyboard first-presentation height

Validated on 2026-10-01 with Xcode at `/Applications/Xcode.app/Contents/Developer`.

## Change

The keyboard could retain a provisional host height on first attachment while its
rows stayed bottom-aligned, leaving a large blank area above them. Previously the
layout callback refreshed the requested height only after a width change.

The controller now disables inherited autoresizing-mask constraints, enables
`allowsSelfSizing` on UIKit's original input view, and updates the content-height
constraint before layout and again on presentation. A host-width constraint keeps
width under system control without inheriting the provisional height. Retaining
UIKit's input view also preserves host resizing when Buffer opens or closes.

Apple documents [input-view self-sizing](https://developer.apple.com/documentation/uikit/uiinputview/allowsselfsizing)
and [custom keyboard height constraints](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html).

## Automated checks

- `KeyboardSizingTests`: 3 passed. Covers UIKit presentation through a focused
  text field, a provisional 600-point height at widths 320/393/852, changes at an
  unchanged width, Buffer, expanded candidates, rotation, and reappearance.
- `KeyboardLayoutTests` and `KeyboardInteractionTests`, together with the new
  sizing tests: 56 selected, 55 passed, 1 intentionally skipped, 0 failures.
  The skipped test is the opt-in promo footage capture.
- `python3 platforms/ios/scripts/verify.py`: passed.
- `git diff --check`: passed.
- Signed iOS device Debug build and app/extension signature verification: passed.

The final test result is
`build/DerivedData/Logs/Test/Test-RIMES-2026.10.01_22-50-56-+0800.xcresult`.
The runner emitted a `simctl` lookup error during diagnostic collection; the
individual suites, final `TEST SUCCEEDED` result, and command exit status all
confirm successful execution.

## Running extension

Checked the installed keyboard extension in the simulator (iPhone 17 Pro,
iOS 26.5), separately from the hosted XCTest fixtures. First presentation, Buffer
open/close, and dismissal/reopening showed the complete keyboard without excess
space above it. The bottom space-key frame remained `(111, 754, 180, 40)` across
first presentation, Buffer expansion, and reopening; Buffer expanded upward.

Local captures and element geometry are in the ignored
`build/height-validation/` directory: `first-presentation.png`, `buffer-open.png`,
`reopened.png`, and `simulator-checks.json` (all 3 checks passed).

## Physical installation and remaining acceptance

The final signed development build was installed successfully on the connected
iPhone 15 Pro (iOS 26.7.1). Device readback reports `org.scholay.rimes.ios`,
version `0.1.0`, build `22`. This is a local development update, with no version
bump or App Store submission.

Final installation receipt: container
`7C20C64A-A521-4B62-89B0-05D43A488E9E`, database sequence `1752`.
Installed package's keyboard debug library SHA-256:
`856d4b7234008c2a87419198a9b8d3e3807d63ecf41df0de713b596a6f1c310e`.

Physical-device installation is confirmed. The original intermittent appearance
inside the user's host apps still requires hands-on acceptance; automated UI
inspection on that phone was unavailable because its UI automation agent was not
installed. Simulator results do not establish that the original physical-device
symptom has been eliminated in every host app.
