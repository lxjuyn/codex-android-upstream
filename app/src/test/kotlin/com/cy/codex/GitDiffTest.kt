package com.cy.codex

import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.ConnectionState
import com.cy.codex.protocol.protocol.RequestId
import com.cy.codex.protocol.protocol.v2.ClientInfo
import com.cy.codex.protocol.protocol.v2.CommandExecResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitDiffTest {
    @Test
    fun `a clean work tree is not an empty diff`() = runTest {
        val result = GitDiff.load(FakeClient { response(it) }, "/workspace")
        assertEquals(GitDiffResult.Clean, result)
    }

    @Test
    fun `a directory outside a repository is reported as such`() = runTest {
        val result = GitDiff.load(
            FakeClient { command ->
                if ("rev-parse" in command) response(command, exitCode = 128) else response(command)
            },
            "/workspace",
        )
        assertEquals(GitDiffResult.NotARepository, result)
    }

    @Test
    fun `tracked and untracked changes are concatenated in order`() = runTest {
        val seen = mutableListOf<List<String>>()
        val client = FakeClient { command ->
            seen += command
            when {
                "rev-parse" in command -> response(command)
                "ls-files" in command -> response(command, stdout = "new file.txt\n")
                "--no-index" in command -> response(command, exitCode = 1, stdout = "untracked\n")
                else -> response(command, exitCode = 1, stdout = "tracked\n")
            }
        }
        val result = GitDiff.load(client, "/workspace")
        assertEquals(GitDiffResult.Changes("tracked\nuntracked\n"), result)

        val untracked = seen.single { "--no-index" in it }
        // `--` keeps a leading-dash file name from being read as an option, and the path is passed
        // as one argument so a name with spaces survives.
        assertEquals(listOf("--", "/dev/null", "new file.txt"), untracked.takeLast(3))
        assertTrue("-c" in seen.single { "ls-files" in it })
    }

    @Test
    fun `a diff that fails is reported with git stderr`() = runTest {
        val result = GitDiff.load(
            FakeClient { command ->
                if ("diff" in command) response(command, exitCode = 2, stderr = "fatal: bad revision") else response(command)
            },
            "/workspace",
        )
        assertEquals(GitDiffResult.Failed("fatal: bad revision"), result)
    }

    private fun response(
        command: List<String>,
        exitCode: Int = 0,
        stdout: String = "",
        stderr: String = "",
    ): CommandExecResponse {
        assertTrue(command.first() == "git")
        return CommandExecResponse(exitCode = exitCode, stdout = stdout, stderr = stderr)
    }

    private class FakeClient(
        private val onExec: (List<String>) -> CommandExecResponse,
    ) : AppServerClient {
        override val events: Flow<AppServerEvent> = flowOf()
        override val requests: Flow<ApprovalRequest> = flowOf()
        override val connection = flowOf(ConnectionState.Ready)
        override suspend fun initialize(clientInfo: ClientInfo) = Result.success(Unit)
        override suspend fun respond(requestId: RequestId, response: ApprovalResponse) = Unit
        override suspend fun execCommand(
            command: List<String>,
            cwd: String?,
            timeoutMs: Long?,
            tty: Boolean,
            env: Map<String, String>?,
        ): Result<CommandExecResponse> = Result.success(onExec(command))

        override suspend fun close() = Unit
    }
}
