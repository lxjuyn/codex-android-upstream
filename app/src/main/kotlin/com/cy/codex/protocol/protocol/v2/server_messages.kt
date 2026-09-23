package com.cy.codex.protocol.protocol.v2

import com.cy.codex.protocol.protocol.array
import com.cy.codex.protocol.protocol.stringOrNull
import com.cy.codex.protocol.protocol.text
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * `ServerNotification` — the 82 methods the server pushes at the client.
 *
 * Mirrors the generated `ServerNotification.json`, and [entries] is asserted equal to it by
 * `UpstreamSchemaTest`. Notifications that only matter to desktop surfaces (realtime voice,
 * Windows sandbox, world-writable warnings) are declared so the dispatcher has a total mapping,
 * but the phone ignores them.
 */
enum class ServerNotificationMethod(val wire: String) {
    // item/…
    ItemStarted("item/started"),
    ItemCompleted("item/completed"),
    AgentMessageDelta("item/agentMessage/delta"),
    PlanDelta("item/plan/delta"),
    ReasoningTextDelta("item/reasoning/textDelta"),
    ReasoningSummaryTextDelta("item/reasoning/summaryTextDelta"),
    ReasoningSummaryPartAdded("item/reasoning/summaryPartAdded"),
    CommandExecutionOutputDelta("item/commandExecution/outputDelta"),
    CommandExecutionTerminalInteraction("item/commandExecution/terminalInteraction"),
    FileChangeOutputDelta("item/fileChange/outputDelta"),
    FileChangePatchUpdated("item/fileChange/patchUpdated"),
    McpToolCallProgress("item/mcpToolCall/progress"),
    AutoApprovalReviewStarted("item/autoApprovalReview/started"),
    AutoApprovalReviewCompleted("item/autoApprovalReview/completed"),

    // turn/…
    TurnStarted("turn/started"),
    TurnCompleted("turn/completed"),
    TurnDiffUpdated("turn/diff/updated"),
    TurnPlanUpdated("turn/plan/updated"),
    TurnModerationMetadata("turn/moderationMetadata"),

    // thread/…
    ThreadStarted("thread/started"),
    ThreadClosed("thread/closed"),
    ThreadArchived("thread/archived"),
    ThreadUnarchived("thread/unarchived"),
    ThreadDeleted("thread/deleted"),
    ThreadCompacted("thread/compacted"),
    ThreadReverted("thread/reverted"),
    ThreadNameUpdated("thread/name/updated"),
    ThreadStatusChanged("thread/status/changed"),
    ThreadTokenUsageUpdated("thread/tokenUsage/updated"),
    ThreadSettingsUpdated("thread/settings/updated"),
    ThreadEnvironmentConnected("thread/environment/connected"),
    ThreadEnvironmentDisconnected("thread/environment/disconnected"),
    ThreadProjectUpdated("thread/project/updated"),
    ThreadAttachmentUpdated("thread/attachment/updated"),
    ThreadGoalUpdated("thread/goal/updated"),
    ThreadGoalCleared("thread/goal/cleared"),
    ThreadQueueChanged("thread/queue/changed"),

    // diagnostics and warnings
    Error("error"),
    Warning("warning"),
    ConfigWarning("configWarning"),
    GuardianWarning("guardianWarning"),
    DeprecationNotice("deprecationNotice"),

    // account / model / mcp
    AccountUpdated("account/updated"),
    AccountLoginCompleted("account/login/completed"),
    AccountRateLimitsUpdated("account/rateLimits/updated"),
    ModelRerouted("model/rerouted"),
    ModelVerification("model/verification"),
    McpServerStartupStatusUpdated("mcpServer/startupStatus/updated"),
    McpServerOauthLoginCompleted("mcpServer/oauthLogin/completed"),

    // server-side resolution of a request the client is still showing
    ServerRequestResolved("serverRequest/resolved"),

