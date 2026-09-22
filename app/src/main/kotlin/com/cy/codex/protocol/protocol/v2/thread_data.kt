package com.cy.codex.protocol.protocol.v2

import com.cy.codex.protocol.protocol.item.ThreadItem
import kotlinx.serialization.json.JsonElement

/**
 * One row of `thread/list`: metadata only, no transcript.
 *
 * Mirrors `v2::Thread`. The client carries the fields its surfaces render and drops the rest
 * (`source`, `turns`, `environments`, `extra`, …), which the schema allows: a Kotlin type may have
 * fewer fields than upstream, but a field it does carry must use the upstream wire name.
 *
 * `createdAt`/`updatedAt`/`recencyAt` arrive as Unix **seconds** and are decoded to epoch millis,
 * because that is what the sidebar's relative-time formatting reads.
 */
data class Thread(
    /** Identifier for this thread. Codex-generated thread IDs are UUIDv7. */
    val id: String,
    /** Usually the first user message in the thread, if available. */
    val preview: String,
    /** Model provider used for this thread, for example `openai`. */
    val modelProvider: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Working directory captured for the thread. */
    val cwd: String,
    val status: ThreadStatus,
    /** Version of the CLI that created the thread. */
    val cliVersion: String,
    /** Whether the thread is ephemeral and should not be materialized on disk. */
    val ephemeral: Boolean,
    /** Canonical project assignment owned by app-server, if any. Nullable, but always present. */
    val projectId: String?,
    /** Session id shared by threads that belong to the same session tree. */
    val sessionId: String,
    /** Optional user-facing thread title. */
    val name: String? = null,
    /** Source thread id when this thread was created by forking another thread. */
    val forkedFromId: String? = null,
    /** Optional Git metadata captured when the thread was created. */
    val gitInfo: GitInfo? = null,
    /** Current configured model when loaded, otherwise the latest persisted model. */
    val model: String? = null,
    /** Current configured reasoning effort when loaded, otherwise the latest persisted effort. */
    val reasoningEffort: ReasoningEffort? = null,
    /** `[UNSTABLE]` Path to the thread on disk. */
    val path: String? = null,
    /** Persisted thread history contract selected when this thread was created. */
    val historyMode: String = "legacy",
    /** Unix timestamp (in seconds) used for recency ordering. */
    val recencyAt: Long? = null,
    /** Originator recorded when the thread was created. */
    val originator: String? = null,
    /** Set when this thread is a sub-agent of another thread. */
    val parentThreadId: String? = null,
    /** Whether the server accepts direct turn input for this loaded thread; `null` when unknown. */
    val canAcceptDirectInput: Boolean? = null,
    /** Random unique nickname assigned to an AgentControl-spawned sub-agent. */
    val agentNickname: String? = null,
    /** Role assigned to an AgentControl-spawned sub-agent. */
    val agentRole: String? = null,
)

/** Optional Git metadata captured when a thread was created. */
data class GitInfo(
    val branch: String? = null,
    val originUrl: String? = null,
    val sha: String? = null,
)

sealed interface ThreadStatus {
    data object Idle : ThreadStatus

    data object NotLoaded : ThreadStatus

    data class Active(val activeFlags: List<ThreadActiveFlag> = emptyList()) : ThreadStatus

    data class SystemError(val message: String) : ThreadStatus
}

enum class ThreadActiveFlag(val wire: String) {
    WaitingOnApproval("waitingOnApproval"),
    WaitingOnUserInput("waitingOnUserInput"),
}

/** `thread/read` response: the full item list of one thread. */
data class ThreadReadResponse(
    val thread: Thread,
    val items: List<ThreadItem> = emptyList(),
    val turns: List<Turn> = emptyList(),
)

/** One agent turn: a user message plus everything the agent did in response. */
/**
 * `TurnError.misalignment`: the safety system stopped the turn because it could not confirm the
 * agent was following the user's intent. Mirrors `v2/thread_data.rs`.
 */
data class MisalignmentErrorDetails(
    val errorType: String? = null,
    val detailedExplanation: String? = null,
    val steer: MisalignmentSteer? = null,
)

