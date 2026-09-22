package com.cy.codex.app

import com.cy.codex.protocol.AppServerClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The git status shown on the status card, mirroring `tui/src/branch_summary.rs`.
 *
 * The TUI computes this with `git` and `gh` subprocesses; on Android the same commands run through
 * `command/exec`, with the prompts disabled so a repository that needs credentials answers with an
 * error instead of hanging.
 */
data class GitSummary(
    val branch: String?,
    val additions: Int,
    val deletions: Int,
    val pullRequest: PullRequestSummary?,
) {
    /** `main +3 -1`, or `main · No changes`. */
    val diffLabel: String
        get() = if (additions == 0 && deletions == 0) "No changes" else "+$additions -$deletions"
}

data class PullRequestSummary(val number: Int, val url: String?)

/** Gather the branch, its diff against the default branch, and the open PR, best effort. */
internal suspend fun loadGitSummary(client: AppServerClient, cwd: String): GitSummary? {
    val branch = git(client, cwd, "branch", "--show-current")?.trim()?.takeIf { it.isNotEmpty() }
    val base = defaultBranch(client, cwd)
    var additions = 0
    var deletions = 0
    if (base != null) {
        val mergeBase = git(client, cwd, "merge-base", "HEAD", base)?.trim()?.takeIf { it.isNotEmpty() }
        if (mergeBase != null) {
            val numstat = git(client, cwd, "diff", "--numstat", "$mergeBase..HEAD")
            if (numstat != null) {
                val totals = parseNumstat(numstat)
                additions = totals.first
                deletions = totals.second
            }
        }
    }
    val pullRequest = pullRequest(client, cwd)
    if (branch == null && pullRequest == null) return null
    return GitSummary(branch, additions, deletions, pullRequest)
}

/**
 * The default branch to diff against: the remote's HEAD first, then the usual names.
 *
 * Mirrors the discovery order in `branch_summary.rs`: a configured `origin/HEAD` wins, then
 * whichever of `origin/main`, `origin/master`, `main`, `master` actually resolves.
 */
private suspend fun defaultBranch(client: AppServerClient, cwd: String): String? {
    val symbolic = git(client, cwd, "symbolic-ref", "--short", "refs/remotes/origin/HEAD")
        ?.trim()
        ?.removePrefix("origin/")
        ?.takeIf { it.isNotEmpty() }
    if (symbolic != null) return symbolic
    for (candidate in listOf("origin/main", "origin/master", "main", "master")) {
        val exists = client.execCommand(
            command = listOf("git", "rev-parse", "--verify", "--quiet", candidate),
            cwd = cwd,
            timeoutMs = GitTimeoutMs,
            env = GitEnvironment,
        ).getOrNull()
        if (exists != null && exists.exitCode == 0 && exists.stdout.isNotBlank()) return candidate
    }
    return null
}

private suspend fun pullRequest(client: AppServerClient, cwd: String): PullRequestSummary? {
    val result = client.execCommand(
        command = listOf("gh", "pr", "view", "--json", "number,url,state"),
        cwd = cwd,
        timeoutMs = GitTimeoutMs,
        env = GitEnvironment,
    ).getOrNull() ?: return null
    if (result.exitCode != 0) return null
    return parseGhPrJson(result.stdout)
}

private suspend fun git(client: AppServerClient, cwd: String, vararg args: String): String? {
    val result = client.execCommand(
        command = listOf("git") + args,
        cwd = cwd,
        timeoutMs = GitTimeoutMs,
        env = GitEnvironment,
    ).getOrNull() ?: return null
    return if (result.exitCode == 0) result.stdout else null
}

/** `git diff --numstat` totals: added and deleted lines across every file. */
internal fun parseNumstat(output: String): Pair<Int, Int> {
    var additions = 0
    var deletions = 0
    for (line in output.lineSequence()) {
        val parts = line.split('\t')
        if (parts.size < 3) continue
        additions += parts[0].toIntOrNull() ?: 0
        deletions += parts[1].toIntOrNull() ?: 0
    }
    return additions to deletions
}

/** `gh pr view --json number,url,state`: only an open pull request is reported. */
internal fun parseGhPrJson(output: String): PullRequestSummary? {
    val obj = runCatching { Json.parseToJsonElement(output).jsonObject }.getOrNull() ?: return null
    if ((obj["state"] as? JsonPrimitive)?.content?.uppercase() != "OPEN") return null
    val number = (obj["number"] as? JsonPrimitive)?.intOrNull ?: return null
    val url = (obj["url"] as? JsonPrimitive)?.content
    return PullRequestSummary(number, url)
}

private const val GitTimeoutMs = 5_000L

/** `branch_summary.rs` disables every interactive prompt; an auth failure is just an error. */
private val GitEnvironment = mapOf(
    "GIT_OPTIONAL_LOCKS" to "0",
    "GIT_TERMINAL_PROMPT" to "0",
    "GH_PROMPT_DISABLED" to "1",
)
