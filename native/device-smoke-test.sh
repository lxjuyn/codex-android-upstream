#!/usr/bin/env bash
set -euo pipefail

NATIVE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ABI="${1:-arm64-v8a}"
PACKED="$NATIVE_ROOT/../toolchain/out/android/$ABI"
SMOKE="$NATIVE_ROOT/target/aarch64-linux-android/release/codex-smoke"
[[ "$ABI" == arm64-v8a ]] || { echo "Only arm64-v8a is supported" >&2; exit 1; }
[[ -x "$SMOKE" ]] || { echo "Run ./gradlew :native:buildJni -PnativeSmoke first" >&2; exit 1; }
[[ -f "$PACKED/native-manifest.txt" ]] || { echo "Run ./gradlew :toolchain:packJniLibs first" >&2; exit 1; }

remote="$(adb shell mktemp -d /data/local/tmp/codex-core-smoke.XXXXXX | tr -d '\r')"
[[ "$remote" == /data/local/tmp/codex-core-smoke.* ]] || { echo "Invalid device scratch path" >&2; exit 1; }
trap 'adb shell rm -r "$remote" >/dev/null' EXIT
adb push "$PACKED/jniLibs" "$remote/native" >/dev/null
adb push "$PACKED/assets/toolchain" "$remote/toolchain" >/dev/null
adb push "$PACKED/native-manifest.txt" "$remote/native-manifest.txt" >/dev/null
adb push "$NATIVE_ROOT/out/$ABI/libcodex_helper.so" "$remote/native/libcodex_helper.so" >/dev/null
adb push "$SMOKE" "$remote/codex-smoke" >/dev/null

# This reconstructs the APK layout; app-UID JNI coverage lives in androidTest.
adb shell sh -s -- "$remote" <<'DEVICE'
set -eu
base="$1"
farm="$base/toolchain"
while IFS='|' read -r kind relative target; do
    case "$kind" in
        abi|data) continue ;;
    esac
    mkdir -p "$farm/$(dirname "$relative")"
    case "$kind" in
        file) ln -s "$base/native/$target" "$farm/$relative" ;;
        link) ln -s "$target" "$farm/$relative" ;;
    esac
done < "$base/native-manifest.txt"
ln -s "$base/native/libcodex_helper.so" "$farm/bin/apply_patch"
mkdir -p "$base/home" "$base/tmp" "$base/cache"
chmod 700 "$base/codex-smoke" "$base/native/libcodex_helper.so"
export HOME="$base/home" CODEX_HOME="$base/runtime/codex"
export CODEX_SQLITE_HOME="$CODEX_HOME"
export XDG_CONFIG_HOME="$HOME/.config" XDG_DATA_HOME="$HOME/.local/share"
export XDG_STATE_HOME="$HOME/.local/state" XDG_CACHE_HOME="$base/cache"
export TMPDIR="$base/tmp" TMP="$base/tmp" TEMP="$base/tmp"
export SHELL="$farm/bin/bash" CODEX_SHELL="$farm/bin/bash"
export GIT_EXEC_PATH="$farm/libexec/git-core" GIT_TEMPLATE_DIR="$farm/share/git-core/templates"
export GIT_SSL_CAINFO="$farm/share/cacert.pem" CURL_CA_BUNDLE="$farm/share/cacert.pem"
export SSL_CERT_FILE="$farm/share/cacert.pem" PYTHONHOME="$farm" PYTHONDONTWRITEBYTECODE=1
export MAGIC="$farm/share/misc/magic" LANG=C.UTF-8 TERM=xterm-256color
export PATH="$farm/bin:$farm/sbin"
exec "$base/codex-smoke" "$base/runtime" "$base/native/libcodex_helper.so"
DEVICE
