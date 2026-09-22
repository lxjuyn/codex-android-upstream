package com.cy.codex.protocol.protocol.v2

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * The long tail: search, review, attachments, terminals, projects, environments, remote control,
 * verification, external-agent migration and the diagnostics probe.
 *
 * Mirrors the matching `schema/typescript/v2/….ts` files. These are grouped in one file because each
 * family is a handful of small records, and splitting them further would produce files shorter than
 * their own headers — the domain files next door (`config.kt`, `fs.kt`, `queue.kt`, `catalog.kt`)
 * carry the families that are big enough to earn one.
 */

// ---------------------------------------------------------------------------------------------
// fuzzy file search
// ---------------------------------------------------------------------------------------------

/** `fuzzyFileSearch` — a one-shot search. */
data class FuzzyFileSearchParams(
    val query: String,
    val roots: List<String> = emptyList(),
    val cancellationToken: String? = null,
)

data class FuzzyFileSearchResponse(val files: List<FuzzyFileSearchResult> = emptyList())

data class FuzzyFileSearchResult(
    val path: String,
    /** `file` or `directory`. */
    val matchType: String = "file",
    val fileName: String = "",
    /** Root the match was found under; `fuzzyFileSearch` is always rooted. */
    val root: String = "",
    /** Match score; an unsigned integer upstream, not a fraction. */
    val score: Long = 0L,
    val indices: List<Int>? = null,
)

/** `fuzzyFileSearch/sessionStart|sessionUpdate|sessionStop` — the incremental variant. */
data class FuzzyFileSearchSessionStartParams(
    val sessionId: String,
    val roots: List<String> = emptyList(),
)

data class FuzzyFileSearchSessionUpdateParams(
    val sessionId: String,
    val query: String,
)

data class FuzzyFileSearchSessionStopParams(val sessionId: String)

/** `fuzzyFileSearch/sessionUpdated`. */
data class FuzzyFileSearchSessionUpdatedNotification(
    val sessionId: String,
    val query: String = "",
    val files: List<FuzzyFileSearchResult> = emptyList(),
)

/** `fuzzyFileSearch/sessionCompleted`. */
data class FuzzyFileSearchSessionCompletedNotification(val sessionId: String)

// ---------------------------------------------------------------------------------------------
// review
// ---------------------------------------------------------------------------------------------

/** What a review should look at. */
sealed interface ReviewTarget {
    /** Uncommitted changes in the working tree. */
    data object UncommittedChanges : ReviewTarget

    /** The changes a named branch introduced relative to its base. */
    data class BaseBranch(val branch: String) : ReviewTarget

    /** One commit. */
    data class Commit(val sha: String, val title: String? = null) : ReviewTarget

    /** Free-form instructions instead of a diff range. */
    data class Custom(val instructions: String) : ReviewTarget
}

data class ReviewStartParams(
    val threadId: String,
    val target: ReviewTarget = ReviewTarget.UncommittedChanges,
    val delivery: String? = null,
)

data class ReviewStartResponse(
    val reviewThreadId: String = "",
    val turn: Turn = Turn(id = ""),
)

/** `EnteredReviewModeNotification`-equivalent payload used by the transcript cell. */
data class ReviewModeEntered(
    val threadId: String,
    val target: String,
)

// ---------------------------------------------------------------------------------------------
// attachments, terminals, timeline, search
// ---------------------------------------------------------------------------------------------

data class ThreadAttachmentAddParams(
    val threadId: String,
    /** `image`, `file`, … — decides how the item is rendered. */
    val attachmentType: String,
    val identityKey: String,
    val payload: JsonElement = JsonNull,
)

/**
 * An attachment's `attachmentType` as far as this client distinguishes it.
 *
 * The wire field is a plain string, and a client only sets it: the server is the one that decides
 * how to interpret the payload, so anything unrecognised is carried through as [Other].
 */
enum class AttachmentType(val wire: String) {
    Image("image"),
    File("file"),
    ;

    companion object {
        fun fromWire(value: String?): AttachmentType =
            entries.firstOrNull { it.wire == value } ?: File
    }
}

