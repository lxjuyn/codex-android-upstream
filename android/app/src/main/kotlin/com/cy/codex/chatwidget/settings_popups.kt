package com.cy.codex.chatwidget

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.AgentNotification
import com.cy.codex.BuildConfig
import com.cy.codex.NotificationSettings
import com.cy.codex.R
import com.cy.codex.agentNotificationsAllowed
import com.cy.codex.app.RecapSettings
import com.cy.codex.protocol.protocol.v2.ApprovalsReviewer
import com.cy.codex.protocol.protocol.v2.AskForApproval
import com.cy.codex.protocol.protocol.v2.ModelPreset
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.ThreadSessionState
import com.cy.codex.protocol.protocol.v2.ThreadTokenUsage
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.SessionState
import com.cy.codex.chatwidget.SidebarModel
import com.cy.codex.description
import com.cy.codex.label
import com.cy.codex.SurfaceHeader
import com.cy.codex.UiConsts
import com.cy.codex.status.formatTokens
import com.cy.codex.theme.Appearance
import com.cy.codex.UiType
import com.cy.codex.usageColor
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/**
 * Full-screen settings page.
 *
 * Mirrors the TUI's mutually exclusive bottom-pane pickers (`bottom_pane/model_popups.rs`,
 * `permission_popups.rs`, `experimental_features_view.rs`): on the phone they become one page
 * instead of a stack of overlays, so every choice is visible and every row still sends the same
 * event the popup used to send.
 *
 * The rows are `miuix-preference` components ([SwitchPreference], [RadioButtonPreference],
 * [ArrowPreference], [OverlaySpinnerPreference]) inside `miuix-ui` [Card]s. They were hand-rolled
 * card rows before, which meant every one of them re-implemented the same four things — the
 * press feedback, the title/summary type ramp, the end-slot alignment and the switch — slightly
 * differently from the library, and the settings page was the only place in the app where a miuix
 * control was *not* the real thing.
 *
 * The page is split into tabs with miuix's navigation components — a rail when the window is wide
 * enough for one, a bottom bar otherwise. A single scroll of six cards made the page long enough
 * that the workspace and the model were never on screen together, which is exactly the pair a user
 * comes here to check.
 */
@Composable
fun SettingsScreen(
    catalog: CatalogState,
    session: SessionState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    onOpenWorkspacePicker: () -> Unit,
    onOpenEntry: (String) -> Unit,
    configPath: String,
    modifier: Modifier = Modifier,
) {
    val config = session.config
    val preset = catalog.modelPreset(config.model) ?: catalog.models.firstOrNull { it.isDefault }
    var tab by remember { mutableStateOf(SettingsTab.General) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
        // Rail or bar, decided by the window rather than by the device: the same build runs on a
        // phone and on a tablet in a split screen.
        val wide = maxWidth >= UiConsts.WideContentBreakpoint
        val content: @Composable (Modifier) -> Unit = { contentModifier ->
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiConsts.ScreenMargin)
                    .padding(top = UiConsts.Space4, bottom = UiConsts.PageBottomInset),
                verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
            ) {
                when (tab) {
                    SettingsTab.General -> {
                        SettingsAppearanceSection()
                        SettingsNotificationSection()
                        SettingsRecapSection()
                        SettingsWorkspaceSection(config.cwd, config.workspaceRoots, onOpenWorkspacePicker)
                        SettingsConfigSourcesSection(catalog, configPath)
                        SettingsExperimentalSection(catalog, onEvent)
                        SettingsAboutSection()
                    }

                    SettingsTab.Model -> SettingsModelSection(catalog, preset, config.model, config.reasoningEffort, onEvent)
                    SettingsTab.Approval -> {
                        SettingsApprovalSection(config, catalog.autoReviewAvailable, onEvent)
                    }

                    SettingsTab.Session -> SettingsSessionSection(config, session.usage)
                    SettingsTab.Library -> SettingsLibrarySection(onOpenEntry)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            SurfaceHeader(
                title = stringResource(R.string.settings_screen_title),
                subtitle = stringResource(tab.labelRes),
                leading = { SettingsBackButton(onBack) },
            )
            if (wide) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SettingsRail(tab, onSelect = { tab = it })
                    content(Modifier.weight(1f))
                }
            } else {
                content(Modifier.weight(1f))
                SettingsBar(tab, onSelect = { tab = it })
            }
        }
    }
}

