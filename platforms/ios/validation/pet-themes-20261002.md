# Pet-linked keyboard themes — iOS build 28

## Behavior

- A single selection controls both the Buffer pet and the keyboard palette: Apple native plus the 17 existing pets.
- Apple keeps the native gray/white/blue appearance. RIMES Rhino uses ivory, gray and orange. Other palettes follow the bundled pet artwork, including the gray poodle, coral octopus, orange crab and yellow chick.
- Every theme supplies light and dark colors, a pressed state and readable pressed/selected ink. Bright pet accents choose black or white by contrast; native keycaps retain white-on-blue press feedback.
- Standard 26-key, full-pinyin 9-key, custom layouts, orthogonal chord and split-orthogonal chord share the theme. Chord geometry, mappings and touch behavior are unchanged.
- Keycaps, function keys, chord utility/emoji keys, candidate feedback, plugin chips and Buffer accents update together. Tapping the Buffer pet cycles the selected rotation immediately.
- The app's **键盘布局与换肤 → 宠物与键盘配色** shows a gallery, three color swatches per theme and the same palette in its keyboard preview. **宠物轮换** controls rotation membership. The keyboard gear menu also offers **Pet & colors / 宠物与配色** in chord mode.
- `keyboard-theme-v1.json` has an independent revision. Changing layouts cannot reset the pet. Existing pet choices survive migration; legacy dot becomes Apple native, and legacy classic without a pet becomes Rhino. The extension keeps its private choice if shared-container writes are unavailable.
- Build 27's default-on system input clicks and user sound-off preference are retained. The original `UIInputView`, self-sizing and first-presentation height fix remain in place.

## Automated validation

- Full iOS suite: 129 tests, 128 passed, 1 existing opt-in promotional capture skipped; no failures.
- After final palette calibration, all 4 `KeyboardThemeTests` passed again.
- Tests cover migration, one-time app revision application, theme/layout storage isolation, light/dark contrast, all 18 themes across 26-key/9-key/both chord layouts, unchanged frames and in-progress composition, function/utility key colors, pet cycling, and selected/pressed ink.
- Existing standard keyboard, imported scheme, custom layout, chord interaction, sizing, Buffer, sounds and text delivery regressions passed in the full suite.
- Shared `KeyboardExperienceTests`: 18 passed. `scripts/verify.py` passed. `git diff --check` passed.
- Debug simulator and signed physical-device builds succeeded; deep/strict code-sign verification passed.
- Xcode's post-test diagnostics still report the host's missing default `simctl` path; test results themselves are successful. The existing test harness also logs its CoreGraphics and appearance-transition warnings.

## Simulator UI checks

iPhone 17 Pro, iOS 26.5, `BF324C0A-2DD4-4C11-9DB7-A6E1CBD74C3C`:

- Selected Rhino through the app gallery, read back its shared theme revision, reopened the input playground, and observed ivory keycaps with gray function keys. Pressing Q shows the orange cap and releasing restores the normal cap.
- Opened Buffer and tapped its Rhino pet: the pet and complete keyboard changed to the hermit crab palette immediately.
- Switched to chord mode, selected Apple from the keyboard menu, and observed unchanged orthogonal placement with native caps. Held Delete: blue cap and white icon.
- Selected Rhino on the chord keyboard and changed the simulator to dark mode: dark gray caps and orange controls rendered correctly.
- Selected Chick in the app, switched to 9-key full pinyin, and observed the matching pale-yellow keyboard. Held ABC: yellow cap with black letters; release restored the normal cap and produced pinyin candidates. Delete cleared the composition. Dark appearance also rendered correctly.
- Evidence, UI dumps and screenshots are under ignored `platforms/ios/build/pet-theme-validation/`.

## Physical installation and retained state

- Installed development build **28**, version **0.1.0**, on the previously authorized iPhone 15 Pro `00008130-001E5C100EE0001C`.
- Installation succeeded, database sequence **1824**, installed app container `A77453FE-7B88-4A2B-A90B-421FB368F1F2`; device app readback confirms bundle version 28.
- Extension preferences and the imported Rime scheme manifest were copied before and after installation. All decoded preference fields were identical, and the scheme manifest was byte-identical.
- Retained phone settings include chord mode, orthogonal layout, Puppy pet, the existing four-item pet rotation and enabled key sounds. No phone configuration was reset to match simulator demonstrations.
- Installation/readback is confirmed; physical WeChat interaction and subjective color/sound acceptance remain for the user after reopening the keyboard. This is not an App Store release.
