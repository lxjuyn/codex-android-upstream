package com.cy.codex.chatwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.PluginInstallAuthFlow
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.label
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.v2.PluginDetail
import com.cy.codex.protocol.protocol.v2.PluginEntry
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * `/plugins` output as a page.
 *
 * Mirrors `plugin/list` and `bottom_pane/plugin_catalog`: entries are split the way the catalog
 * presents them — what is already installed, and what the marketplace still offers. Every row's
 * action round-trips: `plugin/install` and `plugin/uninstall` are emitted as [AppEvent]s, and the
 * catalog is re-read from the server afterwards rather than patched locally.
 */
@Composable
fun PluginsScreen(
    catalog: CatalogState,
    client: AppServerClient,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    // A search narrows the list with the *server's* index rather than filtering the copy on screen:
    // `plugin/list` returns at most one marketplace's worth of entries, so a local filter would
    // silently fail to find a plugin that is installed but not in the visible catalog.
    var term by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PluginEntry>?>(null) }
    // `plugin/installed` is a different question from `plugin/list`: it answers with what is
    // installed regardless of which marketplace it came from, and drops the rest.
    var installedOnly by remember { mutableStateOf(false) }
    var installedEntries by remember { mutableStateOf<List<PluginEntry>?>(null) }
    var detail by remember { mutableStateOf<PluginDetail?>(null) }
    var addingMarketplace by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // `null` is the "All plugins" tab; a name selects one marketplace, the tab set upstream builds
    // from `plugin/list`.
    var selectedMarketplace by remember { mutableStateOf<String?>(null) }

    val tabPlugins =
        if (selectedMarketplace == null) {
            catalog.plugins
        } else {
            catalog.marketplaces.filter { it.name == selectedMarketplace }.flatMap { it.plugins }
        }
    val plugins = results ?: installedEntries ?: tabPlugins
    val installed = plugins.filter { it.installed }
    val marketplace = plugins.filterNot { it.installed }

    fun search(query: String) {
        term = query
        if (query.isBlank()) {
            results = null
            return
        }
        scope.launch {
            client
                .searchPlugins(query)
                .onSuccess { if (term == query) results = it }
                .onFailure { if (term == query) error = it.message }
        }
    }

    fun setInstalledOnly(value: Boolean) {
        installedOnly = value
        if (!value) {
            installedEntries = null
            return
        }
        scope.launch {
            client
                .listInstalledPlugins()
                .onSuccess { response ->
                    installedEntries = response.marketplaces.flatMap { it.plugins }
                }
                .onFailure { error = it.message }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.plugins_screen_title),
            summary =
                stringResource(R.string.plugins_screen_subtitle, installed.size, marketplace.size),
            startAction = { PluginsBackButton(onBack) },
            insideMargin = PaddingValues(14.dp, 10.dp),
        )
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = UiConsts.ScreenMargin)
                    .padding(bottom = UiConsts.Space10)
        ) {
            TextField(
                value = term,
                onValueChange = ::search,
                label = (stringResource(R.string.plugins_screen_search)).orEmpty(),
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            SwitchPreference(
                title = stringResource(R.string.plugins_screen_installed_only),
                summary = stringResource(R.string.plugins_screen_installed_only_detail),
                checked = installedOnly,
                onCheckedChange = ::setInstalledOnly,
            )
            // One tab per marketplace, beside "All plugins": with several marketplaces a plugin's
            // source is a filter, not a column in a list that already scrolls.
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = UiConsts.Space6),
                horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tabs = listOf<String?>(null) + catalog.marketplaces.map { it.name }
                tabs.forEach { tab ->
                    Button(
                        onClick = {
                            selectedMarketplace = tab
                            results = null
                        },
                        colors =
                            if (selectedMarketplace == tab) {
                                ButtonDefaults.buttonColorsPrimary()
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        cornerRadius = UiConsts.ButtonHeightCompact / 2,
                        minHeight = UiConsts.ButtonHeightCompact,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = tab ?: stringResource(R.string.plugins_screen_all),
                            fontSize = UiType.Action,
                            lineHeight = UiType.ActionLine,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiConsts.ScreenMargin)
                    .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            error?.let { Text(it, color = colors.error, fontSize = UiType.Meta) }
            MarketplacesCard(catalog.marketplaces, onEvent, onAdd = { addingMarketplace = true })
            if (plugins.isEmpty()) {
                Card(
                    cornerRadius = UiConsts.SectionCorner,
                    insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.plugins_screen_title),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Store,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                    )

                    Text(
                        text = stringResource(R.string.plugins_screen_empty),
                        modifier = Modifier.padding(vertical = UiConsts.Space4),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.disabledOnSurface,
                    )
                }
            } else {
                PluginGroupCard(
                    title = stringResource(R.string.plugins_screen_installed),
                    emptyText = stringResource(R.string.plugins_screen_installed_empty),
                    onEvent = onEvent,
                    entries = installed,
                    onOpen = { plugin ->
                        scope.launch {
                            client
                                .readPlugin(plugin.name, plugin.marketplace.ifEmpty { null })
                                .onSuccess { detail = it }
                                .onFailure { error = it.message }
                        }
                    },
                )
                PluginGroupCard(
                    title = stringResource(R.string.plugins_screen_marketplace),
                    emptyText = stringResource(R.string.plugins_screen_marketplace_empty),
                    onEvent = onEvent,
                    entries = marketplace,
                    onOpen = { plugin ->
                        scope.launch {
                            client
                                .readPlugin(plugin.name, plugin.marketplace.ifEmpty { null })
                                .onSuccess { detail = it }
                                .onFailure { error = it.message }
                        }
                    },
                )
            }
        }
    }

    detail?.let { open ->
        PluginDetailSheet(detail = open, client = client, onDismiss = { detail = null })
    }
    if (addingMarketplace) {
        MarketplaceFormSheet(
            onDismiss = { addingMarketplace = false },
            onSubmit = { source, ref ->
                onEvent(AppEvent.AddMarketplace(source, ref))
                addingMarketplace = false
            },
        )
    }
    catalog.pluginInstallAuth?.let { flow ->
        PluginInstallAuthSheet(
            flow = flow,
            client = client,
            onRefresh = { onEvent(AppEvent.ReloadApps) },
            onDismiss = { catalog.pluginInstallAuth = null },
        )
    }
}

