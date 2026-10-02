# RIMES Android

**0.1.0-dev.4** is a native Java Android input method for daily Chinese input and
local dictionary learning. Minimum Android 8.0 / API 26; development package
`org.scholay.rimes.android.debug`. This is a local development build, not a store release.
Current evidence and remaining acceptance are in [VALIDATION.md](VALIDATION.md).

## Input

- Offline librime 1.17.0: simplified Pinyin, natural-code Shuangpin and Wubi 86.
  Default Pinyin; the last Chinese schema is remembered. English and numeric
  input remain available while Chinese resources initialize; failures show Retry.
- The footer 中/英 control changes language. The toolbar scheme control cycles
  拼音 → 自然码 → 五笔. There are nine candidates per page, with tap selection
  and previous/next buttons. Composing text is displayed through the host's
  `InputConnection` when Buffer is off.
- Space selects the first candidate on the current page. Without composition it
  inserts a space. Return during composition confirms the raw input code only;
  the same press never also sends Buffer. Delete edits composition first.
- Switching language/schema settles raw code into the current route. Buffer's
  existing blocks remain. Buffer cannot toggle during composition or queued work;
  turning it off never sends stored blocks.
- Buffer keeps composition/candidates inside the keyboard. Each confirmed Rime
  string is a whole block; English still forms word blocks. Insert sends the next
  block, Insert all sends the remainder, and Delete removes a whole last block.
  Failed framework acceptance retains text for retry, including already queued
  results. Storage is bounded to 16,384 UTF-16 units in the block store.
- Changing fields, hiding/switching the keyboard or destroying the service
  revokes queued engine work and delivery authority, and clears transient state.
  Android hosts may finish and retain the already displayed preedit in the *old*
  editor before disconnecting it. RIMES never replays it into the next editor.

## Keyboard layouts and appearance

The layout/appearance controls in the keyboard toolbar and Setup open the same
chooser. The layout and theme are stored with the existing schema/learning
preferences; changing colors does not settle composition or reset Buffer.

- **26-key QWERTY** uses equal letter widths on all three rows, with a half-key
  inset on row two and a one-and-a-half-key inset on row three. Shift/Delete flank
  row three. The footer has numbers, emoji, language, a wide Space, and Return.
  Return displays the real editor action, raw-code confirmation or Buffer insert.
- **9-key Pinyin** uses the iOS telephone arrangement, `ABC` through `WXYZ`, a
  double-height Return, separator, punctuation and spelling selection. Choosing
  this layout selects Pinyin. Natural-code, Wubi, English and protected fields
  use QWERTY; returning to Pinyin restores the chosen nine-key preference.
  `64426` offers `你好` through librime. “选拼音” narrows ambiguous syllables;
  deleting a pinned syllable's separator returns it to its digit spelling.
- **Numbers / symbols / emoji** have dedicated pages. Numeric/symbol rows keep
  the letter-key width; the emoji page offers 30 fixed choices without recording
  recents. Emoji are committed through the existing native input route.
- **18 palettes** mirror iOS: native, Rhino, hermit crab, kitten, puppy, piglet,
  dog, poodle, pig, rabbit, crab, penguin, fox, panda, turtle, octopus, frog and
  chick. Every palette has light/dark colors, functional caps and visible press
  feedback. Pet glyphs use system emoji; animated pets are not included.
- Portrait touch rows are 56 dp; landscape rows are 36 dp. In landscape, preedit
  moves into the toolbar and chrome rows shrink to 36 dp. Insets keep keys clear
  of navigation and cutouts. Text auto-sizes within the key while touch geometry
  stays fixed, including larger system fonts.
- Candidate refreshes reuse the keyboard surface. Composition and idle punctuation
  share a stable rail; long phrases retain their full natural width and scroll.
  “选拼音” temporarily uses that rail, returning to word candidates after a choice.

Layout/language/page changes settle pending raw code through the current input
route and preserve confirmed Buffer blocks. Switching a theme only changes colors.
Nine-key schemas have the same private/off-learning variants and share the
existing Pinyin user dictionary; they are compiled on the build machine.

## Local learning and privacy

“Learn words on this device” in Setup is on by default. Rime candidate selections,
including Buffer selections, update local user dictionaries. There is no complete
input-history log, network permission, clipboard access or cloud synchronization.
Turning learning off preserves existing data and uses precompiled schema variants
with `enable_user_dict: false`. A live setting change settles unfinished raw code
through the current route, preserves confirmed Buffer blocks, and switches the
engine before subsequent keys. Private editors use the same variants and disable
Buffer. Password/numeric input bypasses Chinese composition; passwords have no
candidate UI. An OEM may independently substitute its own secure keyboard.

System assets live in versioned `no_backup/rime-system/<manifest hash>` directories.
User dictionaries live separately in `no_backup/rime-user`; upgrades do not replace
this directory. Preferences stay in `shared_prefs/keyboard.xml`. User dictionaries
are excluded from backup; cloud/device-transfer rules also exclude app data.
Only validated, precompiled resources are extracted on-device. There is no runtime
maintenance/deployment, dictionary compiler invocation or resource download.

## Build

Use JDK 21 and set `JAVA_HOME` and `ANDROID_HOME`. Gradle 9.7.1 and AGP 9.4.0 are
pinned. Install compile SDK 37.2, build tools 36.0.0, NDK 29.0.14206865 and CMake 3.22.1:

