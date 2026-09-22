package com.cy.codex.runtime

/** The library is loaded on the IO dispatcher after the toolchain is ready. */
object NativeBridge {
    fun load() {
        try {
            System.loadLibrary("codex_android_jni")
        } catch (error: LinkageError) {
            throw IllegalStateException("Cannot load the embedded Codex library: ${error.message}", error)
        }
    }

    /**
     * Messages cross JNI as UTF-8 bytes, not Java strings.
     *
     * `serde_json` already produces UTF-8 (`to_vec`) and consumes it (`from_slice`), and Kotlin
     * decodes with `String(bytes, UTF_8)`: one pass each way. `jstring` would add a Modified UTF-8
     * conversion per message and re-encode non-BMP characters as surrogate pairs.
     */
    external fun nativeStart(configJson: ByteArray): Long
    external fun nativeSend(handle: Long, kind: Int, json: ByteArray)
    external fun nativeReceive(handle: Long, timeoutMillis: Int): ByteArray?
    external fun nativeStop(handle: Long)
}
