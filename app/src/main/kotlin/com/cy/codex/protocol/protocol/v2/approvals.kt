package com.cy.codex.protocol.protocol.v2

/**
 * Parameters of the four approval families the client has to answer, plus their response bodies.
 *
 * Mirrors `schema/typescript/v2/{CommandExecutionRequestApprovalParams, FileChangeRequestApprovalParams,
 * PermissionsRequestApprovalParams, ToolRequestUserInputParams, McpServerElicitationRequestParams}.ts`.
 * The deprecated v1 `execCommandApproval` / `applyPatchApproval` pair is registered in
 * [ServerRequestMethod] but not answered: this client speaks the v2 methods only.
 */

data class CommandExecutionApprovalParams(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val startedAtMs: Long = 0L,
    val approvalId: String? = null,
    val environmentId: String? = null,
    val reason: String? = null,
    val command: String? = null,
    val cwd: String? = null,
    val commandActions: List<CommandAction> = emptyList(),
    /** Rules that accepting with the execpolicy amendment would install, as whole rules. */
    val proposedExecpolicyAmendment: List<String>? = null,
    /** Host rules `ApplyNetworkPolicyAmendment` can enact; an empty list means none proposed. */
    val proposedNetworkPolicyAmendments: List<NetworkPolicyAmendment> = emptyList(),
    /**
     * Which decisions the server will accept. Empty means the server did not say, and the UI falls
     * back to accept / decline.
     */
    val availableDecisions: List<CommandExecutionApprovalDecision> = emptyList(),
)

/**
 * Parameters of `item/fileChange/requestApproval`.
 *
 * The request names the item under review but does not repeat its diff — the wire has no `changes`
 * field (`codex-rs/app-server-protocol/src/protocol/v2/item.rs`, `FileChangeRequestApprovalParams`).
 * The patch is recovered from the `FileChangeItem` the same ids point at; see `ChatWidget`.
 */
data class FileChangeApprovalParams(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val startedAtMs: Long = 0L,
    val reason: String? = null,
    val grantRoot: String? = null,
)

data class PermissionsApprovalParams(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val environmentId: String? = null,
    val startedAtMs: Long = 0L,
    val cwd: String = "",
    val reason: String? = null,
    val permissions: RequestPermissionProfile = RequestPermissionProfile(),
)

/** Snapshot of what the agent is asking for; rendered as a checklist on the approval card. */
data class RequestPermissionProfile(
    val network: Boolean = false,
    val fileSystemRead: List<String> = emptyList(),
    val fileSystemWrite: List<String> = emptyList(),
    val shell: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !network && fileSystemRead.isEmpty() && fileSystemWrite.isEmpty() && !shell
}

data class ToolRequestUserInputParams(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val questions: List<ToolRequestUserInputQuestion>,
    val isBlocking: Boolean = true,
)

/** Flattened JSON Schema handed to the elicitation form renderer. */
data class McpElicitationSchema(
    val title: String = "",
    val fields: List<McpElicitationField> = emptyList(),
)

/** `item/tool/call`: the server asks the client to run a dynamically declared tool. */
data class DynamicToolCallParams(
    val threadId: String,
    val turnId: String,
    val callId: String,
    val namespace: String? = null,
    val tool: String,
    val arguments: String = "{}",
)

data class DynamicToolCallResponse(
    val success: Boolean,
    val contentItems: List<String> = emptyList(),
)