```sh
sdkmanager 'platforms;android-37.2' 'build-tools;36.0.0' 'ndk;29.0.14206865' 'cmake;3.22.1'
cd platforms/android
python3 scripts/build-engine.py
./gradlew --no-daemon :core:test :app:assembleDebug :app:lintDebug
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 -v 4 app/build/outputs/apk/debug/app-debug.apk
```

The Android-only builder reads the iOS dependency lock, three minimal mobile schemas and
licenses, plus the Android-owned nine-key resources in `resources/`. It never invokes iOS generation or writes iOS resources. Its `.native/`
cache, generated assets and ABI libraries are ignored. Optional
`--source-cache /path/to/Vendor/ios-build` exports the exact pinned Git commits and
checks the Boost archive checksum; it does not copy source modifications or builds.
Both `arm64-v8a` and `x86_64` are built by default. Gradle fails if assets, native
inputs, receipts or library alignment do not match.

Output: `app/build/outputs/apk/debug/app-debug.apk`. The existing development key
is used; install with `adb -s "$RIMES_ANDROID_SERIAL" install -r ...` to preserve data.
Never uninstall or clear application data during an ordinary upgrade.

The APK includes third-party licenses in `assets/licenses/`. Dictionary sources
are pinned by URL and SHA-256 in `platforms/ios/dependencies.lock.json`; Wubi data
is LGPL-3.0 and Pinyin data Apache-2.0. librime and every linked dependency retain
their original license notices. No iOS executable code or Apple framework is linked.

## Checks

`core:test` covers block boundaries, exact consumption, capacity, privacy, editor
revocation, immutable snapshots, layout geometry and nine-key spelling constraints. `app` instrumentation checks the real JNI
Unicode bridge, nine-key schemas, all 18 keycap palettes and Android editor policies. `native/engine_contract.cpp` checks
actual schemas, paging, punctuation, learning/restart and disabled learning.

On x86_64 Linux (including an x86_64 Linux container on an ARM development host):

```sh
python3 scripts/test-linux-native.py
```

This downloads a checksum-pinned AOSP runtime for validation only, extracts it
with `debugfs` (e2fsprogs), and loads the Android `.so` with Bionic. This is native
ABI/engine evidence, not Android emulator UI or 16 KB runtime acceptance.

`testhost` is a separate, offline validation app with native, password, private and
WebView fields. It is never included in the keyboard APK. Its instrumentation
clicks the actual RIMES buttons and checks actual host text; it does not inject
text through an automation keyboard. While visible it keeps its own window awake,
without changing the device timeout. Close other UiAutomation sessions before use.

```sh
./gradlew :app:assembleDebugAndroidTest :testhost:assembleDebug :testhost:assembleDebugAndroidTest
# Install the test APKs with install -r after approving any OEM USB-install prompts.
adb -s "$RIMES_ANDROID_SERIAL" shell am instrument -w \
  org.scholay.rimes.android.debug.test/org.scholay.rimes.android.EngineInstrumentation
# Select RIMES after the JNI test (instrumentation restarts the keyboard process).
adb -s "$RIMES_ANDROID_SERIAL" shell ime set \
  org.scholay.rimes.android.debug/org.scholay.rimes.android.RimesInputMethodService
adb -s "$RIMES_ANDROID_SERIAL" shell am instrument -w \
  org.scholay.rimes.testhost.test/org.scholay.rimes.testhost.InputContractInstrumentation
# Layout, touch coordinates, spelling, Buffer and orientation with screenshots.
adb -s "$RIMES_ANDROID_SERIAL" shell am instrument -w -e mode layout \
  org.scholay.rimes.testhost.test/org.scholay.rimes.testhost.InputContractInstrumentation
# Continuous real-keyboard input; the runner reports every minute.
adb -s "$RIMES_ANDROID_SERIAL" shell am instrument -w -e mode soak -e seconds 1800 \
  org.scholay.rimes.testhost.test/org.scholay.rimes.testhost.InputContractInstrumentation
```

For a long run whose desktop ADB connection may close, start the same bounded job
on the phone and read its log. This is the mode used for final dev.3 acceptance:

```sh
adb -s "$RIMES_ANDROID_SERIAL" shell 'nohup am instrument -w -e mode soak -e seconds 1800 org.scholay.rimes.testhost.test/org.scholay.rimes.testhost.InputContractInstrumentation > /data/local/tmp/rimes-dev3-soak.log 2>&1 < /dev/null &'
adb -s "$RIMES_ANDROID_SERIAL" shell tail -n 6 /data/local/tmp/rimes-dev3-soak.log
```

Require both `PASS SOAK` with at least 1,800 seconds and `PASS input contract`;
elapsed desktop time or an interrupted run is not a pass.

Always save and restore the original default keyboard and rotation settings.
The host runner starts a fresh validation activity for each run; it clears only
that activity's transient task. It uses UiAutomation's shell launch to avoid OEM
background-activity restrictions and fails if the host is not created in 15 seconds.
If an OEM replaces password input with a secure
keyboard, record that result separately and pass `-e skipPassword true` to run the
remaining contract. A skipped password case is not a RIMES password UI pass.
The Android workflow builds/checks both ABIs and resources; it does not publish,
install on a physical phone, or participate in macOS release jobs.

Out of scope: chord/sliding/long-press gestures, complete Buffer editing, AI,
translation, custom layout importing, animated pets and complete iOS feature parity.
