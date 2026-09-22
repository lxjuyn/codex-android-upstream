package com.cy.codex.protocol.protocol.v2

/**
 * `account/…` — signing in, signing out, and the account's own read models.
 *
 * Mirrors `schema/typescript/v2/LoginAccountParams.ts`, which is an internally-tagged union over six
 * sign-in methods. The phone only starts two of them (ChatGPT and an API key) but the whole union is
 * carried, because [LoginAccountResponse] has to be decoded for whichever one the user picked and a
 * half-modelled union silently decodes the wrong variant.
 */

/** `account/login/start` params. The `type` tag decides which variant is on the wire. */
sealed interface LoginAccountParams {
    /** Paste an API key; completes immediately. */
    data class ApiKey(val apiKey: String) : LoginAccountParams

    /** Browser/device sign-in: the server returns a URL (or a code) and waits. */
    data class Chatgpt(
        val appBrand: LoginAppBrand = LoginAppBrand.Codex,
        val useHostedLoginSuccessPage: Boolean = true,
        val codexStreamlinedLogin: Boolean = false,
    ) : LoginAccountParams

    /** Headless sign-in: show `userCode`, send the user to `verificationUrl`. */
    data object ChatgptDeviceCode : LoginAccountParams

    /** Re-hydrate a session from tokens the host already holds (e.g. an Android account). */
    data class ChatgptAuthTokens(
        val accessToken: String,
        val chatgptAccountId: String,
        val chatgptPlanType: String? = null,
    ) : LoginAccountParams

    data class AmazonBedrock(
        val apiKey: String,
        val region: String,
    ) : LoginAccountParams

    data class AmazonBedrockAccessKeys(
        val accessKeyId: String,
        val secretAccessKey: String,
        val region: String,
        val sessionToken: String? = null,
    ) : LoginAccountParams
}

/** Which product the browser sign-in page should be branded as. */
enum class LoginAppBrand(val wire: String) {
    Codex("codex"),
    Chatgpt("chatgpt"),
}

/** `account/login/start` response. */
sealed interface LoginAccountResponse {
    /** The key was accepted; there is nothing to wait for. */
    data object ApiKey : LoginAccountResponse

    /** Open [authUrl]; completion arrives as `account/login/completed` with this [loginId]. */
    data class Chatgpt(val loginId: String, val authUrl: String) : LoginAccountResponse

    /** Headless variant: show [userCode] and send the user to [verificationUrl]. */
    data class ChatgptDeviceCode(
        val loginId: String,
        val userCode: String,
        val verificationUrl: String,
    ) : LoginAccountResponse

    data object ChatgptAuthTokens : LoginAccountResponse

    data object AmazonBedrock : LoginAccountResponse

    /** The server answered with a variant this client does not model. */
    data object Unknown : LoginAccountResponse
}

/** `account/login/cancel`. */
data class CancelLoginAccountParams(val loginId: String)

/** `account/login/completed`. */
data class AccountLoginCompletedNotification(
    val success: Boolean,
    val loginId: String? = null,
    val error: String? = null,
)

/** `account/workspaceMessages/read` response. Mirrors `GetWorkspaceMessagesResponse`. */
data class WorkspaceMessagesResponse(
    val featureEnabled: Boolean,
    val messages: List<WorkspaceMessage>,
)

/** One account-level notice. Mirrors `WorkspaceMessage`. */
data class WorkspaceMessage(
    val messageId: String,
    val messageType: WorkspaceMessageType,
    val messageBody: String,
    /** Server-side render kind; an unknown future value decodes to [WorkspaceMessageType.Unknown]. */
    val createdAt: Long? = null,
    val archivedAt: Long? = null,
)

enum class WorkspaceMessageType(val wire: String) {
    Headline("headline"),
    Announcement("announcement"),
    Unknown("unknown"),
    ;

    companion object {
        fun fromWire(value: String?): WorkspaceMessageType =
            entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

/** `account/rateLimitResetCredit/consume`. */
data class ConsumeRateLimitResetCreditParams(
    /** Identifies one logical reset attempt; reuse it when retrying. */
    val idempotencyKey: String,
    val creditId: String? = null,
)

/** What a reset-credit redemption did. */
enum class ConsumeRateLimitResetCreditOutcome(val wire: String) {
    /** A credit was spent and the eligible windows were reset. */
    Reset("reset"),

    /** No current window is eligible for a reset. */
    NothingToReset("nothingToReset"),

    /** The account has no earned credits. */
    NoCredit("noCredit"),

    /** This idempotency key already completed a reset. */
    AlreadyRedeemed("alreadyRedeemed"),
    ;

    companion object {
        fun fromWire(value: String?): ConsumeRateLimitResetCreditOutcome =
            entries.firstOrNull { it.wire == value } ?: NothingToReset
    }
}

data class ConsumeRateLimitResetCreditResponse(
    val outcome: ConsumeRateLimitResetCreditOutcome = ConsumeRateLimitResetCreditOutcome.NothingToReset,
)

/** `account/sendAddCreditsNudgeEmail`. */
data class SendAddCreditsNudgeEmailParams(val creditType: AddCreditsNudgeCreditType)

enum class AddCreditsNudgeCreditType(val wire: String) {
    Credits("credits"),
    UsageLimit("usage_limit"),
}

data class SendAddCreditsNudgeEmailResponse(val status: AddCreditsNudgeEmailStatus = AddCreditsNudgeEmailStatus.Sent)

enum class AddCreditsNudgeEmailStatus(val wire: String) {
    Sent("sent"),
    CooldownActive("cooldown_active"),
    ;

    companion object {
        fun fromWire(value: String?): AddCreditsNudgeEmailStatus =
            entries.firstOrNull { it.wire == value } ?: CooldownActive
    }
}

/** `account/bedrock/discover`. */
data class BedrockDiscoverResponse(
    val profiles: List<BedrockAwsProfile> = emptyList(),
    val environmentCredentials: List<BedrockEnvironmentCredential> = emptyList(),
)

data class BedrockAwsProfile(val name: String, val region: String? = null)

data class BedrockEnvironmentCredential(
    /** `accessKeys` or `bedrockApiKey`. */
    val type: String = "",
    val region: String? = null,
)

/**
 * `account/bedrock/setup`, an internally-tagged union over how credentials are selected: an AWS
 * profile on disk, or the process environment.
 */
sealed interface BedrockSetupParams {
    data class Profile(val profile: String, val region: String) : BedrockSetupParams

    data class Environment(val region: String) : BedrockSetupParams
}
