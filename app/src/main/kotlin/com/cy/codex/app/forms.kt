package com.cy.codex.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.cy.codex.CodexButton
import com.cy.codex.ButtonRole
import com.cy.codex.CodexTextField
import com.cy.codex.ModalSheet
import com.cy.codex.R
import com.cy.codex.UiConsts
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * One field of a [FormSheet].
 *
 * A list of these rather than a composable slot per page: the sheets in this app are two fields and
 * a button, and the four of them had already drifted on where the submit button sits and what a
 * missing required field does. Describing the fields as data is what makes one sheet do all four.
 */
data class FormField(
    /** Key the value is reported under; also the field's identity across recompositions. */
    val key: String,
    val label: String,
    val placeholder: String? = null,
    val initial: String = "",
    /** A blank required field disables the submit button rather than being sent empty. */
    val required: Boolean = true,
    val keyboardType: KeyboardType = KeyboardType.Text,
    /** Shown under the field; use it to say what the value is for. */
    val help: String? = null,
    /**
     * Fixed choices as wire value to label. When present the field renders as a radio group and the
     * reported value is the chosen wire value, so a form that must send an enum cannot send prose.
     */
    val choices: List<Pair<String, String>>? = null,
    /** Render the input as bullets; for secrets that are about to be sent to the server. */
    val masked: Boolean = false,
)

/**
 * A sheet that collects a few values and submits them together.
 *
 * `config/batchWrite` is the reason this shape exists: several of these forms write more than one
 * key, and sending them one at a time would leave the config in a half-applied state if the second
 * write were refused.
 *
 * Dismissal is the caller's: the sheet is composed only while it should be visible, matching every
 * other sheet in the app, so `onDismissFinished` and `onDismiss` are the same callback.
 */
@Composable
fun FormSheet(
    title: String,
    fields: List<FormField>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    val values = remember(fields) { mutableStateMapOf<String, String>().apply {
        fields.forEach { put(it.key, it.initial) }
    } }
    var touched by remember(fields) { mutableStateOf(false) }
    val complete = fields.all { !it.required || values[it.key].orEmpty().isNotBlank() }

    ModalSheet(
        show = true,
        onDismiss = onDismiss,
        onDismissFinished = onDismiss,
        title = title,
        subtitle = subtitle,
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space12),
        ) {
            fields.forEach { field ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (field.choices != null) {
                        Text(
                            text = field.label,
                            fontSize = com.cy.codex.UiType.Meta,
                            lineHeight = com.cy.codex.UiType.MetaLine,
                            color = colors.onSurfaceVariantSummary,
                        )
                        field.choices.forEach { (value, label) ->
                            RadioButtonPreference(
                                title = label,
                                selected = values[field.key] == value,
                                onClick = { values[field.key] = value },
                            )
                        }
                    } else {
                        CodexTextField(
                            value = values[field.key].orEmpty(),
                            onValueChange = { values[field.key] = it },
                            label = field.label,
                            placeholder = field.placeholder,
                            keyboardOptions = KeyboardOptions(keyboardType = field.keyboardType),
                            visualTransformation = if (field.masked) {
                                androidx.compose.ui.text.input.PasswordVisualTransformation()
                            } else {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            },
                        )
                    }
                    val note = field.help ?: if (touched && field.required && values[field.key].isNullOrBlank()) {
                        stringResource(R.string.form_field_required)
                    } else {
                        null
                    }
                    if (note != null) {
                        Spacer(Modifier.height(UiConsts.Space4))
                        Text(
                            text = note,
                            fontSize = com.cy.codex.UiType.Meta,
                            lineHeight = com.cy.codex.UiType.MetaLine,
                            color = if (touched && values[field.key].isNullOrBlank()) {
                                colors.error
                            } else {
                                colors.onSurfaceVariantSummary
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(UiConsts.Space4))
            CodexButton(
                text = confirmLabel,
                onClick = {
                    touched = true
                    if (complete) onSubmit(values.toMap())
                },
                modifier = Modifier.fillMaxWidth(),
                role = if (destructive) ButtonRole.Destructive else ButtonRole.Primary,
                enabled = complete,
            )
        }
    }
}

/** The project editor: a name, and the directory it stands for. */
@Composable
fun ProjectFormSheet(
    title: String,
    initial: com.cy.codex.protocol.protocol.v2.ProjectEntry?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, path: String) -> Unit,
) {
    FormSheet(
        title = title,
        fields = listOf(
            FormField(
                key = "name",
                label = stringResource(R.string.projects_form_name),
                placeholder = stringResource(R.string.projects_form_name_placeholder),
                initial = initial?.name.orEmpty(),
            ),
            FormField(
                key = "path",
                label = stringResource(R.string.projects_form_path),
                placeholder = stringResource(R.string.projects_form_path_placeholder),
                initial = initial?.path.orEmpty(),
                help = stringResource(R.string.projects_form_path_help),
            ),
        ),
        confirmLabel = stringResource(R.string.projects_form_save),
        onDismiss = onDismiss,
        onSubmit = { onSubmit(it["name"].orEmpty().trim(), it["path"].orEmpty().trim()) },
    )
}

/** A sheet whose single value is a path — importing a project, opening a file. */
@Composable
fun PathSheet(
    title: String,
    label: String,
    confirm: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    initial: String = "",
    help: String? = null,
) {
    FormSheet(
        title = title,
        fields = listOf(
            FormField(
                key = "path",
                label = label,
                initial = initial,
                help = help,
            ),
        ),
        confirmLabel = confirm,
        onDismiss = onDismiss,
        onSubmit = { onSubmit(it["path"].orEmpty().trim()) },
        modifier = modifier,
    )
}

/**
 * Registering an execution environment.
 *
 * Two fields that look unrelated and are not: an environment is a *remote* exec server, so it is
 * named by an id the caller chooses and reached by its url. There is no "add this folder" form in
 * the protocol — a local directory is a project, not an environment.
 */
@Composable
fun EnvironmentFormSheet(
    onDismiss: () -> Unit,
    onSubmit: (id: String, url: String) -> Unit,
) {
    FormSheet(
        title = stringResource(R.string.projects_screen_add_environment),
        subtitle = stringResource(R.string.projects_screen_add_environment_detail),
        fields = listOf(
            FormField(
                key = "id",
                label = stringResource(R.string.environment_form_id),
                placeholder = stringResource(R.string.environment_form_id_placeholder),
            ),
            FormField(
                key = "url",
                label = stringResource(R.string.environment_form_url),
                placeholder = stringResource(R.string.environment_form_url_placeholder),
                help = stringResource(R.string.environment_form_url_help),
                keyboardType = KeyboardType.Uri,
            ),
        ),
        confirmLabel = stringResource(R.string.environment_form_add),
        onDismiss = onDismiss,
        onSubmit = { onSubmit(it["id"].orEmpty().trim(), it["url"].orEmpty().trim()) },
    )
}