/** The instruction submitted again when the user confirms continuing after a misalignment stop. */
data class MisalignmentSteer(val message: String)

data class Turn(
    val id: String,
    val items: List<ThreadItem> = emptyList(),
    /** How much of [items] this payload loaded. Mirrors `TurnItemsView`; defaults to `full`. */
    val itemsView: TurnItemsView = TurnItemsView.Full,
    val status: TurnStatus = TurnStatus.Completed,
    val startedAt: Long = 0L,
    val completedAt: Long? = null,
    /** Wall-clock duration the server measured, when it reports one. */
    val durationMs: Long? = null,
    val usage: ThreadTokenUsage? = null,
)

enum class TurnStatus(val wire: String) {
    InProgress("inProgress"),
    Completed("completed"),
    Interrupted("interrupted"),
    Failed("failed"),
    ;

    companion object {
        fun fromWire(value: String): TurnStatus =
            entries.firstOrNull { it.wire == value } ?: Completed
    }
}

/** One bucket of token accounting. Mirrors `TokenUsageBreakdown`. */
data class TokenUsageBreakdown(
    val totalTokens: Long,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val reasoningOutputTokens: Long,
    /** Present in newer servers; older payloads simply omit it. */
    val cacheWriteInputTokens: Long = 0,
) {
    companion object {
        val Empty = TokenUsageBreakdown(0, 0, 0, 0, 0)
    }
}

/**
 * Token accounting for one thread, straight from `thread/tokenUsage/updated`.
 *
 * Mirrors `v2::ThreadTokenUsage`: [total] is the lifetime counter, [last] the most recent turn, and
 * [modelContextWindow] the denominator for the context meter.
 */
data class ThreadTokenUsage(
    val total: TokenUsageBreakdown,
    val last: TokenUsageBreakdown,
    val modelContextWindow: Long? = null,
) {
    val usedFraction: Float
        get() = modelContextWindow?.takeIf { it > 0 }?.let {
            (total.totalTokens.toFloat() / it.toFloat()).coerceIn(0f, 1f)
        } ?: 0f

    companion object {
        val Empty = ThreadTokenUsage(TokenUsageBreakdown.Empty, TokenUsageBreakdown.Empty)
    }
}

/** One `thread/list` sweep: active rows plus, when the scope asked for them, archived rows. */
data class ThreadListing(
    val threads: List<Thread> = emptyList(),
    /** Ids of the rows that came from the archived half of the listing. */
    val archivedIds: Set<String> = emptySet(),
)

/** `thread/list` params. Mirrors `v2::ThreadListParams`; every field is optional upstream. */
data class ThreadListParams(
    val archived: Boolean? = null,
    val cursor: String? = null,
    val cwd: String? = null,
    val limit: Int? = null,
    val modelProviders: List<String>? = null,
    val originators: List<String>? = null,
    val searchTerm: String? = null,
    val sectionId: String? = null,
    val sortDirection: SortDirection? = null,
    val sortKey: ThreadSortKey? = null,
    val sourceKinds: List<String>? = null,
    val useStateDbOnly: Boolean? = null,
    /** Experimental `thread/list.parentThreadId`: direct children of one thread. */
    val parentThreadId: String? = null,
    /** Experimental `thread/list.ancestorThreadId`: spawned descendants at any depth. */
    val ancestorThreadId: String? = null,
)

enum class SortDirection(val wire: String) {
    Asc("asc"),
    Desc("desc"),
}

enum class ThreadSortKey(val wire: String) {
    CreatedAt("created_at"),
    UpdatedAt("updated_at"),
    RecencyAt("recency_at"),
    SectionPosition("section_position"),
}

/** `thread/items/list` params. */
data class ThreadItemsListParams(
    val threadId: String,
    val cursor: String? = null,
    val limit: Int? = null,
    val sortDirection: SortDirection? = null,
    val turnId: String? = null,
)

/** `thread/turns/list` params. */
data class ThreadTurnsListParams(
    val threadId: String,
    val cursor: String? = null,
    val itemsView: TurnItemsView? = null,
    val limit: Int? = null,
    val sortDirection: SortDirection? = null,
)

