package com.cy.codex.bottom_pane

import com.cy.codex.protocol.protocol.v2.McpElicitationRequest
import com.cy.codex.protocol.protocol.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLinkTest {

    private fun meta(json: String) = Json.parse(json)

    private fun urlElicitation(
        serverName: String,
        url: String,
        meta: kotlinx.serialization.json.JsonElement? = null,
    ) = McpElicitationRequest.Url(
        serverName = serverName,
        message = "Authorize",
        url = url,
        elicitationId = "call-1",
        meta = meta,
    )

    @Test
    fun `connector auth failure requires the is_auth_failure flag`() {
        val flagged = meta(
            """{"_codex_apps":{"connector_auth_failure":{"is_auth_failure":true,"connector_id":"abc","connector_name":"Calendar"}}}""",
        )
        val failure = connectorAuthFailure(flagged)
        assertEquals("abc", failure?.connectorId)
        assertEquals("Calendar", failure?.connectorName)

        assertNull(connectorAuthFailure(meta("""{"_codex_apps":{"connector_auth_failure":{"connector_id":"abc"}}}""")))
        assertNull(connectorAuthFailure(JsonNull))
        assertNull(connectorAuthFailure(meta("""{"other":{}}""")))
    }

    @Test
    fun `only https without userinfo is openable`() {
        assertEquals("https://example.test/auth", validateAppLinkUrl("https://example.test/auth", requireChatgptHost = false))
        assertNull(validateAppLinkUrl("http://example.test/auth", requireChatgptHost = false))
        assertNull(validateAppLinkUrl("https://user:pass@example.test/auth", requireChatgptHost = false))
        assertNull(validateAppLinkUrl("not a url", requireChatgptHost = false))
    }

    @Test
    fun `a codex_apps url must stay on chatgpt hosts`() {
        assertTrue(isAllowedChatgptAuthHost("chatgpt.com"))
        assertTrue(isAllowedChatgptAuthHost("app.chatgpt-staging.com"))
        assertFalse(isAllowedChatgptAuthHost("evilchatgpt.com"))
        assertFalse(isAllowedChatgptAuthHost("chatgpt.com.evil.test"))
        assertNull(validateAppLinkUrl("https://example.test/apps/1", requireChatgptHost = true))
        assertEquals(
            "https://chatgpt.com/apps/calendar/abc",
            validateAppLinkUrl("https://chatgpt.com/apps/calendar/abc", requireChatgptHost = true),
        )
    }

    @Test
    fun `codex_apps builds an auth prompt and generic urls an external one`() {
        val auth = appLinkPrompt(
            urlElicitation(
                serverName = "codex_apps",
                url = "https://chatgpt.com/apps/calendar/abc",
                meta = meta(
                    """{"_codex_apps":{"connector_auth_failure":{"is_auth_failure":true,"connector_id":"abc","connector_name":"Calendar"}}}""",
                ),
            ),
        )
        assertEquals(AppLinkKind.Auth, auth?.kind)
        assertEquals("Calendar", auth?.connectorName)

        // Missing metadata is not an auth failure, so the request must not be shown at all.
        assertNull(appLinkPrompt(urlElicitation("codex_apps", "https://chatgpt.com/apps/1/2")))

        val external = appLinkPrompt(urlElicitation("docs", "https://example.test/auth"))
        assertEquals(AppLinkKind.ExternalAction, external?.kind)
        assertEquals("docs", external?.serverName)
        assertNull(appLinkPrompt(urlElicitation("docs", "http://example.test/auth")))
    }

    @Test
    fun `only the connector flow triggers the app refresh`() {
        val connector = urlElicitation(
            serverName = "codex_apps",
            url = "https://chatgpt.com/apps/calendar/abc",
            meta = meta(
                """{"_codex_apps":{"connector_auth_failure":{"is_auth_failure":true,"connector_id":"abc"}}}""",
            ),
        )
        assertTrue(connector.isConnectorAuth())
        assertFalse(urlElicitation("docs", "https://example.test/auth").isConnectorAuth())
    }
}
