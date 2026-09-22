package com.cy.codex.app

import org.junit.Test
import kotlin.test.assertEquals

class WorktreesTest {
    @Test
    fun `porcelain blocks become paths with their branch`() {
        val output = """
            worktree /repo
            HEAD abc123
            branch refs/heads/main

            worktree /repo-feature
            HEAD def456
            branch refs/heads/feature/login

            worktree /repo-detached
            HEAD 987cba
            detached

        """.trimIndent()

        assertEquals(
            listOf(
                GitWorktree("/repo", "main", detached = false, bare = false),
                GitWorktree("/repo-feature", "feature/login", detached = false, bare = false),
                GitWorktree("/repo-detached", null, detached = true, bare = false),
            ),
            parseWorktrees(output),
        )
    }

    @Test
    fun `unknown keys are ignored`() {
        val output = "worktree /repo\nHEAD abc\nbranch refs/heads/main\nlocked reason\nprunable gitdir file missing\n"
        assertEquals(listOf(GitWorktree("/repo", "main", false, false)), parseWorktrees(output))
    }

    @Test
    fun `branch names are sanitized into sibling paths`() {
        assertEquals("/work/repo-feature-login", worktreePathFor("/work/repo", "feature/login"))
        assertEquals("/repo-worktree", worktreePathFor("/repo", ""))
    }
}
