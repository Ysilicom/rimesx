#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

echo "=== 1. Preparing native engine & dictionary resources ==="
python3 "$ROOT/platforms/android/scripts/build-engine.py"

echo "=== 2. Building Release APK with Gradle ==="
export RIMES_ANDROID_KEYSTORE="$HOME/.config/rimesx/rimesx-release.jks"
export RIMES_ANDROID_STORE_PASSWORD="rimesx2026"
export RIMES_ANDROID_KEY_ALIAS="rimesx"
export RIMES_ANDROID_KEY_PASSWORD="rimesx2026"

cd "$ROOT/platforms/android"
./gradlew assembleRelease

mkdir -p "$ROOT/build-output"
APK="$(find "$ROOT/platforms/android/app/build/outputs/apk/release" -name "*.apk" | head -n 1)"
cp "$APK" "$ROOT/build-output/rimesx-release.apk"

echo "=== 3. Verifying Signature with apksigner ==="
bash "$ROOT/platforms/android/scripts/sign-rimesx.sh" "$ROOT/build-output/rimesx-release.apk"

echo "=== Build Successful! Output located at: $ROOT/build-output/rimesx-release.apk ==="
