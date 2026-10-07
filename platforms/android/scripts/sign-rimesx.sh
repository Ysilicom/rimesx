#!/usr/bin/env bash
set -euo pipefail

APK="${1:-}"
if [ -z "$APK" ]; then
    SEARCH_DIRS=("$HOME/rimes/build-output" "$HOME/rimes/platforms/android/app/build/outputs/apk/release" "$HOME/rimes/platforms/android/app/build/outputs/apk/debug")
    for d in "${SEARCH_DIRS[@]}"; do
        if [ -d "$d" ]; then
            APK="$(find "$d" -maxdepth 3 -name "*.apk" 2>/dev/null | sort -V | tail -n 1 || true)"
            [ -n "$APK" ] && break
        fi
    done
fi

KS="${RIMESX_KEYSTORE:-$HOME/.config/rimesx/rimesx-release.jks}"
APKSIGNER="${APKSIGNER:-$(find "$HOME/Android/Sdk/build-tools" -name apksigner 2>/dev/null | sort -V | tail -n 1 || true)}"

if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    echo "Error: APK file '$APK' not found. Please provide path: $0 <path-to-apk>" >&2
    exit 1
fi
if [ ! -f "$KS" ]; then
    echo "Error: Keystore $KS not found." >&2
    exit 1
fi
if [ -z "$APKSIGNER" ] || [ ! -x "$APKSIGNER" ]; then
    echo "Error: apksigner tool not found or not executable." >&2
    exit 1
fi

echo "Signing $APK with permanent RIMES X key ($KS)..."
"$APKSIGNER" sign --ks "$KS" \
    --ks-key-alias rimesx \
    --ks-pass pass:rimesx2026 \
    --key-pass pass:rimesx2026 \
    "$APK"

echo "✓ Successfully signed $APK with permanent RIMES X key"
"$APKSIGNER" verify --print-certs "$APK" | grep -E "Signer #1 certificate (DN|SHA-256)"
