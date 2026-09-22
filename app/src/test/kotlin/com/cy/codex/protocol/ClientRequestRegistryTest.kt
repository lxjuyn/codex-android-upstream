package com.cy.codex.protocol

import com.cy.codex.protocol.protocol.v2.ClientRequestMethod
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps [ClientRequestMethod] and [AppServerClient] from drifting apart.
 *
 * The registry is the only place the protocol's method list is written down, but nothing about it is
 * enforced by the compiler: it is an enum of strings, and an interface of functions. A method can
 * therefore exist upstream with no way to call it, or a function can be added that calls nothing —
 * and both mistakes are invisible until a real backend is wired in, at which point they surface as
 * a feature that silently does nothing.
 *
 * This is the check that was missing. It failed to exist while `thread/realtime/appendAudio` and the
 * two elicitation counters were absent from the client, and while `listMarketplaces` called a method
 * (`marketplace/list`) that the protocol does not define.
 *
 * The mapping below is deliberately written out rather than derived. A wire name and a Kotlin
 * function name are allowed to differ — `thread/list` is `listThreads`, `thread/queue/update` is
 * `updateQueued` — so any automatic derivation would need a convention the protocol does not have.
 * Writing it down means renaming a function without updating this table fails the reflection check,
 * and adding a registry entry without a client method fails the completeness check.
 */
class ClientRequestRegistryTest {

