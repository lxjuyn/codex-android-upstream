# Android Native Runtime

Project overview, build entry points and device verification live in [../README.md](../README.md);
remaining work is tracked in [../docs/TODO.md](../docs/TODO.md).

`src/bridge.rs` starts the real Codex app-server in process. The JNI API owns
runtime handles and carries JSON-RPC requests, responses, and notifications over
bounded queues. `codex-helper` provides the upstream apply-patch, filesystem, and
argv0 execution entry points required when Codex launches a child process.

## Build

The toolchain dependencies (openssl/sqlite/libffi prefixes) are built by the same
Gradle invocation. The Android build uses Rust 1.95.0 and the NDK selected from
`ANDROID_NDK_HOME` or `local.properties`.

```sh
./gradlew :native:buildJni                # JNI library + helper
./gradlew :native:buildJni -PnativeSmoke  # also codex-smoke for the device smoke
./gradlew :native:hostSmokeTest           # host kernel smoke, no model calls
ANDROID_SERIAL=DEVICE_SERIAL bash native/file-lock-smoke-test.sh
```

Production outputs are `out/arm64-v8a/libcodex_android_jni.so` and
`out/arm64-v8a/libcodex_helper.so`. Both use 16 KiB ELF load alignment. The helper
is an executable packaged under Android's native-library naming convention.
The final link includes the NDK compiler-rt archive for outlined ARM atomics used
by C dependencies and rejects unresolved symbols in both the JNI library and helper.

## Isolated Patches

`:native:prepareUpstream` creates `build-upstream/` from the checked-out Codex
submodule revision and applies `patches/android-runtime.patch`. It records and
reverses only its own previous patch when preparing a new revision. The source
submodule remains unchanged. This patch makes Android use the explicitly
configured packaged Bash without Unix account lookup or profile files.

Rust 1.95.0's `std::fs::File` locking methods exclude Android from their supported
Unix platforms, even though Bionic implements `flock`. Codex requires these locks
for its installation ID, rollouts, OAuth coordination, and other state. The
checked-in `patches/rust-std-android-flock.patch` enables Android for all five
methods: `lock`, `lock_shared`, `try_lock`, `try_lock_shared`, and `unlock`.

`:native:prepareRustStd` copies rust-src to the generated `rust-sysroot/` directory.
Compiler binaries and prebuilt host libraries are linked from the pinned
toolchain. A generated `native/build/rustc-android.sh` selects that sysroot, and Cargo's
`-Z build-std=std,panic_unwind` rebuilds the Android standard library. The build
sets `RUSTC_BOOTSTRAP=1` because build-std is still unstable; it does not modify
the globally installed rust-src or require changing Codex's file-lock calls.

## Runtime Contract

The Android host installs environment variables before loading the native
library. Startup validates the supplied environment instead of mutating the
process environment from Rust worker threads. `CODEX_HOME`, the shell path, and
the helper path must be absolute; Android requires `toolchainRoot/bin/bash`
and a `PATH` starting with that toolchain's `bin` directory. Changing an installed
runtime environment requires restarting the app process.

The in-process server performs its own initialize/initialized handshake. Kotlin
must start its normal RPC requests after `nativeStart` returns. Shutdown stops
pending work and rejects subsequent sends.

Messages cross JNI as UTF-8 bytes plus a `JsonRpcMessageKind` tag (0 request,
1 notification, 2 response, 3 error). `bridge.rs` deserializes each payload
directly into the typed `ClientRequest` / `ClientNotification` / server-response
type with one `serde_json` pass, and writes events with one `serde_json::to_vec`
from the typed `ServerNotification`; no JSON-RPC envelope is materialized as an
intermediate `serde_json::Value`. The byte transport also removes the Java string
Modified UTF-8 conversion from the message path.

## Verification

The host smoke runs real app-server requests without model inference: model and
account reads, thread creation, shell execution, direct command execution,
shutdown, restart, thread read/resume, and archive. It also executes all three
helper entry points. Upstream intentionally hides threads whose preview is
empty from `thread/list`; shell-only persistence is verified by thread ID and
the recorded command output after restart.

The file-lock probe runs the patched standard library on a connected Android
device and verifies exclusive contention, shared coexistence, explicit unlock,
and lock release on close. The app's instrumentation covers JNI and toolchain
execution under the application UID.

For optional standalone core verification under the device's shell UID:

```sh
./gradlew :native:buildJni -PnativeSmoke
ANDROID_SERIAL=DEVICE_SERIAL bash native/device-smoke-test.sh
```
