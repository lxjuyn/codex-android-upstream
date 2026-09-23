package com.cy.codex.protocol

import com.cy.codex.protocol.protocol.Json
import com.cy.codex.protocol.protocol.RequestId
import com.cy.codex.protocol.protocol.array
import com.cy.codex.protocol.protocol.bool
import com.cy.codex.protocol.protocol.int
import com.cy.codex.protocol.protocol.long
import com.cy.codex.protocol.protocol.objectOrNull
import com.cy.codex.protocol.protocol.objectValue
import com.cy.codex.protocol.protocol.required
import com.cy.codex.protocol.protocol.stringOrNull
import com.cy.codex.protocol.protocol.strings
import com.cy.codex.protocol.protocol.text
import com.cy.codex.protocol.protocol.wireText
import com.cy.codex.protocol.protocol.v2.*
import java.io.EOFException
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put

/**
 * Which JSON-RPC envelope an outgoing message is.
 *
 * The envelope shape is the only thing a transport cannot infer from the text, and the native side
 * uses it to deserialize straight into the typed request/notification/response instead of parsing an
 * envelope and re-encoding its payload.
 */
enum class JsonRpcMessageKind(val code: Int) {
    Request(0),
    Notification(1),
    Response(2),
    Error(3),
}

/** Native start completes the app-server initialize/initialized handshake before returning. */
interface JsonRpcTransport {
    suspend fun start()
    suspend fun send(kind: JsonRpcMessageKind, message: String)
    suspend fun receive(): String?
    suspend fun close()
}

class AppServerRpcException(val code: Int, message: String, val data: JsonElement? = null) : Exception(message)