    /**
     * Every client request the protocol defines, and the [AppServerClient] function that issues it.
     *
     * The set is the experimental-inclusive one: `InitializeCapabilities.experimentalApi` defaults
     * to `true` here exactly as it does in the TUI, so the methods tagged experimental upstream are
     * part of the surface, not an appendix to it. The one method deliberately left out is
     * `mock/experimentalMethod`, an upstream test scaffold no product client should call;
     * `UpstreamSchemaTest` pins that the difference is exactly that method.
     */
    private val wireToClientMethod: Map<String, String> = mapOf(
        "account/bedrock/discover" to "bedrockDiscover",
        "account/bedrock/setup" to "bedrockSetup",
        "account/login/cancel" to "cancelLogin",
        "account/login/start" to "login",
        "account/logout" to "logout",
        "account/rateLimitResetCredit/consume" to "consumeRateLimitResetCredit",
        "account/rateLimits/read" to "readRateLimits",
        "account/read" to "readAccount",
        "account/sendAddCreditsNudgeEmail" to "sendAddCreditsNudgeEmail",
        "account/usage/read" to "readUsage",
        "account/workspaceMessages/read" to "readWorkspaceMessages",
        "app/installed" to "listInstalledApps",
        "app/list" to "listApps",
        "app/read" to "readApps",
        "collaborationMode/list" to "listCollaborationModes",
        "command/exec" to "execCommand",
        "command/exec/resize" to "execResize",
        "command/exec/terminate" to "execTerminate",
        "command/exec/write" to "execWrite",
        "config/batchWrite" to "writeConfigBatch",
        "config/mcpServer/reload" to "reloadMcpServers",
        "config/read" to "readConfig",
        "config/value/write" to "writeConfigValue",
        "configRequirements/read" to "readConfigRequirements",
        "environment/add" to "addEnvironment",
        "environment/info" to "readEnvironmentInfo",
        "environment/status" to "readEnvironmentStatus",
        "experimentalFeature/enablement/set" to "setExperimentalFeature",
        "experimentalFeature/list" to "listExperimentalFeatures",
        "externalAgentConfig/detect" to "detectExternalAgentConfig",
        "externalAgentConfig/import" to "importExternalAgentConfig",
        "externalAgentConfig/import/readHistories" to "readExternalAgentImportHistories",
        "externalAgentConfig/import/recordHistory" to "recordExternalAgentImportHistory",
        "feedback/upload" to "uploadFeedback",
        "fs/copy" to "copyPath",
        "fs/createDirectory" to "createDirectory",
        "fs/getMetadata" to "getMetadata",
        "fs/readDirectory" to "readDirectory",
        "fs/readFile" to "readFile",
        "fs/remove" to "removePath",
        "fs/unwatch" to "unwatchPath",
        "fs/watch" to "watchPath",
        "fs/writeFile" to "writeFile",
        "fuzzyFileSearch" to "fuzzyFileSearch",
        "fuzzyFileSearch/sessionStart" to "startFuzzySearchSession",
        "fuzzyFileSearch/sessionStop" to "stopFuzzySearchSession",
        "fuzzyFileSearch/sessionUpdate" to "updateFuzzySearchSession",
        "hooks/list" to "listHooks",
        "initialize" to "initialize",
        "marketplace/add" to "addMarketplace",
        "marketplace/remove" to "removeMarketplace",
        "marketplace/upgrade" to "upgradeMarketplace",
        "mcpServer/event/stream/start" to "startMcpEventStream",
        "mcpServer/event/stream/stop" to "stopMcpEventStream",
        "mcpServer/oauth/login" to "mcpOauthLogin",
        "mcpServer/resource/read" to "readMcpResource",
        "mcpServer/tool/call" to "callMcpTool",
        "mcpServerStatus/list" to "listMcpServers",
        "memory/reset" to "resetMemory",
        "memory/status" to "readMemoryStatus",
        "model/list" to "listModels",
        "modelProvider/capabilities/read" to "readModelProviderCapabilities",
        "permissionProfile/list" to "listPermissionProfiles",
        "plugin/install" to "installPlugin",
        "plugin/installed" to "listInstalledPlugins",
        "plugin/list" to "listPlugins",
        "plugin/read" to "readPlugin",
        "plugin/reconcile" to "reconcilePlugins",
        "plugin/search" to "searchPlugins",
        "plugin/share/checkout" to "checkoutPluginShare",
        "plugin/share/delete" to "deletePluginShare",
        "plugin/share/list" to "listPluginShares",
        "plugin/share/save" to "savePluginShare",
        "plugin/share/updateTargets" to "updatePluginShareTargets",
        "plugin/skill/read" to "readPluginSkill",
        "plugin/uninstall" to "uninstallPlugin",
        "process/kill" to "killProcess",
        "process/resizePty" to "resizeProcessPty",
        "process/spawn" to "spawnProcess",
        "process/writeStdin" to "writeProcessStdin",
        "project/create" to "createProject",
        "project/delete" to "deleteProject",
        "project/import" to "importProject",
        "project/list" to "listProjects",
        "project/move" to "moveProject",
        "project/read" to "readProject",
        "project/update" to "updateProject",
        "remoteControl/client/list" to "listRemoteControlClients",
        "remoteControl/client/revoke" to "revokeRemoteControlClient",
        "remoteControl/disable" to "disableRemoteControl",
        "remoteControl/enable" to "enableRemoteControl",
        "remoteControl/pairing/start" to "startRemoteControlPairing",
        "remoteControl/pairing/status" to "readRemoteControlPairing",
        "remoteControl/status/read" to "readRemoteControlStatus",
        "review/start" to "startReview",
        "rollout/compress" to "compressRollout",
        "server/diagnostics" to "readServerDiagnostics",
        "skills/config/write" to "writeSkillConfig",
        "skills/extraRoots/set" to "setSkillExtraRoots",
        "skills/list" to "listSkills",
        "thread/approveGuardianDeniedAction" to "approveGuardianDeniedAction",
        "thread/archive" to "archiveThread",
        "thread/attachment/add" to "addAttachment",
        "thread/attachment/list" to "listAttachments",
        "thread/attachment/remove" to "removeAttachment",
        "thread/backgroundTerminals/clean" to "cleanBackgroundTerminals",
        "thread/backgroundTerminals/list" to "listBackgroundTerminals",
        "thread/backgroundTerminals/terminate" to "terminateBackgroundTerminal",
        "thread/compact/start" to "compactThread",
        "thread/decrement_elicitation" to "decrementElicitation",
        "thread/delete" to "deleteThread",
        "thread/fork" to "forkThread",
        "thread/goal/clear" to "clearGoal",
        "thread/goal/get" to "getGoal",
        "thread/goal/set" to "setGoal",
        "thread/increment_elicitation" to "incrementElicitation",
        "thread/inject_items" to "injectThreadItems",
        "thread/items/list" to "listThreadItems",
        "thread/list" to "listThreads",
        "thread/loaded/list" to "listLoadedThreads",
        "thread/memoryMode/set" to "setThreadMemoryMode",
        "thread/metadata/update" to "updateThreadMetadata",
        "thread/name/set" to "setThreadName",
        "thread/queue/add" to "addToQueue",
        "thread/queue/delete" to "deleteQueued",
        "thread/queue/list" to "listQueue",
        "thread/queue/reorder" to "reorderQueue",
        "thread/queue/start" to "startQueued",
        "thread/queue/update" to "updateQueued",
        "thread/read" to "readThread",
        "thread/realtime/appendAudio" to "appendRealtimeAudio",
        "thread/realtime/appendSpeech" to "appendRealtimeSpeech",
        "thread/realtime/appendText" to "appendRealtimeText",
        "thread/realtime/listVoices" to "listRealtimeVoices",
        "thread/realtime/start" to "startRealtime",
        "thread/realtime/stop" to "stopRealtime",
        "thread/resume" to "resumeThread",
        "thread/revert" to "revertThread",
        "thread/search" to "searchThreads",
        "thread/searchOccurrences" to "searchThreadOccurrences",
        "thread/section/move" to "moveThreadToSection",
        "thread/settings/update" to "updateThreadSettings",
        "thread/shellCommand" to "runShellCommand",
        "thread/start" to "startThread",
        "thread/timeline/list" to "listThreadTimeline",
        "thread/turns/list" to "listThreadTurns",
        "thread/unarchive" to "unarchiveThread",
        "thread/unsubscribe" to "unsubscribeThread",
        "threadSection/create" to "createSection",
        "threadSection/delete" to "deleteSection",
        "threadSection/list" to "listSections",
        "threadSection/update" to "updateSection",
        "turn/interrupt" to "interruptTurn",
        "turn/settings/update" to "updateTurnSettings",
        "turn/start" to "startTurn",
        "turn/steer" to "steerTurn",
        "userVerification/cancel" to "cancelUserVerification",
        "userVerification/delete" to "deleteUserVerification",
        "userVerification/enroll" to "enrollUserVerification",
        "userVerification/status" to "readUserVerificationStatus",
        "userVerification/verify" to "verifyUserVerification",
        "windowsSandbox/readiness" to "windowsSandboxReadiness",
        "windowsSandbox/setupStart" to "windowsSandboxSetupStart",
    )

