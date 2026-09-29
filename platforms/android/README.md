# RIMES Android

Native Android application and input method, targeting the behavior of the iOS
keyboard. This is the first development foundation, **0.1.0-dev.1**, not an iOS
feature-complete port. Minimum Android version: 8.0 / API 26.

## Current behavior

- System `InputMethodService`, setup screen, English QWERTY, numbers and symbols.
- In-memory Default Buffer: word blocks, exact next/all insertion, whole-block
  deletion, clear, and an explicit on/off switch. No automatic insertion.
- Delivery stays on the current `InputConnection`. A prepared block is consumed
  only if `commitText` accepts it and its target/revision remains current.
- Switching editor, closing the keyboard or destroying the service clears the
  draft. Password, numeric and `NO_PERSONALIZED_LEARNING` fields cannot use Buffer.
- No network permission, clipboard reads, text logging, accounts or persisted
  typing. Development installs use `org.scholay.rimes.android.debug`.
- English and simplified Chinese setup/control labels.

Not implemented yet: librime/JNI and Chinese schemes, inline Chinese composition,
chord input/sliding, candidate/association UI, Buffer cursor/selection editing,
repeat-delete and space gestures, iOS-equivalent layout, settings/profile transfer,
AI providers/streaming and translation. The basic key grid is provisional.

Android needs its own translation adapter; Apple Translation cannot run here.
Network providers and credentials will require explicit configuration and consent.
See [the platform roadmap](../../PLATFORM-ROADMAP.md) for acceptance milestones.

## Build and test

Use JDK 21 and the Android SDK. `JAVA_HOME` must point to that JDK, and
`ANDROID_HOME` to the SDK (or supply a local, ignored `local.properties`).
The checked-in wrapper pins Gradle 9.7.1 with the official distribution checksum;
AGP is pinned to 9.4.0. Install SDK platform `android-37.2` and build tools 36.0.0
using the SDK Manager after accepting the SDK terms in your development environment.

```sh
cd platforms/android
./gradlew --no-daemon :core:test :app:assembleDebug :app:lintDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (development key only).
Unit results: `core/build/test-results/test/`.
Lint results: `app/build/reports/lint-results-debug.html`.
The independent Android workflow performs these checks; it does not publish an
app or join the macOS release gate.

For an explicitly selected test device:

```sh
adb -s "$RIMES_ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$RIMES_ANDROID_SERIAL" shell am start -n \
  org.scholay.rimes.android.debug/org.scholay.rimes.android.SetupActivity
```

Use the setup buttons to enable and choose RIMES. Device installation permission
and the keyboard enable warning are controlled by Android. Do not uninstall or
clear another app to get past them. Keep the existing keyboard available.

## Device acceptance

In the setup playground, then in a separate host app:

1. Type and delete English, selected text, numbers and symbols. Enter must add a
   newline in a multiline field and respect a host's explicit editor action.
2. Enable Buffer; type `hello world`. Host text must remain unchanged. Insert one
   block, then all; each block must appear once and the queue must consume exactly
   the accepted text. A failed connection must not consume it.
3. With a pending draft, switch to the second field and close/reopen the keyboard.
   No draft may carry into the new session.
4. Open the password field. Buffer must be disabled and no previous draft shown.
5. Rotate, switch keyboard and background/foreground the host. Check focus,
   keyboard height, navigation insets and no unintended insertion.

Record APK checksum, OS/model, exact source commit and checks performed. JVM
tests and an APK build alone do not satisfy device acceptance.
