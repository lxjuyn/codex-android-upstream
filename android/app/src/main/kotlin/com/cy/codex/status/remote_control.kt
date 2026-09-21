package com.cy.codex.status

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cy.codex.ActionRow
import com.cy.codex.AppEvent
import com.cy.codex.ButtonRole
import com.cy.codex.CatalogState
import com.cy.codex.CodexButton
import com.cy.codex.CodexButtonSize
import com.cy.codex.CodexDivider
import com.cy.codex.CodexSwitchRow
import com.cy.codex.EmptyState
import com.cy.codex.R
import com.cy.codex.SectionCard
import com.cy.codex.SurfaceBackButton
import com.cy.codex.SurfaceHeader
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.ValueRow
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.RemoteControlClient
import com.cy.codex.protocol.protocol.v2.RemoteControlConnectionStatus
import com.cy.codex.protocol.protocol.v2.RemoteControlStatus
import com.cy.codex.statusDotColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Remote control: the relay, the pairing flow, and the devices paired with this machine.
 *
 * Mirrors `codex-rs/tui/src/status/remote_connection.rs` and `app/daemon_menu.rs`: the terminal
 * puts the relay's state on the `/status` card and the pairing commands in the daemon menu, and a
 * phone reads both in one glance, so this page is the two halves together rather than two surfaces.
 *
 * The page keeps no state of its own. Every value it prints comes out of [CatalogState] and every
 * write leaves as an [AppEvent], which keeps the shell's reducer the only thing that talks to the
 * server and lets this page be recomposed from the catalog alone.
 */
@Composable
fun RemoteControlScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val status = catalog.remoteControl
    val relayLabel = status?.status?.label()
        ?: stringResource(R.string.remote_control_page_unread)

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SurfaceHeader(
            title = stringResource(R.string.remote_control_page_title),
            subtitle = relayLabel,
            leading = {
                SurfaceBackButton(stringResource(R.string.remote_control_page_back), onBack)
            },
            trailing = {
                IconButton(
                    onClick = { onEvent(AppEvent.ReloadRemoteControl) },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.remote_control_page_refresh),
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
            ConnectionCard(status = status, onEvent = onEvent)
            PairingCard(
                code = catalog.remoteControlPairingCode,
                claimed = catalog.remoteControlPairingClaimed,
                onEvent = onEvent,
            )
            PairedDevicesCard(clients = catalog.remoteControlClients, onEvent = onEvent)
        }
    }
}

/**
 * The relay's state, and the switch that turns it on.
 *
 * The switch sits in this card rather than in one of its own because it is the only control the
 * state has: a card whose whole body is one switch would hide the four facts a user needs in order
 * to decide about it. When nothing has been read yet the card shows an empty state instead of the
 * switch — an unchecked switch over an unknown link would report the machine as off, which is the
 * one thing this client does not know at that point.
 */
@Composable
private fun ConnectionCard(
    status: RemoteControlStatus?,
    onEvent: (AppEvent) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.remote_control_connection),
        icon = MiuixIcons.ScreenMirroring,
    ) {
        if (status == null) {
            EmptyState(
                icon = MiuixIcons.ScreenMirroring,
                title = stringResource(R.string.remote_control_unread),
                detail = stringResource(R.string.remote_control_unread_detail),
                action = {
                    CodexButton(
                        text = stringResource(R.string.remote_control_page_retry),
                        onClick = { onEvent(AppEvent.ReloadRemoteControl) },
                        role = ButtonRole.Secondary,
                    )
                },
            )
        } else {
            ValueRow(
                label = stringResource(R.string.remote_control_status_label),
                value = status.status.label(),
                tint = statusDotColor(status.status.tone()),
            )
            CodexDivider()
            ValueRow(
                label = stringResource(R.string.remote_control_server),
                value = status.serverName,
            )
            CodexDivider()
            ValueRow(
                label = stringResource(R.string.remote_control_installation),
                value = status.installationId,
                monospace = true,
            )
            CodexDivider()
            ValueRow(
                label = stringResource(R.string.remote_control_environment),
                value = status.environmentId.orEmpty(),
            )
            CodexSwitchRow(
                title = stringResource(R.string.remote_control_relay),
                subtitle = stringResource(R.string.remote_control_relay_detail),
                checked = status.status != RemoteControlConnectionStatus.Disabled,
                onCheckedChange = { onEvent(AppEvent.SetRemoteControlEnabled(it)) },
            )
        }
    }
}

/**
 * Pairing: the code another device claims.
 *
 * Three actions, because the protocol gives three: mint a code, ask whether it has been taken, and
 * mint a different one. The middle one is a *poll* rather than a subscription — nothing pushes the
 * claim — so it is a button the user presses rather than something the page waits on; a page that
 * polled on its own would have to choose an interval, and a code read out loud is not something to
 * hammer the relay about.
 */
@Composable
private fun PairingCard(
    code: String?,
    claimed: Boolean?,
    onEvent: (AppEvent) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.remote_control_pairing),
        icon = MiuixIcons.Link,
    ) {
        if (code == null) {
            RemoteControlNote(stringResource(R.string.remote_control_pairing_detail))
            CodexButton(
                text = stringResource(R.string.remote_control_pairing_start),
                onClick = { onEvent(AppEvent.StartRemoteControlPairing) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiConsts.Space4),
            )
        } else {
            RemoteControlNote(stringResource(R.string.remote_control_pairing_hint))
            ValueRow(
                label = stringResource(R.string.remote_control_pairing_code),
                value = code,
                monospace = true,
            )
            CodexDivider()
            ValueRow(
                label = stringResource(R.string.remote_control_pairing_state),
                value = if (claimed == true) {
                    stringResource(R.string.remote_control_pairing_claimed)
                } else {
                    stringResource(R.string.remote_control_pairing_waiting)
                },
                tint = statusDotColor(
                    if (claimed == true) ThreadStatusTone.Done else ThreadStatusTone.Waiting,
                ),
            )
            CodexButton(
                text = stringResource(R.string.remote_control_pairing_check),
                onClick = { onEvent(AppEvent.PollRemoteControlPairing) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
                enabled = claimed != true,
            )
            CodexButton(
                text = stringResource(R.string.remote_control_pairing_restart),
                onClick = { onEvent(AppEvent.StartRemoteControlPairing) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiConsts.Space4)
                    .padding(bottom = UiConsts.Space8),
                role = ButtonRole.Secondary,
            )
        }
    }
}

