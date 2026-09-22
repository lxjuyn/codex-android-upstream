package com.cy.codex.protocol.protocol.v2

/**
 * `fs/…` — the sandboxed filesystem the server exposes to the client.
 *
 * Mirrors `schema/typescript/v2/Fs*.ts`. These are the *client's* file operations (the workspace
 * picker walking a tree, the composer attaching a file); the agent's own file access never appears
 * here, it arrives as a `ThreadItem`.
 *
 * Every path is absolute: the protocol's `AbsolutePathBuf` rejects anything else, so a relative path
 * is a client bug rather than something the server resolves.
 */

data class FsReadFileParams(val path: String)

/** File contents are base64 so a binary file round-trips unharmed. */
data class FsReadFileResponse(val dataBase64: String)

data class FsWriteFileParams(
    val path: String,
    val dataBase64: String,
)

data class FsCreateDirectoryParams(
    val path: String,
    val recursive: Boolean = true,
)

data class FsGetMetadataParams(val path: String)

data class FsRemoveParams(
    val path: String,
    val recursive: Boolean = false,
    /** Remove even when the path is a non-empty directory or is not writable. */
    val force: Boolean = false,
)

data class FsCopyParams(
    val sourcePath: String,
    val destinationPath: String,
    val recursive: Boolean = false,
)

/** `fs/watch`: the client asks the server to push [FsChangedNotification]s for one path. */
data class FsWatchParams(
    val path: String,
    /** Client-chosen correlation id; `fs/unwatch` and every change event carry it back. */
    val watchId: String,
)

data class FsUnwatchParams(val watchId: String)

/** `fs/changed`: one or more paths under a watched root changed. */
data class FsChangedNotification(
    val watchId: String,
    val changedPaths: List<String> = emptyList(),
)

/** `fs/readDirectory` entry. */
data class FsReadDirectoryEntry(
    /** Direct child name only — not a path. */
    val fileName: String,
    val isDirectory: Boolean = false,
    val isFile: Boolean = false,
    val isSymlink: Boolean = false,
)

data class FsReadDirectoryResponse(val entries: List<FsReadDirectoryEntry> = emptyList())
