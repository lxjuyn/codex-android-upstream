package com.cy.codex

import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.ConnectionState
import com.cy.codex.protocol.ThreadItemsPage
import com.cy.codex.protocol.ThreadTurnsPage
import com.cy.codex.protocol.protocol.RequestId
import com.cy.codex.protocol.protocol.v2.ClientInfo
import com.cy.codex.protocol.protocol.v2.ItemTextDelta
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalDecision
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalParams
import com.cy.codex.protocol.protocol.v2.CommandExecutionOutputDelta
import com.cy.codex.protocol.protocol.v2.FileChangeApprovalParams
import com.cy.codex.protocol.protocol.v2.FileChangePatchUpdatedNotification
import com.cy.codex.protocol.protocol.v2.FileUpdateChange
import com.cy.codex.protocol.protocol.v2.GuardianApprovalReviewNotification
import com.cy.codex.protocol.protocol.v2.PatchApplyStatus
import com.cy.codex.protocol.protocol.v2.PatchChangeKind
import com.cy.codex.protocol.protocol.v2.ThreadSessionState
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.protocol.protocol.v2.Thread
import com.cy.codex.protocol.protocol.v2.ThreadReadResponse
import com.cy.codex.protocol.protocol.v2.ThreadTokenUsage
import com.cy.codex.protocol.protocol.v2.TokenUsageBreakdown
import com.cy.codex.protocol.protocol.v2.TerminalInteraction
import com.cy.codex.protocol.protocol.v2.Turn
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.TurnsPage
import com.cy.codex.protocol.protocol.v2.WarningNotification
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.FileChangeItem
import com.cy.codex.protocol.protocol.item.ReasoningItem
import com.cy.codex.protocol.protocol.v2.UserInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatWidgetTest {
    @Test
    fun `failed submission retains the draft and leaves running state`() = runTest {
        val client = TestClient().apply { turnResult = Result.failure(IllegalStateException("offline")) }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.state.applyDraft("keep this draft")
        widget.action(AppEvent.SubmitUserMessage(listOf(UserInput.Text("keep this draft"))))
        assertTrue(widget.state.running)
        runCurrent()
        assertFalse(widget.state.running)
        assertEquals("keep this draft", widget.state.composerDraft)
        assertEquals(DiagnosticCode.SendFailed, widget.state.diagnostics.single().code)
    }

    @Test
    fun `failed approval response remains retryable`() = runTest {
        val client = TestClient().apply { rejectResponse = true }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val request = ApprovalRequest.Exec(
            RequestId("approval"), "thread", "turn", "item", 0,
            CommandExecutionApprovalParams("thread", "turn", "item", command = "pwd"),
        )
        client.requests.emit(request)
        runCurrent()
        val event = AppEvent.ResolveApproval(request.requestId, ApprovalResponse.CommandExecution(CommandExecutionApprovalDecision.Accept))
        widget.action(event)
        runCurrent()
        assertEquals(request, widget.currentApproval)
        assertFalse(widget.answeringApproval)
        assertNotNull(widget.approvalError)
        client.rejectResponse = false
        widget.action(event)
        runCurrent()
        assertNull(widget.currentApproval)
        assertNull(widget.approvalError)
    }

    @Test
    fun `an approval waits for the composer to go idle`() = runTest {
        val client = TestClient()
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        widget.noteComposerActivity()
        val request = ApprovalRequest.Exec(
            RequestId("approval"), "thread", "turn", "item", 0,
            CommandExecutionApprovalParams("thread", "turn", "item", command = "pwd"),
        )
        client.requests.emit(request)
        runCurrent()
        assertNull(widget.currentApproval)

        advanceTimeBy(500)
        runCurrent()
        assertNull(widget.currentApproval)

        advanceTimeBy(600)
        runCurrent()
        assertEquals(request, widget.currentApproval)
    }

    @Test
    fun `a request for another thread is counted instead of shown`() = runTest {
        val client = TestClient()
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        client.requests.emit(
            ApprovalRequest.Exec(
                RequestId("other-approval"), "other", "turn", "item", 0,
                CommandExecutionApprovalParams("other", "turn", "item", command = "pwd"),
            ),
        )
        runCurrent()
        assertNull(widget.currentApproval)
        assertEquals(listOf(ForeignApproval("other", 1)), widget.otherThreadApprovals)
    }

    @Test
    fun `parallel reviews aggregate and a denial stays overridable`() = runTest {
        val client = TestClient()
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val command = buildJsonObject {
            put("type", JsonPrimitive("command"))
            put("command", JsonPrimitive("rm -rf /tmp/x"))
        }
        client.events.emit(
            AppServerEvent.AutoApprovalReviewStarted(
                "thread",
                GuardianApprovalReviewNotification(
                    threadId = "thread", turnId = "turn", reviewId = "r1", status = "inProgress",
                    itemId = "item-1", action = command,
                ),
            ),
        )
        client.events.emit(
            AppServerEvent.AutoApprovalReviewStarted(
                "thread",
                GuardianApprovalReviewNotification(
                    threadId = "thread", turnId = "turn", reviewId = "r2", status = "inProgress",
                    itemId = "item-2", action = command,
                ),
            ),
        )
        runCurrent()
        assertEquals(2, widget.pendingReviews.size)

        client.events.emit(
            AppServerEvent.AutoApprovalReviewCompleted(
                "thread",
                GuardianApprovalReviewNotification(
                    threadId = "thread", turnId = "turn", reviewId = "r1", status = "denied",
                    itemId = "item-1", rationale = "outside the workspace", action = command,
                ),
            ),
        )
        runCurrent()
        assertEquals(listOf("r2"), widget.pendingReviews.map { it.id })
        assertEquals("rm -rf /tmp/x", widget.approvalDenials.single().summary)

        widget.action(AppEvent.DismissAutoReviewDenial("item-1"))
        assertTrue(widget.approvalDenials.isEmpty())
    }

    @Test
    fun `file change approval recovers the patch the request does not carry`() = runTest {
        val client = TestClient()
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val streamed = listOf(FileUpdateChange("a.txt", PatchChangeKind.Update, "@@ -1 +1 @@\n-old\n+new\n"))
        client.events.emit(
            AppServerEvent.FileChangePatchUpdated(
                "thread",
                FileChangePatchUpdatedNotification("thread", "turn", "patch", streamed),
            ),
        )
        runCurrent()
        client.requests.emit(
            ApprovalRequest.ApplyPatch(
                RequestId("file-approval"), "thread", "turn", "patch", 0L,
                FileChangeApprovalParams("thread", "turn", "patch"),
            ),
        )
        runCurrent()
        assertNotNull(widget.currentApproval)
        assertEquals(streamed, widget.fileChangeChanges("patch"))

        // The item is the authoritative copy: once it arrives, its changes are what is rendered.
        val fromItem = listOf(FileUpdateChange("a.txt", PatchChangeKind.Update, "@@ -1,2 +1,2 @@\n-old\n-older\n+new\n+newer\n"))
        client.events.emit(
            AppServerEvent.ItemStarted(
                "thread", "turn",
                FileChangeItem("patch", fromItem, PatchApplyStatus.InProgress),
            ),
        )
        runCurrent()
        assertEquals(fromItem, widget.fileChangeChanges("patch"))
    }

    @Test
    fun `failed resume exits loading and remains closed`() = runTest {
        val widget = ChatWidget(TestClient(), backgroundScope)
        var loaded: Result<ThreadReadResponse>? = null
        widget.open("unavailable") { loaded = it }
        runCurrent()
        assertFalse(widget.state.loading)
        assertFalse(widget.state.open)
        assertTrue(widget.state.status is ThreadStatus.SystemError)
        assertTrue(loaded?.isFailure == true)
    }

    @Test
    fun `open reports persisted thread metadata and history without a preview`() = runTest {
        val thread = testThread("shell-only", preview = "", cwd = "/workspace")
        val actual = CommandExecutionItem("actual", "pwd", "/workspace", aggregatedOutput = "/workspace\n", exitCode = 0)
        val client = TestClient().apply {
            resumeResult = Result.success(ThreadSessionState(threadId = thread.id))
            historyResult = Result.success(ThreadReadResponse(thread, listOf(actual)))
        }
        val widget = ChatWidget(client, backgroundScope)
        var loaded: Result<ThreadReadResponse>? = null
        widget.open(thread.id) { loaded = it }
        runCurrent()
        assertEquals(thread, loaded?.getOrThrow()?.thread)
        assertEquals(listOf(actual), widget.state.items.toList())
        assertTrue(widget.state.open)
    }

    @Test
    fun `load earlier prepends the older page and follows its cursor`() = runTest {
        val thread = testThread("paged")
        val newest = CommandExecutionItem("newest", "second", "/workspace", aggregatedOutput = "2\n", exitCode = 0)
        val older = CommandExecutionItem("older", "first", "/workspace", aggregatedOutput = "1\n", exitCode = 0)
        val client = TestClient().apply {
            resumeResult = Result.success(
                ThreadSessionState(
                    threadId = thread.id,
                    thread = thread,
                    initialTurnsPage = TurnsPage(
                        data = listOf(Turn("turn-newest", listOf(newest))),
                        nextCursor = "cursor-1",
                    ),
                ),
            )
            // `thread/turns/list` pages backwards, so its data arrives newest-first.
            earlierResult = Result.success(ThreadTurnsPage(listOf(Turn("turn-older", listOf(older))), nextCursor = null))
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.open(thread.id)
        runCurrent()
        assertEquals(listOf(newest), widget.state.items.toList())
        assertTrue(widget.canLoadEarlier)

        widget.loadEarlier()
        runCurrent()
        assertEquals(listOf(older, newest), widget.state.items.toList())
        assertFalse(widget.canLoadEarlier)
    }

    @Test
    fun `failed earlier page keeps the cursor for a retry`() = runTest {
        val thread = testThread("paged")
        val client = TestClient().apply {
            resumeResult = Result.success(
                ThreadSessionState(
                    threadId = thread.id,
                    thread = thread,
                    initialTurnsPage = TurnsPage(data = emptyList(), nextCursor = "cursor-1"),
                ),
            )
            earlierResult = Result.failure(IllegalStateException("offline"))
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.open(thread.id)
        runCurrent()

        widget.loadEarlier()
        runCurrent()
        assertTrue(widget.canLoadEarlier)
        assertFalse(widget.loadingEarlier)
    }

    @Test
    fun `first screen uses the bounded resume page without a full read`() = runTest {
        val thread = testThread("long")
        val first = AgentMessageItem("first", "newest kept")
        val dropped = AgentMessageItem("dropped", "whole rollout")
        val client = TestClient().apply {
            resumeResult = Result.success(
                ThreadSessionState(
                    threadId = thread.id,
                    thread = thread,
                    initialTurnsPage = TurnsPage(data = listOf(Turn("turn-1", listOf(first))), nextCursor = "cursor-1"),
                ),
            )
            // A full read would surface `dropped`; the bounded first screen must never make it.
            historyResult = Result.success(ThreadReadResponse(thread, listOf(dropped)))
        }
        val widget = ChatWidget(client, backgroundScope)
        var loaded: Result<ThreadReadResponse>? = null
        widget.open(thread.id) { loaded = it }
        runCurrent()

        assertEquals(listOf(first), widget.state.items.toList())
        assertEquals(thread, loaded?.getOrThrow()?.thread)
        assertFalse(client.readThreadCalled)
        assertTrue(widget.canLoadEarlier)
    }

    @Test
    fun `failed history read can clear the stale session for startup fallback`() = runTest {
        val client = TestClient().apply {
            resumeResult = Result.success(ThreadSessionState(threadId = "deleted"))
        }
        val widget = ChatWidget(client, backgroundScope)
        var failed = false
        widget.open("deleted") { result ->
            failed = result.isFailure
            if (failed) widget.clear()
        }
        runCurrent()
        assertTrue(failed)
        assertFalse(widget.state.open)
        assertFalse(widget.state.loading)
        assertEquals("", widget.state.threadId)
        assertEquals("", widget.state.config.threadId)
        assertTrue(widget.state.items.isEmpty())
        assertTrue(widget.state.diagnostics.isEmpty())
    }

    @Test
    fun `switching threads clears old running and streaming state`() {
        val state = SessionState()
        state.bindThread("first", ThreadSessionState(threadId = "first"))
        state.applyStatus(ThreadStatus.Active())
        state.applyStreaming("old-message")
        state.beginLoad("second")
        assertFalse(state.open)
        assertFalse(state.running)
        assertNull(state.streamingItemId)
        assertTrue(state.loading)
        assertEquals("second", state.threadId)
    }

    @Test
    fun `events for a hidden thread are replayed when it opens`() = runTest {
        val buffered = AgentMessageItem("buffered", "written while away")
        val client = TestClient().apply {
            resumeResult = Result.success(ThreadSessionState(threadId = "second"))
            historyResult = Result.success(ThreadReadResponse(testThread("second"), emptyList()))
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "first"))
        widget.attach()
        client.events.emit(AppServerEvent.ItemCompleted("second", "turn", buffered))
        client.events.emit(AppServerEvent.WarningEvent("second", WarningNotification("second", "heads up")))
        client.events.emit(AppServerEvent.AgentMessageDelta("second", ItemTextDelta("second", "turn", "buffered", "ignored")))
        runCurrent()
        // Nothing from the hidden thread leaks into the open transcript.
        assertTrue(widget.state.items.isEmpty())
        assertTrue(widget.state.diagnostics.isEmpty())

        widget.open("second")
        runCurrent()

        assertEquals(listOf(buffered), widget.state.items.toList())
        assertEquals("heads up", widget.state.diagnostics.single().message)
    }

    @Test
    fun `compaction reloads real history without inventing token usage`() = runTest {
        val actual = AgentMessageItem("actual", "Persisted history")
        val client = TestClient().apply {
            historyResult = Result.success(ThreadReadResponse(testThread("thread"), listOf(actual)))
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.state.applyUsage(ThreadTokenUsage(
            total = TokenUsageBreakdown(8_000, 0, 0, 500, 0),
            last = TokenUsageBreakdown(8_000, 0, 0, 500, 0),
        ))
        widget.state.upsert(AgentMessageItem("old", "Old history"))
        widget.attach()
        client.events.emit(AppServerEvent.ThreadCompacted("thread", null))
        runCurrent()
        assertEquals(listOf(actual), widget.state.items.toList())
        assertEquals(8_000L, widget.state.usage.total.totalTokens)
    }

    @Test
    fun `agent deltas buffer into a stream without rewriting the item`() = runTest {
        val client = TestClient()
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val id = "message"

        client.events.emit(AppServerEvent.ItemStarted("thread", "turn", AgentMessageItem(id, "")))
        runCurrent()
        val revisionAfterStart = widget.state.itemsRevision

        client.events.emit(AppServerEvent.AgentMessageDelta("thread", ItemTextDelta("thread", "turn", id, "Hello ")))
        client.events.emit(AppServerEvent.AgentMessageDelta("thread", ItemTextDelta("thread", "turn", id, "world")))
        runCurrent()

        // Deltas wait for the commit tick, so nothing has been parsed yet.
        assertNull(widget.state.stream(id))
        advanceTimeBy(Motion.StreamCommitIntervalMs + 1)
        runCurrent()

        // The item body stays empty while the deltas are buffered, so nothing copies the answer per
        // token, and the revision does not move, so folds keyed on it do not rerun per delta.
        assertEquals("", (widget.state.items.single() as AgentMessageItem).text)
        assertEquals(revisionAfterStart, widget.state.itemsRevision)
        assertEquals(id, widget.state.streamingItemId)
        assertEquals("Hello world", assertNotNull(widget.state.stream(id)).text)

        client.events.emit(AppServerEvent.ItemCompleted("thread", "turn", AgentMessageItem(id, "Hello world")))
        runCurrent()

        assertEquals("Hello world", (widget.state.items.single() as AgentMessageItem).text)
        assertNull(widget.state.stream(id))
        assertNull(widget.state.streamingItemId)
    }

    @Test
    fun `completion without a text body keeps the streamed buffer`() = runTest {
        val client = TestClient()
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val id = "message"

        client.events.emit(AppServerEvent.AgentMessageDelta("thread", ItemTextDelta("thread", "turn", id, "kept")))
        runCurrent()
        client.events.emit(AppServerEvent.ItemCompleted("thread", "turn", AgentMessageItem(id, "")))
        runCurrent()

        assertEquals("kept", (widget.state.items.single() as AgentMessageItem).text)
    }

    @Test
    fun `reasoning and command output deltas buffer and flush in arrival order`() = runTest {
        val client = TestClient()
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val reasoning = "reasoning"
        val command = "command"

        client.events.emit(AppServerEvent.ItemStarted("thread", "turn", CommandExecutionItem(command, "ls", "/")))
        client.events.emit(AppServerEvent.ReasoningTextDelta("thread", ItemTextDelta("thread", "turn", reasoning, "think ")))
        client.events.emit(AppServerEvent.ReasoningTextDelta("thread", ItemTextDelta("thread", "turn", reasoning, "more")))
        client.events.emit(AppServerEvent.CommandOutputDelta("thread", CommandExecutionOutputDelta("thread", "turn", command, "out ")))
        client.events.emit(AppServerEvent.CommandTerminalInteraction("thread", TerminalInteraction("thread", "turn", command, "proc", "in\n")))
        client.events.emit(AppServerEvent.CommandOutputDelta("thread", CommandExecutionOutputDelta("thread", "turn", command, "done")))
        runCurrent()

        // Nothing is committed until the tick.
        assertNull(widget.state.item(reasoning))
        assertNull((widget.state.item(command) as CommandExecutionItem).aggregatedOutput)

        advanceTimeBy(Motion.StreamCommitIntervalMs + 1)
        runCurrent()

        assertEquals("think more", assertIs<ReasoningItem>(widget.state.item(reasoning)).summary.single())
        // Stdout and the terminal interaction share one field, so their order must survive.
        assertEquals("out in\ndone", (widget.state.item(command) as CommandExecutionItem).aggregatedOutput)
    }

    @Test
    fun `transport lag reloads the open thread`() = runTest {
        val actual = AgentMessageItem("actual", "Persisted history")
        val client = TestClient().apply {
            historyResult = Result.success(ThreadReadResponse(testThread("thread"), listOf(actual)))
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.state.upsert(AgentMessageItem("stale", "Dropped deltas left this behind"))
        widget.attach()

        client.events.emit(AppServerEvent.TransportLagged(2))
        runCurrent()

        assertEquals(listOf(actual), widget.state.items.toList())
    }

    @Test
    fun `interrupted turn folds buffered deltas into the item`() = runTest {
        val client = TestClient()
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val id = "message"

        client.events.emit(AppServerEvent.AgentMessageDelta("thread", ItemTextDelta("thread", "turn", id, "half")))
        runCurrent()
        client.events.emit(AppServerEvent.TurnCompleted("thread", "turn", TurnStatus.Interrupted))
        runCurrent()

        assertEquals("half", (widget.state.items.single() as AgentMessageItem).text)
        assertNull(widget.state.stream(id))
    }

    @Test
    fun `dynamic tool calls are executed without a dialog`() = runTest {
        val client = TestClient().apply {
            listThreadsResult = Result.success(
                com.cy.codex.protocol.protocol.v2.ThreadListing(
                    threads = listOf(testThread("t1", preview = "Ship the parser")),
                ),
            )
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        client.requests.emit(
            ApprovalRequest.DynamicTool(
                RequestId("call"), "thread", "turn", "item", 0,
                com.cy.codex.protocol.protocol.v2.DynamicToolCallParams(
                    threadId = "thread",
                    turnId = "turn",
                    callId = "call",
                    namespace = "codex_tui",
                    tool = "list_threads",
                    arguments = """{"limit":5}""",
                ),
            ),
        )
        runCurrent()

        assertNull(widget.currentApproval)
        val response = assertIs<ApprovalResponse.DynamicTool>(client.responses.single())
        assertTrue(response.result.success)
        assertTrue(response.result.contentItems.single().contains("t1"))
    }

    @Test
    fun `wait_threads answers a snapshot instead of failing`() = runTest {
        val thread = testThread("t1")
        val turn = Turn("turn-1", listOf(AgentMessageItem("m1", "done")), status = TurnStatus.Completed)
        val client = TestClient().apply {
            historyResult = Result.success(ThreadReadResponse(thread, emptyList(), listOf(turn)))
            earlierResult = Result.success(ThreadTurnsPage(listOf(turn), nextCursor = null))
            itemsResult = Result.success(ThreadItemsPage(listOf(AgentMessageItem("m1", "done"))))
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "parent"))
        widget.attach()
        client.requests.emit(
            ApprovalRequest.DynamicTool(
                RequestId("call"), "parent", "turn", "item", 0,
                com.cy.codex.protocol.protocol.v2.DynamicToolCallParams(
                    threadId = "parent",
                    turnId = "turn",
                    callId = "call",
                    namespace = "codex_tui",
                    tool = "wait_threads",
                    arguments = """{"targets":[{"threadId":"t1"}],"timeoutMs":0}""",
                ),
            ),
        )
        runCurrent()

        val response = assertIs<ApprovalResponse.DynamicTool>(client.responses.single())
        assertTrue(response.result.success)
        val text = response.result.contentItems.single()
        assertTrue(""""timedOut":false""" in text, text)
        assertTrue(""""reason":"turnCompleted"""" in text, text)
        assertTrue(""""id":"m1"""" in text, text)
    }

    @Test
    fun `wait_threads rejects waiting on the calling task`() = runTest {
        val client = TestClient()
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "parent"))
        widget.attach()
        client.requests.emit(
            ApprovalRequest.DynamicTool(
                RequestId("call"), "parent", "turn", "item", 0,
                com.cy.codex.protocol.protocol.v2.DynamicToolCallParams(
                    threadId = "parent",
                    turnId = "turn",
                    callId = "call",
                    namespace = "codex_tui",
                    tool = "wait_threads",
                    arguments = """{"targets":[{"threadId":"parent"}],"timeoutMs":0}""",
                ),
            ),
        )
        runCurrent()

        val response = assertIs<ApprovalResponse.DynamicTool>(client.responses.single())
        assertFalse(response.result.success)
        assertTrue(response.result.contentItems.single().contains("calling task"))
    }

    @Test
    fun `delegation tools are refused in side conversations`() = runTest {
        val client = TestClient()
        val widget = ChatWidget(client, backgroundScope)
        widget.isSideThread = { it == "side" }
        widget.bind(ThreadSessionState(threadId = "side"))
        widget.attach()
        client.requests.emit(
            ApprovalRequest.DynamicTool(
                RequestId("call"), "side", "turn", "item", 0,
                com.cy.codex.protocol.protocol.v2.DynamicToolCallParams(
                    threadId = "side",
                    turnId = "turn",
                    callId = "call",
                    namespace = "codex_tui",
                    tool = "create_thread",
                    arguments = """{"prompt":"do something"}""",
                ),
            ),
        )
        runCurrent()

        val response = assertIs<ApprovalResponse.DynamicTool>(client.responses.single())
        assertFalse(response.result.success)
        assertTrue(response.result.contentItems.single().contains("side conversation"))
    }

    private class TestClient : AppServerClient {
        override val events = MutableSharedFlow<AppServerEvent>()
        override val requests = MutableSharedFlow<ApprovalRequest>()
        override val connection = flowOf(ConnectionState.Ready)
        var turnResult: Result<String> = Result.success("turn")
        var resumeResult: Result<ThreadSessionState> = Result.failure(IllegalStateException("unavailable"))
        var historyResult: Result<ThreadReadResponse> = Result.failure(IllegalStateException("unavailable"))
        var rejectResponse = false
        val responses = mutableListOf<ApprovalResponse>()
        var listThreadsResult: Result<com.cy.codex.protocol.protocol.v2.ThreadListing> =
            Result.failure(IllegalStateException("unavailable"))
        override suspend fun listThreads(params: com.cy.codex.protocol.protocol.v2.ThreadListParams) =
            listThreadsResult
        override suspend fun initialize(clientInfo: ClientInfo) = Result.success(Unit)
        override suspend fun startTurn(
            threadId: String,
            inputs: List<UserInput>,
            outputSchema: JsonElement?,
            effort: com.cy.codex.protocol.protocol.v2.ReasoningEffort?,
            clientMetadata: Map<String, String>?,
        ) = turnResult
        override suspend fun resumeThread(params: com.cy.codex.protocol.protocol.v2.ThreadResumeParams) = resumeResult
        var readThreadCalled = false
        override suspend fun readThread(params: com.cy.codex.protocol.protocol.v2.ThreadReadParams): Result<ThreadReadResponse> {
            readThreadCalled = true
            return historyResult
        }
        var earlierResult: Result<ThreadTurnsPage> = Result.failure(IllegalStateException("unavailable"))
        override suspend fun listThreadTurns(params: com.cy.codex.protocol.protocol.v2.ThreadTurnsListParams) = earlierResult
        var itemsResult: Result<ThreadItemsPage> = Result.failure(IllegalStateException("unavailable"))
        override suspend fun listThreadItems(params: com.cy.codex.protocol.protocol.v2.ThreadItemsListParams) = itemsResult
        override suspend fun respond(requestId: RequestId, response: ApprovalResponse) {
            check(!rejectResponse) { "connection lost" }
            responses += response
        }
        override suspend fun close() = Unit
    }

    /** A `thread/list` row with everything the wire always sends filled in. */
    private fun testThread(id: String, preview: String = "", cwd: String = "") = com.cy.codex.protocol.protocol.v2.Thread(
        id = id, preview = preview, modelProvider = "openai", createdAt = 0L, updatedAt = 0L, cwd = cwd,
        status = com.cy.codex.protocol.protocol.v2.ThreadStatus.Idle, cliVersion = "1.0", ephemeral = false,
        projectId = null, sessionId = id,
    )
}
