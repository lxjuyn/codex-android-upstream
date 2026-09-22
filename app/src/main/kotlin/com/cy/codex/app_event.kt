package com.cy.codex

import com.cy.codex.protocol.ApprovalResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import com.cy.codex.protocol.protocol.RequestId
import com.cy.codex.protocol.protocol.v2.ApprovalsReviewer
import com.cy.codex.protocol.protocol.v2.AskForApproval
import com.cy.codex.protocol.protocol.v2.AttachmentType
import com.cy.codex.protocol.protocol.v2.CollaborationMode
import com.cy.codex.protocol.protocol.v2.ConfigBatchWriteParams
import com.cy.codex.protocol.protocol.v2.LoginAccountParams
import com.cy.codex.protocol.protocol.v2.MergeStrategy
import com.cy.codex.protocol.protocol.v2.PluginShareDiscoverability
import com.cy.codex.protocol.protocol.v2.PluginShareTarget
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.ReviewTarget
import com.cy.codex.protocol.protocol.v2.ThreadRealtimeAudioChunk
import com.cy.codex.protocol.protocol.v2.UserInput
import com.cy.codex.protocol.protocol.v2.WindowsSandboxSetupMode

/**
 * Application-level events used to coordinate UI actions.
 *
 * Mirrors `codex-rs/tui/src/app_event.rs`: widgets emit these instead of reaching into the session,
 * and one reducer owns every transition. Keeping the widget→action direction event-based is what
 * lets the composer, the status card and the approval cards stay independent of each other.
 *
 * Two rules decide whether something belongs here rather than inside a screen:
 *
 *  - **A read a screen owns does not.** A page that lists something reads it through the
 *    [com.cy.codex.protocol.AppServerClient] it was handed — `WorkspacePickerScreen`,
 *    `SubAgentThreadScreen` and the file browser all work that way. Routing a read through an event
 *    would put the answer in a second place and let two copies of it disagree.
 *  - **A write does, when it invalidates something the app holds.** Archiving a thread changes the
 *    sidebar; installing a plugin changes the plugin catalog. The events below are the ones whose
 *    effect outlives the page that asked for it.
 */
sealed interface AppEvent {

    // ---- threads ---------------------------------------------------------------
    //
    // The whole lifecycle, not just the part the sidebar draws. Every one of these used to fall into
    // the reducer's `else` arm: the session list rendered Fork / Rename / Archive / Delete rows and
    // tapping any of them did nothing at all, silently, because no reducer owned the transition.
    data class NewThread(val cwd: String? = null) : AppEvent
    data class ResumeThread(val threadId: String) : AppEvent
    data class ForkThread(val threadId: String) : AppEvent
    data class ArchiveThread(val threadId: String, val archived: Boolean) : AppEvent
    data class DeleteThread(val threadId: String) : AppEvent
    data class RenameThread(val threadId: String, val name: String) : AppEvent
    data class CompactThread(val threadId: String) : AppEvent
    data class RevertThread(val threadId: String, val itemId: String?) : AppEvent
    /** Re-read the thread list with whatever scope is currently set. */
    data object RefreshThreadList : AppEvent

    /**
     * Re-read the subagent threads spawned from [ancestorThreadId].
     *
     * The agents dashboard needs thread metadata (name, liveness, cli version) that the parent
     * transcript does not carry; `thread/list` scoped by ancestor is the only call that has it.
     */
    data class ReloadAgentThreads(val ancestorThreadId: String) : AppEvent

    /** Interrupt the active turn of [threadId], which may not be the open thread. */
    data class StopThreadTurn(val threadId: String) : AppEvent

    /**
     * Choose whether the thread list includes archived sessions.
     *
     * A scope, not a filter applied to a copy: `thread/list` takes it as a parameter, so an
     * archived thread is either in the list the server returned or not in it at all.
     */
    data class SetThreadListScope(val includeArchived: Boolean) : AppEvent
    data class MoveThreadToSection(val threadId: String, val sectionId: String?) : AppEvent

    /** Run one shell command in the session's shell, without starting a turn. */
    data class RunShellCommand(val threadId: String, val command: String) : AppEvent

    /** Override a guardian denial for one item. */
    data class ApproveGuardianDeniedAction(val threadId: String, val itemId: String) : AppEvent

    // ---- sidebar sections ------------------------------------------------------
    data class CreateSection(val name: String) : AppEvent
    data class RenameSection(val sectionId: String, val name: String) : AppEvent
    data class DeleteSection(val sectionId: String) : AppEvent

