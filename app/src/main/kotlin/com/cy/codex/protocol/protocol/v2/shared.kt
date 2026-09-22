package com.cy.codex.protocol.protocol.v2

/**
 * Value types shared by many v2 requests and notifications.
 *
 * Mirrors `schema/typescript/v2/` for the subset the Android client renders. Field names follow the
 * camelCase wire spellings so a generated decoder can drop in later without touching call sites.
 */

enum class CommandExecutionStatus(val wire: String) {
    InProgress("inProgress"),
    Completed("completed"),
    Failed("failed"),
    Declined("declined"),
    ;

    companion object {
        fun fromWire(value: String): CommandExecutionStatus =
            entries.firstOrNull { it.wire == value } ?: Completed
    }
}

enum class CommandExecutionSource(val wire: String) {
    Agent("agent"),
    UserShell("userShell"),
    UnifiedExecStartup("unifiedExecStartup"),
    UnifiedExecInteraction("unifiedExecInteraction"),
}

/** Best-effort parse of a shell command, used to label a tool call without running it. */
sealed interface CommandAction {
    val command: String

    data class Read(override val command: String, val name: String, val path: String) : CommandAction
    data class ListFiles(override val command: String, val path: String?) : CommandAction
    data class Search(override val command: String, val query: String?, val path: String?) : CommandAction
    data class Unknown(override val command: String) : CommandAction
}

enum class PatchApplyStatus(val wire: String) {
    InProgress("inProgress"),
    Completed("completed"),
    Failed("failed"),
    Declined("declined"),
    ;

    companion object {
        fun fromWire(value: String): PatchApplyStatus =
            entries.firstOrNull { it.wire == value } ?: Completed
    }
}

enum class PatchChangeKind(val wire: String) {
    Add("add"),
    Delete("delete"),
    Update("update"),
    ;

    companion object {
        fun fromWire(value: String): PatchChangeKind =
            entries.firstOrNull { it.wire == value } ?: Update
    }
}

/** One file inside a `ThreadItem.FileChange`, with the unified diff the server already produced. */
data class FileUpdateChange(
    val path: String,
    val kind: PatchChangeKind,
    val diff: String,
)

enum class McpToolCallStatus(val wire: String) {
    InProgress("inProgress"),
    Completed("completed"),
    Failed("failed"),
}

enum class DynamicToolCallStatus(val wire: String) {
    InProgress("inProgress"),
    Completed("completed"),
    Failed("failed"),
}

enum class CollabAgentTool(val wire: String) {
    SpawnAgent("spawnAgent"),
    SendInput("sendInput"),
    ResumeAgent("resumeAgent"),
    Wait("wait"),
    CloseAgent("closeAgent"),
    SendMessage("sendMessage"),
    FollowupTask("followupTask"),
    InterruptAgent("interruptAgent"),
    ListAgents("listAgents"),
}

enum class CollabAgentToolCallStatus(val wire: String) {
    InProgress("inProgress"),
    Completed("completed"),
    Failed("failed"),
    Interrupted("interrupted"),
}

/** Last known status of one agent addressed by a collab tool call. */
data class CollabAgentState(
    val status: AgentRunStatus,
    val message: String? = null,
)

enum class AgentRunStatus(val wire: String) {
    PendingInit("pendingInit"),
    Running("running"),
    Interrupted("interrupted"),
    Completed("completed"),
    Errored("errored"),
    Shutdown("shutdown"),
    NotFound("notFound"),
    ;

    companion object {
        fun fromWire(value: String): AgentRunStatus =
            entries.firstOrNull { it.wire == value } ?: Running
    }
}

enum class SubAgentActivityKind(val wire: String) {
    Started("started"),
    Interacted("interacted"),
    Interrupted("interrupted"),
    Completed("completed"),
}

enum class MessagePhase(val wire: String) {
    Commentary("commentary"),
    FinalAnswer("finalAnswer"),
}

enum class ReasoningEffort(val wire: String) {
    None("none"),
    Minimal("minimal"),
    Low("low"),
    Medium("medium"),
    High("high"),
    XHigh("xhigh"),
    Max("max"),
    Ultra("ultra"),
    ;

    companion object {
        fun fromWire(value: String): ReasoningEffort =
            entries.firstOrNull { it.wire == value } ?: Medium
    }
}

/** A byte range inside a `UserInput.Text` buffer; indexes are UTF-8 bytes, not chars. */
data class ByteRange(val start: Int, val end: Int)

/**
 * A UI-defined span within a text input, such as an `[Image #N]` placeholder.
 *
 * `placeholder` is nullable on the wire even though the client always sends one: the server
 * tolerates elements that only mark a range.
 */
data class TextElement(val byteRange: ByteRange, val placeholder: String? = null)

/** Input the user contributed to a turn. */
sealed interface UserInput {
    data class Text(
        val text: String,
        val textElements: List<TextElement> = emptyList(),
    ) : UserInput

    data class Image(val url: String, val detail: String? = null) : UserInput

    data class LocalImage(val path: String, val detail: String? = null) : UserInput

    data class Audio(val url: String) : UserInput

    data class LocalAudio(val path: String) : UserInput

    data class Skill(val name: String, val path: String) : UserInput

    data class Mention(val name: String, val path: String) : UserInput
}

/** One `request_user_input` question and its options. */
data class ToolRequestUserInputQuestion(
    val id: String,
    val header: String,
    val question: String,
    val isOther: Boolean = false,
    val isSecret: Boolean = false,
    val options: List<ToolRequestUserInputOption>? = null,
)

data class ToolRequestUserInputOption(val label: String, val description: String)

/** A question the agent asked inside an agent message (`AsyncUserInputQuestion`). */
data class AsyncUserInputQuestion(val title: String, val options: List<String>? = null)
