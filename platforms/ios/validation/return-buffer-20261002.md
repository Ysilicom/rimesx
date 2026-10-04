# Buffer naming, Return states and menu polish — iOS build 29

## Changes

- Rename the original/default plugin to **Buffer**. Use the stack icon in the plugin menu, current-plugin button and settings panel; plugin selection IDs and stored data remain unchanged.
- Restore the original status dot for the Apple-native palette, including legacy `light` selections. Its existing idle/ready/waiting/streaming/error colors and breathing behavior are retained. No Apple logo is rendered.
- All layouts share one Return state: confirm active composition first; insert the next Buffer block on a separate tap; then use the host field's Send/Search/Done/Return action after the Buffer empties. An occupied Buffer no longer receives a newline from Return.
- Plugin output remains unavailable until ready. Consuming one plugin result retires its source while keeping the remaining result blocks insertable.
- Freeze Return's action and document/block context at touch-down. If automatic insertion or a different action drains the Buffer before touch-up, that release cannot turn into a host Send action.
- Add Traditional Chinese output and Key haptics icons. Remove the Chord layout submenu from the keyboard gear menu; retain the app's chord configuration and existing key mappings.
- The playground's secondary field now requests a Send key, while its primary editor retains multiline Return, so both host traits can be checked locally.

## Automated checks

- Selected iOS suites: **83 tests, 82 passed, 1 existing opt-in promotional capture skipped, 0 failures**.
- Suites: `KeyboardReturnTests` (6), `KeyboardInteractionTests` (52 including skip), `KeyboardSizingTests` (5), `KeyboardThemeTests` (4), `StandardKeyboardRuntimeTests` (8), `ImportedSchemeRuntimeTests` (8).
- New regression coverage includes 26-key, 9-key, custom layout, orthogonal chord and split chord; Apple and Rhino themes; confirmation before Buffer insertion; multi-block insertion; plugin generation/pending/retained results; held Return after another insertion; stale document identity; legacy/native dot; Buffer names/icons; and gear menu scope.
- Simulator and signed device Debug builds succeeded. Deep/strict code-sign verification, iOS resource/privacy verification and `git diff --check` passed.
- Xcode's post-test diagnostic collection reports the pre-existing host default `simctl` lookup error. The simulator tests and final test command succeeded.

## Simulator interaction

iPhone 17 Pro, iOS 26.5, `BF324C0A-2DD4-4C11-9DB7-A6E1CBD74C3C`:

- Selected Apple native through the app theme gallery. The actual extension displays the gray idle dot and green ready dot, with native key colors.
- Opened the Buffer plugin menu and settings panel. Both display **Buffer** with a stack icon; the current-plugin button uses the same icon.
- Visually checked the Traditional Chinese output and Key haptics menu icons. The chord gear menu contains no layout configuration entry.
- Used actual keyboard taps in 26-key, 9-key and orthogonal chord modes. Each changed **Confirm → Insert → Send**. Each first tap confirmed composition into Buffer without modifying the host; the next inserted the content into the host without a newline. Host text progressed `ni`, `ni你`, then `ni你ni`.
- Screenshots and UI dumps are in ignored `platforms/ios/build/return-buffer-validation/`.

## Physical installation

- Installed development **0.1.0 (29)** on the authorized iPhone 15 Pro `00008130-001E5C100EE0001C`. Device readback confirms build 29; installation database sequence 1832, app container `0C5C759C-F46B-4674-8CA2-820DD3B3F56A`.
- Copied extension preferences and the imported-scheme manifest before and after installation. The scheme manifest is byte-identical. Chord mode, orthogonal layout, rotation list, sound/haptic settings and all other decoded preference fields match except the selected theme and its revision: Puppy before, Rhino after. No device preference reset or restoration was performed; these snapshots alone do not establish when that theme selection changed.
- Physical installation/readback is verified. WeChat behavior on the phone has not been independently rechecked in this turn. This is a development installation, not an App Store release.
