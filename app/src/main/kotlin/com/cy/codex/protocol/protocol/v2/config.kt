package com.cy.codex.protocol.protocol.v2

import com.cy.codex.protocol.protocol.bool
import com.cy.codex.protocol.protocol.int
import com.cy.codex.protocol.protocol.objectOrNull
import com.cy.codex.protocol.protocol.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * `config/read`, `config/value/write`, `config/batchWrite`, `configRequirements/read`.
 *
 * Mirrors `schema/typescript/v2/{ConfigReadResponse, ConfigLayer, ConfigBatchWriteParams,
 * ConfigValueWriteParams, ConfigWriteResponse}.ts`.
 *
 * The effective config is carried as a raw [JsonElement] tree rather than a typed struct: on the
 * wire it *is* a nested map (the TOML `config.toml` parsed into JSON), and `ConfigLayer.config` is
 * untyped for exactly that reason. The typed projection the UI actually renders lives in
 * [ConfigSnapshot], which reads keys out of that tree.
 */

/** One contributing layer of the config stack, `config.layers[i]`. */
data class ConfigLayer(
    /** The layer's own TOML/JSON body, untyped — this is what `config/read` returns. */
    val config: JsonElement = JsonObject(emptyMap()),
    val name: ConfigLayerSource = ConfigLayerSource.User,
    val version: String = "",
    /** Set when a requirement disabled this layer; the settings page shows it as a notice. */
    val disabledReason: String? = null,
    /** File or project configuration directory reported by the server for this layer. */
    val sourcePath: String? = null,
)

/**
 * Where a layer came from.
 *
 * The protocol spells this as an internally-tagged enum over many variants (packaged defaults, MDM,
 * system, user, project, session flags, …). The phone renders a label and a precedence hint, so the
 * variants collapse onto the four that read differently in a settings row.
 */
enum class ConfigLayerSource(val wire: String, val label: String) {
    PackagedDefaults("packagedDefaults", "内置默认"),
    System("system", "系统"),
    User("user", "用户"),
    Project("project", "项目"),
    SessionFlags("sessionFlags", "本次会话"),
    Unknown("unknown", "其他"),
    ;

