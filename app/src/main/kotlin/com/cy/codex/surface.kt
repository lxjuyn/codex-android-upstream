package com.cy.codex

import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * What the shell is currently showing.
 *
 * Mirrors the overlay stack in `codex-rs/tui/src/app.rs` (`App::overlay`): the chat is always the
 * base surface and everything else is pushed on top of it, so dismissing a page always lands back
 * on the live transcript instead of rebuilding navigation state.
 *
 * These are `miuix-nav` route keys. The shell hands them to a `NavDisplay`, which owns the page
 * stack, the transition and the back gesture — the app only says *what* is on the stack.
 *
 * The set is wider than the TUI's overlay enum on purpose. The terminal reaches several of these
 * protocol families through slash commands that print into the transcript (`/mcp`, `/skills`,
 * `/plugins`, `/status`), and a phone has no transcript wide enough to hold a table of MCP servers
 * and no way to type at a row of one. Each family that answers with a list therefore gets a page.
 */
sealed interface Surface : NavKey {
    /** The transcript, with the sidebar, status card and composer floating over it. */
    data object Chat : Surface

    // ---- catalogs, mirroring the TUI's pickers ---------------------------------
    data object Settings : Surface
    data object Account : Surface
    data object McpServers : Surface
    data object Skills : Surface
    data object Plugins : Surface
    data object Apps : Surface
    data object Hooks : Surface
    data object WorkspacePicker : Surface

    /** Session picker, opened from the sidebar or `/resume`. */
    data object Sessions : Surface

    // ---- projects and execution environments -----------------------------------
    /** Saved projects, and the environments a thread can be run on. */
    data object Projects : Surface

    /**
     * One environment, named by id.
     *
     * A page rather than a section of [Projects] because `environment/info` and
     * `environment/status` are both addressed by id and neither can be asked for the whole set:
     * there is no call that lists environments, so a detail page is the only shape that fits.
     */
    data class EnvironmentDetail(val environmentId: String) : Surface

    // ---- remote control and user verification ----------------------------------
    /** Pairing, the paired-device list, and the switch that turns the relay on. */
    data object RemoteControl : Surface

    /** Local credential readiness and enrollment. */
    data object UserVerification : Surface

    // ---- plugin sharing ---------------------------------------------------------
    /** Plugins this account published, and the checkouts of other people's. */
    data object PluginShares : Surface

    // ---- filesystem, processes and review ---------------------------------------
    /**
     * The filesystem the session can reach, browsed one directory at a time.
     *
     * `fs/…` is a capability surface, not a file manager: every call is scoped by the server's own
     * sandbox, so this page shows what that sandbox allows rather than the device's storage.
     */
    data class FileBrowser(val path: String, val picking: Boolean = false) : Surface

    /** `command/exec`: one command, streamed. */
    data object ExecCommand : Surface

    /** `thread/backgroundTerminals/list`: the long-lived terminals a session left running. */
    data object BackgroundTerminals : Surface

    /** `/review` — pick what to review, then start it. */
    data object Review : Surface

    /** `/worktree` — git worktrees of the open thread's repository. */
    data object Worktrees : Surface

    /**
     * `/diff` — the working tree, tracked and untracked.
     *
     * A page rather than a transcript cell: the turn diff on the status card only carries what a
     * turn changed, and untracked files are not part of it at all.
     */
    data object Diff : Surface

    // ---- sessions ---------------------------------------------------------------
    /** The realtime voice session for the open thread. */
    data object Realtime : Surface

    /** Ctrl+T: the read-only transcript overlay, mirroring the TUI's transcript pager. */
    data object ThreadHistory : Surface

    // ---- diagnostics and maintenance --------------------------------------------
    /** `server/diagnostics`, plus the feedback upload that goes with a bug report. */
    data object Diagnostics : Surface

    /** `/status`: the open thread's own session state, not the server process. */
    data object SessionStatus : Surface

    /** `bottom_pane/memories_settings_view.rs`: what the memory store holds and how to clear it. */
    data object Memories : Surface

    /** `external_agent_config_migration/`: import another agent's configuration. */
    data object ExternalAgentImport : Surface

    /** `onboarding/bedrock.rs`: sign in through Bedrock instead of ChatGPT. */
    data object Bedrock : Surface

    /** Windows sandbox readiness and setup. */
    data object WindowsSandbox : Surface

    // ---- MCP --------------------------------------------------------------------
    /** One MCP server's resources and tools, called by hand. */
    data class McpToolbox(val server: String) : Surface

    // ---- agents -----------------------------------------------------------------
    /** `/agents` and `/subagents`: the roster of agents spawned from the open thread. */
    data object Agents : Surface

    /**
     * One subagent of the open thread.
     *
     * A subagent is not a thread of its own: the item stream only ever mentions one through a
     * `CollabAgentToolCallItem` receiver id or a `SubAgentActivityItem`, so the page is addressed
     * by that id and its body is folded out of the parent transcript.
     */
    data class SubAgent(val threadId: String) : Surface

    /**
     * The same agent's conversation, read as a page.
     *
     * A separate route from [SubAgent] because they are separate questions — "what is this agent"
     * and "what did this agent do" — and because entering the conversation must not disturb the
     * session it was spawned from.
     */
    data class SubAgentThread(val threadId: String) : Surface
}
