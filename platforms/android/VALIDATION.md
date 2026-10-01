# Android Chinese input validation — 2026-10-01

Version **0.1.0-dev.3 / code 3**, development package
`org.scholay.rimes.android.debug`, Android 8.0 / API 26 minimum.
Application source: `70e6301311b28987de12e180e943cadf04c3a2cb`.
Branch: `codex/android-chinese-input`, based on `1c2b2ee`.
Local development delivery only; no push, remote CI run or public release.

Machine-readable evidence: [validation/2026-10-01.json](validation/2026-10-01.json).
Previous English/Buffer foundation: [dev.2 report](validation/2026-09-29.md).

## Artifact and installation

Final APK: `RIMES-Android-0.1.0-dev.3-70e6301.apk` (17,212,089 bytes).
SHA-256:

```text
76744592bd8968bba770c6ced476e4998b65a2b7a325621ba3c44ef53a20d42e
```

The APK, checksum file, installed-package readback, logs and screenshots are kept
locally in `.build/android-dev3-validation/` at the repository root (ignored).
The installed base APK was pulled from the phone and matched the final APK byte
for byte. Readback confirms version code 3, min SDK 26, target SDK 37, arm64-v8a.
The existing development signature was used with `adb install -r`; app data was
not cleared and the package was not uninstalled. `aapt2 dump permissions` reports
no requested app permissions.

## Automated build and engine evidence

JDK 21, Gradle 9.7.1, AGP 9.4.0, compile SDK 37.2, build tools 36.0.0,
NDK **29.0.14206865**, CMake **3.22.1**. The Gradle wrapper and distribution are
checksum-pinned. The Android-only native builder reads the pinned iOS dependency
lock, mobile schemas and licenses without modifying any iOS resources.

| Check | Result and evidence boundary |
|---|---|
| librime and dependencies | librime 1.17.0, commit `33e78140250125871856cdc5b42ddc6a5fcd3cd4`; dependency revisions match the iOS lock |
| Offline resources | 15 precompiled resource files; manifest hashes checked before packaging and again inside the final APK; no phone-side dictionary compilation or download |
| Schemas | Simplified Pinyin, natural-code Shuangpin and Wubi 86; private/off-learning variants have `enable_user_dict: false`; nine candidates per page |
| Licenses | Notices copied without modification into APK `assets/licenses/`; hashes checked against source notices |
| arm64-v8a | Real native engine on the Android 16 phone; exact final APK library loaded with `dlopen` |
| x86_64 | Real Android static engine and exact final APK `.so` loaded with a checksum-pinned AOSP Bionic runtime in an x86_64 Linux container |
| Required conversions | Both ABIs: `nihao → 你好`, `nihk → 你好`, `wq → 你`; actual Rime results, not mock candidates |
| Additional engine behavior | Nine-item paging, non-first selection, Chinese comma, backspace and clear composition |
| Local learning | Normal selections create user-dictionary entries; exports survive process restart; disabled variants leave existing entries unchanged and create no learned entries in a fresh user directory |
| ELF / APK alignment | Both libraries' LOAD segments align to 16,384 bytes; final APK passes `zipalign -c -P 16 -v 4` |
| JVM tests | 14 pass, zero failures/errors: 9 Buffer tests and 5 Chinese/epoch/snapshot/capacity tests |
| Lint | App: zero errors, 3 warnings; validation host: zero errors, 5 warnings |
| Workflow | Native build, real x86_64 engine contract, resource validation, JVM tests, APK build, Lint and alignment added; dependency path triggers include iOS lock/schemas/licenses; YAML checked locally |

App Lint warnings are the newer Gradle version notice, the pre-Android-12
`fullBackupContent` advisory and the development app icon. No warning was
suppressed. `allowBackup=false` applies to older Android; user dictionaries use
`no_backup` on every supported version, and Android 12+ cloud/device-transfer
rules explicitly exclude app data. The separate test host's warnings concern its
intentional local WebView JavaScript, backup/icon metadata and literal test labels.