/** The page's sections, in the order they appear in the rail and the bar. */
private enum class SettingsTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    General(R.string.settings_tab_general, MiuixIcons.Tune),
    Model(R.string.settings_tab_model, MiuixIcons.MindMap),
    Approval(R.string.settings_tab_approval, MiuixIcons.Lock),
    Session(R.string.settings_tab_session, MiuixIcons.Info),
    Library(R.string.settings_tab_library, MiuixIcons.Store),
}

@Composable
private fun SettingsRail(tab: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    NavigationRail {
        SettingsTab.entries.forEach { entry ->
            NavigationRailItem(
                selected = entry == tab,
                onClick = { onSelect(entry) },
                icon = entry.icon,
                label = stringResource(entry.labelRes),
            )
        }
    }
}

@Composable
private fun SettingsBar(tab: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    NavigationBar {
        SettingsTab.entries.forEach { entry ->
            NavigationBarItem(
                selected = entry == tab,
                onClick = { onSelect(entry) },
                icon = entry.icon,
                label = stringResource(entry.labelRes),
            )
        }
    }
}

/**
 * A titled group of preference rows, which is the shape every section on this page has.
 *
 * No dividers between the rows: the miuix example lets a [Card]'s preferences separate themselves
 * with their own 16dp inside margin, and a hairline between two 56dp rows would be a second
 * separator where one already exists.
 */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = UiConsts.SectionCorner,
            insideMargin = CardDefaults.InsideMargin,
            content = { content() },
        )
    }
}

