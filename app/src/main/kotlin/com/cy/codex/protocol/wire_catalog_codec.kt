package com.cy.codex.protocol

import com.cy.codex.protocol.protocol.array
import com.cy.codex.protocol.protocol.bool
import com.cy.codex.protocol.protocol.int
import com.cy.codex.protocol.protocol.long
import com.cy.codex.protocol.protocol.objectOrNull
import com.cy.codex.protocol.protocol.objectValue
import com.cy.codex.protocol.protocol.required
import com.cy.codex.protocol.protocol.strings
import com.cy.codex.protocol.protocol.text
import com.cy.codex.protocol.protocol.v2.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal object WireCatalogCodec {
    fun goal(o: JsonObject) = ThreadGoalUpdated(o.required("threadId"), o.required("objective"),
        GoalStatus.entries.find { it.wire == o.text("status") } ?: error("Unknown goal status: ${o.text("status")}"),
        o.long("tokenBudget"), o.int("tokensUsed") ?: 0, o.long("timeUsedSeconds") ?: 0)

    fun queued(o: JsonObject) = QueuedSubmission(o.required("id"), o.required("clientUserMessageId"), o.array("input").map { WireCodec.input(it) })

    fun project(o: JsonObject) = ProjectEntry(o.required("id"), o.required("name"),
        o.array("roots").firstOrNull()?.objectValue()?.text("path").orEmpty())

    fun section(o: JsonObject) = ThreadSection(o.required("id"), o.required("name"))

    fun skill(o: JsonObject) = SkillEntry(o.text("path") ?: o.required("name"), o.required("name"), o.text("description").orEmpty(),
        o.text("path").orEmpty(), o.bool("enabled") != false, SkillScope.fromWire(o.text("scope")))

    fun app(o: JsonObject) = AppInfo(o.required("id"), o.required("name"), o.text("description").orEmpty(), o.bool("isAccessible") == true)

    /** `v2::AppSummary`, the connector shape `plugin/install` returns in `appsNeedingAuth`. */
    fun appSummary(o: JsonObject) = AppSummary(
        id = o.required("id"),
        name = o.required("name"),
        description = o.text("description"),
        installUrl = o.text("installUrl"),
        category = o.text("category"),
    )

    fun plugin(o: JsonObject, marketplace: String) = PluginEntry(o.required("id"), o.required("name"),
        o.objectOrNull("interface")?.text("shortDescription") ?: o.objectOrNull("interface")?.text("description").orEmpty(),
        o.bool("installed") == true, o.text("localVersion") ?: o.text("version").orEmpty(), marketplace,
        remotePluginId = o.text("remotePluginId"),
        enabled = o.bool("enabled") != false,
        shareContext = o.objectOrNull("shareContext")?.let { context ->
            PluginShareContext(
                shareUrl = context.text("shareUrl"),
                discoverability = context.text("discoverability")?.let(PluginShareDiscoverability::fromWire),
                sharePrincipals = (context["sharePrincipals"] as? JsonArray)?.map { WireCodec.pluginSharePrincipals(it.objectValue()) },
            )
        })

    fun marketplace(o: JsonObject): MarketplaceEntry {
        val name = o.required("name")
        val plugins = o.array("plugins").map { plugin(it.objectValue(), name) }
        return MarketplaceEntry(name, o.text("path").orEmpty(), o.text("path") == null, plugins.size,
            description = o.objectOrNull("interface")?.text("description").orEmpty(), plugins = plugins)
    }

    fun marketplaceErrors(o: JsonObject) = o.array("marketplaceLoadErrors").map { value -> value.objectValue().let {
        MarketplaceLoadErrorInfo(it.text("marketplacePath").orEmpty(), it.text("message").orEmpty())
    } }

    fun pluginDetail(o: JsonObject): PluginDetail {
        val marketplace = o.required("marketplaceName")
        val summary = plugin(o.objectOrNull("summary") ?: error("Missing plugin summary"), marketplace)
        return PluginDetail(summary.id, summary.name, o.text("description") ?: summary.description, summary.version, marketplace, summary.installed,
            skills = o.array("skills").map { skill(it.objectValue()) },
            mcpServers = o.strings("mcpServers").map { McpServerStatusEntry(it, McpServerConnectionStatus.Starting) },
            apps = o.array("apps").map { app(it.objectValue()) })
    }
}