    // ---- turns -----------------------------------------------------------------
    data class SubmitUserMessage(val inputs: List<UserInput>, val queued: Boolean = false) : AppEvent
    data object InterruptTurn : AppEvent

    /**
     * Answer one inline question tapping an option or typing into its field in the transcript.
     *
     * The answer is an ordinary user message (upstream `chatwidget/questions.rs` does the same), but
     * it must bypass the composer: slash classification would eat an option that begins with `/`,
     * and submitting it must not clear a draft the user is still writing. [text] is already framed
     * with the question it answers — the cell builds it through `AsyncQuestions.answeredText`.
     */
    data class AnswerAsyncQuestion(val text: String) : AppEvent
    /**
     * Create or patch the thread goal; every null field is left as it is.
     *
     * `/goal <text>` sends only an objective, `/goal pause|resume` only a status, and saving an
     * edit sends both, which is why this is one event rather than one per control.
     */
    data class SetGoal(
        val objective: String? = null,
        val status: com.cy.codex.protocol.protocol.v2.GoalStatus? = null,
    ) : AppEvent

    data object ClearGoal : AppEvent

    /** `/recap`: summarize the recent exchange through a hidden structured turn. */
    data object GenerateRecap : AppEvent

    /**
     * Resume the stopped turn with the misalignment override.
     *
     * The steer message comes from the error details, so this carries no payload: answering the
     * wrong turn's steer is not expressible.
     */
    data object ContinueMisalignment : AppEvent

    /**
     * `/side` and `/btw`: fork an ephemeral conversation, or return to the parent when one is open.
     *
     * [message] is submitted as the first side turn when the command carried one.
     */
    data class ToggleSideConversation(val message: String? = null) : AppEvent

    // ---- server-side queue -----------------------------------------------------
    //
    // The queue lives on the server, so every one of these is a request followed by a
    // `thread/queue/changed` notification that pulls the new order back. Nothing here mutates the
    // local list directly: a locally reordered queue and the server's would disagree on the ids.
    data class StartQueuedMessage(val queuedId: String? = null) : AppEvent
    data class DeleteQueuedMessage(val queuedId: String) : AppEvent

    /**
     * Replace a queued message's body.
     *
     * Whole inputs rather than a string: a queued entry can hold an image or a file reference as
     * well as text, and `thread/queue/update` takes the list, so sending a rewritten string would
     * silently drop everything that was not text.
     */
    data class UpdateQueuedMessage(val queuedId: String, val inputs: List<UserInput>) : AppEvent
    data class MoveQueuedMessage(val queuedId: String, val delta: Int) : AppEvent
    data object ClearQueue : AppEvent

    // ---- approvals -------------------------------------------------------------
    /** Answer the request currently on screen. */
    data class ResolveApproval(val requestId: RequestId, val response: ApprovalResponse) : AppEvent

    /** A request the UI is showing was resolved elsewhere. */
    data class DismissApproval(val requestId: RequestId) : AppEvent

    /** Hide an auto-review denial from the notice bar without approving it. */
    data class DismissAutoReviewDenial(val itemId: String) : AppEvent

    // ---- settings --------------------------------------------------------------
    data class SetModel(val model: String) : AppEvent
    data class SetReasoningEffort(val effort: ReasoningEffort) : AppEvent
    data class SetApprovalPolicy(val policy: AskForApproval) : AppEvent
    data class SetApprovalsReviewer(val reviewer: ApprovalsReviewer) : AppEvent

    /** Switch the thread between Default and Plan; `thread/settings/update` carries the mask. */
    data class SetCollaborationMode(val mode: CollaborationMode) : AppEvent

    /** Select a model service tier; `null` means the model's default tier. */
    data class SetServiceTier(val tier: String?) : AppEvent
    data class SetExperimentalFeature(val id: String, val enabled: Boolean) : AppEvent

    // ---- memories --------------------------------------------------------------
    /**
     * Persist both memory settings and, when generation changed, apply the mode to the open thread.
     *
     * Mirrors `update_memory_settings_with_app_server`: the config write is the durable half, the
     * `thread/memoryMode/set` is what makes the open thread honor it without a restart.
     */
    data class SetMemorySettings(val useMemories: Boolean, val generateMemories: Boolean) : AppEvent

    // ---- hooks -----------------------------------------------------------------
    /**
     * Trust one hook by writing its current hash under `hooks.state.<key>`.
     *
     * The hash is what `hook_needs_review` compares against: an untrusted or modified hook stays
     * blocked until the reviewed bytes are pinned.
     */
    data class SetHookTrust(val key: String, val currentHash: String) : AppEvent

