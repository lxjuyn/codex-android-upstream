package com.cy.codex.protocol

import com.cy.codex.protocol.protocol.Json
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
import com.cy.codex.protocol.protocol.item.*
import com.cy.codex.protocol.protocol.v2.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

internal fun obj(vararg fields: Pair<String, Any?>): JsonObject =
    JsonObject(fields.filter { it.second != null }.associate { it.first to json(it.second) })

internal fun json(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    // Integral doubles become integer literals so `obj("limit" to 100.0)` does not put `100.0`
    // into an integer field; non-integral values keep their decimal form.
    is Double -> if (value.isFinite() && value == value.toLong().toDouble()) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
    is Float -> json(value.toDouble())
    is Number -> JsonPrimitive(value.toDouble())
    is List<*> -> JsonArray(value.map(::json))
    is Map<*, *> -> JsonObject(value.entries.associate { (key, entry) -> key.toString() to json(entry) })
    else -> error("Unsupported JSON value: ${value.javaClass.name}")
}

internal object WireCodec {
    fun input(value: UserInput): JsonElement = when (value) {
        is UserInput.Text -> obj(
            "type" to "text",
            "text" to value.text,
            // The wire field stays snake_case: the v2 enum's variant fields were not renamed.
            "text_elements" to value.textElements.map(::textElement),
        )
        // The image arm is a flattened `{url} | {fileId}` union, so only the present reference is
        // sent; `obj` drops null fields for exactly this reason.
        is UserInput.Image -> obj("type" to "image", "url" to value.url, "fileId" to value.fileId, "detail" to value.detail)
        is UserInput.LocalImage -> obj("type" to "localImage", "path" to value.path, "detail" to value.detail)
        is UserInput.Audio -> obj("type" to "audio", "url" to value.url)
        is UserInput.LocalAudio -> obj("type" to "localAudio", "path" to value.path)
        is UserInput.Skill -> obj("type" to "skill", "name" to value.name, "path" to value.path)
        is UserInput.Mention -> obj("type" to "mention", "name" to value.name, "path" to value.path)
    }

    fun textElement(value: TextElement): JsonElement = obj(
        "byteRange" to obj("start" to value.byteRange.start, "end" to value.byteRange.end),
        "placeholder" to value.placeholder,
    )

    fun textElement(value: JsonElement): TextElement {
        val o = value.objectValue()
        val range = o.objectOrNull("byteRange")
        return TextElement(
            ByteRange(range?.int("start") ?: 0, range?.int("end") ?: 0),
            o.text("placeholder"),
        )
    }

    fun input(value: JsonElement): UserInput {
        val o = value.objectValue()
        return when (o.required("type")) {
            "text" -> UserInput.Text(o.required("text"), o.array("text_elements").map(::textElement))
            // `url` and `fileId` are a union, so neither may be required: an uploaded image has
            // no url and requiring one would abort the whole message.
            "image" -> UserInput.Image(o.text("url"), o.text("fileId"), o.text("detail"))
            "localImage" -> UserInput.LocalImage(o.required("path"), o.text("detail"))
            "audio" -> UserInput.Audio(o.required("url"))
            "localAudio" -> UserInput.LocalAudio(o.required("path"))
            "skill" -> UserInput.Skill(o.required("name"), o.required("path"))
            "mention" -> UserInput.Mention(o.required("name"), o.required("path"))
            else -> error("Unsupported user input type: ${o.text("type")}")
        }
    }

    fun status(value: JsonElement?): ThreadStatus {
        val o = value as? JsonObject
        return when (o?.text("type") ?: value?.stringOrNull()) {
            "active" -> ThreadStatus.Active(o?.strings("activeFlags").orEmpty().mapNotNull { flag -> ThreadActiveFlag.entries.find { it.wire == flag } })
            "notLoaded" -> ThreadStatus.NotLoaded
            "systemError" -> ThreadStatus.SystemError(o?.text("message").orEmpty())
            else -> ThreadStatus.Idle
        }
    }

