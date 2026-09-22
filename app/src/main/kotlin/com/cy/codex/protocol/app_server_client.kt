package com.cy.codex.protocol

import com.cy.codex.protocol.protocol.RequestId
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.AccountReadResponse
import com.cy.codex.protocol.protocol.v2.AccountLoginCompletedNotification
import com.cy.codex.protocol.protocol.v2.AccountUsage
import com.cy.codex.protocol.protocol.v2.AppInfo
import com.cy.codex.protocol.protocol.v2.AttachmentType
import com.cy.codex.protocol.protocol.v2.BedrockDiscoverResponse
import com.cy.codex.protocol.protocol.v2.BedrockSetupParams
import com.cy.codex.protocol.protocol.v2.CatalogChanged
import com.cy.codex.protocol.protocol.v2.CollaborationModeEntry
import com.cy.codex.protocol.protocol.v2.CommandExecOutputDeltaNotification
import com.cy.codex.protocol.protocol.v2.CommandExecResponse
import com.cy.codex.protocol.protocol.v2.CommandExecutionOutputDelta
import com.cy.codex.protocol.protocol.v2.ConfigBatchWriteParams
import com.cy.codex.protocol.protocol.v2.ConfigLayer
import com.cy.codex.protocol.protocol.v2.ConfigReadResponse
import com.cy.codex.protocol.protocol.v2.ConfigValueWriteParams
import com.cy.codex.protocol.protocol.v2.ConfigWarningNotification
import com.cy.codex.protocol.protocol.v2.ConfigWriteResponse
import com.cy.codex.protocol.protocol.v2.ConsumeRateLimitResetCreditResponse
import com.cy.codex.protocol.protocol.v2.DeprecationNoticeNotification
import com.cy.codex.protocol.protocol.v2.DiagnosticSeverity
import com.cy.codex.protocol.protocol.v2.ElicitationCountResponse
import com.cy.codex.protocol.protocol.v2.EnvironmentInfoResponse
import com.cy.codex.protocol.protocol.v2.EnvironmentStatusResponse
import com.cy.codex.protocol.protocol.v2.ErrorNotification
import com.cy.codex.protocol.protocol.v2.ExperimentalFeatureEntry
import com.cy.codex.protocol.protocol.v2.ExternalAgentConfigImportHistory
import com.cy.codex.protocol.protocol.v2.ExternalAgentConfigMigrationItem
import com.cy.codex.protocol.protocol.v2.FileChangeOutputDelta
import com.cy.codex.protocol.protocol.v2.FileChangePatchUpdatedNotification
import com.cy.codex.protocol.protocol.v2.FileMetadata
import com.cy.codex.protocol.protocol.v2.FileUpdateChange
import com.cy.codex.protocol.protocol.v2.FsChangedNotification
import com.cy.codex.protocol.protocol.v2.FuzzyFileSearchResult
import com.cy.codex.protocol.protocol.v2.FuzzyFileSearchSessionCompletedNotification
import com.cy.codex.protocol.protocol.v2.FuzzyFileSearchSessionUpdatedNotification
import com.cy.codex.protocol.protocol.v2.GuardianApprovalReviewNotification
import com.cy.codex.protocol.protocol.v2.GuardianWarningNotification
import com.cy.codex.protocol.protocol.v2.HookCompletedNotification
import com.cy.codex.protocol.protocol.v2.FeedbackUploadParams
import com.cy.codex.protocol.protocol.v2.FeedbackUploadResponse
import com.cy.codex.protocol.protocol.v2.HookMetadata
import com.cy.codex.protocol.protocol.v2.HooksListEntry
import com.cy.codex.protocol.protocol.v2.HookStartedNotification
import com.cy.codex.protocol.protocol.v2.ItemTextDelta
import com.cy.codex.protocol.protocol.v2.LoginAccountParams
import com.cy.codex.protocol.protocol.v2.LoginAccountResponse
import com.cy.codex.protocol.protocol.v2.MarketplaceEntry
import com.cy.codex.protocol.protocol.v2.McpElicitationRequest
import com.cy.codex.protocol.protocol.v2.McpResourceReadResponse
import com.cy.codex.protocol.protocol.v2.McpServerEventStreamNotification
import com.cy.codex.protocol.protocol.v2.McpServerOauthLoginCompletedNotification
import com.cy.codex.protocol.protocol.v2.McpServerStatusEntry
import com.cy.codex.protocol.protocol.v2.McpServerToolCallResponse
import com.cy.codex.protocol.protocol.v2.McpStartupStatusUpdated
import com.cy.codex.protocol.protocol.v2.McpToolCallProgress
import com.cy.codex.protocol.protocol.v2.MemoryStatusResponse
import com.cy.codex.protocol.protocol.v2.ModelPreset
import com.cy.codex.protocol.protocol.v2.ModelProviderAuthRecoveryNotification
import com.cy.codex.protocol.protocol.v2.ModelRerouted
import com.cy.codex.protocol.protocol.v2.ModelSafetyBufferingUpdatedNotification
import com.cy.codex.protocol.protocol.v2.ModelVerificationNotification
import com.cy.codex.protocol.protocol.v2.PermissionProfileEntry
import com.cy.codex.protocol.protocol.v2.PluginDetail
import com.cy.codex.protocol.protocol.v2.PluginEntry
import com.cy.codex.protocol.protocol.v2.PluginInstallResponse
import com.cy.codex.protocol.protocol.v2.PluginInstalledParams
import com.cy.codex.protocol.protocol.v2.PluginInstalledResponse
import com.cy.codex.protocol.protocol.v2.PluginListParams
import com.cy.codex.protocol.protocol.v2.PluginListResponse
import com.cy.codex.protocol.protocol.v2.PluginShareEntry
import com.cy.codex.protocol.protocol.v2.ProcessExitedNotification
import com.cy.codex.protocol.protocol.v2.ProcessOutputDeltaNotification
import com.cy.codex.protocol.protocol.v2.ProjectChangedNotification
import com.cy.codex.protocol.protocol.v2.ProjectEntry
import com.cy.codex.protocol.protocol.v2.QueuedSubmission
import com.cy.codex.protocol.protocol.v2.AccountRateLimits
import com.cy.codex.protocol.protocol.v2.RateLimitSnapshot
import com.cy.codex.protocol.protocol.v2.RemoteControlClientsListResponse
import com.cy.codex.protocol.protocol.v2.RemoteControlPairingStartResponse
import com.cy.codex.protocol.protocol.v2.RemoteControlPairingStatusResponse
import com.cy.codex.protocol.protocol.v2.RemoteControlStatus
import com.cy.codex.protocol.protocol.v2.RemoteControlStatusChangedNotification
import com.cy.codex.protocol.protocol.v2.ReviewStartResponse
import com.cy.codex.protocol.protocol.v2.ReviewTarget
import com.cy.codex.protocol.protocol.v2.ServerDiagnosticsResponse
import com.cy.codex.protocol.protocol.v2.ServerRequestResolved
import com.cy.codex.protocol.protocol.v2.SkillEntry
import com.cy.codex.protocol.protocol.v2.StrictReviewRequiredNotification
import com.cy.codex.protocol.protocol.v2.TerminalInteraction
import com.cy.codex.protocol.protocol.v2.Thread
import com.cy.codex.protocol.protocol.v2.ThreadAttachment
import com.cy.codex.protocol.protocol.v2.ThreadBackgroundTerminal
import com.cy.codex.protocol.protocol.v2.ThreadGoalUpdated
import com.cy.codex.protocol.protocol.v2.ThreadMemoryMode
import com.cy.codex.protocol.protocol.v2.ThreadNameUpdated
import com.cy.codex.protocol.protocol.v2.ThreadQueueChanged
import com.cy.codex.protocol.protocol.v2.ThreadItemsListParams
import com.cy.codex.protocol.protocol.v2.ThreadListParams
import com.cy.codex.protocol.protocol.v2.ThreadListing
import com.cy.codex.protocol.protocol.v2.ThreadReadParams
import com.cy.codex.protocol.protocol.v2.ThreadReadResponse
import com.cy.codex.protocol.protocol.v2.ThreadResumeParams
import com.cy.codex.protocol.protocol.v2.ThreadStartParams
import com.cy.codex.protocol.protocol.v2.ThreadTurnsListParams
import com.cy.codex.protocol.protocol.v2.ThreadRealtimeAudioChunk
import com.cy.codex.protocol.protocol.v2.ThreadReverted
import com.cy.codex.protocol.protocol.v2.ThreadSection
import com.cy.codex.protocol.protocol.v2.ThreadSessionState
import com.cy.codex.protocol.protocol.v2.ThreadSettingsUpdated
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.protocol.protocol.v2.ThreadStatusChanged
import com.cy.codex.protocol.protocol.v2.ThreadTokenUsageUpdated
import com.cy.codex.protocol.protocol.v2.ThreadUsage
import com.cy.codex.protocol.protocol.v2.TimelineEntry
import com.cy.codex.protocol.protocol.v2.TurnDiffUpdated
import com.cy.codex.protocol.protocol.v2.TurnModerationMetadataNotification
import com.cy.codex.protocol.protocol.v2.TurnPlanUpdated
import com.cy.codex.protocol.protocol.v2.TurnSettingsUpdateParams
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.UserInput
import com.cy.codex.protocol.protocol.v2.UserVerificationEnrollResponse
import com.cy.codex.protocol.protocol.v2.UserVerificationStatusResponse
import com.cy.codex.protocol.protocol.v2.UserVerificationVerifyParams
import com.cy.codex.protocol.protocol.v2.UserVerificationVerifyResponse
import com.cy.codex.protocol.protocol.v2.WarningNotification
import com.cy.codex.protocol.protocol.v2.WindowsSandboxReadinessResponse
import com.cy.codex.protocol.protocol.v2.WindowsSandboxSetupCompletedNotification
import com.cy.codex.protocol.protocol.v2.WindowsSandboxSetupMode
import com.cy.codex.protocol.protocol.v2.WindowsSandboxSetupStartResponse
import com.cy.codex.protocol.protocol.v2.WorkspaceMessage
import com.cy.codex.protocol.protocol.v2.WorldWritableWarningNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
/**
 * The typed event stream the transport produces.
 *
 * One variant per `ServerNotification` method — all 82 of them — so the decoder has an exhaustive
 * target and the reducer cannot silently drop a notification it does not recognise. Mirrors
 * `codex-rs/tui/src/app/app_server_events.rs`: the raw JSON-RPC notification is decoded into one of
 * these before any state sees it, so the reducer never parses JSON.
 */
