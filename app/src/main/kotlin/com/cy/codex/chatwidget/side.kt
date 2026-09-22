package com.cy.codex.chatwidget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.cy.codex.R
import com.cy.codex.SlashCommands
import com.cy.codex.ThreadListState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Th1
import top.yukonga.miuix.kmp.icon.extended.Timer

/**
 * Sidebar model.
 *
 * Mirrors the TUI's side panel (`codex-rs/tui/src/chatwidget/side.rs` and `app/side.rs`): the
 * drawer is made of an action list, a project/working-directory grouping and the sessions inside
 * each group. The TUI builds it from `thread/list`; so does this, through
 * [ThreadListState.grouped]. Groups are derived from each thread's working directory.
 */
data class SidebarEntry(
    val id: String,
    val title: String,
    val icon: ImageVector,
)

data class SidebarSession(
    val id: String,
    val title: String,
    val date: String,
    val archived: Boolean = false,
    val running: Boolean = false,
)

data class SidebarProject(
    val id: String,
    val name: String,
    val path: String,
    val sessions: List<SidebarSession>,
)

object SidebarModel {

    /**
     * Drawer actions, in the order the TUI lists them.
     *
     * One entry only: the drawer is for *getting somewhere in this session* — a new workspace, or a
     * thread under a project. Everything else it used to hold (MCP, Skill, 插件, Hook, App, 账户,
     * 已归档, 设置) is configuration, and configuration now lives on the settings page, where the
     * full labels fit instead of being squeezed into a 252dp drawer.
     */
    @Composable
    @ReadOnlyComposable
    fun actions(): List<SidebarEntry> = listOf(
        SidebarEntry("new", stringResource(R.string.runtime_new_thread), MiuixIcons.Messages),
        SidebarEntry("workspace", stringResource(R.string.sidebar_add_workspace), MiuixIcons.AddFolder),
    )

    /**
     * The pages the drawer used to list, now rows on the settings page.
     *
     * Ids are the same ones [com.cy.codex.chatwidget.SidebarPanel]'s action handler switches
     * on, so a row opens exactly what the drawer entry opened.
     */
    @Composable
    @ReadOnlyComposable
    fun libraryEntries(): List<SidebarEntry> = listOf(
        SidebarEntry("sessions", stringResource(R.string.sidebar_library_all_sessions), MiuixIcons.Messages),
        SidebarEntry("mcp", stringResource(R.string.sidebar_library_mcp_servers), MiuixIcons.Link),
        SidebarEntry("skills", stringResource(R.string.sidebar_library_skills), MiuixIcons.Tasks),
        SidebarEntry("plugins", stringResource(R.string.sidebar_library_plugins), MiuixIcons.Store),
        SidebarEntry("apps", stringResource(R.string.sidebar_library_apps), MiuixIcons.Community),
        SidebarEntry("account", stringResource(R.string.sidebar_library_account), MiuixIcons.Info),
        SidebarEntry("archived", stringResource(R.string.sidebar_library_archived), MiuixIcons.Blocklist),
        SidebarEntry("projects", stringResource(R.string.sidebar_library_projects), MiuixIcons.Folder),
        SidebarEntry("files", stringResource(R.string.sidebar_library_files), MiuixIcons.File),
        SidebarEntry("exec", stringResource(R.string.sidebar_library_exec), MiuixIcons.Th1),
        SidebarEntry("review", stringResource(R.string.sidebar_library_review), MiuixIcons.Search),
        SidebarEntry("goal", stringResource(R.string.goal_sheet_title), MiuixIcons.Tasks),
        SidebarEntry("memories", stringResource(R.string.sidebar_library_memories), MiuixIcons.Notes),
    )

    /** Turn the thread list into the sidebar's project groups. */
    @Composable
    @ReadOnlyComposable
    fun projects(threads: ThreadListState, includeArchived: Boolean): List<SidebarProject> =
        threads.grouped().mapNotNull { group ->
            val sessions = group.threads
                .filter { includeArchived || it.id !in threads.archivedIds }
                .map { thread ->
                    SidebarSession(
                        id = thread.id,
                        title = thread.name ?: thread.preview.ifBlank { thread.id.takeLast(6) },
                        date = relativeTime(thread.updatedAt),
                        archived = thread.id in threads.archivedIds,
                        running = thread.status is com.cy.codex.protocol.protocol.v2.ThreadStatus.Active,
                    )
                }
            if (sessions.isEmpty()) {
                null
            } else {
                SidebarProject(group.id, group.name, group.path.orEmpty(), sessions)
            }
        }

    /** Coarse relative time; the TUI prints the same three buckets in its session picker. */
    @Composable
    @ReadOnlyComposable
    fun relativeTime(epochMillis: Long): String {
        val now = System.currentTimeMillis()
        val delta = (now - epochMillis).coerceAtLeast(0)
        val minutes = delta / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 2 -> stringResource(R.string.sidebar_time_now)
            minutes < 60 -> stringResource(R.string.sidebar_time_minutes, minutes)
            hours < 24 -> stringResource(R.string.sidebar_time_hours, hours)
            days < 7 -> stringResource(R.string.sidebar_time_days, days)
            else -> {
                val weeks = days / 7
                stringResource(R.string.sidebar_time_weeks, weeks)
            }
        }
    }

    /**
     * Slash-command suggestions for the composer.
     *
     * Built from [SlashCommands.All], the same table submission recognizes, so the popup cannot
     * drift from the dispatch. [planAvailable] mirrors the feature gate upstream applies to
     * `SlashCommand::Plan`: the row is hidden until `collaborationMode/list` has answered with a
     * plan preset.
     */
    @Composable
    fun slashSuggestions(query: String = "/", planAvailable: Boolean = true): List<SlashCommand> =
        SlashCommands.filter(query)
            .filter { spec -> planAvailable || spec.name != "plan" }
            .map { spec ->
                SlashCommand(
                    command = "/" + spec.name,
                    description = stringResource(spec.descriptionRes),
                    takesArgument = spec.takesArgument,
                )
            }
}

data class SlashCommand(val command: String, val description: String, val takesArgument: Boolean = false)
