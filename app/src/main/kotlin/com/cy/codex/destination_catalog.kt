package com.cy.codex

/**
 * Canonical ownership for every page/action id the shell exposes.
 *
 * The TUI put navigation, session tools and configuration in one overlay list. Compose has room
 * for better placement, so the sets below make that placement explicit and testable: a destination
 * has exactly one owner, and adding it somewhere without removing it elsewhere fails a JVM test.
 */
object DestinationCatalog {
    object Id {
        const val Settings = "settings"
        const val New = "new"
        const val Workspace = "workspace"
        const val Sessions = "sessions"
        const val Archived = "archived"
        const val Projects = "projects"
        const val History = "history"
        const val Files = "files"
        const val Exec = "exec"
        const val Terminals = "terminals"
        const val Review = "review"
        const val Worktree = "worktree"
        const val Diff = "diff"
        const val Goal = "goal"
        const val Realtime = "realtime"
        const val Mcp = "mcp"
        const val Skills = "skills"
        const val Plugins = "plugins"
        const val Apps = "apps"
        const val Hooks = "hooks"
        const val PluginShares = "plugin_shares"
        const val Account = "account"
        const val Memories = "memories"
        const val Migration = "migration"
        const val RemoteControl = "remote_control"
        const val Verification = "verification"
        const val Bedrock = "bedrock"
        const val Sandbox = "sandbox"
        const val Diagnostics = "diagnostics"
        const val Status = "status"
    }

    val Chrome = setOf(Id.Settings)
    val Navigation = setOf(Id.New, Id.Workspace, Id.Sessions, Id.Archived, Id.Projects)
    val SessionTools = setOf(
        Id.History,
        Id.Files,
        Id.Exec,
        Id.Terminals,
        Id.Review,
        Id.Worktree,
        Id.Diff,
        Id.Goal,
        Id.Realtime,
    )
    val SettingsExtensions = setOf(
        Id.Mcp,
        Id.Skills,
        Id.Plugins,
        Id.Apps,
        Id.Hooks,
        Id.PluginShares,
    )
    val SettingsData = setOf(
        Id.Account,
        Id.Memories,
        Id.Migration,
        Id.RemoteControl,
        Id.Verification,
        Id.Bedrock,
        Id.Sandbox,
        Id.Diagnostics,
    )
    val SettingsSessionLinks = setOf(Id.Status)

    val Owned =
        buildSet {
            addAll(Chrome)
            addAll(Navigation)
            addAll(SessionTools)
            addAll(SettingsExtensions)
            addAll(SettingsData)
            addAll(SettingsSessionLinks)
        }

    /** Every id has one owner; the sets are deliberately flat so a duplicate cannot hide in a map. */
    fun duplicateIds(): Set<String> {
        val groups = listOf(Chrome, Navigation, SessionTools, SettingsExtensions, SettingsData, SettingsSessionLinks)
        return groups.flatten().groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    }
}