sealed interface AppServerEvent {
    val threadId: String?

    // ---- item lifecycle -------------------------------------------------------
    data class ItemStarted(override val threadId: String, val turnId: String, val item: ThreadItem) :
        AppServerEvent

    data class ItemCompleted(override val threadId: String, val turnId: String, val item: ThreadItem) :
        AppServerEvent

    data class AgentMessageDelta(override val threadId: String, val delta: ItemTextDelta) : AppServerEvent
    data class PlanDelta(override val threadId: String, val delta: ItemTextDelta) : AppServerEvent
    data class ReasoningTextDelta(override val threadId: String, val delta: ItemTextDelta) : AppServerEvent
    data class ReasoningSummaryDelta(override val threadId: String, val delta: ItemTextDelta) : AppServerEvent
    data class ReasoningSummaryPartAdded(override val threadId: String, val delta: ItemTextDelta) : AppServerEvent
    data class CommandOutputDelta(override val threadId: String, val delta: CommandExecutionOutputDelta) :
        AppServerEvent

    data class CommandTerminalInteraction(override val threadId: String, val delta: TerminalInteraction) :
        AppServerEvent

    /**
     * One file's share of a patch's output, as it is produced.
     *
     * The payload is the protocol's own `FileChangeOutputDelta` and not this class: typing it as
     * the enclosing variant made the field self-recursive, so the only value that compiled was
     * `null` and every delta a server sent arrived empty.
     */
    data class FileChangeOutputDelta(
        override val threadId: String,
        val delta: com.cy.codex.protocol.protocol.v2.FileChangeOutputDelta,
    ) : AppServerEvent

    /** `item/fileChange/patchUpdated`: the patch grew; the item must be re-rendered. */
    data class FileChangePatchUpdated(
        override val threadId: String,
        val delta: FileChangePatchUpdatedNotification,
    ) : AppServerEvent

    data class McpToolProgress(override val threadId: String, val delta: McpToolCallProgress) : AppServerEvent

    // ---- guardian / auto approval review --------------------------------------
    data class AutoApprovalReviewStarted(
        override val threadId: String,
        val delta: GuardianApprovalReviewNotification,
    ) : AppServerEvent

    data class AutoApprovalReviewCompleted(
        override val threadId: String,
        val delta: GuardianApprovalReviewNotification,
    ) : AppServerEvent

    data class StrictReviewRequired(
        override val threadId: String,
        val delta: StrictReviewRequiredNotification,
    ) : AppServerEvent

    // ---- turn lifecycle -------------------------------------------------------
    data class TurnStarted(override val threadId: String, val turnId: String) : AppServerEvent
    data class TurnCompleted(
        override val threadId: String,
        val turnId: String,
        val status: TurnStatus,
        val error: String? = null,
        /** Server-measured duration, when the notification carried one. */
        val durationMs: Long? = null,
        /** Completion time in epoch millis, when the notification carried one. */
        val completedAt: Long? = null,
        /** Detail of a safety stop; see `MisalignmentErrorDetails`. */
        val misalignment: com.cy.codex.protocol.protocol.v2.MisalignmentErrorDetails? = null,
    ) : AppServerEvent

    data class TurnDiffUpdatedEvent(override val threadId: String, val delta: TurnDiffUpdated) : AppServerEvent
    data class TurnPlanUpdatedEvent(override val threadId: String, val delta: TurnPlanUpdated) : AppServerEvent
    data class TurnModerationMetadata(
        override val threadId: String,
        val delta: TurnModerationMetadataNotification,
    ) : AppServerEvent

