package com.cy.codex.protocol

import com.cy.codex.protocol.protocol.Json
import com.cy.codex.protocol.protocol.v2.Account
import com.cy.codex.protocol.protocol.v2.AccountRateLimits
import com.cy.codex.protocol.protocol.v2.AccountReadResponse
import com.cy.codex.protocol.protocol.v2.ByteRange
import com.cy.codex.protocol.protocol.v2.ClientInfo
import com.cy.codex.protocol.protocol.v2.ClientRequestMethod
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalParams
import com.cy.codex.protocol.protocol.v2.CommandExecutionStatus
import com.cy.codex.protocol.protocol.v2.CreditsSnapshot
import com.cy.codex.protocol.protocol.v2.DynamicToolCallParams
import com.cy.codex.protocol.protocol.v2.FileChangeApprovalParams
import com.cy.codex.protocol.protocol.v2.GitInfo
import com.cy.codex.protocol.protocol.v2.GoalStatus
import com.cy.codex.protocol.protocol.v2.InitializeCapabilities
import com.cy.codex.protocol.protocol.v2.InitializeParams
import com.cy.codex.protocol.protocol.v2.InitializeResponse
import com.cy.codex.protocol.protocol.v2.InputModality
import com.cy.codex.protocol.protocol.v2.McpResourceReadResponse
import com.cy.codex.protocol.protocol.v2.McpServerConnectionStatus
import com.cy.codex.protocol.protocol.v2.McpServerEventNotification
import com.cy.codex.protocol.protocol.v2.McpServerEventStreamNotification
import com.cy.codex.protocol.protocol.v2.McpToolCallStatus
import com.cy.codex.protocol.protocol.v2.ModelPreset
import com.cy.codex.protocol.protocol.v2.MisalignmentErrorDetails
import com.cy.codex.protocol.protocol.v2.MisalignmentSteer
import com.cy.codex.protocol.protocol.v2.ModelSafetyBufferingUpdatedNotification
import com.cy.codex.protocol.protocol.v2.ModelServiceTier
import com.cy.codex.protocol.protocol.v2.NetworkPolicyRuleAction
import com.cy.codex.protocol.protocol.v2.PatchApplyStatus
import com.cy.codex.protocol.protocol.v2.PermissionsApprovalParams
import com.cy.codex.protocol.protocol.v2.RateLimitResetCredit
import com.cy.codex.protocol.protocol.v2.RateLimitResetCreditsSummary
import com.cy.codex.protocol.protocol.v2.RateLimitSnapshot
import com.cy.codex.protocol.protocol.v2.RateLimitWindow
import com.cy.codex.protocol.protocol.v2.ServerNotificationMethod
import com.cy.codex.protocol.protocol.v2.ServerRequestMethod
import com.cy.codex.protocol.protocol.v2.SkillScope
import com.cy.codex.protocol.protocol.v2.SortDirection
import com.cy.codex.protocol.protocol.v2.Thread
import com.cy.codex.protocol.protocol.v2.ThreadActiveFlag
import com.cy.codex.protocol.protocol.v2.ThreadForkParams
import com.cy.codex.protocol.protocol.v2.ThreadGoalUpdated
import com.cy.codex.protocol.protocol.v2.ThreadItemsListParams
import com.cy.codex.protocol.protocol.v2.ThreadListParams
import com.cy.codex.protocol.protocol.v2.ThreadMemoryMode
import com.cy.codex.protocol.protocol.v2.ThreadReadParams
import com.cy.codex.protocol.protocol.v2.ThreadResumeInitialTurnsPageParams
import com.cy.codex.protocol.protocol.v2.ThreadResumeParams
import com.cy.codex.protocol.protocol.v2.ThreadSection
import com.cy.codex.protocol.protocol.v2.ThreadSortKey
import com.cy.codex.protocol.protocol.v2.ThreadStartParams
import com.cy.codex.protocol.protocol.v2.ThreadTokenUsage
import com.cy.codex.protocol.protocol.v2.ThreadTurnsListParams
import com.cy.codex.protocol.protocol.v2.TextElement
import com.cy.codex.protocol.protocol.v2.TokenUsageBreakdown
import com.cy.codex.protocol.protocol.v2.ToolRequestUserInputParams
import com.cy.codex.protocol.protocol.v2.TurnItemsView
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.TurnsPage
import com.cy.codex.protocol.protocol.v2.WorkspaceMessage
import com.cy.codex.protocol.protocol.v2.WorkspaceMessageType
import com.cy.codex.protocol.protocol.v2.WorkspaceMessagesResponse
import com.github.luben.zstd.ZstdInputStream
import java.io.File
import java.io.FileInputStream
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Compares the hand-written Kotlin protocol types against the schema the server generates.
 *
 * Every other protocol test in this module is self-referential: it builds a payload from the same
 * Kotlin types the client decodes with, so a field the server does not have, an enum value that no
 * longer exists or a method that was never registered all pass. This test reads the upstream
 * schemas — `codex-rs/app-server-protocol/schema/json/` for the field shapes and the precomputed
 * experimental export for the full method list — and checks the Kotlin declarations against them.
 *
 * The rules, in the order the schema itself allows them:
 *
 *  - A Kotlin field must exist upstream. Dropping fields is allowed (the phone does not render
 *    everything); inventing one is not, because a decoder that reads a key the server never sends
 *    silently produces a wrong value and an encoder can put a bogus key on the wire.
 *  - A field Kotlin requires must be one upstream guarantees. Kotlin may default a field upstream
 *    also sends — that is leniency, not drift — but it may never demand more than the schema
 *    promises, because that turns a legal response into a decode failure.
 *  - An enum's wire set must be equal, not a subset: a value the server added is a mode the phone
 *    silently falls back from, and a value the server removed is one the phone will never see.
 *  - The three method registries must be exactly the upstream sets, with one documented exception
 *    (`mock/experimentalMethod`, an upstream test scaffold).
 *
 * The mappings below are deliberately written out: a Kotlin type and its upstream definition often
 * have different names (`ModelPreset` mirrors `Model`, `AccountRateLimits` mirrors
 * `GetAccountRateLimitsResponse`), so deriving them automatically would need a convention the
 * protocol does not have. Adding a type to a list is a deliberate act; a rename on either side
 * makes this test fail until the table says so.
 */
