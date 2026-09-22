package com.cy.codex.protocol.protocol.v2

import kotlinx.serialization.json.JsonElement

/**
 * `ClientRequest` — the 163 methods the client may call.
 *
 * The count is the *experimental-inclusive* one, because [InitializeCapabilities.experimentalApi]
 * defaults to `true` here exactly as it does in the TUI. `app-server-protocol/schema/json/
 * ClientRequest.json` is generated with `experimental_api = false` and therefore lists only the 101
 * stable methods; the remaining ones are tagged `#[experimental("…")]` in
 * `codex-rs/app-server-protocol/src/protocol/common.rs`. The authoritative full list is the
 * project's own `schema/precomputed/app-server-exports-experimental.json.zst`, which lists 164:
 * the one method deliberately left out of the registry is `mock/experimentalMethod`, an upstream
 * test scaffold that no product client should call. `UpstreamSchemaTest` asserts the difference is
 * exactly that one method.
 *
 * The enum doubles as the request registry so a call site can never invent a method name that the
 * server does not implement. Answering a server-initiated request is *not* in here: that is a
 * JSON-RPC **response** carrying the request's own id, not a method call of its own.
 */
enum class ClientRequestMethod(val wire: String) {
    // Handshake
    Initialize("initialize"),

    // thread/… — lifecycle
    ThreadStart("thread/start"),
    ThreadResume("thread/resume"),
    ThreadFork("thread/fork"),
    ThreadList("thread/list"),
    ThreadLoadedList("thread/loaded/list"),
    ThreadRead("thread/read"),
    ThreadItemsList("thread/items/list"),
    ThreadTurnsList("thread/turns/list"),
    ThreadTimelineList("thread/timeline/list"),
    ThreadArchive("thread/archive"),
    ThreadUnarchive("thread/unarchive"),
    ThreadDelete("thread/delete"),
    ThreadNameSet("thread/name/set"),
    ThreadMetadataUpdate("thread/metadata/update"),
    ThreadCompactStart("thread/compact/start"),
    ThreadRevert("thread/revert"),
    ThreadUnsubscribe("thread/unsubscribe"),
    ThreadInjectItems("thread/inject_items"),
    ThreadShellCommand("thread/shellCommand"),
    ThreadApproveGuardianDeniedAction("thread/approveGuardianDeniedAction"),
    ThreadSectionMove("thread/section/move"),
    ThreadSearch("thread/search"),
    ThreadSearchOccurrences("thread/searchOccurrences"),

    // thread/… — settings, memory and elicitation counters
    ThreadSettingsUpdate("thread/settings/update"),
    ThreadMemoryModeSet("thread/memoryMode/set"),
    ThreadIncrementElicitation("thread/increment_elicitation"),
    ThreadDecrementElicitation("thread/decrement_elicitation"),

    // thread/… — goals
    ThreadGoalSet("thread/goal/set"),
    ThreadGoalGet("thread/goal/get"),
    ThreadGoalClear("thread/goal/clear"),

    // thread/… — queued turns
    ThreadQueueAdd("thread/queue/add"),
    ThreadQueueList("thread/queue/list"),
    ThreadQueueUpdate("thread/queue/update"),
    ThreadQueueDelete("thread/queue/delete"),
    ThreadQueueReorder("thread/queue/reorder"),
    ThreadQueueStart("thread/queue/start"),

    // thread/… — attachments
    ThreadAttachmentAdd("thread/attachment/add"),
    ThreadAttachmentList("thread/attachment/list"),
    ThreadAttachmentRemove("thread/attachment/remove"),

    // thread/… — background terminals
    ThreadBackgroundTerminalsList("thread/backgroundTerminals/list"),
    ThreadBackgroundTerminalsTerminate("thread/backgroundTerminals/terminate"),
    ThreadBackgroundTerminalsClean("thread/backgroundTerminals/clean"),

    // thread/… — realtime voice
    ThreadRealtimeStart("thread/realtime/start"),
    ThreadRealtimeStop("thread/realtime/stop"),
    ThreadRealtimeListVoices("thread/realtime/listVoices"),
    ThreadRealtimeAppendAudio("thread/realtime/appendAudio"),
    ThreadRealtimeAppendSpeech("thread/realtime/appendSpeech"),
    ThreadRealtimeAppendText("thread/realtime/appendText"),

