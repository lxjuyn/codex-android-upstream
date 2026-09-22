package com.cy.codex.bottom_pane

import com.cy.codex.protocol.protocol.v2.McpElicitationRequest
import java.net.URI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * The app-link flow of a URL-mode elicitation, mirroring `tui/src/bottom_pane/app_link_view.rs`.
 *
 * The server asks the user to visit a page: sign in to a connector (`codex_apps` with the
 * `_codex_apps.connector_auth_failure` metadata), or complete some other browser action. The URL is
 * validated before it is ever opened, and [AppLinkPrompt] carries only data — every word the user
 * reads is resolved from a string resource by the composable.
 */
internal enum class AppLinkKind { Auth, ExternalAction }

/** Which screen of the flow is showing; see [AppLinkPrompt]'s composable. */
internal enum class AppLinkScreen { Link, Confirmation }

internal data class AppLinkPrompt(
    val kind: AppLinkKind,
    /** Connector display name, from the auth-failure metadata; null for a generic URL. */
    val connectorName: String? = null,
    /** Connector id, used to name the app when the metadata has no display name. */
    val connectorId: String? = null,
    val serverName: String,
    val message: String,
    val url: String,
)

/** The connector fields this client reads out of `_codex_apps.connector_auth_failure`. */
internal data class ConnectorAuthFailure(
    val connectorId: String?,
    val connectorName: String?,
)

private const val CodexAppsServerName = "codex_apps"
private const val McpToolCodexAppsMetaKey = "_codex_apps"
private const val ConnectorAuthFailureMetaKey = "connector_auth_failure"
private const val ConnectorAuthFailureIsAuthFailureKey = "is_auth_failure"
private const val ConnectorAuthFailureConnectorIdKey = "connector_id"
private const val ConnectorAuthFailureConnectorNameKey = "connector_name"

/**
 * Read the auth-failure metadata, or null when the `is_auth_failure` flag is not exactly true.
 *
 * The flag check is what `codex-mcp/src/auth_elicitation.rs` does before it builds the request:
 * metadata that forgot the flag is not an auth failure, and guessing here would show a sign-in
 * screen for something else.
 */
internal fun connectorAuthFailure(meta: JsonElement?): ConnectorAuthFailure? {
    val failure = ((meta as? JsonObject)?.get(McpToolCodexAppsMetaKey) as? JsonObject)
        ?.get(ConnectorAuthFailureMetaKey) as? JsonObject
        ?: return null
    if ((failure[ConnectorAuthFailureIsAuthFailureKey] as? JsonPrimitive)?.booleanOrNull != true) {
        return null
    }
    return ConnectorAuthFailure(
        connectorId = (failure[ConnectorAuthFailureConnectorIdKey] as? JsonPrimitive)?.content?.trim()?.ifEmpty { null },
        connectorName = (failure[ConnectorAuthFailureConnectorNameKey] as? JsonPrimitive)?.content?.trim()?.ifEmpty { null },
    )
}

/**
 * Validate an external URL before opening it.
 *
 * `requireChatgptHost` is set for the `codex_apps` server: a connector sign-in URL that points
 * anywhere else is not a URL this client will hand to the browser, because the request that carried
 * it was supposed to come from ChatGPT.
 */
internal fun validateAppLinkUrl(url: String, requireChatgptHost: Boolean): String? {
    val parsed = runCatching { URI(url) }.getOrNull() ?: return null
    if (!parsed.scheme.equals("https", ignoreCase = true)) return null
    val host = parsed.host ?: return null
    if (!parsed.userInfo.isNullOrEmpty()) return null
    if (requireChatgptHost && !isAllowedChatgptAuthHost(host)) return null
    return url
}

internal fun isAllowedChatgptAuthHost(host: String): Boolean {
    val lower = host.lowercase()
    return lower == "chatgpt.com" ||
        lower == "chatgpt-staging.com" ||
        lower.endsWith(".chatgpt.com") ||
        lower.endsWith(".chatgpt-staging.com")
}

/**
 * Build the app-link prompt for a URL elicitation, or null when the URL must not be opened.
 *
 * For `codex_apps` the connector metadata is required, exactly as upstream: without it there is
 * nothing to sign in to and the request is declined rather than shown as a generic link.
 */
internal fun appLinkPrompt(payload: McpElicitationRequest.Url): AppLinkPrompt? {
    if (payload.serverName == CodexAppsServerName) {
        val failure = connectorAuthFailure(payload.meta) ?: return null
        val url = validateAppLinkUrl(payload.url, requireChatgptHost = true) ?: return null
        return AppLinkPrompt(
            kind = AppLinkKind.Auth,
            connectorName = failure.connectorName,
            connectorId = failure.connectorId ?: payload.elicitationId,
            serverName = payload.serverName,
            message = payload.message,
            url = url,
        )
    }
    val url = validateAppLinkUrl(payload.url, requireChatgptHost = false) ?: return null
    return AppLinkPrompt(
        kind = AppLinkKind.ExternalAction,
        serverName = payload.serverName,
        message = payload.message,
        url = url,
    )
}

/** True when an accepted elicitation came from the connector sign-in flow. */
internal fun McpElicitationRequest.isConnectorAuth(): Boolean =
    serverName == CodexAppsServerName && connectorAuthFailure(meta) != null
