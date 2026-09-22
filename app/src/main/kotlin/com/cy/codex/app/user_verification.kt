package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cy.codex.AppEvent
import com.cy.codex.ButtonRole
import com.cy.codex.CatalogState
import com.cy.codex.CodexButton
import com.cy.codex.CodexDivider
import com.cy.codex.R
import com.cy.codex.SectionCard
import com.cy.codex.SurfaceBackButton
import com.cy.codex.SurfaceHeader
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.ValueRow
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.UserVerificationEnrollResponse
import com.cy.codex.protocol.protocol.v2.UserVerificationStatusResponse
import com.cy.codex.protocol.protocol.v2.UserVerificationVerifyParams
import com.cy.codex.successColor
import com.cy.codex.warningColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * User verification: enrolling a local credential and signing a challenge with it.
 *
 * Mirrors `codex-rs/tui/src/app/user_verification.rs` and `…/bottom_pane/user_verification.rs`, and
 * covers the whole `userVerification` family — status, enroll, verify, cancel and delete.
 *
 * Three protocol facts decide this page's shape and are the reason it does not present verification
 * as a state machine with a "pending" step:
 *
 *  - **There is no `state` on the wire.** `userVerification/status` answers with a credential id
 *    and, only when verification cannot run at all, an `unavailableReason` plus a human
 *    `unavailableMessage`. So there are exactly three shapes — unavailable, not enrolled, enrolled
 *    — and no fourth one for "verifying". A server that answers with neither field is read as
 *    not-enrolled, because that is the one shape in which enrolling is the useful next action.
 *  - **Enrolling registers nothing.** `userVerification/enroll` creates or reuses a credential on
 *    this device and answers with its public metadata. Backend registration is the caller's job
 *    afterwards, and an older app-server may omit `algorithm` and `publicKey` entirely, so both are
 *    shown only when they are there rather than being filled in with a guess.
 *  - **Verifying is a signing primitive, not a check of the user's state.**
 *    `userVerification/verify` takes a challenge plus display context and answers with a proof — a
 *    signature. Nothing about it consults the server or an elicitation. On a phone the real
 *    implementation would hand the challenge to the platform keystore and let it prompt for
 *    biometrics; this client has no keystore binding, so the form asks the user to paste the
 *    challenge and the page reports what came back instead of pretending a prompt happened.
 *
 * Cancelling is not an undo: `userVerification/cancel` stops an in-flight verification and a
 * completed one is not rolled back by it, which is why it sits beside the signing action under that
 * name rather than being offered as "undo".
 */
@Composable
fun UserVerificationScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val status = catalog.userVerification
    val shape = status.shape()
    // Every string on this line is a resource: the server's own sentence when it sent one, this
    // page's name for the shape when the server has not answered yet, and the shape's label
    // otherwise. The message is preferred over the label because the server knows *why* it is
    // unavailable and a label can only name the category.
    val serverMessage = status?.unavailableMessage
    val subtitle = when {
        !serverMessage.isNullOrBlank() -> serverMessage
        status == null -> stringResource(R.string.user_verification_page_reading)
        else -> shape.label()
    }
    var signing by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SurfaceHeader(
            title = stringResource(R.string.user_verification_page_title),
            subtitle = subtitle,
            leading = {
                SurfaceBackButton(stringResource(R.string.user_verification_page_back), onBack)
            },
            trailing = {
                IconButton(
                    onClick = { onEvent(AppEvent.ReloadUserVerification) },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(
                            R.string.user_verification_page_refresh,
                        ),
                        modifier = Modifier.size(UiConsts.IconRefresh),
                        tint = colors.primary,
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UiConsts.ScreenMargin)
                .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            UserVerificationStatusCard(shape = shape, status = status)
            when (shape) {
                UserVerificationShape.Unavailable -> UnavailableCard(onEvent = onEvent)
                UserVerificationShape.NotEnrolled -> EnrollCard(onEvent = onEvent)
                UserVerificationShape.Enrolled -> EnrolledCard(
                    credential = catalog.userVerificationCredential,
                    onEvent = onEvent,
                    onSign = { signing = true },
                )
            }
        }
    }

    if (signing) {
        val pageTitle = stringResource(R.string.user_verification_page_title)
        UserVerificationSignSheet(
            onDismiss = { signing = false },
            onSubmit = { challenge, description ->
                onEvent(
                    AppEvent.VerifyUserVerification(
                        UserVerificationVerifyParams(
                            challenge = challenge,
                            // The title is the page's own name for the operation, which is the
                            // display context a platform prompt would have shown: the user has
                            // already read it by the time they submit, so it is not a second
                            // question, it is a record of what was approved.
                            title = pageTitle,
                            description = description,
                        ),
                    ),
                )
                signing = false
            },
        )
    }
}