/**
 * `thread/resume` params.
 *
 * `excludeTurns` keeps the response metadata-only, which is what makes a bounded first screen
 * possible: the client asks for [initialTurnsPage] instead of letting the server replay the whole
 * rollout. Both fields exist upstream (`v2::ThreadResumeParams`).
 */
data class ThreadResumeParams(
    val threadId: String,
    /** When true, do not populate `thread.turns`; hydrate with pages instead. */
    val excludeTurns: Boolean? = null,
    /** Experimental `thread/resume.initialTurnsPage`: embed one bounded turns page. */
    val initialTurnsPage: ThreadResumeInitialTurnsPageParams? = null,
)

/** Experimental `thread/resume.initialTurnsPage`; defaults to descending order and `summary`. */
data class ThreadResumeInitialTurnsPageParams(
    val limit: Int? = null,
    val sortDirection: SortDirection? = null,
    val itemsView: TurnItemsView? = null,
)

/**
 * One `thread/turns/list` page. Mirrors `TurnsPage`, which `thread/resume.initialTurnsPage` also
 * uses. [backwardsCursor] names the newest row in the page and is only meaningful when reversing
 * direction; older pages follow [nextCursor].
 */
data class TurnsPage(
    val data: List<Turn> = emptyList(),
    val nextCursor: String? = null,
    val backwardsCursor: String? = null,
) {
    val turns: List<Turn> get() = data
}

/** Which item payloads `thread/turns/list` inlines; mirrors `TurnItemsView`. */
enum class TurnItemsView(val wire: String) {
    NotLoaded("notLoaded"),
    Summary("summary"),
    Full("full"),
}

/** `thread/read` params. */
data class ThreadReadParams(
    val threadId: String,
    val includeTurns: Boolean? = null,
)

/**
 * `thread/start` params.
 *
 * Mirrors `v2::ThreadStartParams`. The client carries the knobs its settings surfaces expose; the
 * remaining upstream fields (`baseInstructions`, `config`, `serviceName`, `threadSource`, …) are
 * dropped rather than invented.
 */
data class ThreadStartParams(
    val cwd: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val approvalPolicy: AskForApproval? = null,
    val approvalsReviewer: ApprovalsReviewer? = null,
    val sandbox: SandboxPolicy? = null,
    val personality: Personality? = null,
    val serviceTier: String? = null,
    val ephemeral: Boolean? = null,
    val developerInstructions: String? = null,
    val baseInstructions: String? = null,
    val sessionStartSource: String? = null,
    /** Per-thread config overrides, as raw JSON: the server owns the key space. */
    val config: JsonElement? = null,
    /** Experimental `thread/start.dynamicTools`: client-hosted tool specs the model may call. */
    val dynamicTools: JsonElement? = null,
)

/**
 * `thread/fork`: copy a thread, optionally as an ephemeral side conversation.
 *
 * Every field but [threadId] overrides the parent's setting for the child only, which is what the
 * side-conversation flow uses to pin `ephemeral` and append developer instructions.
 */
data class ThreadForkParams(
    val threadId: String,
    val lastTurnId: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val cwd: String? = null,
    val approvalPolicy: AskForApproval? = null,
    val approvalsReviewer: ApprovalsReviewer? = null,
    val sandbox: SandboxPolicy? = null,
    val serviceTier: String? = null,
    val config: JsonElement? = null,
    val baseInstructions: String? = null,
    val developerInstructions: String? = null,
    val ephemeral: Boolean? = null,
    val excludeTurns: Boolean? = null,
)

/** `threadSection/…`: a user-defined group of threads in the sidebar. */
data class ThreadSection(
    val id: String,
    val name: String,
)

/**
 * `model/list` entry.
 *
 * Mirrors `v2::Model`. `contextWindow` is deliberately absent: the wire has no such field, the
 * session's own window arrives through [ThreadTokenUsage.modelContextWindow].
 */
data class ModelPreset(
    val id: String,
    val model: String,
    val displayName: String,
    val description: String,
    val defaultReasoningEffort: ReasoningEffort,
    val supportedReasoningEfforts: List<ReasoningEffort>,
    val isDefault: Boolean,
    val hidden: Boolean,
    val defaultServiceTier: String? = null,
    val serviceTiers: List<ModelServiceTier> = emptyList(),
    val inputModalities: List<InputModality> = emptyList(),
)

