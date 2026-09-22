#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest "$@"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p "$PROJECT_ROOT/artifacts"
adb shell am instrument -w \
  com.cy.codex.test/com.cy.codex.runtime.RuntimeSmokeInstrumentation \
  | tee "$PROJECT_ROOT/artifacts/device-smoke.txt"
if ! rg -q 'PASS native restart and thread resume' "$PROJECT_ROOT/artifacts/device-smoke.txt" ||
   rg -q 'FAIL|INSTRUMENTATION_FAILED|Process crashed' "$PROJECT_ROOT/artifacts/device-smoke.txt"; then
    echo "Device integration test failed; see artifacts/device-smoke.txt" >&2
    exit 1
fi