/**
 * The devices that have paired with this machine, each with the one action that matters.
 *
 * A tap expands a row instead of opening a sheet, the way the project list behaves: there is one
 * action per device and it is destructive, so it stays behind a deliberate tap rather than sitting
 * in the path of a scroll.
 *
 * The list is addressed by environment — `remoteControl/client/list` takes an `environmentId` —
 * so a link that has no environment at all cannot have a list, and the app skips that read instead
 * of asking with a blank id.
 */
@Composable
private fun PairedDevicesCard(
    clients: List<RemoteControlClient>,
    onEvent: (AppEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf<String?>(null) }

    SectionCard(
        title = stringResource(R.string.remote_control_devices),
        icon = MiuixIcons.Phone,
        trailing = clients.size.toString(),
    ) {
        if (clients.isEmpty()) {
            EmptyState(
                icon = MiuixIcons.Phone,
                title = stringResource(R.string.remote_control_devices_empty),
                detail = stringResource(R.string.remote_control_devices_empty_detail),
            )
        }
        clients.forEachIndexed { index, client ->
            if (index > 0) CodexDivider()
            PairedDeviceRow(
                client = client,
                expanded = expanded == client.clientId,
                onToggle = {
                    expanded = if (expanded == client.clientId) null else client.clientId
                },
                onRevoke = { onEvent(AppEvent.RevokeRemoteControlClient(client.clientId)) },
            )
        }
    }
}

/**
 * One paired device: what it is, when it was last seen, and its revoke action.
 *
 * The title falls back to the client id because `displayName` is optional on the wire, and a row
 * with no title at all would leave the user choosing which device to cut off by elimination.
 */
@Composable
private fun PairedDeviceRow(
    client: RemoteControlClient,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRevoke: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ActionRow(
            title = client.displayName?.takeIf { it.isNotBlank() } ?: client.clientId,
            subtitle = deviceSummary(client),
            trailing = lastSeenAge(client.lastSeenAt),
            onClick = onToggle,
        )
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = UiConsts.Space4,
                        end = UiConsts.Space4,
                        bottom = UiConsts.Space8,
                    ),
                horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                Spacer(Modifier.weight(1f))
                CodexButton(
                    text = stringResource(R.string.remote_control_device_revoke),
                    onClick = onRevoke,
                    size = CodexButtonSize.Compact,
                    role = ButtonRole.Destructive,
                )
            }
        }
    }
}

/**
 * What a device is, as one line: platform, model and OS version.
 *
 * The three fields are optional on the wire, and one the server did not send is left out rather
 * than rendered as a gap: `platform · deviceModel · osVersion` with a hole in it reads as a device
 * whose model is unknown to the server, which is a different claim from "this client never told
 * us".
 */
@Composable
private fun deviceSummary(client: RemoteControlClient): String? {
    val parts = listOfNotNull(
        client.platform?.takeIf { it.isNotBlank() },
        client.deviceModel?.takeIf { it.isNotBlank() },
        client.osVersion?.takeIf { it.isNotBlank() },
    )
    return parts
        .takeIf { it.isNotEmpty() }
        ?.joinToString(stringResource(R.string.remote_control_device_separator))
}

/**
 * When a device was last seen, as the framework's own relative age.
 *
 * `DateUtils` is used rather than a resource of this page's own because the answer is a *duration*,
 * and the platform already carries that ladder in every language it supports; re-deriving it here
 * would be a second, worse copy that only ever handles the locales this app ships. A device that
 * has never checked in has no timestamp at all, and says so in words rather than by leaving the
 * column empty.
 */
@Composable
private fun lastSeenAge(lastSeenAt: Long?): String = lastSeenAt?.let { seen ->
    DateUtils
        .getRelativeTimeSpanString(seen, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        .toString()
} ?: stringResource(R.string.remote_control_device_never_seen)

/**
 * The explanatory paragraph of a card on this page.
 *
 * One composable so every note on the page shares a size, a leading and a colour. Each sentence it
 * is given is a claim about what the protocol does, which is why the text is always a string
 * resource rather than assembled at the call site.
 */
@Composable
private fun RemoteControlNote(text: String) {
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
 * Fold the relay's connection state into the tone the theme knows how to colour.
 *
 * Kept on this page for the same reason the projects page keeps its own environment mapping: which
 * protocol state means which tone is the *surface's* reading, and putting one page's reading of
 * "errored" into the shared palette would let it re-colour an unrelated state on another page.
 */
private fun RemoteControlConnectionStatus.tone(): ThreadStatusTone = when (this) {
    RemoteControlConnectionStatus.Connected -> ThreadStatusTone.Done
    RemoteControlConnectionStatus.Connecting -> ThreadStatusTone.Running
    RemoteControlConnectionStatus.Disabled -> ThreadStatusTone.Idle
    RemoteControlConnectionStatus.Errored -> ThreadStatusTone.Failed
}
