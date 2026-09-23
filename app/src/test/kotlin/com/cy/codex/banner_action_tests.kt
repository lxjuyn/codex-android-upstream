package com.cy.codex

import com.cy.codex.protocol.protocol.v2.AddCreditsNudgeCreditType
import com.cy.codex.protocol.protocol.v2.RateLimitUpsellBanner
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Locks the cta vocabulary to what `codex-rs/tui/src/backend_banners/actions.rs` resolves. */
class BannerActionTest {
    private fun banner(requestUrl: String? = null) =
        RateLimitUpsellBanner(
            bannerType = "luna_reserve",
            title = "Reserve",
            description = "Ordinary usage is spent",
            requestUrl = requestUrl,
        )

    @Test
    fun `owner actions become the nudge e-mail`() {
        assertEquals(
            BannerIntent.NotifyOwner(AddCreditsNudgeCreditType.Credits),
            bannerAction(banner(), "notify_owner"),
        )
        assertEquals(
            BannerIntent.NotifyOwner(AddCreditsNudgeCreditType.Credits),
            bannerAction(banner(), "contact_owner"),
        )
    }

    @Test
    fun `request_increase uses the backend url when it has one`() {
        assertEquals(
            BannerIntent.OpenUrl("https://example.test/increase"),
            bannerAction(banner(requestUrl = "https://example.test/increase"), "request_increase"),
        )
    }

    @Test
    fun `request_increase without a url asks the owner`() {
        assertEquals(
            BannerIntent.NotifyOwner(AddCreditsNudgeCreditType.UsageLimit),
            bannerAction(banner(), "request_increase"),
        )
    }

    @Test
    fun `an unsafe destination is dropped rather than opened or rerouted`() {
        assertNull(bannerAction(banner(requestUrl = "javascript:alert(1)"), "request_increase"))
        assertNull(
            bannerAction(banner(requestUrl = "https://user:pass@example.test/x"), "request_increase"),
        )
    }

    @Test
    fun `known usage actions open their fixed pages`() {
        assertEquals(
            BannerIntent.OpenUrl("https://chatgpt.com/codex/settings/usage?credits_modal=true"),
            bannerAction(banner(), "add_credits"),
        )
        assertEquals(
            BannerIntent.OpenUrl("https://chatgpt.com/codex/settings/usage"),
            bannerAction(banner(), "view_usage"),
        )
        assertEquals(
            BannerIntent.OpenUrl("https://chatgpt.com/admin/usage-limits/workspace"),
            bannerAction(banner(), "view_workspace_usage"),
        )
        assertEquals(
            BannerIntent.OpenUrl("https://chatgpt.com/explore/pro"),
            bannerAction(banner(), "open_pro_pricing_web"),
        )
    }

    @Test
    fun `actions this build cannot carry out show no button`() {
        // `reset_usage` keeps this client's own picker; `open_pricing_dialog` needs the plan type,
        // which BackendBanner never puts on the wire; anything unknown is left alone.
        assertNull(bannerAction(banner(), "reset_usage"))
        assertNull(bannerAction(banner(), "open_pricing_dialog"))
        assertNull(bannerAction(banner(), "something_new"))
    }
}
