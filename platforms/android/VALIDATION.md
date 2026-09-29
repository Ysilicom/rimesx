# Android foundation validation — 2026-09-29

App source: `e89a5f2fd2aa2be8bac554d91d20883848572de9`.
Version `0.1.0-dev.1` / code `1`, package `org.scholay.rimes.android.debug`.
Development signature only; no Play Store or public release.

## Passed locally

JDK 21, Gradle 9.7.1, AGP 9.4.0, Android compile SDK 37.2, build tools 36.0.0:

```sh
cd platforms/android
./gradlew --no-daemon :core:test :app:assembleDebug :app:lintDebug
```

- Debug APK built successfully.
- Nine JVM tests passed: exact next/all text, rejected insertion retention,
  target/revision invalidation, private-mode rejection, hide cleanup, single
  consumption, cross-session rejection, size limit and whole-block deletion.
  These test the core model; they do not substitute for framework lifecycle tests.
- Lint: zero errors, three warnings (newer Gradle available, backup extraction
  rules, development app icon). No warning was suppressed to obtain this result.
- `aapt2 dump badging`: min SDK 26, target SDK 37, launcher and IME components.
  `aapt2 dump permissions`: no requested app permissions.
- Wrapper JAR matches Gradle's official 9.7.1 checksum. The distribution checksum
  is pinned in `gradle-wrapper.properties`.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `ce7a2539a51c970d4f2340aaa2ee69e0b73112b563ee45220efbef8c7dccb924`.
Reports: `core/build/test-results/test/`, `app/build/reports/lint-results-debug.*`.
The new workflow has been syntax-checked locally; remote CI has not been run.

## Device result and remaining checks

One physical Android 16 / API 36 device was connected and authorized for ADB.
The initial installation attempt returned `INSTALL_FAILED_USER_RESTRICTED`.
The device was no longer available over ADB at the final check.
No successful RIMES installation, keyboard enablement, visual acceptance or real
host typing is claimed. No keyboard preference, app data or system security
setting was changed to bypass that restriction. The later lifecycle fix was
rebuilt locally and is included in the APK checksum above.

After installation is allowed, complete the [device checklist](README.md#device-acceptance).
Chinese input, chord/touch behavior, full Buffer editing, AI and translation
remain implementation work, not passed tests.
