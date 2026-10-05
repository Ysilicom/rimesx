# iOS 1.1.0 (46): import text into Buffer

Validated on 2026-10-05. Installed over the existing app on **iPhone 15 Pro and iPhone 16e**; device metadata confirms **1.1.0 (46)** on both. No app uninstall or user-configuration reset was performed. Feature interaction was checked with the installed keyboard extension in the iPhone 17 Pro simulator, iOS 26.5. Physical WeChat/Xianyu interaction remains a separate acceptance step.

## Behavior

- When an empty Buffer opens over readable host text, its input line shows an import icon, the actual text preview, and “点此移入” / “Tap to import”. The prompt pulses twice, respects Reduce Motion, and disappears when Buffer has text or a composition. Only the preview ellipsizes; the captured text is not shortened to fit the UI.
- The small paste icon stays at the right of the input line. It uses `UIPasteControl` to obtain a text item provider after an explicit tap. It inserts at the Buffer cursor, preserving existing content, whitespace, and newlines. Opening Buffer does not read clipboard payloads. Without keyboard Full Access, tapping explains the required setting. A system paste permission dialog can still appear in a keyboard extension; the pending request retains the draft and cursor across that dialog.
- The native paste control has its minimum layout size and scales for the shorter landscape row. A fixed accessory area keeps text/caret scrolling clear of the icon. Existing keyboard height behavior is unchanged.
- Host text is moved only after the current document and both text boundaries are verified. The full readable source is placed in Buffer before deletion. Selected text and unprovable/truncated context are copied with a notice that the original remains. Changed/unacknowledged context prevents deletion. Each deletion is checked, and unexpected responses, cancellation, target changes or the three-second deletion bound stop the transfer with the full saved Buffer text retained while the keyboard session remains active.
- Import and paste do not inflate typing statistics. Plugin selection, closing Buffer and changing fields cancel obsolete requests; late paste providers cannot write into another field.

## Validation

- Full `xcodebuild test`: **196 tests, 1 existing promotional-capture skip, 0 failures**. Sixteen Buffer import tests cover preview/layout, beginning/middle/end carets, emoji/combining characters/newlines, stale and truncated context, selections, delayed/ignored deletion, changed documents, native paste providers, 5,000-character clipboard content, cursor insertion, permission interruptions, and late-provider cancellation.
- Test log: `/tmp/rimes-build46-tests.log`; result: `build/DerivedData/Logs/Test/Test-RIMES-2026.10.05_14-02-36-+0800.xcresult`.
- Release build: `/tmp/rimes-build46-device.log` (`BUILD SUCCEEDED`). Deep strict signature verification passed. The app and keyboard extension both report bundle version 46. `scripts/verify.py` and `git diff --check` passed.
- Installation/readback: `/tmp/rimes-build46-iphone15-{install,app}.json` and `/tmp/rimes-build46-iphone16-{install,app}.json`.

Actual simulator touches, without development import/paste hooks:

1. Put `Buffer import 46` in the app's secondary native field, focus another field and return, then open Buffer. The complete host-text preview and paste icon appear.
2. Tap the preview. The native field becomes empty and Buffer contains exactly `Buffer import 46`.
3. Paste ` + paste` twice. Buffer becomes exactly `Buffer import 46 + paste + paste`; the host field stays empty.
4. Rotate to landscape. The smaller paste icon remains visible and accepts a tap. A system permission prompt appears; rotate back to portrait and allow the paste. Buffer becomes exactly `Buffer import 46 + paste + paste + paste`, retaining all earlier content.
5. Return to the app's home page, restore the simulator's original Full Access setting (off), clear the dummy simulator clipboard and close the automation session. No external message was sent.

Earlier actual-touch checks exposed the proxy's optimistic cached context: a native field containing `Buffer import 46` could initially expose only `Buffe` after automation typing. Same-turn reads were therefore removed as deletion authority. Boundary probes now wait for a UIKit host-context callback; the stale-context case leaves the native text untouched. Own deletions are checked after yielding, without requiring a callback that UIKit does not reliably issue for those writes.

Live screenshots:

- [Host-text preview](host-preview.png)
- [Text moved into Buffer](host-moved.png)
- [Consecutive paste](consecutive-paste.png)
- [Landscape paste control](landscape-live.png)
- [Draft preserved through system permission](paste-permission-preserved.png)

Layout fixtures at [393 points](buffer-import-portrait.png), [320 points/dark](buffer-import-narrow-dark.png), and [852 points](buffer-import-landscape.png) cover the prompt and layout. These are layer-rendered test fixtures; the system-owned paste icon is visible in the live screenshots above rather than in those fixtures.

The build includes the preceding candidate-selection and Xianyu delivery changes. This validation does not close their separate physical-host acceptance, including the [Xianyu investigation](../xianyu-host-commit-20261005.md).