class JsonRpcAppServerClient(
    private val transport: JsonRpcTransport,
    private val scope: CoroutineScope,
    private val defaultWorkspace: String? = null,
    /**
     * How often the transport is watched while a request is still in flight, in milliseconds.
     *
     * `0` disables the watchdog. Production passes [WatchdogIntervalMs] from `CodexApplication`;
     * JVM tests disable it because `runTest` skips virtual delays and a live watchdog would send
     * probe requests in the middle of a scripted exchange.
     *
     * The watchdog is a liveness probe, not a request timeout: a tick only acts when the server
     * has also been silent since the previous tick, so a healthy stream that is merely quiet is
     * never probed, and long-running silent operations are only ever checked by the canary.
     */
    private val watchdogIntervalMs: Long = 0,
) : AppServerClient {
    private val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connection = state.asStateFlow()
    private val eventStream = MutableSharedFlow<AppServerEvent>(extraBufferCapacity = 128)
    override val events = eventStream.asSharedFlow()
    private val eventQueue = Channel<AppServerEvent>(Channel.UNLIMITED)
    private val requestStream = Channel<ApprovalRequest>(Channel.UNLIMITED)
    override val requests: Flow<ApprovalRequest> = requestStream.receiveAsFlow()
    private val sequence = AtomicLong()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val approvals = ConcurrentHashMap<String, Pair<JsonElement, JsonObject>>()
    private val activeTurns = ConcurrentHashMap<String, String>()
    private val sessions = ConcurrentHashMap<String, ThreadSessionState>()
    private val marketplacePaths = ConcurrentHashMap<String, String>()
    private val guardianDenials = ConcurrentHashMap<String, JsonElement>()
    private val lifecycle = Mutex()
    private var reader: Job? = null
    private var publisher: Job? = null
    private var watchdog: Job? = null

    /** Set on every inbound message; the watchdog only probes a transport that stayed silent. */
    @Volatile
    private var receivedSinceWatchdogTick = false

    override suspend fun initialize(clientInfo: ClientInfo): Result<Unit> = result {
        lifecycle.withLock {
            if (state.value == ConnectionState.Ready) return@withLock
            state.value = ConnectionState.Connecting
            try {
                transport.start()
                state.value = ConnectionState.Ready
                receivedSinceWatchdogTick = false
                publisher = scope.launch {
                    for (event in eventQueue) eventStream.emit(event)
                }
                reader = scope.launch {
                    try {
                        while (true) dispatch(transport.receive() ?: throw EOFException("Native app-server disconnected"))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        lifecycle.withLock {
                            runCatching { transport.close() }
                            fail(error)
                            reader = null
                        }
                    }
                }
                if (watchdogIntervalMs > 0) {
                    watchdog = scope.launch { watch() }
                }
            } catch (error: Exception) {
                runCatching { transport.close() }
                fail(error)
                throw error
            }
        }
    }

    /**
     * The worker watchdog: a wedged native session stops answering requests without closing the
     * event stream, so silence alone is not evidence of a crash. A canary request breaks the tie.
     */
    private suspend fun watch() {
        while (true) {
            delay(watchdogIntervalMs)
            if (state.value != ConnectionState.Ready) return
            val silent = !receivedSinceWatchdogTick
            receivedSinceWatchdogTick = false
            if (!silent || pending.isEmpty()) continue
            try {
                rpc("thread/loaded/list", timeoutMs = WatchdogProbeTimeoutMs)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lifecycle.withLock {
                    runCatching { transport.close() }
                    fail(IOException("App-server stopped responding to requests", error))
                    reader = null
                }
                return
            }
        }
    }

    private fun fail(error: Exception) {
        watchdog?.cancel()
        watchdog = null
        state.value = ConnectionState.Failed(error.message ?: error.javaClass.simpleName)
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        approvals.clear()
        guardianDenials.clear()
        activeTurns.clear()
        sessions.clear()
        publisher?.cancel()
        publisher = null
        while (eventQueue.tryReceive().isSuccess) { /* Discard events from the lost connection. */ }
        while (requestStream.tryReceive().isSuccess) { /* Discard approvals from the lost connection. */ }
    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun rpc(method: String, params: JsonElement? = obj(), timeoutMs: Long = RequestTimeoutMs): JsonObject {
        check(state.value == ConnectionState.Ready) { "App-server is not connected" }
        val id = "android-${sequence.incrementAndGet()}"
        val response = CompletableDeferred<JsonObject>()
        pending[id] = response
        try {
            transport.send(JsonRpcMessageKind.Request, Json.write(buildJsonObject {
                put("id", id)
                put("method", method)
                if (params != null) put("params", params)
            }))
            return try {
                withTimeout(timeoutMs) { response.await() }
            } catch (error: TimeoutCancellationException) {
                throw IOException("App-server request timed out: $method", error)
            }
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun call(method: String, params: JsonElement? = obj()): Result<Unit> = result { rpc(method, params) }

    private suspend fun dispatch(message: String) {
        receivedSinceWatchdogTick = true
        val envelope = Json.parse(message).objectValue()
        val method = envelope.text("method")
        val id = envelope["id"]
        if (method == null && id != null) {
            val awaiting = pending.remove(id.wireText()) ?: return
            val error = envelope.objectOrNull("error")
            if (error != null) awaiting.completeExceptionally(AppServerRpcException(error.int("code") ?: -32603, error.text("message") ?: "App-server request failed", error["data"]))
            else if ("result" in envelope) awaiting.complete((envelope["result"] as? JsonObject) ?: obj())
            else awaiting.completeExceptionally(IOException("App-server response has no result or error"))
        } else if (method != null) {
            val params = envelope.objectOrNull("params") ?: obj()
            if (id == null) notification(method, params) else serverRequest(id, method, params)
        }
    }

    override suspend fun close() {
        lifecycle.withLock {
            reader?.cancel()
            reader = null
            publisher?.cancel()
            publisher = null
            watchdog?.cancel()
            watchdog = null
            while (eventQueue.tryReceive().isSuccess) { /* Discard events from the closed connection. */ }
            while (requestStream.tryReceive().isSuccess) { /* Discard approvals from the closed connection. */ }
            try { transport.close() } finally {
                pending.values.forEach { it.completeExceptionally(EOFException("App-server closed")) }
                pending.clear()
                approvals.clear()
                guardianDenials.clear()
                activeTurns.clear()
                sessions.clear()
                state.value = ConnectionState.Disconnected
            }
        }
    }

    private fun threadListParams(params: ThreadListParams, archived: Boolean, cursor: String?): JsonObject = obj(
        "archived" to archived, "cursor" to cursor, "cwd" to params.cwd,
        "limit" to (params.limit ?: 100), "modelProviders" to params.modelProviders, "originators" to params.originators,
        "searchTerm" to params.searchTerm, "sectionId" to params.sectionId,
        "sortDirection" to params.sortDirection?.wire, "sortKey" to params.sortKey?.wire,
        "sourceKinds" to params.sourceKinds, "useStateDbOnly" to params.useStateDbOnly,
        "parentThreadId" to params.parentThreadId, "ancestorThreadId" to params.ancestorThreadId,
    )

    private suspend fun threadPages(params: ThreadListParams, archived: Boolean): List<Thread> {
        val threads = mutableListOf<Thread>()
        var cursor = params.cursor
        do {
            val page = rpc("thread/list", threadListParams(params, archived, cursor))
            threads += page.array("data").map(WireCodec::thread)
            val next = page.text("nextCursor")
            check(next == null || next != cursor) { "Thread pagination did not advance" }
            cursor = next
        } while (cursor != null)
        return threads
    }

    override suspend fun listThreads(params: ThreadListParams) = result {
        val active = threadPages(params, archived = false)
        val archived = if (params.archived == true) threadPages(params, archived = true) else emptyList()
        ThreadListing(active + archived, archived.mapTo(mutableSetOf()) { it.id })
    }
    override suspend fun searchThreads(term: String, includeArchived: Boolean) = listThreads(
        ThreadListParams(searchTerm = term, archived = includeArchived),
    )
    override suspend fun listLoadedThreads() = result { rpc("thread/loaded/list").strings("data") }
    override suspend fun readThread(params: ThreadReadParams) = result {
        val includeTurns = params.includeTurns ?: true
        val o = rpc("thread/read", obj("threadId" to params.threadId, "includeTurns" to includeTurns)).get("thread")!!.objectValue()
        val turns = if (includeTurns) o.array("turns").map(WireCodec::turn) else emptyList()
        turns.lastOrNull { it.status == TurnStatus.InProgress }?.let { activeTurns[params.threadId] = it.id }
        ThreadReadResponse(WireCodec.thread(o), turns.flatMap { it.items }, turns)
    }
    private suspend fun session(method: String, params: JsonObject) = result {
        WireCodec.session(rpc(method, params)).also { sessions[it.threadId] = it }
    }
    override suspend fun startThread(params: ThreadStartParams) = session("thread/start", obj(
        "cwd" to (params.cwd?.takeIf { it.isNotBlank() } ?: defaultWorkspace.orEmpty()), "model" to params.model,
        "modelProvider" to params.modelProvider, "approvalPolicy" to params.approvalPolicy?.wire,
        "approvalsReviewer" to params.approvalsReviewer?.wire, "sandbox" to params.sandbox?.let(::sandboxPolicyJson),
        "personality" to params.personality?.wire, "serviceTier" to params.serviceTier, "ephemeral" to params.ephemeral,
        "developerInstructions" to params.developerInstructions, "baseInstructions" to params.baseInstructions,
        "sessionStartSource" to params.sessionStartSource, "config" to params.config,
        "dynamicTools" to params.dynamicTools,
    ))
    override suspend fun resumeThread(params: ThreadResumeParams) = session("thread/resume", obj(
        "threadId" to params.threadId,
        "excludeTurns" to params.excludeTurns,
        "initialTurnsPage" to params.initialTurnsPage?.let {
            obj("limit" to it.limit, "sortDirection" to it.sortDirection?.wire, "itemsView" to it.itemsView?.wire)
        },
    ))
    override suspend fun forkThread(params: com.cy.codex.protocol.protocol.v2.ThreadForkParams) =
        session("thread/fork", obj(
            "threadId" to params.threadId,
            "lastTurnId" to params.lastTurnId,
            "model" to params.model,
            "modelProvider" to params.modelProvider,
            "cwd" to params.cwd,
            "approvalPolicy" to params.approvalPolicy?.wire,
            "approvalsReviewer" to params.approvalsReviewer?.wire,
            "sandbox" to params.sandbox?.let(::sandboxPolicyJson),
            "serviceTier" to params.serviceTier,
            "config" to params.config,
            "baseInstructions" to params.baseInstructions,
            "developerInstructions" to params.developerInstructions,
            "ephemeral" to params.ephemeral,
            "excludeTurns" to params.excludeTurns,
        ))
    override suspend fun archiveThread(threadId: String) = call("thread/archive", obj("threadId" to threadId))
    override suspend fun unarchiveThread(threadId: String) = call("thread/unarchive", obj("threadId" to threadId))
    override suspend fun deleteThread(threadId: String) = call("thread/delete", obj("threadId" to threadId))
    override suspend fun setThreadName(threadId: String, name: String) = call("thread/name/set", obj("threadId" to threadId, "name" to name))
    override suspend fun compactThread(threadId: String) = call("thread/compact/start", obj("threadId" to threadId))
    override suspend fun runShellCommand(threadId: String, command: String) = call("thread/shellCommand", obj("threadId" to threadId, "command" to command))
    override suspend fun unsubscribeThread(threadId: String) = call("thread/unsubscribe", obj("threadId" to threadId))
    override suspend fun listThreadItems(params: ThreadItemsListParams) = result {
        val o = rpc("thread/items/list", obj("threadId" to params.threadId, "cursor" to params.cursor, "limit" to params.limit,
            "sortDirection" to params.sortDirection?.wire, "turnId" to params.turnId))
        ThreadItemsPage(o.array("data").map { WireCodec.item(it.objectValue()["item"] ?: it) }, o.text("nextCursor"))
    }
    override suspend fun listThreadTurns(params: ThreadTurnsListParams) = result {
        val o = rpc("thread/turns/list", obj("threadId" to params.threadId, "cursor" to params.cursor, "limit" to params.limit,
            "itemsView" to params.itemsView?.wire, "sortDirection" to params.sortDirection?.wire))
        ThreadTurnsPage(o.array("data").map(WireCodec::turn), o.text("nextCursor"), o.text("backwardsCursor"))
    }
    override suspend fun revertThread(threadId: String, itemId: String?) = result {
        val history = readThread(ThreadReadParams(threadId)).getOrThrow()
        val turn = if (itemId == null) history.turns.lastOrNull() else history.turns.find { turn -> turn.items.any { it.id == itemId } }
        requireNotNull(turn) { "The selected message is no longer in this thread" }
        rpc("thread/revert", obj("threadId" to threadId, "beforeTurnId" to turn.id))
        Unit
    }
    override suspend fun updateThreadMetadata(threadId: String, name: String?, projectId: String?) = result {
        if (name != null) setThreadName(threadId, name).getOrThrow()
        WireCodec.thread(rpc("thread/metadata/update", obj("threadId" to threadId, "projectId" to (projectId ?: JsonNull)))["thread"]!!)
    }
    override suspend fun listThreadTimeline(threadId: String, cursor: String?, limit: Int?) = result {
        val o = rpc("thread/timeline/list", obj("threadId" to threadId, "cursor" to cursor, "limit" to limit))
        o.array("data").map(WireCodec::timelineEntry)
    }
    override suspend fun injectThreadItems(threadId: String, items: List<JsonElement>) = call("thread/inject_items", obj("threadId" to threadId, "items" to items))
    override suspend fun searchThreadOccurrences(threadId: String, term: String) = result {
        catalog("thread/searchOccurrences", obj("threadId" to threadId, "searchTerm" to term)).map { o ->
            val range = o.objectOrNull("snippetMatchRange")
            OccurrenceMatch(o.required("turnId"), o.required("itemId"), o.text("snippet").orEmpty(), range?.int("start") ?: 0,
                range?.int("end") ?: 0, o.text("turnCursor").orEmpty())
        }
    }

    /**
     * `thread/approveGuardianDeniedAction`.
     *
     * The server takes the serialized `GuardianAssessmentEvent`, not an item id, so the client
     * remembers the event it synthesized from `item/autoApprovalReview/completed` and looks it up
     * by the item the review targeted.
     */
    override suspend fun approveGuardianDeniedAction(threadId: String, itemId: String) = result {
        val event = guardianDenials[itemId] ?: error("No guardian denial is pending for item $itemId")
        rpc("thread/approveGuardianDeniedAction", obj("threadId" to threadId, "event" to event))
        Unit
    }

    // ---- thread/… attachments -------------------------------------------------
    override suspend fun listAttachments(threadId: String) = result {
        catalog("thread/attachment/list", obj("threadId" to threadId)).map(WireCodec::attachment)
    }
    override suspend fun addAttachment(threadId: String, type: AttachmentType, identityKey: String, payload: JsonElement) = result {
        val o = rpc("thread/attachment/add", obj("threadId" to threadId, "attachmentType" to type.wire,
            "identityKey" to identityKey, "payload" to payload))
        WireCodec.attachment(o.objectOrNull("attachment") ?: error("Missing attachment"))
    }
    override suspend fun removeAttachment(threadId: String, type: AttachmentType, identityKey: String) = call("thread/attachment/remove",
        obj("threadId" to threadId, "attachmentType" to type.wire, "identityKey" to identityKey))

    // ---- thread/… background terminals ---------------------------------------
    override suspend fun listBackgroundTerminals(threadId: String) = result {
        catalog("thread/backgroundTerminals/list", obj("threadId" to threadId)).map(WireCodec::backgroundTerminal)
    }
    override suspend fun terminateBackgroundTerminal(threadId: String, processId: String) = result {
        rpc("thread/backgroundTerminals/terminate", obj("threadId" to threadId, "processId" to processId))
        Unit
    }
    override suspend fun cleanBackgroundTerminals(threadId: String) = call("thread/backgroundTerminals/clean", obj("threadId" to threadId))

    // ---- thread/… realtime voice ---------------------------------------------
    override suspend fun startRealtime(threadId: String, sdpOffer: String?) = result {
        val transport = sdpOffer?.let { obj("type" to "webrtc", "sdp" to it) } ?: obj("type" to "websocket")
        rpc("thread/realtime/start", obj("threadId" to threadId, "outputModality" to "audio", "transport" to transport))
        Unit
    }
    override suspend fun stopRealtime(threadId: String) = result { rpc("thread/realtime/stop", obj("threadId" to threadId)); Unit }
    override suspend fun listRealtimeVoices() = result {
        val voices = rpc("thread/realtime/listVoices").objectOrNull("voices") ?: obj()
        (voices.strings("v1") + voices.strings("v2")).distinct()
    }
    override suspend fun appendRealtimeText(threadId: String, text: String) = call("thread/realtime/appendText", obj("threadId" to threadId, "text" to text, "role" to "user"))
    override suspend fun appendRealtimeSpeech(threadId: String, text: String) = call("thread/realtime/appendSpeech", obj("threadId" to threadId, "text" to text))
    override suspend fun appendRealtimeAudio(threadId: String, audio: ThreadRealtimeAudioChunk) = call("thread/realtime/appendAudio",
        obj("threadId" to threadId, "audio" to obj("data" to audio.data, "sampleRate" to audio.sampleRate, "numChannels" to audio.numChannels,
            "samplesPerChannel" to audio.samplesPerChannel, "itemId" to audio.itemId)))
    override suspend fun moveThreadToSection(threadId: String, sectionId: String?) = call("thread/section/move", obj("threadId" to threadId, "sectionId" to (sectionId ?: JsonNull)))
    override suspend fun listSections() = result { catalog("threadSection/list").map(WireCatalogCodec::section) }
    override suspend fun createSection(name: String) = result { WireCatalogCodec.section(rpc("threadSection/create", obj("name" to name)).objectOrNull("section")!!) }
    override suspend fun updateSection(sectionId: String, name: String) = result { WireCatalogCodec.section(rpc("threadSection/update", obj("sectionId" to sectionId, "name" to name)).objectOrNull("section")!!) }
    override suspend fun deleteSection(sectionId: String) = call("threadSection/delete", obj("sectionId" to sectionId))

    override suspend fun setGoal(params: com.cy.codex.protocol.protocol.v2.ThreadGoalSetParams) = result {
        val body = obj(
            "threadId" to params.threadId,
            "objective" to params.objective,
            "status" to params.status?.wire,
            // `null` here is the double option: an explicit JSON null clears the ceiling, while an
            // absent key leaves it untouched. `obj` filters Kotlin nulls, so the clear has to be
            // the JsonNull value, not a null reference.
            "tokenBudget" to if (params.clearTokenBudget) JsonNull else params.tokenBudget,
        )
        WireCatalogCodec.goal(rpc("thread/goal/set", body).objectOrNull("goal")!!)
    }
    override suspend fun getGoal(threadId: String) = result { rpc("thread/goal/get", obj("threadId" to threadId)).objectOrNull("goal")?.let(WireCatalogCodec::goal) }
    override suspend fun clearGoal(threadId: String) = call("thread/goal/clear", obj("threadId" to threadId))
    override suspend fun incrementElicitation(threadId: String) = result { rpc("thread/increment_elicitation", obj("threadId" to threadId)).let {
        ElicitationCountResponse(it.int("count") ?: error("Missing elicitation count"), it.bool("paused") == true)
    } }
    override suspend fun decrementElicitation(threadId: String) = result { rpc("thread/decrement_elicitation", obj("threadId" to threadId)).let {
        ElicitationCountResponse(it.int("count") ?: error("Missing elicitation count"), it.bool("paused") == true)
    } }
    override suspend fun listQueue(threadId: String) = result { catalog("thread/queue/list", obj("threadId" to threadId)).map(WireCatalogCodec::queued) }
    override suspend fun addToQueue(threadId: String, inputs: List<UserInput>) = result {
        WireCatalogCodec.queued(rpc("thread/queue/add", obj("threadId" to threadId, "input" to inputs.map(WireCodec::input),
            "clientUserMessageId" to UUID.randomUUID().toString())).objectOrNull("queuedSubmission")!!)
    }
    override suspend fun updateQueued(threadId: String, id: String, inputs: List<UserInput>) = call("thread/queue/update", obj("threadId" to threadId, "queuedSubmissionId" to id, "input" to inputs.map(WireCodec::input)))
    override suspend fun deleteQueued(threadId: String, id: String) = call("thread/queue/delete", obj("threadId" to threadId, "queuedSubmissionId" to id))
    override suspend fun reorderQueue(threadId: String, ids: List<String>) = call("thread/queue/reorder", obj("threadId" to threadId, "queuedSubmissionIds" to ids))
    override suspend fun startQueued(threadId: String, id: String?) = result {
        val turn = rpc("thread/queue/start", obj("threadId" to threadId, "queuedSubmissionId" to id)).objectOrNull("turn")!!
        activeTurns[threadId] = turn.required("id")
        Unit
    }
    override suspend fun updateThreadSettings(threadId: String, model: String?, effort: ReasoningEffort?, approvalPolicy: AskForApproval?, collaborationMode: CollaborationMode?, personality: Personality?) = result {
        require(approvalPolicy != AskForApproval.Granular) { "Granular approval requires an explicit permissions configuration" }
        val current = sessions[threadId]
        val mode = collaborationMode?.let {
            obj("mode" to it.wire, "settings" to obj("model" to (model ?: current?.model ?: error("Load the thread before changing collaboration mode")),
                "reasoning_effort" to (effort ?: current?.reasoningEffort)?.wire, "developer_instructions" to JsonNull))
        }
        rpc("thread/settings/update", obj("threadId" to threadId, "model" to model, "effort" to effort?.wire, "approvalPolicy" to approvalPolicy?.wire,
            "collaborationMode" to mode, "personality" to personality?.wire))
        if (current != null) sessions[threadId] = current.copy(model = model ?: current.model, modelDisplayName = model ?: current.modelDisplayName,
            reasoningEffort = effort ?: current.reasoningEffort, approvalPolicy = approvalPolicy ?: current.approvalPolicy, collaborationMode = collaborationMode ?: current.collaborationMode)
        Unit
    }
    /** One encoder for `SandboxPolicy`, shared by `thread/start` and `thread/settings/update`. */
    private fun sandboxPolicyJson(policy: SandboxPolicy): JsonObject = when (policy.mode) {
        SandboxMode.DangerFullAccess -> obj("type" to "dangerFullAccess")
        SandboxMode.ReadOnly -> obj("type" to "readOnly", "networkAccess" to policy.networkAccess)
        SandboxMode.WorkspaceWrite -> obj("type" to "workspaceWrite", "writableRoots" to policy.writableRoots, "networkAccess" to policy.networkAccess,
            "excludeTmpdirEnvVar" to policy.excludeTmpdirEnvVar, "excludeSlashTmp" to policy.excludeSlashTmp)
    }

    override suspend fun updateThreadSettingsFull(params: ThreadSettingsUpdateParams) = result {
        require(params.approvalPolicy != AskForApproval.Granular) { "Granular approval requires an explicit permissions configuration" }
        val current = sessions[params.threadId]
        val collaboration = params.collaborationMode?.let { obj("mode" to it.wire, "settings" to obj(
            "model" to (params.model ?: current?.model ?: error("Load the thread before changing collaboration mode")),
            "reasoning_effort" to (params.effort ?: current?.reasoningEffort)?.wire, "developer_instructions" to JsonNull)) }
        val sandbox = params.sandboxPolicy?.let(::sandboxPolicyJson)
        rpc("thread/settings/update", obj("threadId" to params.threadId, "model" to params.model, "effort" to params.effort?.wire,
            "approvalPolicy" to params.approvalPolicy?.wire, "approvalsReviewer" to params.approvalsReviewer?.wire, "summary" to params.summary,
            "sandboxPolicy" to sandbox, "permissions" to params.permissions, "collaborationMode" to collaboration, "personality" to params.personality?.wire,
            "serviceTier" to params.serviceTier, "cwd" to params.cwd, "disabledPluginIds" to params.disabledPluginIds, "multiAgentMode" to params.multiAgentMode))
        if (current != null) sessions[params.threadId] = current.copy(model = params.model ?: current.model, reasoningEffort = params.effort ?: current.reasoningEffort,
            approvalPolicy = params.approvalPolicy ?: current.approvalPolicy, approvalsReviewer = params.approvalsReviewer ?: current.approvalsReviewer,
            collaborationMode = params.collaborationMode ?: current.collaborationMode,
            serviceTier = (params.serviceTier ?: current.serviceTier)?.takeUnless { it == "default" })
        Unit
    }
    override suspend fun updateTurnSettings(params: TurnSettingsUpdateParams) = result {
        val response = rpc("turn/settings/update", obj("threadId" to params.threadId, "turnId" to params.turnId, "model" to params.model,
            "effort" to params.effort?.wire, "summary" to params.summary, "approvalsReviewer" to params.approvalsReviewer?.wire, "serviceTier" to params.serviceTier))
        check(response.text("status") == "applied") { "The active turn is no longer available" }
        Unit
    }
    override suspend fun setThreadMemoryMode(threadId: String, mode: ThreadMemoryMode) = result {
        rpc("thread/memoryMode/set", obj("threadId" to threadId, "mode" to mode.wire))
        Unit
    }
    override suspend fun startTurn(
        threadId: String,
        inputs: List<UserInput>,
        outputSchema: JsonElement?,
        effort: com.cy.codex.protocol.protocol.v2.ReasoningEffort?,
        clientMetadata: Map<String, String>?,
    ) = result {
        val turn = rpc("turn/start", obj(
            "threadId" to threadId,
            "input" to inputs.map(WireCodec::input),
            "outputSchema" to outputSchema,
            "effort" to effort?.wire,
            // The experimental export spells this camelCase even though the Rust field is snake.
            "responsesapiClientMetadata" to clientMetadata,
        )).objectOrNull("turn") ?: error("Missing turn")
        turn.required("id").also { activeTurns[threadId] = it }
    }
    override suspend fun steerTurn(threadId: String, inputs: List<UserInput>) = result {
        val id = activeTurns[threadId] ?: error("No active turn in this thread")
        rpc("turn/steer", obj("threadId" to threadId, "expectedTurnId" to id, "input" to inputs.map(WireCodec::input))).required("turnId")
    }
    override suspend fun interruptTurn(threadId: String) = result {
        val id = activeTurns[threadId] ?: error("No active turn in this thread")
        rpc("turn/interrupt", obj("threadId" to threadId, "turnId" to id)); Unit
    }
    override suspend fun startReview(threadId: String, target: ReviewTarget) = result {
        val wireTarget = when (target) {
            ReviewTarget.UncommittedChanges -> obj("type" to "uncommittedChanges")
            is ReviewTarget.BaseBranch -> obj("type" to "baseBranch", "branch" to target.branch)
            is ReviewTarget.Commit -> obj("type" to "commit", "sha" to target.sha, "title" to (target.title ?: JsonNull))
            is ReviewTarget.Custom -> obj("type" to "custom", "instructions" to target.instructions)
        }
        val o = rpc("review/start", obj("threadId" to threadId, "target" to wireTarget))
        val reviewThreadId = o.required("reviewThreadId")
        val turn = WireCodec.turn(o["turn"]!!)
        if (turn.status == TurnStatus.InProgress) activeTurns[reviewThreadId] = turn.id
        ReviewStartResponse(reviewThreadId, turn)
    }

    override suspend fun readAccount() = result { WireCodec.account(rpc("account/read")) }
    override suspend fun compressRollout() = result { rpc("rollout/compress", null); Unit }
    override suspend fun login(params: LoginAccountParams) = result {
        val body = when (params) {
            is LoginAccountParams.ApiKey -> obj("type" to "apiKey", "apiKey" to params.apiKey)
            is LoginAccountParams.Chatgpt -> obj("type" to "chatgpt", "appBrand" to params.appBrand.wire, "useHostedLoginSuccessPage" to params.useHostedLoginSuccessPage)
            LoginAccountParams.ChatgptDeviceCode -> obj("type" to "chatgptDeviceCode")
            is LoginAccountParams.ChatgptAuthTokens -> obj("type" to "chatgptAuthTokens", "accessToken" to params.accessToken, "chatgptAccountId" to params.chatgptAccountId, "chatgptPlanType" to params.chatgptPlanType)
            is LoginAccountParams.AmazonBedrock -> obj("type" to "amazonBedrock", "apiKey" to params.apiKey, "region" to params.region)
            is LoginAccountParams.AmazonBedrockAccessKeys -> obj("type" to "amazonBedrockAccessKeys", "accessKeyId" to params.accessKeyId, "secretAccessKey" to params.secretAccessKey, "sessionToken" to params.sessionToken, "region" to params.region)
        }
        val o = rpc("account/login/start", body)
        when (o.required("type")) {
            "apiKey" -> LoginAccountResponse.ApiKey
            "chatgpt" -> LoginAccountResponse.Chatgpt(o.required("loginId"), o.required("authUrl"))
            "chatgptDeviceCode" -> LoginAccountResponse.ChatgptDeviceCode(o.required("loginId"), o.required("userCode"), o.required("verificationUrl"))
            "chatgptAuthTokens" -> LoginAccountResponse.ChatgptAuthTokens
            "amazonBedrock", "amazonBedrockAccessKeys" -> LoginAccountResponse.AmazonBedrock
            else -> error("Unsupported login response: ${o.text("type")}")
        }
    }
    override suspend fun cancelLogin(loginId: String) = call("account/login/cancel", obj("loginId" to loginId))
    override suspend fun logout() = call("account/logout", null)
    override suspend fun readRateLimits() = result {
        // `supportsLunaReserve` is a capability claim, not a hint: it tells the backend this client
        // can be recorded in the fallback experiment. An older server rejects the unknown params
        // outright, so the read retries with none rather than failing the usage screen
        // (`app/background_requests.rs` in the TUI does the same).
        val read = runCatching {
            rpc("account/rateLimits/read", obj("supportsLunaReserve" to true))
        }.recoverCatching { error ->
            val code = (error as? AppServerRpcException)?.code
            if (code != -32600 && code != -32602) throw error
            rpc("account/rateLimits/read", null)
        }
        WireCodec.accountRateLimits(read.getOrThrow())
    }
    override suspend fun readUsage() = result {
        val o = rpc("account/usage/read")
        val summary = o.objectOrNull("summary")
        AccountUsage(
            dailyBuckets = o.array("dailyUsageBuckets").map {
                it.objectValue().let { bucket -> UsageBucket(bucket.required("startDate"), bucket.int("tokens") ?: 0) }
            },
            totalTokens = summary?.long("lifetimeTokens") ?: 0L,
            peakDailyTokens = summary?.long("peakDailyTokens") ?: 0L,
            longestRunningTurnSec = summary?.long("longestRunningTurnSec") ?: 0L,
            currentStreakDays = summary?.long("currentStreakDays") ?: 0L,
            longestStreakDays = summary?.long("longestStreakDays") ?: 0L,
        )
    }
    override suspend fun readThreadUsage(threadId: String) = result {
        val o = rpc("account/usage/read", obj("threadId" to threadId))
        WireCodec.threadUsage(o.objectOrNull("threadUsage") ?: error("account/usage/read answered without threadUsage"))
    }
    override suspend fun readWorkspaceMessages() = result { rpc("account/workspaceMessages/read", null).array("messages").map { value -> value.objectValue().let {
        WorkspaceMessage(it.required("messageId"), WorkspaceMessageType.fromWire(it.text("messageType")), it.required("messageBody"),
            it.long("createdAt")?.times(1000), it.long("archivedAt")?.times(1000))
    } } }

    override suspend fun readConfig(cwd: String?, includeLayers: Boolean) = result { WireCodec.config(rpc("config/read", obj("cwd" to cwd, "includeLayers" to includeLayers))) }
    override suspend fun readConfigLayers() = readConfig().map { it.layers.orEmpty() }
    override suspend fun readConfigRequirements() = result { ConfigRequirementsReadResponse(rpc("configRequirements/read", null)["requirements"] ?: JsonNull) }
    private fun configWritten(o: JsonObject) = ConfigWriteResponse(o.required("filePath"), WriteStatus.fromWire(o.text("status")), o.text("version").orEmpty())
    override suspend fun writeConfigValue(params: ConfigValueWriteParams) = result {
        configWritten(rpc("config/value/write", obj("keyPath" to params.keyPath, "value" to params.value, "mergeStrategy" to params.mergeStrategy.wire, "filePath" to params.filePath, "expectedVersion" to params.expectedVersion)))
    }
    override suspend fun writeConfigBatch(params: ConfigBatchWriteParams) = result {
        configWritten(rpc("config/batchWrite", obj("edits" to params.edits.map { obj("keyPath" to it.keyPath, "value" to it.value, "mergeStrategy" to it.mergeStrategy.wire) },
            "filePath" to params.filePath, "expectedVersion" to params.expectedVersion, "reloadUserConfig" to params.reloadUserConfig)))
    }
    override suspend fun reloadMcpServers() = call("config/mcpServer/reload", null)

    override suspend fun listModels() = result {
        val models = mutableListOf<ModelPreset>()
        var cursor: String? = null
        do {
            val page = rpc("model/list", obj("limit" to 100, "cursor" to cursor))
            models += page.array("data").map { value -> value.objectValue().let { o -> ModelPreset(
                id = o.required("id"), model = o.required("model"), displayName = o.required("displayName"), description = o.required("description"),
                defaultReasoningEffort = ReasoningEffort.fromWire(o.required("defaultReasoningEffort")),
                supportedReasoningEfforts = o.array("supportedReasoningEfforts").map { ReasoningEffort.fromWire(it.objectValue().required("reasoningEffort")) },
                isDefault = o.bool("isDefault") == true, hidden = o.bool("hidden") == true,
                defaultServiceTier = o.text("defaultServiceTier"),
                serviceTiers = o.array("serviceTiers").map { tier -> tier.objectValue().let { ModelServiceTier(it.required("id"), it.required("name"), it.required("description")) } },
                inputModalities = o.strings("inputModalities").mapNotNull { wire -> InputModality.entries.find { it.wire == wire } },
            ) } }
            val next = page.text("nextCursor")
            check(next == null || next != cursor) { "Model pagination did not advance" }
            cursor = next
        } while (cursor != null)
        models
    }

    private suspend fun catalog(method: String, params: JsonObject = obj()): List<JsonObject> {
        val values = mutableListOf<JsonObject>()
        var cursor: String? = null
        do {
            val page = rpc(method, JsonObject(params + obj("limit" to 100, "cursor" to cursor)))
            values += page.array("data").map { it.objectValue() }
            val next = page.text("nextCursor")
            check(next == null || next != cursor) { "Catalog pagination did not advance: $method" }
            cursor = next
        } while (cursor != null)
        return values
    }
    override suspend fun readModelProviderCapabilities() = result { rpc("modelProvider/capabilities/read").mapNotNull { (name, value) ->
        (value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull?.let { name to it }
    }.toMap() }
    override suspend fun listPermissionProfiles() = result {
        catalog("permissionProfile/list").map { o -> PermissionProfileEntry(o.required("id"), o.required("id"), o.text("description").orEmpty()) }
    }
    override suspend fun listExperimentalFeatures() = result {
        catalog("experimentalFeature/list").map { o -> ExperimentalFeatureEntry(o.required("name"), o.text("displayName") ?: o.required("name"),
            o.text("description").orEmpty(), o.bool("enabled") == true, o.required("stage")) }
    }
    override suspend fun setExperimentalFeature(id: String, enabled: Boolean) = call("experimentalFeature/enablement/set", obj("enablement" to obj(id to enabled)))
    override suspend fun listCollaborationModes() = result {
        catalog("collaborationMode/list").map { o ->
            val mode = CollaborationMode.entries.find { it.wire == o.text("mode") } ?: CollaborationMode.Default
            CollaborationModeEntry(o.text("mode") ?: o.required("name"), mode, o.required("name"), readonly = mode == CollaborationMode.Plan)
        }
    }
    override suspend fun listSkills() = result {
        rpc("skills/list", obj("cwds" to defaultWorkspace?.let(::listOf))).array("data").flatMap { it.objectValue().array("skills") }
            .map { WireCatalogCodec.skill(it.objectValue()) }
    }
    override suspend fun writeSkillConfig(name: String, enabled: Boolean) = call("skills/config/write", obj(if (name.startsWith('/')) "path" to name else "name" to name, "enabled" to enabled))
    override suspend fun setSkillExtraRoots(roots: List<String>) = call("skills/extraRoots/set", obj("extraRoots" to roots))
    override suspend fun listMcpServers() = result {
        val servers = mutableListOf<McpServerStatusEntry>()
        var cursor: String? = null
        do {
            val page = rpc("mcpServerStatus/list", obj("cursor" to cursor, "limit" to 100))
            servers += page.array("data").map { value -> value.objectValue().let { o -> McpServerStatusEntry(o.required("name"),
                McpServerConnectionStatus.entries.find { it.wire == o.text("runtimeStatus") } ?: McpServerConnectionStatus.Starting,
                o.objectOrNull("tools")?.size ?: 0, o.array("resources").size, o.text("toolsError"),
                McpAuthStatus.fromWire(o.text("authStatus"))) } }
            val next = page.text("nextCursor")
            check(next == null || next != cursor) { "MCP pagination did not advance" }
            cursor = next
        } while (cursor != null)
        servers
    }
    override suspend fun mcpOauthLogin(name: String) = result { rpc("mcpServer/oauth/login", obj("name" to name)).required("authorizationUrl") }
    override suspend fun callMcpTool(server: String, tool: String, arguments: String, threadId: String?) = result {
        val thread = threadId ?: sessions.keys.singleOrNull() ?: error("Select a loaded thread before calling an MCP tool")
        val o = rpc("mcpServer/tool/call", obj("threadId" to thread, "server" to server, "tool" to tool, "arguments" to Json.parse(arguments)))
        McpServerToolCallResponse(Json.write(o), o.bool("isError") == true)
    }
    override suspend fun readMcpResource(server: String, uri: String) = result {
        val o = rpc("mcpServer/resource/read", obj("server" to server, "uri" to uri))
        McpResourceReadResponse(
            contents = o.array("contents").map { value -> value.objectValue().let { content ->
                ResourceContent(content.required("uri"), content.text("mimeType"), content.text("text"), content.text("blob"))
            } },
            originCallId = o.text("originCallId"),
        )
    }
    override suspend fun readMemoryStatus() = result { rpc("memory/status").let { MemoryStatusResponse(it.bool("v2Ready") == true, it.int("v2ConsolidatedThreads") ?: 0) } }
    override suspend fun resetMemory() = call("memory/reset", null)

    // ---- account: reset credits, nudge e-mail, Bedrock ------------------------
    override suspend fun consumeRateLimitResetCredit(creditId: String?) = result {
        val o = rpc("account/rateLimitResetCredit/consume", obj("idempotencyKey" to UUID.randomUUID().toString(), "creditId" to creditId))
        ConsumeRateLimitResetCreditResponse(ConsumeRateLimitResetCreditOutcome.fromWire(o.text("outcome")))
    }
    override suspend fun sendAddCreditsNudgeEmail(creditType: AddCreditsNudgeCreditType) = result {
        val o = rpc("account/sendAddCreditsNudgeEmail", obj("creditType" to creditType.wire))
        SendAddCreditsNudgeEmailResponse(AddCreditsNudgeEmailStatus.fromWire(o.text("status")))
    }
    override suspend fun bedrockDiscover() = result {
        val o = rpc("account/bedrock/discover")
        BedrockDiscoverResponse(
            profiles = o.array("profiles").map { value -> value.objectValue().let { BedrockAwsProfile(it.required("name"), it.text("region")) } },
            environmentCredentials = o.array("environmentCredentials").map { value -> value.objectValue().let {
                BedrockEnvironmentCredential(it.text("type").orEmpty(), it.text("region")) } },
        )
    }
    override suspend fun bedrockSetup(params: BedrockSetupParams) = result {
        val body = when (params) {
            is BedrockSetupParams.Profile -> obj("type" to "profile", "profile" to params.profile, "region" to params.region)
            is BedrockSetupParams.Environment -> obj("type" to "environment", "region" to params.region)
        }
        rpc("account/bedrock/setup", body)
        Unit
    }

    // ---- environments ---------------------------------------------------------
    override suspend fun addEnvironment(environmentId: String, execServerUrl: String, connectTimeoutMs: Long?) = call("environment/add",
        obj("environmentId" to environmentId, "execServerUrl" to execServerUrl, "connectTimeoutMs" to connectTimeoutMs))
    override suspend fun readEnvironmentInfo(environmentId: String) = result {
        val o = rpc("environment/info", obj("environmentId" to environmentId))
        val shell = o.objectOrNull("shell")
        EnvironmentInfoResponse(EnvironmentShellInfo(shell?.text("name").orEmpty(), shell?.text("path").orEmpty()), o.text("cwd"))
    }
    override suspend fun readEnvironmentStatus(environmentId: String) = result {
        val o = rpc("environment/status", obj("environmentId" to environmentId))
        EnvironmentStatusResponse(EnvironmentStatusKind.fromWire(o.text("status")), o.text("error"))
    }

    // ---- remote control -------------------------------------------------------
    override suspend fun readRemoteControlStatus() = result { remoteControlStatus(rpc("remoteControl/status/read", null)) }
    override suspend fun enableRemoteControl(ephemeral: Boolean) = result { remoteControlStatus(rpc("remoteControl/enable", obj("ephemeral" to ephemeral))) }
    override suspend fun disableRemoteControl(ephemeral: Boolean) = result { remoteControlStatus(rpc("remoteControl/disable", obj("ephemeral" to ephemeral))) }
    override suspend fun startRemoteControlPairing(manualCode: Boolean) = result {
        val o = rpc("remoteControl/pairing/start", obj("manualCode" to manualCode))
        RemoteControlPairingStartResponse(o.required("pairingCode"), o.text("manualPairingCode"), o.required("environmentId"), o.long("expiresAt") ?: 0L)
    }
    override suspend fun readRemoteControlPairing(pairingCode: String?, manualPairingCode: String?) = result {
        val o = rpc("remoteControl/pairing/status", obj("pairingCode" to pairingCode, "manualPairingCode" to manualPairingCode))
        RemoteControlPairingStatusResponse(o.bool("claimed") == true)
    }
    override suspend fun listRemoteControlClients(environmentId: String, cursor: String?, limit: Int?) = result {
        val o = rpc("remoteControl/client/list", obj("environmentId" to environmentId, "cursor" to cursor, "limit" to limit))
        RemoteControlClientsListResponse(o.array("data").map { value -> value.objectValue().let { client ->
            RemoteControlClient(client.required("clientId"), client.text("displayName"), client.text("deviceType"), client.text("platform"),
                client.text("osVersion"), client.text("deviceModel"), client.text("appVersion"), client.long("lastSeenAt"))
        } }, o.text("nextCursor"))
    }
    override suspend fun revokeRemoteControlClient(environmentId: String, clientId: String) = call("remoteControl/client/revoke",
        obj("environmentId" to environmentId, "clientId" to clientId))

    private fun remoteControlStatus(o: JsonObject) = RemoteControlStatus(RemoteControlConnectionStatus.fromWire(o.text("status")),
        o.text("serverName").orEmpty(), o.text("installationId").orEmpty(), o.text("environmentId"))

    // ---- user verification ----------------------------------------------------
    override suspend fun readUserVerificationStatus() = result {
        val o = rpc("userVerification/status")
        UserVerificationStatusResponse(o.text("credentialId"),
            o.text("unavailableReason")?.let { UserVerificationUnavailableReason.entries.find { reason -> reason.wire == it } },
            o.text("unavailableMessage"))
    }
    override suspend fun enrollUserVerification() = result {
        val o = rpc("userVerification/enroll")
        UserVerificationEnrollResponse(o.required("credentialId"), o.text("algorithm"), o.text("publicKey"))
    }
    override suspend fun verifyUserVerification(params: UserVerificationVerifyParams) = result {
        val o = rpc("userVerification/verify", obj("challenge" to params.challenge, "title" to params.title, "description" to params.description))
        val proof = o.objectOrNull("proof") ?: error("Missing verification proof")
        UserVerificationVerifyResponse(UserVerificationProof(proof.required("credentialId"), proof.required("signature")))
    }
    override suspend fun cancelUserVerification(requestId: String) = call("userVerification/cancel", obj("requestId" to requestId))
    override suspend fun deleteUserVerification() = call("userVerification/delete")

    // ---- external agent config migration -------------------------------------
    override suspend fun detectExternalAgentConfig() = result {
        val o = rpc("externalAgentConfig/detect", obj("includeHome" to true, "cwds" to defaultWorkspace?.let(::listOf)))
        ExternalAgentConfigDetectResponse(
            items = o.array("items").map(WireCodec::externalAgentConfigItem),
            connectors = o.array("connectors").map { value -> value.objectValue().let { c ->
                ExternalAgentDetectedConnectorCandidate(c.required("name"), c.long("sessionCount") ?: 0L, c.text("source").orEmpty()) } },
        )
    }
    override suspend fun importExternalAgentConfig(items: List<ExternalAgentConfigMigrationItem>) = result {
        rpc("externalAgentConfig/import", obj("migrationItems" to items.map(WireCodec::externalAgentConfigItemOut), "source" to "android",
            "providerId" to "android", "migrationSource" to "android")).required("importId")
    }
    override suspend fun readExternalAgentImportHistories() = result {
        rpc("externalAgentConfig/import/readHistories", null).array("data").map { WireCodec.externalAgentImportHistory(it.objectValue()) }
    }
    override suspend fun recordExternalAgentImportHistory(params: ExternalAgentConfigImportHistoryRecordParams) = result {
        rpc("externalAgentConfig/import/recordHistory", obj("providerId" to params.providerId,
            "itemTypeResults" to params.itemTypeResults.map(WireCodec::externalAgentImportTypeResultOut))).required("importId")
    }

    // ---- fuzzy file search ----------------------------------------------------
    override suspend fun fuzzyFileSearch(query: String, roots: List<String>) = result {
        rpc("fuzzyFileSearch", obj("query" to query, "roots" to roots)).array("files").map { WireCodec.fuzzyResult(it.objectValue()) }
    }
    override suspend fun startFuzzySearchSession(sessionId: String, roots: List<String>) = call("fuzzyFileSearch/sessionStart",
        obj("sessionId" to sessionId, "roots" to roots))
    override suspend fun updateFuzzySearchSession(sessionId: String, query: String) = call("fuzzyFileSearch/sessionUpdate",
        obj("sessionId" to sessionId, "query" to query))
    override suspend fun stopFuzzySearchSession(sessionId: String) = call("fuzzyFileSearch/sessionStop", obj("sessionId" to sessionId))

    // ---- hooks, feedback, diagnostics ----------------------------------------
    override suspend fun listHooks() = result {
        // `hooks/list` has no cursor: it answers with one entry per requested working directory.
        rpc("hooks/list", obj("cwds" to defaultWorkspace?.let(::listOf))).array("data")
            .map { entry ->
                val o = entry.objectValue()
                HooksListEntry(
                    cwd = o.text("cwd").orEmpty(),
                    hooks = o.array("hooks").map { WireCodec.hookMetadata(it.objectValue()) },
                    warnings = o.strings("warnings"),
                    errors = o.array("errors").map { value -> value.objectValue().let {
                        HookErrorInfo(it.text("path").orEmpty(), it.text("message").orEmpty()) } },
                )
            }
    }
    override suspend fun uploadFeedback(params: FeedbackUploadParams) = result {
        val o = rpc("feedback/upload", obj("classification" to params.classification, "reason" to params.reason, "threadId" to params.threadId,
            "includeLogs" to params.includeLogs, "extraLogFiles" to params.extraLogFiles, "tags" to params.tags))
        FeedbackUploadResponse(o.required("threadId"), o.text("promptHash"))
    }
    override suspend fun readServerDiagnostics() = result { WireCodec.diagnostics(rpc("server/diagnostics")) }

    // ---- mcpServer/… event streams -------------------------------------------
    override suspend fun startMcpEventStream(server: String, subscriptionId: String, name: String, arguments: JsonElement, threadId: String) = call(
        "mcpServer/event/stream/start", obj("threadId" to threadId, "server" to server, "subscriptionId" to subscriptionId,
            "name" to name, "arguments" to arguments))
    override suspend fun stopMcpEventStream(subscriptionId: String) = call("mcpServer/event/stream/stop", obj("subscriptionId" to subscriptionId))

    // ---- plugin shares --------------------------------------------------------
    override suspend fun listPluginShares() = result {
        rpc("plugin/share/list").array("data").map { WireCodec.pluginShare(it.objectValue()) }
    }
    override suspend fun savePluginShare(pluginPath: String, remotePluginId: String?) = result {
        val o = rpc("plugin/share/save", obj("pluginPath" to pluginPath, "remotePluginId" to remotePluginId))
        PluginShareSaveResponse(o.required("remotePluginId"), o.text("shareUrl").orEmpty(), o.bool("canPublishToWorkspace"))
    }
    override suspend fun deletePluginShare(remotePluginId: String) = call("plugin/share/delete", obj("remotePluginId" to remotePluginId))
    override suspend fun checkoutPluginShare(remotePluginId: String) = result {
        val o = rpc("plugin/share/checkout", obj("remotePluginId" to remotePluginId))
        PluginShareCheckoutResponse(o.required("remotePluginId"), o.required("pluginId"), o.required("pluginName"), o.required("pluginPath"),
            o.required("marketplaceName"), o.required("marketplacePath"), o.text("remoteVersion"))
    }
    override suspend fun updatePluginShareTargets(remotePluginId: String, discoverability: PluginShareDiscoverability,
        targets: List<PluginShareTarget>) = result {
        val o = rpc("plugin/share/updateTargets", obj("remotePluginId" to remotePluginId, "discoverability" to discoverability.wire,
            "shareTargets" to targets.map { obj("principalType" to it.principalType, "principalId" to it.principalId, "role" to it.role) }))
        PluginShareUpdateTargetsResponse(o.array("principals").map { WireCodec.pluginSharePrincipals(it.objectValue()) },
            PluginShareDiscoverability.fromWire(o.text("discoverability")))
    }

    // ---- windows sandbox ------------------------------------------------------
    override suspend fun windowsSandboxReadiness() = result {
        val status = rpc("windowsSandbox/readiness", null).text("status")
        WindowsSandboxReadinessResponse(WindowsSandboxReadiness.entries.find { it.wire == status } ?: WindowsSandboxReadiness.NotConfigured)
    }
    override suspend fun windowsSandboxSetupStart(mode: WindowsSandboxSetupMode, cwd: String?) = result {
        WindowsSandboxSetupStartResponse(rpc("windowsSandbox/setupStart", obj("mode" to mode.wire, "cwd" to cwd)).bool("started") == true)
    }

    override suspend fun listProjects() = result { catalog("project/list").map(WireCatalogCodec::project) }
    override suspend fun readProject(projectId: String) = result { WireCatalogCodec.project(rpc("project/read", obj("projectId" to projectId)).objectOrNull("project")!!) }
    override suspend fun createProject(name: String, path: String) = result {
        WireCatalogCodec.project(rpc("project/create", obj("name" to name, "roots" to listOf(obj("path" to path)), "idempotencyKey" to UUID.randomUUID().toString())).objectOrNull("project")!!)
    }
    override suspend fun updateProject(projectId: String, name: String?, path: String?) = result {
        WireCatalogCodec.project(rpc("project/update", obj("projectId" to projectId, "name" to name, "roots" to path?.let { listOf(obj("path" to it)) })).objectOrNull("project")!!)
    }
    override suspend fun deleteProject(projectId: String) = call("project/delete", obj("projectId" to projectId))
    override suspend fun moveProject(projectId: String, position: Int) = result {
        val others = listProjects().getOrThrow().filterNot { it.id == projectId }
        require(position in 0..others.size) { "Project position is out of range" }
        rpc("project/move", obj("projectId" to projectId, "beforeProjectId" to others.getOrNull(position)?.id))
        Unit
    }
    override suspend fun importProject(path: String) = result {
        WireCatalogCodec.project(rpc("project/import", obj("name" to path.trimEnd('/').substringAfterLast('/'), "roots" to listOf(obj("path" to path)),
            "idempotencyKey" to UUID.randomUUID().toString())).objectOrNull("project")!!)
    }

    private fun marketplaces(o: JsonObject): List<MarketplaceEntry> = o.array("marketplaces").map { WireCatalogCodec.marketplace(it.objectValue()) }.also { list ->
        list.forEach { if (it.path.isNotBlank()) marketplacePaths[it.name] = it.path }
    }
    override suspend fun listPlugins(params: PluginListParams) = result {
        val o = rpc("plugin/list", obj("cwds" to (params.cwds ?: defaultWorkspace?.let(::listOf)), "forceRefetch" to params.forceRefetch,
            "marketplaceKinds" to params.marketplaceKinds?.map { it.wire }))
        PluginListResponse(marketplaces(o), o.strings("featuredPluginIds"), WireCatalogCodec.marketplaceErrors(o))
    }
    override suspend fun listInstalledPlugins(params: PluginInstalledParams) = result {
        val o = rpc("plugin/installed", obj("cwds" to (params.cwds ?: defaultWorkspace?.let(::listOf)), "installSuggestionPluginNames" to params.installSuggestionPluginNames))
        PluginInstalledResponse(marketplaces(o), WireCatalogCodec.marketplaceErrors(o))
    }
    private fun pluginSelector(name: String, marketplace: String?): JsonObject {
        val path = marketplace?.takeIf { it.startsWith('/') } ?: marketplacePaths[marketplace.orEmpty()]
        return obj("pluginName" to name, "marketplacePath" to path, "remoteMarketplaceName" to marketplace?.takeIf { path == null })
    }
    override suspend fun readPlugin(name: String, marketplace: String?) = result { WireCatalogCodec.pluginDetail(rpc("plugin/read", pluginSelector(name, marketplace)).objectOrNull("plugin")!!) }
    override suspend fun installPlugin(name: String, marketplace: String?) = result {
        val o = rpc("plugin/install", pluginSelector(name, marketplace))
        PluginInstallResponse(
            authPolicy = PluginAuthPolicy.fromWire(o.text("authPolicy")),
            appsNeedingAuth = o.array("appsNeedingAuth").map {
                WireCatalogCodec.appSummary(it.objectValue())
            },
        )
    }
    override suspend fun uninstallPlugin(pluginId: String) = call("plugin/uninstall", obj("pluginId" to pluginId))
    override suspend fun readPluginSkill(marketplace: String, pluginId: String, skillName: String) = result {
        rpc("plugin/skill/read", obj("remoteMarketplaceName" to marketplace, "remotePluginId" to pluginId, "skillName" to skillName)).text("contents")
    }
    override suspend fun reconcilePlugins() = result {
        val o = rpc("plugin/reconcile")
        check(o.strings("failedRemotePluginIds").isEmpty()) { "Plugin reconciliation failed for: ${o.strings("failedRemotePluginIds").joinToString()}" }
        listInstalledPlugins().getOrThrow().marketplaces.flatMap { it.plugins }
    }
    override suspend fun searchPlugins(term: String) = result {
        listPlugins().getOrThrow().marketplaces.flatMap { it.plugins }.filter { it.name.contains(term, true) || it.description.contains(term, true) }
    }
    override suspend fun addMarketplace(source: String, refName: String?) = result {
        val o = rpc("marketplace/add", obj("source" to source, "refName" to refName))
        MarketplaceEntry(o.required("marketplaceName"), o.required("installedRoot"))
    }
    override suspend fun removeMarketplace(name: String) = call("marketplace/remove", obj("marketplaceName" to name))
    override suspend fun upgradeMarketplace(name: String?) = result {
        val o = rpc("marketplace/upgrade", obj("marketplaceName" to name))
        check(o.array("errors").isEmpty()) { "Marketplace update failed: ${o.array("errors").joinToString { Json.write(it) }}" }
        o.strings("upgradedRoots")
    }
    override suspend fun listApps() = result { catalog("app/list").map(WireCatalogCodec::app) }
    override suspend fun listInstalledApps() = result { rpc("app/installed").array("apps").map { value -> value.objectValue().let { o ->
        AppInfo(o.required("id"), o.text("runtimeName") ?: o.required("id"), installed = true)
    } } }
    override suspend fun readApps(ids: List<String>) = result {
        ids.distinct().chunked(100).flatMap { batch -> rpc("app/read", obj("appIds" to batch)).array("apps").map { WireCatalogCodec.app(it.objectValue()) } }
    }

    override suspend fun readFile(path: String) = result { Base64.getDecoder().decode(rpc("fs/readFile", obj("path" to path)).required("dataBase64")) }
    override suspend fun writeFile(path: String, bytes: ByteArray) = call("fs/writeFile", obj("path" to path, "dataBase64" to Base64.getEncoder().encodeToString(bytes)))
    override suspend fun readDirectory(path: String) = result {
        rpc("fs/readDirectory", obj("path" to path)).array("entries").map { value -> value.objectValue().let { o ->
            FileMetadata(path.trimEnd('/') + "/" + o.required("fileName"), o.bool("isDirectory") == true, isFile = o.bool("isFile") == true, isSymlink = o.bool("isSymlink") == true) } }
    }
    override suspend fun getMetadata(path: String) = result { rpc("fs/getMetadata", obj("path" to path)).let { o ->
        FileMetadata(path, o.bool("isDirectory") == true, modifiedAt = o.long("modifiedAtMs") ?: 0, isFile = o.bool("isFile") == true, isSymlink = o.bool("isSymlink") == true, createdAt = o.long("createdAtMs") ?: 0) } }
    override suspend fun createDirectory(path: String, recursive: Boolean) = call("fs/createDirectory", obj("path" to path, "recursive" to recursive))
    override suspend fun removePath(path: String, recursive: Boolean) = call("fs/remove", obj("path" to path, "recursive" to recursive))
    override suspend fun copyPath(source: String, destination: String, recursive: Boolean) = call("fs/copy", obj("sourcePath" to source, "destinationPath" to destination, "recursive" to recursive))
    override suspend fun watchPath(path: String, watchId: String) = call("fs/watch", obj("path" to path, "watchId" to watchId))
    override suspend fun unwatchPath(watchId: String) = call("fs/unwatch", obj("watchId" to watchId))
    override suspend fun execCommand(
        command: List<String>,
        cwd: String?,
        timeoutMs: Long?,
        tty: Boolean,
        env: Map<String, String>?,
    ) = result {
        require(!tty) { "Interactive command sessions are not yet supported" }
        val o = rpc("command/exec", obj("command" to command, "cwd" to (cwd ?: defaultWorkspace), "timeoutMs" to timeoutMs, "env" to env))
        CommandExecResponse(exitCode = o.int("exitCode") ?: error("Missing command exit code"), stdout = o.text("stdout").orEmpty(), stderr = o.text("stderr").orEmpty())
    }
    override suspend fun execWrite(processId: String, data: ByteArray?, closeStdin: Boolean) = call("command/exec/write", obj("processId" to processId, "deltaBase64" to data?.let { Base64.getEncoder().encodeToString(it) }, "closeStdin" to closeStdin))
    override suspend fun execResize(processId: String, rows: Int, cols: Int) = call("command/exec/resize", obj("processId" to processId, "size" to obj("rows" to rows, "cols" to cols)))
    override suspend fun execTerminate(processId: String) = call("command/exec/terminate", obj("processId" to processId))

    /**
     * `process/spawn`.
     *
     * The process handle is connection-scoped and client-supplied, so this client mints one and
     * answers with it: the handle is what the three follow-up calls address, and upstream has no
     * server-side id to read out of the response. A tty implies stdin and stdout streaming, so both
     * flags follow [tty]; a buffered run still streams stdout because the response only carries the
     * tail.
     */
    override suspend fun spawnProcess(command: List<String>, cwd: String?, tty: Boolean) = result {
        val handle = UUID.randomUUID().toString()
        val workingDirectory = cwd?.takeIf { it.isNotBlank() } ?: defaultWorkspace ?: error("A working directory is required to spawn a process")
        rpc("process/spawn", obj("command" to command, "processHandle" to handle, "cwd" to workingDirectory,
            "tty" to tty, "streamStdin" to tty, "streamStdoutStderr" to true))
        handle
    }
    override suspend fun writeProcessStdin(processId: String, data: ByteArray?, closeStdin: Boolean) = call("process/writeStdin",
        obj("processHandle" to processId, "deltaBase64" to data?.let { Base64.getEncoder().encodeToString(it) }, "closeStdin" to closeStdin))
    override suspend fun resizeProcessPty(processId: String, rows: Int, cols: Int) = call("process/resizePty",
        obj("processHandle" to processId, "size" to obj("rows" to rows, "cols" to cols)))
    override suspend fun killProcess(processId: String) = call("process/kill", obj("processHandle" to processId))

    private suspend fun notification(method: String, p: JsonObject) {
        val threadId = p.text("threadId").orEmpty()
        val turnId = p.text("turnId").orEmpty()
        val itemId = p.text("itemId").orEmpty()
        fun delta() = ItemTextDelta(threadId, turnId, itemId, p.text("delta").orEmpty(), p.int("summaryIndex") ?: 0)
        val event: AppServerEvent? = when (method) {
            "item/started" -> AppServerEvent.ItemStarted(threadId, turnId, WireCodec.item(p["item"]!!))
            "item/completed" -> AppServerEvent.ItemCompleted(threadId, turnId, WireCodec.item(p["item"]!!))
            "item/agentMessage/delta" -> AppServerEvent.AgentMessageDelta(threadId, delta())
            "item/plan/delta" -> AppServerEvent.PlanDelta(threadId, delta())
            "item/reasoning/textDelta" -> AppServerEvent.ReasoningTextDelta(threadId, delta())
            "item/reasoning/summaryTextDelta" -> AppServerEvent.ReasoningSummaryDelta(threadId, delta())
            "item/reasoning/summaryPartAdded" -> AppServerEvent.ReasoningSummaryPartAdded(threadId, delta())
            "item/commandExecution/outputDelta" -> AppServerEvent.CommandOutputDelta(threadId, CommandExecutionOutputDelta(threadId, turnId, itemId, p.required("delta")))
            "item/commandExecution/terminalInteraction" -> AppServerEvent.CommandTerminalInteraction(threadId, TerminalInteraction(threadId, turnId, itemId, p.required("processId"), p.required("stdin")))
            "item/fileChange/outputDelta" -> AppServerEvent.FileChangeOutputDelta(threadId, FileChangeOutputDelta(threadId, turnId, itemId, p.required("delta")))
            "item/fileChange/patchUpdated" -> AppServerEvent.FileChangePatchUpdated(threadId, FileChangePatchUpdatedNotification(threadId, turnId, itemId, WireCodec.changes(p)))
            "item/mcpToolCall/progress" -> AppServerEvent.McpToolProgress(threadId, McpToolCallProgress(threadId, turnId, itemId, p.required("message")))
            "turn/started" -> {
                val id = p.objectOrNull("turn")!!.required("id")
                activeTurns[threadId] = id
                AppServerEvent.TurnStarted(threadId, id)
            }
            "turn/completed" -> {
                val t = p.objectOrNull("turn")!!
                val error = t.objectOrNull("error")
                activeTurns.remove(threadId, t.required("id"))
                AppServerEvent.TurnCompleted(
                    threadId,
                    t.required("id"),
                    TurnStatus.fromWire(t.required("status")),
                    error?.text("message"),
                    durationMs = t.long("durationMs"),
                    completedAt = t.long("completedAt")?.times(1000),
                    misalignment = error?.objectOrNull("misalignment")?.let { details ->
                        com.cy.codex.protocol.protocol.v2.MisalignmentErrorDetails(
                            errorType = details.text("errorType"),
                            detailedExplanation = details.text("detailedExplanation"),
                            steer = details.objectOrNull("steer")?.text("message")
                                ?.let { MisalignmentSteer(it) },
                        )
                    },
                )
            }
            "turn/diff/updated" -> AppServerEvent.TurnDiffUpdatedEvent(threadId, TurnDiffUpdated(threadId, turnId, p.required("diff")))
            "turn/plan/updated" -> AppServerEvent.TurnPlanUpdatedEvent(threadId, TurnPlanUpdated(threadId, turnId, p.array("plan").map { it.objectValue().let { step -> PlanStep(step.required("step"), PlanStepStatus.fromWire(step.required("status"))) } }))
            "thread/started" -> WireCodec.thread(p["thread"]!!).let { AppServerEvent.ThreadStartedEvent(it.id, it) }
            "thread/closed" -> AppServerEvent.ThreadClosed(threadId)
            "thread/archived" -> AppServerEvent.ThreadArchived(threadId)
            "thread/unarchived" -> AppServerEvent.ThreadUnarchived(threadId)
            "thread/deleted" -> AppServerEvent.ThreadDeleted(threadId)
            "thread/compacted" -> AppServerEvent.ThreadCompacted(threadId, p.text("summary"))
            "thread/reverted" -> AppServerEvent.ThreadRevertedEvent(threadId, ThreadReverted(threadId, null))
            "thread/queue/changed" -> AppServerEvent.ThreadQueueChangedEvent(threadId, ThreadQueueChanged(threadId))
            "thread/goal/updated" -> AppServerEvent.ThreadGoalUpdatedEvent(threadId, WireCatalogCodec.goal(p.objectOrNull("goal")!!))
            "thread/goal/cleared" -> AppServerEvent.ThreadGoalCleared(threadId)
            "thread/project/updated" -> AppServerEvent.ThreadProjectUpdated(threadId, p.text("projectId"))
            "project/changed" -> AppServerEvent.ProjectChanged(ProjectChangedNotification(p.text("projectId")))
            "thread/name/updated" -> AppServerEvent.ThreadNameUpdatedEvent(threadId, ThreadNameUpdated(threadId, p.text("threadName") ?: p.text("name")))
            "thread/status/changed" -> AppServerEvent.ThreadStatusChangedEvent(threadId, ThreadStatusChanged(threadId, WireCodec.status(p["status"])))
            "thread/settings/updated" -> {
                val settings = p.objectOrNull("threadSettings") ?: p
                AppServerEvent.ThreadSettingsUpdatedEvent(threadId, ThreadSettingsUpdated(threadId, settings.text("model"),
                    settings.text("effort")?.let(ReasoningEffort::fromWire), settings.text("approvalPolicy")?.let(AskForApproval::fromWire),
                    settings.text("approvalsReviewer")?.let(ApprovalsReviewer::fromWire),
                    settings.objectOrNull("collaborationMode")?.text("mode")?.let(CollaborationMode::fromWire),
                    settings.text("serviceTier")))
            }
            "thread/tokenUsage/updated" -> {
                val usage = p.objectOrNull("tokenUsage")!!
                AppServerEvent.ThreadTokenUsageEvent(threadId, ThreadTokenUsageUpdated(threadId, p.text("turnId"),
                    ThreadTokenUsage(usage.objectOrNull("total")?.let(::breakdown) ?: TokenUsageBreakdown.Empty,
                        usage.objectOrNull("last")?.let(::breakdown) ?: TokenUsageBreakdown.Empty, usage.long("modelContextWindow"))))
            }
            "serverRequest/resolved" -> {
                val id = p["requestId"]?.wireText().orEmpty()
                approvals.remove(id)
                AppServerEvent.RequestResolved(threadId, ServerRequestResolved(id, threadId))
            }
            "account/updated" -> { scope.launch { readAccount().onSuccess { eventQueue.send(AppServerEvent.AccountUpdated(it)) } }; null }
            "account/login/completed" -> AppServerEvent.AccountLoginCompleted(AccountLoginCompletedNotification(p.bool("success") == true, p.text("loginId"), p.text("error")))
            "account/rateLimits/updated" -> AppServerEvent.RateLimitsUpdatedEvent(WireCodec.rateLimitSnapshot(p.objectOrNull("rateLimits")!!))
            "skills/changed" -> AppServerEvent.SkillsChanged(CatalogChanged())
            "app/list/updated" -> AppServerEvent.AppListUpdated(CatalogChanged())
            "model/rerouted" -> AppServerEvent.ModelReroutedEvent(threadId, ModelRerouted(threadId, p.required("fromModel"), p.required("toModel"), p.required("reason")))
            "mcpServer/startupStatus/updated" -> AppServerEvent.McpStartupStatusEvent(McpStartupStatusUpdated(p.required("name"),
                McpServerStartupState.fromWire(p.text("status")), p.text("error"), p.text("failureReason")))
            "mcpServer/event/stream/notification" -> AppServerEvent.McpServerEvent(McpServerEventStreamNotification(
                subscriptionId = p.required("subscriptionId"),
                notification = p.objectOrNull("notification")?.let { n -> McpServerEventNotification(n.required("method"), n["params"] ?: JsonNull) }
                    ?: McpServerEventNotification("unknown"),
            ))
            "model/safetyBuffering/updated" -> AppServerEvent.ModelSafetyBufferingUpdated(ModelSafetyBufferingUpdatedNotification(
                threadId = threadId, turnId = turnId, model = p.text("model").orEmpty(), reasons = p.strings("reasons"),
                useCases = p.strings("useCases"), showBufferingUi = p.bool("showBufferingUi") == true, fasterModel = p.text("fasterModel"),
            ))
            "thread/realtime/item/transcript/delta" -> AppServerEvent.RealtimeItemTranscriptDelta(threadId, p.required("itemId"), p.required("delta"))
            "mcpServer/oauthLogin/completed" -> AppServerEvent.McpOauthLoginCompleted(McpServerOauthLoginCompletedNotification(p.required("name"), p.bool("success") == true, p.text("error")))
            "fs/changed" -> AppServerEvent.FsChangedEvent(FsChangedNotification(p.required("watchId"), p.strings("changedPaths")))
            "thread/attachment/updated" -> AppServerEvent.ThreadAttachmentUpdated(threadId)
            "command/exec/outputDelta" -> AppServerEvent.CommandExecOutput(CommandExecOutputDeltaNotification(p.required("processId"), p.required("deltaBase64"), CommandExecStream.fromWire(p.text("stream")), p.bool("capReached") == true))
            "process/outputDelta" -> AppServerEvent.ProcessOutputDelta(ProcessOutputDeltaNotification(p.required("processHandle"),
                ProcessOutputStream.fromWire(p.text("stream")), p.required("deltaBase64"), p.bool("capReached") == true))
            "process/exited" -> AppServerEvent.ProcessExited(ProcessExitedNotification(p.required("processHandle"), p.int("exitCode") ?: 0,
                p.text("stdout").orEmpty(), p.bool("stdoutCapReached") == true, p.text("stderr").orEmpty(), p.bool("stderrCapReached") == true))
            "hook/started" -> AppServerEvent.HookStarted(threadId, HookStartedNotification(threadId, p.text("turnId"), WireCodec.hookRun(p["run"]!!.objectValue())))
            "hook/completed" -> AppServerEvent.HookCompleted(threadId, HookCompletedNotification(threadId, p.text("turnId"), WireCodec.hookRun(p["run"]!!.objectValue())))
            "fuzzyFileSearch/sessionUpdated" -> AppServerEvent.FuzzySearchUpdated(FuzzyFileSearchSessionUpdatedNotification(p.required("sessionId"),
                p.text("query").orEmpty(), p.array("files").map { WireCodec.fuzzyResult(it.objectValue()) }))
            "fuzzyFileSearch/sessionCompleted" -> AppServerEvent.FuzzySearchCompleted(FuzzyFileSearchSessionCompletedNotification(p.required("sessionId")))
            "item/autoApprovalReview/started", "item/autoApprovalReview/completed" -> {
                val review = p.objectOrNull("review") ?: obj()
                val status = review.text("status").orEmpty()
                val notification = GuardianApprovalReviewNotification(
                    threadId = threadId,
                    turnId = p.text("turnId").orEmpty(),
                    reviewId = p.required("reviewId"),
                    status = status,
                    itemId = p.text("targetItemId").orEmpty(),
                    rationale = review.text("rationale"),
                    riskLevel = review.text("riskLevel"),
                    action = p["action"],
                )
                if (method == "item/autoApprovalReview/completed") {
                    // The approve call needs the core-shaped event, not the notification; synthesize
                    // it here so the item's denial can be overridden later.
                    p.text("targetItemId")?.let { target ->
                        guardianDenials[target] = obj(
                            "id" to p.required("reviewId"), "target_item_id" to target, "turn_id" to p.text("turnId").orEmpty(),
                            "started_at_ms" to (p.long("startedAtMs") ?: 0L), "completed_at_ms" to (p.long("completedAtMs") ?: 0L),
                            "status" to guardianStatus(status), "risk_level" to review.text("riskLevel"),
                            "user_authorization" to review.text("userAuthorization"), "rationale" to review.text("rationale"),
                            "decision_source" to "agent", "action" to (p["action"] ?: JsonNull),
                        )
                    }
                    AppServerEvent.AutoApprovalReviewCompleted(threadId, notification)
                } else {
                    AppServerEvent.AutoApprovalReviewStarted(threadId, notification)
                }
            }
            "externalAgentConfig/import/progress" -> AppServerEvent.ExternalAgentImportProgress(p.required("importId"),
                p.array("itemTypeResults").map { WireCodec.externalAgentImportTypeResult(it.objectValue()) })
            "externalAgentConfig/import/completed" -> AppServerEvent.ExternalAgentImportCompleted(p.required("importId"),
                p.array("itemTypeResults").map { WireCodec.externalAgentImportTypeResult(it.objectValue()) })
            "android/transportError" -> throw IOException(p.required("message"))
            "android/transportLagged" -> AppServerEvent.TransportLagged(p.long("skipped") ?: 0L)
            "error" -> {
                val e = p.objectOrNull("error")!!
                AppServerEvent.ErrorEvent(threadId, ErrorNotification(TurnError(e.required("message"), e.text("additionalDetails"), e["codexErrorInfo"]?.wireText()), threadId, turnId, p.bool("willRetry") == true))
            }
            "warning" -> AppServerEvent.WarningEvent(threadId, WarningNotification(threadId, p.required("message")))
            "configWarning" -> AppServerEvent.ConfigWarningEvent(ConfigWarningNotification(p.required("summary"), p.text("details"), p.text("path")))
            "deprecationNotice" -> AppServerEvent.DeprecationNoticeEvent(DeprecationNoticeNotification(p.required("summary"), p.text("details")))
            else -> null
        }
        if (event != null) eventQueue.send(event)
    }

    /** One element of a request's `commandActions`; shared with the `commandExecution` item. */
    private fun commandAction(value: JsonElement): CommandAction? = WireCodec.commandAction(value)

    private fun networkPolicyAmendment(value: JsonElement): NetworkPolicyAmendment? {
        val o = value.objectValue()
        val action = NetworkPolicyRuleAction.entries.find { it.wire == o.text("action") } ?: return null
        return NetworkPolicyAmendment(action, o.required("host"))
    }

    /**
     * One `availableDecisions` element.
     *
     * The wire union mixes bare strings with single-key objects, so the string form is probed
     * first; an element that is neither a known decision string nor a known payload key is dropped
     * rather than guessed at.
     */
    private fun approvalDecision(value: JsonElement): CommandExecutionApprovalDecision? {
        value.stringOrNull()?.let { wire ->
            return when (wire) {
                "accept" -> CommandExecutionApprovalDecision.Accept
                "acceptForSession" -> CommandExecutionApprovalDecision.AcceptForSession
                "decline" -> CommandExecutionApprovalDecision.Decline
                "cancel" -> CommandExecutionApprovalDecision.Cancel
                else -> null
            }
        }
        val o = value as? JsonObject ?: return null
        o.objectOrNull("acceptWithExecpolicyAmendment")?.let { amendment ->
            return CommandExecutionApprovalDecision.AcceptWithExecpolicyAmendment(amendment.strings("execpolicy_amendment"))
        }
        o.objectOrNull("applyNetworkPolicyAmendment")?.let { amendment ->
            return amendment.objectOrNull("network_policy_amendment")?.let(::networkPolicyAmendment)
                ?.let(CommandExecutionApprovalDecision::ApplyNetworkPolicyAmendment)
        }
        return null
    }

    /** Encode a command-execution decision back into the upstream union. */
    private fun decisionBody(decision: CommandExecutionApprovalDecision): JsonElement = when (decision) {
        CommandExecutionApprovalDecision.Accept -> JsonPrimitive("accept")
        CommandExecutionApprovalDecision.AcceptForSession -> JsonPrimitive("acceptForSession")
        CommandExecutionApprovalDecision.Decline -> JsonPrimitive("decline")
        CommandExecutionApprovalDecision.Cancel -> JsonPrimitive("cancel")
        is CommandExecutionApprovalDecision.AcceptWithExecpolicyAmendment ->
            obj("acceptWithExecpolicyAmendment" to obj("execpolicy_amendment" to decision.execpolicyAmendment))
        is CommandExecutionApprovalDecision.ApplyNetworkPolicyAmendment ->
            obj("applyNetworkPolicyAmendment" to obj("network_policy_amendment" to obj(
                "action" to decision.networkPolicyAmendment.action.wire, "host" to decision.networkPolicyAmendment.host)))
    }

    private fun breakdown(o: JsonObject) = TokenUsageBreakdown(
        totalTokens = o.long("totalTokens") ?: 0L, inputTokens = o.long("inputTokens") ?: 0L,
        cachedInputTokens = o.long("cachedInputTokens") ?: 0L, outputTokens = o.long("outputTokens") ?: 0L,
        reasoningOutputTokens = o.long("reasoningOutputTokens") ?: 0L, cacheWriteInputTokens = o.long("cacheWriteInputTokens") ?: 0L,
    )

    /** The v2 review status is camelCase; the core event the approve call takes is snake_case. */
    private fun guardianStatus(v2: String): String = when (v2) {
        "inProgress" -> "in_progress"
        "timedOut" -> "timed_out"
        else -> v2
    }

    private suspend fun serverRequest(id: JsonElement, method: String, p: JsonObject) {
        if (method == "currentTime/read") {
            transport.send(JsonRpcMessageKind.Response, Json.write(obj("id" to id, "result" to obj("currentTimeAt" to System.currentTimeMillis() / 1000))))
            return
        }
        val key = id.wireText()
        val requestId = RequestId(key)
        val thread = p.text("threadId").orEmpty()
        val turn = p.text("turnId").orEmpty()
        val item = p.text("itemId").orEmpty()
        val time = p.long("startedAtMs") ?: System.currentTimeMillis()
        val request = when (method) {
            "item/commandExecution/requestApproval" -> ApprovalRequest.Exec(requestId, thread, turn, item, time,
                CommandExecutionApprovalParams(thread, turn, item, time, p.text("approvalId"), p.text("environmentId"), p.text("reason"), p.text("command"), p.text("cwd"),
                    commandActions = p.array("commandActions").mapNotNull { commandAction(it) },
                    proposedExecpolicyAmendment = p.strings("proposedExecpolicyAmendment").takeIf { it.isNotEmpty() },
                    proposedNetworkPolicyAmendments = p.array("proposedNetworkPolicyAmendments").mapNotNull { networkPolicyAmendment(it) },
                    availableDecisions = p.array("availableDecisions").mapNotNull { approvalDecision(it) }))
            "item/fileChange/requestApproval" -> ApprovalRequest.ApplyPatch(requestId, thread, turn, item, time,
                FileChangeApprovalParams(thread, turn, item, time, p.text("reason"), p.text("grantRoot")))
            "item/permissions/requestApproval" -> {
                val permissions = p.objectOrNull("permissions") ?: obj()
                val fs = permissions.objectOrNull("fileSystem")
                val entries = fs?.array("entries").orEmpty().map { it.objectValue() }
                fun paths(access: String) = entries.filter { it.text("access") == access }.map { entry ->
                    val path = entry.objectOrNull("path") ?: obj()
                    path.text("path") ?: path.text("pattern") ?: Json.write(path)
                }
                ApprovalRequest.Permissions(requestId, thread, turn, item, time, PermissionsApprovalParams(thread, turn, item, p.text("environmentId"), time, p.text("cwd").orEmpty(), p.text("reason"),
                    RequestPermissionProfile(permissions.objectOrNull("network")?.bool("enabled") == true,
                        (fs?.strings("read").orEmpty() + paths("read")).distinct(), (fs?.strings("write").orEmpty() + paths("write")).distinct())))
            }
            // `item/tool/call`: a tool this client declared at `thread/start` is being invoked.
            "item/tool/call" -> ApprovalRequest.DynamicTool(
                requestId, thread, turn, item, time,
                DynamicToolCallParams(
                    threadId = thread,
                    turnId = turn,
                    callId = p.text("callId").orEmpty(),
                    namespace = p.text("namespace"),
                    tool = p.required("tool"),
                    arguments = p["arguments"]?.let(Json::write) ?: "{}",
                ),
            )
            "item/tool/requestUserInput" -> {
                val questions = p.array("questions").map { value ->
                    val q = value.objectValue()
                    val options = (q["options"] as? JsonArray)?.map { option ->
                        val o = option.objectValue()
                        ToolRequestUserInputOption(o.required("label"), o.text("description").orEmpty())
                    }
                    ToolRequestUserInputQuestion(q.required("id"), q.required("header"), q.required("question"), q.bool("isOther") == true, q.bool("isSecret") == true, options)
                }
                ApprovalRequest.UserInput(requestId, thread, turn, item, time, ToolRequestUserInputParams(thread, turn, item, questions, p.bool("isBlocking") != false))
            }
            "mcpServer/elicitation/request" -> {
                val schema = p.objectOrNull("requestedSchema") ?: obj()
                val fields = schema.objectOrNull("properties").orEmpty().map { (name, value) ->
                    val field = value.objectValue()
                    val options = field.strings("enum")
                    val kind = when {
                        options.isNotEmpty() -> McpElicitationFieldKind.Enum
                        field.text("type") == "boolean" -> McpElicitationFieldKind.Boolean
                        field.text("type") in listOf("number", "integer") -> McpElicitationFieldKind.Number
                        else -> McpElicitationFieldKind.Text
                    }
                    McpElicitationField(name, field.text("title") ?: name, field.text("description").orEmpty(), kind,
                        name in schema.strings("required"), options, field["default"]?.wireText().orEmpty())
                }
                val serverName = p.required("serverName")
                val message = p.text("message").orEmpty()
                // The union flattens `_meta` next to `mode`/`message`; it carries the
                // `_codex_apps.connector_auth_failure` payload the app-link flow reads.
                val meta = p["_meta"]
                val payload = if (p.text("mode") == "url") {
                    McpElicitationRequest.Url(serverName, message, p.required("url"), p.required("elicitationId"), meta)
                } else {
                    McpElicitationRequest.Form(serverName, message, McpElicitationSchema(schema.text("title").orEmpty(), fields), meta)
                }
                ApprovalRequest.Elicitation(requestId, thread, p.text("turnId"), item, time, payload)
            }
            else -> null
        }
        if (request == null) {
            transport.send(JsonRpcMessageKind.Error, Json.write(obj("id" to id, "error" to obj("code" to -32601, "message" to "Unsupported Android client request: $method"))))
        } else {
            approvals[key] = id to p
            requestStream.send(request)
        }
    }

    override suspend fun respond(requestId: RequestId, response: ApprovalResponse) {
        val (id, params) = approvals[requestId.value] ?: error("Approval is no longer pending")
        val body = when (response) {
            is ApprovalResponse.CommandExecution -> obj("decision" to decisionBody(response.decision))
            is ApprovalResponse.FileChange -> obj("decision" to response.decision.wire)
            is ApprovalResponse.Permissions -> obj("permissions" to if (response.decision == PermissionsApprovalDecision.Decline) obj() else (params["permissions"] ?: obj()),
                "scope" to if (response.decision == PermissionsApprovalDecision.AcceptForSession) "session" else "turn")
            is ApprovalResponse.UserInput -> obj("answers" to JsonObject(response.answers.associate { it.questionId to obj("answers" to it.answers) }))
            is ApprovalResponse.Elicitation -> {
                val properties = params.objectOrNull("requestedSchema")?.objectOrNull("properties")
                val content = response.content.mapValues { (key, value) ->
                    when (properties?.objectOrNull(key)?.text("type")) {
                        "boolean" -> json(value.toBooleanStrict())
                        "integer" -> json(value.toLong())
                        "number" -> json(value.toDouble().also { require(it.isFinite()) })
                        else -> json(value)
                    }
                }
                // A URL-mode accept carries no content: the accept *is* the answer. A form accept
                // with no fields is the same shape, so an empty map is sent as null, not `{}`.
                obj("action" to response.action.wire,
                    "content" to if (response.action == ElicitationAction.Accept && content.isNotEmpty()) JsonObject(content) else JsonNull,
                    "_meta" to response.meta)
            }
            is ApprovalResponse.DynamicTool -> obj("success" to response.result.success, "contentItems" to response.result.contentItems.map { obj("type" to "inputText", "text" to it) })
            is ApprovalResponse.Tokens -> obj("accessToken" to response.accessToken, "chatgptAccountId" to response.chatgptAccountId, "chatgptPlanType" to response.chatgptPlanType)
            is ApprovalResponse.Attestation -> obj("token" to response.token)
            is ApprovalResponse.CurrentTime -> obj("currentTimeAt" to response.epochMillis / 1000)
        }
        transport.send(JsonRpcMessageKind.Response, Json.write(obj("id" to id, "result" to body)))
        approvals.remove(requestId.value)
    }

    companion object {
        /** Watchdog tick wired by `CodexApplication`: a pending request is probed after one quiet tick. */
        const val WatchdogIntervalMs = 30_000L

        /** How long the canary request may take before the worker counts as wedged. */
        private const val WatchdogProbeTimeoutMs = 30_000L

        /** Ordinary request deadline; compaction and turn setup return long before it. */
        private const val RequestTimeoutMs = 120_000L
    }
}