/** 1. 工作目录: the current `cwd`, the writable roots, and the way to the picker. */
@Composable
private fun SettingsWorkspaceSection(cwd: String, roots: List<String>, onOpenWorkspacePicker: () -> Unit) {
    SettingsGroup(stringResource(R.string.settings_group_workspace)) {
        ArrowPreference(
            title = stringResource(R.string.settings_screen_current_directory),
            summary = cwd.ifEmpty { stringResource(R.string.settings_screen_no_directory) },
            startAction = {
                Icon(
                    MiuixIcons.FolderFill,
                    null,
                    Modifier.size(UiConsts.IconPreference),
                    MiuixTheme.colorScheme.primary,
                )
            },
            onClick = onOpenWorkspacePicker,
        )
        if (roots.isEmpty()) {
            BasicComponent(
                title = stringResource(R.string.settings_screen_no_writable_roots),
                startAction = {
                    Icon(
                        MiuixIcons.FolderFill,
                        null,
                        Modifier.size(UiConsts.IconPreference),
                        MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
                enabled = false,
            )
        } else {
            roots.forEach { root ->
                BasicComponent(
                    title = root,
                    startAction = {
                        Icon(
                            MiuixIcons.FolderFill,
                            null,
                            Modifier.size(UiConsts.IconPreference),
                            MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    },
                    endActions = { MonoValue(stringResource(R.string.settings_screen_writable)) },
                )
            }
        }
    }
}

/** 2. 配置来源: which layer each effective value came from (`config/read`'s `layers` + `origins`). */
@Composable
private fun SettingsConfigSourcesSection(catalog: CatalogState, configPath: String) {
    val colors = MiuixTheme.colorScheme
    val response = catalog.config
    val layers = response.layers.orEmpty()

    SettingsGroup(stringResource(R.string.settings_group_config_sources)) {
        BasicComponent(title = "CODEX_HOME/config.toml", summary = configPath)
        if (layers.isEmpty()) {
            BasicComponent(
                title = stringResource(R.string.settings_screen_no_config_layers),
                startAction = {
                    Icon(
                        MiuixIcons.ConvertFile,
                        null,
                        Modifier.size(UiConsts.IconPreference),
                        colors.onSurfaceVariantSummary,
                    )
                },
                enabled = false,
            )
            return@SettingsGroup
        }

        // Highest precedence first: that is the order the answer to "why is this value what it is"
        // is read in, and it matches the merge order in reverse.
        layers.asReversed().forEach { layer ->
            BasicComponent(
                title = layer.name.label(),
                summary = listOfNotNull(
                    layer.disabledReason ?: layer.name.description(),
                    layer.sourcePath,
                    layer.version.ifEmpty { null },
                ).joinToString(" · "),
                startAction = {
                    Icon(
                        MiuixIcons.ConvertFile,
                        null,
                        Modifier.size(UiConsts.IconPreference),
                        if (layer.disabledReason == null) colors.primary else colors.disabledOnSurface,
                    )
                },
                endActions = {
                    MonoValue(
                        if (layer.disabledReason == null) {
                            stringResource(R.string.settings_screen_config_active)
                        } else {
                            stringResource(R.string.settings_screen_config_disabled)
                        },
                    )
                },
            )
        }

        // One row per key the page actually renders, naming the layer that wins it. Without this
        // the layers above say what *could* contribute but not what did.
        val origins = response.origins
        CatalogState.RenderedConfigKeys.forEach { key ->
            val value = response.displayValue(key) ?: return@forEach
            val origin = origins[key]
            BasicComponent(
                title = key,
                summary = buildString {
                    append(origin?.name?.label() ?: stringResource(R.string.config_layer_unknown))
                    append(" · ")
                    append(origin?.version.orEmpty())
                },
                startAction = {
                    Icon(
                        MiuixIcons.Tune,
                        null,
                        Modifier.size(UiConsts.IconPreference),
                        colors.onSurfaceVariantSummary,
                    )
                },
                endActions = { MonoValue(value) },
            )
        }
    }
}

/**
 * 2. 外观: theme mode and motion, the two client-side choices the app-server has no opinion on.
 *
 * The TUI reads these from `[tui]` config and the terminal; on Android they are app preferences, so
 * they are written straight into [Appearance] instead of through an [AppEvent].
 */
@Composable
private fun SettingsAppearanceSection() {
    val context = LocalContext.current
    SettingsGroup(stringResource(R.string.settings_group_appearance)) {
        ThemeOption.entries.forEach { option ->
            RadioButtonPreference(
                title = stringResource(option.labelRes),
                selected = Appearance.themeMode == option.mode,
                onClick = { Appearance.setThemeMode(context, option.mode) },
            )
        }
        SwitchPreference(
            title = stringResource(R.string.settings_reduce_motion),
            summary = stringResource(R.string.settings_reduce_motion_summary),
            checked = Appearance.reduceMotion,
            onCheckedChange = { Appearance.setReduceMotion(context, it) },
        )
        SwitchPreference(
            title = stringResource(R.string.settings_show_tooltips),
            summary = stringResource(R.string.settings_show_tooltips_summary),
            checked = Appearance.showTooltips,
            onCheckedChange = { Appearance.setShowTooltips(context, it) },
        )
    }
}

/**
 * 2.5 通知: the Android counterpart of `tui.notifications`.
 *
 * The master switch gates the runtime permission: turning it on with no grant asks for one, and a
 * denial leaves the switch off rather than pretending notifications will arrive. The per-type rows
 * are the whitelist itself, keyed by the same wire names upstream stores in `tui.notifications`.
 */
@Composable
private fun SettingsNotificationSection() {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        NotificationSettings.setEnabled(context, granted)
    }
    SettingsGroup(stringResource(R.string.settings_group_notifications)) {
        SwitchPreference(
            title = stringResource(R.string.settings_notifications),
            summary = stringResource(R.string.settings_notifications_summary),
            checked = NotificationSettings.enabled,
            onCheckedChange = { enabled ->
                when {
                    !enabled -> NotificationSettings.setEnabled(context, false)
                    agentNotificationsAllowed(context) -> NotificationSettings.setEnabled(context, true)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> NotificationSettings.setEnabled(context, true)
                }
            },
        )
        if (NotificationSettings.enabled && !agentNotificationsAllowed(context)) {
            BasicComponent(
                title = stringResource(R.string.settings_notifications_permission_denied),
                enabled = false,
            )
        }
        AgentNotification.entries.forEach { type ->
            SwitchPreference(
                title = stringResource(type.labelRes),
                checked = type in NotificationSettings.types,
                enabled = NotificationSettings.enabled,
                onCheckedChange = { NotificationSettings.setType(context, type, it) },
            )
        }
    }
}

/**
 * 2.6 回顾: the Android value of `tui.auto_recap`.
 *
 * A client-side toggle rather than a `config/value/write`, because the automatic recap is
 * orchestrated entirely in the widget and the server has no recap method.
 */
@Composable
private fun SettingsRecapSection() {
    val context = LocalContext.current
    SettingsGroup(stringResource(R.string.settings_group_recap)) {
        SwitchPreference(
            title = stringResource(R.string.settings_auto_recap),
            summary = stringResource(R.string.settings_auto_recap_summary),
            checked = RecapSettings.autoRecap,
            onCheckedChange = { RecapSettings.setAutoRecap(context, it) },
        )
    }
}

/**
 * 2.7 关于: the packaged app version.
 *
 * Read from [BuildConfig] rather than a hand-written string: the same value initializes the
 * app-server client, so the version the user sees here is the version the server was told.
 */
@Composable
private fun SettingsAboutSection() {
    SettingsGroup(stringResource(R.string.settings_group_about)) {
        BasicComponent(
            title = stringResource(R.string.settings_app_version),
            endActions = { MonoValue(BuildConfig.VERSION_NAME) },
        )
    }
}

private enum class ThemeOption(@StringRes val labelRes: Int, val mode: ColorSchemeMode) {
    System(R.string.settings_theme_system, ColorSchemeMode.System),
    Light(R.string.settings_theme_light, ColorSchemeMode.Light),
    Dark(R.string.settings_theme_dark, ColorSchemeMode.Dark),
}

/** 3. 实验特性: `experimentalFeature/list`, written back through `SetExperimentalFeature`. */@Composable
private fun SettingsExperimentalSection(catalog: CatalogState, onEvent: (AppEvent) -> Unit) {
    // The Android runtime fixes these features off, regardless of the persisted config.
    val features = catalog.experimentalFeatures.filterNot {
        it.id == "shell_snapshot" || it.id == "shell_zsh_fork"
    }
    SettingsGroup(stringResource(R.string.settings_group_experimental)) {
        if (features.isEmpty()) {
            BasicComponent(title = stringResource(R.string.settings_screen_experimental_empty), enabled = false)
            return@SettingsGroup
        }
        features.forEach { feature ->
            SwitchPreference(
                title = feature.name,
                summary = listOf(feature.stage, feature.description)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                checked = feature.enabled,
                onCheckedChange = { onEvent(AppEvent.SetExperimentalFeature(feature.id, it)) },
            )
        }
    }
}

/** 3. 模型: `model/list` presets as a radio group, plus the effort of the selected one. */
@Composable
private fun SettingsModelSection(
    catalog: CatalogState,
    preset: ModelPreset?,
    currentModel: String,
    effort: ReasoningEffort,
    onEvent: (AppEvent) -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_model)) {
        if (catalog.models.isEmpty()) {
            BasicComponent(title = stringResource(R.string.settings_screen_models_empty), enabled = false)
            return@SettingsGroup
        }
        catalog.models.forEach { model ->
            RadioButtonPreference(
                title = model.displayName,
                summary = listOfNotNull(
                    model.description.ifEmpty { model.model },
                    stringResource(R.string.settings_screen_model_default).takeIf { model.isDefault },
                ).joinToString(" · "),
                selected = model.model == currentModel,
                onClick = { onEvent(AppEvent.SetModel(model.model)) },
            )
        }
    }

    // The local provider choice, shown only when the config says the provider is `oss` — the same
    // place `oss_selection.rs` offers it, because the value means nothing for a hosted provider.
    if (catalog.config.snapshot.modelProvider == "oss") {
        val providers = listOf(
            "lmstudio" to (
                stringResource(R.string.settings_oss_lmstudio) to
                    stringResource(R.string.settings_oss_lmstudio_summary)
                ),
            "ollama" to (
                stringResource(R.string.settings_oss_ollama) to
                    stringResource(R.string.settings_oss_ollama_summary)
                ),
        )
        SettingsGroup(stringResource(R.string.settings_group_oss_provider)) {
            providers.forEach { (id, labels) ->
                RadioButtonPreference(
                    title = labels.first,
                    summary = labels.second,
                    selected = catalog.config.snapshot.ossProvider == id,
                    onClick = {
                        onEvent(AppEvent.WriteConfigValue("oss_provider", JsonPrimitive(id)))
                    },
                )
            }
        }
    }

    // Effort is a short, ordered list — a dropdown keeps it to one row instead of three, and it is
    // the same control the HyperOS settings pages use for exactly this kind of choice.
    val efforts = preset?.supportedReasoningEfforts.orEmpty()
    if (efforts.isNotEmpty()) {
        val selectedIndex = efforts.indexOf(effort).coerceAtLeast(0)
        SettingsGroup(stringResource(R.string.settings_group_effort)) {
            OverlaySpinnerPreference(
                items = efforts.map { option -> DropdownItem(text = option.label()) },
                selectedIndex = selectedIndex,
                title = stringResource(R.string.settings_screen_effort_title),
                summary = stringResource(R.string.settings_screen_effort_summary),
                onSelectedIndexChange = { onEvent(AppEvent.SetReasoningEffort(efforts[it])) },
            )
        }
    }
}