class UpstreamSchemaTest {

    private val repositoryRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "codex/codex-rs/app-server-protocol/schema/json").isDirectory }
        ?: fail(
            "Could not find codex/codex-rs/app-server-protocol/schema/json above the test working " +
                "directory; initialize the codex submodule before running the protocol tests.",
        )

    private val schemaDirectory = File(repositoryRoot, "codex/codex-rs/app-server-protocol/schema/json")

    private val experimentalExports = File(
        repositoryRoot,
        "codex/codex-rs/app-server-protocol/schema/precomputed/app-server-exports-experimental.json.zst",
    )

    /**
     * Every `definitions` entry from every schema file, plus the per-file top-level schemas.
     *
     * The on-disk `schema/json/` tree is the stable generation, so it strips experimental-gated
     * fields (`availableDecisions`, for one). The experimental export is laid over it last, because
     * this client advertises `experimentalApi` and therefore really receives those fields.
     */
    private val definitions: Map<String, JsonObject> by lazy {
        val merged = LinkedHashMap<String, JsonObject>()
        schemaDirectory.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .sortedBy { it.path }
            .forEach { file ->
                val root = runCatching { Json.parse(file.readText()).jsonObject }.getOrNull() ?: return@forEach
                root["definitions"]?.jsonObject?.forEach { (name, value) ->
                    (value as? JsonObject)?.let { merged[name] = it }
                }
                if (root["properties"] is JsonObject) merged[file.nameWithoutExtension] = root
            }
        experimentalSchemas.forEach { (file, root) ->
            root["definitions"]?.jsonObject?.forEach { (name, value) ->
                (value as? JsonObject)?.let { merged[name] = it }
            }
            if (root["properties"] is JsonObject) merged[file.substringAfterLast('/').removeSuffix(".json")] = root
        }
        merged
    }

    /** The experimental-inclusive exports: `{ "json_schema": { "File.json": "<schema JSON>" } }`. */
    private val experimentalSchemas: Map<String, JsonObject> by lazy {
        val text = ZstdInputStream(FileInputStream(experimentalExports)).use { it.readBytes() }.decodeToString()
        val schemas = Json.parse(text).jsonObject["json_schema"]?.jsonObject
            ?: fail("$experimentalExports has no json_schema object")
        schemas.mapValues { (_, value) -> Json.parse(value.jsonPrimitive.content).jsonObject }
    }

    // ---- field names and requiredness ---------------------------------------------------------

    private val fieldMappings: List<Pair<KClass<*>, String>> = listOf(
        Thread::class to "Thread",
        GitInfo::class to "GitInfo",
        ThreadGoalUpdated::class to "ThreadGoal",
        MisalignmentErrorDetails::class to "MisalignmentErrorDetails",
        MisalignmentSteer::class to "MisalignmentSteer",
        ByteRange::class to "ByteRange",
        TextElement::class to "TextElement",
        ThreadTokenUsage::class to "ThreadTokenUsage",
        TokenUsageBreakdown::class to "TokenUsageBreakdown",
        ModelPreset::class to "Model",
        ModelServiceTier::class to "ModelServiceTier",
        RateLimitSnapshot::class to "RateLimitSnapshot",
        RateLimitWindow::class to "RateLimitWindow",
        CreditsSnapshot::class to "CreditsSnapshot",
        RateLimitResetCreditsSummary::class to "RateLimitResetCreditsSummary",
        RateLimitResetCredit::class to "RateLimitResetCredit",
        AccountRateLimits::class to "GetAccountRateLimitsResponse",
        AccountReadResponse::class to "GetAccountResponse",
        WorkspaceMessagesResponse::class to "GetWorkspaceMessagesResponse",
        WorkspaceMessage::class to "WorkspaceMessage",
        McpResourceReadResponse::class to "McpResourceReadResponse",
        InitializeResponse::class to "InitializeResponse",
        InitializeParams::class to "InitializeParams",
        InitializeCapabilities::class to "InitializeCapabilities",
        ClientInfo::class to "ClientInfo",
        ThreadListParams::class to "ThreadListParams",
        ThreadItemsListParams::class to "ThreadItemsListParams",
        ThreadTurnsListParams::class to "ThreadTurnsListParams",
        ThreadReadParams::class to "ThreadReadParams",
        ThreadResumeParams::class to "ThreadResumeParams",
        ThreadResumeInitialTurnsPageParams::class to "ThreadResumeInitialTurnsPageParams",
        TurnsPage::class to "TurnsPage",
        ThreadStartParams::class to "ThreadStartParams",
        ThreadForkParams::class to "ThreadForkParams",
        ThreadSection::class to "ThreadSection",
        CommandExecutionApprovalParams::class to "CommandExecutionRequestApprovalParams",
        FileChangeApprovalParams::class to "FileChangeRequestApprovalParams",
        PermissionsApprovalParams::class to "PermissionsRequestApprovalParams",
        ToolRequestUserInputParams::class to "ToolRequestUserInputParams",
        DynamicToolCallParams::class to "DynamicToolCallParams",
        McpServerEventStreamNotification::class to "McpServerEventStreamNotification",
        McpServerEventNotification::class to "McpServerEventNotification",
        ModelSafetyBufferingUpdatedNotification::class to "ModelSafetyBufferingUpdatedNotification",
    )

    @Test
    fun `Kotlin fields exist upstream and never demand more than the schema promises`() {
        val failures = mutableListOf<String>()
        for ((kotlinType, upstreamName) in fieldMappings) {
            val shape = runCatching { shapeOf(upstreamName) }.getOrElse {
                failures += "${kotlinType.simpleName}: ${it.message}"
                continue
            }
            val parameters = kotlinType.primaryConstructor?.parameters
                ?: fail("${kotlinType.simpleName} has no primary constructor")
            val names = parameters.mapNotNull { it.name }.toSet()

            (names - shape.properties).takeIf { it.isNotEmpty() }?.let {
                failures += "${kotlinType.simpleName} declares field(s) $upstreamName does not have: ${it.sorted()}"
            }
            val required = parameters.filterNot { it.isOptional }.mapNotNull { it.name }.toSet()
            (required - shape.required).takeIf { it.isNotEmpty() }?.let {
                failures += "${kotlinType.simpleName} requires field(s) $upstreamName does not guarantee: ${it.sorted()}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `Account union variants carry upstream field names`() {
        val variants = definitions["Account"]?.get("oneOf")?.jsonArray?.map { it.jsonObject }
            ?: fail("upstream has no Account union")
        val chatgpt = variants.single { it.typeTag() == "chatgpt" }
        val bedrock = variants.single { it.typeTag() == "amazonBedrock" }

        val chatgptFields = Account.Chatgpt::class.primaryConstructor!!.parameters.mapNotNull { it.name }.toSet()
        val bedrockFields = Account.AmazonBedrock::class.primaryConstructor!!.parameters.mapNotNull { it.name }.toSet()
        val chatgptProperties = chatgpt["properties"]!!.jsonObject.keys
        val bedrockProperties = bedrock["properties"]!!.jsonObject.keys
        assertTrue(
            chatgptFields.all { it in chatgptProperties },
            "Account.Chatgpt declares ${chatgptFields - chatgptProperties}",
        )
        assertTrue(
            bedrockFields.all { it in bedrockProperties },
            "Account.AmazonBedrock declares ${bedrockFields - bedrockProperties}",
        )
    }

    // ---- enums --------------------------------------------------------------------------------

    private val enumMappings: List<Pair<Class<out Enum<*>>, String>> = listOf(
        ThreadMemoryMode::class.java to "ThreadMemoryMode",
        ThreadActiveFlag::class.java to "ThreadActiveFlag",
        GoalStatus::class.java to "ThreadGoalStatus",
        TurnStatus::class.java to "TurnStatus",
        CommandExecutionStatus::class.java to "CommandExecutionStatus",
        PatchApplyStatus::class.java to "PatchApplyStatus",
        McpToolCallStatus::class.java to "McpToolCallStatus",
        McpServerConnectionStatus::class.java to "McpServerConnectionStatus",
        SkillScope::class.java to "SkillScope",
        SortDirection::class.java to "SortDirection",
        ThreadSortKey::class.java to "ThreadSortKey",
        TurnItemsView::class.java to "TurnItemsView",
        InputModality::class.java to "InputModality",
        NetworkPolicyRuleAction::class.java to "NetworkPolicyRuleAction",
        WorkspaceMessageType::class.java to "WorkspaceMessageType",
    )

    @Test
    fun `enum wire values equal the upstream sets`() {
        val failures = mutableListOf<String>()
        for ((kotlinEnum, upstreamName) in enumMappings) {
            val upstream = runCatching { enumValuesOf(upstreamName) }.getOrElse {
                failures += "${kotlinEnum.simpleName}: ${it.message}"
                continue
            }
            val kotlin = kotlinEnum.enumConstants.map { constant ->
                kotlinEnum.getMethod("getWire").invoke(constant) as String
            }.toSet()
            if (kotlin != upstream) {
                failures += "${kotlinEnum.simpleName}: kotlin=${kotlin.sorted()} upstream=${upstream.sorted()}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `command approval decision union keeps the payload variants`() {
        val variants = definitions["CommandExecutionApprovalDecision"]?.get("oneOf")?.jsonArray?.map { it.jsonObject }
            ?: fail("upstream has no CommandExecutionApprovalDecision union")
        val scalar = variants.mapNotNull { it["enum"]?.jsonArray?.single()?.jsonPrimitive?.content }.toSet()
        val payloadKeys = variants.flatMap { it["properties"]?.jsonObject?.keys.orEmpty() }.toSet()
        assertEquals(setOf("accept", "acceptForSession", "decline", "cancel"), scalar)
        assertEquals(setOf("acceptWithExecpolicyAmendment", "applyNetworkPolicyAmendment"), payloadKeys)
    }

    // ---- method registries --------------------------------------------------------------------

    @Test
    fun `server notification registry equals the upstream method set`() {
        assertEquals(methodSet("ServerNotification.json"), ServerNotificationMethod.entries.map { it.wire }.toSet())
    }

    @Test
    fun `server request registry equals the upstream method set`() {
        assertEquals(methodSet("ServerRequest.json"), ServerRequestMethod.entries.map { it.wire }.toSet())
    }

    @Test
    fun `client request registry equals upstream except the mock test scaffold`() {
        val upstream = methodSet("ClientRequest.json")
        val registered = ClientRequestMethod.entries.map { it.wire }.toSet()
        assertEquals(
            setOf("mock/experimentalMethod"),
            upstream - registered,
            "The only upstream client method this registry may omit is the upstream test scaffold.",
        )
        assertTrue(
            (registered - upstream).isEmpty(),
            "Registered methods upstream does not define: ${(registered - upstream).sorted()}",
        )
    }

    // ---- schema helpers -----------------------------------------------------------------------

    private class Shape(val properties: Set<String>, val required: Set<String>)

    private fun shapeOf(name: String): Shape =
        shapeOf(definitions[name] ?: fail("upstream schema has no definition named $name"))

    private fun shapeOf(definition: JsonObject): Shape {
        definition["properties"]?.let { properties ->
            return Shape(properties.jsonObject.keys, definition.requiredNames())
        }
        val variants = (definition["oneOf"] ?: definition["anyOf"])?.jsonArray
            ?: fail("definition is neither an object nor a union: ${definition.keys}")
        val shapes = variants.map { variant ->
            variant.jsonObject["\$ref"]?.jsonPrimitive?.content
                ?.let { reference -> shapeOf(reference.substringAfterLast('/')) }
                ?: shapeOf(variant.jsonObject)
        }
        return Shape(
            properties = shapes.flatMapTo(linkedSetOf()) { it.properties },
            // A field is only guaranteed when every variant requires it.
            required = shapes.map { it.required }.reduce { left, right -> left intersect right },
        )
    }

    private fun enumValuesOf(name: String): Set<String> {
        val definition = definitions[name] ?: fail("upstream schema has no definition named $name")
        definition["enum"]?.let { values ->
            return values.jsonArray.map { it.jsonPrimitive.content }.toSet()
        }
        val variants = (definition["oneOf"] ?: definition["anyOf"])?.jsonArray
            ?: fail("definition $name is neither an enum nor a union of enums")
        return variants.flatMapTo(linkedSetOf()) { variant ->
            variant.jsonObject["enum"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        }
    }

    private fun methodSet(schemaFile: String): Set<String> {
        val root = experimentalSchemas[schemaFile] ?: fail("experimental export has no $schemaFile")
        return root["oneOf"]?.jsonArray?.map { variant ->
            variant.jsonObject["properties"]?.jsonObject?.get("method")
                ?.jsonObject?.get("enum")?.jsonArray?.single()?.jsonPrimitive?.content
                ?: fail("$schemaFile has a variant without a method enum")
        }?.toSet() ?: fail("$schemaFile has no oneOf")
    }

    private fun JsonObject.requiredNames(): Set<String> =
        (this["required"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            .orEmpty()
            .toSet()

    private fun JsonObject.typeTag(): String? =
        this["properties"]?.jsonObject?.get("type")?.jsonObject?.get("enum")?.jsonArray?.single()?.jsonPrimitive?.content
}