The x86_64 runner uses AOSP runtime revision
`070571b455076f77a01c7b07154a15e545d2b428`, APEX SHA-256
`11c91be1e1f1fe19af790a0c3a6f9db6e8e36d0ab74e403aabb3b81bbe31bdd9`.
The runtime is only a validation dependency and is not packaged in the APK.
Its stand-alone linker reports the expected missing Android linker-configuration
warning. This test does **not** establish emulator UI or 16 KB runtime acceptance.

## Physical phone evidence

Android 16 / API 36, model `25098PN5AC`, arm64-v8a, **4 KB** memory pages,
three-button navigation, English system locale. Tests use synthetic text only.
The separate offline `org.scholay.rimes.testhost` app contains native EditText,
private/password and WebView fields and is not included in the RIMES APK.

The device contract activates the actual RIMES keyboard buttons using Android
accessibility actions and reads actual host text. It does not type through an
automation IME or call the Rime engine in place of keyboard interactions.

| Device contract | Observed result |
|---|---|
| First/non-first candidate and paging | Correct selected Chinese replaces composing text; page two is selectable |
| Three schemes | Pinyin `nihao`, natural-code `nihk`, Wubi `wq` produce the required Chinese |
| Fast ordered keys | Ten rapid `nihao` sequences each commit exactly `你好` |
| Long phrases | `zhongguoren` offers and commits a phrase of at least three characters without compressing it into a narrow column |
| Punctuation, delete and raw Return | `你好，。`, host deletion, and raw `nihao` Return behave as specified |
| Language switch | Unconfirmed `ni` settles before English `abc`, yielding `niabc` |
| Buffer isolation | Composition and selected Chinese leave the host unchanged until explicit insertion |
| Chinese/English blocks | Next sends the whole `你好` block; all sends the remaining `ab c` exactly once; empty Buffer Return inserts nothing |
| Raw Return in Buffer | First Return stores raw `ni` only; second Return sends it |
| Buffer toggle/delete | Toggle disabled during composition; turning Buffer off sends nothing and preserves blocks; Delete removes a whole block |
| New editor | Pending old composition is not replayed into the second field; new input works normally |
| Selection replacement | Replacing the selected middle of `abcd` produces `a你好d` |
| Private field | Chinese works with Buffer disabled and a no-user-dictionary schema |
| Hide/reopen | Temporary composition clears; reopening starts a fresh input target |
| Orientation | Actual landscape and portrait configurations asserted; landscape Buffer isolates and sends Chinese, with Return visible |
| Independent WebView | Chinese commit, isolated Buffer, exact insertion and focus change between two DOM fields pass |

Current short-contract result: **301 assertions passed**. Password automation was
explicitly skipped in the independent host because Xiaomi replaces RIMES with
`com.miui.securityinputmethod/.latin.LatinIME`, confirmed by system readback.
The OEM secure-keyboard setting was not changed. Separately, the setup password
field retained RIMES: five literal key taps entered five masked characters, no
candidate bar appeared, and language/schema/Buffer controls were disabled. JNI
instrumentation also passed text, visible, web and numeric password policies.

Android may finish an already displayed composing span in the **old** EditText
before notifying the IME of a new connection. The old field can therefore retain
raw `ni`. The contract accepts either empty or `ni` in the old field and requires
that the new field receives no stale text. RIMES clears its own composition,
Buffer and outstanding result authority in both cases.

## Learning, upgrade and additional device checks

- With the keyboard already open, turning learning off preserved the existing
  `你好` Buffer block and settled pending `ni` into the same Buffer. A subsequent
  private-schema selection added `好`; Insert all delivered `你好ni好` exactly.
  Exported Pinyin and Wubi dictionary entries were unchanged across the off test.
- With learning enabled, a private editor committed `你好` while Buffer remained
  disabled. Exported entries again remained unchanged.
- Normal and Buffer selection resumed learning when enabled: the Buffer word
  `世界` added a learned entry. Exported entries survived process restart and
  reinstall unchanged. Test exports operate on copies of app-private data.