/** The created or existing attachment. */
data class ThreadAttachment(
    val id: String,
    val attachmentType: String = "file",
    val identityKey: String = "",
    /** Type-specific body; opaque to this client. */
    val payload: JsonElement = JsonNull,
    val createdAt: Long = 0L,
)

/** `thread/attachment/add` — whether the call created the record or found the existing one. */
enum class ThreadAttachmentAddOutcome(val wire: String) {
    Created("created"),
    Existing("existing"),
    ;

    companion object {
        fun fromWire(value: String?): ThreadAttachmentAddOutcome =
            entries.firstOrNull { it.wire == value } ?: Existing
    }
}

data class ThreadAttachmentAddResponse(
    val outcome: ThreadAttachmentAddOutcome = ThreadAttachmentAddOutcome.Existing,
    val attachment: ThreadAttachment = ThreadAttachment(id = ""),
)

data class ThreadAttachmentListParams(
    val threadId: String,
    val cursor: String? = null,
    val limit: Int? = null,
)

data class ThreadAttachmentListResponse(
    val data: List<ThreadAttachment> = emptyList(),
    val nextCursor: String? = null,
)

data class ThreadAttachmentRemoveParams(
    val threadId: String,
    val attachmentType: String,
    val identityKey: String,
)

/** `thread/attachment/updated`. */
data class ThreadAttachmentUpdatedNotification(
    val threadId: String,
    val attachmentType: String = "",
    val identityKey: String = "",
    val attachmentId: String = "",
    val operation: ThreadAttachmentOperation = ThreadAttachmentOperation.Created,
)

enum class ThreadAttachmentOperation(val wire: String) {
    Created("created"),
    Deleted("deleted"),
    ;

    companion object {
        fun fromWire(value: String?): ThreadAttachmentOperation =
            entries.firstOrNull { it.wire == value } ?: Created
    }
}

/** `thread/backgroundTerminals/list`. */
data class ThreadBackgroundTerminalsListParams(
    val threadId: String,
    val cursor: String? = null,
    val limit: Int? = null,
)

/**
 * One long-lived terminal a turn left running.
 *
 * The resource numbers are optional because a terminal that has just started, or one whose process
 * the OS no longer reports, sends neither.
 */
data class ThreadBackgroundTerminal(
    val itemId: String,
    val processId: String,
    val command: String = "",
    val cwd: String = "",
    val osPid: Long? = null,
    val cpuPercent: Double? = null,
    val rssKb: Long? = null,
)

data class ThreadBackgroundTerminalsListResponse(
    val data: List<ThreadBackgroundTerminal> = emptyList(),
    val nextCursor: String? = null,
)

data class ThreadBackgroundTerminalsTerminateParams(
    val threadId: String,
    val processId: String,
)

data class ThreadBackgroundTerminalsTerminateResponse(val terminated: Boolean = false)

/** `thread/timeline/list` — the sparse "what happened when" index behind the scrubber. */
data class ThreadTimelineListParams(
    val threadId: String,
    val cursor: String? = null,
    val limit: Int? = null,
)

/**
 * One entry of `thread/timeline/list`, in canonical rollout order.
 *
 * A tagged union upstream rather than a flat record, so it is one here too: an item entry carries
 * the item, a turn boundary carries only its id and timing. [position] is the entry's index in the
 * rollout and is the only field every variant has.
 */
sealed interface TimelineEntry {
    val position: Long

    data class Item(
        override val position: Long,
        val turnId: String,
        val item: com.cy.codex.protocol.protocol.item.ThreadItem,
    ) : TimelineEntry

    data class Realtime(
        override val position: Long,
        val item: ThreadRealtimeItem,
    ) : TimelineEntry

    data class TurnStarted(
        override val position: Long,
        val turnId: String,
        val startedAt: Long? = null,
    ) : TimelineEntry

    data class TurnCompleted(
        override val position: Long,
        val turnId: String,
        val status: TurnStatus,
        val error: String? = null,
        val startedAt: Long? = null,
        val completedAt: Long? = null,
        val durationMs: Long? = null,
    ) : TimelineEntry
}

/** `thread/timeline/list` response envelope. */
data class ThreadTimelineListResponse(
    val data: List<TimelineEntry> = emptyList(),
    val nextCursor: String? = null,
    val activeRealtimeSessionAtPageStart: String? = null,
)

