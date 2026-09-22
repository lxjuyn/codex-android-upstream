package com.cy.codex.history_cell

import com.cy.codex.protocol.protocol.v2.AsyncUserInputQuestion

/**
 * Framing and bounds for the questions an agent message can carry inline.
 *
 * Mirrors `codex-rs/context-fragments/src/answered_question.rs` and the normalization in
 * `codex-rs/tui/src/bottom_pane/async_questions/state.rs::append`. The answer itself is an
 * ordinary user message — the server never receives a structured answer — so the quoted question
 * above it is what tells the model which question a bare "yes" belonged to. That framing is
 * client-authored, which is why it lives here and not in the protocol.
 */
internal object AsyncQuestions {

    /** UTF-8 byte bound on the quoted question (`AnsweredQuestion::new`). */
    const val QuestionByteLimit = 512

    /** Model-authored options beyond these bounds are dropped (`state.rs::append`). */
    const val MaxOptions = 32
    const val OptionByteLimit = 512

    /**
     * `> {question}\n\n{answer}` with the question flattened to one bounded line.
     *
     * The answer is trimmed the way `go_next_or_submit` trims it; callers must not submit a blank
     * one, because the whole point of the framing is that something followed it.
     */
    fun answeredText(question: String, answer: String): String =
        "> ${flattened(question)}\n\n${answer.trim()}"

    /**
     * Bound each question's options before they are shown.
     *
     * The take-then-filter order matches the TUI: the first 32 authored options are considered, and
     * an over-long label inside that window is dropped rather than truncated. An empty result is a
     * free-text question, exactly as an omitted `options` field is.
     */
    fun normalize(questions: List<AsyncUserInputQuestion>): List<AsyncUserInputQuestion> =
        questions.map { question ->
            question.copy(
                options = question.options
                    ?.asSequence()
                    ?.take(MaxOptions)
                    ?.filter { it.toByteArray(Charsets.UTF_8).size <= OptionByteLimit }
                    ?.toList(),
            )
        }

    private fun flattened(question: String): String =
        question.takeUtf8Bytes(QuestionByteLimit).replace('\n', ' ').replace('\r', ' ')

    /** [limit] UTF-8 bytes from the front, stopping on a character boundary. */
    private fun String.takeUtf8Bytes(limit: Int): String {
        var bytes = 0
        var end = 0
        while (end < length) {
            val codePoint = codePointAt(end)
            val width = when {
                codePoint < 0x80 -> 1
                codePoint < 0x800 -> 2
                codePoint < 0x10000 -> 3
                else -> 4
            }
            if (bytes + width > limit) break
            bytes += width
            end += Character.charCount(codePoint)
        }
        return substring(0, end)
    }
}