    /** Enable or disable one hook under `hooks.state.<key>.enabled`. */
    data class SetHookEnabled(val key: String, val enabled: Boolean) : AppEvent

    // ---- account ---------------------------------------------------------------
    data object ReloadAccount : AppEvent
    data object ReloadRateLimits : AppEvent
    data object ReloadUsage : AppEvent

    /** Start sign-in. The browser/device-code flow completes through `account/login/completed`. */
    data class Login(val params: LoginAccountParams) : AppEvent

    data class CancelLogin(val loginId: String) : AppEvent
    data object Logout : AppEvent

    /** Consume one rate-limit reset credit; `null` means "whichever the server picks". */
    data class ConsumeResetCredit(val creditId: String? = null) : AppEvent

    /** Ask the server to e-mail a top-up link for one credit type. */
    data class SendAddCreditsNudgeEmail(
        val creditType: com.cy.codex.protocol.protocol.v2.AddCreditsNudgeCreditType,
    ) : AppEvent

    data object BedrockDiscover : AppEvent
    data class BedrockSetup(val params: com.cy.codex.protocol.protocol.v2.BedrockSetupParams) : AppEvent

    // ---- catalogs: reload requests --------------------------------------------
    //
    // One event per catalog rather than one carrying the new value. The `Refresh*` family these
    // replaced took a list the caller had already read, so "refresh" could only ever echo back the
    // copy on screen — and because nothing handled them it did not even do that.
    data object ReloadSkills : AppEvent
    data object ReloadPlugins : AppEvent
    data object ReloadPluginShares : AppEvent
    data object ReloadApps : AppEvent
    data object ReloadHooks : AppEvent
    data object ReloadMcpServers : AppEvent
    data object ReloadConfig : AppEvent
    data object ReloadProjects : AppEvent
    data object ReloadEnvironments : AppEvent
    data object ReloadMemories : AppEvent
    data object ReloadRealtimeVoices : AppEvent
    data object ReloadUserVerification : AppEvent
    data object ReloadRemoteControl : AppEvent
    data object ReloadDiagnostics : AppEvent
    data object ReloadExternalAgentConfig : AppEvent
    data object ReloadGoal : AppEvent

    // ---- plugins, marketplaces and shares --------------------------------------
    data class InstallPlugin(val name: String, val marketplace: String? = null) : AppEvent
    data class UninstallPlugin(val pluginId: String) : AppEvent
    /** `config/value/write` of `plugins.<id>.enabled`, as the TUI's Space toggle does. */
    data class SetPluginEnabled(val pluginId: String, val enabled: Boolean) : AppEvent
    data class AddMarketplace(val source: String, val ref: String? = null) : AppEvent
    data class RemoveMarketplace(val name: String) : AppEvent
    data class UpgradeMarketplace(val name: String? = null) : AppEvent

    /** Rewrite the catalog from what is on disk; answers with the entries it changed. */
    data object ReconcilePlugins : AppEvent

    data class SavePluginShare(val pluginPath: String, val remotePluginId: String? = null) : AppEvent
    data class DeletePluginShare(val remotePluginId: String) : AppEvent
    data class CheckoutPluginShare(val remotePluginId: String) : AppEvent
    data class UpdatePluginShareTargets(
        val remotePluginId: String,
        val discoverability: PluginShareDiscoverability,
        val targets: List<PluginShareTarget>,
    ) : AppEvent

    // ---- skills, apps, hooks ---------------------------------------------------
    data class SetSkillEnabled(val name: String, val enabled: Boolean) : AppEvent

    /** Replace the extra roots skills are discovered under. */
    data class SetSkillExtraRoots(val roots: List<String>) : AppEvent

    data class SetAppInstalled(val appId: String, val installed: Boolean) : AppEvent

    // ---- MCP -------------------------------------------------------------------
    data class McpLogin(val serverName: String) : AppEvent
    data object ReloadMcpConfig : AppEvent

    /**
     * Open or close a server's event stream; its notifications arrive on the event flow.
     *
     * [subscriptionId] is the client-chosen name the stop call uses; [name] and [arguments] are the
     * MCP tool call the stream belongs to, the same pair `mcpServer/tool/call` takes.
     */
    data class SetMcpEventStream(
        val server: String,
        val threadId: String,
        val subscriptionId: String,
        val name: String,
        val arguments: JsonElement = JsonNull,
        val streaming: Boolean,
    ) : AppEvent

    // ---- projects and environments ---------------------------------------------
    data class CreateProject(val name: String, val path: String) : AppEvent
    data class UpdateProject(
        val projectId: String,
        val name: String? = null,
        val path: String? = null,
    ) : AppEvent