/** One selectable speed tier of a model. Mirrors `ModelServiceTier`. */
data class ModelServiceTier(
    val id: String,
    val name: String,
    val description: String,
)

/** Input kinds a model accepts. Mirrors `InputModality`. */
enum class InputModality(val wire: String) {
    Text("text"),
    Image("image"),
    Audio("audio"),
}

/** `permissionProfile/list` entry. */
data class PermissionProfileEntry(
    val id: String,
    val name: String,
    val description: String = "",
    val active: Boolean = false,
)

/** `experimentalFeature/list` entry. */
data class ExperimentalFeatureEntry(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = false,
    val stage: String = "beta",
)

/** `mcpServerStatus/list` entry. */
data class McpServerStatusEntry(
    val name: String,
    val status: McpServerConnectionStatus = McpServerConnectionStatus.NotStarted,
    val tools: Int = 0,
    val resources: Int = 0,
    val error: String? = null,
    /** How the server authenticates; `null` when an older server did not report it. */
    val authStatus: McpAuthStatus? = null,
)

/** Mirrors upstream `McpServerConnectionStatus`. */
enum class McpServerConnectionStatus(val wire: String) {
    NotStarted("notStarted"),
    Starting("starting"),
    Connected("connected"),
    AuthenticationRequired("authenticationRequired"),
    Failed("failed"),
    Cancelled("cancelled"),
    Disabled("disabled"),
}

/**
 * Mirrors upstream `McpAuthStatus`.
 *
 * This is what decides whether a "Log in" affordance can work at all: a server on a bearer token
 * or an unsupported mode has no OAuth flow to start.
 */
enum class McpAuthStatus(val wire: String) {
    Unknown("unknown"),
    Unsupported("unsupported"),
    NotLoggedIn("notLoggedIn"),
    BearerToken("bearerToken"),
    OAuth("oAuth"),
    ;

    companion object {
        fun fromWire(value: String?): McpAuthStatus? =
            value?.let { wire -> entries.firstOrNull { it.wire == wire } }
    }
}

/** Mirrors upstream `McpServerStartupState`: the phases of the startup broadcast. */
enum class McpServerStartupState(val wire: String) {
    Starting("starting"),
    Ready("ready"),
    Failed("failed"),
    Cancelled("cancelled"),
    ;

    companion object {
        fun fromWire(value: String?): McpServerStartupState =
            entries.firstOrNull { it.wire == value } ?: Starting
    }
}

/** `skills/list` entry. */
data class SkillEntry(
    val id: String,
    val name: String,
    val description: String = "",
    val path: String = "",
    val enabled: Boolean = true,
    val scope: SkillScope = SkillScope.User,
)

/** Mirrors upstream `SkillScope`; a repo-scoped skill travels as `repo`, not `project`. */
enum class SkillScope(val wire: String) {
    User("user"),
    Project("repo"),
    System("system"),
    Admin("admin"),
    ;

    companion object {
        fun fromWire(value: String?): SkillScope = when (value) {
            // `project` is a legacy spelling the server no longer sends.
            "project" -> Project
            else -> entries.firstOrNull { it.wire == value } ?: User
        }
    }
}

/** `plugin/list` entry: the parts of `PluginSummary` this client renders. */
data class PluginEntry(
    val id: String,
    val name: String,
    val description: String = "",
    val installed: Boolean = false,
    val version: String = "",
    val marketplace: String = "",
    /** Whether the plugin is currently active in the config; toggled via `config/value/write`. */
    val enabled: Boolean = true,
    /** Backend remote plugin identifier, when the plugin service published one. */
    val remotePluginId: String? = null,
    /** Remote sharing context, when this account has shared the plugin. */
    val shareContext: PluginShareContext? = null,
)

/** `app/list` entry — connectors exposed by the account. */
data class AppInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val installed: Boolean = false,
)

/**
 * One hook configured for a working directory.
 *
 * Mirrors `v2::HookMetadata`. The handler is a flattened tagged union upstream (`handlerType` plus
 * that variant's fields), so the variant fields are carried as optionals here and [handlerType]
 * says which are meaningful.
 */
