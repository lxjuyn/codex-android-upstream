package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.R
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.McpAuthStatus
import com.cy.codex.protocol.protocol.v2.McpServerConnectionStatus
import com.cy.codex.protocol.protocol.v2.McpServerStartupState
import com.cy.codex.protocol.protocol.v2.McpServerStatusEntry
import com.cy.codex.raisedSurface
import com.cy.codex.statusDotColor
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
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/mcp` output as a page: one row per configured server with its connection state.
 *
 * Mirrors `mcpServerStatus/list` and the startup banner the TUI prints (`mcp_startup`): the dot is
 * the same status tone the transcript uses, so a failed server looks the same in both places.
 */
@Composable
fun McpScreen(
    catalog: CatalogState,
    onBack: () -> Unit,
    onOpenServer: (String) -> Unit,
    onEvent: (AppEvent) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val servers = catalog.mcpServers
    val ready = servers.count { it.status == McpServerConnectionStatus.Connected }
    // Failed first, then still-starting, so a server that needs attention is above one that is
    // merely slow.
    val startup = catalog.mcpStartup.values.sortedBy { it.status != McpServerStartupState.Failed }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.mcp_screen_title),
            summary = stringResource(R.string.mcp_screen_subtitle, servers.size, ready),
            startAction = { McpBackButton(onBack) },
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
            if (startup.isNotEmpty()) {
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
                        title = stringResource(R.string.mcp_screen_startup_section),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Community,
                                contentDescription = null,
                                modifier = Modifier.size(UiConsts.IconInline),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        insideMargin = PaddingValues(0.dp),
                        endActions = { Text(text = startup.size.toString(), maxLines = 1) },
                    )
                    Spacer(Modifier.height(UiConsts.Space8))

                    startup.forEachIndexed { index, update ->
                        if (index > 0) McpDivider()
                        McpStartupRow(update)
                    }
                }
            }
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
                    title = stringResource(R.string.mcp_screen_section_servers),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Community,
                            contentDescription = null,
                            modifier = Modifier.size(UiConsts.IconInline),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    insideMargin = PaddingValues(0.dp),
                    endActions = { Text(text = servers.size.toString(), maxLines = 1) },
                )
                Spacer(Modifier.height(UiConsts.Space8))

                if (servers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.mcp_screen_empty),
                        modifier = Modifier.padding(vertical = UiConsts.Space4),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.disabledOnSurface,
                    )
                } else {
                    servers.forEachIndexed { index, server ->
                        if (index > 0) McpDivider()
                        McpServerRow(
                            server = server,
                            onClick = { onOpenServer(server.name) },
                            onLogin = { onEvent(AppEvent.McpLogin(server.name)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun McpStartupRow(update: com.cy.codex.protocol.protocol.v2.McpStartupStatusUpdated) {
    val colors = MiuixTheme.colorScheme
    val failed = update.status == McpServerStartupState.Failed
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(UiConsts.DotSize)
                    .clip(CircleShape)
                    .background(
                        statusDotColor(
                            if (failed) ThreadStatusTone.Failed else ThreadStatusTone.Waiting
                        )
                    )
        )
        Spacer(Modifier.width(UiConsts.Space8))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    if (failed) {
                        stringResource(R.string.mcp_screen_startup_failed, update.serverName)
                    } else {
                        stringResource(R.string.mcp_screen_startup_starting, update.serverName)
                    },
                fontSize = UiType.Subtitle,
                lineHeight = UiType.SubtitleLine,
                color = colors.onSurface,
            )
            val detail =
                update.error
                    ?: if (failed && update.failureReason == "reauthenticationRequired") {
                        stringResource(R.string.mcp_screen_startup_reauth)
                    } else {
                        null
                    }
            if (detail != null) {
                Spacer(Modifier.height(UiConsts.Space2))
                Text(
                    text = detail,
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.error,
                )
            }
        }
    }
}

@Composable
private fun McpServerRow(server: McpServerStatusEntry, onClick: () -> Unit, onLogin: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val tone = mcpTone(server.status)
    val needsLogin =
        server.authStatus == McpAuthStatus.NotLoggedIn ||
            server.status == McpServerConnectionStatus.AuthenticationRequired
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.size(UiConsts.DotSize)
                        .clip(CircleShape)
                        .background(statusDotColor(tone))
            )
            Spacer(Modifier.width(UiConsts.Space8))
            Text(
                text = server.name,
                modifier = Modifier.weight(1f),
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            McpChip(text = server.status.label(), tint = statusDotColor(tone))
        }
        Spacer(Modifier.height(UiConsts.Space4))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    stringResource(
                        R.string.mcp_screen_tools_resources,
                        server.tools,
                        server.resources,
                    ),
                modifier = Modifier.weight(1f),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
            Text(
                text = server.status.wire,
                fontSize = UiType.Caption,
                lineHeight = UiType.CaptionLine,
                fontFamily = FontFamily.Monospace,
                color = colors.disabledOnSurface,
            )
        }
        if (!server.error.isNullOrBlank()) {
            Spacer(Modifier.height(UiConsts.Space6))
            Text(
                text = server.error,
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(McpRowShape)
                        .background(colors.error.copy(alpha = 0.12f))
                        .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space6),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.error,
            )
        }
        // The action `authStatus` exists for: without it the server stays disconnected and the
        // reason is only visible as a status word.
        if (needsLogin) {
            Spacer(Modifier.height(UiConsts.Space8))
            Row(verticalAlignment = Alignment.CenterVertically) {
                McpChip(
                    text = stringResource(R.string.mcp_screen_auth_not_logged_in),
                    tint = colors.error,
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onLogin,
                    modifier = Modifier,
                    enabled = true,
                    colors = ButtonDefaults.buttonColorsPrimary(),
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
                        text = stringResource(R.string.mcp_screen_auth_login),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** `mcp add` opens a config editor the phone does not have yet, so the row stays inert. */
@Composable
private fun McpAddServerRow() {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.AddCircle,
            contentDescription = null,
            modifier = Modifier.size(UiConsts.IconInline),
            tint = colors.disabledOnSurface,
        )
        Spacer(Modifier.width(UiConsts.Space8))
        Text(
            text = stringResource(R.string.mcp_screen_add_server),
            modifier = Modifier.weight(1f),
            fontSize = UiType.Subtitle,
            lineHeight = UiType.SubtitleLine,
            color = colors.disabledOnSurface,
        )
        Text(
            text = stringResource(R.string.mcp_screen_not_connected),
            fontSize = UiType.Caption,
            lineHeight = UiType.CaptionLine,
            color = colors.disabledOnSurface,
        )
    }
}

@Composable
private fun McpBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        minWidth = UiConsts.IconButtonSize,
        minHeight = UiConsts.IconButtonSize,
    ) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.mcp_screen_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun McpChip(text: String, tint: Color) {
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(UiConsts.BadgeCorner))
                .background(tint.copy(alpha = UiConsts.BadgeTintAlpha))
                .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space2)
    ) {
        Text(
            text = text,
            fontSize = UiType.Chip,
            lineHeight = UiType.ChipLine,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun McpDivider() =
    HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))

private val McpRowShape = RoundedCornerShape(UiConsts.RowCorner)

/** Connection state → the same four tones the transcript's status dots use. */
private fun mcpTone(status: McpServerConnectionStatus): ThreadStatusTone =
    when (status) {
        McpServerConnectionStatus.Connected -> ThreadStatusTone.Done
        McpServerConnectionStatus.NotStarted,
        McpServerConnectionStatus.Starting,
        McpServerConnectionStatus.AuthenticationRequired -> ThreadStatusTone.Waiting

        McpServerConnectionStatus.Failed -> ThreadStatusTone.Failed
        McpServerConnectionStatus.Cancelled,
        McpServerConnectionStatus.Disabled -> ThreadStatusTone.Idle
    }