    // turn/…
    TurnStart("turn/start"),
    TurnSteer("turn/steer"),
    TurnInterrupt("turn/interrupt"),
    TurnSettingsUpdate("turn/settings/update"),

    // threadSection/…
    ThreadSectionList("threadSection/list"),
    ThreadSectionCreate("threadSection/create"),
    ThreadSectionUpdate("threadSection/update"),
    ThreadSectionDelete("threadSection/delete"),

    // account/…
    AccountRead("account/read"),
    AccountLoginStart("account/login/start"),
    AccountLoginCancel("account/login/cancel"),
    AccountLogout("account/logout"),
    AccountRateLimitsRead("account/rateLimits/read"),
    AccountUsageRead("account/usage/read"),
    AccountWorkspaceMessagesRead("account/workspaceMessages/read"),
    AccountRateLimitResetCreditConsume("account/rateLimitResetCredit/consume"),
    AccountSendAddCreditsNudgeEmail("account/sendAddCreditsNudgeEmail"),
    AccountBedrockDiscover("account/bedrock/discover"),
    AccountBedrockSetup("account/bedrock/setup"),

    // fs/…
    FsReadFile("fs/readFile"),
    FsWriteFile("fs/writeFile"),
    FsReadDirectory("fs/readDirectory"),
    FsCreateDirectory("fs/createDirectory"),
    FsGetMetadata("fs/getMetadata"),
    FsRemove("fs/remove"),
    FsCopy("fs/copy"),
    FsWatch("fs/watch"),
    FsUnwatch("fs/unwatch"),

    // command/…
    CommandExec("command/exec"),
    CommandExecWrite("command/exec/write"),
    CommandExecResize("command/exec/resize"),
    CommandExecTerminate("command/exec/terminate"),

    // process/… (experimental)
    ProcessSpawn("process/spawn"),
    ProcessWriteStdin("process/writeStdin"),
    ProcessResizePty("process/resizePty"),
    ProcessKill("process/kill"),

    // config/…
    ConfigRead("config/read"),
    ConfigValueWrite("config/value/write"),
    ConfigBatchWrite("config/batchWrite"),
    ConfigMcpServerReload("config/mcpServer/reload"),
    ConfigRequirementsRead("configRequirements/read"),

    // model / permissions
    ModelList("model/list"),
    ModelProviderCapabilitiesRead("modelProvider/capabilities/read"),
    PermissionProfileList("permissionProfile/list"),

    // experimental features
    ExperimentalFeatureList("experimentalFeature/list"),
    ExperimentalFeatureEnablementSet("experimentalFeature/enablement/set"),

    // collaboration modes
    CollaborationModeList("collaborationMode/list"),

    // mcpServer/…
    McpServerStatusList("mcpServerStatus/list"),
    McpServerOauthLogin("mcpServer/oauth/login"),
    McpServerResourceRead("mcpServer/resource/read"),
    McpServerToolCall("mcpServer/tool/call"),
    McpServerEventStreamStart("mcpServer/event/stream/start"),
    McpServerEventStreamStop("mcpServer/event/stream/stop"),

    // memory/…
    MemoryStatus("memory/status"),
    MemoryReset("memory/reset"),

    // skills / plugins / marketplace
    SkillsList("skills/list"),
    SkillsConfigWrite("skills/config/write"),
    SkillsExtraRootsSet("skills/extraRoots/set"),
    PluginList("plugin/list"),
    PluginInstalled("plugin/installed"),
    PluginRead("plugin/read"),
    PluginInstall("plugin/install"),
    PluginUninstall("plugin/uninstall"),
    PluginSkillRead("plugin/skill/read"),
    PluginReconcile("plugin/reconcile"),
    PluginSearch("plugin/search"),
    PluginShareList("plugin/share/list"),
    PluginShareSave("plugin/share/save"),
    PluginShareDelete("plugin/share/delete"),
    PluginShareCheckout("plugin/share/checkout"),
    PluginShareUpdateTargets("plugin/share/updateTargets"),
    MarketplaceAdd("marketplace/add"),
    MarketplaceRemove("marketplace/remove"),
    MarketplaceUpgrade("marketplace/upgrade"),

