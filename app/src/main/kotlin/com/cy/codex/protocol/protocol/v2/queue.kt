package com.cy.codex.protocol.protocol.v2

/**
 * `thread/queue/…` — messages submitted while a turn is already running.
 *
 * Mirrors `schema/typescript/v2/ThreadQueue*.ts`.
 *
 * The queue lives on the server, not in the client. `thread/queue/changed` carries only a thread id
 * — it is a *poke*, not a payload — so the client answers it by calling `thread/queue/list` and
 * replacing its copy. Modelling the notification as "here are the queued messages" is a trap: the
 * server never sends them, and a client that believes it does shows a stale queue forever.
 */

/** One queued user submission. */
data class QueuedSubmission(
    val id: String,
    /** Echo of the id the client sent, so an optimistic row can be reconciled. */
    val clientUserMessageId: String = "",
    val input: List<UserInput> = emptyList(),
) {
    /** Preview text for the queue row. */
    val preview: String
        get() = input.filterIsInstance<UserInput.Text>().joinToString("\n") { it.text }.trim()
}

/** `thread/queue/add` — enqueue one message behind the running turn. */
data class ThreadQueueAddParams(
    val threadId: String,
    val input: List<UserInput>,
    val clientUserMessageId: String,
)

data class ThreadQueueAddResponse(val queuedSubmission: QueuedSubmission? = null)

/** `thread/queue/list` — paged, so a long queue does not arrive in one frame. */
data class ThreadQueueListParams(
    val threadId: String,
    val cursor: String? = null,
    val limit: Int? = null,
)

data class ThreadQueueListResponse(
    val data: List<QueuedSubmission> = emptyList(),
    val nextCursor: String? = null,
)

/** `thread/queue/update` — rewrite the input of a queued entry before it starts. */
data class ThreadQueueUpdateParams(
    val threadId: String,
    val queuedSubmissionId: String,
    val input: List<UserInput>,
)

/** `thread/queue/delete` — drop a queued entry. */
data class ThreadQueueDeleteParams(
    val threadId: String,
    val queuedSubmissionId: String,
)

/** `thread/queue/reorder` — replace the whole order in one call. */
data class ThreadQueueReorderParams(
    val threadId: String,
    val queuedSubmissionIds: List<String>,
)

/** `thread/queue/start` — run a queued entry now; `null` means "the head". */
data class ThreadQueueStartParams(
    val threadId: String,
    val queuedSubmissionId: String? = null,
)
