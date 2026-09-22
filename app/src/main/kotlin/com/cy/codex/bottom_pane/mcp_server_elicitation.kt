package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ElicitationAction
import com.cy.codex.protocol.protocol.v2.McpElicitationField
import com.cy.codex.protocol.protocol.v2.McpElicitationFieldKind
import com.cy.codex.protocol.protocol.v2.McpElicitationRequest
import com.cy.codex.label
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.pressableRow
import com.cy.codex.raisedSurface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `mcpServer/elicitation/request` in its form mode: an MCP server asks the user to fill in a schema.
 *
 * Mirrors `codex-rs/tui/src/bottom_pane/mcp_server_elicitation.rs`: the flattened
 * [McpElicitationField] list is rendered as a real form, required fields gate the submit button,
 * and the submitted map is the *whole* form rather than only the fields the user touched.
 *
 * Every field is a label (with the required marker beside it), an optional description, and the
 * control. The switch and the enum chips both sit on the same [raisedSurface] the option rows of
 * the other dialog use, so the two forms read as one component.
 */

/** Enum options are chips: fully rounded, and the only control here that is not a full-width row. */
private val ChipShape = RoundedCornerShape(percent = UiConsts.PillCorner)

/** The switch row a boolean field renders as. */
private val BooleanRowShape = RoundedCornerShape(UiConsts.CornerControl)

/** One field's own rhythm. */
private val RequiredBadgeShape = RoundedCornerShape(UiConsts.CornerChip)
private val FieldControlGap = UiConsts.Space8

/** The URL a redirect-mode elicitation points at; a code surface, like every other payload. */
private val UrlShape = RoundedCornerShape(UiConsts.CornerControl)

@Composable
internal fun McpElicitationForm(
    request: ApprovalRequest.Elicitation,
    onSubmit: (Map<String, String>) -> Unit,
    onDecline: () -> Unit,
    busy: Boolean = false,
) {
    // The two wire modes are different interactions: a schema form to fill in, or a page to open
    // and accept. Rendering the URL variant as an empty form lost the URL entirely, which is why it
    // gets its own body.
    when (val payload = request.params) {
        is McpElicitationRequest.Url -> McpElicitationUrl(
            payload = payload,
            onAccept = { onSubmit(emptyMap()) },
            onDecline = onDecline,
            busy = busy,
        )

        is McpElicitationRequest.Form -> McpElicitationFields(
            payload = payload,
            onSubmit = onSubmit,
            onDecline = onDecline,
            busy = busy,
        )
    }
}