    data class DeleteProject(val projectId: String) : AppEvent
    data class MoveProject(val projectId: String, val position: Int) : AppEvent
    data class ImportProject(val path: String) : AppEvent
    data class AddEnvironment(val environmentId: String, val execServerUrl: String) : AppEvent

    // ---- remote control --------------------------------------------------------
    data class SetRemoteControlEnabled(val enabled: Boolean) : AppEvent
    data object StartRemoteControlPairing : AppEvent

    /**
     * Ask whether the other device has claimed the code yet.
     *
     * `remoteControl/pairing/status` is a poll, not a subscription: nothing pushes the claim, so
     * without this the page could show a code but never learn that it was taken.
     */
    data object PollRemoteControlPairing : AppEvent
    data class RevokeRemoteControlClient(val clientId: String) : AppEvent

    // ---- user verification -----------------------------------------------------
    data object EnrollUserVerification : AppEvent

    /**
     * Sign a challenge with the enrolled credential.
     *
     * The whole parameter object, not just the challenge: `userVerification/verify` also takes a
     * title and a description, and those are *display context the UI has already approved* — a
     * sheet that collects them and drops them would be asking for something it never uses.
     */
    data class VerifyUserVerification(
        val params: com.cy.codex.protocol.protocol.v2.UserVerificationVerifyParams,
    ) : AppEvent
    data object CancelUserVerification : AppEvent
    data object DeleteUserVerification : AppEvent

    // ---- sessions: realtime voice ----------------------------------------------
    data class StartRealtime(val threadId: String, val sdpOffer: String? = null) : AppEvent
    data class StopRealtime(val threadId: String) : AppEvent
    data class AppendRealtimeText(val threadId: String, val text: String) : AppEvent
    data class AppendRealtimeSpeech(val threadId: String, val text: String) : AppEvent
    data class AppendRealtimeAudio(
        val threadId: String,
        val audio: ThreadRealtimeAudioChunk,
    ) : AppEvent

    /**
     * Move the elicitation counter the session tracks.
     *
     * Not a settings toggle: the server counts the questions a session has open on a thread and
     * pauses the turn while any are outstanding, so this is part of the approval protocol.
     */
    data class IncrementElicitation(val threadId: String) : AppEvent
    data class DecrementElicitation(val threadId: String) : AppEvent

    // ---- review ----------------------------------------------------------------
    data class StartReview(val threadId: String, val target: ReviewTarget) : AppEvent

    // ---- memories --------------------------------------------------------------
    data object ResetMemory : AppEvent

    // ---- external agent migration ----------------------------------------------
    data object DetectExternalAgentConfig : AppEvent

    /** The detected items go back to the server unchanged; there is no id-only import. */
    data class ImportExternalAgentConfig(
        val items: List<com.cy.codex.protocol.protocol.v2.ExternalAgentConfigMigrationItem>,
    ) : AppEvent

    // ---- feedback --------------------------------------------------------------
    data class UploadFeedback(
        val classification: String,
        val reason: String? = null,
        val threadId: String? = null,
        /** Only ever true from the form's explicit disclosure; the log can carry prompts. */
        val includeLogs: Boolean = false,
    ) : AppEvent

    // ---- windows sandbox -------------------------------------------------------
    data class WindowsSandboxSetupStart(
        val mode: WindowsSandboxSetupMode,
        val cwd: String? = null,
    ) : AppEvent

    // ---- attachments and background terminals ---------------------------------
    data class RemoveAttachment(
        val threadId: String,
        val type: AttachmentType,
        val identityKey: String,
    ) : AppEvent
    data class TerminateBackgroundTerminal(val threadId: String, val processId: String) : AppEvent
    data class CleanBackgroundTerminals(val threadId: String) : AppEvent

    // ---- config ----------------------------------------------------------------
    /** Persist the settings page's toggle through `config/value/write`. */
    data class WriteConfigValue(
        val keyPath: String,
        val value: JsonElement,
        val merge: MergeStrategy = MergeStrategy.Replace,
    ) : AppEvent

    /** Persist several keys in one round trip, which is what a form's submit does. */
    data class WriteConfigBatch(val params: ConfigBatchWriteParams) : AppEvent

    // ---- composer drafts -------------------------------------------------------
    data class SetComposerDraft(val text: String) : AppEvent

    /** Drop a staged local image (and its `[Image #N]` placeholder) from the draft. */
    data class RemoveComposerImage(val path: String) : AppEvent

    data class SubmitSlashCommand(val command: String, val args: String) : AppEvent

}
