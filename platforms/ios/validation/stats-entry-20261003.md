# Statistics entry correction — build 33

The Default Buffer status/readout is the single keyboard entry again. Tapping it
opens a preview with Emoji blocks, ordinary text and image tabs; it does not
immediately append the old signature. The gear-menu Stats image (optional) item
has been removed. The containing app's empty-state instructions point to the
readout and Image tab.

Text requires an explicit Insert text action through the existing proxy delivery
path, including without Full Access. Image keeps Save to Photos as its primary
action. Hints remain across the bottom. Closing/copying/switching format does not
consume totals; automatic insertion is paused while the preview is open.

Emoji frames are eight cells wide and normally eight rows. Long numbers wrap to
continuation rows instead of widening the frame. The legend and complete GitHub
URL sit below the frame; no pet is included. Text and image use the actual current
session aggregates, captured when the preview opens.

Validation:

- Simulator: 72 tests executed, 71 passed, one existing skip, zero failures.
  `/tmp/rimes-stats-entry33-tests.log`.
- The interaction tests invoke the actual readout callback, check the three tabs,
  absence of the settings item, unchanged keyboard height and host text on open,
  paused auto insertion, explicit matrix insertion and ordinary-text fallback.
- Format tests check text-vs-photo action routing, actual Emoji metrics and the
  eight-cell frame with long values. Existing PhotoKit authorization/error,
  keyboard return and height suites remain passing.
- Device build and deep strict signature verification passed. Resources/privacy
  verification and `git diff --check` passed.
- Native UIKit preview inspected at 393 pt, with layout checks at 320/393/852 pt.
  Snapshot: `platforms/ios/build/stats-entry33-validation/preview.png` (ignored).
- Build 33 installation receipts are in `/tmp/rimes-stats-entry33-install-phone1.json`
  and `/tmp/rimes-stats-entry33-install-phone2.json`. Installation is separate from
  manual acceptance of the status-tap flow in a physical host app.

No chat message was sent and no configuration/history reset was performed.