    // remaining desktop-only families, kept for a total mapping
    ThreadRealtimeStarted("thread/realtime/started"),
    ThreadRealtimeClosed("thread/realtime/closed"),
    ThreadRealtimeError("thread/realtime/error"),
    ThreadRealtimeSdp("thread/realtime/sdp"),
    ThreadRealtimeItemStarted("thread/realtime/item/started"),
    ThreadRealtimeItemCompleted("thread/realtime/item/completed"),
    ThreadRealtimeItemAdded("thread/realtime/itemAdded"),
    ThreadRealtimeItemTranscriptDelta("thread/realtime/item/transcript/delta"),
    ThreadRealtimeTranscriptDelta("thread/realtime/transcript/delta"),
    ThreadRealtimeTranscriptDone("thread/realtime/transcript/done"),
    ThreadRealtimeOutputAudioDelta("thread/realtime/outputAudio/delta"),
    AutoApprovalReviewStrictReviewRequired("autoApprovalReview/strictReviewRequired"),
    AppListUpdated("app/list/updated"),
    CommandExecOutputDelta("command/exec/outputDelta"),
    ExternalAgentConfigImportProgress("externalAgentConfig/import/progress"),
    ExternalAgentConfigImportCompleted("externalAgentConfig/import/completed"),
    FuzzyFileSearchSessionUpdated("fuzzyFileSearch/sessionUpdated"),
    FuzzyFileSearchSessionCompleted("fuzzyFileSearch/sessionCompleted"),
    HookStarted("hook/started"),
    HookCompleted("hook/completed"),
    ModelProviderAuthRecoveryStarted("modelProvider/authRecoveryStarted"),
    ModelProviderAuthRecoveryCompleted("modelProvider/authRecoveryCompleted"),
    ModelSafetyBufferingUpdated("model/safetyBuffering/updated"),
    McpServerEventStreamNotification("mcpServer/event/stream/notification"),
    ProcessOutputDelta("process/outputDelta"),
    ProcessExited("process/exited"),
    FsChanged("fs/changed"),
    ProjectChanged("project/changed"),
    RemoteControlStatusChanged("remoteControl/status/changed"),
    SkillsChanged("skills/changed"),
    WindowsWorldWritableWarning("windows/worldWritableWarning"),
    WindowsSandboxSetupCompleted("windowsSandbox/setupCompleted"),
    ;

    companion object {
        private val byWire = entries.associateBy { it.wire }

        fun fromWire(wire: String): ServerNotificationMethod? = byWire[wire]
    }
}

/**
 * `ServerRequest` — the 11 requests that must be answered.
 *
 * Mirrors the experimental-inclusive upstream set. A client that ignores one of these leaves the
 * agent blocked forever. The transport either delivers a typed request to the UI or returns an
 * explicit unsupported-method error.
 */
enum class ServerRequestMethod(val wire: String) {
    CommandExecutionApproval("item/commandExecution/requestApproval"),
    FileChangeApproval("item/fileChange/requestApproval"),
    PermissionsApproval("item/permissions/requestApproval"),
    ToolRequestUserInput("item/tool/requestUserInput"),
    ToolCall("item/tool/call"),
    McpServerElicitation("mcpServer/elicitation/request"),
    AccountChatgptAuthTokensRefresh("account/chatgptAuthTokens/refresh"),
    AttestationGenerate("attestation/generate"),
    CurrentTimeRead("currentTime/read"),
    ExecCommandApproval("execCommandApproval"),
    ApplyPatchApproval("applyPatchApproval"),
    ;

    companion object {
        private val byWire = entries.associateBy { it.wire }

        fun fromWire(wire: String): ServerRequestMethod? = byWire[wire]
    }
}

/**
 * Decision the user can hand back for a command execution approval.
 *
 * Mirrors the upstream union: four plain-string decisions plus two payload-carrying ones — the
 * execpolicy amendment ("accept and remember this rule") and the network-policy amendment ("allow
 * or deny this host from now on"). A four-value enum cannot represent the payload variants, which
 * is why "accept and remember" was unreachable before.
 */
