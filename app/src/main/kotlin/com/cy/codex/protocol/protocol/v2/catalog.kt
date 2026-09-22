package com.cy.codex.protocol.protocol.v2

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * The catalog families: plugins, marketplaces, apps, skills config, MCP tools and memory.
 *
 * Mirrors `schema/typescript/v2/{Plugin*, Marketplace*, Apps*, Skills*, Mcp*}.ts`.
 *
 * "Catalog" here means anything the settings surfaces *list and then act on*: the list response is
 * modelled as the flat row type the screen renders (see [PluginEntry] and friends in
 * `thread_data.kt`), while the action requests carry the ids needed to address one row.
 */

// ---------------------------------------------------------------------------------------------
// plugins and marketplaces
// ---------------------------------------------------------------------------------------------

/** Which kinds of marketplace a `plugin/list` call may consider. */
enum class PluginListMarketplaceKind(val wire: String) {
    Local("local"),
    Vertical("vertical"),
    WorkspaceDirectory("workspace-directory"),
    SharedWithMe("shared-with-me"),
    CreatedByMeRemote("created-by-me-remote"),
}

/** `plugin/list` — the marketplace catalog, and therefore where the installed rows come from. */
data class PluginListParams(
    /**
     * Working directories used to discover repo marketplaces.
     *
     * When omitted, only home-scoped marketplaces and the official curated marketplace are
     * considered.
     */
    val cwds: List<String>? = null,
    /** Whether the client requests a fresh remote plugin catalog fetch. */
    val forceRefetch: Boolean = false,
    /**
     * Marketplace kind filter.
     *
     * When omitted, only local marketplaces are queried, plus the default remote catalog when the
     * feature flag enables it.
     */
    val marketplaceKinds: List<PluginListMarketplaceKind>? = null,
)

/**
 * `plugin/list`.
 *
 * There is deliberately no `marketplace/list` in the protocol: the marketplace catalog *is* this
 * response, and every plugin row hangs off one of its entries. A client that wants "which
 * marketplaces exist" reads [marketplaces] here rather than calling a method of its own.
 */
data class PluginListResponse(
    val marketplaces: List<MarketplaceEntry> = emptyList(),
    /** Ids the server wants surfaced first; the UI pins these above the rest. */
    val featuredPluginIds: List<String> = emptyList(),
    /** Marketplaces that failed to load; the catalog still renders the ones that did. */
    val marketplaceLoadErrors: List<MarketplaceLoadErrorInfo> = emptyList(),
)

/** One marketplace that could not be read. */
data class MarketplaceLoadErrorInfo(
    val marketplacePath: String = "",
    val message: String = "",
)

/** `plugin/install`. */
data class PluginInstallParams(
    val pluginName: String,
    /** Local marketplace path; mutually exclusive with [remoteMarketplaceName]. */
    val marketplacePath: String? = null,
    val remoteMarketplaceName: String? = null,
    val installAttemptId: String? = null,
)

/**
 * `plugin/install` response.
 *
 * Mirrors `v2::PluginInstallResponse`: the install itself is done, and what comes back is the
 * follow-up — the auth policy and the connectors this plugin needs the user to set up before it
 * can run. The client reads the installed plugin back through `plugin/read`.
 */
data class PluginInstallResponse(
    val authPolicy: PluginAuthPolicy = PluginAuthPolicy.OnUse,
    val appsNeedingAuth: List<AppSummary> = emptyList(),
)

/** `v2::PluginAuthPolicy`: when the plugin's connectors are asked to authorize. */
enum class PluginAuthPolicy(val wire: String) {
    OnInstall("ON_INSTALL"),
    OnUse("ON_USE"),
    ;

    companion object {
        fun fromWire(value: String?): PluginAuthPolicy =
            entries.firstOrNull { it.wire == value } ?: OnUse
    }
}

/** `v2::AppSummary`: the connector facts the plugin-install flow shows. */
data class AppSummary(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    /** Where the connector is installed; opened in a browser by the setup sheet. */
    val installUrl: String? = null,
    val category: String? = null,
)

/** `plugin/uninstall`. */
data class PluginUninstallParams(val pluginId: String)

/** `plugin/read` — the detail page behind one row. */
data class PluginReadParams(
    val pluginName: String,
    val marketplacePath: String? = null,
    val remoteMarketplaceName: String? = null,
)

data class PluginReadResponse(val plugin: PluginDetail = PluginDetail())

/** One plugin's full record, as the detail page renders it. */
data class PluginDetail(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val version: String = "",
    val marketplace: String = "",
    val installed: Boolean = false,
    val author: String? = null,
    val homepage: String? = null,
    /** Skills the plugin contributes. */
    val skills: List<SkillEntry> = emptyList(),
    /** MCP servers the plugin contributes. */
    val mcpServers: List<McpServerStatusEntry> = emptyList(),
    /** Apps/connectors the plugin contributes. */
    val apps: List<AppInfo> = emptyList(),
    val readme: String? = null,
) {
    /** Fold back to the list row, so one plugin has one representation per screen. */
    fun toEntry(): PluginEntry = PluginEntry(
        id = id.ifEmpty { name },
        name = name.ifEmpty { id },
        description = description,
        installed = installed,
        version = version,
        marketplace = marketplace,
    )
}