- A same-development-signature `install -r` preserved all **20** user-dictionary
  and preference file hashes, including nondefault natural-code schema and
  `learning=false`. Reopened keyboard and preference readback confirmed them;
  final testing returns to Pinyin with learning enabled.
- In System Settings search, Buffer kept `你好` out of the editor until Insert
  all. Turning Buffer off and pressing Return invoked Search, hid the keyboard
  and retained exactly `你好` without an extra newline.
- Switching from RIMES to Sogou through the system picker and back cleared the
  unsent `世界` draft. RIMES returned with Buffer off; enabling it showed no
  pending block, and the host had not received that draft.
- Packaged JNI tests passed Chinese, non-BMP characters, embedded NUL, actual
  partial-Chinese preedit UTF-8-byte to UTF-16 caret conversion and editor policy.

## Continuous input and final restoration

The final APK completed **1,801 seconds (30 minutes), 995 input cycles and
10,948 assertions**, with no duplicate or cross-field commit. The runner alternates
between two native editors and between direct input and Buffer capture/send,
including Chinese candidate confirmation and punctuation. It exits successfully
only after the measured interval finishes. The RIMES process ID remained unchanged
throughout this run, and its crash log for the interval is empty.

The final run used a bounded instrumentation job detached on the phone; the Mac
only read its progress log. Earlier startup waits, preliminary runs and a run
interrupted by a UiAutomation watcher disconnect are excluded from the 30 minutes.
The watcher failure was in the separate test host, while RIMES remained running.
The app code did not change during the successful final run.

Afterward, the original Sogou default input method
`com.sohu.inputmethod.sogou.xiaomi/.SogouIME` and portrait rotation lock
(`accelerometer_rotation=0`, `user_rotation=0`) were restored and read back.
The screen timeout remains 120,000 ms; the OEM secure-keyboard setting was not
changed. The validation host is closed, releasing its keep-screen-on window flag.
RIMES remains installed and enabled, with Pinyin selected and learning enabled.
The installed base APK was pulled again after testing and still matches the
local artifact hash above.

Local evidence includes `soak-final.log`, `soak-result.json`,
`ime-crash-check.log`, `device-contract.log`, `jni.log`, `arm64-engine.log`,
`arm64-library.log`, `x86-contract.log`, `x86-packaged-library.log`,
`apk-alignment.log`, `upgrade-final.json`, `restored.json`, the build receipt,
resource manifest and selected phone screenshots. These are retained in the
ignored evidence directory, not uploaded or committed as user-dictionary data.

## Implementation changes and review boundaries

1. `11bccdc`: Android-only pinned native build, precompiled resources, manifests,
   licenses, version 3, JNI and immutable engine snapshots.
2. `eff9ca7`: serial engine worker, target-bound asynchronous results, composing
   API, Chinese modes/candidates/paging and stable keyboard views.
3. `70412f5`: whole Chinese Buffer blocks, retained failed delivery, private/off
   variants, local learning preference and platform backup exclusions.
4. `236546d`: readable long phrase candidates on the phone's candidate rail.
5. `70e6301`: apply learning preference changes to an already active input
   session, preserving confirmed Buffer blocks and serialized result ordering.

All five commits contain the repository-required Codex attribution. Validation
fixtures, CI and this report are recorded in a subsequent Android-only commit;
they do not change the final APK's application code.

The separate main working tree and its pre-existing macOS/iOS changes were left
untouched. No iOS generation or macOS install command was run.

## Remaining coverage

- Older Android versions, other manufacturers, x86_64 Android emulator UI and an
  actual 16 KB Android runtime are **not tested**.
- Broader host compatibility, gesture navigation, large fonts, dark theme and
  assistive-technology usability require follow-up device acceptance.
- Buffer refusal/capacity and stale-result rejection have automated core/epoch
  coverage; arbitrary third-party InputConnection failures and process-death
  behavior have not been exhaustively fault-injected on this phone.
- Chord/sliding/long-press gestures, full Buffer editing, AI, translation and a
  theme redesign remain outside this phase.