/**
 * One durable realtime fact in the timeline.
 *
 * The wire type flattens its tagged content into the item, so the variant payloads are optional
 * fields here: `type` is the discriminant, and which of [role]/[text]/[turnId]/[itemId]/[outcome]
 * are set follows from it.
 */
data class ThreadRealtimeItem(
    val id: String,
    val realtimeSessionId: String = "",
    val type: String = "",
    val role: String? = null,
    val text: String? = null,
    val turnId: String? = null,
    val itemId: String? = null,
    val outcome: String? = null,
)

/** `thread/search` — find threads by text. */
data class ThreadSearchParams(
    val searchTerm: String,
    val cursor: String? = null,
    val limit: Int? = null,
    val archived: Boolean? = null,
    val sortKey: String? = null,
    val sortDirection: String? = null,
    val sourceKinds: List<String>? = null,
)

/** `thread/searchOccurrences` — find matches *inside* one thread. */
data class ThreadSearchOccurrencesParams(
    val threadId: String,
    val searchTerm: String,
    val cursor: String? = null,
    val limit: Int? = null,
)

/** UTF-16 code-unit range within a snippet. */
data class ThreadSearchTextRange(val start: Int = 0, val end: Int = 0)

data class ThreadSearchOccurrence(
    val turnId: String,
    val itemId: String,
    val snippet: String = "",
    /** Match range within [snippet], in UTF-16 code units. */
    val snippetMatchRange: ThreadSearchTextRange = ThreadSearchTextRange(),
    /** Opaque cursor `thread/turns/list` accepts for the turn this occurrence is in. */
    val turnCursor: String = "",
)

data class ThreadSearchOccurrencesResponse(
    val data: List<ThreadSearchOccurrence> = emptyList(),
    val nextCursor: String? = null,
)

// ---------------------------------------------------------------------------------------------
// thread metadata / settings / memory mode
// ---------------------------------------------------------------------------------------------

data class ThreadMetadataUpdateParams(
    val threadId: String,
    val projectId: String? = null,
    val daybreakEnabled: Boolean? = null,
)

/** `thread/settings/update` — the per-thread half of the settings the composer changes. */
data class ThreadSettingsUpdateParams(
    val threadId: String,
    val model: String? = null,
    val effort: ReasoningEffort? = null,
    val summary: String? = null,
    val approvalPolicy: AskForApproval? = null,
    val approvalsReviewer: ApprovalsReviewer? = null,
    val sandboxPolicy: SandboxPolicy? = null,
    val permissions: String? = null,
    val collaborationMode: CollaborationMode? = null,
    val personality: Personality? = null,
    val serviceTier: String? = null,
    val cwd: String? = null,
    val disabledPluginIds: List<String>? = null,
    val multiAgentMode: String? = null,
)

/** `turn/settings/update` — the same knobs, scoped to one turn. */
data class TurnSettingsUpdateParams(
    val threadId: String,
    val turnId: String,
    val model: String? = null,
    val effort: ReasoningEffort? = null,
    val summary: String? = null,
    val approvalsReviewer: ApprovalsReviewer? = null,
    val serviceTier: String? = null,
)

/** Mirrors upstream `ThreadMemoryMode`: the server has two modes, not three. */
enum class ThreadMemoryMode(val wire: String) {
    Enabled("enabled"),
    Disabled("disabled"),
    ;

    companion object {
        fun fromWire(value: String?): ThreadMemoryMode =
            entries.firstOrNull { it.wire == value } ?: Disabled
    }
}

data class ThreadMemoryModeSetParams(
    val threadId: String,
    val mode: ThreadMemoryMode,
)

/**
 * `thread/increment_elicitation` and `thread/decrement_elicitation`.
 *
 * Both answer with the counter *after* the change plus whether timeout accounting is currently
 * paused, which is what a status line reports while an open-form question is outstanding.
 */
data class ElicitationCountResponse(
    val count: Int = 0,
    val paused: Boolean = false,
)