/**
 * `plugin/installed` — what is present, which is again the marketplace catalog.
 *
 * Like [PluginListResponse] this carries marketplaces rather than a flat plugin list, because a
 * plugin only ever exists *inside* a marketplace. Which rows are installed is read off
 * [PluginEntry.installed] on each entry's `plugins`.
 */
data class PluginInstalledParams(
    /** Working directories used to discover repo marketplaces. */
    val cwds: List<String>? = null,
    /**
     * Uninstalled plugin names that should still be returned when present locally.
     *
     * Mention surfaces use this to offer an install entrypoint for a plugin they can see but the
     * account has not installed.
     */
    val installSuggestionPluginNames: List<String>? = null,
)

data class PluginInstalledResponse(
    val marketplaces: List<MarketplaceEntry> = emptyList(),
    val marketplaceLoadErrors: List<MarketplaceLoadErrorInfo> = emptyList(),
)

/** `plugin/reconcile` — re-resolve every installed plugin against its marketplace. */
data class PluginReconcileParams(val reason: String? = null)

data class PluginReconcileResponse(
    val changed: List<PluginEntry> = emptyList(),
    val summary: String? = null,
)

/** `plugin/search`. */
data class PluginSearchParams(
    val searchTerm: String,
    val cursor: String? = null,
    val limit: Int? = null,
    val scope: String? = null,
    val cwds: List<String>? = null,
)

data class PluginSearchResponse(
    val data: List<PluginEntry> = emptyList(),
    val nextCursor: String? = null,
)

/** `plugin/skill/read` — the body of one skill a plugin ships. */
data class PluginSkillReadParams(
    val remoteMarketplaceName: String,
    val remotePluginId: String,
    val skillName: String,
)

data class PluginSkillReadResponse(val contents: String? = null)

/** How widely a shared plugin is discoverable; the wire values are uppercase. */
enum class PluginShareDiscoverability(val wire: String) {
    Listed("LISTED"),
    Unlisted("UNLISTED"),
    Private("PRIVATE"),
    ;

    companion object {
        fun fromWire(value: String?): PluginShareDiscoverability =
            entries.firstOrNull { it.wire == value } ?: Private
    }
}

/** Who a share targets, and with which role. */
data class PluginShareTarget(
    /** `user`, `group` or `workspace`. */
    val principalType: String,
    val principalId: String,
    /** `reader` or `editor`. */
    val role: String = "reader",
)

/** A resolved share principal; the server adds the display [name] and may report `owner`. */
data class PluginSharePrincipal(
    val principalType: String = "",
    val principalId: String = "",
    /** `reader`, `editor` or `owner`. */
    val role: String = "reader",
    val name: String = "",
)

/** The sharing context the server attaches to a plugin summary. */
data class PluginShareContext(
    val shareUrl: String? = null,
    val discoverability: PluginShareDiscoverability? = null,
    val sharePrincipals: List<PluginSharePrincipal>? = null,
)

/** One installed plugin that the account has shared or can share. */
data class PluginShareEntry(
    val plugin: PluginEntry,
    /** Local checkout of this share, when one exists. */
    val localPluginPath: String? = null,
)

/** `plugin/share/list`. */
data class PluginShareListResponse(val data: List<PluginShareEntry> = emptyList())

/**
 * `plugin/share/save` params.
 *
 * [pluginPath] is the local plugin package; everything else is optional and only present when the
 * caller is updating an existing share rather than creating one.
 */
data class PluginShareSaveParams(
    val pluginPath: String,
    val remotePluginId: String? = null,
    val discoverability: PluginShareDiscoverability? = null,
    val shareTargets: List<PluginShareTarget>? = null,
)

data class PluginShareSaveResponse(
    val remotePluginId: String = "",
    val shareUrl: String = "",
    val canPublishToWorkspace: Boolean? = null,
)

data class PluginShareDeleteParams(val remotePluginId: String)

data class PluginShareCheckoutParams(val remotePluginId: String)

data class PluginShareCheckoutResponse(
    val remotePluginId: String = "",
    val pluginId: String = "",
    val pluginName: String = "",
    val pluginPath: String = "",
    val marketplaceName: String = "",
    val marketplacePath: String = "",
    val remoteVersion: String? = null,
)

data class PluginShareUpdateTargetsParams(
    val remotePluginId: String,
    val discoverability: PluginShareDiscoverability,
    val shareTargets: List<PluginShareTarget> = emptyList(),
)

data class PluginShareUpdateTargetsResponse(
    val principals: List<PluginSharePrincipal> = emptyList(),
    val discoverability: PluginShareDiscoverability = PluginShareDiscoverability.Private,
)