    companion object {
        fun fromWire(value: String?): ConfigLayerSource =
            entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

/** `config/read` response. */
data class ConfigReadResponse(
    val config: JsonElement = JsonObject(emptyMap()),
    val layers: List<ConfigLayer>? = null,
    /** `origins` maps a dotted key path to the layer that set it, for the "overridden by" hint. */
    val origins: Map<String, ConfigLayerOrigin> = emptyMap(),
) {
    /** The typed projection the settings page renders. */
    val snapshot: ConfigSnapshot get() = ConfigSnapshot.from(config)

    /**
     * Render the effective value at a dotted [keyPath] for display, or `null` when it is unset.
     *
     * Read from the raw tree rather than from [snapshot] on purpose: the sources card lists keys the
     * typed projection deliberately does not model (an arbitrary `features.*` flag, say), and a key
     * that is absent must render as absent rather than as a blank row.
     */
    fun displayValue(keyPath: String): String? = when (val value = readOrigin(config, keyPath)) {
        null, JsonNull -> null
        is JsonArray -> value.joinToString(", ") { renderScalar(it) }
        is JsonObject -> value.entries.joinToString(", ") { "${it.key}=${renderScalar(it.value)}" }
        is JsonPrimitive -> value.content
    }
}

private fun readOrigin(root: JsonElement, path: String): JsonElement? {
    var current: JsonElement = root
    for (segment in path.split('.')) {
        current = (current as? JsonObject)?.get(segment) ?: return null
    }
    return current
}

/** One-line rendering of a leaf; a nested container is summarised by its size. */
private fun renderScalar(value: JsonElement): String = when (value) {
    is JsonNull -> "null"
    is JsonArray -> "[${value.size}]"
    is JsonObject -> "{${value.size}}"
    is JsonPrimitive -> value.content
}

/** `origins[keyPath]`: which layer last set a key, and that layer's version. */
data class ConfigLayerOrigin(
    val name: ConfigLayerSource = ConfigLayerSource.Unknown,
    val version: String = "",
)

/** `config/read` params. */
data class ConfigReadParams(
    val cwd: String? = null,
    /** Include the per-layer bodies in the response; the settings page's "来源" section needs them. */
    val includeLayers: Boolean = true,
)

/** `config/value/write` params. */
data class ConfigValueWriteParams(
    val keyPath: String,
    val value: JsonElement,
    val mergeStrategy: MergeStrategy = MergeStrategy.Replace,
    /** `null` means "the user config file", which is what the settings page always writes. */
    val filePath: String? = null,
    val expectedVersion: String? = null,
)

/** One edit inside `config/batchWrite`. */
data class ConfigEdit(
    val keyPath: String,
    val value: JsonElement,
    val mergeStrategy: MergeStrategy = MergeStrategy.Replace,
)

/** `config/batchWrite` params: several edits applied as one write, so the file version moves once. */
data class ConfigBatchWriteParams(
    val edits: List<ConfigEdit>,
    val filePath: String? = null,
    val expectedVersion: String? = null,
    /** Re-read the user config after writing so the new values take effect without a restart. */
    val reloadUserConfig: Boolean = true,
)

/** How a written value combines with what is already at `keyPath`. */
enum class MergeStrategy(val wire: String) {
    /** Overwrite the key outright. */
    Replace("replace"),

    /** Deep-merge into an existing object/array. */
    Upsert("upsert"),
    ;

    companion object {
        fun fromWire(value: String?): MergeStrategy =
            entries.firstOrNull { it.wire == value } ?: Replace
    }
}

/** `config/…/write` response. */
data class ConfigWriteResponse(
    val filePath: String = "",
    val status: WriteStatus = WriteStatus.Ok,
    val version: String = "",
    /** Set when a higher-precedence layer shadows what was just written. */
    val overriddenMetadata: OverriddenMetadata? = null,
)

enum class WriteStatus(val wire: String) {
    Ok("ok"),

    /** Written, but a managed/CLI layer still wins; the UI must say so or the edit looks inert. */
    OkOverridden("okOverridden"),
    ;

    companion object {
        fun fromWire(value: String?): WriteStatus =
            entries.firstOrNull { it.wire == value } ?: Ok
    }
}

data class OverriddenMetadata(
    val message: String = "",
    val overridingLayer: ConfigLayerOrigin = ConfigLayerOrigin(),
    /** The value that actually takes effect, so the UI can show "期望 X，实际 Y". */
    val effectiveValue: JsonElement = JsonNull,
)

/** `configRequirements/read` response. */
data class ConfigRequirementsReadResponse(
    /** Raw `requirements.toml`, untyped for the same reason as the config body. */
    val requirements: JsonElement = JsonObject(emptyMap()),
) {
    /**
     * The reviewers managed policy permits, or `null` when it does not restrict them.
     *
     * Mirrors `allowed_approvals_reviewers`: an absent or non-array field means "any reviewer",
     * which is different from an empty list ("none").
     */
    val allowedApprovalsReviewers: List<ApprovalsReviewer>?
        get() {
            val raw = (requirements as? JsonObject)?.get("allowedApprovalsReviewers") as? JsonArray
                ?: return null
            return raw.mapNotNull { element ->
                (element as? JsonPrimitive)?.content?.let { wire ->
                    ApprovalsReviewer.entries.find { it.wire == wire }
                }
            }
        }
}

/**
 * The subset of `config.toml` the phone renders, read out of the raw tree.
 *
 * Mirrors the `Config` struct's shape (not its field-for-field contents): only the keys the
 * settings page, the composer and the status card show. Everything else stays in the [JsonElement]
 * tree and is passed through untouched by a write.
 */
data class ConfigSnapshot(
    val model: String? = null,
    val modelProvider: String? = null,
    val modelReasoningEffort: ReasoningEffort? = null,
    val modelReasoningSummary: String? = null,
    val modelVerbosity: String? = null,
    val approvalPolicy: AskForApproval? = null,
    val approvalsReviewer: ApprovalsReviewer? = null,
    val sandboxMode: SandboxMode? = null,
    val sandboxWorkspaceWrite: List<String> = emptyList(),
    val sandboxNetworkAccess: Boolean? = null,
    val disableResponseStorage: Boolean? = null,
    val hideAgentReasoning: Boolean? = null,
    val showRawAgentReasoning: Boolean? = null,
    val modelContextWindow: Int? = null,
    val reviewModel: String? = null,
    val tuiAlternateScreen: String? = null,
    /** `[mcp_servers]` keys, in file order. */
    val mcpServerNames: List<String> = emptyList(),
    /** `[projects]` keys — the trusted directories. */
    val trustedProjects: List<String> = emptyList(),
    /** `oss_provider`: which local server an `oss` model provider talks to. */
    val ossProvider: String? = null,
    /** `[features]` entries. */
    val features: Map<String, Boolean> = emptyMap(),
    val notifications: Boolean? = null,
    val historyPersistence: String? = null,
    /** `[memories] use_memories`: inject stored memories into future threads. */
    val useMemories: Boolean? = null,
    /** `[memories] generate_memories`: consolidate threads into the store. */
    val generateMemories: Boolean? = null,
    /** `[notices] hide_rate_limit_model_nudge`: the user asked never to see the switch prompt. */
    val hideRateLimitModelNudge: Boolean? = null,
    /** `[experimental]` keys that are on. */
    val experimental: Map<String, Boolean> = emptyMap(),
) {
    companion object {
        /**
         * Read the rendered keys out of a raw config tree.
         *
         * Tolerant on purpose: a config file is hand-written, so a key may be absent, spelled in
         * either case, or of the wrong type, and the page must still render the keys it did get.
         */
        fun from(tree: JsonElement): ConfigSnapshot {
            val root = tree as? JsonObject ?: return ConfigSnapshot()
            fun obj(key: String): JsonObject? = root.objectOrNull(key)
            fun str(key: String): String? = root[key]?.stringOrNull()
            fun bool(key: String): Boolean? = root.bool(key)
            fun int(key: String): Int? = root.int(key)
            fun flags(key: String): Map<String, Boolean> =
                obj(key).orEmpty().mapNotNull { (k, v) ->
                    (v as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull?.let { k to it }
                }.toMap()

            val sandbox = obj("sandbox_workspace_write")
            return ConfigSnapshot(
                model = str("model"),
                modelProvider = str("model_provider"),
                modelReasoningEffort = str("model_reasoning_effort")?.let(ReasoningEffort::fromWire),
                modelReasoningSummary = str("model_reasoning_summary"),
                modelVerbosity = str("model_verbosity"),
                approvalPolicy = str("approval_policy")?.let(AskForApproval::fromWire),
                approvalsReviewer = str("approvals_reviewer")?.let(ApprovalsReviewer::fromWire),
                sandboxMode = str("sandbox_mode")?.let(SandboxMode::fromWire),
                sandboxWorkspaceWrite = (sandbox?.get("writable_roots") as? JsonArray)
                    .orEmpty().mapNotNull { it.stringOrNull() },
                sandboxNetworkAccess = (sandbox?.get("network_access") as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull,
                disableResponseStorage = bool("disable_response_storage"),
                hideAgentReasoning = bool("hide_agent_reasoning"),
                showRawAgentReasoning = bool("show_raw_agent_reasoning"),
                modelContextWindow = int("model_context_window"),
                reviewModel = str("review_model"),
                tuiAlternateScreen = str("tui_alternate_screen"),
                mcpServerNames = obj("mcp_servers")?.keys?.toList().orEmpty(),
                trustedProjects = obj("projects")?.keys?.toList().orEmpty(),
                ossProvider = str("oss_provider"),
                features = flags("features"),
                notifications = bool("notifications"),
                historyPersistence = str("history"),
                useMemories = obj("memories")?.bool("use_memories"),
                generateMemories = obj("memories")?.bool("generate_memories"),
                hideRateLimitModelNudge = obj("notices")?.bool("hide_rate_limit_model_nudge"),
                experimental = flags("experimental"),
            )
        }
    }
}