/**
 * The three shapes this page can be in, read out of the status answer.
 *
 * Deliberately three and not more: the protocol carries no verification *state*, so "pending",
 * "verified" and "failed" exist only inside a single `userVerification/verify` call and are never
 * something this page is told about. Inventing them here would make the page claim to know
 * something the server never said.
 */
private enum class UserVerificationShape {
    Unavailable,
    NotEnrolled,
    Enrolled,
}

/** This page's headline for a shape; the body of the card spells the shape out again in full. */
@Composable
private fun UserVerificationShape.label(): String = stringResource(
    when (this) {
        UserVerificationShape.Unavailable -> R.string.user_verification_page_state_unavailable
        UserVerificationShape.NotEnrolled -> R.string.user_verification_page_state_not_enrolled
        UserVerificationShape.Enrolled -> R.string.user_verification_page_state_enrolled
    },
)

/**
 * Which of the three shapes [this] describes.
 *
 * Unavailable wins over the credential id on purpose: a reason is the server saying verification
 * cannot run here at all, and the id it may still be carrying is then moot — offering to sign with
 * it would offer an action that is guaranteed to fail.
 */
private fun UserVerificationStatusResponse?.shape(): UserVerificationShape = when {
    this?.unavailableReason != null -> UserVerificationShape.Unavailable
    this?.credentialId.isNullOrEmpty() -> UserVerificationShape.NotEnrolled
    else -> UserVerificationShape.Enrolled
}

/**
 * The status card: which shape applies, and the id it applies to.
 *
 * The credential id is monospace because it is a value a human compares against what the server
 * holds; a proportional face makes `l` and `1` the same shape and the comparison stops being one a
 * person can make by eye.
 */
@Composable
private fun UserVerificationStatusCard(
    shape: UserVerificationShape,
    status: UserVerificationStatusResponse?,
) {
    val tint = when (shape) {
        // Green and amber carry the meaning here, so the state row reads as a state rather than as
        // one more label/value pair.
        UserVerificationShape.Enrolled -> successColor()
        UserVerificationShape.Unavailable -> warningColor()
        UserVerificationShape.NotEnrolled -> MiuixTheme.colorScheme.onSurface
    }
    SectionCard(
        title = stringResource(R.string.user_verification_page_status),
        icon = MiuixIcons.Lock,
    ) {
        ValueRow(
            label = stringResource(R.string.user_verification_page_state_label),
            value = shape.label(),
            tint = tint,
        )
        if (shape == UserVerificationShape.Unavailable) {
            CodexDivider()
            ValueRow(
                label = stringResource(R.string.user_verification_page_reason),
                value = status?.unavailableReason?.label().orEmpty(),
            )
            CodexDivider()
            ValueRow(
                label = stringResource(R.string.user_verification_page_message),
                value = status?.unavailableMessage.orEmpty(),
            )
        }
        if (shape == UserVerificationShape.Enrolled) {
            CodexDivider()
            ValueRow(
                label = stringResource(R.string.user_verification_page_credential_id),
                value = status?.credentialId.orEmpty(),
                monospace = true,
            )
        }
    }
}