    fun thread(value: JsonElement): Thread {
        val o = value.objectValue()
        return Thread(
            id = o.required("id"), preview = o.text("preview").orEmpty(), modelProvider = o.text("modelProvider").orEmpty(),
            createdAt = (o.long("createdAt") ?: 0) * 1000, updatedAt = (o.long("updatedAt") ?: 0) * 1000,
            cwd = o.text("cwd").orEmpty(), status = status(o["status"]), cliVersion = o.text("cliVersion").orEmpty(),
            ephemeral = o.bool("ephemeral") == true, projectId = o.text("projectId"), sessionId = o.text("sessionId").orEmpty(),
            name = o.text("name"), forkedFromId = o.text("forkedFromId"),
            gitInfo = o.objectOrNull("gitInfo")?.let { GitInfo(it.text("branch"), it.text("originUrl"), it.text("sha")) },
            model = o.text("model"), reasoningEffort = o.text("reasoningEffort")?.let(ReasoningEffort::fromWire),
            path = o.text("path"), historyMode = o.text("historyMode") ?: "legacy",
            recencyAt = o.long("recencyAt")?.times(1000), originator = o.text("originator"),
            parentThreadId = o.text("parentThreadId"), canAcceptDirectInput = o.bool("canAcceptDirectInput"),
            agentNickname = o.text("agentNickname"), agentRole = o.text("agentRole"),
        )
    }

    fun turn(value: JsonElement): Turn {
        val o = value.objectValue()
        return Turn(o.required("id"), o.array("items").map { item(it) },
            TurnItemsView.entries.firstOrNull { it.wire == o.text("itemsView") } ?: TurnItemsView.Full,
            TurnStatus.fromWire(o.required("status")),
            (o.long("startedAt") ?: 0) * 1000, o.long("completedAt")?.times(1000),
            durationMs = o.long("durationMs"))
    }

    /** One `TurnsPage`: `thread/turns/list` and `thread/resume.initialTurnsPage` share the shape. */
    fun turnsPage(value: JsonElement): TurnsPage {
        val o = value.objectValue()
        return TurnsPage(o.array("data").map(::turn), o.text("nextCursor"), o.text("backwardsCursor"))
    }

    fun session(value: JsonObject): ThreadSessionState {
        val row = thread(value["thread"]!!)
        val sandbox = value.objectOrNull("sandbox")
        val mode = when (sandbox?.text("type")) {
            "dangerFullAccess", "externalSandbox" -> SandboxMode.DangerFullAccess
            "readOnly" -> SandboxMode.ReadOnly
            else -> SandboxMode.WorkspaceWrite
        }
        val model = value.required("model")
        val cwd = value.text("cwd") ?: row.cwd
        return ThreadSessionState(
            threadId = row.id, forkedFromId = row.forkedFromId, threadName = row.name,
            model = model, modelDisplayName = model, modelProviderId = value.required("modelProvider"),
            reasoningEffort = value.text("reasoningEffort")?.let(ReasoningEffort::fromWire) ?: ReasoningEffort.Medium,
            approvalPolicy = AskForApproval.fromWire(value.text("approvalPolicy").orEmpty()),
            approvalsReviewer = ApprovalsReviewer.fromWire(value.text("approvalsReviewer")),
            sandboxPolicy = SandboxPolicy(mode, sandbox?.strings("writableRoots").orEmpty(), sandbox?.bool("networkAccess") ?: (mode == SandboxMode.DangerFullAccess)),
            collaborationMode = value.objectOrNull("collaborationMode")?.text("mode")?.let(CollaborationMode::fromWire)
                ?: CollaborationMode.Default,
            serviceTier = value.text("serviceTier"),
            cwd = cwd, workspaceRoots = listOf(cwd), instructionSourcePaths = value.strings("instructionSources"),
            gitBranch = row.gitInfo?.branch, rolloutPath = row.path,
            parentThreadId = row.parentThreadId, canAcceptDirectInput = row.canAcceptDirectInput,
            turnsBackwardsCursor = value.text("turnsBackwardsCursor"),
            initialTurnsPage = value.objectOrNull("initialTurnsPage")?.let(::turnsPage),
            thread = row,
        )
    }

    fun changes(value: JsonObject): List<FileUpdateChange> = value.array("changes").map {
        val o = it.objectValue()
        FileUpdateChange(o.required("path"), PatchChangeKind.fromWire(o.objectOrNull("kind")?.text("type") ?: o.text("kind").orEmpty()), o.text("diff").orEmpty())
    }

