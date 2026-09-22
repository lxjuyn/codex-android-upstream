package com.cy.codex

import com.cy.codex.protocol.AppServerClient

/**
 * What one `/diff` read found.
 *
 * Mirrors `codex-rs/tui/src/get_git_diff.rs`, whose return value is the pair "is this a repo" and
 * "the diff text": a clean tree and a non-repo are different answers, not an empty diff.
 */
sealed interface GitDiffResult {
    /** Unified diff text for every tracked and untracked change. */
    data class Changes(val diff: String) : GitDiffResult

    /** The work tree has no tracked or untracked changes. */
    data object Clean : GitDiffResult

    /** The working directory is not inside a Git work tree. */
    data object NotARepository : GitDiffResult

    /** Git itself failed, or the server could not run it. */
    data class Failed(val message: String) : GitDiffResult
}

/**
 * Computes the working-tree diff through the only execution surface this client has.
 *
 * `command/exec` runs in the same workspace with the packaged toolchain on `PATH`, so this is the
 * toolchain's own `git` — no new client method is needed, and no repository state leaves the
 * device. The command lines follow `tui/src/get_git_diff.rs`: tracked changes from `git diff`,
 * untracked files from `git ls-files --others --exclude-standard` rendered one by one as
 * `/dev/null` diffs. Two deliberate differences:
 *
 *  - `--color` is dropped, because the renderer reads plain unified diff text (`parseTurnDiff`).
 *  - `core.quotePath=false` is set for the untracked listing, so a non-ASCII path survives as
 *    itself instead of git's quoted octal form, which `--no-index` would not resolve.
 *
 * `core.hooksPath` is pointed at the null device like upstream does, so a configured hook cannot
 * run as a side effect of reading a diff. Textconv and external diff drivers are disabled for the
 * same reason. Exit code 1 is a success for a diff command: git uses it to mean "differences".
 */
object GitDiff {
    /** Per-command budget, the same cap upstream puts on each of its git calls. */
    private const val CommandTimeoutMs = 30_000L

    private val NoHooks = listOf("-c", "core.hooksPath=/dev/null")

    private val DiffFlags = listOf(
        "--no-textconv",
        "--no-ext-diff",
        "--submodule=short",
        "--ignore-submodules=dirty",
    )

    /** `git diff` over tracked changes. */
    internal fun trackedDiffCommand(): List<String> = listOf("git") + NoHooks + listOf("diff") + DiffFlags

    /** `git ls-files`: the untracked files, one path per line. */
    internal fun untrackedListCommand(): List<String> = listOf("git") + NoHooks +
        listOf("-c", "core.quotePath=false", "ls-files", "--others", "--exclude-standard")

    /** A `/dev/null` diff for one untracked [path]. */
    internal fun untrackedDiffCommand(path: String): List<String> =
        listOf("git") + NoHooks + listOf("diff") + DiffFlags + listOf("--no-index", "--", "/dev/null", path)

    /** `git rev-parse`: is [cwd] inside a work tree at all? */
    internal fun insideRepositoryCommand(): List<String> =
        listOf("git") + NoHooks + listOf("rev-parse", "--is-inside-work-tree")

    suspend fun load(client: AppServerClient, cwd: String): GitDiffResult {
        val inside = client.execCommand(insideRepositoryCommand(), cwd, CommandTimeoutMs)
            .getOrElse { return GitDiffResult.Failed(it.message.orEmpty()) }
        if (inside.exitCode != 0) return GitDiffResult.NotARepository

        val tracked = client.execCommand(trackedDiffCommand(), cwd, CommandTimeoutMs)
            .getOrElse { return GitDiffResult.Failed(it.message.orEmpty()) }
        if (tracked.exitCode > 1) return GitDiffResult.Failed(tracked.stderr.ifBlank { "git diff" })

        val untracked = client.execCommand(untrackedListCommand(), cwd, CommandTimeoutMs)
            .getOrElse { return GitDiffResult.Failed(it.message.orEmpty()) }
        if (untracked.exitCode != 0) {
            return GitDiffResult.Failed(untracked.stderr.ifBlank { "git ls-files" })
        }

        val payload = StringBuilder(tracked.stdout)
        for (file in untracked.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }) {
            val diff = client.execCommand(untrackedDiffCommand(file), cwd, CommandTimeoutMs)
                .getOrElse { return GitDiffResult.Failed(it.message.orEmpty()) }
            if (diff.exitCode > 1) return GitDiffResult.Failed(diff.stderr.ifBlank { "git diff --no-index" })
            payload.append(diff.stdout)
        }
        return if (payload.isBlank()) GitDiffResult.Clean else GitDiffResult.Changes(payload.toString())
    }
}