data class HookMetadata(
    /** Stable identity of the hook inside its config source. */
    val key: String,
    val eventName: String,
    /** `command`, `mcpTool`, `prompt` or `agent`. */
    val handlerType: String = "",
    val command: String? = null,
    val async: Boolean = false,
    val server: String? = null,
    val tool: String? = null,
    val matcher: String? = null,
    val timeoutSec: Long = 0L,
    val statusMessage: String? = null,
    val additionalContextLimit: Int? = null,
    val sourcePath: String = "",
    val source: String = "",
    val pluginId: String? = null,
    val displayOrder: Long = 0L,
    val enabled: Boolean = true,
    val isManaged: Boolean = false,
    val currentHash: String = "",
    /** `managed`, `untrusted`, `trusted` or `modified`. */
    val trustStatus: String = "",
)

/** One `hooks/list` entry: the hooks discovered under one working directory. */
data class HooksListEntry(
    val cwd: String = "",
    val hooks: List<HookMetadata> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<HookErrorInfo> = emptyList(),
)

data class HookErrorInfo(val path: String = "", val message: String = "")

/**
 * `account/read` response. Mirrors `GetAccountResponse`.
 *
 * [requiresOpenaiAuth] is the field a signed-out surface needs: it says whether OpenAI
 * authentication is required at all (Bedrock and API-key accounts answer `false`), which is what
 * decides if the sign-in call to action is shown.
 */
data class AccountReadResponse(
    val requiresOpenaiAuth: Boolean,
    val account: Account? = null,
)

/**
 * The signed-in account. Mirrors the `Account` tagged union: [ApiKey], [Chatgpt] and
 * [AmazonBedrock] are the three variants the wire defines.
 */
sealed interface Account {
    /** An API-key account carries no identity fields. */
    data object ApiKey : Account

    data class Chatgpt(val email: String?, val planType: String) : Account

    data class AmazonBedrock(val usesCodexManagedCredentials: Boolean = false) : Account
}

/**
 * `account/rateLimits/read` response. Mirrors `GetAccountRateLimitsResponse`.
 *
 * [rateLimits] is the backward-compatible single bucket; [rateLimitsByLimitId] keys the same shape
 * by metered `limit_id` (for example `codex`), which is what multi-bucket UIs read.
 */
data class AccountRateLimits(
    val rateLimits: RateLimitSnapshot = RateLimitSnapshot(),
    val rateLimitsByLimitId: Map<String, RateLimitSnapshot>? = null,
    val accountId: String? = null,
    val rateLimitResetCredits: RateLimitResetCreditsSummary? = null,
    val ordinaryUsageAllowed: Boolean? = null,
)

/** One rate-limit bucket. Mirrors `RateLimitSnapshot`; every field is optional. */
data class RateLimitSnapshot(
    val primary: RateLimitWindow? = null,
    val secondary: RateLimitWindow? = null,
    val credits: CreditsSnapshot? = null,
    val limitId: String? = null,
    val limitName: String? = null,
    val planType: String? = null,
    val rateLimitReachedType: String? = null,
    val spendControlReached: Boolean? = null,
    val normalModelSlug: String? = null,
    /** Per-account spend control: `used` against `limit`, as the backend reports it. */
    val individualLimit: SpendControlLimitSnapshot? = null,
) {
    /**
     * Fold a sparse `account/rateLimits/updated` into this snapshot.
     *
     * The notification only carries what the server could supply; a missing field means
     * "unchanged", not "cleared", so this must not be a plain replace.
     */
    fun mergedWith(update: RateLimitSnapshot): RateLimitSnapshot = copy(
        primary = update.primary ?: primary,
        secondary = update.secondary ?: secondary,
        credits = update.credits ?: credits,
        limitId = update.limitId ?: limitId,
        limitName = update.limitName ?: limitName,
        planType = update.planType ?: planType,
        rateLimitReachedType = update.rateLimitReachedType ?: rateLimitReachedType,
        spendControlReached = update.spendControlReached ?: spendControlReached,
        normalModelSlug = update.normalModelSlug ?: normalModelSlug,
        individualLimit = update.individualLimit ?: individualLimit,
    )
}