    // ---- thread lifecycle -----------------------------------------------------
    data class ThreadStartedEvent(override val threadId: String, val thread: Thread) : AppServerEvent
    data class ThreadClosed(override val threadId: String) : AppServerEvent
    data class ThreadArchived(override val threadId: String) : AppServerEvent
    data class ThreadUnarchived(override val threadId: String) : AppServerEvent
    data class ThreadDeleted(override val threadId: String) : AppServerEvent
    data class ThreadCompacted(override val threadId: String, val summary: String?) : AppServerEvent
    data class ThreadRevertedEvent(override val threadId: String, val delta: ThreadReverted) : AppServerEvent
    data class ThreadNameUpdatedEvent(override val threadId: String, val delta: ThreadNameUpdated) :
        AppServerEvent

    data class ThreadStatusChangedEvent(override val threadId: String, val delta: ThreadStatusChanged) :
        AppServerEvent

    data class ThreadTokenUsageEvent(override val threadId: String, val delta: ThreadTokenUsageUpdated) :
        AppServerEvent

    data class ThreadSettingsUpdatedEvent(override val threadId: String, val delta: ThreadSettingsUpdated) :
        AppServerEvent

    data class ThreadGoalUpdatedEvent(override val threadId: String, val delta: ThreadGoalUpdated) :
        AppServerEvent

    data class ThreadGoalCleared(override val threadId: String) : AppServerEvent

    /** The queue moved. Carries no payload — the reducer answers with `thread/queue/list`. */
    data class ThreadQueueChangedEvent(override val threadId: String, val delta: ThreadQueueChanged) :
        AppServerEvent

    data class ThreadAttachmentUpdated(override val threadId: String) : AppServerEvent
    data class ThreadProjectUpdated(override val threadId: String, val projectId: String?) : AppServerEvent
    data class EnvironmentConnected(override val threadId: String, val environmentId: String) : AppServerEvent
    data class EnvironmentDisconnected(override val threadId: String, val environmentId: String) :
        AppServerEvent

    // ---- realtime voice -------------------------------------------------------
    data class RealtimeStarted(override val threadId: String, val sessionId: String) : AppServerEvent
    data class RealtimeClosed(override val threadId: String, val reason: String?) : AppServerEvent
    data class RealtimeError(override val threadId: String, val message: String) : AppServerEvent
    data class RealtimeSdp(override val threadId: String, val sdp: String) : AppServerEvent
    data class RealtimeItemAdded(override val threadId: String, val itemId: String) : AppServerEvent
    data class RealtimeItemStarted(override val threadId: String, val itemId: String, val role: String) :
        AppServerEvent

    data class RealtimeItemCompleted(override val threadId: String, val itemId: String, val text: String) :
        AppServerEvent

    data class RealtimeItemTranscriptDelta(
        override val threadId: String,
        val itemId: String,
        val delta: String,
    ) : AppServerEvent

    data class RealtimeTranscriptDelta(override val threadId: String, val delta: String) : AppServerEvent
    data class RealtimeTranscriptDone(override val threadId: String, val text: String) : AppServerEvent
    data class RealtimeOutputAudioDelta(override val threadId: String, val audioBase64: String) :
        AppServerEvent

    // ---- diagnostics ----------------------------------------------------------
    data class ErrorEvent(
        override val threadId: String?,
        val delta: ErrorNotification,
    ) : AppServerEvent

    data class WarningEvent(
        override val threadId: String?,
        val delta: WarningNotification,
    ) : AppServerEvent