    /**
     * Functions on [AppServerClient] that are not requests of their own.
     *
     * `respond` answers a server-initiated request, which is a JSON-RPC *response* carrying the
     * request's own id rather than a method call; `close` is local teardown. Both are part of the
     * transport contract and neither appears in the registry.
     */
    /**
     * Members of [AppServerClient] that are not registry requests.
     *
     * Listed rather than inferred, so adding a member forces a deliberate classification. There are
     * three kinds:
     *
     *  - The transport contract. `respond` answers a server-initiated request, which is a JSON-RPC
     *    *response* carrying the request's own id rather than a method call, and `close` is local
     *    teardown. Neither is a `ClientRequest`.
     *  - Stream accessors. `events`, `requests` and `connection` are flows the transport pushes on,
     *    not calls the client makes.
      *  - Two client-side projections. `updateThreadSettingsFull` is the typed overload of
      *    `thread/settings/update`, and `readConfigLayers` reads the layer list out of a
      *    `config/read` response that already carried it — the protocol has no `config/layers/read`.
      *  - `readThreadUsage` is the thread-scoped overload of `account/usage/read`: the same request
      *    with a `threadId`, answered with `threadUsage` instead of the daily buckets.
      */
    private val nonRequestMembers = setOf(
        "respond",
        "close",
        "getEvents",
        "getRequests",
        "getConnection",
        "updateThreadSettingsFull",
        "readConfigLayers",
        "readThreadUsage",
    )

    /**
     * The JVM name of every non-synthetic function on [AppServerClient], demangled.
     *
     * Kotlin appends a hash suffix to a function's JVM name when its signature mentions a value
     * class, and `Result<T>` is one — so `listThreads` is declared as `listThreads-gIAlu-s`. That
     * suffix is a hash of the signature rather than part of the name a caller writes, so it is
     * stripped before comparing against the registry. Plain Java reflection is enough here; adding
     * `kotlin-reflect` for one name lookup would be a heavier dependency than the check deserves.
     *
     * Properties (`events`, `requests`, `connection`) never appear, because a Kotlin interface
     * property is an accessor rather than a method.
     */
    private fun declaredClientMethods(): Set<String> =
        AppServerClient::class.java.declaredMethods
            .filterNot { it.isSynthetic }
            .map { it.name.substringBefore('-') }
            .toSet()

    @Test
    fun `every registry entry names a client method`() {
        val unmapped = ClientRequestMethod.entries
            .map { it.wire }
            .filterNot { it in wireToClientMethod }

        assertTrue(
            unmapped.isEmpty(),
            "These protocol methods have no AppServerClient function, so nothing can call them: " +
                unmapped.joinToString(", "),
        )
    }

    @Test
    fun `every mapped client method exists on the interface`() {
        val declared = declaredClientMethods()
        val absent = wireToClientMethod
            .filterValues { it !in declared }
            .map { (wire, method) -> "$wire -> $method()" }

        assertTrue(
            absent.isEmpty(),
            "The registry maps these wire methods to functions the interface does not declare: " +
                absent.joinToString(", "),
        )
    }

    @Test
    fun `every interface function is either a request or explicitly local`() {
        val mapped = wireToClientMethod.values.toSet()
        val unregistered = declaredClientMethods()
            .filter { it !in nonRequestMembers }
            .filterNot { it in mapped }
            .distinct()

        assertTrue(
            unregistered.isEmpty(),
            "These AppServerClient functions issue no registered protocol method, so either the " +
                "registry is missing an entry or the function is dead: " + unregistered.joinToString(", "),
        )
    }

    @Test
    fun `the mapping covers the whole registry and nothing else`() {
        val registry = ClientRequestMethod.entries.map { it.wire }.toSet()
        assertEquals(registry, wireToClientMethod.keys)
    }
}