/**
 * `RateLimitSnapshot.individualLimit`. Mirrors `SpendControlLimitSnapshot`.
 *
 * Every field is required upstream; the backend reports spend-control only for accounts that have
 * one, so the whole object is optional on the snapshot.
 */
data class SpendControlLimitSnapshot(
    val limit: String = "",
    val remainingPercent: Int = 0,
    val resetsAt: Long = 0L,
    val used: String = "",
)

/**
 * `account/usage/read` answer when a `threadId` is passed. Mirrors `v2::ThreadUsage`.
 *
 * Credits are micros, so the formatter divides once rather than carrying a float through the state.
 */
data class ThreadUsage(
    val threadId: String,
    val estimatedUsageCreditsMicros: Long = 0L,
    val estimatedUsageUsdMicros: Long? = null,
    val groups: List<ThreadUsageGroup> = emptyList(),
)

/** One model/effort bucket inside [ThreadUsage]. Mirrors `ThreadUsageBreakdownGroup`. */
data class ThreadUsageGroup(
    val model: String? = null,
    val reasoningEffort: String? = null,
    val speed: String? = null,
    val totalTokens: Long? = null,
    val inputTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val netNewInputTokens: Long? = null,
    val outputTokens: Long? = null,
    val estimatedUsageCreditsMicros: Long = 0L,
)

/** One window inside a bucket. Mirrors `RateLimitWindow`. */
data class RateLimitWindow(
    /** Percentage used, 0–100. */
    val usedPercent: Long,
    val windowDurationMins: Long? = null,
    /** Epoch millis; decoded from the wire's Unix seconds. */
    val resetsAt: Long? = null,
)

/** Mirrors `CreditsSnapshot`. */
data class CreditsSnapshot(
    val hasCredits: Boolean,
    val unlimited: Boolean,
    val balance: String? = null,
)

/** Mirrors `RateLimitResetCreditsSummary`. */
data class RateLimitResetCreditsSummary(
    val availableCount: Long,
    val credits: List<RateLimitResetCredit>? = null,
)

/** Mirrors `RateLimitResetCredit`. */
data class RateLimitResetCredit(
    val id: String,
    val status: String,
    val resetType: String,
    val grantedAt: Long,
    val title: String? = null,
    val description: String? = null,
    val expiresAt: Long? = null,
)

/**
 * `account/usage/read` response.
 *
 * [dailyBuckets] carries the per-day series; the summary fields mirror `AccountTokenUsageSummary`
 * (lifetime/peak tokens, streaks, longest turn) and are zero when an older server omits them.
 */
data class AccountUsage(
    val dailyBuckets: List<UsageBucket> = emptyList(),
    /** `summary.lifetimeTokens`. */
    val totalTokens: Long = 0L,
    val peakDailyTokens: Long = 0L,
    val longestRunningTurnSec: Long = 0L,
    val currentStreakDays: Long = 0L,
    val longestStreakDays: Long = 0L,
)

data class UsageBucket(val day: String, val tokens: Int)

/**
 * `fs/getMetadata` response, and the shape `fs/readDirectory` rows are folded into.
 *
 * The real `fs/readDirectory` returns `FsReadDirectoryEntry { fileName, isDirectory, isFile,
 * isSymlink }` — a name, not a path. The picker works in paths (it navigates by joining), so the
 * entry is widened to a path here and [isFile]/[isSymlink] are carried through for the row icon.
 */
data class FileMetadata(
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modifiedAt: Long = 0L,
    val isFile: Boolean = !isDirectory,
    val isSymlink: Boolean = false,
    val createdAt: Long = 0L,
) {
    /** `fs/readDirectory` returns entries carrying the same metadata shape. */
    val name: String get() = path.trimEnd('/').substringAfterLast('/')

    companion object {
        /** Fold one `fs/readDirectory` entry into the path-addressed shape the picker navigates by. */
        fun of(directory: String, entry: FsReadDirectoryEntry): FileMetadata = FileMetadata(
            path = directory.trimEnd('/') + "/" + entry.fileName,
            isDirectory = entry.isDirectory,
            isFile = entry.isFile,
            isSymlink = entry.isSymlink,
        )
    }
}