    data class ConfigWarningEvent(val delta: ConfigWarningNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class GuardianWarningEvent(
        override val threadId: String?,
        val delta: GuardianWarningNotification,
    ) : AppServerEvent

    data class DeprecationNoticeEvent(val delta: DeprecationNoticeNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class WorldWritableWarning(val delta: WorldWritableWarningNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class RequestResolved(override val threadId: String?, val delta: ServerRequestResolved) :
        AppServerEvent

    /**
     * `android/transportLagged`: the bridge's bounded queue dropped [skipped] messages.
     *
     * A transport health marker, not an application event — upstream models it as
     * `InProcessServerEvent::Lagged` (`app-server/src/in_process.rs:171-181`). The connection is
     * still alive, but anything only known from deltas may have gone missing, so the reducers
     * resync from the server instead of treating it as a lost connection.
     */
    data class TransportLagged(val skipped: Long) : AppServerEvent {
        override val threadId: String? get() = null
    }

    // ---- account, model, catalogs --------------------------------------------
    data class AccountUpdated(val account: AccountReadResponse) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class AccountLoginCompleted(val delta: AccountLoginCompletedNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class RateLimitsUpdatedEvent(val rateLimits: RateLimitSnapshot) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class ModelReroutedEvent(override val threadId: String, val delta: ModelRerouted) : AppServerEvent
    data class ModelVerification(val delta: ModelVerificationNotification) : AppServerEvent {
        override val threadId: String? get() = delta.threadId
    }

    data class ModelSafetyBufferingUpdated(val delta: ModelSafetyBufferingUpdatedNotification) : AppServerEvent {
        override val threadId: String? get() = delta.threadId
    }

    data class ModelProviderAuthRecovery(
        val delta: ModelProviderAuthRecoveryNotification,
        val started: Boolean,
    ) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class McpStartupStatusEvent(val delta: McpStartupStatusUpdated) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class McpOauthLoginCompleted(val delta: McpServerOauthLoginCompletedNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class McpServerEvent(val delta: McpServerEventStreamNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class SkillsChanged(val delta: CatalogChanged) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class AppListUpdated(val delta: CatalogChanged) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class ProjectChanged(val delta: ProjectChangedNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class RemoteControlStatusChanged(val delta: RemoteControlStatusChangedNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    // ---- hooks, fs, one-off processes ----------------------------------------
    data class HookStarted(override val threadId: String, val delta: HookStartedNotification) : AppServerEvent
    data class HookCompleted(override val threadId: String, val delta: HookCompletedNotification) :
        AppServerEvent

    data class FsChangedEvent(val delta: FsChangedNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class CommandExecOutput(val delta: CommandExecOutputDeltaNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class ProcessOutputDelta(val delta: ProcessOutputDeltaNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class ProcessExited(val delta: ProcessExitedNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class FuzzySearchUpdated(val delta: FuzzyFileSearchSessionUpdatedNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class FuzzySearchCompleted(val delta: FuzzyFileSearchSessionCompletedNotification) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class ExternalAgentImportProgress(
        val importId: String,
        val results: List<com.cy.codex.protocol.protocol.v2.ExternalAgentConfigImportTypeResult>,
    ) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class ExternalAgentImportCompleted(
        val importId: String,
        val results: List<com.cy.codex.protocol.protocol.v2.ExternalAgentConfigImportTypeResult>,
    ) : AppServerEvent {
        override val threadId: String? get() = null
    }

    data class WindowsSandboxSetupCompleted(val delta: WindowsSandboxSetupCompletedNotification) :
        AppServerEvent {
        override val threadId: String? get() = null
    }
}

/**
 * Server-initiated request that is waiting for the client.
 *
 * One variant per `ServerRequest` method — all 11 of them. The six approval families block a turn
 * until answered; the other five are the server asking the client for something only the client can
 * produce (an attestation, the current time, a refreshed token).
 *
 * Mirrors `codex-rs/tui/src/bottom_pane/approval_overlay.rs::ApprovalRequest` plus the
 * `request_user_input` and elicitation families. Exactly one of these is on screen at a time; the
 * rest queue up in arrival order.
 */
sealed interface ApprovalRequest {
    /** Id used to answer the server; also the correlation key for `serverRequest/resolved`. */
    val requestId: RequestId
    val threadId: String
    val turnId: String?
    val itemId: String
    val receivedAt: Long

    data class Exec(
        override val requestId: RequestId,
        override val threadId: String,
        override val turnId: String?,
        override val itemId: String,
        override val receivedAt: Long,
        val params: com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalParams,
    ) : ApprovalRequest

    data class ApplyPatch(
        override val requestId: RequestId,
        override val threadId: String,
        override val turnId: String?,
        override val itemId: String,
        override val receivedAt: Long,
        val params: com.cy.codex.protocol.protocol.v2.FileChangeApprovalParams,
    ) : ApprovalRequest

    data class Permissions(
        override val requestId: RequestId,
        override val threadId: String,
        override val turnId: String?,
        override val itemId: String,
        override val receivedAt: Long,
        val params: com.cy.codex.protocol.protocol.v2.PermissionsApprovalParams,
    ) : ApprovalRequest

    data class UserInput(
        override val requestId: RequestId,
        override val threadId: String,
        override val turnId: String?,
        override val itemId: String,
        override val receivedAt: Long,
        val params: com.cy.codex.protocol.protocol.v2.ToolRequestUserInputParams,
    ) : ApprovalRequest

    data class Elicitation(
        override val requestId: RequestId,
        override val threadId: String,
        override val turnId: String?,
        override val itemId: String,
        override val receivedAt: Long,
        val params: McpElicitationRequest,
    ) : ApprovalRequest

    data class DynamicTool(
        override val requestId: RequestId,
        override val threadId: String,
        override val turnId: String?,
        override val itemId: String,
        override val receivedAt: Long,
        val params: com.cy.codex.protocol.protocol.v2.DynamicToolCallParams,
    ) : ApprovalRequest

    /**
     * `account/chatgptAuthTokens/refresh`.
     *
     * Not an approval and never shown to the user: the server's access token expired and it is
     * asking the host to mint a new one. Answering with [ApprovalResponse.Tokens] resumes the turn.
     */
    data class ChatgptAuthTokensRefresh(
        override val requestId: RequestId,
        val reason: String? = null,
        val previousAccountId: String? = null,
    ) : ApprovalRequest {
        override val threadId: String get() = ""
        override val turnId: String? get() = null
        override val itemId: String get() = ""
        override val receivedAt: Long get() = 0L
    }

    /** `attestation/generate`: the client must attest the running binary. */
    data class AttestationGenerate(
        override val requestId: RequestId,
        val nonce: String = "",
    ) : ApprovalRequest {
        override val threadId: String get() = ""
        override val turnId: String? get() = null
        override val itemId: String get() = ""
        override val receivedAt: Long get() = 0L
    }

    /** `currentTime/read`: the server wants a trusted clock reading for prompt context. */
    data class CurrentTimeRead(override val requestId: RequestId) : ApprovalRequest {
        override val threadId: String get() = ""
        override val turnId: String? get() = null
        override val itemId: String get() = ""
        override val receivedAt: Long get() = 0L
    }
}

/** Answer handed back for one server-initiated [ApprovalRequest]. */
sealed interface ApprovalResponse {
    data class CommandExecution(
        val decision: com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalDecision,
    ) : ApprovalResponse

    data class FileChange(
        val decision: com.cy.codex.protocol.protocol.v2.FileChangeApprovalDecision,
    ) : ApprovalResponse

    data class Permissions(
        val decision: com.cy.codex.protocol.protocol.v2.PermissionsApprovalDecision,
    ) : ApprovalResponse

    data class UserInput(
        val answers: List<com.cy.codex.protocol.protocol.v2.UserInputAnswer>,
    ) : ApprovalResponse

    data class Elicitation(
        val action: ElicitationAction,
        val content: Map<String, String> = emptyMap(),
        /** The `_meta` object to echo back, for servers that asked for one. */
        val meta: JsonElement? = null,
    ) : ApprovalResponse

    data class DynamicTool(
        val result: com.cy.codex.protocol.protocol.v2.DynamicToolCallResponse,
    ) : ApprovalResponse

    /** Answer to [ApprovalRequest.ChatgptAuthTokensRefresh]. */
    data class Tokens(
        val accessToken: String,
        val chatgptAccountId: String,
        val chatgptPlanType: String? = null,
    ) : ApprovalResponse

    /** Answer to [ApprovalRequest.AttestationGenerate]. */
    data class Attestation(val token: String) : ApprovalResponse

    /** Answer to [ApprovalRequest.CurrentTimeRead]; epoch millis. */
    data class CurrentTime(val epochMillis: Long) : ApprovalResponse
}

/**
 * What every unimplemented [AppServerClient] method answers with.
 *
 * A `Result.failure` rather than a thrown exception, so a call a backend cannot serve takes down
 * one call and not the session. It sits outside the interface because it is not part of the
 * contract: `AppServerClientBindingTest` walks the interface and would otherwise see it as a
 * method the only backend fails to override.
 */
private fun <T> unsupported(method: String): Result<T> =
    Result.failure(UnsupportedOperationException("$method is not supported by the embedded Android client"))

enum class ElicitationAction(val wire: String) {
    Accept("accept"),
    Decline("decline"),
    Cancel("cancel"),
}

/**
 * Typed boundary for the embedded app-server JSON-RPC connection.
 *
 * Methods are grouped by the protocol family they call, in the same order as
 * [com.cy.codex.protocol.protocol.v2.ClientRequestMethod]. A method that a backend cannot serve
 * returns `Result.failure` rather than throwing, so one unsupported call cannot take the session
 * down.
 */
interface AppServerClient {

    /** `initialize` + the `initialized` notification. */
    suspend fun initialize(clientInfo: com.cy.codex.protocol.protocol.v2.ClientInfo): Result<Unit>

    /** Typed notifications, server requests and connection state. */
    val events: Flow<AppServerEvent>

    /** Server-initiated requests that need a user decision. */
    val requests: Flow<ApprovalRequest>

    /** Answer one [ApprovalRequest]. */
    suspend fun respond(requestId: RequestId, response: ApprovalResponse)

    // ---- thread/… lifecycle ---------------------------------------------------
    /**
     * `thread/list`.
     *
     * When [ThreadListParams.archived] is true the listing spans both scopes, and which rows came
     * from the archived half is reported by [ThreadListing.archivedIds]: the wire's `Thread` has no
     * archived flag, so the scope a row was fetched under is the only thing that knows.
     */
    suspend fun listThreads(params: ThreadListParams = ThreadListParams()): Result<ThreadListing> = unsupported("listThreads")
    suspend fun listLoadedThreads(): Result<List<String>> = unsupported("listLoadedThreads")
    suspend fun readThread(params: ThreadReadParams): Result<ThreadReadResponse> = unsupported("readThread")
    suspend fun startThread(params: ThreadStartParams): Result<ThreadSessionState> = unsupported("startThread")
    suspend fun resumeThread(params: ThreadResumeParams): Result<ThreadSessionState> = unsupported("resumeThread")
    suspend fun forkThread(params: com.cy.codex.protocol.protocol.v2.ThreadForkParams): Result<ThreadSessionState> = unsupported("forkThread")
    suspend fun archiveThread(threadId: String): Result<Unit> = unsupported("archiveThread")
    suspend fun unarchiveThread(threadId: String): Result<Unit> = unsupported("unarchiveThread")
    suspend fun deleteThread(threadId: String): Result<Unit> = unsupported("deleteThread")
    suspend fun setThreadName(threadId: String, name: String): Result<Unit> = unsupported("setThreadName")
    suspend fun compactThread(threadId: String): Result<Unit> = unsupported("compactThread")
    suspend fun revertThread(threadId: String, itemId: String?): Result<Unit> = unsupported("revertThread")

    /** `thread/items/list`: one page of items, for hydrating a long transcript. */
    suspend fun listThreadItems(params: ThreadItemsListParams): Result<ThreadItemsPage> = unsupported("listThreadItems")

    /** `thread/turns/list`: one page of turns. */
    suspend fun listThreadTurns(params: ThreadTurnsListParams): Result<ThreadTurnsPage> = unsupported("listThreadTurns")

    /** `thread/timeline/list`: the sparse index behind the scrubber. */
    suspend fun listThreadTimeline(threadId: String, cursor: String? = null, limit: Int? = null): Result<List<TimelineEntry>> = unsupported("listThreadTimeline")

    suspend fun unsubscribeThread(threadId: String): Result<Unit> = unsupported("unsubscribeThread")
    suspend fun updateThreadMetadata(
        threadId: String,
        name: String? = null,
        projectId: String? = null,
    ): Result<Thread> = unsupported("updateThreadMetadata")

    /** `thread/inject_items`: splice raw response items into the thread's history. */
    suspend fun injectThreadItems(threadId: String, items: List<JsonElement>): Result<Unit> = unsupported("injectThreadItems")

    /** `thread/shellCommand`: run a shell command as the *user*, outside the agent. */
    suspend fun runShellCommand(threadId: String, command: String): Result<Unit> = unsupported("runShellCommand")

    /** `thread/approveGuardianDeniedAction`: unblock something the review policy denied. */
    suspend fun approveGuardianDeniedAction(threadId: String, itemId: String): Result<Unit> = unsupported("approveGuardianDeniedAction")

    /** `thread/search` across every thread. */
    suspend fun searchThreads(term: String, includeArchived: Boolean = false): Result<ThreadListing> = unsupported("searchThreads")

    /** `thread/searchOccurrences` inside one thread. */
    suspend fun searchThreadOccurrences(threadId: String, term: String): Result<List<OccurrenceMatch>> = unsupported("searchThreadOccurrences")

    suspend fun moveThreadToSection(threadId: String, sectionId: String?): Result<Unit> = unsupported("moveThreadToSection")

    suspend fun updateThreadSettings(
        threadId: String,
        model: String? = null,
        effort: com.cy.codex.protocol.protocol.v2.ReasoningEffort? = null,
        approvalPolicy: com.cy.codex.protocol.protocol.v2.AskForApproval? = null,
        collaborationMode: com.cy.codex.protocol.protocol.v2.CollaborationMode? = null,
        personality: com.cy.codex.protocol.protocol.v2.Personality? = null,
    ): Result<Unit> = unsupported("updateThreadSettings")

    /** `thread/settings/update` in full; the typed overload above covers the common knobs. */
    suspend fun updateThreadSettingsFull(params: com.cy.codex.protocol.protocol.v2.ThreadSettingsUpdateParams): Result<Unit> = unsupported("updateThreadSettingsFull")

    suspend fun setThreadMemoryMode(threadId: String, mode: ThreadMemoryMode): Result<Unit> = unsupported("setThreadMemoryMode")

    // ---- thread/… elicitation bookkeeping -------------------------------------
    /**
     * `thread/increment_elicitation`: an open-form question is outstanding.
     *
     * While the count is non-zero the server pauses the thread's timeout accounting, so the two
     * calls bracket anything that waits on a human.
     */
    suspend fun incrementElicitation(threadId: String): Result<ElicitationCountResponse> = unsupported("incrementElicitation")

    /** `thread/decrement_elicitation`: the open-form question was answered. */
    suspend fun decrementElicitation(threadId: String): Result<ElicitationCountResponse> = unsupported("decrementElicitation")

    // ---- thread/… goals -------------------------------------------------------
    suspend fun setGoal(params: com.cy.codex.protocol.protocol.v2.ThreadGoalSetParams): Result<ThreadGoalUpdated> = unsupported("setGoal")
    suspend fun getGoal(threadId: String): Result<ThreadGoalUpdated?> = unsupported("getGoal")
    suspend fun clearGoal(threadId: String): Result<Unit> = unsupported("clearGoal")

    // ---- thread/… queue -------------------------------------------------------
    suspend fun listQueue(threadId: String): Result<List<QueuedSubmission>> = unsupported("listQueue")
    suspend fun addToQueue(threadId: String, inputs: List<UserInput>): Result<QueuedSubmission?> = unsupported("addToQueue")
    suspend fun updateQueued(threadId: String, id: String, inputs: List<UserInput>): Result<Unit> = unsupported("updateQueued")
    suspend fun deleteQueued(threadId: String, id: String): Result<Unit> = unsupported("deleteQueued")
    suspend fun reorderQueue(threadId: String, ids: List<String>): Result<Unit> = unsupported("reorderQueue")
    suspend fun startQueued(threadId: String, id: String? = null): Result<Unit> = unsupported("startQueued")

    // ---- thread/… attachments -------------------------------------------------
    suspend fun listAttachments(threadId: String): Result<List<ThreadAttachment>> = unsupported("listAttachments")
    suspend fun addAttachment(
        threadId: String,
        type: AttachmentType,
        identityKey: String,
        payload: JsonElement = JsonNull,
    ): Result<ThreadAttachment> = unsupported("addAttachment")

    /**
     * `thread/attachment/remove`.
     *
     * An attachment is addressed by its type and identity key, not by the id the add response
     * carried: the server keys the record by the caller-supplied identity, and the same identity
     * added twice is one record.
     */
    suspend fun removeAttachment(threadId: String, type: AttachmentType, identityKey: String): Result<Unit> = unsupported("removeAttachment")

    // ---- thread/… background terminals ---------------------------------------
    suspend fun listBackgroundTerminals(threadId: String): Result<List<ThreadBackgroundTerminal>> = unsupported("listBackgroundTerminals")
    suspend fun terminateBackgroundTerminal(threadId: String, processId: String): Result<Unit> = unsupported("terminateBackgroundTerminal")
    suspend fun cleanBackgroundTerminals(threadId: String): Result<Unit> = unsupported("cleanBackgroundTerminals")

    // ---- thread/… realtime voice ---------------------------------------------
    suspend fun startRealtime(threadId: String, sdpOffer: String? = null): Result<Unit> = unsupported("startRealtime")
    suspend fun stopRealtime(threadId: String): Result<Unit> = unsupported("stopRealtime")
    suspend fun listRealtimeVoices(): Result<List<String>> = unsupported("listRealtimeVoices")
    suspend fun appendRealtimeText(threadId: String, text: String): Result<Unit> = unsupported("appendRealtimeText")
    suspend fun appendRealtimeSpeech(threadId: String, text: String): Result<Unit> = unsupported("appendRealtimeSpeech")

    /**
     * `thread/realtime/appendAudio`: push one captured PCM chunk into the voice session.
     *
     * The counterpart to [appendRealtimeSpeech]: speech is text the *client* generated, audio is
     * the raw microphone stream the server transcribes. Without it the voice feature can only be
     * driven by typing, which is what made it look complete while the mic path was missing.
     */
    suspend fun appendRealtimeAudio(threadId: String, audio: ThreadRealtimeAudioChunk): Result<Unit> = unsupported("appendRealtimeAudio")

    // ---- turn/… ---------------------------------------------------------------
    /**
     * Start a turn; [outputSchema] asks the server to constrain the final answer to a JSON schema,
     * which is how hidden structured requests (recap, thread titles) read their result.
     */
    suspend fun startTurn(
        threadId: String,
        inputs: List<UserInput>,
        outputSchema: JsonElement? = null,
        effort: com.cy.codex.protocol.protocol.v2.ReasoningEffort? = null,
        /** Experimental `responsesapiClientMetadata`; the misalignment override rides here. */
        clientMetadata: Map<String, String>? = null,
    ): Result<String> = unsupported("startTurn")
    suspend fun steerTurn(threadId: String, inputs: List<UserInput>): Result<String> = unsupported("steerTurn")
    suspend fun interruptTurn(threadId: String): Result<Unit> = unsupported("interruptTurn")
    suspend fun updateTurnSettings(params: TurnSettingsUpdateParams): Result<Unit> = unsupported("updateTurnSettings")

    // ---- threadSection/… ------------------------------------------------------
    suspend fun listSections(): Result<List<ThreadSection>> = unsupported("listSections")
    suspend fun createSection(name: String): Result<ThreadSection> = unsupported("createSection")
    suspend fun updateSection(sectionId: String, name: String): Result<ThreadSection> = unsupported("updateSection")
    suspend fun deleteSection(sectionId: String): Result<Unit> = unsupported("deleteSection")

    // ---- account/… ------------------------------------------------------------
    suspend fun readAccount(): Result<AccountReadResponse> = unsupported("readAccount")
    suspend fun login(params: LoginAccountParams): Result<LoginAccountResponse> = unsupported("login")
    suspend fun cancelLogin(loginId: String): Result<Unit> = unsupported("cancelLogin")
    suspend fun logout(): Result<Unit> = unsupported("logout")
    suspend fun readRateLimits(): Result<AccountRateLimits> = unsupported("readRateLimits")
    suspend fun readUsage(): Result<AccountUsage> = unsupported("readUsage")

    /** Estimated credits/USD for one thread; `account/usage/read` with a `threadId`. */
    suspend fun readThreadUsage(threadId: String): Result<ThreadUsage> = unsupported("readThreadUsage")
    suspend fun readWorkspaceMessages(): Result<List<WorkspaceMessage>> = unsupported("readWorkspaceMessages")
    suspend fun consumeRateLimitResetCredit(creditId: String? = null): Result<ConsumeRateLimitResetCreditResponse> = unsupported("consumeRateLimitResetCredit")
    suspend fun sendAddCreditsNudgeEmail(
        creditType: com.cy.codex.protocol.protocol.v2.AddCreditsNudgeCreditType,
    ): Result<com.cy.codex.protocol.protocol.v2.SendAddCreditsNudgeEmailResponse> = unsupported("sendAddCreditsNudgeEmail")

    suspend fun bedrockDiscover(): Result<BedrockDiscoverResponse> = unsupported("bedrockDiscover")
    suspend fun bedrockSetup(params: BedrockSetupParams): Result<Unit> = unsupported("bedrockSetup")

    // ---- fs/… -----------------------------------------------------------------
    suspend fun readFile(path: String): Result<ByteArray> = unsupported("readFile")
    suspend fun writeFile(path: String, bytes: ByteArray): Result<Unit> = unsupported("writeFile")
    suspend fun readDirectory(path: String): Result<List<FileMetadata>> = unsupported("readDirectory")
    suspend fun createDirectory(path: String, recursive: Boolean = true): Result<Unit> = unsupported("createDirectory")
    suspend fun getMetadata(path: String): Result<FileMetadata> = unsupported("getMetadata")
    suspend fun removePath(path: String, recursive: Boolean = false): Result<Unit> = unsupported("removePath")
    suspend fun copyPath(source: String, destination: String, recursive: Boolean = false): Result<Unit> = unsupported("copyPath")
    suspend fun watchPath(path: String, watchId: String): Result<Unit> = unsupported("watchPath")
    suspend fun unwatchPath(watchId: String): Result<Unit> = unsupported("unwatchPath")

    // ---- command/exec and process/… ------------------------------------------
    suspend fun execCommand(
        command: List<String>,
        cwd: String? = null,
        timeoutMs: Long? = null,
        tty: Boolean = false,
        env: Map<String, String>? = null,
    ): Result<CommandExecResponse> = unsupported("execCommand")

    suspend fun execWrite(processId: String, data: ByteArray? = null, closeStdin: Boolean = false): Result<Unit> = unsupported("execWrite")
    suspend fun execResize(processId: String, rows: Int, cols: Int): Result<Unit> = unsupported("execResize")
    suspend fun execTerminate(processId: String): Result<Unit> = unsupported("execTerminate")

    suspend fun spawnProcess(command: List<String>, cwd: String? = null, tty: Boolean = true): Result<String> = unsupported("spawnProcess")
    suspend fun writeProcessStdin(processId: String, data: ByteArray? = null, closeStdin: Boolean = false): Result<Unit> = unsupported("writeProcessStdin")
    suspend fun resizeProcessPty(processId: String, rows: Int, cols: Int): Result<Unit> = unsupported("resizeProcessPty")
    suspend fun killProcess(processId: String): Result<Unit> = unsupported("killProcess")

    // ---- config/… -------------------------------------------------------------
    suspend fun readConfig(cwd: String? = null, includeLayers: Boolean = true): Result<ConfigReadResponse> = unsupported("readConfig")
    suspend fun writeConfigValue(params: ConfigValueWriteParams): Result<ConfigWriteResponse> = unsupported("writeConfigValue")
    suspend fun writeConfigBatch(params: ConfigBatchWriteParams): Result<ConfigWriteResponse> = unsupported("writeConfigBatch")
    suspend fun reloadMcpServers(): Result<Unit> = unsupported("reloadMcpServers")
    suspend fun readConfigRequirements(): Result<com.cy.codex.protocol.protocol.v2.ConfigRequirementsReadResponse> = unsupported("readConfigRequirements")

    /** Every layer of the config stack, highest precedence first. */
    suspend fun readConfigLayers(): Result<List<ConfigLayer>> = unsupported("readConfigLayers")

    // ---- model, permissions, features, collaboration -------------------------
    suspend fun listModels(): Result<List<ModelPreset>> = unsupported("listModels")
    suspend fun readModelProviderCapabilities(): Result<Map<String, Boolean>> = unsupported("readModelProviderCapabilities")
    suspend fun listPermissionProfiles(): Result<List<PermissionProfileEntry>> = unsupported("listPermissionProfiles")
    suspend fun listExperimentalFeatures(): Result<List<ExperimentalFeatureEntry>> = unsupported("listExperimentalFeatures")
    suspend fun setExperimentalFeature(id: String, enabled: Boolean): Result<Unit> = unsupported("setExperimentalFeature")
    suspend fun listCollaborationModes(): Result<List<CollaborationModeEntry>> = unsupported("listCollaborationModes")

    // ---- mcpServer/… ----------------------------------------------------------
    suspend fun listMcpServers(): Result<List<McpServerStatusEntry>> = unsupported("listMcpServers")
    suspend fun mcpOauthLogin(name: String): Result<String> = unsupported("mcpOauthLogin")
    suspend fun readMcpResource(server: String, uri: String): Result<McpResourceReadResponse> = unsupported("readMcpResource")

    suspend fun callMcpTool(
        server: String,
        tool: String,
        arguments: String = "{}",
        threadId: String? = null,
    ): Result<McpServerToolCallResponse> = unsupported("callMcpTool")

    /**
     * `mcpServer/event/stream/start`: subscribe to one MCP server's notifications for a tool call.
     *
     * [subscriptionId] is client-chosen and is what [stopMcpEventStream] names; [name] and
     * [arguments] identify the tool call the stream belongs to, matching the `mcpServer/tool/call`
     * parameters.
     */
    suspend fun startMcpEventStream(
        server: String,
        subscriptionId: String,
        name: String,
        arguments: JsonElement,
        threadId: String,
    ): Result<Unit> = unsupported("startMcpEventStream")

    suspend fun stopMcpEventStream(subscriptionId: String): Result<Unit> = unsupported("stopMcpEventStream")

    // ---- memory/… -------------------------------------------------------------
    suspend fun readMemoryStatus(): Result<MemoryStatusResponse> = unsupported("readMemoryStatus")
    suspend fun resetMemory(): Result<Unit> = unsupported("resetMemory")

    // ---- skills, plugins, marketplaces, apps ---------------------------------
    suspend fun listSkills(): Result<List<SkillEntry>> = unsupported("listSkills")
    suspend fun writeSkillConfig(name: String, enabled: Boolean): Result<Unit> = unsupported("writeSkillConfig")
    suspend fun setSkillExtraRoots(roots: List<String>): Result<Unit> = unsupported("setSkillExtraRoots")
    /**
     * `plugin/list`.
     *
     * Returns the marketplace catalog rather than a flat plugin list, because that is what the
     * protocol sends: every plugin row hangs off a marketplace, and the featured ids travel
     * alongside. `catalog.marketplaces` is filled from here — there is no `marketplace/list` method
     * to call, so this is the only place the marketplace list can come from.
     */
    suspend fun listPlugins(params: PluginListParams = PluginListParams()): Result<PluginListResponse> = unsupported("listPlugins")

    /** `plugin/installed`: what is present, again keyed by marketplace. */
    suspend fun listInstalledPlugins(
        params: PluginInstalledParams = PluginInstalledParams(),
    ): Result<PluginInstalledResponse> = unsupported("listInstalledPlugins")

    suspend fun readPlugin(name: String, marketplace: String? = null): Result<PluginDetail> = unsupported("readPlugin")
    suspend fun installPlugin(name: String, marketplace: String? = null): Result<PluginInstallResponse> = unsupported("installPlugin")
    suspend fun uninstallPlugin(pluginId: String): Result<Unit> = unsupported("uninstallPlugin")
    suspend fun readPluginSkill(marketplace: String, pluginId: String, skillName: String): Result<String?> = unsupported("readPluginSkill")
    suspend fun reconcilePlugins(): Result<List<PluginEntry>> = unsupported("reconcilePlugins")
    suspend fun searchPlugins(term: String): Result<List<PluginEntry>> = unsupported("searchPlugins")
    suspend fun listPluginShares(): Result<List<PluginShareEntry>> = unsupported("listPluginShares")
    suspend fun savePluginShare(
        pluginPath: String,
        remotePluginId: String? = null,
    ): Result<com.cy.codex.protocol.protocol.v2.PluginShareSaveResponse> = unsupported("savePluginShare")

    suspend fun deletePluginShare(remotePluginId: String): Result<Unit> = unsupported("deletePluginShare")
    suspend fun checkoutPluginShare(
        remotePluginId: String,
    ): Result<com.cy.codex.protocol.protocol.v2.PluginShareCheckoutResponse> = unsupported("checkoutPluginShare")

    suspend fun updatePluginShareTargets(
        remotePluginId: String,
        discoverability: com.cy.codex.protocol.protocol.v2.PluginShareDiscoverability,
        targets: List<com.cy.codex.protocol.protocol.v2.PluginShareTarget>,
    ): Result<com.cy.codex.protocol.protocol.v2.PluginShareUpdateTargetsResponse> = unsupported("updatePluginShareTargets")
    suspend fun addMarketplace(source: String, refName: String? = null): Result<MarketplaceEntry> = unsupported("addMarketplace")
    suspend fun removeMarketplace(name: String): Result<Unit> = unsupported("removeMarketplace")
    suspend fun upgradeMarketplace(name: String? = null): Result<List<String>> = unsupported("upgradeMarketplace")
    suspend fun listApps(): Result<List<AppInfo>> = unsupported("listApps")
    suspend fun listInstalledApps(): Result<List<AppInfo>> = unsupported("listInstalledApps")
    suspend fun readApps(ids: List<String>): Result<List<AppInfo>> = unsupported("readApps")

    // ---- projects and environments -------------------------------------------
    suspend fun listProjects(): Result<List<ProjectEntry>> = unsupported("listProjects")
    suspend fun readProject(projectId: String): Result<ProjectEntry> = unsupported("readProject")
    suspend fun createProject(name: String, path: String): Result<ProjectEntry> = unsupported("createProject")
    suspend fun updateProject(projectId: String, name: String? = null, path: String? = null): Result<ProjectEntry> = unsupported("updateProject")
    suspend fun deleteProject(projectId: String): Result<Unit> = unsupported("deleteProject")
    suspend fun moveProject(projectId: String, position: Int): Result<Unit> = unsupported("moveProject")
    suspend fun importProject(path: String): Result<ProjectEntry> = unsupported("importProject")

    /**
     * Register an exec server as an environment.
     *
     * Not "add a local folder": an environment is a *remote* execution target reached over its exec
     * server, which is why the parameters are a url and a timeout rather than a path.
     */
    suspend fun addEnvironment(
        environmentId: String,
        execServerUrl: String,
        connectTimeoutMs: Long? = null,
    ): Result<Unit> = unsupported("addEnvironment")

    /** Read one environment's shell and default directory. There is no bulk variant. */
    suspend fun readEnvironmentInfo(environmentId: String): Result<EnvironmentInfoResponse> = unsupported("readEnvironmentInfo")

    /** Read one environment's connection state without starting or recovering it. */
    suspend fun readEnvironmentStatus(environmentId: String): Result<EnvironmentStatusResponse> = unsupported("readEnvironmentStatus")

    // ---- remote control ------------------------------------------------------
    //
    // Every answer carries the same four fields, so all four reads and writes return one
    // [RemoteControlStatus] shape and the app stores it once.
    suspend fun readRemoteControlStatus(): Result<RemoteControlStatus> = unsupported("readRemoteControlStatus")
    suspend fun enableRemoteControl(ephemeral: Boolean = false): Result<RemoteControlStatus> = unsupported("enableRemoteControl")
    suspend fun disableRemoteControl(ephemeral: Boolean = false): Result<RemoteControlStatus> = unsupported("disableRemoteControl")

    /** Begin pairing; `manualCode` asks for a short code a human can type. */
    suspend fun startRemoteControlPairing(manualCode: Boolean = false): Result<RemoteControlPairingStartResponse> = unsupported("startRemoteControlPairing")

    /** Poll a pairing attempt. Either code form identifies it. */
    suspend fun readRemoteControlPairing(
        pairingCode: String? = null,
        manualPairingCode: String? = null,
    ): Result<RemoteControlPairingStatusResponse> = unsupported("readRemoteControlPairing")

    suspend fun listRemoteControlClients(
        environmentId: String,
        cursor: String? = null,
        limit: Int? = null,
    ): Result<RemoteControlClientsListResponse> = unsupported("listRemoteControlClients")

    suspend fun revokeRemoteControlClient(environmentId: String, clientId: String): Result<Unit> = unsupported("revokeRemoteControlClient")

    // ---- user verification ---------------------------------------------------
    suspend fun readUserVerificationStatus(): Result<UserVerificationStatusResponse> = unsupported("readUserVerificationStatus")

    /** Create or reuse a local credential; registering it with the backend is a separate step. */
    suspend fun enrollUserVerification(): Result<UserVerificationEnrollResponse> = unsupported("enrollUserVerification")

    /** Sign a challenge with the enrolled credential. */
    suspend fun verifyUserVerification(
        params: UserVerificationVerifyParams,
    ): Result<UserVerificationVerifyResponse> = unsupported("verifyUserVerification")

    /**
     * Abandon a verification RPC already in flight.
     *
     * [requestId] names the verification being cancelled, not this call — a completed verification
     * is not rolled back, so this is a "stop asking" rather than an undo.
     */
    suspend fun cancelUserVerification(requestId: String): Result<Unit> = unsupported("cancelUserVerification")

    suspend fun deleteUserVerification(): Result<Unit> = unsupported("deleteUserVerification")

    // ---- external agent config migration -------------------------------------
    suspend fun detectExternalAgentConfig(): Result<com.cy.codex.protocol.protocol.v2.ExternalAgentConfigDetectResponse> = unsupported("detectExternalAgentConfig")

    /** `externalAgentConfig/import`; the items come back from detect unchanged. */
    suspend fun importExternalAgentConfig(items: List<ExternalAgentConfigMigrationItem>): Result<String> = unsupported("importExternalAgentConfig")

    suspend fun readExternalAgentImportHistories(): Result<List<ExternalAgentConfigImportHistory>> = unsupported("readExternalAgentImportHistories")

    suspend fun recordExternalAgentImportHistory(
        params: com.cy.codex.protocol.protocol.v2.ExternalAgentConfigImportHistoryRecordParams,
    ): Result<String> = unsupported("recordExternalAgentImportHistory")

    // ---- review, search, hooks, feedback, diagnostics ------------------------
    suspend fun startReview(threadId: String, target: ReviewTarget): Result<ReviewStartResponse> = unsupported("startReview")

    /**
     * `rollout/compress` (experimental).
     *
     * Acknowledges the trigger, not completion: the server's background pass may skip while a
     * maintenance lock or cooldown is active.
     */
    suspend fun compressRollout(): Result<Unit> = unsupported("compressRollout")
    suspend fun fuzzyFileSearch(query: String, roots: List<String> = emptyList()): Result<List<FuzzyFileSearchResult>> = unsupported("fuzzyFileSearch")
    suspend fun startFuzzySearchSession(sessionId: String, roots: List<String> = emptyList()): Result<Unit> = unsupported("startFuzzySearchSession")
    suspend fun updateFuzzySearchSession(sessionId: String, query: String): Result<Unit> = unsupported("updateFuzzySearchSession")
    suspend fun stopFuzzySearchSession(sessionId: String): Result<Unit> = unsupported("stopFuzzySearchSession")
    suspend fun listHooks(): Result<List<HooksListEntry>> = unsupported("listHooks")
    suspend fun uploadFeedback(params: FeedbackUploadParams): Result<FeedbackUploadResponse> = unsupported("uploadFeedback")
    suspend fun readServerDiagnostics(): Result<ServerDiagnosticsResponse> = unsupported("readServerDiagnostics")

    // ---- windowsSandbox/… ------------------------------------------------------
    /**
     * `windowsSandbox/readiness`: whether the host can run the sandbox at all.
     *
     * Only meaningful on Windows, but declared on every platform: a client that ships the settings
     * page for it should be able to ask and get "not configured" rather than a missing method.
     */
    suspend fun windowsSandboxReadiness(): Result<WindowsSandboxReadinessResponse> = unsupported("windowsSandboxReadiness")

    /** `windowsSandbox/setupStart`: begin raising the sandbox; completion arrives as a notification. */
    suspend fun windowsSandboxSetupStart(
        mode: WindowsSandboxSetupMode,
        cwd: String? = null,
    ): Result<WindowsSandboxSetupStartResponse> = unsupported("windowsSandboxSetupStart")

    /** Connection state, surfaced as the "backend banner" the TUI shows on disconnect. */
    val connection: Flow<ConnectionState>

    suspend fun close()
}

/** One page of `thread/items/list`. */
data class ThreadItemsPage(
    val items: List<ThreadItem> = emptyList(),
    val nextCursor: String? = null,
)

/** One page of `thread/turns/list`. */
data class ThreadTurnsPage(
    val turns: List<com.cy.codex.protocol.protocol.v2.Turn> = emptyList(),
    val nextCursor: String? = null,
    /** Names the newest turn in the page; only meaningful when reversing direction. */
    val backwardsCursor: String? = null,
)

/** One `thread/searchOccurrences` hit. */
data class OccurrenceMatch(
    val turnId: String,
    val itemId: String,
    val snippet: String = "",
    /** Match range inside [snippet], in UTF-16 code units. */
    val start: Int = 0,
    val end: Int = 0,
    val turnCursor: String = "",
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Ready : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

/** Thread status helper: is the agent waiting on the human? */
val ThreadStatus.isWaitingOnUser: Boolean
    get() = this is ThreadStatus.Active && activeFlags.any {
        it == com.cy.codex.protocol.protocol.v2.ThreadActiveFlag.WaitingOnApproval ||
            it == com.cy.codex.protocol.protocol.v2.ThreadActiveFlag.WaitingOnUserInput
    }