/**
 * One chunk of microphone audio for `thread/realtime/appendAudio`.
 *
 * [data] is base64 PCM; the sample rate and channel count travel with it because the server does
 * not assume the client's capture format. [itemId] addresses the transcript item the audio belongs
 * to when the client already knows it, and [samplesPerChannel] lets the server size a partial
 * final chunk.
 */
data class ThreadRealtimeAudioChunk(
    val data: String,
    val numChannels: Int = 1,
    val sampleRate: Int = 24_000,
    val itemId: String? = null,
    val samplesPerChannel: Int? = null,
)

data class ThreadRealtimeAppendAudioParams(
    val threadId: String,
    val audio: ThreadRealtimeAudioChunk,
)

// ---------------------------------------------------------------------------------------------
// collaboration modes, diagnostics, feedback
// ---------------------------------------------------------------------------------------------

/** `collaborationMode/list` entry. */
data class CollaborationModeEntry(
    val id: String,
    val mode: CollaborationMode = CollaborationMode.Default,
    val name: String = "",
    val description: String = "",
    /** Whether the mode mutates the workspace; plan mode does not. */
    val readonly: Boolean = false,
)

data class CollaborationModeListResponse(val data: List<CollaborationModeEntry> = emptyList())

/** `server/diagnostics` — a content-free probe used by the connection banner. */
data class ServerDiagnosticsResponse(
    val process: ServerDiagnosticsProcess = ServerDiagnosticsProcess(),
    val gauges: List<ServerDiagnosticsGauge> = emptyList(),
)

/** The server process behind the connection: id plus the two memory readings it can report. */
data class ServerDiagnosticsProcess(
    val id: Long = 0L,
    val residentMemoryBytes: Long? = null,
    val physicalFootprintBytes: Long? = null,
)

data class ServerDiagnosticsGauge(
    val name: String,
    val value: Long = 0L,
)

/** `feedback/upload`. */
data class FeedbackUploadParams(
    val classification: String,
    val reason: String? = null,
    val threadId: String? = null,
    val includeLogs: Boolean = false,
    val extraLogFiles: List<String>? = null,
    val tags: Map<String, String>? = null,
)

/** `feedback/upload` response: which rollout the report was filed against. */
data class FeedbackUploadResponse(
    val threadId: String = "",
    val promptHash: String? = null,
)

// ---------------------------------------------------------------------------------------------
// projects and environments
// ---------------------------------------------------------------------------------------------

data class ProjectEntry(
    val id: String,
    val name: String,
    val path: String = "",
    val threadCount: Int = 0,
)

data class ProjectListResponse(val data: List<ProjectEntry> = emptyList())

data class ProjectReadResponse(val project: ProjectEntry = ProjectEntry(id = "", name = ""))

data class ProjectCreateParams(
    val name: String,
    val path: String,
)

data class ProjectUpdateParams(
    val projectId: String,
    val name: String? = null,
    val path: String? = null,
)

data class ProjectDeleteParams(val projectId: String)

data class ProjectMoveParams(
    val projectId: String,
    /** Where in the user's ordering the project should land. */
    val position: Int = 0,
)

data class ProjectImportParams(val path: String)

/**
 * An environment a loaded thread is bound to, independent of whether it is connected.
 *
 * `cwd` and `runtimeWorkspaceRoots` are file URIs on the wire; they are plain strings here because
 * nothing on this side parses them, only displays them.
 */
data class ThreadEnvironment(
    val environmentId: String,
    val cwd: String = "",
    val runtimeWorkspaceRoots: List<String> = emptyList(),
)

/** `environment/add` — register an exec server as an environment. */
data class EnvironmentAddParams(
    val environmentId: String,
    val execServerUrl: String,
    /** Null leaves the server's own connect timeout in place. */
    val connectTimeoutMs: Long? = null,
)

/** `environment/info` — one environment, named by id. There is no "list every environment" call. */
data class EnvironmentInfoParams(val environmentId: String)

data class EnvironmentShellInfo(
    /** Stable shell name, for example `zsh`, `bash`, `powershell`, `sh` or `cmd`. */
    val name: String = "",
    /** Target-native shell executable path or command name. */
    val path: String = "",
)

