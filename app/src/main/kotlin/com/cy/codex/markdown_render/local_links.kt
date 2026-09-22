package com.cy.codex.markdown_render

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import com.cy.codex.R
import java.io.File

/**
 * What a markdown link points at, after the transcript has decided it is not prose.
 *
 * Mirrors `codex-rs/tui/src/markdown_render/local_links.rs`: a destination is either a web URL or a
 * local file path with an optional `:line:col` / `#L12C3` location, and a local path is displayed
 * relative to the session's working directory.
 */
sealed interface LinkTarget {
    /** Anything with a scheme the system can open. */
    data class Web(val url: String) : LinkTarget

    /**
     * A path inside the session's workspace.
     *
     * [path] is the destination as written (normalized to forward slashes) and [location] the
     * `:12:4` / `#L12C4` suffix when the link carried one.
     */
    data class Local(val path: String, val location: String?, val display: String) : LinkTarget
}

/** Split a markdown destination into a [LinkTarget]; [cwd] only affects the display path. */
fun parseLinkTarget(destination: String, cwd: String? = null): LinkTarget {
    val dest = destination.trim()
    if (!isLocalPathLike(dest)) return LinkTarget.Web(dest)
    val (path, location) = splitLocation(dest)
    return LinkTarget.Local(path, location, displayLocalPath(path, cwd) + location.orEmpty())
}

/** True for the shapes `local_links.rs` treats as a path rather than a URL. */
fun isLocalPathLike(dest: String): Boolean =
    dest.startsWith("file://") ||
        dest.startsWith("/") ||
        dest.startsWith("~/") ||
        dest.startsWith("./") ||
        dest.startsWith("../") ||
        dest.startsWith("\\\\") ||
        dest.length > 2 && dest[0].isLetter() && dest[1] == ':' && (dest[2] == '/' || dest[2] == '\\')

private val HashLocation = Regex("^L\\d+(?:C\\d+)?(?:-L\\d+(?:C\\d+)?)?$")
private val ColonLocation = Regex(":\\d+(?::\\d+)?(?:[-–]\\d+(?::\\d+)?)?$")
private const val CitationOpen = ":codex-file-citation{"
private val CitationPath = Regex("path\\s*=\\s*\"([^\"]*)\"")

/** The path of a `:codex-file-citation{path="…"}` directive starting at [from], if any. */
internal fun citationAt(text: String, from: Int): Pair<Int, String>? {
    if (!text.startsWith(CitationOpen, from)) return null
    val close = text.indexOf('}', from + CitationOpen.length)
    if (close < 0) return null
    val body = text.substring(from + CitationOpen.length, close)
    val path = CitationPath.find(body)?.groupValues?.get(1)
    return if (path.isNullOrEmpty()) null else (close + 1) to path
}

/** Strip a `file://` prefix and URL-decoding, then split off the location suffix. */
private fun splitLocation(dest: String): Pair<String, String?> {
    val decoded = decodeFileUrl(dest)
    val hash = decoded.substringAfter('#', "")
    if (hash.isNotEmpty() && HashLocation.matches(hash)) {
        return decoded.substringBefore('#') to "#$hash"
    }
    val match = ColonLocation.find(decoded)
    if (match != null) {
        return decoded.substring(0, match.range.first) to match.value
    }
    return decoded to null
}

private fun decodeFileUrl(dest: String): String {
    val stripped = if (dest.startsWith("file://")) dest.removePrefix("file://") else dest
    return decodePercent(stripped.replace('\\', '/'))
}

/** Percent-decode a path without turning `+` into a space, which a query decoder would. */
private fun decodePercent(text: String): String {
    if ('%' !in text) return text
    val bytes = java.io.ByteArrayOutputStream()
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (char == '%' && index + 2 < text.length) {
            val value = text.substring(index + 1, index + 3).toIntOrNull(16)
            if (value != null) {
                bytes.write(value)
                index += 3
                continue
            }
        }
        bytes.write(char.toString().toByteArray(Charsets.UTF_8))
        index++
    }
    return String(bytes.toByteArray(), Charsets.UTF_8)
}

/** A local path as the transcript shows it: cwd-relative when the file is under the workspace. */
fun displayLocalPath(path: String, cwd: String?): String {
    val normalized = path.replace('\\', '/')
    if (!normalized.startsWith("/") || cwd.isNullOrBlank()) return normalized
    val base = cwd.replace('\\', '/').trimEnd('/')
    if (base.isEmpty() || normalized == base) return normalized
    return if (normalized.startsWith("$base/")) normalized.removePrefix("$base/") else normalized
}

/**
 * A path as a diff row shows it.
 *
 * Mirrors `diff_render.rs::display_path_for`: a relative path stays as written, one under the
 * working directory is stripped, one that shares an ancestor with the working directory is
 * expressed relative to it (the git-root case, approximated without touching the filesystem), and
 * one under the runtime home is shown with `~`. Anything else stays absolute.
 */
fun displayDiffPath(path: String, cwd: String?, home: String?): String {
    val normalized = path.replace('\\', '/')
    if (!normalized.startsWith("/")) return normalized
    val base = cwd?.replace('\\', '/')?.trimEnd('/').orEmpty()
    if (base.isNotEmpty()) {
        if (normalized == base) return normalized
        if (normalized.startsWith("$base/")) return normalized.removePrefix("$base/")
        relativeTo(normalized, base)?.let { return it }
    }
    val homeBase = home?.replace('\\', '/')?.trimEnd('/').orEmpty()
    if (homeBase.isNotEmpty() && normalized.startsWith("$homeBase/")) {
        return "~/" + normalized.removePrefix("$homeBase/")
    }
    return normalized
}

/** `path` relative to `base` using `..` segments, or `null` when they share no ancestor. */
private fun relativeTo(path: String, base: String): String? {    val pathParts = path.split('/')
    val baseParts = base.split('/')
    var shared = 0
    while (shared < pathParts.size && shared < baseParts.size && pathParts[shared] == baseParts[shared]) {
        shared++
    }
    // Two absolute paths always share the empty root segment; a real ancestor needs one more.
    if (shared <= 1) return null
    val ups = baseParts.size - shared
    return (List(ups) { ".." } + pathParts.drop(shared)).joinToString("/")
}

/**
 * Act on a tapped link.
 *
 * A web link is handed to the platform. A local path cannot be opened by a browser and the app has
 * no editor, so the honest action is to put the exact target on the clipboard where it can be
 * pasted into the file browser or a terminal.
 */
fun openLink(context: Context, uriHandler: UriHandler, target: LinkTarget) {
    when (target) {
        is LinkTarget.Web -> runCatching { uriHandler.openUri(target.url) }
            .onFailure { Toast.makeText(context, target.url, Toast.LENGTH_SHORT).show() }

        is LinkTarget.Local -> {
            val text = target.path + target.location.orEmpty()
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText(text, text))
            Toast.makeText(
                context,
                context.getString(R.string.markdown_link_path_copied, text),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

/** The directory the runtime sets as `HOME`, used to shorten absolute paths for display. */
@Composable
fun runtimeHome(): String {
    val context = LocalContext.current
    return remember(context) { File(context.filesDir, "home").absolutePath }
}
