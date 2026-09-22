#!/usr/bin/env bash
# Build the file-lock probe with Gradle and run it on a connected device.
set -euo pipefail
NATIVE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ABI="${1:-arm64-v8a}"
(cd "$NATIVE_ROOT/.." && ./gradlew --console=plain :native:buildFileLockProbe)
probe="$NATIVE_ROOT/target/$([ "$ABI" = arm64-v8a ] && echo aarch64-linux-android)/release/codex-android-file-lock-probe"
remote="$(adb shell mktemp -d /data/local/tmp/codex-lock-smoke.XXXXXX | tr -d '\r')"
[[ "$remote" == /data/local/tmp/codex-lock-smoke.* ]] || { echo "Invalid device scratch path" >&2; exit 1; }
trap 'adb shell rm -r "$remote" >/dev/null' EXIT
adb push "$probe" "$remote/probe" >/dev/null
adb shell chmod 700 "$remote/probe"
adb shell "$remote/probe" "$remote/file.lock"