    fun item(value: JsonElement): ThreadItem {
        val o = value.objectValue()
        val id = o.required("id")
        val status = o.text("status")
        return when (val type = o.required("type")) {
            "userMessage" -> UserMessageItem(id, o.text("clientId"), o.array("content").map { input(it) })
            "agentMessage" -> AgentMessageItem(
                id,
                o.text("text").orEmpty(),
                MessagePhase.entries.find { it.wire == o.text("phase") || (it == MessagePhase.FinalAnswer && o.text("phase") == "final_answer") },
                asyncQuestions(o),
                memoryCitation(o),
                AgentMessageDelivery.fromWire(o.text("delivery")),
            )
            "plan" -> PlanItem(id, o.text("text").orEmpty())
            "reasoning" -> ReasoningItem(id, o.strings("summary"), o.strings("content"))
            "commandExecution" -> CommandExecutionItem(id, o.required("command"), o.required("cwd"), o.text("processId"),
                CommandExecutionSource.entries.find { it.wire == o.text("source") } ?: CommandExecutionSource.Agent,
                CommandExecutionStatus.fromWire(status.orEmpty()),
                o.array("commandActions").mapNotNull(::commandAction),
                pluginId = o.text("pluginId"), scriptPath = o.text("scriptPath"),
                aggregatedOutput = o.text("aggregatedOutput"), exitCode = o.int("exitCode"), durationMs = o.long("durationMs"))
            "fileChange" -> FileChangeItem(id, changes(o), PatchApplyStatus.fromWire(status.orEmpty()))
            "mcpToolCall" -> McpToolCallItem(id, o.required("server"), o.required("tool"), McpToolCallStatus.entries.find { it.wire == status } ?: McpToolCallStatus.InProgress,
                o["arguments"]?.let(Json::write) ?: "{}", appContext = mcpAppContext(o), mcpAppUi = mcpAppUi(o),
                pluginId = o.text("pluginId"), readOnlyHint = o.bool("readOnlyHint"), mcpAppResourceUri = o.text("mcpAppResourceUri"),
                result = o["result"]?.takeUnless { it == JsonNull }?.let(Json::write), error = o.objectOrNull("error")?.text("message"), durationMs = o.long("durationMs"))
            "dynamicToolCall" -> DynamicToolCallItem(id, o.text("namespace"), o.required("tool"), o["arguments"]?.let(Json::write) ?: "{}",
                DynamicToolCallStatus.entries.find { it.wire == status } ?: DynamicToolCallStatus.InProgress, o.array("contentItems").mapNotNull(::dynamicToolContent), o.bool("success"), o.long("durationMs"))
            "collabAgentToolCall" -> CollabAgentToolCallItem(id, CollabAgentTool.entries.find { it.wire == o.text("tool") } ?: CollabAgentTool.SendInput,
                CollabAgentToolCallStatus.entries.find { it.wire == status } ?: CollabAgentToolCallStatus.InProgress,
                o.required("senderThreadId"), o.strings("receiverThreadIds"), o.text("prompt"), o.text("model"), o.text("reasoningEffort")?.let(ReasoningEffort::fromWire),
                o.objectOrNull("agentsStates").orEmpty().mapValues { (_, state) -> state.objectValue().let { CollabAgentState(AgentRunStatus.fromWire(it.required("status")), it.text("message")) } })
            "subAgentActivity" -> SubAgentActivityItem(id, SubAgentActivityKind.entries.find { it.wire == o.text("kind") } ?: SubAgentActivityKind.Started, o.required("agentThreadId"), o.required("agentPath"))
            "webSearch" -> WebSearchItem(
                id,
                o.text("query") ?: o.objectOrNull("action")?.text("query").orEmpty(),
                results = o.array("results").mapNotNull(::webSearchResult),
                action = o.objectOrNull("action")?.let(::webSearchAction),
            )
            "imageView" -> ImageViewItem(id, o.required("path"))
            "sleep" -> SleepItem(id, o.long("durationMs") ?: 0)
            "imageGeneration" -> ImageGenerationItem(id, status.orEmpty(), o.text("revisedPrompt"),
                o.text("result").orEmpty(), o.bool("transparentBackground"), imageGenerationFailure(o), o.text("savedPath"))
            "enteredReviewMode" -> EnteredReviewModeItem(id, o.required("review"))
            "exitedReviewMode" -> ExitedReviewModeItem(id, o.required("review"))
            "contextCompaction" -> ContextCompactionItem(id)
            "hookPrompt" -> HookPromptItem(id, o.array("fragments").map { it.objectValue().let { f -> HookPromptFragment(f.required("text"), f.text("hookRunId").orEmpty()) } })
            "functionCallOutput" -> FunctionCallOutputItem(id, o.required("name"), o.text("namespace"), o["output"]?.wireText().orEmpty())
            else -> FunctionCallOutputItem(id, type, output = Json.write(o))
        }
    }