/**
 * One marketplace the account can install from.
 *
 * A flattened projection of the protocol's `PluginMarketplaceEntry`: the screen renders a name, a
 * path and a count, so the nested `interface` and per-plugin records are folded into the fields
 * below. [plugins] keeps the association the protocol makes, which is what lets a caller answer
 * "what is installed" without a method of its own; [pluginCount] stays separate because a
 * marketplace legitimately advertises more plugins than the client has records for.
 */
data class MarketplaceEntry(
    val name: String,
    val path: String = "",
    val isRemote: Boolean = false,
    val pluginCount: Int = 0,
    val sharedWithMe: Boolean = false,
    val description: String = "",
    /** The plugin records this marketplace contributed, installed or not. */
    val plugins: List<PluginEntry> = emptyList(),
)

/** `marketplace/add`. */
data class MarketplaceAddParams(
    val source: String,
    val refName: String? = null,
    val sparsePaths: List<String>? = null,
)

data class MarketplaceAddResponse(val marketplace: MarketplaceEntry = MarketplaceEntry(name = ""))

/** `marketplace/remove`. */
data class MarketplaceRemoveParams(val marketplaceName: String)

/** `marketplace/upgrade`; `null` upgrades every marketplace. */
data class MarketplaceUpgradeParams(val marketplaceName: String? = null)

data class MarketplaceUpgradeResponse(
    val upgraded: List<String> = emptyList(),
    val summary: String? = null,
)

// ---------------------------------------------------------------------------------------------
// apps / connectors
// ---------------------------------------------------------------------------------------------

/** `app/read` — one connector's detail, optionally with its tool list. */
data class AppsReadParams(
    val appIds: List<String>,
    val includeTools: Boolean = false,
    val threadId: String? = null,
)

data class AppsReadResponse(
    val apps: List<AppInfo> = emptyList(),
    /** Ids the server does not know; the UI shows them as unavailable rather than dropping them. */
    val missingAppIds: List<String> = emptyList(),
)

/** `app/installed`. */
data class AppsInstalledParams(
    val forceRefresh: Boolean = false,
    val threadId: String? = null,
)

data class AppsInstalledResponse(val apps: List<AppInfo> = emptyList())

// ---------------------------------------------------------------------------------------------
// skills config
// ---------------------------------------------------------------------------------------------

/** `skills/config/write` — enable/disable one skill, addressed by name or by path. */
data class SkillsConfigWriteParams(
    val enabled: Boolean,
    val name: String? = null,
    val path: String? = null,
)

/** `skills/extraRoots/set` — the directories searched for skills beyond the defaults. */
data class SkillsExtraRootsSetParams(val extraRoots: List<String> = emptyList())

// ---------------------------------------------------------------------------------------------
// MCP beyond the status list
// ---------------------------------------------------------------------------------------------

/** `mcpServer/oauth/login`. */
data class McpServerOauthLoginParams(
    val name: String,
    val scopes: List<String>? = null,
    val threadId: String? = null,
    val timeoutSecs: Int? = null,
)

data class McpServerOauthLoginResponse(val authorizationUrl: String = "")

/** `mcpServer/tool/call` — call a tool on a connected server, outside a turn. */
data class McpServerToolCallParams(
    val server: String,
    val tool: String,
    val arguments: String = "{}",
    val threadId: String? = null,
)

data class McpServerToolCallResponse(
    val result: String = "",
    val isError: Boolean = false,
)

/** `mcpServer/resource/read`. */
data class McpResourceReadParams(
    val server: String,
    val uri: String,
    val threadId: String? = null,
)

/** One resource body. Mirrors `ResourceContent`: either inline [text] or base64 [blob]. */
data class ResourceContent(
    val uri: String,
    val mimeType: String? = null,
    val text: String? = null,
    val blob: String? = null,
)

/** `mcpServer/resource/read` response. Mirrors upstream: a list of contents, not one flat body. */
data class McpResourceReadResponse(
    val contents: List<ResourceContent>,
    val originCallId: String? = null,
)

/** `mcpServer/oauthLogin/completed`. */
data class McpServerOauthLoginCompletedNotification(
    val name: String,
    val success: Boolean = true,
    val error: String? = null,
)

/** `mcpServer/event/stream/…`. */
data class McpServerEventStreamStartParams(
    val server: String,
    val threadId: String? = null,
)

data class McpServerEventStreamStopParams(
    val server: String,
    val threadId: String? = null,
)

/** `mcpServer/event/stream/notification`: one server-pushed JSON-RPC notification. */
data class McpServerEventStreamNotification(
    val subscriptionId: String,
    val notification: McpServerEventNotification,
)

/** The inner `{method, params}` pair of an event-stream notification, verbatim from the server. */
data class McpServerEventNotification(
    val method: String,
    val params: JsonElement = JsonNull,
)

// ---------------------------------------------------------------------------------------------
// memory
// ---------------------------------------------------------------------------------------------

/** `memory/status`. */
data class MemoryStatusParams(val minConsolidatedThreads: Int? = null)

data class MemoryStatusResponse(
    val v2Ready: Boolean = false,
    val v2ConsolidatedThreads: Int = 0,
)