@Composable
private fun McpElicitationFields(
    payload: McpElicitationRequest.Form,
    onSubmit: (Map<String, String>) -> Unit,
    onDecline: () -> Unit,
    busy: Boolean,
) {
    val params = payload.requestedSchema
    val fields = params.fields
    // fieldName -> current raw value; seeded from the schema's `value`.
    val values = remember(fields) {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { put(it.name, it.value) }
        }
    }
    var submitted by remember(fields) { mutableStateOf(false) }

    val missing = fields.count { it.required && values[it.name].orEmpty().isBlank() }
    val complete = missing == 0

    Column(modifier = Modifier.fillMaxWidth()) {
        ApprovalScrollBody {
            if (payload.message.isNotBlank()) {
                Text(
                    text = payload.message,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
            }
            fields.forEachIndexed { index, field ->
                if (index > 0) Spacer(Modifier.height(UiConsts.DialogFieldGap))
                ElicitationFieldRow(
                    field = field,
                    value = values[field.name].orEmpty(),
                    onValueChange = { values[field.name] = it },
                )
            }
            if (fields.isEmpty()) {
                Text(
                    text = stringResource(R.string.mcp_server_elicitation_empty_form),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        // The scrolling body stops here and the footer starts: without the gap the last question
        // reads as if it ran into the buttons, which are a different thing entirely.
        Spacer(Modifier.height(UiConsts.DialogFooterGap))
        FormButtons(
            // The submit button *is* the protocol's accept action, so it takes that label rather
            // than keeping a second copy of the same word.
            confirmLabel = ElicitationAction.Accept.label(),
            enabled = complete,
            busy = submitted || busy,
            onConfirm = {
                submitted = true
                onSubmit(fields.associate { it.name to values[it.name].orEmpty().trim() })
            },
            onCancel = onDecline,
        )
    }
}

/**
 * URL-mode elicitation: the connector sign-in or browser-action flow, in two screens.
 *
 * Mirrors `bottom_pane/app_link_view.rs`: the first screen explains what is about to happen and
 * opens the URL; the second asks the user to come back and confirm. Both the accept and the URL
 * validation happen here, and an unrecognized or unsafe URL shows no open button at all.
 */
@Composable
private fun McpElicitationUrl(
    payload: McpElicitationRequest.Url,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    busy: Boolean,
) {
    val colors = MiuixTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val prompt = remember(payload) { appLinkPrompt(payload) }
    var screen by remember(payload) { mutableStateOf(AppLinkScreen.Link) }
    val auth = prompt?.kind == AppLinkKind.Auth
    val link = prompt?.url ?: payload.url

    Column(modifier = Modifier.fillMaxWidth()) {
        ApprovalScrollBody {
            if (screen == AppLinkScreen.Confirmation && prompt != null) {
                Text(
                    text = stringResource(
                        if (auth) R.string.app_link_finish_auth_title else R.string.app_link_finish_browser_title,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.DialogTitle,
                    lineHeight = UiType.DialogTitleLine,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
                Text(
                    text = stringResource(
                        if (auth) R.string.app_link_finish_auth_body else R.string.app_link_finish_browser_body,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceSecondary,
                )
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
                UrlSurface(link)
            } else {
                Text(
                    text = when {
                        prompt == null -> stringResource(R.string.mcp_server_elicitation_url_message)
                        auth -> prompt.connectorName ?: prompt.connectorId.orEmpty()
                        else -> stringResource(R.string.app_link_external_title)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.DialogTitle,
                    lineHeight = UiType.DialogTitleLine,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                if (prompt != null && !auth) {
                    Spacer(Modifier.height(UiConsts.Space4))
                    Text(
                        text = stringResource(R.string.app_link_external_description, prompt.serverName),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
                if (prompt?.message?.isNotBlank() == true) {
                    Spacer(Modifier.height(UiConsts.DialogFieldGap))
                    Text(
                        text = prompt.message,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = UiType.Body,
                        lineHeight = UiType.BodyLine,
                        color = colors.onSurfaceSecondary,
                    )
                }
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
                Text(
                    text = stringResource(
                        if (auth) R.string.app_link_auth_instructions else R.string.app_link_external_instructions,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceSecondary,
                )
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
                UrlSurface(link)
            }
        }
        Spacer(Modifier.height(UiConsts.DialogFooterGap))
        FormButtons(
            confirmLabel = when {
                prompt == null -> stringResource(R.string.mcp_server_elicitation_url_open)
                screen == AppLinkScreen.Link && auth -> stringResource(R.string.app_link_open_sign_in)
                screen == AppLinkScreen.Link -> stringResource(R.string.app_link_open_link)
                auth -> stringResource(R.string.app_link_signed_in)
                else -> stringResource(R.string.app_link_finished)
            },
            // A URL that failed validation has no open button: declining is the only way out, the
            // same outcome the TUI reaches by rejecting the conversion.
            enabled = prompt != null,
            busy = busy,
            onConfirm = {
                val target = prompt
                if (target != null) {
                    when (screen) {
                        AppLinkScreen.Link -> {
                            runCatching { uriHandler.openUri(target.url) }
                            screen = AppLinkScreen.Confirmation
                        }

                        AppLinkScreen.Confirmation -> onAccept()
                    }
                }
            },
            onCancel = onDecline,
        )
    }
}

/** The redirect target as a code surface, the way every other payload is shown. */
@Composable
private fun UrlSurface(url: String) {
    Text(
        text = url,
        modifier = Modifier
            .fillMaxWidth()
            .background(codeSurface(), UrlShape)
            .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space10),
        fontSize = UiType.Code,
        lineHeight = UiType.CodeLine,
        fontFamily = FontFamily.Monospace,
        color = MiuixTheme.colorScheme.primary,
    )
}

@Composable
private fun ElicitationFieldRow(
    field: McpElicitationField,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = field.title.ifBlank { field.name },
                modifier = Modifier.weight(1f),
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            if (field.required) {
                Spacer(Modifier.width(UiConsts.Space8))
                Text(
                    text = stringResource(R.string.mcp_server_elicitation_required),
                    modifier = Modifier
                        .background(colors.error.copy(alpha = 0.12f), RequiredBadgeShape)
                        .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space2),
                    fontSize = UiType.Badge,
                    lineHeight = UiType.BadgeLine,
                    fontWeight = FontWeight.Medium,
                    color = colors.error,
                )
            }
        }
        if (field.description.isNotBlank()) {
            Spacer(Modifier.height(UiConsts.Space2))
            Text(
                text = field.description,
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(FieldControlGap))
        when (field.kind) {
            McpElicitationFieldKind.Text -> TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = field.title.ifBlank { field.name },
                singleLine = true,
            )

            McpElicitationFieldKind.Multiline -> TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = field.title.ifBlank { field.name },
                singleLine = false,
                minLines = 3,
                maxLines = 6,
            )

            McpElicitationFieldKind.Number -> TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = field.title.ifBlank { field.name },
                singleLine = true,
                textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace),
            )

            McpElicitationFieldKind.Boolean -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressableRow(
                            shape = BooleanRowShape,
                            container = raisedSurface(),
                            onClick = { onValueChange(if (isTrue(value)) "false" else "true") },
                        )
                        .padding(
                            horizontal = UiConsts.Space16,
                            vertical = UiConsts.Space8,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isTrue(value)) {
                            stringResource(R.string.mcp_server_elicitation_boolean_on)
                        } else {
                            stringResource(R.string.mcp_server_elicitation_boolean_off)
                        },
                        modifier = Modifier.weight(1f),
                        fontSize = UiType.Body,
                        lineHeight = UiType.BodyLine,
                        color = colors.onSurface,
                    )
                    Switch(
                        checked = isTrue(value),
                        onCheckedChange = { onValueChange(if (it) "true" else "false") },
                    )
                }
            }

            McpElicitationFieldKind.Enum -> EnumChips(
                options = field.options,
                selected = value,
                onSelect = onValueChange,
            )
        }
    }
}

/** Option chips for an enum field; an unchosen enum reads as unanswered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnumChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    if (options.isEmpty()) {
        Text(
            text = stringResource(R.string.mcp_server_elicitation_enum_unavailable),
            fontSize = UiType.Meta,
            lineHeight = UiType.MetaLine,
            color = colors.onSurfaceVariantSummary,
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8),
        verticalArrangement = Arrangement.spacedBy(UiConsts.Space8),
    ) {
        options.forEach { option ->
            val chosen = option == selected
            Row(
                modifier = Modifier
                    .background(
                        if (chosen) colors.primary.copy(alpha = 0.14f) else raisedSurface(),
                        ChipShape,
                    )
                    .border(
                        width = UiConsts.OutlineThickness,
                        color = if (chosen) colors.primary else colors.outline.copy(alpha = 0.3f),
                        shape = ChipShape,
                    )
                    .clickable { onSelect(option) }
                    .padding(
                        horizontal = UiConsts.Space16,
                        vertical = UiConsts.Space8,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chosen) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = UiConsts.Space5)
                            .size(UiConsts.IconCheck),
                        tint = colors.primary,
                    )
                }
                Text(
                    text = option,
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal,
                    color = if (chosen) colors.primary else colors.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/** `Boolean` elicitation fields arrive as strings; anything truthy opens the switch. */
private fun isTrue(value: String): Boolean =
    value.trim().lowercase() in setOf("true", "1", "yes", "on")
