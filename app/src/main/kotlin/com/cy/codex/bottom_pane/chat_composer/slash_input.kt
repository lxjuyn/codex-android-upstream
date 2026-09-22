package com.cy.codex.bottom_pane.chat_composer

/**
 * What a composer line beginning with `/` is.
 *
 * Mirrors `codex-rs/tui/src/bottom_pane/chat_composer/slash_input.rs::validate_submission`: a
 * leading token is a command only when it is a single name. A path such as `/home/me/notes` has a
 * second slash and is submitted as ordinary text; a single name that matches nothing in the catalog
 * is reported to the user rather than sent to the model.
 */
sealed interface SlashInput {
    /** The name is in the catalog; [args] is everything after it, trimmed. */
    data class Command(val name: String, val args: String) : SlashInput

    /** A command-shaped token that names nothing in the catalog. */
    data class Unknown(val name: String) : SlashInput

    /** Not a command: plain text, or a path that merely starts with a slash. */
    data object NotCommand : SlashInput
}

/**
 * Classify [text] against [known], the catalog written without the leading slash.
 *
 * The name ends at the first whitespace character and an empty name is not a command, so a bare `/`
 * stays ordinary text.
 */
fun classifySlashInput(text: String, known: Set<String>): SlashInput {
    val line = text.trimStart()
    if (!line.startsWith("/")) return SlashInput.NotCommand
    val stripped = line.substring(1)
    val name = stripped.takeWhile { !it.isWhitespace() }
    if (name.isEmpty() || name.contains('/')) return SlashInput.NotCommand
    val args = stripped.substring(name.length).trim()
    return if (name in known) SlashInput.Command(name, args) else SlashInput.Unknown(name)
}