/**
 * What the page says when the platform cannot verify at all — and the only action it offers.
 *
 * Enrolling and signing are both hidden in this shape, because a device the server has already
 * called unavailable cannot complete either one: the capability is biometrics plus a keystore, and
 * both live on the device rather than in the protocol. A re-read is the one action that can change
 * the answer, since the availability the server reported is a property of this device's state and
 * not of the request.
 */
@Composable
private fun UnavailableCard(onEvent: (AppEvent) -> Unit) {
    SectionCard(
        title = stringResource(R.string.user_verification_page_unavailable),
        icon = MiuixIcons.Info,
    ) {
        UserVerificationNote(stringResource(R.string.user_verification_page_unavailable_detail))
        CodexButton(
            text = stringResource(R.string.user_verification_page_refresh),
            onClick = { onEvent(AppEvent.ReloadUserVerification) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.Space4),
            role = ButtonRole.Secondary,
        )
    }
}

/**
 * The one action of the not-enrolled shape.
 *
 * *Not* the not-enrolled shape's whole story: `userVerification/enroll` mints or reuses a
 * credential in this device's keystore and answers with the public half of it. It signs nothing,
 * registers nothing with the backend and prompts for nothing — so the note under the button says
 * what the tap will and will not do before the user spends a biometric on it.
 */
@Composable
private fun EnrollCard(onEvent: (AppEvent) -> Unit) {
    SectionCard(
        title = stringResource(R.string.user_verification_page_enroll),
        icon = MiuixIcons.Ok,
    ) {
        UserVerificationNote(stringResource(R.string.user_verification_page_enroll_note))
        CodexButton(
            text = stringResource(R.string.user_verification_page_enroll),
            onClick = { onEvent(AppEvent.EnrollUserVerification) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.Space4),
        )
    }
}

/**
 * The enrolled shape: the credential's public metadata, the signing action, and the two ways out.
 *
 * The metadata comes from [credential] — the answer to the enroll call — and not from the status
 * answer, which never carries it. Both fields are optional on the wire, so a row appears only when
 * its field is present: on an older app-server that omits them this card shows the credential id
 * and nothing that looks like a fact but is really a blank.
 */
@Composable
private fun EnrolledCard(
    credential: UserVerificationEnrollResponse?,
    onEvent: (AppEvent) -> Unit,
    onSign: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.user_verification_page_credential),
        icon = MiuixIcons.Lock,
    ) {
        if (credential != null) {
            credential.algorithm?.takeIf { it.isNotBlank() }?.let { algorithm ->
                ValueRow(
                    label = stringResource(R.string.user_verification_page_algorithm),
                    value = algorithm,
                    monospace = true,
                )
                CodexDivider()
            }
            credential.publicKey?.takeIf { it.isNotBlank() }?.let { key ->
                // The marker lives in a resource like every other visible character on this page.
                val ellipsis = stringResource(R.string.user_verification_page_ellipsis)
                ValueRow(
                    label = stringResource(R.string.user_verification_page_public_key),
                    value = truncated(key, ellipsis),
                    monospace = true,
                )
                CodexDivider()
            }
            if (credential.algorithm.isNullOrBlank() || credential.publicKey.isNullOrBlank()) {
                UserVerificationNote(
                    stringResource(R.string.user_verification_page_metadata_absent),
                )
            }
        }
        CodexButton(
            text = stringResource(R.string.user_verification_page_sign),
            onClick = onSign,
            modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.Space4),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = UiConsts.Space4,
                    end = UiConsts.Space4,
                    top = UiConsts.Space8,
                ),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space8),
        ) {
            CodexButton(
                text = stringResource(R.string.user_verification_page_cancel),
                onClick = { onEvent(AppEvent.CancelUserVerification) },
                modifier = Modifier.fillMaxWidth(),
                role = ButtonRole.Secondary,
            )
            CodexButton(
                text = stringResource(R.string.user_verification_page_delete),
                onClick = { onEvent(AppEvent.DeleteUserVerification) },
                modifier = Modifier.fillMaxWidth(),
                role = ButtonRole.Destructive,
            )
        }
        // The two buttons above are not opposites and the page has to say so: cancel stops a
        // verification that is still running, and nothing that already returned a proof is taken
        // back by it. Delete is the destructive half — it removes the local credential, so signing
        // stops working until a new one is enrolled.
        UserVerificationNote(stringResource(R.string.user_verification_page_cancel_note))
    }
}

