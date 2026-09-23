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
    fun `reset_usage keeps this client's own picker`() {
        assertEquals(BannerIntent.ResetUsage, bannerAction(banner(), "reset_usage"))
    }

    @Test
    fun `personal usage actions open their fixed pages`() {
        assertEquals(
            BannerIntent.OpenUrl("https://chatgpt.com/codex/settings/usage?credits_modal=true"),
            bannerAction(banner(), "add_credits", planType = "plus"),
        )
        assertEquals(
            BannerIntent.OpenUrl("https://chatgpt.com/codex/settings/usage"),
            bannerAction(banner(), "view_usage"),
        )
        assertEquals(
            BannerIntent.OpenUrl("https://chatgpt.com/explore/pro"),
            bannerAction(banner(), "open_pro_pricing_web"),
        )
    }

    @Test
    fun `workspace plans get the admin destination and its account id`() {
        assertEquals(
            BannerIntent.OpenUrl(
                "https://chatgpt.com/admin/billing?codex_credit_action=add_credits&account_id=acct",
            ),
            bannerAction(banner(), "add_credits", planType = "business", accountId = "acct"),
        )
        assertEquals(
            BannerIntent.OpenUrl("https://chatgpt.com/admin/usage-limits/workspace?account_id=acct"),
            bannerAction(banner(), "view_workspace_usage", planType = "edu", accountId = "acct"),
        )
    }

    @Test
    fun `an admin route without an account id is dropped`() {
        assertNull(bannerAction(banner(), "view_workspace_usage", planType = "team", accountId = null))
        assertNull(bannerAction(banner(), "view_workspace_usage", planType = "team", accountId = " "))
    }

    @Test
    fun `the pricing dialog is rewritten from the plan`() {
        assertEquals(
            BannerIntent.OpenUrl("https://chatgpt.com/?cta_tab=personal&highlight_plan=pro#pricing"),
            bannerAction(banner(), "open_pricing_dialog", planType = "plus"),
        )
        assertEquals(
            BannerIntent.OpenUrl("https://chatgpt.com/?cta_tab=personal&highlight_plan=plus#pricing"),
            bannerAction(banner(), "open_pricing_dialog", planType = "free"),
        )
        assertEquals(
            BannerIntent.OpenUrl(
                "https://chatgpt.com/?cta_tab=personal&highlight_plan=pro&pro_variant=2x#pricing",
            ),
            bannerAction(banner(), "open_pricing_dialog", planType = "prolite"),
        )
    }

    @Test
    fun `an unknown action shows no button`() {
        assertNull(bannerAction(banner(), "something_new"))
    }
}