    // apps
    AppList("app/list"),
    AppInstalled("app/installed"),
    AppRead("app/read"),

    // projects (experimental)
    ProjectList("project/list"),
    ProjectRead("project/read"),
    ProjectCreate("project/create"),
    ProjectUpdate("project/update"),
    ProjectDelete("project/delete"),
    ProjectMove("project/move"),
    ProjectImport("project/import"),

    // environments (experimental)
    EnvironmentInfo("environment/info"),
    EnvironmentStatus("environment/status"),
    EnvironmentAdd("environment/add"),

    // remote control (experimental)
    RemoteControlStatusRead("remoteControl/status/read"),
    RemoteControlEnable("remoteControl/enable"),
    RemoteControlDisable("remoteControl/disable"),
    RemoteControlPairingStart("remoteControl/pairing/start"),
    RemoteControlPairingStatus("remoteControl/pairing/status"),
    RemoteControlClientList("remoteControl/client/list"),
    RemoteControlClientRevoke("remoteControl/client/revoke"),

    // user verification (experimental)
    UserVerificationStatus("userVerification/status"),
    UserVerificationEnroll("userVerification/enroll"),
    UserVerificationVerify("userVerification/verify"),
    UserVerificationCancel("userVerification/cancel"),
    UserVerificationDelete("userVerification/delete"),

    // external agent config migration (experimental)
    ExternalAgentConfigDetect("externalAgentConfig/detect"),
    ExternalAgentConfigImport("externalAgentConfig/import"),
    ExternalAgentConfigImportReadHistories("externalAgentConfig/import/readHistories"),
    ExternalAgentConfigImportRecordHistory("externalAgentConfig/import/recordHistory"),

    // review / search / misc
    ReviewStart("review/start"),
    RolloutCompress("rollout/compress"),
    FuzzyFileSearch("fuzzyFileSearch"),
    FuzzyFileSearchSessionStart("fuzzyFileSearch/sessionStart"),
    FuzzyFileSearchSessionUpdate("fuzzyFileSearch/sessionUpdate"),
    FuzzyFileSearchSessionStop("fuzzyFileSearch/sessionStop"),
    HooksList("hooks/list"),
    FeedbackUpload("feedback/upload"),
    ServerDiagnostics("server/diagnostics"),

    // windowsSandbox/… (only meaningful on Windows hosts; carried so the registry is complete)
    WindowsSandboxReadiness("windowsSandbox/readiness"),
    WindowsSandboxSetupStart("windowsSandbox/setupStart"),

    ;

    companion object {
        private val byWire = entries.associateBy { it.wire }

        fun fromWire(wire: String): ClientRequestMethod? = byWire[wire]
    }
}

/**
 * `ClientNotification` — only one exists in the protocol, and it must be sent before any other
 * traffic is accepted.
 */
enum class ClientNotificationMethod(val wire: String) {
    Initialized("initialized"),
}

/** Capabilities the client declares at handshake time. Mirrors `InitializeCapabilities`. */
data class InitializeCapabilities(
    /** Receives experimental methods and fields. The TUI itself declares `true`. */
    val experimentalApi: Boolean = true,
    val requestAttestation: Boolean = false,
    val mcpServerOpenaiFormElicitation: Boolean = false,
    val optOutNotificationMethods: List<String>? = null,
    /** MCP extension settings declared by this client. */
    val extensions: Map<String, JsonElement>? = null,
)

/** `initialize` params. Mirrors v1 `InitializeParams`: capabilities are optional. */
data class InitializeParams(
    val clientInfo: ClientInfo,
    val capabilities: InitializeCapabilities? = null,
)

data class ClientInfo(
    val name: String,
    val title: String? = null,
    val version: String,
)

/**
 * `initialize` response. Mirrors v1 `InitializeResponse`.
 *
 * The handshake itself is completed by the native transport before it returns, so nothing in the
 * app decodes this today; the type is carried so the schema match covers the wire.
 */
data class InitializeResponse(
    val userAgent: String,
    val codexHome: String,
    val platformFamily: String,
    val platformOs: String,
)
