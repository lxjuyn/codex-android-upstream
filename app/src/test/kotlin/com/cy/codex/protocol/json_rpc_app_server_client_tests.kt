package com.cy.codex.protocol

import com.cy.codex.protocol.protocol.Json
import com.cy.codex.protocol.protocol.array
import com.cy.codex.protocol.protocol.bool
import com.cy.codex.protocol.protocol.int
import com.cy.codex.protocol.protocol.long
import com.cy.codex.protocol.protocol.objectOrNull
import com.cy.codex.protocol.protocol.objectValue
import com.cy.codex.protocol.protocol.required
import com.cy.codex.protocol.protocol.strings
import com.cy.codex.protocol.protocol.text
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.v2.ApprovalsReviewer
import com.cy.codex.protocol.protocol.v2.AttachmentType
import com.cy.codex.protocol.protocol.v2.ClientInfo
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalDecision
import com.cy.codex.protocol.protocol.v2.PatchChangeKind
import com.cy.codex.protocol.protocol.v2.ThreadSettingsUpdateParams
import com.cy.codex.protocol.protocol.v2.TimelineEntry
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.UserInput
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises real wire messages while replacing only the JNI byte transport. */
@OptIn(ExperimentalCoroutinesApi::class)
class JsonRpcAppServerClientTest {
    private class HarnessTransport : JsonRpcTransport {
        val incoming = Channel<Result<String?>>(Channel.UNLIMITED)
        val outgoing = Channel<Pair<JsonRpcMessageKind, String>>(Channel.UNLIMITED)
        var starts = 0
        var closes = 0
        override suspend fun start() { starts++ }
        override suspend fun send(kind: JsonRpcMessageKind, message: String) { outgoing.send(kind to message) }
        override suspend fun receive(): String? = incoming.receive().getOrThrow()
        override suspend fun close() { closes++ }
        suspend fun request(): JsonObject {
            val (kind, message) = outgoing.receive()
            assertEquals(JsonRpcMessageKind.Request, kind)
            return Json.parse(message).objectValue()
        }
        suspend fun response(request: JsonObject, result: JsonObject) {
            incoming.send(Result.success(Json.write(obj("id" to request["id"], "result" to result))))
        }
        suspend fun sentResponse(): JsonObject {
            val (kind, message) = outgoing.receive()
            assertEquals(JsonRpcMessageKind.Response, kind)
            return Json.parse(message).objectValue()
        }
        suspend fun push(message: String) { incoming.send(Result.success(message)) }
    }

