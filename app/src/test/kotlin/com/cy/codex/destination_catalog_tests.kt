package com.cy.codex

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Locks the information architecture so a route cannot return to two competing menus. */
class DestinationCatalogTest {
    @Test
    fun `every destination has exactly one owner`() {
        assertEquals(emptySet(), DestinationCatalog.duplicateIds())
    }

    @Test
    fun `legacy sidebar and settings destinations are all rehomed`() {
        val expected = setOf(
            "settings", "new", "workspace", "sessions", "archived", "projects",
            "history", "files", "exec", "terminals", "review", "worktree", "diff", "goal", "realtime",
            "mcp", "skills", "plugins", "apps", "hooks", "plugin_shares",
            "account", "memories", "migration", "remote_control", "verification", "bedrock", "sandbox",
            "diagnostics", "status",
        )
        assertEquals(expected, DestinationCatalog.Owned)
    }

    @Test
    fun `session tools and settings pages stay separate`() {
        assertTrue(DestinationCatalog.SessionTools.intersect(DestinationCatalog.SettingsExtensions).isEmpty())
        assertTrue(DestinationCatalog.SessionTools.intersect(DestinationCatalog.SettingsData).isEmpty())
    }
}