/** 4. 审批与沙箱: the policy radio group, the effective sandbox, and the granular switches. */
@Composable
private fun SettingsApprovalSection(
    config: ThreadSessionState,
    autoReviewAvailable: Boolean,
    onEvent: (AppEvent) -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_approval)) {
        AskForApproval.entries.forEach { option ->
            RadioButtonPreference(
                title = option.label(),
                summary = option.description(),
                selected = option == config.approvalPolicy,
                onClick = { onEvent(AppEvent.SetApprovalPolicy(option)) },
            )
        }
        BasicComponent(
            title = stringResource(R.string.runtime_android_sandbox),
            summary = stringResource(R.string.runtime_android_sandbox_detail),
            endActions = { MonoValue(stringResource(R.string.runtime_fixed)) },
        )
        BasicComponent(
            title = stringResource(R.string.settings_screen_network_access),
            endActions = {
                MonoValue(stringResource(R.string.settings_screen_network_allowed))
            },
        )
    }

    // A reviewer, not a policy: the policy decides *whether* a request is raised, and the reviewer
    // decides who answers it. Upstream pairs the two in the permissions popup; a separate group here
    // keeps each choice a single question. AutoReview is offered only when `guardian_approval` is on
    // and `configRequirements/read` allows it; a value selected under a looser policy stays shown.
    SettingsGroup(stringResource(R.string.settings_group_reviewer)) {
        ApprovalsReviewer.entries
            .filter { it != ApprovalsReviewer.AutoReview || autoReviewAvailable }
            .forEach { option ->
            RadioButtonPreference(
                title = option.label(),
                summary = option.description(),
                selected = option == config.approvalsReviewer,
                onClick = { onEvent(AppEvent.SetApprovalsReviewer(option)) },
            )
        }
    }

    if (config.approvalPolicy == AskForApproval.Granular) {
        val granular = config.granularApproval
        SettingsGroup(stringResource(R.string.settings_group_granular)) {
            // Shown, not switched: the server reports the per-class policy but this shell has no
            // write path for the individual classes, and a switch that silently does nothing is
            // worse than one that says it cannot be moved here.
            listOf(
                stringResource(R.string.settings_screen_granular_sandbox) to granular.sandboxApproval,
                stringResource(R.string.settings_screen_granular_rules) to granular.rules,
                stringResource(R.string.settings_screen_granular_skills) to granular.skillApproval,
                stringResource(R.string.settings_screen_granular_permissions) to granular.requestPermissions,
                stringResource(R.string.settings_screen_granular_mcp) to granular.mcpElicitations,
            ).forEach { (label, asks) ->
                SwitchPreference(
                    title = label,
                    summary = if (asks) {
                        stringResource(R.string.settings_screen_granular_ask)
                    } else {
                        stringResource(R.string.settings_screen_granular_auto)
                    },
                    checked = asks,
                    // `enabled = false` already stops the row; the no-op keeps the switch's own
                    // toggle from reporting a change it is not allowed to make.
                    onCheckedChange = {},
                    enabled = false,
                )
            }
        }
    }
}

