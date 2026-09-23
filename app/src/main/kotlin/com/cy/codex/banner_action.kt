package com.cy.codex

import com.cy.codex.protocol.protocol.v2.AddCreditsNudgeCreditType
import com.cy.codex.protocol.protocol.v2.RateLimitUpsellBanner

/** What a banner call-to-action resolves to on Android. */
sealed interface BannerIntent {
    /** Open a destination that passed the safety checks. */
    data class OpenUrl(val url: String) : BannerIntent

    /** Ask the account owner for more usage; this client has the nudge e-mail, not the web form. */
    data class NotifyOwner(val creditType: AddCreditsNudgeCreditType) : BannerIntent

    /** Open the account page, where the reset credits and their confirmation live. */
    data object ResetUsage : BannerIntent
}

private const val UsageUrl = "https://chatgpt.com/codex/settings/usage"
private const val WorkspaceUsageUrl = "https://chatgpt.com/admin/usage-limits/workspace"
private const val WorkspaceBillingUrl =
    "https://chatgpt.com/admin/billing?codex_credit_action=add_credits"

/** The plans an admin destination belongs to; mirrors `PlanType::is_workspace_account`. */
private val WorkspacePlans =
    setOf(
        "team",
        "self_serve_business_prolite",
        "self_serve_business_usage_based",
        "business",
        "ent26",
        "enterprise_cbp_automation",
        "enterprise_cbp_usage_based",
        "enterprise",
        "edu",
        "edu_plus",
        "edu_pro",
    )

/**
 * Resolve one cta `action` to something this client can actually do.
 *
 * Mirrors `resolve_action` in `codex-rs/tui/src/backend_banners/actions.rs`. [planType] and
 * [accountId] stand in for the TUI's locally-injected `BackendBanner.plan_type` / `account_id`,
 * which the wire marks `serde(skip)` and so never carries.
 */
fun bannerAction(
    banner: RateLimitUpsellBanner,
    action: String,
    planType: String? = null,
    accountId: String? = null,
): BannerIntent? {
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

        // Keeps this client's own picker and its explicit confirmation before a reset is spent.
        "reset_usage" -> BannerIntent.ResetUsage

        // The TUI rewrites this one from the plan type rather than opening what the backend sent.
        "open_pricing_dialog" -> safeUrl(pricingDialogUrl(planType))?.let(BannerIntent::OpenUrl)

        else ->
            destinationFor(action, planType, accountId)
                ?.let(::safeUrl)
                ?.let(BannerIntent::OpenUrl)
    }
}

/** The destinations that do not depend on when the banner arrived. */
private fun destinationFor(action: String, planType: String?, accountId: String?): String? {
    val workspace = planType != null && planType in WorkspacePlans
    val destination =
        when (action) {
            "add_credits", "buy_credits" ->
                if (workspace) WorkspaceBillingUrl else "$UsageUrl?credits_modal=true"

            "buy_reset" -> "https://chatgpt.com/codex/purchase/reset"
            "view_usage", "request_increase_usage_settings" -> UsageUrl
            "view_workspace_usage", "increase_spend_cap" -> WorkspaceUsageUrl
            "open_plus_pricing_web" -> "https://chatgpt.com/explore/plus"
            "open_pro_pricing_web" -> "https://chatgpt.com/explore/pro"
            else -> return null
        }
    return withAccount(destination, accountId)
}

/** An admin route selects the workspace it belongs to; without an account id there is no route. */
private fun withAccount(destination: String, accountId: String?): String? {
    val uri = runCatching { java.net.URI(destination) }.getOrNull() ?: return destination
    if (!uri.path.orEmpty().startsWith("/admin/")) return destination
    val id = accountId?.takeIf { it.isNotBlank() } ?: return null
    return "$destination${if ("?" in destination) "&" else "?"}account_id=$id"
}

/** The pricing dialog the TUI rewrites from the plan: `?cta_tab=personal&highlight_plan=…`. */
private fun pricingDialogUrl(planType: String?): String {
    val target = if (planType == "plus" || planType == "prolite") "pro" else "plus"
    val variant = if (planType == "prolite") "&pro_variant=2x" else ""
    return "https://chatgpt.com/?cta_tab=personal&highlight_plan=$target$variant#pricing"
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
