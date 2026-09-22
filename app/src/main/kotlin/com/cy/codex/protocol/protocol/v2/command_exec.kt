package com.cy.codex.protocol.protocol.v2

/**
 * `command/exec` and `process/…` — running a process outside a turn.
 *
 * Mirrors `schema/typescript/v2/{CommandExecParams, CommandExecResponse, ProcessSpawnParams,
 * ProcessOutputDeltaNotification}.ts`.
 *
 * These are the "one-off command" family the TUI uses for `/shell`: the client owns the process and
 * reads its output from notifications, unlike a turn where the agent owns it and the client only
 * watches. [CommandExecParams] streams (`streamStdoutStderr`), so the companion notification is the
 * normal way to see output and [CommandExecResponse] is only the final summary.
 */

/** `command/exec` params. `command` is argv, never a shell string. */
data class CommandExecParams(
    val command: List<String>,
    val cwd: String? = null,
    val env: Map<String, String>? = null,
    val timeoutMs: Long? = null,
    /** Bytes of stdout kept before the server stops buffering. */
    val outputBytesCap: Int? = null,
    val disableOutputCap: Boolean = false,
    val disableTimeout: Boolean = false,
    /** Permission profile the command runs under; `null` means the session default. */
    val permissionProfile: String? = null,
    /** Attach to an existing process instead of starting a new one. */
    val processId: String? = null,
    /** Ask for a pty; required for anything interactive. */
    val tty: Boolean = false,
    val size: TerminalSize? = null,
    val streamStdin: Boolean = false,
    val streamStdoutStderr: Boolean = true,
)

/** `command/exec` response, returned once the process exits. */
data class CommandExecResponse(
    val exitCode: Int = 0,
    val stdout: String = "",
    val stderr: String = "",
)

/** `command/exec/write`: feed stdin, or close it. */
data class CommandExecWriteParams(
    val processId: String,
    val deltaBase64: String? = null,
    val closeStdin: Boolean = false,
)

/** `command/exec/resize`: pty window size. */
data class CommandExecResizeParams(
    val processId: String,
    val size: TerminalSize,
)

/** `command/exec/terminate`. */
data class CommandExecTerminateParams(val processId: String)

data class TerminalSize(val rows: Int, val cols: Int)

/** Which stream an output chunk came from. */
enum class CommandExecStream(val wire: String) {
    Stdout("stdout"),
    Stderr("stderr"),
    Stdin("stdin"),
    ;

    companion object {
        fun fromWire(value: String?): CommandExecStream =
            entries.firstOrNull { it.wire == value } ?: Stdout
    }
}

/** `command/exec/outputDelta`. */
data class CommandExecOutputDeltaNotification(
    val processId: String,
    val deltaBase64: String = "",
    val stream: CommandExecStream = CommandExecStream.Stdout,
    /** `true` once the byte cap was hit, so the UI can say the output is truncated. */
    val capReached: Boolean = false,
)

// ---------------------------------------------------------------------------------------------
// process/* — the same idea, but for a long-lived pty the client keeps talking to
// ---------------------------------------------------------------------------------------------

/**
 * `process/spawn` params.
 *
 * [processHandle] is client-supplied and connection-scoped: it is how the follow-up
 * `writeStdin`/`resizePty`/`kill` calls name this process, which is why the client returns the
 * handle it generated rather than one from the server.
 */
data class ProcessSpawnParams(
    val command: List<String>,
    val processHandle: String,
    /** Absolute working directory for the process. */
    val cwd: String? = null,
    val tty: Boolean = false,
    val streamStdin: Boolean = false,
    val streamStdoutStderr: Boolean = false,
    val outputBytesCap: Long? = null,
    val timeoutMs: Long? = null,
    val env: Map<String, String?>? = null,
    val size: TerminalSize? = null,
)

data class ProcessWriteStdinParams(
    val processHandle: String,
    val deltaBase64: String? = null,
    val closeStdin: Boolean = false,
)

data class ProcessResizePtyParams(
    val processHandle: String,
    val size: TerminalSize,
)

data class ProcessKillParams(val processHandle: String)

/** Which stream a `process/outputDelta` chunk belongs to. */
enum class ProcessOutputStream(val wire: String) {
    /** stdout stream; PTY mode multiplexes terminal output here. */
    Stdout("stdout"),

    Stderr("stderr"),
    ;

    companion object {
        fun fromWire(value: String?): ProcessOutputStream =
            entries.firstOrNull { it.wire == value } ?: Stdout
    }
}

/** `process/outputDelta`. */
data class ProcessOutputDeltaNotification(
    val processHandle: String,
    val stream: ProcessOutputStream = ProcessOutputStream.Stdout,
    val deltaBase64: String = "",
    /** True on the final streamed chunk when output was truncated by `outputBytesCap`. */
    val capReached: Boolean = false,
)

/** `process/exited`. */
data class ProcessExitedNotification(
    val processHandle: String,
    val exitCode: Int = 0,
    val stdout: String = "",
    val stdoutCapReached: Boolean = false,
    val stderr: String = "",
    val stderrCapReached: Boolean = false,
)