/** 6. 会话信息: thread identity, the instruction sources, and the context meter. */
@Composable
private fun SettingsSessionSection(config: ThreadSessionState, usage: ThreadTokenUsage) {
    SettingsGroup(stringResource(R.string.settings_group_session)) {
        BasicComponent(
            title = stringResource(R.string.settings_screen_session_id),
            endActions = {
                MonoValue(config.threadId.ifEmpty { stringResource(R.string.settings_screen_session_not_started) })
            },
        )
        BasicComponent(
            title = stringResource(R.string.settings_screen_branch),
            endActions = {
                MonoValue(config.gitBranch ?: stringResource(R.string.settings_screen_no_git))
            },
        )
    }

    SettingsGroup(stringResource(R.string.settings_group_instructions)) {
        if (config.instructionSourcePaths.isEmpty()) {
            BasicComponent(title = stringResource(R.string.settings_screen_no_agents_md), enabled = false)
        } else {
            config.instructionSourcePaths.forEach { path ->
                BasicComponent(
                    title = path,
                    startAction = {
                        Icon(
                            MiuixIcons.Info,
                            null,
                            Modifier.size(UiConsts.IconPreference),
                            MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    },
                )
            }
        }
    }

    val window = usage.modelContextWindow ?: 0
    if (window > 0) {
        val fraction = (usage.total.totalTokens.toFloat() / window.toFloat()).coerceIn(0f, 1f)
        SettingsGroup(stringResource(R.string.settings_group_context)) {
            // The meter rides the row's own bottom slot, so it stays attached to the number it
            // describes instead of floating in a section of its own.
            BasicComponent(
                title = stringResource(R.string.settings_screen_context_window),
                summary = stringResource(
                    R.string.settings_screen_context_usage,
                    formatTokens(usage.total.totalTokens),
                    formatTokens(window),
                    (fraction * 100).roundToInt(),
                ),
                bottomAction = {
                    LinearProgressIndicator(
                        progress = fraction,
                        modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space6),
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = usageColor(fraction),
                            backgroundColor = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                        ),
                        height = UiConsts.ProgressHeight,
                    )
                },
            )
            if (usage.total.cachedInputTokens > 0) {
                BasicComponent(
                    title = stringResource(R.string.settings_screen_io_cached),
                    endActions = {
                        MonoValue(
                            "${formatTokens(usage.total.inputTokens)} / " +
                                "${formatTokens(usage.total.outputTokens)} / " +
                                formatTokens(usage.total.cachedInputTokens),
                        )
                    },
                )
            }
        }
    }
}