    /**
     * One `DynamicToolCallOutputContentItem` block.
     *
     * Unknown or malformed blocks are dropped rather than rendered: unlike an MCP result there is no
     * exact-JSON fallback upstream (`dynamic_tools.rs`), and a missing URL would be unusable.
     */
    fun dynamicToolContent(value: JsonElement): DynamicToolOutputContent? {
        val block = value as? JsonObject ?: return null
        return when (block.text("type")) {
            "inputText" -> block.text("text")?.let(DynamicToolOutputContent::InputText)
            "inputImage" -> block.text("imageUrl")?.let(DynamicToolOutputContent::InputImage)
            "inputAudio" -> block.text("audioUrl")?.let(DynamicToolOutputContent::InputAudio)
            else -> null
        }
    }

    /**
     * Inline questions an `agentMessage` carries, or `null` when the field is absent.
     *
     * `options` is nullable upstream, so an explicit JSON null has to stay distinct from an empty
     * list: the first means "free text only", the second a choice with nothing to choose.
     */
    private fun asyncQuestions(o: JsonObject): List<AsyncUserInputQuestion>? {
        val raw = o["questions"] ?: return null
        if (raw is JsonNull) return null
        return o.array("questions").map { element ->
            element.objectValue().let { q ->
                val options = q["options"]?.takeUnless { it is JsonNull }?.let { q.strings("options") }
                AsyncUserInputQuestion(q.required("title"), options)
            }
        }
    }

    fun account(o: JsonObject): AccountReadResponse {
        // `requiresOpenaiAuth` is required upstream; a malformed response is the only way to miss
        // it, and treating that as "auth required" is the safe default.
        val requires = o.bool("requiresOpenaiAuth") ?: true
        val a = o.objectOrNull("account") ?: return AccountReadResponse(requires)
        val account = when (a.required("type")) {
            "apiKey" -> Account.ApiKey
            "chatgpt" -> Account.Chatgpt(a.text("email"), a.text("planType").orEmpty())
            "amazonBedrock" -> Account.AmazonBedrock(a.bool("usesCodexManagedCredentials") == true)
            else -> null
        }
        return AccountReadResponse(requires, account)
    }

    fun rateLimitWindow(o: JsonObject): RateLimitWindow = RateLimitWindow(
        usedPercent = o.long("usedPercent") ?: 0L,
        windowDurationMins = o.long("windowDurationMins"),
        resetsAt = o.long("resetsAt")?.times(1000),
    )

    fun rateLimitSnapshot(o: JsonObject): RateLimitSnapshot = RateLimitSnapshot(
        primary = o.objectOrNull("primary")?.let(::rateLimitWindow),
        secondary = o.objectOrNull("secondary")?.let(::rateLimitWindow),
        credits = o.objectOrNull("credits")?.let { CreditsSnapshot(it.bool("hasCredits") == true, it.bool("unlimited") == true, it.text("balance")) },
        limitId = o.text("limitId"), limitName = o.text("limitName"), planType = o.text("planType"),
        rateLimitReachedType = o.text("rateLimitReachedType"), spendControlReached = o.bool("spendControlReached"),
        normalModelSlug = o.text("normalModelSlug"),
        individualLimit = o.objectOrNull("individualLimit")?.let(::spendControlLimit),
    )

