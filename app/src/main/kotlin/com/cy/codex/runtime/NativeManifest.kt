package com.cy.codex.runtime

import java.nio.file.Paths

data class NativeManifestEntry(val kind: String, val path: String, val target: String)

/** Validate the packaged manifest before using its paths to write into the private runtime. */
fun parseNativeManifest(text: String): List<NativeManifestEntry> {
    val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
    require(lines.firstOrNull() == "abi|arm64-v8a") { "Unsupported toolchain ABI." }
    val seen = mutableSetOf<String>()
    return lines.drop(1).map { line ->
        val columns = line.split('|')
        require(columns.size == 3) { "Malformed native manifest entry." }
        val (kind, path, target) = columns
        require(kind in setOf("file", "link", "data")) { "Unknown native entry: $kind" }
        require(path.isNotBlank() && !path.startsWith('/') &&
            path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) {
            "Unsafe native path: $path"
        }
        require(seen.add(path)) { "Duplicate native path: $path" }
        when (kind) {
            "file" -> require(target.matches(Regex("lib[A-Za-z0-9._-]+\\.so"))) {
                "Invalid native library: $target"
            }
            "link" -> {
                require(target.isNotBlank() && !target.startsWith('/')) { "Invalid link target." }
                val resolved = (Paths.get(path).parent ?: Paths.get("")).resolve(target).normalize()
                require(!resolved.startsWith("..")) { "Link escapes the runtime: $path" }
            }
            "data" -> require(target.isEmpty()) { "Unexpected data target." }
        }
        NativeManifestEntry(kind, path, target)
    }
}
