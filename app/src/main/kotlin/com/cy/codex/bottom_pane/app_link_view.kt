package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.v2.AppInfo
import com.cy.codex.raisedSurface
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/apps` output as a page.
 *
 * Mirrors `app/list` and `bottom_pane/app_link_view.rs`: the TUI opens one app at a time to link or
 * install it in a browser, the phone lists the account's connectors and keeps the same two-section
 * split the plugin catalog uses. The action is inert until an app-link event exists.
 */
@Composable
fun AppsScreen(
    catalog: CatalogState,
    client: AppServerClient,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    // `app/installed` and `app/list` are two different questions. The list the catalog holds comes
    // from `app/list`; asking for the installed set narrows it server-side and drops the rest.
    var installedOnly by remember { mutableStateOf(false) }
    var narrowed by remember { mutableStateOf<List<AppInfo>?>(null) }
    // `app/read` takes ids, which is what a page needs after it has *written* one app's state: it
    // can re-read just that row instead of the whole catalog.
    var reread by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    val apps = narrowed ?: catalog.apps
    val installed = apps.filter { it.installed }
    val marketplace = apps.filterNot { it.installed }

    fun setInstalledOnly(value: Boolean) {
        installedOnly = value
        if (!value) {
            narrowed = null
            return
        }
        scope.launch { client.listInstalledApps().onSuccess { narrowed = it } }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.apps_screen_title),
            summary =
                stringResource(R.string.apps_screen_subtitle, installed.size, marketplace.size),
            startAction = { AppsBackButton(onBack) },
            insideMargin = PaddingValues(14.dp, 10.dp),
        )
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiConsts.ScreenMargin)
                    .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            SwitchPreference(
                title = stringResource(R.string.apps_screen_installed_only),
                summary = stringResource(R.string.apps_screen_installed_only_detail),
                checked = installedOnly,
                onCheckedChange = ::setInstalledOnly,
            )
            if (reread.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = UiConsts.SectionCorner,
                    insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                    colors =
                        CardDefaults.defaultColors(
                            color = raisedSurface(),
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        ),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.apps_screen_reread),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Community,
                                contentDescription = null,
                                modifier = Modifier.size(UiConsts.IconInline),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        insideMargin = PaddingValues(0.dp),
                        endActions = { Text(text = reread.size.toString(), maxLines = 1) },
                    )
                    Spacer(Modifier.height(UiConsts.Space8))

                    reread.forEachIndexed { index, app ->
                        if (index > 0)
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = UiConsts.Space1)
                            )
                        BasicComponent(
                            title = app.name,
                            endActions = {
                                Text(
                                    text =
                                        if (app.installed) {
                                                stringResource(R.string.apps_screen_installed)
                                            } else {
                                                stringResource(R.string.apps_screen_not_installed)
                                            }
                                            .ifEmpty { "—" },
                                    fontFamily = null,
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
            if (apps.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = UiConsts.SectionCorner,
                    insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                    colors =
                        CardDefaults.defaultColors(
                            color = raisedSurface(),
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        ),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.apps_screen_title),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.GridView,
                                contentDescription = null,
                                modifier = Modifier.size(UiConsts.IconInline),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        insideMargin = PaddingValues(0.dp),
                    )
                    Spacer(Modifier.height(UiConsts.Space8))

                    Text(
                        text = stringResource(R.string.apps_screen_empty),
                        modifier = Modifier.padding(vertical = UiConsts.Space4),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.disabledOnSurface,
                    )
                }
            } else {
                AppsGroupCard(
                    title = stringResource(R.string.apps_screen_installed),
                    emptyText = stringResource(R.string.apps_screen_installed_empty),
                    onEvent = onEvent,
                    entries = installed,
                    onReread = { id ->
                        scope.launch {
                            client.readApps(listOf(id)).onSuccess { reread = it }
                        }
                    },
                )
                AppsGroupCard(
                    title = stringResource(R.string.apps_screen_marketplace),
                    emptyText = stringResource(R.string.apps_screen_marketplace_empty),
                    onEvent = onEvent,
                    entries = marketplace,
                    onReread = { id ->
                        scope.launch {
                            client.readApps(listOf(id)).onSuccess { reread = it }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AppsGroupCard(
    title: String,
    emptyText: String,
    onEvent: (AppEvent) -> Unit,
    entries: List<AppInfo>,
    onReread: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        colors =
            CardDefaults.defaultColors(
                color = raisedSurface(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        BasicComponent(
            title = title,
            startAction = {
                Icon(
                    imageVector = MiuixIcons.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconInline),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            insideMargin = PaddingValues(0.dp),
            endActions = { Text(text = entries.size.toString(), maxLines = 1) },
        )
        Spacer(Modifier.height(UiConsts.Space8))

        if (entries.isEmpty()) {
            Text(
                text = emptyText,
                modifier = Modifier.padding(vertical = UiConsts.Space4),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.disabledOnSurface,
            )
        } else {
            entries.forEachIndexed { index, app ->
                if (index > 0) AppsDivider()
                AppsRow(app, onEvent, onReread)
            }
        }
    }
}

@Composable
private fun AppsRow(
    app: AppInfo,
    onEvent: (AppEvent) -> Unit,
    onReread: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.description.isNotEmpty()) {
                Text(
                    text = app.description,
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(UiConsts.Space3))
            Text(
                text = app.id,
                fontSize = UiType.Caption,
                lineHeight = UiType.CaptionLine,
                fontFamily = FontFamily.Monospace,
                color = colors.disabledOnSurface,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(UiConsts.Space10))
        // Re-reading one app is `app/read` with a single id: after a write, the server's copy of
        // that row is the only one that can say whether the install actually took, and re-listing
        // every app to find out would be a request per row.
        Button(
            onClick = { onReread(app.id) },
            modifier = Modifier,
            enabled = true,
            colors = ButtonDefaults.buttonColors(),
            cornerRadius = UiConsts.ButtonHeightCompact / 2,
            minWidth = 0.dp,
            minHeight = UiConsts.ButtonHeightCompact,
            insideMargin =
                PaddingValues(
                    horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                    vertical = 0.dp,
                ),
        ) {
            Text(
                text = stringResource(R.string.apps_screen_reread_one),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(UiConsts.Space6))
        // Installing is the one thing this page can do for an app, so the marketplace chip is the
        // accent pill and "Installed" — a state, not an action — is the outlined one.
        Button(
            onClick = { onEvent(AppEvent.SetAppInstalled(app.id, !app.installed)) },
            colors =
                if (app.installed) ButtonDefaults.buttonColors()
                else ButtonDefaults.buttonColorsPrimary(),
            cornerRadius = UiConsts.ButtonHeightCompact / 2,
            minWidth = 0.dp,
            minHeight = UiConsts.ButtonHeightCompact,
            insideMargin =
                PaddingValues(
                    horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                    vertical = 0.dp,
                ),
        ) {
            Text(
                text =
                    if (app.installed) {
                        stringResource(R.string.apps_screen_installed)
                    } else {
                        stringResource(R.string.apps_screen_install)
                    },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppsBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        minWidth = UiConsts.IconButtonSize,
        minHeight = UiConsts.IconButtonSize,
    ) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.apps_screen_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AppsDivider() =
    HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