data class EnvironmentInfoResponse(
    val shell: EnvironmentShellInfo = EnvironmentShellInfo(),
    /** Default working directory the environment reported, as a canonical file URI. */
    val cwd: String? = null,
)

/** `environment/status` — inspect one environment without starting or recovering it. */
data class EnvironmentStatusParams(val environmentId: String)

/**
 * How an environment looks right now.
 *
 * [Disconnected] is not terminal: a later ordinary use may recover it, and this call deliberately
 * does not try. [Unknown] means the id is not configured at all, which is a different problem from
 * one that is merely down.
 */
enum class EnvironmentStatusKind(val wire: String) {
    Ready("ready"),
    Pending("pending"),
    Disconnected("disconnected"),
    Unknown("unknown"),
    ;

    companion object {
        fun fromWire(value: String?): EnvironmentStatusKind =
            entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

data class EnvironmentStatusResponse(
    val status: EnvironmentStatusKind = EnvironmentStatusKind.Unknown,
    /** Human-readable detail, present for `disconnected` and `unknown` only. */
    val error: String? = null,
)

/** `thread/environment/connected` and `thread/environment/disconnected`. */
data class EnvironmentConnectionNotification(
    val threadId: String,
    val environmentId: String,
)

// ---------------------------------------------------------------------------------------------
// remote control
// ---------------------------------------------------------------------------------------------

/** Where the remote-control link to this machine stands. */
enum class RemoteControlConnectionStatus(val wire: String) {
    Disabled("disabled"),
    Connecting("connecting"),
    Connected("connected"),
    Errored("errored"),
    ;

    companion object {
        fun fromWire(value: String?): RemoteControlConnectionStatus =
            entries.firstOrNull { it.wire == value } ?: Disabled
    }
}

data class RemoteControlEnableParams(
    /** Do not persist the setting; it lasts for this server process only. */
    val ephemeral: Boolean = false,
)

data class RemoteControlDisableParams(val ephemeral: Boolean = false)

/**
 * The four fields every remote-control answer carries.
 *
 * `remoteControl/enable`, `disable`, `status/read` and `status/changed` all report the same tuple,
 * which is why they share one shape here rather than four identical classes: the client stores it
 * once and every page reads the same value.
 */
data class RemoteControlStatus(
    val status: RemoteControlConnectionStatus = RemoteControlConnectionStatus.Disabled,
    val serverName: String = "",
    val installationId: String = "",
    val environmentId: String? = null,
)

data class RemoteControlEnableResponse(val status: RemoteControlStatus = RemoteControlStatus())
data class RemoteControlDisableResponse(val status: RemoteControlStatus = RemoteControlStatus())
data class RemoteControlStatusReadResponse(val status: RemoteControlStatus = RemoteControlStatus())

/** `remoteControl/pairing/start`; `manualCode` asks for a short code a human can type. */
data class RemoteControlPairingStartParams(val manualCode: Boolean = false)

data class RemoteControlPairingStartResponse(
    val pairingCode: String = "",
    /** The short code, present only when the request asked for one. */
    val manualPairingCode: String? = null,
    val environmentId: String = "",
    val expiresAt: Long = 0L,
)

/** Poll a pairing attempt; either code form identifies it. */
data class RemoteControlPairingStatusParams(
    val pairingCode: String? = null,
    val manualPairingCode: String? = null,
)

data class RemoteControlPairingStatusResponse(val claimed: Boolean = false)

data class RemoteControlClientsListParams(
    val environmentId: String,
    val cursor: String? = null,
    val limit: Int? = null,
    val order: RemoteControlClientsListOrder? = null,
)

enum class RemoteControlClientsListOrder(val wire: String) {
    Asc("asc"),
    Desc("desc"),
}

data class RemoteControlClientsListResponse(
    val data: List<RemoteControlClient> = emptyList(),
    val nextCursor: String? = null,
)

/** One device that has paired with this machine. */
data class RemoteControlClient(
    val clientId: String,
    val displayName: String? = null,
    val deviceType: String? = null,
    val platform: String? = null,
    val osVersion: String? = null,
    val deviceModel: String? = null,
    val appVersion: String? = null,
    val lastSeenAt: Long? = null,
)

data class RemoteControlClientsRevokeParams(
    val environmentId: String,
    val clientId: String,
)

/** `remoteControl/status/changed`. */
data class RemoteControlStatusChangedNotification(
    val status: RemoteControlStatus = RemoteControlStatus(),
)

// ---------------------------------------------------------------------------------------------
// user verification
// ---------------------------------------------------------------------------------------------

/** A signature over the exact decoded challenge. The verifier validates and consumes it. */
data class UserVerificationProof(
    val credentialId: String = "",
    /** Unpadded base64url DER ECDSA signature using P-256 and SHA-256. */
    val signature: String = "",
)

enum class UserVerificationUnavailableReason(val wire: String) {
    CredentialMissing("credentialMissing"),
    BiometricsUnavailable("biometricsUnavailable"),
    ProviderUnavailable("providerUnavailable"),
}

enum class UserVerificationCancellationReason(val wire: String) {
    UserCancelled("userCancelled"),
    Interrupted("interrupted"),
}

enum class UserVerificationFailureReason(val wire: String) {
    AuthenticationFailed("authenticationFailed"),
    Timeout("timeout"),
    ProviderError("providerError"),
    ServiceError("serviceError"),
}

/**
 * The closed set of ways verification can fail.
 *
 * Closed on purpose: the native layer's diagnostic payloads must not cross this boundary, so the
 * server maps them onto these four categories and the client can switch on them exhaustively.
 */
sealed interface UserVerificationErrorDetails {
    data class InvalidRequest(val reason: String = "invalidParams") : UserVerificationErrorDetails
    data class Unavailable(val reason: UserVerificationUnavailableReason) : UserVerificationErrorDetails
    data class Cancelled(val reason: UserVerificationCancellationReason) : UserVerificationErrorDetails
    data class Failed(val reason: UserVerificationFailureReason) : UserVerificationErrorDetails
}

/** The `error` object `userVerification/…` failures carry inside the JSON-RPC envelope. */
data class UserVerificationRpcError(
    val code: Long = 0L,
    val message: String = "",
    val data: UserVerificationErrorDetails? = null,
)

/** Local readiness only; this neither prompts nor queries server registration. */
data class UserVerificationStatusResponse(
    val credentialId: String? = null,
    val unavailableReason: UserVerificationUnavailableReason? = null,
    val unavailableMessage: String? = null,
)

/**
 * Metadata for a credential this client just created.
 *
 * Nothing is registered by this call — the caller completes backend registration afterwards, and
 * an older app-server may omit both metadata fields, so a caller has to check them.
 */
data class UserVerificationEnrollResponse(
    val credentialId: String = "",
    val algorithm: String? = null,
    /** Unpadded base64url of the SubjectPublicKeyInfo DER encoding. */
    val publicKey: String? = null,
)

/** Local signing primitive, independent of any pending elicitation. */
data class UserVerificationVerifyParams(
    /** Unpadded base64url encoding of 1 to 4096 challenge bytes. */
    val challenge: String,
    /** Display context the UI has already approved; 1 to 256 UTF-8 bytes. */
    val title: String = "",
    /** Additional display context; at most 4096 UTF-8 bytes. */
    val description: String = "",
)

data class UserVerificationVerifyResponse(val proof: UserVerificationProof = UserVerificationProof())

/**
 * Cancels a native verification RPC on this connection, not an elicitation.
 *
 * The id names the *verification* being cancelled and must differ from the id of this cancellation
 * call itself.
 */
data class UserVerificationCancelParams(val requestId: String)

/** `attestation/generate` — the client is asked to produce an attestation token. */
data class AttestationGenerateParams(val nonce: String = "")

data class AttestationGenerateResponse(val token: String = "")

// ---------------------------------------------------------------------------------------------
// external agent config migration
// ---------------------------------------------------------------------------------------------

/**
 * One thing a competing agent's config would bring over.
 *
 * [itemType] uses the protocol's uppercase wire values (`AGENTS_MD`, `CONFIG`, `SKILLS`, …).
 * `selected` is absent upstream: every detected item is passed back by the client, which
 * is why an import carries whole items rather than ids.
 */
data class ExternalAgentConfigMigrationItem(
    val itemType: String,
    val description: String,
    /** Null or empty means home-scoped; non-empty means repo-scoped. */
    val cwd: String? = null,
    val details: MigrationDetails? = null,
)

/** The per-type detail of one migration item; every list is independently optional upstream. */
data class MigrationDetails(
    val plugins: List<PluginsMigration> = emptyList(),
    val skills: List<NamedMigration> = emptyList(),
    val sessions: List<SessionMigration> = emptyList(),
    val mcpServers: List<NamedMigration> = emptyList(),
    val hooks: List<NamedMigration> = emptyList(),
    val subagents: List<NamedMigration> = emptyList(),
    val commands: List<NamedMigration> = emptyList(),
    val memory: List<String> = emptyList(),
)

data class PluginsMigration(
    val marketplaceName: String = "",
    val pluginNames: List<String> = emptyList(),
)

/** `{name}` detail shared by skills, MCP servers, hooks, subagents and commands. */
data class NamedMigration(val name: String = "")

data class SessionMigration(
    val path: String = "",
    val cwd: String = "",
    val title: String? = null,
)

data class ExternalAgentConfigDetectParams(
    val includeHome: Boolean = false,
    val cwds: List<String>? = null,
    val maxSessionAgeDays: Int? = null,
    val maxSessions: Int? = null,
    val source: String? = null,
    val migrationSource: String? = null,
)

data class ExternalAgentConfigDetectResponse(
    val items: List<ExternalAgentConfigMigrationItem> = emptyList(),
    val connectors: List<ExternalAgentDetectedConnectorCandidate> = emptyList(),
)

/** A connector the migration source detected, with how many sessions referenced it. */
data class ExternalAgentDetectedConnectorCandidate(
    val name: String,
    val sessionCount: Long = 0L,
    val source: String = "",
)

data class ExternalAgentConfigImportParams(
    val migrationItems: List<ExternalAgentConfigMigrationItem> = emptyList(),
    val source: String? = null,
    val providerId: String? = null,
    val migrationSource: String? = null,
)

data class ExternalAgentConfigImportResponse(val importId: String = "")

/** One item type's successes and failures inside a recorded import. */
data class ExternalAgentConfigImportTypeResult(
    val itemType: String,
    val successes: List<ExternalAgentConfigImportSuccess> = emptyList(),
    val failures: List<ExternalAgentConfigImportFailure> = emptyList(),
)

data class ExternalAgentConfigImportSuccess(
    val itemType: String = "",
    val cwd: String? = null,
    val source: String? = null,
    val target: String? = null,
    val title: String? = null,
)

data class ExternalAgentConfigImportFailure(
    val itemType: String = "",
    val errorType: String? = null,
    val subErrorType: String? = null,
    val failureStage: String = "",
    val message: String = "",
    val cwd: String? = null,
    val source: String? = null,
)

data class ExternalAgentConfigImportHistory(
    val importId: String,
    val providerId: String? = null,
    val completedAtMs: Long = 0L,
    val successes: List<ExternalAgentConfigImportSuccess> = emptyList(),
    val failures: List<ExternalAgentConfigImportFailure> = emptyList(),
)

data class ExternalAgentConfigImportHistoriesReadResponse(
    val data: List<ExternalAgentConfigImportHistory> = emptyList(),
)

/** `externalAgentConfig/import/recordHistory` params. */
data class ExternalAgentConfigImportHistoryRecordParams(
    /** Opaque provider identifier for the externally completed import. */
    val providerId: String,
    val itemTypeResults: List<ExternalAgentConfigImportTypeResult> = emptyList(),
)

data class ExternalAgentConfigImportHistoryRecordResponse(val importId: String = "")

// ---------------------------------------------------------------------------------------------
// windows sandbox
// ---------------------------------------------------------------------------------------------

/**
 * Whether the Windows sandbox is usable on this host.
 *
 * Only meaningful on a Windows host; carried on every platform so the registry stays complete and
 * a client can decode the response wherever it lands.
 */
enum class WindowsSandboxReadiness(val wire: String) {
    Ready("ready"),
    NotConfigured("notConfigured"),
    UpdateRequired("updateRequired"),
}

/** The two ways `windowsSandbox/setupStart` can raise a sandbox. */
enum class WindowsSandboxSetupMode(val wire: String) {
    Elevated("elevated"),
    Unelevated("unelevated"),
}

data class WindowsSandboxReadinessResponse(
    val status: WindowsSandboxReadiness = WindowsSandboxReadiness.NotConfigured,
)

data class WindowsSandboxSetupStartParams(
    val mode: WindowsSandboxSetupMode = WindowsSandboxSetupMode.Elevated,
    val cwd: String? = null,
)

data class WindowsSandboxSetupStartResponse(val started: Boolean = false)

data class WindowsSandboxSetupCompletedNotification(
    val mode: WindowsSandboxSetupMode = WindowsSandboxSetupMode.Elevated,
    val success: Boolean = true,
    val error: String? = null,
)

// ---------------------------------------------------------------------------------------------
// model provider recovery and moderation
// ---------------------------------------------------------------------------------------------

data class ModelProviderAuthRecoveryNotification(
    val provider: String,
    val detail: String? = null,
)

data class ModelVerificationNotification(
    val threadId: String?,
    val model: String = "",
    val verified: Boolean = true,
)

/** `model/safetyBuffering/updated` — the server paused or resumed buffering a turn. */
data class ModelSafetyBufferingUpdatedNotification(
    val threadId: String,
    val turnId: String,
    val model: String,
    val reasons: List<String> = emptyList(),
    val useCases: List<String> = emptyList(),
    val showBufferingUi: Boolean = false,
    val fasterModel: String? = null,
)

data class TurnModerationMetadataNotification(
    val threadId: String,
    val turnId: String,
    val detail: String? = null,
)

data class StrictReviewRequiredNotification(
    val threadId: String,
    val turnId: String? = null,
    val reason: String? = null,
)

/**
 * `item/autoApprovalReview/started` and `.../completed`.
 *
 * The lifecycle part is `review` (status and rationale); `action` is the request the subagent is
 * judging and is kept raw because the review action union has seven shapes and only a summary of it
 * is ever displayed.
 */
data class GuardianApprovalReviewNotification(
    val threadId: String,
    val turnId: String,
    val reviewId: String = "",
    val status: String = "",
    val itemId: String = "",
    val rationale: String? = null,
    val riskLevel: String? = null,
    val action: JsonElement? = null,
)

data class FileChangePatchUpdatedNotification(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val changes: List<FileUpdateChange> = emptyList(),
)

/**
 * One hook execution, from `hook/started` and `hook/completed`.
 *
 * `status` is `running`, `completed`, `failed`, `blocked` or `stopped`; the started notification
 * always carries `running`, which is why both notifications share this shape.
 */
data class HookRunSummary(
    val id: String,
    val eventName: String = "",
    val handlerType: String = "",
    val executionMode: String = "",
    val scope: String = "",
    val sourcePath: String = "",
    val source: String = "",
    val displayOrder: Long = 0L,
    val status: String = "running",
    val statusMessage: String? = null,
    val startedAt: Long = 0L,
    val completedAt: Long? = null,
    val durationMs: Long? = null,
    val entries: List<HookOutputEntry> = emptyList(),
) {
    val failed: Boolean get() = status == "failed" || status == "blocked" || status == "stopped"
}

data class HookOutputEntry(
    /** `warning`, `stop`, `feedback`, `context` or `error`. */
    val kind: String = "",
    val text: String = "",
)

/** `hook/started`. */
data class HookStartedNotification(
    val threadId: String,
    val turnId: String? = null,
    val run: HookRunSummary = HookRunSummary(id = ""),
)

/** `hook/completed`. */
data class HookCompletedNotification(
    val threadId: String,
    val turnId: String? = null,
    val run: HookRunSummary = HookRunSummary(id = ""),
)

/** `project/changed` and `thread/project/updated`. */
data class ProjectChangedNotification(val projectId: String? = null)

data class ThreadProjectUpdatedNotification(
    val threadId: String,
    val projectId: String? = null,
)

/** `deprecationNotice` and `configWarning` share [DeprecationNoticeNotification]/[ConfigWarningNotification]. */
data class ModelRouterMetadata(
    val requested: String = "",
    val served: String = "",
)