    private fun spendControlLimit(o: JsonObject) = SpendControlLimitSnapshot(
        limit = o.text("limit").orEmpty(),
        remainingPercent = o.int("remainingPercent") ?: 0,
        resetsAt = o.long("resetsAt") ?: 0L,
        used = o.text("used").orEmpty(),
    )

    fun threadUsage(o: JsonObject) = ThreadUsage(
        threadId = o.required("threadId"),
        estimatedUsageCreditsMicros = o.long("estimatedUsageCreditsMicros") ?: 0L,
        estimatedUsageUsdMicros = o.long("estimatedUsageUsdMicros"),
        groups = o.array("groups").map { value -> value.objectValue().let { group ->
            ThreadUsageGroup(
                model = group.text("model"),
                reasoningEffort = group.text("reasoningEffort"),
                speed = group.text("speed"),
                totalTokens = group.long("totalTokens"),
                inputTokens = group.long("inputTokens"),
                cachedInputTokens = group.long("cachedInputTokens"),
                netNewInputTokens = group.long("netNewInputTokens"),
                outputTokens = group.long("outputTokens"),
                estimatedUsageCreditsMicros = group.long("estimatedUsageCreditsMicros") ?: 0L,
            )
        } },
    )

    fun accountRateLimits(o: JsonObject): AccountRateLimits = AccountRateLimits(
        rateLimits = o.objectOrNull("rateLimits")?.let(::rateLimitSnapshot) ?: RateLimitSnapshot(),
        rateLimitsByLimitId = o.objectOrNull("rateLimitsByLimitId")?.mapValues { (_, value) -> rateLimitSnapshot(value.objectValue()) },
        accountId = o.text("accountId"),
        rateLimitResetCredits = o.objectOrNull("rateLimitResetCredits")?.let { summary ->
            RateLimitResetCreditsSummary(
                availableCount = summary.long("availableCount") ?: 0L,
                credits = (summary["credits"] as? JsonArray)?.map { value -> value.objectValue().let { credit ->
                    RateLimitResetCredit(credit.required("id"), credit.text("status").orEmpty(), credit.text("resetType").orEmpty(),
                        credit.long("grantedAt") ?: 0L, credit.text("title"), credit.text("description"), credit.long("expiresAt"))
                } },
            )
        },
        ordinaryUsageAllowed = o.bool("ordinaryUsageAllowed"),
    )

    fun attachment(o: JsonObject) = ThreadAttachment(
        id = o.required("id"),
        attachmentType = o.text("attachmentType").orEmpty(),
        identityKey = o.text("identityKey").orEmpty(),
        payload = o["payload"] ?: JsonNull,
        createdAt = o.long("createdAt") ?: 0L,
    )