sealed interface CommandExecutionApprovalDecision {
    data object Accept : CommandExecutionApprovalDecision

    data object AcceptForSession : CommandExecutionApprovalDecision

    data object Decline : CommandExecutionApprovalDecision

    data object Cancel : CommandExecutionApprovalDecision

    data class AcceptWithExecpolicyAmendment(val execpolicyAmendment: List<String>) :
        CommandExecutionApprovalDecision

    data class ApplyNetworkPolicyAmendment(val networkPolicyAmendment: NetworkPolicyAmendment) :
        CommandExecutionApprovalDecision
}

/** One persistent host rule: mirrors `NetworkPolicyAmendment`. */
data class NetworkPolicyAmendment(
    val action: NetworkPolicyRuleAction,
    val host: String,
)

enum class NetworkPolicyRuleAction(val wire: String) {
    Allow("allow"),
    Deny("deny"),
}

/** Decision the user can hand back for a file-change approval. */
enum class FileChangeApprovalDecision(val wire: String) {
    Accept("accept"),
    AcceptForSession("acceptForSession"),
    Decline("decline"),
    Cancel("cancel"),
    ;

    companion object {
        fun fromWire(value: String): FileChangeApprovalDecision? =
            entries.firstOrNull { it.wire == value }
    }
}

/** Decision the user can hand back for a permission-profile approval. */
enum class PermissionsApprovalDecision(val wire: String) {
    Accept("accept"),
    AcceptForSession("acceptForSession"),
    Decline("decline"),
    ;

    companion object {
        fun fromWire(value: String): PermissionsApprovalDecision? =
            entries.firstOrNull { it.wire == value }
    }
}

/** Answer to a single `request_user_input` question. */
data class UserInputAnswer(val questionId: String, val answers: List<String>)

/**
 * `McpServerElicitationRequestParams`, split by the `mode` tag.
 *
 * The wire is a four-variant union (three schema flavours plus the URL redirect); the schema
 * flavours collapse into [Form] because this client renders the flattened schema either way, while
 * [Url] must stay its own variant: it has no fields to submit, only a page to open and an accept.
 */
/**
 * The typed view of an elicitation's `_meta` when the server is really asking for an approval.
 *
 * These keys are snake_case on purpose: the object is `codex-rs/protocol/src/mcp_approval_meta.rs`
 * handed through verbatim, unlike the camelCase of the v2 protocol around it, so it has to be read
 * by its own names. [persist] decides which allow variants the server will honour.
 */
