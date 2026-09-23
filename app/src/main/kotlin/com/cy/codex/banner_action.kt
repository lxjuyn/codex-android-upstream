package com.cy.codex

import com.cy.codex.protocol.protocol.v2.AddCreditsNudgeCreditType
import com.cy.codex.protocol.protocol.v2.RateLimitUpsellBanner

/** What a banner call-to-action resolves to on Android. */
sealed interface BannerIntent {
    /** Open a destination that passed the safety checks. */
    data class OpenUrl(val url: String) : BannerIntent

    /** Ask the account owner for more usage; this client has the nudge e-mail, not the web form. */
    data class NotifyOwner(val creditType: AddCreditsNudgeCreditType) : BannerIntent
}

private const val UsageUrl = "https://chatgpt.com/codex/settings/usage"
private const val WorkspaceUsageUrl = "https://chatgpt.com/admin/usage-limits/workspace"

/**
 * Resolve one cta `action` to something this client can actually do.
 *
 * Mirrors `resolve_action` in `codex-rs/tui/src/backend_banners/actions.rs`. Two branches are
 * deliberately not reproduced: `reset_usage`, because this client's reset flow is its own picker
 * with its own confirmation rather than a banner button, and the plan-dependent `open_pricing_dialog`
 * / `add_credits` workspace variants, because `BackendBanner.plan_type` is `serde(skip)` and never
 * reaches the wire. Those, and any action this build does not know, resolve to null and show no
 * button rather than a guess.
 */
fun bannerAction(banner: RateLimitUpsellBanner, action: String): BannerIntent? {
    return when (action) {
        "notify_owner",
        "contact_owner" -> BannerIntent.NotifyOwner(AddCreditsNudgeCreditType.Credits)

        "request_increase" -> {
            // No destination means "ask the owner instead"; a destination that fails the safety
            // checks is dropped outright, not quietly turned into the owner path.
            val destination = banner.requestUrl
            if (destination == null) {
                BannerIntent.NotifyOwner(AddCreditsNudgeCreditType.UsageLimit)
            } else {
                safeUrl(destination)?.let(BannerIntent::OpenUrl)
            }
        }

        else -> destinationFor(action)?.let(::safeUrl)?.let(BannerIntent::OpenUrl)
    }
}

/** The destinations that do not depend on the account's plan. */
private fun destinationFor(action: String): String? =
    when (action) {
        "add_credits", "buy_credits" -> "$UsageUrl?credits_modal=true"
        "buy_reset" -> "https://chatgpt.com/codex/purchase/reset"
        "view_usage", "request_increase_usage_settings" -> UsageUrl
        "view_workspace_usage", "increase_spend_cap" -> WorkspaceUsageUrl
        "open_plus_pricing_web" -> "https://chatgpt.com/explore/plus"
        "open_pro_pricing_web" -> "https://chatgpt.com/explore/pro"
        else -> null
    }

/**
 * `http(s)` only, with a host and no embedded credentials.
 *
 * The TUI applies the same checks before it will open a backend-supplied link; a destination that
 * fails them is dropped rather than handed to the browser.
 */
private fun safeUrl(raw: String): String? {
    val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank()) return null
    if (uri.userInfo != null) return null
    return raw
}
