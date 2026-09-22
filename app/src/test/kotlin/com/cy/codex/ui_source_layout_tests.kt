package com.cy.codex

import java.io.File
import org.junit.Test
import kotlin.test.assertTrue

/** Keeps Compose sources aligned with the module and file layout of `codex-rs/tui/src`. */
class UiSourceLayoutTest {

    @Test
    fun `test files use snake case names`() {
        val offenders = sourceFiles(File("src/test/kotlin"))
            .filterNot { it.name.matches(Regex("[a-z0-9_]+_tests\\.kt")) }

        assertPaths("test files must be <subject>_tests.kt", offenders)
    }

    @Test
    fun `ui source files use snake case names`() {
        val root = File("src/main/kotlin/com/cy/codex")
        val uiDirectories = setOf(
            "app",
            "bottom_pane",
            "chatwidget",
            "external_agent_config_migration",
            "history_cell",
            "keymap",
            "markdown_render",
            "onboarding",
            "render",
            "status",
            "theme",
        )
        val offenders = sourceFiles(root)
            .filter { it.parentFile?.name in uiDirectories }
            .filterNot { it.name.matches(Regex("[a-z0-9_]+\\.kt")) }

        assertPaths("UI source files must use lower snake_case", offenders)
    }

    @Test
    fun `packages match source directories`() {
        val roots = listOf(File("src/main/kotlin"), File("src/test/kotlin"))
        val offenders = roots.flatMap { sourceFiles(it) }.filter { file ->
            val relative = file.invariantSeparatorsPath.substringAfter("kotlin/")
            val expected = relative.substringBeforeLast('/').replace('/', '.')
            val actual = file.readLines().firstOrNull { it.startsWith("package ") }
                ?.removePrefix("package ")
                .orEmpty()
            actual != expected
        }

        assertPaths("package declarations must match their directories", offenders)
    }

    @Test
    fun `ported ui files keep their tui path and stem`() {
        val kotlinRoot = File("src/main/kotlin/com/cy/codex")
        val tuiRoot = File("../codex/codex-rs/tui/src")
        val tuiStems = sourceFiles(tuiRoot)
            .map { it.invariantSeparatorsPath.removePrefix(tuiRoot.invariantSeparatorsPath + "/").removeSuffix(".rs") }
            .toSet()
        val offenders = sourceFiles(kotlinRoot).filter { file ->
            val stem = file.invariantSeparatorsPath
                .removePrefix(kotlinRoot.invariantSeparatorsPath + "/")
                .removeSuffix(".kt")
            val sharesStem = tuiStems.any { it.substringAfterLast('/') == stem.substringAfterLast('/') }
            sharesStem && stem !in tuiStems
        }

        assertPaths("files shared with codex-tui must keep its relative path and stem", offenders)
    }

    private fun sourceFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun assertPaths(message: String, files: List<File>) {
        assertTrue(files.isEmpty(), buildString {
            appendLine(message + ":")
            files.forEach { appendLine("  $it") }
        })
    }
}