data class McpApprovalMeta(
    /** `codex_approval_kind`: `mcp_tool_call`, `tool_suggestion` or `browser_auth`. */
    val kind: String?,
    val persist: Set<String> = emptySet(),
    val toolName: String? = null,
    val toolTitle: String? = null,
    val toolDescription: String? = null,
    val paramsDisplay: List<McpApprovalParamDisplay> = emptyList(),
    // `tool_suggestion` only.
    val toolType: String? = null,
    val suggestType: String? = null,
    val suggestReason: String? = null,
    val toolId: String? = null,
    val installUrl: String? = null,
) {
    /** A tool call awaiting a decision, as opposed to a suggestion card or a browser hand-off. */
    val isToolCall: Boolean get() = kind == KindMcpToolCall
    val allowsSession: Boolean get() = PersistSession in persist
    val allowsAlways: Boolean get() = PersistAlways in persist

    /**
     * A suggestion to install or enable a connector/plugin before the call can run.
     *
     * The TUI drops a suggestion missing any required field (`parse_tool_suggestion_request`), so
     * an incomplete payload stays a plain elicitation rather than a half-filled card.
     */
    val isToolSuggestion: Boolean
        get() =
            kind == KindToolSuggestion &&
                toolType in ToolTypes &&
                suggestType in SuggestTypes &&
                !suggestReason.isNullOrBlank() &&
                !toolId.isNullOrBlank() &&
                !toolName.isNullOrBlank()

    val isInstall: Boolean get() = suggestType == SuggestInstall

    companion object {
        const val KindMcpToolCall = "mcp_tool_call"
        const val KindToolSuggestion = "tool_suggestion"
        const val SuggestInstall = "install"
        const val SuggestEnable = "enable"
        const val PersistSession = "session"
        const val PersistAlways = "always"

        private val ToolTypes = setOf("connector", "plugin")
        private val SuggestTypes = setOf(SuggestInstall, SuggestEnable)

        /** Null when the meta carries no approval kind, which is every elicitation but an approval. */
        fun from(meta: JsonElement?): McpApprovalMeta? {
            val o = meta as? JsonObject ?: return null
            val kind = o.text("codex_approval_kind") ?: return null
            return McpApprovalMeta(
                kind = kind,
                persist = o.persistValues(),
                toolName = o.text("tool_name"),
                toolTitle = o.text("tool_title"),
                toolDescription = o.text("tool_description"),
                paramsDisplay =
                    o.array("tool_params_display").mapNotNull { it as? JsonObject }.map { entry ->
                        McpApprovalParamDisplay(
                            name = entry.text("name").orEmpty(),
                            value = entry.text("value").orEmpty(),
                            displayName = entry.text("display_name"),
                        )
                    },
                toolType = o.text("tool_type"),
                suggestType = o.text("suggest_type"),
                suggestReason = o.text("suggest_reason"),
                toolId = o.text("tool_id"),
                installUrl = o.text("install_url"),
            )
        }
    }
}

/** One `tool_params_display` row: the parameter's name, its raw value and an optional label. */
data class McpApprovalParamDisplay(val name: String, val value: String, val displayName: String?)

/** `persist` arrives as a single value or an array; both mean "these modes are allowed". */
private fun JsonObject.persistValues(): Set<String> =
    when (val raw = this["persist"]) {
        is JsonArray -> raw.mapNotNull { it.stringOrNull() }.toSet()
        else -> raw?.stringOrNull()?.let { setOf(it) } ?: emptySet()
    }

sealed interface McpElicitationRequest {
    val serverName: String
    val message: String

    /**
     * The wire `_meta` object, or null when the server sent none.
     *
     * Opaque to the client except for the `_codex_apps.connector_auth_failure` keys the app-link
     * flow reads; keeping it around also lets an accept echo it back.
     */
    val meta: JsonElement?

    data class Form(
        override val serverName: String,
        override val message: String,
        val requestedSchema: McpElicitationSchema = McpElicitationSchema(),
        override val meta: JsonElement? = null,
    ) : McpElicitationRequest {
        val fields: List<McpElicitationField> get() = requestedSchema.fields

        /** The approval this elicitation really is, when [_meta] marks one; see [McpApprovalMeta]. */
        val approval: McpApprovalMeta? = McpApprovalMeta.from(meta)
    }

    data class Url(
        override val serverName: String,
        override val message: String,
        val url: String,
        val elicitationId: String,
        override val meta: JsonElement? = null,
    ) : McpElicitationRequest

    /**
     * `openai/userVerification`: a device-authenticated approval.
     *
     * The server wants the local credential to sign [challenge]; the signed proof travels back as
     * the accept's *content*, not as form fields, so this variant has no schema to render.
     */
    data class UserVerification(
        override val serverName: String,
        val title: String,
        val description: String,
        val challenge: String,
        override val meta: JsonElement? = null,
    ) : McpElicitationRequest {
        override val message: String get() = title
    }
}

/** One field of an MCP elicitation form, flattened from the requested JSON Schema. */
data class McpElicitationField(
    val name: String,
    val title: String,
    val description: String = "",
    val kind: McpElicitationFieldKind = McpElicitationFieldKind.Text,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val value: String = "",
)

enum class McpElicitationFieldKind { Text, Multiline, Boolean, Number, Enum }
