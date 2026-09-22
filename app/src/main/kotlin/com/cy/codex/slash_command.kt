package com.cy.codex

import androidx.annotation.StringRes

/**
 * One slash command's metadata.
 *
 * Mirrors the parts of `codex-rs/tui/src/slash_command.rs` this client can honor: the name, the
 * description shown in the popup, whether an argument is spliced into the draft instead of being
 * dispatched, whether the command may run while a turn is in flight
 * (`SlashCommand::available_during_task`), and the aliases the terminal accepts for it. Feature
 * gating stays at the call site because it needs catalog data — `/plan` needs the mode list to have
 * answered — while this table is static.
 */
internal data class SlashCommandSpec(
    val name: String,
    @StringRes val descriptionRes: Int,
    val takesArgument: Boolean = false,
    val availableDuringTask: Boolean = true,
    val aliases: List<String> = emptyList(),
)

/**
 * The command catalog, in popup order.
 *
 * This is the single source of truth for [CodexApp.ComposerCommands] and for the popup: a command
 * that is missing here is neither recognized on submission nor suggested. Aliases are recognized
 * and dispatched but never listed, the way `command_popup.rs` hides `quit`, `btw`, `clean` and
 * `cwd` until they are typed.
 */
internal object SlashCommands {
    val All: List<SlashCommandSpec> = listOf(
        SlashCommandSpec("new", R.string.runtime_new_thread, availableDuringTask = false),
        SlashCommandSpec("clear", R.string.slash_desc_clear, availableDuringTask = false),
        SlashCommandSpec("resume", R.string.sidebar_library_all_sessions),
        SlashCommandSpec("fork", R.string.session_list_fork, availableDuringTask = false),
        SlashCommandSpec("rename", R.string.session_list_rename, takesArgument = true),
        SlashCommandSpec("archive", R.string.session_list_archive, availableDuringTask = false),
        SlashCommandSpec("compact", R.string.slash_desc_compact, availableDuringTask = false),
        SlashCommandSpec("recap", R.string.slash_desc_recap, availableDuringTask = false),
        SlashCommandSpec("revert", R.string.slash_desc_revert, takesArgument = true),
        SlashCommandSpec("stop", R.string.slash_desc_stop, aliases = listOf("clean")),
        SlashCommandSpec("model", R.string.settings_tab_model),
        SlashCommandSpec("approvals", R.string.settings_tab_approval),
        SlashCommandSpec("permissions", R.string.settings_group_permissions),
        SlashCommandSpec("plan", R.string.slash_desc_plan, availableDuringTask = false),
        SlashCommandSpec("goal", R.string.goal_sheet_title, takesArgument = true),
        SlashCommandSpec("side", R.string.side_conversation_title, takesArgument = true, aliases = listOf("btw")),
        SlashCommandSpec("review", R.string.sidebar_library_review, availableDuringTask = false),
        SlashCommandSpec("worktree", R.string.worktrees_title, availableDuringTask = false),
        SlashCommandSpec("diff", R.string.git_diff_screen_description),
        SlashCommandSpec("status", R.string.slash_desc_status),
        SlashCommandSpec("copy", R.string.slash_desc_copy),
        SlashCommandSpec("export", R.string.slash_desc_export, takesArgument = true, availableDuringTask = false),
        SlashCommandSpec("usage", R.string.account_screen_title),
        SlashCommandSpec("settings", R.string.settings_screen_title),
        SlashCommandSpec("theme", R.string.settings_group_appearance, availableDuringTask = false),
        SlashCommandSpec("shell", R.string.exec_command_title, takesArgument = true),
        SlashCommandSpec("cd", R.string.workspace_picker_title, takesArgument = true,
            availableDuringTask = false, aliases = listOf("cwd", "pwd")),
        SlashCommandSpec("mcp", R.string.sidebar_library_mcp_servers),
        SlashCommandSpec("skills", R.string.sidebar_library_skills),
        SlashCommandSpec("plugins", R.string.sidebar_library_plugins),
        SlashCommandSpec("apps", R.string.sidebar_library_apps),
        SlashCommandSpec("hooks", R.string.hooks_screen_title),
        SlashCommandSpec("memories", R.string.memories_screen_title),
        SlashCommandSpec("agents", R.string.agents_overview_title),
        SlashCommandSpec("subagents", R.string.agent_picker_title),
        SlashCommandSpec("import", R.string.slash_desc_import, availableDuringTask = false),
        SlashCommandSpec("feedback", R.string.diagnostics_feedback_section),
        SlashCommandSpec("voice", R.string.realtime_page_title),
        SlashCommandSpec("logout", R.string.account_screen_sign_out, availableDuringTask = false),
        SlashCommandSpec("init", R.string.slash_desc_init, availableDuringTask = false),
    )

    /** Names and aliases: everything a submission may resolve to a command. */
    val Known: Set<String> = All.flatMap { it.aliases + it.name }.toSet()

    /** Resolve a typed name or alias to its command, preferring an exact canonical name. */
    fun find(name: String): SlashCommandSpec? =
        All.firstOrNull { it.name == name } ?: All.firstOrNull { name in it.aliases }

    /**
     * The popup rows for [query] (which includes the leading `/`).
     *
     * Upstream `command_popup.rs` puts exact matches before prefix matches and preserves the
     * catalog's own order inside each group; a bare `/` matches everything in declared order. An
     * alias matches only exactly, so typing `/cle` does not offer `clean` but `/clean` does.
     */
    fun filter(query: String): List<SlashCommandSpec> {
        val needle = query.removePrefix("/")
        if (needle.isEmpty()) return All
        val exact = mutableListOf<SlashCommandSpec>()
        val prefix = mutableListOf<SlashCommandSpec>()
        for (spec in All) {
            when {
                spec.name.equals(needle, ignoreCase = true) -> exact += spec
                spec.aliases.any { it.equals(needle, ignoreCase = true) } -> exact += spec
                spec.name.startsWith(needle, ignoreCase = true) -> prefix += spec
            }
        }
        return exact + prefix
    }
}