    @Test
    fun `native handshake is not repeated and concurrent responses use their ids`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        assertEquals(1, transport.starts)
        assertTrue(transport.outgoing.tryReceive().isFailure)
        val account = async { client.readAccount().getOrThrow() }
        val config = async { client.readConfig().getOrThrow() }
        val accountRequest = transport.request()
        val configRequest = transport.request()
        transport.response(configRequest, obj("config" to obj("model" to "test-model")))
        transport.response(accountRequest, obj("account" to obj("type" to "chatgpt", "email" to "user@example.test", "planType" to "pro"),
            "requiresOpenaiAuth" to true))
        assertEquals("test-model", config.await().snapshot.model)
        assertEquals("user@example.test", (account.await().account as com.cy.codex.protocol.protocol.v2.Account.Chatgpt).email)
        assertTrue(account.await().account != null)
        assertTrue(account.await().requiresOpenaiAuth)
        client.close()
    }

    @Test
    fun `archived listing includes all pages without replacing active threads`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val listing = async {
            client.listThreads(
                com.cy.codex.protocol.protocol.v2.ThreadListParams(
                    archived = true, sortKey = com.cy.codex.protocol.protocol.v2.ThreadSortKey.UpdatedAt,
                    sortDirection = com.cy.codex.protocol.protocol.v2.SortDirection.Desc, limit = 5,
                ),
            ).getOrThrow()
        }
        val first = transport.request()
        assertFalse(first.objectOrNull("params")!!.bool("archived")!!)
        assertEquals("updated_at", first.objectOrNull("params")!!.text("sortKey"))
        assertEquals("desc", first.objectOrNull("params")!!.text("sortDirection"))
        assertEquals(5, first.objectOrNull("params")!!.int("limit"))
        transport.response(first, obj("data" to listOf(obj("id" to "one", "createdAt" to 17, "status" to obj("type" to "idle"))), "nextCursor" to "page-two"))
        val second = transport.request()
        assertEquals("page-two", second.objectOrNull("params")!!.text("cursor"))
        transport.response(second, obj("data" to listOf(obj("id" to "two"))))
        val archive = transport.request()
        assertTrue(archive.objectOrNull("params")!!.bool("archived")!!)
        transport.response(archive, obj("data" to listOf(obj("id" to "old"))))
        val threads = listing.await()
        assertEquals(listOf("one", "two", "old"), threads.threads.map { it.id })
        assertEquals(17_000L, threads.threads.first().createdAt)
        assertEquals(setOf("old"), threads.archivedIds)
        client.close()
    }

    @Test
    fun `history is decoded from nested turns and stream retains final text`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val read = async { client.readThread(com.cy.codex.protocol.protocol.v2.ThreadReadParams("t")).getOrThrow() }
        transport.response(transport.request(), obj("thread" to obj("id" to "t", "turns" to listOf(
            obj("id" to "turn", "status" to "inProgress", "items" to listOf(obj("type" to "agentMessage", "id" to "item", "text" to "Partial"))),
        ))))
        assertEquals("Partial", (read.await().items.single() as AgentMessageItem).text)
        val event = async(UnconfinedTestDispatcher(testScheduler)) { client.events.first() }
        transport.push("""{"method":"item/agentMessage/delta","params":{"threadId":"t","turnId":"turn","itemId":"item","delta":" result"}}""")
        assertEquals(" result", assertIs<AppServerEvent.AgentMessageDelta>(event.await()).delta.delta)
        val stop = async { client.interruptTurn("t").getOrThrow() }
        val request = transport.request()
        assertEquals("turn/interrupt", request.text("method"))
        assertEquals("turn", request.objectOrNull("params")!!.text("turnId"))
        transport.response(request, obj())
        stop.await()
        client.close()
    }

    @Test
    fun `agent message decodes inline questions`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val read = async { client.readThread(com.cy.codex.protocol.protocol.v2.ThreadReadParams("t")).getOrThrow() }
        transport.response(transport.request(), obj("thread" to obj("id" to "t", "turns" to listOf(
            obj("id" to "turn", "status" to "completed", "items" to listOf(
                obj("type" to "agentMessage", "id" to "item", "text" to "Pick one", "questions" to listOf(
                    obj("title" to "Which one?", "options" to listOf("a", "b")),
                    obj("title" to "Why?", "options" to null),
                )),
            )),
        ))))
        val item = read.await().items.single() as AgentMessageItem
        val questions = item.questions!!
        assertEquals(listOf("Which one?", "Why?"), questions.map { it.title })
        assertEquals(listOf("a", "b"), questions[0].options)
        assertNull(questions[1].options, "an explicit null options list is free text, not an empty choice")
        client.close()
    }

    @Test
    fun `session decodes collaboration mode, service tier and parent ownership`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val resumed = async {
            client.resumeThread(
                com.cy.codex.protocol.protocol.v2.ThreadResumeParams(
                    threadId = "t",
                    excludeTurns = true,
                    initialTurnsPage = com.cy.codex.protocol.protocol.v2.ThreadResumeInitialTurnsPageParams(
                        limit = 20,
                        sortDirection = com.cy.codex.protocol.protocol.v2.SortDirection.Desc,
                        itemsView = com.cy.codex.protocol.protocol.v2.TurnItemsView.Full,
                    ),
                ),
            ).getOrThrow()
        }
        val request = transport.request()
        assertEquals("thread/resume", request.text("method"))
        val params = request.objectOrNull("params")!!
        assertEquals(true, params.bool("excludeTurns"))
        val requestedPage = params.objectOrNull("initialTurnsPage")!!
        assertEquals(20, requestedPage.int("limit"))
        assertEquals("desc", requestedPage.text("sortDirection"))
        assertEquals("full", requestedPage.text("itemsView"))
        transport.response(request, obj(
            "thread" to obj("id" to "t", "cwd" to "/w", "parentThreadId" to "parent", "canAcceptDirectInput" to false),
            "model" to "gpt-5", "modelProvider" to "openai", "cwd" to "/w",
            "collaborationMode" to obj("mode" to "plan", "settings" to obj("model" to "gpt-5")),
            "serviceTier" to "fast",
            "turnsBackwardsCursor" to "cursor-head",
            "initialTurnsPage" to obj(
                "data" to listOf(obj("id" to "turn", "status" to "completed", "itemsView" to "summary", "items" to emptyList<String>())),
                "nextCursor" to "cursor-older",
                "backwardsCursor" to "cursor-newer",
            ),
        ))
        val state = resumed.await()
        assertEquals(com.cy.codex.protocol.protocol.v2.CollaborationMode.Plan, state.collaborationMode)
        assertEquals("fast", state.serviceTier)
        assertTrue(state.blocksDirectInput)
        assertEquals("t", state.thread?.id)
        assertEquals("cursor-head", state.turnsBackwardsCursor)
        assertEquals("cursor-older", state.initialTurnsPage?.nextCursor)
        assertEquals("turn", state.initialTurnsPage?.turns?.single()?.id)
        assertEquals(com.cy.codex.protocol.protocol.v2.TurnItemsView.Summary, state.initialTurnsPage?.turns?.single()?.itemsView)
        client.close()
    }

    @Test
    fun `start and steer preserve text escaping and active turn precondition`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val input = "A \"quote\"\nnext line"
        val start = async { client.startTurn("t", listOf(UserInput.Text(input))).getOrThrow() }
        val request = transport.request()
        val params = request.objectOrNull("params")!!
        assertEquals(input, params.array("input").single().objectValue().text("text"))
        assertEquals(emptyList(), params.array("input").single().objectValue().array("text_elements"))
        transport.response(request, obj("turn" to obj("id" to "turn", "status" to "inProgress")))
        assertEquals("turn", start.await())
        val steer = async { client.steerTurn("t", listOf(UserInput.Text("More"))).getOrThrow() }
        val steerRequest = transport.request()
        assertEquals("turn", steerRequest.objectOrNull("params")!!.text("expectedTurnId"))
        transport.response(steerRequest, obj("turnId" to "turn"))
        assertEquals("turn", steer.await())
        client.close()
    }

    @Test
    fun `text elements and local images keep their upstream wire shape`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val elements = listOf(
            com.cy.codex.protocol.protocol.v2.TextElement(
                com.cy.codex.protocol.protocol.v2.ByteRange(4, 13),
                "[Image #1]",
            ),
        )
        val start = async {
            client.startTurn(
                "t",
                listOf(
                    UserInput.LocalImage("/data/user/0/app/files/photo.png"),
                    UserInput.Text("see [Image #1] now", elements),
                ),
            ).getOrThrow()
        }
        val request = transport.request()
        val inputs = request.objectOrNull("params")!!.array("input")
        val image = inputs[0].objectValue()
        assertEquals("localImage", image.required("type"))
        assertEquals("/data/user/0/app/files/photo.png", image.required("path"))
        val text = inputs[1].objectValue()
        assertEquals("text", text.required("type"))
        val element = text.array("text_elements").single().objectValue()
        assertEquals("[Image #1]", element.required("placeholder"))
        val range = element["byteRange"]!!.objectValue()
        assertEquals(4, range.int("start"))
        assertEquals(13, range.int("end"))
        transport.response(request, obj("turn" to obj("id" to "turn", "status" to "inProgress")))
        assertEquals("turn", start.await())
        client.close()
    }

    @Test
    fun `server errors and request timeout are failures while cancellation propagates`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val read = async { client.readAccount() }
        val request = transport.request()
        transport.push(Json.write(obj("id" to request["id"], "error" to obj("code" to -32000, "message" to "Cannot read auth"))))
        assertEquals("Cannot read auth", read.await().exceptionOrNull()?.message)
        val timedOut = async { client.readAccount() }
        transport.request()
        advanceTimeBy(120_001)
        assertIs<IOException>(timedOut.await().exceptionOrNull())
        val cancelled = async { client.readAccount() }
        transport.request()
        cancelled.cancel()
        runCurrent()
        assertTrue(cancelled.isCancelled)
        assertTrue(client.listProjects().isFailure)
        client.close()
    }

    @Test
    fun `disconnect fails pending requests and retry starts a fresh transport`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val read = async { client.readAccount() }
        transport.request()
        transport.incoming.send(Result.failure(IOException("Disconnected")))
        assertEquals("Disconnected", read.await().exceptionOrNull()?.message)
        assertIs<ConnectionState.Failed>(client.connection.value)
        assertEquals(1, transport.closes)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        assertEquals(2, transport.starts)
        val retried = async { client.readAccount().getOrThrow() }
        transport.response(transport.request(), obj("account" to JsonNull))
        assertNull(retried.await().account)
        client.close()
    }

    @Test
    fun `transport lag is an event and the connection survives`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val lag = async(UnconfinedTestDispatcher(testScheduler)) { client.events.first() }
        transport.push("""{"method":"android/transportLagged","params":{"skipped":3}}""")
        assertEquals(3L, assertIs<AppServerEvent.TransportLagged>(lag.await()).skipped)
        assertEquals(ConnectionState.Ready, client.connection.value)
        val account = async { client.readAccount().getOrThrow() }
        transport.response(transport.request(), obj("account" to JsonNull))
        assertNull(account.await().account)
        client.close()
    }

    @Test
    fun `a silent unanswered request trips the watchdog and fails the connection`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope, watchdogIntervalMs = 30_000)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val read = async { client.readAccount() }
        transport.request()
        // The first quiet tick probes the worker; the unanswered canary ends the session.
        advanceTimeBy(30_001)
        runCurrent()
        val probe = transport.request()
        assertEquals("thread/loaded/list", probe.text("method"))
        advanceTimeBy(30_001)
        runCurrent()
        assertIs<IOException>(read.await().exceptionOrNull())
        assertIs<ConnectionState.Failed>(client.connection.value)
        assertEquals(1, transport.closes)
        client.close()
    }

    @Test
    fun `watchdog leaves an idle connection alone`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope, watchdogIntervalMs = 30_000)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        advanceTimeBy(120_001)
        runCurrent()
        assertTrue(transport.outgoing.tryReceive().isFailure)
        assertEquals(ConnectionState.Ready, client.connection.value)
        client.close()
    }

    @Test
    fun `command approval preserves numeric server request ids`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val approval = async { client.requests.first() }
        transport.push("""{"id":42,"method":"item/commandExecution/requestApproval","params":{"threadId":"t","turnId":"turn","itemId":"cmd","command":"git status","cwd":"/workspace"}}""")
        val received = assertIs<ApprovalRequest.Exec>(approval.await())
        assertEquals("git status", received.params.command)
        client.respond(received.requestId, ApprovalResponse.CommandExecution(CommandExecutionApprovalDecision.Decline))
        val response = transport.sentResponse()
        assertEquals(JsonPrimitive(42), response["id"])
        assertEquals("decline", response.objectOrNull("result")!!.text("decision"))
        client.close()
    }

    @Test
    fun `file change approval carries no patch and patch updates decode from their own notification`() =
        runTest {
            val transport = HarnessTransport()
            val client = JsonRpcAppServerClient(transport, backgroundScope)
            client.initialize(ClientInfo("android", version = "1")).getOrThrow()
            val approval = async { client.requests.first() }
            transport.push("""{"id":"patch","method":"item/fileChange/requestApproval","params":{"threadId":"t","turnId":"turn","itemId":"item","startedAtMs":7,"reason":"needs write","grantRoot":"/workspace"}}""")
            val received = assertIs<ApprovalRequest.ApplyPatch>(approval.await())
            assertEquals("needs write", received.params.reason)
            assertEquals("/workspace", received.params.grantRoot)
            assertEquals(7L, received.params.startedAtMs)

            val update = async(UnconfinedTestDispatcher(testScheduler)) { client.events.first() }
            transport.push("""{"method":"item/fileChange/patchUpdated","params":{"threadId":"t","turnId":"turn","itemId":"item","changes":[{"path":"a.txt","kind":{"type":"update"},"diff":"@@ -1 +1 @@\n-a\n+b\n"}]}}""")
            val patch = assertIs<AppServerEvent.FileChangePatchUpdated>(update.await())
            assertEquals("a.txt", patch.delta.changes.single().path)
            assertEquals(PatchChangeKind.Update, patch.delta.changes.single().kind)
            client.close()
        }

    @Test
    fun `closing discards queued approvals before reconnecting`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        transport.push("""{"id":"old","method":"item/commandExecution/requestApproval","params":{"threadId":"t","turnId":"turn","itemId":"old-item","command":"git status","cwd":"/workspace"}}""")
        runCurrent()
        client.close()
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val approval = async { client.requests.first() }
        transport.push("""{"id":"new","method":"item/commandExecution/requestApproval","params":{"threadId":"t","turnId":"next-turn","itemId":"new-item","command":"git diff","cwd":"/workspace"}}""")
        val received = assertIs<ApprovalRequest.Exec>(approval.await())
        assertEquals("new", received.requestId.value)
        client.respond(received.requestId, ApprovalResponse.CommandExecution(CommandExecutionApprovalDecision.Decline))
        assertEquals(JsonPrimitive("new"), transport.sentResponse()["id"])
        client.close()
    }

    @Test
    fun `MCP form response retains boolean and number types and current time uses seconds`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val approval = async { client.requests.first() }
        transport.push("""{"id":"form","method":"mcpServer/elicitation/request","params":{"threadId":"t","serverName":"test","mode":"form","message":"Settings","requestedSchema":{"properties":{"enabled":{"type":"boolean"},"count":{"type":"integer"}},"required":["count"]}}}""")
        val received = assertIs<ApprovalRequest.Elicitation>(approval.await())
        assertTrue((received.params as com.cy.codex.protocol.protocol.v2.McpElicitationRequest.Form).requestedSchema.fields.single { it.name == "count" }.required)
        client.respond(received.requestId, ApprovalResponse.Elicitation(ElicitationAction.Accept, mapOf("enabled" to "true", "count" to "3")))
        val content = transport.sentResponse().objectOrNull("result")!!.objectOrNull("content")!!
        assertEquals(JsonPrimitive(true), content["enabled"])
        assertEquals(JsonPrimitive(3), content["count"])
        transport.push("""{"id":"time","method":"currentTime/read","params":{"threadId":"t"}}""")
        val response = transport.sentResponse().objectOrNull("result")!!
        assertTrue(response.long("currentTimeAt")!! in (System.currentTimeMillis() / 1000 - 2)..(System.currentTimeMillis() / 1000 + 2))
        assertNull(response["epochMillis"])
        client.close()
    }

    @Test
    fun `project creation sends roots and idempotency and goals decode nested response`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val created = async { client.createProject("Work", "/workspace").getOrThrow() }
        val request = transport.request()
        val params = request.objectOrNull("params")!!
        assertEquals("/workspace", params.array("roots").single().objectValue().text("path"))
        assertTrue(params.required("idempotencyKey").isNotBlank())
        transport.response(request, obj("project" to obj("id" to "p", "name" to "Work", "roots" to listOf(obj("path" to "/workspace")))))
        assertEquals("/workspace", created.await().path)
        val goal = async { client.getGoal("t").getOrThrow() }
        transport.response(transport.request(), obj("goal" to obj("threadId" to "t", "objective" to "Implement", "status" to "active", "tokenBudget" to 50_000, "tokensUsed" to 123)))
        assertEquals(123, goal.await()!!.tokensUsed)
        assertEquals(50_000L, goal.await()!!.tokenBudget)

        // An explicit JSON null clears the budget; an absent key leaves it alone, and so does an
        // absent objective/status.
        val cleared = async {
            client.setGoal(
                com.cy.codex.protocol.protocol.v2.ThreadGoalSetParams(
                    threadId = "t",
                    objective = "Ship it",
                    status = com.cy.codex.protocol.protocol.v2.GoalStatus.Paused,
                    clearTokenBudget = true,
                ),
            ).getOrThrow()
        }
        val clearedRequest = transport.request()
        val clearedParams = clearedRequest.objectOrNull("params")!!
        assertEquals("Ship it", clearedParams.required("objective"))
        assertEquals("paused", clearedParams.text("status"))
        assertEquals(JsonNull, clearedParams["tokenBudget"])
        transport.response(clearedRequest, obj("goal" to obj("threadId" to "t", "objective" to "Ship it", "status" to "paused", "tokensUsed" to 0, "timeUsedSeconds" to 1)))
        cleared.await()

        val resumed = async {
            client.setGoal(
                com.cy.codex.protocol.protocol.v2.ThreadGoalSetParams(
                    threadId = "t",
                    status = com.cy.codex.protocol.protocol.v2.GoalStatus.Active,
                ),
            ).getOrThrow()
        }
        val resumedRequest = transport.request()
        val resumedParams = resumedRequest.objectOrNull("params")!!
        assertNull(resumedParams["tokenBudget"])
        assertNull(resumedParams["objective"])
        transport.response(resumedRequest, obj("goal" to obj("threadId" to "t", "objective" to "Ship it", "status" to "active", "tokensUsed" to 0, "timeUsedSeconds" to 2)))
        assertEquals(com.cy.codex.protocol.protocol.v2.GoalStatus.Active, resumed.await().status)
        client.close()
    }

    @Test
    fun `queue submission has an identity and starts the server supplied turn`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val queue = async { client.addToQueue("t", listOf(UserInput.Text("Next"))).getOrThrow() }
        val request = transport.request()
        val params = request.objectOrNull("params")!!
        assertTrue(params.required("clientUserMessageId").isNotEmpty())
        transport.response(request, obj("queuedSubmission" to obj("id" to "q", "clientUserMessageId" to params.required("clientUserMessageId"), "input" to params["input"])))
        assertEquals("Next", queue.await().preview)
        val start = async { client.startQueued("t", "q").getOrThrow() }
        val startRequest = transport.request()
        assertEquals("q", startRequest.objectOrNull("params")!!.required("queuedSubmissionId"))
        transport.response(startRequest, obj("turn" to obj("id" to "queued-turn")))
        start.await()
        val stop = async { client.interruptTurn("t").getOrThrow() }
        val stopRequest = transport.request()
        assertEquals("queued-turn", stopRequest.objectOrNull("params")!!.required("turnId"))
        transport.response(stopRequest, obj())
        stop.await()
        client.close()
    }

    @Test
    fun `plugin installation parses the connector auth follow-up`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val install = async { client.installPlugin("formatter", "/marketplace.json").getOrThrow() }
        val request = transport.request()
        assertEquals("/marketplace.json", request.objectOrNull("params")!!.required("marketplacePath"))
        assertNull(request.objectOrNull("params")!!.text("remoteMarketplaceName"))
        transport.response(
            request,
            obj(
                "authPolicy" to "ON_INSTALL",
                "appsNeedingAuth" to listOf(
                    obj(
                        "id" to "github",
                        "name" to "GitHub",
                        "description" to "Issues and pull requests",
                        "installUrl" to "https://chatgpt.com/apps/github",
                        "category" to "developer",
                    ),
                ),
            ),
        )
        val response = install.await()
        assertEquals(
            com.cy.codex.protocol.protocol.v2.PluginAuthPolicy.OnInstall,
            response.authPolicy,
        )
        assertEquals(listOf("github"), response.appsNeedingAuth.map { it.id })
        assertEquals(
            "https://chatgpt.com/apps/github",
            response.appsNeedingAuth.single().installUrl,
        )
        client.close()
    }

    @Test
    fun `unit parameter methods omit params instead of serializing an invalid empty object`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val logout = async { client.logout().getOrThrow() }
        val request = transport.request()
        assertEquals("account/logout", request.required("method"))
        assertFalse("params" in request)
        transport.response(request, obj())
        logout.await()
        client.close()
    }

    @Test
    fun `configuration layers retain server reported paths`() {
        val response = WireCodec.config(obj("config" to obj("model" to "actual-model"), "layers" to listOf(
            obj("name" to obj("type" to "user", "file" to "/data/user/0/app/files/home/.codex/config.toml"), "config" to obj()),
            obj("name" to obj("type" to "project", "dotCodexFolder" to "/workspace/.codex"), "config" to obj()),
        )))
        assertEquals("/data/user/0/app/files/home/.codex/config.toml", response.layers!![0].sourcePath)
        assertEquals("/workspace/.codex", response.layers[1].sourcePath)
        assertEquals(com.cy.codex.protocol.protocol.v2.GoalStatus.Blocked,
            WireCatalogCodec.goal(obj("threadId" to "t", "objective" to "Work", "status" to "blocked")).status)
        assertEquals(com.cy.codex.protocol.protocol.v2.SkillScope.Project,
            WireCatalogCodec.skill(obj("name" to "local", "path" to "/workspace/SKILL.md", "scope" to "repo")).scope)
    }

    @Test
    fun `permissions approval exposes structured file system entries`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val approval = async { client.requests.first() }
        transport.push("""{"id":"permissions","method":"item/permissions/requestApproval","params":{"threadId":"t","turnId":"turn","itemId":"item","permissions":{"network":{"enabled":true},"fileSystem":{"entries":[{"path":{"type":"path","path":"/workspace"},"access":"write"}]}}}}""")
        val received = assertIs<ApprovalRequest.Permissions>(approval.await())
        assertEquals(listOf("/workspace"), received.params.permissions.fileSystemWrite)
        assertTrue(received.params.permissions.network)
        client.respond(received.requestId, ApprovalResponse.Permissions(com.cy.codex.protocol.protocol.v2.PermissionsApprovalDecision.Decline))
        val result = transport.sentResponse().objectOrNull("result")!!
        assertTrue(result.objectOrNull("permissions")!!.isEmpty())
        client.close()
    }

    @Test
    fun `attachments are addressed by identity and decode their payload`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val listed = async { client.listAttachments("t").getOrThrow() }
        val list = transport.request()
        assertEquals("thread/attachment/list", list.required("method"))
        assertEquals("t", list.objectOrNull("params")!!.required("threadId"))
        transport.response(list, obj("data" to listOf(obj("id" to "a", "attachmentType" to "image", "identityKey" to "shot.png",
            "payload" to obj("path" to "/tmp/shot.png"), "createdAt" to 17))))
        val attachment = listed.await().single()
        assertEquals("shot.png", attachment.identityKey)
        assertEquals("image", attachment.attachmentType)
        assertEquals(17L, attachment.createdAt)

        val added = async { client.addAttachment("t", AttachmentType.File, "notes.txt", JsonPrimitive("/tmp/notes.txt")).getOrThrow() }
        val add = transport.request()
        assertEquals("thread/attachment/add", add.required("method"))
        assertEquals("file", add.objectOrNull("params")!!.required("attachmentType"))
        assertEquals("notes.txt", add.objectOrNull("params")!!.required("identityKey"))
        transport.response(add, obj("outcome" to "created", "attachment" to obj("id" to "b", "attachmentType" to "file",
            "identityKey" to "notes.txt", "payload" to "/tmp/notes.txt", "createdAt" to 1)))
        assertEquals("notes.txt", added.await().identityKey)

        val removed = async { client.removeAttachment("t", AttachmentType.File, "notes.txt").getOrThrow() }
        val remove = transport.request()
        assertEquals("thread/attachment/remove", remove.required("method"))
        assertEquals("file", remove.objectOrNull("params")!!.required("attachmentType"))
        assertNull(remove.objectOrNull("params")!!.text("attachmentId"))
        transport.response(remove, obj())
        removed.await()
        client.close()
    }

    @Test
    fun `timeline decodes every tagged entry variant`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val timeline = async { client.listThreadTimeline("t").getOrThrow() }
        val request = transport.request()
        assertEquals("thread/timeline/list", request.required("method"))
        transport.response(request, obj("data" to listOf(
            obj("type" to "item", "position" to 1, "turnId" to "turn", "item" to obj("type" to "agentMessage", "id" to "item", "text" to "hi")),
            obj("type" to "turnCompleted", "position" to 2, "turnId" to "turn", "status" to "completed", "durationMs" to 5, "startedAt" to 3, "completedAt" to 4),
        )))
        val entries = timeline.await()
        assertEquals("hi", (assertIs<TimelineEntry.Item>(entries[0]).item as AgentMessageItem).text)
        val completed = assertIs<TimelineEntry.TurnCompleted>(entries[1])
        assertEquals(TurnStatus.Completed, completed.status)
        assertEquals(5L, completed.durationMs)
        client.close()
    }

    @Test
    fun `background terminals, hooks and diagnostics decode their response envelopes`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val terminals = async { client.listBackgroundTerminals("t").getOrThrow() }
        transport.response(transport.request(), obj("data" to listOf(obj("itemId" to "i", "processId" to "p", "command" to "sleep 60",
            "cwd" to "/workspace", "osPid" to 7, "cpuPercent" to 1.5, "rssKb" to 128))))
        val terminal = terminals.await().single()
        assertEquals("p", terminal.processId)
        assertEquals(7L, terminal.osPid)

        val terminate = async { client.terminateBackgroundTerminal("t", "p").getOrThrow() }
        val terminateRequest = transport.request()
        assertEquals("thread/backgroundTerminals/terminate", terminateRequest.required("method"))
        transport.response(terminateRequest, obj("terminated" to false))
        terminate.await()

        val hooks = async { client.listHooks().getOrThrow() }
        transport.response(transport.request(), obj("data" to listOf(obj("cwd" to "/workspace", "hooks" to listOf(
            obj("key" to "k", "eventName" to "preToolUse", "handlerType" to "command", "command" to "echo hi", "enabled" to true, "trustStatus" to "trusted"),
        ), "warnings" to listOf("hook file ignored"), "errors" to listOf(obj("path" to "/home/.codex/hooks.json", "message" to "invalid json"))))))
        val hookEntry = hooks.await().single()
        assertEquals("echo hi", hookEntry.hooks.single().command)
        assertEquals(listOf("hook file ignored"), hookEntry.warnings)
        assertEquals("invalid json", hookEntry.errors.single().message)

        val diagnostics = async { client.readServerDiagnostics().getOrThrow() }
        transport.response(transport.request(), obj("process" to obj("id" to 9, "residentMemoryBytes" to 1024),
            "gauges" to listOf(obj("name" to "threads.active", "value" to 2))))
        val report = diagnostics.await()
        assertEquals(9L, report.process.id)
        assertEquals(2L, report.gauges.single().value)
        client.close()
    }

    @Test
    fun `process handle names the spawn and its notifications carry that handle`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val spawned = async { client.spawnProcess(listOf("bash"), "/workspace", tty = true).getOrThrow() }
        val request = transport.request()
        assertEquals("process/spawn", request.required("method"))
        val params = request.objectOrNull("params")!!
        assertEquals(true, params.bool("tty"))
        assertEquals("/workspace", params.required("cwd"))
        transport.response(request, obj())
        val handle = spawned.await()
        assertEquals(params.required("processHandle"), handle)

        val write = async { client.writeProcessStdin(handle, "ls\n".toByteArray(), closeStdin = true).getOrThrow() }
        val writeRequest = transport.request()
        assertEquals("process/writeStdin", writeRequest.required("method"))
        assertEquals(handle, writeRequest.objectOrNull("params")!!.required("processHandle"))
        assertTrue(writeRequest.objectOrNull("params")!!.bool("closeStdin")!!)
        transport.response(writeRequest, obj())
        write.await()

        val resize = async { client.resizeProcessPty(handle, 30, 90).getOrThrow() }
        val resizeRequest = transport.request()
        assertEquals("process/resizePty", resizeRequest.required("method"))
        val size = resizeRequest.objectOrNull("params")!!.objectOrNull("size")!!
        assertEquals(30, size.int("rows"))
        assertEquals(90, size.int("cols"))
        transport.response(resizeRequest, obj())
        resize.await()

        val killed = async { client.killProcess(handle).getOrThrow() }
        transport.response(transport.request(), obj())
        killed.await()

        val observed = async(UnconfinedTestDispatcher(testScheduler)) { client.events.first() }
        transport.push("""{"method":"process/outputDelta","params":{"processHandle":"$handle","stream":"stdout","deltaBase64":"aGk=","capReached":false}}""")
        assertEquals("aGk=", assertIs<AppServerEvent.ProcessOutputDelta>(observed.await()).delta.deltaBase64)
        client.close()
    }

    @Test
    fun `account, remote control and user verification decode their status enums`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val credit = async { client.consumeRateLimitResetCredit("c").getOrThrow() }
        val creditRequest = transport.request()
        assertEquals("account/rateLimitResetCredit/consume", creditRequest.required("method"))
        assertEquals("c", creditRequest.objectOrNull("params")!!.text("creditId"))
        assertTrue(creditRequest.objectOrNull("params")!!.required("idempotencyKey").isNotEmpty())
        transport.response(creditRequest, obj("outcome" to "noCredit"))
        assertEquals(com.cy.codex.protocol.protocol.v2.ConsumeRateLimitResetCreditOutcome.NoCredit, credit.await().outcome)

        val nudge = async { client.sendAddCreditsNudgeEmail(com.cy.codex.protocol.protocol.v2.AddCreditsNudgeCreditType.UsageLimit).getOrThrow() }
        val nudgeRequest = transport.request()
        assertEquals("usage_limit", nudgeRequest.objectOrNull("params")!!.required("creditType"))
        transport.response(nudgeRequest, obj("status" to "cooldown_active"))
        assertEquals(com.cy.codex.protocol.protocol.v2.AddCreditsNudgeEmailStatus.CooldownActive, nudge.await().status)

        val status = async { client.readRemoteControlStatus().getOrThrow() }
        transport.response(transport.request(), obj("status" to "connected", "serverName" to "phone", "installationId" to "i", "environmentId" to JsonNull))
        assertEquals(com.cy.codex.protocol.protocol.v2.RemoteControlConnectionStatus.Connected, status.await().status)

        val pairing = async { client.startRemoteControlPairing(manualCode = true).getOrThrow() }
        val pairingRequest = transport.request()
        assertEquals(true, pairingRequest.objectOrNull("params")!!.bool("manualCode"))
        transport.response(pairingRequest, obj("pairingCode" to "abc", "manualPairingCode" to "1234", "environmentId" to "e", "expiresAt" to 99))
        assertEquals("1234", pairing.await().manualPairingCode)

        val verification = async { client.readUserVerificationStatus().getOrThrow() }
        transport.response(transport.request(), obj("credentialId" to JsonNull, "unavailableReason" to "biometricsUnavailable", "unavailableMessage" to "no sensor"))
        assertEquals(com.cy.codex.protocol.protocol.v2.UserVerificationUnavailableReason.BiometricsUnavailable,
            verification.await().unavailableReason)
        client.close()
    }

    @Test
    fun `plugin shares, feedback and windows sandbox decode server envelopes`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val shares = async { client.listPluginShares().getOrThrow() }
        transport.response(transport.request(), obj("data" to listOf(obj("plugin" to obj("id" to "p@m", "name" to "formatter",
            "installed" to true, "remotePluginId" to "r", "shareContext" to obj("shareUrl" to "https://example.test/s",
                "discoverability" to "UNLISTED", "sharePrincipals" to listOf(obj("principalType" to "user", "principalId" to "alice", "role" to "reader", "name" to "Alice")))),
            "localPluginPath" to JsonNull))))
        val share = shares.await().single()
        assertEquals("r", share.plugin.remotePluginId)
        assertNull(share.localPluginPath)
        assertEquals("alice", share.plugin.shareContext!!.sharePrincipals!!.single().principalId)

        val saved = async { client.savePluginShare("/plugins/p", null).getOrThrow() }
        transport.response(transport.request(), obj("remotePluginId" to "r", "shareUrl" to "https://example.test/s", "canPublishToWorkspace" to true))
        assertEquals("https://example.test/s", saved.await().shareUrl)

        val checkout = async { client.checkoutPluginShare("r").getOrThrow() }
        transport.response(transport.request(), obj("remotePluginId" to "r", "pluginId" to "p", "pluginName" to "formatter",
            "pluginPath" to "/plugins/p", "marketplaceName" to "m", "marketplacePath" to "/m.json", "remoteVersion" to "1.0"))
        assertEquals("/plugins/p", checkout.await().pluginPath)

        val updated = async { client.updatePluginShareTargets("r", com.cy.codex.protocol.protocol.v2.PluginShareDiscoverability.Private,
            listOf(com.cy.codex.protocol.protocol.v2.PluginShareTarget("user", "alice"))).getOrThrow() }
        val updateRequest = transport.request()
        assertEquals("PRIVATE", updateRequest.objectOrNull("params")!!.required("discoverability"))
        transport.response(updateRequest, obj("principals" to emptyList<JsonElement>(), "discoverability" to "PRIVATE"))
        assertEquals(com.cy.codex.protocol.protocol.v2.PluginShareDiscoverability.Private, updated.await().discoverability)

        val feedback = async { client.uploadFeedback(com.cy.codex.protocol.protocol.v2.FeedbackUploadParams("bug", "Crash")).getOrThrow() }
        transport.response(transport.request(), obj("threadId" to "t", "promptHash" to "abc"))
        assertEquals("t", feedback.await().threadId)

        val readiness = async { client.windowsSandboxReadiness().getOrThrow() }
        transport.response(transport.request(), obj("status" to "notConfigured"))
        assertEquals(com.cy.codex.protocol.protocol.v2.WindowsSandboxReadiness.NotConfigured, readiness.await().status)
        client.close()
    }

    @Test
    fun `environment reads and external agent import carry upstream parameter names`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val info = async { client.readEnvironmentInfo("remote").getOrThrow() }
        val infoRequest = transport.request()
        assertEquals("environment/info", infoRequest.required("method"))
        assertEquals("remote", infoRequest.objectOrNull("params")!!.required("environmentId"))
        transport.response(infoRequest, obj("shell" to obj("name" to "zsh", "path" to "/bin/zsh"), "cwd" to "file:///workspace"))
        assertEquals("zsh", info.await().shell.name)

        val status = async { client.readEnvironmentStatus("remote").getOrThrow() }
        transport.response(transport.request(), obj("status" to "disconnected", "error" to "refused"))
        assertEquals(com.cy.codex.protocol.protocol.v2.EnvironmentStatusKind.Disconnected, status.await().status)

        val detected = async { client.detectExternalAgentConfig().getOrThrow() }
        transport.response(transport.request(), obj("items" to listOf(obj("itemType" to "SKILLS", "description" to "One skill",
            "cwd" to JsonNull, "details" to obj("skills" to listOf(obj("name" to "skill"))))), "connectors" to emptyList<JsonElement>()))
        val item = detected.await().items.single()
        assertEquals("SKILLS", item.itemType)
        assertEquals("skill", item.details!!.skills.single().name)

        val imported = async { client.importExternalAgentConfig(listOf(item)).getOrThrow() }
        val importRequest = transport.request()
        assertEquals("externalAgentConfig/import", importRequest.required("method"))
        assertEquals("SKILLS", importRequest.objectOrNull("params")!!.array("migrationItems").single().objectValue().required("itemType"))
        transport.response(importRequest, obj("importId" to "imp-1"))
        assertEquals("imp-1", imported.await())

        val histories = async { client.readExternalAgentImportHistories().getOrThrow() }
        transport.response(transport.request(), obj("data" to listOf(obj("importId" to "imp-1", "providerId" to "claude",
            "completedAtMs" to 5, "successes" to listOf(obj("itemType" to "SKILLS")), "failures" to emptyList<JsonElement>()))))
        assertEquals("imp-1", histories.await().single().importId)
        client.close()
    }

    @Test
    fun `guardian denials cache the event the approve call needs`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val review = async(UnconfinedTestDispatcher(testScheduler)) { client.events.first() }
        transport.push("""{"method":"item/autoApprovalReview/completed","params":{"threadId":"t","turnId":"turn","startedAtMs":1,"completedAtMs":2,"reviewId":"r","targetItemId":"item","review":{"status":"denied","riskLevel":"high","userAuthorization":"low","rationale":"rm -rf"},"decisionSource":"agent","action":{"type":"command","source":"shell","command":"rm -rf /","cwd":"/workspace"}}}""")
        assertIs<AppServerEvent.AutoApprovalReviewCompleted>(review.await())
        val approve = async { client.approveGuardianDeniedAction("t", "item").getOrThrow() }
        val request = transport.request()
        assertEquals("thread/approveGuardianDeniedAction", request.required("method"))
        val event = request.objectOrNull("params")!!.objectOrNull("event")!!
        assertEquals("denied", event.required("status"))
        assertEquals("item", event.required("target_item_id"))
        assertEquals("rm -rf /", event.objectOrNull("action")!!.required("command"))
        transport.response(request, obj())
        approve.await()
        client.close()
    }

    @Test
    fun `approvals reviewer round-trips as its wire value`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val update = async {
            client.updateThreadSettingsFull(
                ThreadSettingsUpdateParams("t", approvalsReviewer = ApprovalsReviewer.AutoReview),
            ).getOrThrow()
        }
        val request = transport.request()
        assertEquals("thread/settings/update", request.required("method"))
        assertEquals("auto_review", request.objectOrNull("params")!!.required("approvalsReviewer"))
        transport.response(request, obj())
        update.await()

        // The legacy `guardian_subagent` alias is the same mode and must fold into AutoReview.
        val settings = async(UnconfinedTestDispatcher(testScheduler)) { client.events.first() }
        transport.push(
            """{"method":"thread/settings/updated","params":{"threadId":"t","threadSettings":{"model":"gpt","approvalPolicy":"on-request","approvalsReviewer":"guardian_subagent"}}}""",
        )
        val event = assertIs<AppServerEvent.ThreadSettingsUpdatedEvent>(settings.await())
        assertEquals(ApprovalsReviewer.AutoReview, event.delta.approvalsReviewer)
        client.close()
    }

    @Test
    fun `guardian review notification carries the judged action`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val started = async(UnconfinedTestDispatcher(testScheduler)) { client.events.first() }
        transport.push(
            """{"method":"item/autoApprovalReview/started","params":{"threadId":"t","turnId":"turn","startedAtMs":1,"reviewId":"r1","targetItemId":"item","review":{"status":"inProgress"},"action":{"type":"applyPatch","cwd":"/workspace","files":["/workspace/a.kt"]}}}""",
        )
        val event = assertIs<AppServerEvent.AutoApprovalReviewStarted>(started.await())
        assertEquals("r1", event.delta.reviewId)
        assertEquals("inProgress", event.delta.status)
        assertEquals("item", event.delta.itemId)
        assertEquals(
            "applyPatch",
            event.delta.action!!.objectValue().required("type"),
        )
        client.close()
    }

    @Test
    fun `fuzzy search and its sessions use the upstream parameter names`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val oneShot = async { client.fuzzyFileSearch("read", listOf("/workspace")).getOrThrow() }
        transport.response(transport.request(), obj("files" to listOf(obj("path" to "/workspace/README.md", "matchType" to "file",
            "fileName" to "README.md", "root" to "/workspace", "score" to 12, "indices" to listOf(0, 1)))))
        assertEquals(12L, oneShot.await().single().score)

        val started = async { client.startFuzzySearchSession("s", listOf("/workspace")).getOrThrow() }
        val start = transport.request()
        assertEquals("fuzzyFileSearch/sessionStart", start.required("method"))
        assertEquals("s", start.objectOrNull("params")!!.required("sessionId"))
        transport.response(start, obj())
        started.await()

        val updated = async { client.updateFuzzySearchSession("s", "read").getOrThrow() }
        val update = transport.request()
        assertEquals("fuzzyFileSearch/sessionUpdate", update.required("method"))
        assertEquals("read", update.objectOrNull("params")!!.required("query"))
        transport.response(update, obj())
        updated.await()

        val stopped = async { client.stopFuzzySearchSession("s").getOrThrow() }
        val stop = transport.request()
        assertEquals("fuzzyFileSearch/sessionStop", stop.required("method"))
        transport.response(stop, obj())
        stopped.await()
        client.close()
    }

    @Test
    fun `command approval decodes decisions and encodes the payload variants`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val approval = async { client.requests.first() }
        transport.push(
            """{"id":"amend","method":"item/commandExecution/requestApproval","params":{"threadId":"t","turnId":"turn","itemId":"cmd","command":"curl https://example.test","cwd":"/workspace","proposedExecpolicyAmendment":["curl","https://example.test"],"proposedNetworkPolicyAmendments":[{"action":"allow","host":"example.test"}],"availableDecisions":["accept",{"acceptWithExecpolicyAmendment":{"execpolicy_amendment":["curl"]}},{"applyNetworkPolicyAmendment":{"network_policy_amendment":{"action":"allow","host":"example.test"}}}],"commandActions":[{"type":"unknown","command":"curl https://example.test"}]}}""",
        )
        val received = assertIs<ApprovalRequest.Exec>(approval.await())
        assertEquals(listOf("curl", "https://example.test"), received.params.proposedExecpolicyAmendment)
        assertEquals("example.test", received.params.proposedNetworkPolicyAmendments.single().host)
        assertEquals(3, received.params.availableDecisions.size)
        assertEquals(com.cy.codex.protocol.protocol.v2.CommandAction.Unknown("curl https://example.test"), received.params.commandActions.single())

        client.respond(received.requestId, ApprovalResponse.CommandExecution(
            CommandExecutionApprovalDecision.AcceptWithExecpolicyAmendment(listOf("curl"))))
        val amendment = transport.sentResponse().objectOrNull("result")!!
            .objectOrNull("decision")!!.objectOrNull("acceptWithExecpolicyAmendment")!!
        assertEquals(listOf("curl"), amendment.strings("execpolicy_amendment"))
        client.close()
    }

    @Test
    fun `URL elicitation keeps its own mode instead of rendering as a form`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val approval = async { client.requests.first() }
        transport.push("""{"id":"url","method":"mcpServer/elicitation/request","params":{"threadId":"t","serverName":"codex_apps","mode":"url","message":"Authorize","url":"https://chatgpt.com/apps/calendar/abc","elicitationId":"e-1","_meta":{"_codex_apps":{"connector_auth_failure":{"is_auth_failure":true,"connector_id":"abc","connector_name":"Calendar"}}}}}""")
        val received = assertIs<ApprovalRequest.Elicitation>(approval.await())
        val payload = assertIs<com.cy.codex.protocol.protocol.v2.McpElicitationRequest.Url>(received.params)
        assertEquals("https://chatgpt.com/apps/calendar/abc", payload.url)
        assertEquals("e-1", payload.elicitationId)
        val failure = com.cy.codex.bottom_pane.connectorAuthFailure(payload.meta)
        assertEquals("abc", failure?.connectorId)
        assertEquals("Calendar", failure?.connectorName)

        // An accepted connector sign-in echoes the metadata back, the way the TUI resolves with
        // `meta: None` and the server keeps its own copy; here the client carries it explicitly.
        client.respond(
            received.requestId,
            ApprovalResponse.Elicitation(ElicitationAction.Accept, meta = payload.meta),
        )
        val result = transport.sentResponse().objectOrNull("result")!!
        assertEquals("accept", result.text("action"))
        assertEquals(JsonNull, result["content"])
        assertEquals(
            "Calendar",
            com.cy.codex.bottom_pane.connectorAuthFailure(result["_meta"])?.connectorName,
        )
        client.close()
    }

    @Test
    fun `rate limits decode windows, credits and the account envelope`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val limits = async { client.readRateLimits().getOrThrow() }
        transport.response(transport.request(), obj(
            "rateLimits" to obj("primary" to obj("usedPercent" to 42, "windowDurationMins" to 300, "resetsAt" to 99),
                "credits" to obj("hasCredits" to true, "unlimited" to false, "balance" to "12.5"), "limitId" to "codex"),
            "accountId" to "acct",
            "rateLimitResetCredits" to obj("availableCount" to 2, "credits" to listOf(
                obj("id" to "rc1", "status" to "available", "resetType" to "weekly", "grantedAt" to 10,
                    "title" to "Weekly reset", "expiresAt" to 200),
            )),
        ))
        val decoded = limits.await()
        val primary = decoded.rateLimits.primary!!
        assertEquals(42L, primary.usedPercent)
        assertEquals(300L, primary.windowDurationMins)
        assertEquals(99_000L, primary.resetsAt)
        assertEquals("12.5", decoded.rateLimits.credits!!.balance)
        assertEquals("codex", decoded.rateLimits.limitId)
        assertEquals("acct", decoded.accountId)
        val reset = decoded.rateLimitResetCredits!!
        assertEquals(2L, reset.availableCount)
        val credit = reset.credits!!.single()
        assertEquals("rc1", credit.id)
        assertEquals("available", credit.status)
        assertEquals(200L, credit.expiresAt)
        client.close()
    }

    @Test
    fun `mcp status list decodes auth status and tool counts`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val servers = async { client.listMcpServers().getOrThrow() }
        transport.response(transport.request(), obj("data" to listOf(
            obj("name" to "srv", "runtimeStatus" to "authenticationRequired", "authStatus" to "notLoggedIn",
                "tools" to obj("a" to obj(), "b" to obj()), "resources" to listOf(obj("uri" to "u"))),
        )))
        val server = servers.await().single()
        assertEquals(com.cy.codex.protocol.protocol.v2.McpAuthStatus.NotLoggedIn, server.authStatus)
        assertEquals(2, server.tools)
        assertEquals(1, server.resources)
        assertEquals(com.cy.codex.protocol.protocol.v2.McpServerConnectionStatus.AuthenticationRequired, server.status)
        client.close()
    }

    @Test
    fun `config snapshot reads the memory settings`() {
        val snapshot = com.cy.codex.protocol.protocol.v2.ConfigSnapshot.from(
            obj("memories" to obj("use_memories" to false, "generate_memories" to true)),
        )
        assertEquals(false, snapshot.useMemories)
        assertEquals(true, snapshot.generateMemories)
    }

    @Test
    fun `token usage decodes the nested breakdowns`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val event = async(UnconfinedTestDispatcher(testScheduler)) { client.events.first() }
        transport.push("""{"method":"thread/tokenUsage/updated","params":{"threadId":"t","turnId":"turn","tokenUsage":{"total":{"totalTokens":100,"inputTokens":60,"cachedInputTokens":10,"outputTokens":30,"reasoningOutputTokens":5,"cacheWriteInputTokens":7},"last":{"totalTokens":40,"inputTokens":20,"cachedInputTokens":4,"outputTokens":16,"reasoningOutputTokens":2},"modelContextWindow":1000}}}""")
        val usage = assertIs<AppServerEvent.ThreadTokenUsageEvent>(event.await()).delta.usage
        assertEquals(100L, usage.total.totalTokens)
        assertEquals(40L, usage.last.totalTokens)
        assertEquals(7L, usage.total.cacheWriteInputTokens)
        assertEquals(1000L, usage.modelContextWindow)
        assertEquals(0.1f, usage.usedFraction)
        client.close()
    }

    @Test
    fun `account usage decodes the full summary`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val usage = async { client.readUsage().getOrThrow() }
        transport.response(
            transport.request(),
            obj(
                "dailyUsageBuckets" to listOf(obj("startDate" to "2026-01-01", "tokens" to 10)),
                "summary" to obj(
                    "lifetimeTokens" to 123L,
                    "peakDailyTokens" to 45L,
                    "longestRunningTurnSec" to 7200L,
                    "currentStreakDays" to 3L,
                    "longestStreakDays" to 9L,
                ),
            ),
        )
        val decoded = usage.await()
        assertEquals(123L, decoded.totalTokens)
        assertEquals(45L, decoded.peakDailyTokens)
        assertEquals(7200L, decoded.longestRunningTurnSec)
        assertEquals(3L, decoded.currentStreakDays)
        assertEquals(9L, decoded.longestStreakDays)
        assertEquals("2026-01-01", decoded.dailyBuckets.single().day)
        client.close()
    }

    @Test
    fun `memory mode and rollout compression use their upstream wire shapes`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val memory = async { client.setThreadMemoryMode("t", com.cy.codex.protocol.protocol.v2.ThreadMemoryMode.Enabled).getOrThrow() }
        val memoryRequest = transport.request()
        assertEquals("thread/memoryMode/set", memoryRequest.required("method"))
        assertEquals("enabled", memoryRequest.objectOrNull("params")!!.required("mode"))
        transport.response(memoryRequest, obj())
        memory.await()

        val compress = async { client.compressRollout().getOrThrow() }
        val compressRequest = transport.request()
        assertEquals("rollout/compress", compressRequest.required("method"))
        assertFalse("params" in compressRequest)
        transport.response(compressRequest, obj())
        compress.await()
        client.close()
    }

    @Test
    fun `workspace messages decode their upstream field names`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val messages = async { client.readWorkspaceMessages().getOrThrow() }
        transport.response(transport.request(), obj("messages" to listOf(obj("messageId" to "m1", "messageType" to "headline",
            "messageBody" to "hi", "createdAt" to 5))))
        val message = messages.await().single()
        assertEquals("m1", message.messageId)
        assertEquals(com.cy.codex.protocol.protocol.v2.WorkspaceMessageType.Headline, message.messageType)
        assertEquals(5_000L, message.createdAt)
        client.close()
    }

    @Test
    fun `model presets carry service tiers and input modalities`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val models = async { client.listModels().getOrThrow() }
        transport.response(transport.request(), obj("data" to listOf(obj("id" to "gpt", "model" to "gpt", "displayName" to "GPT",
            "description" to "one", "defaultReasoningEffort" to "medium",
            "supportedReasoningEfforts" to listOf(obj("reasoningEffort" to "low")), "isDefault" to true, "hidden" to false,
            "defaultServiceTier" to "fast", "serviceTiers" to listOf(obj("id" to "fast", "name" to "Fast", "description" to "faster")),
            "inputModalities" to listOf("text", "image")))))
        val preset = models.await().single()
        assertEquals("fast", preset.defaultServiceTier)
        assertEquals("Fast", preset.serviceTiers.single().name)
        assertEquals(listOf(com.cy.codex.protocol.protocol.v2.InputModality.Text,
            com.cy.codex.protocol.protocol.v2.InputModality.Image), preset.inputModalities)
        client.close()
    }

    @Test
    fun `a slow event observer cannot block RPC responses behind streaming notifications`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val observed = async(UnconfinedTestDispatcher(testScheduler)) {
            client.events.first {
                client.readAccount().getOrThrow()
                true
            }
        }
        val delta = """{"method":"item/agentMessage/delta","params":{"threadId":"t","turnId":"turn","itemId":"item","delta":"chunk"}}"""
        transport.push(delta)
        val account = transport.request()
        repeat(300) { transport.push(delta) }
        transport.response(account, obj("account" to JsonNull))
        assertIs<AppServerEvent.AgentMessageDelta>(observed.await())
        assertEquals(0L, testScheduler.currentTime)
        client.close()
    }
}