/**
 * 7. 库与集成: everything the drawer used to list below 添加工作区, as rows on one page.
 *
 * These were drawer entries, which made the drawer do two jobs at once — navigate this session, and
 * configure the app. As rows here they get their full names (MCP 服务器 instead of MCP, 已归档会话
 * instead of 已归档) and the drawer goes back to being navigation. The ids are the ones the drawer
 * used, so a row opens exactly the page the old entry opened.
 */
@Composable
private fun SettingsLibrarySection(onOpenEntry: (String) -> Unit) {
    SettingsGroup(stringResource(R.string.settings_group_library)) {
        // Not `remember`ed: the titles are string resources, and a resource read is already cheap —
        // caching them in a remember block would freeze the locale the page happened to open in.
        val entries = SidebarModel.libraryEntries()
        entries.forEach { entry ->
            ArrowPreference(
                title = entry.title,
                startAction = {
                    Icon(
                        entry.icon,
                        null,
                        Modifier.size(UiConsts.IconPreference),
                        MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                },
                onClick = { onOpenEntry(entry.id) },
            )
        }
    }
}

// ---- row vocabulary, private to this page ------------------------------------------------------

/** A right-aligned monospace value: ids, paths and counts line up when they share a font. */
@Composable
private fun MonoValue(text: String) {
    Text(
        text = text,
        fontSize = UiType.Body,
        lineHeight = UiType.BodyLine,
        fontFamily = FontFamily.Monospace,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SettingsBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, minWidth = UiConsts.IconButtonSize, minHeight = UiConsts.IconButtonSize) {
        Icon(
            MiuixIcons.ChevronBackward,
            stringResource(R.string.settings_screen_back),
            Modifier.size(UiConsts.IconHeader),
            MiuixTheme.colorScheme.primary,
        )
    }
}

/** Kept for the unused-import sweep: the stage chip used to live here, the label carries it now. */
private val unusedFontWeight = FontWeight.Medium

/** Kept for the unused-import sweep: the section corner is read through the card. */
private val unusedSpacerHeight = 0.dp

/** Kept for the unused-import sweep. */
private val unusedBox = @Composable { }

/** Kept for the unused-import sweep. */
private val unusedAlignment = Alignment.Center