    fun backgroundTerminal(o: JsonObject) = ThreadBackgroundTerminal(
        itemId = o.required("itemId"),
        processId = o.required("processId"),
        command = o.text("command").orEmpty(),
        cwd = o.text("cwd").orEmpty(),
        osPid = o.long("osPid"),
        cpuPercent = (o["cpuPercent"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull,
        rssKb = o.long("rssKb"),
    )

    fun timelineEntry(value: JsonElement): TimelineEntry {
        val o = value.objectValue()
        val position = o.long("position") ?: 0L
        val turnId = o.text("turnId").orEmpty()
        return when (o.required("type")) {
            "item" -> TimelineEntry.Item(position, turnId, item(o["item"]!!))
            "realtime" -> TimelineEntry.Realtime(position, realtimeItem(o.objectOrNull("item")!!))
            "turnStarted" -> TimelineEntry.TurnStarted(position, turnId, o.long("startedAt")?.times(1000))
            "turnCompleted" -> TimelineEntry.TurnCompleted(position, turnId, TurnStatus.fromWire(o.required("status")),
                o.objectOrNull("error")?.text("message"), o.long("startedAt")?.times(1000),
                o.long("completedAt")?.times(1000), o.long("durationMs"))
            else -> error("Unknown timeline entry type: ${o.text("type")}")
        }
    }

    fun realtimeItem(o: JsonObject) = ThreadRealtimeItem(o.required("id"), o.text("realtimeSessionId").orEmpty(),
        o.text("type").orEmpty(), o.text("role"), o.text("text"), o.text("turnId"), o.text("itemId"), o.text("outcome"))

    /** One `WebSearchAction`; unknown tags fall back to [WebSearchAction.Other]. */
    private fun webSearchAction(o: JsonObject): WebSearchAction = when (o.text("type")) {
        "openPage" -> WebSearchAction.OpenPage(o.text("url"))
        "findInPage" -> WebSearchAction.FindInPage(o.text("url"), o.text("pattern"))
        "search" -> WebSearchAction.Search(
            query = o.text("query"),
            queries = o.strings("queries").takeIf { it.isNotEmpty() },
        )

        else -> WebSearchAction.Other
    }

    /**
     * One element of `webSearch.results`.
     *
     * The wire type is opaque JSON (`ext/items/src/web_search.rs`), so anything without a title or
     * a url is dropped instead of rendered as a blank row — a result the transcript cannot name is
     * not worth a line.
     */
    private fun webSearchResult(value: JsonElement): WebSearchResult? {
        val o = value as? JsonObject ?: return null
        val url = o.text("url").orEmpty()
        val title = o.text("title").orEmpty()
        if (url.isBlank() && title.isBlank()) return null
        return WebSearchResult(title = title, url = url, snippet = o.text("snippet"), type = o.text("type"))
    }

    /**
     * One `ImageGenerationFailure`; unknown tags are dropped rather than shown as a generic failure.
     *
     * The union has a single variant today, so an unrecognised tag means the server knows a failure
     * mode this build cannot describe.
     */
    private fun imageGenerationFailure(o: JsonObject): ImageGenerationFailure? {
        val failure = o.objectOrNull("failure") ?: return null
        return when (failure.text("type")) {
            "usageLimitExceeded" ->
                ImageGenerationFailure.UsageLimitExceeded(failure.required("limitId"), failure.long("resetsAt"))

            else -> null
        }
    }

    /**
     * Memory the agent message cited.
     *
     * `entries` is the only place the citation's file paths live; upstream keeps `threadIds` on the
     * wire too (`MemoryCitation` in `codex-rs/protocol/src/memory_citation.rs`).
     */
    private fun memoryCitation(o: JsonObject): MemoryCitation? {
        val citation = o.objectOrNull("memoryCitation") ?: return null
        val entries = citation.array("entries").mapNotNull { value ->
            val entry = value as? JsonObject ?: return@mapNotNull null
            val path = entry.text("path").orEmpty()
            if (path.isBlank()) null else MemoryCitationEntry(
                path = path,
                lineStart = entry.int("lineStart"),
                lineEnd = entry.int("lineEnd"),
                note = entry.text("note"),
            )
        }
        return MemoryCitation(entries, citation.strings("threadIds"))
    }

    /** `mcpToolCall.appContext`; `connectorId` is the field that decides the object exists. */
    private fun mcpAppContext(o: JsonObject): McpToolCallAppContext? {
        val context = o.objectOrNull("appContext") ?: return null
        val connectorId = context.text("connectorId") ?: return null
        return McpToolCallAppContext(
            connectorId = connectorId,
            linkId = context.text("linkId"),
            resourceUri = context.text("resourceUri"),
            appName = context.text("appName"),
            actionName = context.text("actionName"),
        )
    }

    /** `mcpToolCall.mcpAppUi`: presentation captured from the invoked descriptor. */
    private fun mcpAppUi(o: JsonObject): McpAppUi? {
        val ui = o.objectOrNull("mcpAppUi") ?: return null
        return McpAppUi(ui.text("resourceUri"), ui.text("preferredModelDisplayMode"))
    }

    /**
     * One `CommandAction` element, shared by `commandExecution.commandActions` and the approval
     * requests that describe the same parsed command; an unknown future tag is dropped, not faked.
     */
    fun commandAction(value: JsonElement): CommandAction? {
        val o = value as? JsonObject ?: return null
        val command = o.text("command") ?: return null
        return when (o.text("type")) {
            "read" -> CommandAction.Read(command, o.required("name"), o.required("path"))
            "listFiles" -> CommandAction.ListFiles(command, o.text("path"))
            "search" -> CommandAction.Search(command, o.text("query"), o.text("path"))
            "unknown" -> CommandAction.Unknown(command)
            else -> null
        }
    }

    fun hookMetadata(o: JsonObject) = HookMetadata(
        key = o.required("key"), eventName = o.required("eventName"), handlerType = o.text("handlerType").orEmpty(),
        command = o.text("command"), async = o.bool("async") == true, server = o.text("server"), tool = o.text("tool"),
        matcher = o.text("matcher"), timeoutSec = o.long("timeoutSec") ?: 0L, statusMessage = o.text("statusMessage"),
        additionalContextLimit = o.int("additionalContextLimit"), sourcePath = o.text("sourcePath").orEmpty(),
        source = o.text("source").orEmpty(), pluginId = o.text("pluginId"), displayOrder = o.long("displayOrder") ?: 0L,
        enabled = o.bool("enabled") != false, isManaged = o.bool("isManaged") == true,
        currentHash = o.text("currentHash").orEmpty(), trustStatus = o.text("trustStatus").orEmpty(),
    )

    fun hookRun(o: JsonObject) = HookRunSummary(
        id = o.required("id"), eventName = o.text("eventName").orEmpty(), handlerType = o.text("handlerType").orEmpty(),
        executionMode = o.text("executionMode").orEmpty(), scope = o.text("scope").orEmpty(), sourcePath = o.text("sourcePath").orEmpty(),
        source = o.text("source").orEmpty(), displayOrder = o.long("displayOrder") ?: 0L, status = o.text("status").orEmpty(),
        statusMessage = o.text("statusMessage"), startedAt = o.long("startedAt") ?: 0L, completedAt = o.long("completedAt"),
        durationMs = o.long("durationMs"), entries = o.array("entries").map { value -> value.objectValue().let {
            HookOutputEntry(it.text("kind").orEmpty(), it.text("text").orEmpty()) } },
    )

    fun diagnostics(o: JsonObject) = ServerDiagnosticsResponse(
        process = o.objectOrNull("process")?.let { p -> ServerDiagnosticsProcess(p.long("id") ?: 0L,
            p.long("residentMemoryBytes"), p.long("physicalFootprintBytes")) } ?: ServerDiagnosticsProcess(),
        gauges = o.array("gauges").map { value -> value.objectValue().let { ServerDiagnosticsGauge(it.required("name"), it.long("value") ?: 0L) } },
    )

    private fun migrationItem(o: JsonObject) = ExternalAgentConfigMigrationItem(
        itemType = o.required("itemType"), description = o.required("description"), cwd = o.text("cwd"),
        details = o.objectOrNull("details")?.let { d ->
            MigrationDetails(
                plugins = d.array("plugins").map { value -> value.objectValue().let {
                    PluginsMigration(it.text("marketplaceName").orEmpty(), it.strings("pluginNames")) } },
                skills = d.array("skills").map { named(it) }, sessions = d.array("sessions").map { value -> value.objectValue().let {
                    SessionMigration(it.text("path").orEmpty(), it.text("cwd").orEmpty(), it.text("title")) } },
                mcpServers = d.array("mcpServers").map { named(it) }, hooks = d.array("hooks").map { named(it) },
                subagents = d.array("subagents").map { named(it) }, commands = d.array("commands").map { named(it) },
                memory = d.strings("memory"),
            )
        },
    )

    private fun named(value: JsonElement) = NamedMigration(value.objectValue().text("name").orEmpty())

    fun externalAgentConfigItem(value: JsonElement) = migrationItem(value.objectValue())

    fun externalAgentImportSuccess(o: JsonObject) = ExternalAgentConfigImportSuccess(o.text("itemType").orEmpty(),
        o.text("cwd"), o.text("source"), o.text("target"), o.text("title"))

    fun externalAgentImportFailure(o: JsonObject) = ExternalAgentConfigImportFailure(o.text("itemType").orEmpty(),
        o.text("errorType"), o.text("subErrorType"), o.text("failureStage").orEmpty(), o.text("message").orEmpty(),
        o.text("cwd"), o.text("source"))

    fun externalAgentImportTypeResult(o: JsonObject) = ExternalAgentConfigImportTypeResult(
        itemType = o.text("itemType").orEmpty(),
        successes = o.array("successes").map { externalAgentImportSuccess(it.objectValue()) },
        failures = o.array("failures").map { externalAgentImportFailure(it.objectValue()) },
    )

    fun externalAgentImportHistory(o: JsonObject) = ExternalAgentConfigImportHistory(
        importId = o.required("importId"), providerId = o.text("providerId"), completedAtMs = o.long("completedAtMs") ?: 0L,
        successes = o.array("successes").map { externalAgentImportSuccess(it.objectValue()) },
        failures = o.array("failures").map { externalAgentImportFailure(it.objectValue()) },
    )

    fun fuzzyResult(o: JsonObject) = FuzzyFileSearchResult(o.required("path"), o.text("matchType") ?: "file",
        o.text("fileName").orEmpty(), o.text("root").orEmpty(), o.long("score") ?: 0L,
        o.array("indices").mapNotNull { (it as? JsonPrimitive)?.takeIf { primitive -> !primitive.isString }?.intOrNull })

    fun externalAgentConfigItemOut(item: ExternalAgentConfigMigrationItem): JsonObject = obj(
        "itemType" to item.itemType, "description" to item.description, "cwd" to item.cwd,
        "details" to item.details?.let { details -> obj(
            "plugins" to details.plugins.map { obj("marketplaceName" to it.marketplaceName, "pluginNames" to it.pluginNames) }.takeIf { it.isNotEmpty() },
            "skills" to details.skills.map { obj("name" to it.name) }.takeIf { it.isNotEmpty() },
            "sessions" to details.sessions.map { obj("path" to it.path, "cwd" to it.cwd, "title" to it.title) }.takeIf { it.isNotEmpty() },
            "mcpServers" to details.mcpServers.map { obj("name" to it.name) }.takeIf { it.isNotEmpty() },
            "hooks" to details.hooks.map { obj("name" to it.name) }.takeIf { it.isNotEmpty() },
            "subagents" to details.subagents.map { obj("name" to it.name) }.takeIf { it.isNotEmpty() },
            "commands" to details.commands.map { obj("name" to it.name) }.takeIf { it.isNotEmpty() },
            "memory" to details.memory.takeIf { it.isNotEmpty() },
        ) },
    )

    fun externalAgentImportTypeResultOut(result: ExternalAgentConfigImportTypeResult): JsonObject = obj(
        "itemType" to result.itemType,
        "successes" to result.successes.map { obj("itemType" to it.itemType, "cwd" to it.cwd, "source" to it.source, "target" to it.target, "title" to it.title) },
        "failures" to result.failures.map { obj("itemType" to it.itemType, "errorType" to it.errorType, "subErrorType" to it.subErrorType,
            "failureStage" to it.failureStage, "message" to it.message, "cwd" to it.cwd, "source" to it.source) },
    )

    fun pluginShare(o: JsonObject) = PluginShareEntry(
        plugin = WireCatalogCodec.plugin(o.objectOrNull("plugin")!!, ""),
        localPluginPath = o.text("localPluginPath"),
    )

    fun pluginSharePrincipals(o: JsonObject) = PluginSharePrincipal(o.text("principalType").orEmpty(),
        o.text("principalId").orEmpty(), o.text("role").orEmpty(), o.text("name").orEmpty())

    fun config(o: JsonObject): ConfigReadResponse = ConfigReadResponse(
        config = o["config"] ?: error("Missing config"),
        layers = (o["layers"] as? JsonArray)?.map { value ->
            val layer = value.objectValue()
            val origin = layer.objectOrNull("name")
            ConfigLayer(layer["config"] ?: JsonNull, source(layer["name"]), layer.text("version").orEmpty(), layer.text("disabledReason"),
                origin?.text("file") ?: origin?.text("dotCodexFolder"))
        },
        origins = o.objectOrNull("origins").orEmpty().mapValues { (_, value) -> value.objectValue().let { ConfigLayerOrigin(source(it["name"]), it.text("version").orEmpty()) } },
    )

    private fun source(value: JsonElement?): ConfigLayerSource = ConfigLayerSource.fromWire(value?.stringOrNull() ?: (value as? JsonObject)?.text("type"))
}