/**
 * The body text of a card's explanatory paragraph.
 *
 * One composable so the four notes on this page share a leading and a colour; the sentence itself
 * is always a string resource, because each of them is a claim about what the protocol does.
 */
@Composable
private fun UserVerificationNote(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space6),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * The signing form: a challenge to sign, and the display context that goes with it.
 *
 * Two protocol limits are visible here rather than hidden.
 *
 *  - The description is collected because `userVerification/verify` takes display context beside
 *    the challenge, and a real implementation would put both in the keystore prompt — but
 *    [AppEvent.VerifyUserVerification] carries one string, so only the challenge reaches the
 *    server. The field's help text says that; a form that quietly dropped it would be worse than
 *    one that never offered it.
 *  - `userVerification/cancel` addresses a *verification* by request id, and its `AppEvent` takes
 *    no parameters — the shell supplies the id. So one verification at a time is all this page can
 *    express, and the form cannot be reopened while it is open.
 *
 * The sheet is dismissed through the app's shared [LocalDismissState] so the grabber, the scrim, a
 * drag and the back gesture all reach the same callback as the submit button.
 */
@Composable
private fun UserVerificationSignSheet(
    onDismiss: () -> Unit,
    onSubmit: (challenge: String, description: String) -> Unit,
) {
    val dismiss = LocalDismissState.current
    CompositionLocalProvider(LocalDismissState provides null) {
        FormSheet(
            title = stringResource(R.string.user_verification_page_sign),
            subtitle = stringResource(R.string.user_verification_page_sign_detail),
            fields = listOf(
                FormField(
                    key = "challenge",
                    label = stringResource(R.string.user_verification_page_challenge),
                    placeholder = stringResource(
                        R.string.user_verification_page_challenge_placeholder,
                    ),
                    help = stringResource(R.string.user_verification_page_challenge_help),
                ),
                FormField(
                    key = "description",
                    label = stringResource(R.string.user_verification_page_description),
                    placeholder = stringResource(
                        R.string.user_verification_page_description_placeholder,
                    ),
                    required = false,
                    help = stringResource(R.string.user_verification_page_description_help),
                ),
            ),
            confirmLabel = stringResource(R.string.user_verification_page_sign_confirm),
            onDismiss = {
                onDismiss()
                dismiss?.invoke()
            },
            onSubmit = { values ->
                onSubmit(
                    values["challenge"].orEmpty().trim(),
                    values["description"].orEmpty().trim(),
                )
            },
        )
    }
}

/**
 * Cut [value] down to something a phone can show, marking the cut with [ellipsis].
 *
 * A public key is a long base64url blob whose interesting ends are the beginning (the algorithm
 * prefix) and the end; the middle is the part a human never reads. The screen this is shown on is
 * not where the key is verified either — the server holds both halves — so the row exists to
 * confirm that a key arrived, and a truncated value does that as well as a whole one while keeping
 * the card from turning into a wall of characters.
 */
private fun truncated(value: String, ellipsis: String, keep: Int = 36): String =
    if (value.length <= keep * 2 + ellipsis.length) {
        value
    } else {
        value.take(keep) + ellipsis + value.takeLast(keep)
    }