/**
 * The post-install connector setup.
 *
 * Mirrors the auth popup in `chatwidget/plugins.rs`: one connector per step, the browser opens its
 * `installUrl`, and "I've installed it" re-reads `app/list` before advancing — a connector the
 * account does not have yet cannot be skipped past silently. The remaining connectors can be
 * skipped as a group, which abandons the flow rather than pretending the plugin is ready.
 */
@Composable
private fun PluginInstallAuthSheet(
    flow: PluginInstallAuthFlow,
    client: AppServerClient,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var index by remember(flow) { mutableIntStateOf(0) }
    var checking by remember(flow) { mutableStateOf(false) }
    var stillMissing by remember(flow) { mutableStateOf<String?>(null) }
    val app = flow.apps.getOrNull(index)

    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title =
            if (app != null) {
                stringResource(R.string.plugins_auth_step, index + 1, flow.apps.size)
            } else {
                stringResource(R.string.plugins_auth_done)
            },
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = UiConsts.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        ) {
            Text(
                text = flow.pluginName,
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (app == null) {
                Text(
                    text = stringResource(R.string.plugins_auth_done_detail),
                    modifier = Modifier.padding(horizontal = UiConsts.Space4),
                    fontSize = UiType.SheetBody,
                    lineHeight = UiType.SheetBodyLine,
                    color = colors.onSurface,
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space12),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    cornerRadius = UiConsts.ButtonHeight / 2,
                    minHeight = UiConsts.ButtonHeight,
                    insideMargin =
                        PaddingValues(
                            horizontal = UiConsts.ButtonPaddingHorizontal,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.plugins_auth_continue),
                        fontSize = UiType.Action,
                        lineHeight = UiType.ActionLine,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                return@WindowBottomSheet
            }
            Text(
                text = stringResource(R.string.plugins_auth_body),
                modifier = Modifier.padding(horizontal = UiConsts.Space4),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
            Text(
                text = app.name,
                modifier =
                    Modifier.padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space6),
                fontSize = UiType.SheetTitle,
                lineHeight = UiType.SheetTitleLine,
                color = colors.onSurface,
            )
            app.description
                ?.takeIf { it.isNotBlank() }
                ?.let { description ->
                    Text(
                        text = description,
                        modifier = Modifier.padding(horizontal = UiConsts.Space4),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            stillMissing?.let { name ->
                Text(
                    text = stringResource(R.string.plugins_auth_still_missing, name),
                    modifier =
                        Modifier.padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space6),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.error,
                )
            }
            val url = app.installUrl
            if (url != null) {
                Button(
                    onClick = { runCatching { uriHandler.openUri(url) } },
                    modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space10),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    cornerRadius = UiConsts.ButtonHeight / 2,
                    minHeight = UiConsts.ButtonHeight,
                    insideMargin =
                        PaddingValues(
                            horizontal = UiConsts.ButtonPaddingHorizontal,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.plugins_auth_open, app.name),
                        fontSize = UiType.Action,
                        lineHeight = UiType.ActionLine,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        checking = true
                        val installed =
                            client
                                .listApps()
                                .getOrNull()
                                .orEmpty()
                                .filter { it.installed }
                                .mapTo(mutableSetOf()) { it.id }
                        checking = false
                        onRefresh()
                        if (app.id in installed || url == null) {
                            stillMissing = null
                            index++
                        } else {
                            stillMissing = app.name
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !checking,
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.plugins_auth_installed),
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.plugins_auth_skip),
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PluginGroupCard(
    title: String,
    emptyText: String,
    onEvent: (AppEvent) -> Unit,
    entries: List<PluginEntry>,
    onOpen: (PluginEntry) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
    ) {
        BasicComponent(
            title = title,
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Store,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                Text(
                    text = entries.size.toString(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            },
        )

        if (entries.isEmpty()) {
            Text(
                text = emptyText,
                modifier = Modifier.padding(vertical = UiConsts.Space4),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.disabledOnSurface,
            )
        } else {
            entries.forEachIndexed { index, plugin ->
                if (index > 0) PluginsDivider()
                PluginRow(plugin, onEvent, onOpen)
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: PluginEntry,
    onEvent: (AppEvent) -> Unit,
    onOpen: (PluginEntry) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .squircleSurface(color = Color.Transparent, cornerRadius = UiConsts.RowCorner)
                .combinedClickable(onClick = { onOpen(plugin) })
                .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plugin.name,
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (plugin.description.isNotEmpty()) {
                Text(
                    text = plugin.description,
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(UiConsts.Space3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text =
                        plugin.version.ifEmpty {
                            stringResource(R.string.plugins_screen_no_version)
                        },
                    fontSize = UiType.Caption,
                    lineHeight = UiType.CaptionLine,
                    fontFamily = FontFamily.Monospace,
                    color = colors.disabledOnSurface,
                )
                Spacer(Modifier.width(UiConsts.Space6))
                PluginsChip(
                    text =
                        plugin.marketplace.ifEmpty {
                            stringResource(R.string.plugins_screen_local)
                        },
                    tint = colors.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.width(UiConsts.Space10))
        // An installed plugin gets the enable/disable switch the TUI binds to Space; a marketplace
        // entry gets the install button. They are never both: an entry that is not installed has no
        // enablement to toggle, and an installed one has nothing left to install.
        if (plugin.installed) {
            Switch(
                checked = plugin.enabled,
                onCheckedChange = { onEvent(AppEvent.SetPluginEnabled(plugin.id, it)) },
            )
            Spacer(Modifier.width(UiConsts.Space8))
        }
        // Installing is the one thing this page can do for a plugin, so the marketplace chip is the
        // accent pill and "Installed" — a state, not an action — is the outlined one.
        Button(
            onClick = {
                if (plugin.installed) {
                    onEvent(AppEvent.UninstallPlugin(plugin.id))
                } else {
                    onEvent(
                        AppEvent.InstallPlugin(plugin.name, plugin.marketplace.ifEmpty { null })
                    )
                }
            },
            colors =
                if (plugin.installed) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
            cornerRadius = UiConsts.ButtonHeightCompact / 2,
            minHeight = UiConsts.ButtonHeightCompact,
            insideMargin =
                PaddingValues(
                    horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                    vertical = 0.dp,
                ),
        ) {
            Text(
                text =
                    if (plugin.installed) {
                        stringResource(R.string.plugins_screen_installed)
                    } else {
                        stringResource(R.string.plugins_screen_install)
                    },
                fontSize = UiType.Action,
                lineHeight = UiType.ActionLine,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PluginsBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        minWidth = UiConsts.IconButtonSize,
        minHeight = UiConsts.IconButtonSize,
    ) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.plugins_screen_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PluginsChip(text: String, tint: Color) {
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(UiConsts.BadgeCorner))
                .background(tint.copy(alpha = UiConsts.BadgeTintAlpha))
                .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space1)
    ) {
        Text(
            text = text,
            fontSize = UiType.Badge,
            lineHeight = UiType.BadgeLine,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun PluginsDivider() =
    HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))

/**
 * What one plugin actually contributes.
 *
 * `plugin/read` answers with the manifest — skills, MCP servers and apps the plugin installs — and
 * `plugin/skill/read` fetches one skill's body. Both are reads the catalog list cannot answer: the
 * list only knows a plugin's name and whether it is installed, so a user deciding whether to
 * install one has nothing to decide on until this sheet opens.
 */
@Composable
private fun PluginDetailSheet(
    detail: PluginDetail,
    client: AppServerClient,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var openSkill by remember(detail.id) { mutableStateOf<String?>(null) }
    var skillBody by remember(detail.id) { mutableStateOf<String?>(null) }
    var skillFailed by remember(detail.id) { mutableStateOf(false) }

    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = detail.name,
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = UiConsts.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        ) {
            Text(
                text = detail.version.ifEmpty { detail.marketplace },
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (detail.description.isNotEmpty()) {
                Text(
                    text = detail.description,
                    modifier = Modifier.padding(horizontal = UiConsts.Space4),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Card(
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
            ) {
                BasicComponent(
                    title = stringResource(R.string.plugins_detail_facts),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )

                BasicComponent(
                    title = stringResource(R.string.plugins_detail_id),
                    endActions = {
                        Text(
                            text = detail.id.ifEmpty { "—" },
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            fontSize = UiType.Detail,
                        )
                    },
                    insideMargin =
                        PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                BasicComponent(
                    title = stringResource(R.string.plugins_detail_marketplace),
                    endActions = {
                        Text(
                            text = detail.marketplace.ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            fontSize = UiType.Detail,
                        )
                    },
                    insideMargin =
                        PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                )
                if (detail.author != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                    BasicComponent(
                        title = stringResource(R.string.plugins_detail_author),
                        endActions = {
                            Text(
                                text = detail.author.ifEmpty { "—" },
                                color = MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                                fontSize = UiType.Detail,
                            )
                        },
                        insideMargin =
                            PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                    )
                }
                if (detail.homepage != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                    BasicComponent(
                        title = stringResource(R.string.plugins_detail_homepage),
                        endActions = {
                            Text(
                                text = detail.homepage.ifEmpty { "—" },
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                                fontSize = UiType.Detail,
                            )
                        },
                        insideMargin =
                            PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                    )
                }
            }
            if (detail.skills.isNotEmpty()) {
                Card(
                    cornerRadius = UiConsts.SectionCorner,
                    insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.plugins_detail_skills),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Tasks,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        endActions = {
                            Text(
                                text = detail.skills.size.toString(),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        },
                    )

                    detail.skills.forEach { skill ->
                        ArrowPreference(
                            title = skill.name,
                            summary = skill.description.ifEmpty { null },
                            onClick = {
                                if (openSkill == skill.name) {
                                    openSkill = null
                                    return@ArrowPreference
                                }
                                openSkill = skill.name
                                skillBody = null
                                skillFailed = false
                                scope.launch {
                                    client
                                        .readPluginSkill(
                                            marketplace = detail.marketplace,
                                            pluginId = detail.id,
                                            skillName = skill.name,
                                        )
                                        .onSuccess { skillBody = it }
                                        .onFailure { skillFailed = true }
                                }
                            },
                        )
                        if (openSkill == skill.name) {
                            Text(
                                text =
                                    skillBody
                                        ?: if (skillFailed) {
                                            stringResource(R.string.plugins_detail_skill_failed)
                                        } else {
                                            stringResource(R.string.plugins_detail_skill_loading)
                                        },
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(
                                            horizontal = UiConsts.Space4,
                                            vertical = UiConsts.Space6,
                                        ),
                                fontSize = UiType.Code,
                                lineHeight = UiType.CodeLine,
                                fontFamily = FontFamily.Monospace,
                                color = colors.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
            if (detail.mcpServers.isNotEmpty()) {
                Card(
                    cornerRadius = UiConsts.SectionCorner,
                    insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.plugins_detail_mcp),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Link,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        endActions = {
                            Text(
                                text = detail.mcpServers.size.toString(),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        },
                    )

                    detail.mcpServers.forEach { server ->
                        BasicComponent(
                            title = server.name,
                            endActions = {
                                Text(
                                    text = server.status.label().ifEmpty { "—" },
                                    color = MiuixTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                    fontSize = UiType.Detail,
                                )
                            },
                            insideMargin =
                                PaddingValues(
                                    horizontal = UiConsts.Space4,
                                    vertical = UiConsts.Space7,
                                ),
                        )
                    }
                }
            }
            if (detail.apps.isNotEmpty()) {
                Card(
                    cornerRadius = UiConsts.SectionCorner,
                    insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.plugins_detail_apps),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Community,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        endActions = {
                            Text(
                                text = detail.apps.size.toString(),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        },
                    )

                    detail.apps.forEach { app ->
                        BasicComponent(
                            title = app.name,
                            endActions = {
                                Text(
                                    text = app.id.ifEmpty { "—" },
                                    fontFamily = FontFamily.Monospace,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                    fontSize = UiType.Detail,
                                )
                            },
                            insideMargin =
                                PaddingValues(
                                    horizontal = UiConsts.Space4,
                                    vertical = UiConsts.Space7,
                                ),
                        )
                    }
                }
            }
        }
    }
}
