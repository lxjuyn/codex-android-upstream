package com.cy.codex.chatwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.input.KeyboardType
import com.cy.codex.ActionRow
import com.cy.codex.AppEvent
import com.cy.codex.ButtonRole
import com.cy.codex.CatalogState
import com.cy.codex.CodexButton
import com.cy.codex.CodexButtonSize
import com.cy.codex.CodexDivider
import com.cy.codex.EmptyState
import com.cy.codex.R
import com.cy.codex.SectionCard
import com.cy.codex.SurfaceBackButton
import com.cy.codex.SurfaceHeader
import com.cy.codex.UiConsts
import com.cy.codex.ValueRow
import com.cy.codex.app.FormField
import com.cy.codex.app.FormSheet
import com.cy.codex.app.PathSheet
import com.cy.codex.protocol.protocol.v2.MarketplaceEntry
import com.cy.codex.protocol.protocol.v2.PluginShareDiscoverability
import com.cy.codex.protocol.protocol.v2.PluginShareEntry
import com.cy.codex.protocol.protocol.v2.PluginSharePrincipal
import com.cy.codex.protocol.protocol.v2.PluginShareTarget
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Plugin sharing and marketplaces.
 *
 * Mirrors `codex-rs/tui/src/chatwidget/plugins.rs`: this is the surface behind the `plugin/share/…`
 * family, `marketplace/add`, `marketplace/remove`, `marketplace/upgrade` and `plugin/reconcile`.
 *
 * One page for all five because they answer one question — where this account's plugins come from,
 * and where they go — and because each of the five is otherwise a lone button with nowhere to
 * live. The catalog of *installed* plugins is the sibling page `PluginsScreen`: this page never
 * lists what is installed, only what has been published and which marketplaces exist to install
 * from.
 *
 * Every write leaves through [AppEvent] rather than through the client: publishing changes the
 * account's shares and adding or removing a marketplace invalidates the plugin catalog, and both
 * outlive whichever instance of this page asked for them.
 */
@Composable
fun PluginSharesScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val shares = catalog.pluginShares
    val marketplaces = catalog.marketplaces
    val reconciled = catalog.reconciledPlugins
    val upgraded = catalog.upgradedMarketplaces
    var updatingTargets by remember { mutableStateOf<PluginShareEntry?>(null) }
    var publishing by remember { mutableStateOf(false) }
    var addingMarketplace by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SurfaceHeader(
            title = stringResource(R.string.plugin_shares_screen_title),
            subtitle = stringResource(
                R.string.plugin_shares_screen_subtitle,
                shares.size,
                marketplaces.size,
            ),
            leading = {
                SurfaceBackButton(stringResource(R.string.plugin_shares_screen_back), onBack)
            },
            trailing = {
                // Refresh reads both catalogs the page draws: the shares and the marketplaces the
                // plugin catalog was folded from. One button, because a user who suspects either is
                // stale has no way to tell which one is.
                IconButton(
                    onClick = { onEvent(AppEvent.ReloadPluginShares) },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.plugin_shares_screen_refresh),
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
            SharesCard(
                shares = shares,
                onEvent = onEvent,
                onUpdateTargets = { updatingTargets = it },
            )
            PublishCard(onPublish = { publishing = true })
            MarketplacesCard(
                marketplaces = marketplaces,
                onEvent = onEvent,
                onAdd = { addingMarketplace = true },
            )
            ReconcileButton(onEvent = onEvent)
            // The two results sit under the buttons that produce them rather than at the top of the
            // page: nothing scrolls the page for the user, so a card above the fold would report an
            // outcome they would have to scroll back up to find.
            if (reconciled.isNotEmpty() || upgraded.isNotEmpty()) {
                LastRunCard(
                    reconciledPlugins = reconciled,
                    upgradedMarketplaces = upgraded,
                )
            }
        }
    }

    updatingTargets?.let { share ->
        ShareTargetsSheet(
            share = share,
            onDismiss = { updatingTargets = null },
            onSubmit = { discoverability, targets ->
                onEvent(
                    AppEvent.UpdatePluginShareTargets(
                        remotePluginId = share.remotePluginId.orEmpty(),
                        discoverability = discoverability,
                        targets = targets,
                    ),
                )
                updatingTargets = null
            },
        )
    }
    if (publishing) {
        PathSheet(
            title = stringResource(R.string.plugin_shares_publish_title),
            label = stringResource(R.string.plugin_shares_publish_label),
            confirm = stringResource(R.string.plugin_shares_publish_confirm),
            help = stringResource(R.string.plugin_shares_publish_help),
            onDismiss = { publishing = false },
            onSubmit = { path ->
                // A null remote id is the protocol's "create": the server mints the id and answers
                // with it. Updating an existing share does not go through this sheet, so this page
                // can never silently republish over a share the user did not pick.
                onEvent(AppEvent.SavePluginShare(path, null))
                publishing = false
            },
        )
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
}

/**
 * The account's published shares.
 *
 * Deleting a share belongs on *this* card and nowhere else: a share and the plugin it was copied
 * from are two different objects, so putting the delete on the plugin catalog page would aim a
 * destructive action at the row that is not the thing being destroyed.
 */
@Composable
private fun SharesCard(
    shares: List<PluginShareEntry>,
    onEvent: (AppEvent) -> Unit,
    onUpdateTargets: (PluginShareEntry) -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }

    SectionCard(
        title = stringResource(R.string.plugin_shares_shares_title),
        icon = MiuixIcons.Link,
        trailing = shares.size.toString(),
    ) {
        if (shares.isEmpty()) {
            EmptyState(
                icon = MiuixIcons.Link,
                title = stringResource(R.string.plugin_shares_shares_empty),
                detail = stringResource(R.string.plugin_shares_shares_empty_detail),
            )
        }
        shares.forEachIndexed { index, share ->
            if (index > 0) CodexDivider()
            PluginShareRow(
                share = share,
                expanded = selected == share.remotePluginId,
                onToggle = {
                    selected = if (selected == share.remotePluginId) null else share.remotePluginId
                },
                onUpdateTargets = { onUpdateTargets(share) },
                onCheckout = { share.remotePluginId?.let { onEvent(AppEvent.CheckoutPluginShare(it)) } },
                onDelete = { share.remotePluginId?.let { onEvent(AppEvent.DeletePluginShare(it)) } },
            )
        }
    }
}

/**
 * A share's remote id, or `null` for one the server has not published under an id of its own.
 *
 * The wire attaches the sharing context to the plugin summary rather than to the list entry, so the
 * three projections below are how this page reads what the server sent without inventing fields.
 */
private val PluginShareEntry.remotePluginId: String? get() = plugin.remotePluginId

private val PluginShareEntry.discoverability: PluginShareDiscoverability
    get() = plugin.shareContext?.discoverability ?: PluginShareDiscoverability.Private

private val PluginShareEntry.principals: List<PluginSharePrincipal>
    get() = plugin.shareContext?.sharePrincipals.orEmpty()

/**
 * Parse one target token.
 *
 * `type:id` is the protocol's own addressing; a bare id is read as a user, which is what a row of
 * plain account names has always meant, and `type:id:role` carries an explicit role.
 */
private fun parseShareTarget(token: String): PluginShareTarget? {
    if (token.isEmpty()) return null
    val parts = token.split(':', limit = 3)
    return when (parts.size) {
        1 -> PluginShareTarget(principalType = "user", principalId = parts[0])
        2 -> PluginShareTarget(principalType = parts[0], principalId = parts[1])
        else -> PluginShareTarget(principalType = parts[0], principalId = parts[1], role = parts[2])
    }
}

/**
 * One published share, expanding into the three actions a share has.
 *
 * **A share is the published copy, not the plugin.** Saving a share uploads a copy of a local
 * plugin under a remote id, and from that moment the two are separate objects: deleting the share
 * takes the account's copy away and leaves the local plugin, its checkout and its files exactly
 * where they were. That is why the delete is labelled "delete share" rather than "delete plugin",
 * and why it sits on its own line below the two safe actions — the one action here that destroys
 * something the user cannot get back from this page must not be the neighbour of the two they tap
 * all day.
 *
 * Tapping the row expands rather than opens a sheet, following `ProjectRow`: three actions, all one
 * tap deep, and a sheet would hide the list the user is choosing between while they choose.
 */
@Composable
private fun PluginShareRow(
    share: PluginShareEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdateTargets: () -> Unit,
    onCheckout: () -> Unit,
    onDelete: () -> Unit,
) {
    val discoverability = discoverabilityLabel(share.discoverability)
    val targets = share.principals.joinToString(", ") { it.principalId }
    Column(modifier = Modifier.fillMaxWidth()) {
        ActionRow(
            title = share.plugin.name,
            // The label is the state and the targets are its detail: "workspace · team-a, team-b"
            // answers both who can see the share and who it was aimed at, which a user checking
            // whether a share went to the right place needs together.
            subtitle = if (targets.isEmpty()) {
                discoverability
            } else {
                stringResource(R.string.plugin_shares_row_targets, discoverability, targets)
            },
            trailing = share.remotePluginId,
            onClick = onToggle,
        )
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiConsts.Space4)
                    .padding(bottom = UiConsts.Space8),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6)) {
                    CodexButton(
                        text = stringResource(R.string.plugin_shares_update_targets),
                        onClick = onUpdateTargets,
                        role = ButtonRole.Secondary,
                        size = CodexButtonSize.Compact,
                    )
                    CodexButton(
                        text = stringResource(R.string.plugin_shares_checkout),
                        onClick = onCheckout,
                        role = ButtonRole.Secondary,
                        size = CodexButtonSize.Compact,
                    )
                }
                Row {
                    Spacer(Modifier.weight(1f))
                    CodexButton(
                        text = stringResource(R.string.plugin_shares_delete),
                        onClick = onDelete,
                        role = ButtonRole.Destructive,
                        size = CodexButtonSize.Compact,
                    )
                }
            }
        }
    }
}

/**
 * Editing one share's targets.
 *
 * One comma-separated field rather than a row per target: `plugin/share/updateTargets` takes the
 * whole list in a single call, so a form whose fields could be added and removed would send the
 * same list with more taps and a half-edited state in between. The list is trimmed and blank
 * entries are dropped on submit because a trailing comma is what a comma-separated field always
 * ends up with.
 */
@Composable
private fun ShareTargetsSheet(
    share: PluginShareEntry,
    onDismiss: () -> Unit,
    onSubmit: (PluginShareDiscoverability, List<PluginShareTarget>) -> Unit,
) {
    FormSheet(
        title = stringResource(R.string.plugin_shares_targets_title),
        subtitle = share.plugin.name,
        fields = listOf(
            FormField(
                key = "targets",
                label = stringResource(R.string.plugin_shares_targets_label),
                placeholder = stringResource(R.string.plugin_shares_targets_placeholder),
                initial = share.principals.joinToString(", ") { "${it.principalType}:${it.principalId}" },
                // An empty target list is a legal share — it is what a private one has — so the
                // field is not required, and clearing it is how a share is narrowed back to nobody.
                required = false,
                help = stringResource(R.string.plugin_shares_targets_help),
            ),
        ),
        confirmLabel = stringResource(R.string.plugin_shares_targets_confirm),
        onDismiss = onDismiss,
        onSubmit = { values ->
            onSubmit(
                share.discoverability,
                values["targets"]
                    .orEmpty()
                    .split(',')
                    .mapNotNull { parseShareTarget(it.trim()) },
            )
        },
    )
}

/**
 * Publishing a local plugin.
 *
 * The sheet asks for a path and nothing else. `PluginShareSaveParams` also carries targets and
 * discoverability, but a share created without them is a private one, which is the safer default:
 * publishing is the step that copies something off the machine, and widening who can see it should
 * be a second, deliberate edit from the new row.
 */
@Composable
private fun PublishCard(onPublish: () -> Unit) {
    SectionCard(
        title = stringResource(R.string.plugin_shares_publish_title),
        icon = MiuixIcons.UploadCloud,
    ) {
        ActionRow(
            title = stringResource(R.string.plugin_shares_publish_row),
            subtitle = stringResource(R.string.plugin_shares_publish_row_detail),
            icon = MiuixIcons.UploadCloud,
            onClick = onPublish,
        )
    }
}

/**
 * Every marketplace the account can install from.
 *
 * The card carries the two marketplace actions the protocol addresses globally — add by source,
 * and upgrade every marketplace — while a single marketplace's own action hides behind its row.
 * Splitting them that way keeps the destructive and the broad actions off rows the user scans.
 */
@Composable
internal fun MarketplacesCard(
    marketplaces: List<MarketplaceEntry>,
    onEvent: (AppEvent) -> Unit,
    onAdd: () -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }

    SectionCard(
        title = stringResource(R.string.plugin_shares_marketplaces_title),
        icon = MiuixIcons.Store,
        trailing = marketplaces.size.toString(),
    ) {
        // `SectionCard`'s header carries a string, not a slot, so the add affordance is the card's
        // first body row: still the thing the eye lands on first, and a row can name what it adds —
        // a bare plus in a header could not.
        ActionRow(
            title = stringResource(R.string.plugin_shares_marketplace_add),
            subtitle = stringResource(R.string.plugin_shares_marketplace_add_detail),
            icon = MiuixIcons.Add,
            onClick = onAdd,
        )
        if (marketplaces.isEmpty()) {
            EmptyState(
                icon = MiuixIcons.Store,
                title = stringResource(R.string.plugin_shares_marketplaces_empty),
                detail = stringResource(R.string.plugin_shares_marketplaces_empty_detail),
            )
        } else {
            CodexDivider()
            marketplaces.forEachIndexed { index, marketplace ->
                if (index > 0) CodexDivider()
                MarketplaceRow(
                    marketplace = marketplace,
                    expanded = selected == marketplace.name,
                    onToggle = {
                        selected = if (selected == marketplace.name) null else marketplace.name
                    },
                    onRemove = { onEvent(AppEvent.RemoveMarketplace(marketplace.name)) },
                )
            }
            Spacer(Modifier.height(UiConsts.Space8))
            UpgradeAllButton(onEvent = onEvent)
        }
    }
}

/**
 * One marketplace, expanding into the one action it has.
 *
 * Removing is destructive and takes the marketplace's plugin records with it, so it stays behind an
 * expansion instead of being printed on every row the list draws — a list of marketplaces is read
 * far more often than it is edited.
 */
@Composable
private fun MarketplaceRow(
    marketplace: MarketplaceEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    // The source is what a marketplace *is*; a description is a courtesy some catalogs fill in and
    // others do not, so it is only ever the fallback.
    val source = marketplace.path
        .ifBlank { marketplace.description }
        .takeIf { it.isNotBlank() }
    Column(modifier = Modifier.fillMaxWidth()) {
        ActionRow(
            title = marketplace.name,
            subtitle = source,
            // A marketplace advertises a count of its own, but one discovered locally can report
            // zero while still carrying rows; "0 plugins" over a list of them reads as a bug.
            trailing = stringResource(
                R.string.plugin_shares_marketplace_plugin_count,
                maxOf(marketplace.pluginCount, marketplace.plugins.size),
            ),
            onClick = onToggle,
        )
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiConsts.Space4)
                    .padding(bottom = UiConsts.Space8),
            ) {
                Spacer(Modifier.weight(1f))
                CodexButton(
                    text = stringResource(R.string.plugin_shares_marketplace_remove),
                    onClick = onRemove,
                    role = ButtonRole.Destructive,
                    size = CodexButtonSize.Compact,
                )
            }
        }
    }
}

/**
 * Adding a marketplace by source.
 *
 * `MarketplaceAddParams` calls the two values `source` and `refName`: the source is anything the
 * server can clone from, and the ref is the branch, tag or commit to check out. Only the source is
 * required — a marketplace added without a ref follows the source's own default, and every source
 * has one — and a blank ref becomes `null` rather than an empty string, because that is the
 * protocol's spelling of "not specified".
 */
@Composable
internal fun MarketplaceFormSheet(
    onDismiss: () -> Unit,
    onSubmit: (source: String, ref: String?) -> Unit,
) {
    FormSheet(
        title = stringResource(R.string.plugin_shares_marketplace_add),
        fields = listOf(
            FormField(
                key = "source",
                label = stringResource(R.string.plugin_shares_marketplace_source),
                placeholder = stringResource(R.string.plugin_shares_marketplace_source_placeholder),
                // A source is usually a git url but may be a path, and the uri keyboard is the one
                // that puts a slash and a colon within reach of the thumb for both.
                keyboardType = KeyboardType.Uri,
            ),
            FormField(
                key = "ref",
                label = stringResource(R.string.plugin_shares_marketplace_ref),
                placeholder = stringResource(R.string.plugin_shares_marketplace_ref_placeholder),
                required = false,
                help = stringResource(R.string.plugin_shares_marketplace_ref_help),
            ),
        ),
        confirmLabel = stringResource(R.string.plugin_shares_marketplace_add_confirm),
        onDismiss = onDismiss,
        onSubmit = { values ->
            onSubmit(
                values["source"].orEmpty().trim(),
                values["ref"].orEmpty().trim().ifEmpty { null },
            )
        },
    )
}

/**
 * Upgrading every marketplace at once.
 *
 * The button emits a `null` name on purpose: `marketplace/upgrade` takes an optional marketplace
 * name and `null` means *every* marketplace, which is the protocol's own spelling of "all" rather
 * than a shortcut invented here. It is secondary because an upgrade re-reads what the marketplaces
 * already track — it adds nothing the user did not already ask for.
 */
@Composable
private fun UpgradeAllButton(onEvent: (AppEvent) -> Unit) {
    CodexButton(
        text = stringResource(R.string.plugin_shares_upgrade_all),
        onClick = { onEvent(AppEvent.UpgradeMarketplace(null)) },
        modifier = Modifier.fillMaxWidth(),
        role = ButtonRole.Secondary,
    )
}

/**
 * Rewriting the catalog from what is on disk.
 *
 * `plugin/reconcile` re-resolves every installed plugin against its marketplace, so its result can
 * name entries the user never deleted by hand: a marketplace that failed to load, a checkout that
 * moved, or a plugin whose manifest no longer parses all drop out of the catalog when this runs.
 * That is exactly why the button states what it does and why the run is reported back — a catalog
 * that quietly loses rows is indistinguishable from one that was always that size. It is not styled
 * as destructive, because the state it removes is already gone on disk; this run is how the app
 * finds out.
 */
@Composable
private fun ReconcileButton(onEvent: (AppEvent) -> Unit) {
    CodexButton(
        text = stringResource(R.string.plugin_shares_reconcile),
        onClick = { onEvent(AppEvent.ReconcilePlugins) },
        modifier = Modifier.fillMaxWidth(),
        role = ButtonRole.Secondary,
    )
}

/**
 * What the last reconcile or upgrade changed.
 *
 * `plugin/reconcile` and `marketplace/upgrade` answer with a list of changed entries and nothing
 * else: no notification follows either one, and the catalog reload they trigger looks identical
 * whether they changed twenty entries or none. Without this card a run whose result the user wanted
 * to read is indistinguishable from a button that did nothing.
 *
 * It is composed only while there is something to report, so it has no empty state of its own — an
 * empty result card would claim the last run changed nothing when no run may have happened at all.
 */
@Composable
private fun LastRunCard(
    reconciledPlugins: List<String>,
    upgradedMarketplaces: List<String>,
) {
    SectionCard(
        title = stringResource(R.string.plugin_shares_last_run_title),
        icon = MiuixIcons.Info,
        trailing = (reconciledPlugins.size + upgradedMarketplaces.size).toString(),
    ) {
        if (reconciledPlugins.isNotEmpty()) {
            ValueRow(
                label = stringResource(R.string.plugin_shares_last_run_reconciled),
                value = reconciledPlugins.joinToString(", "),
            )
        }
        if (reconciledPlugins.isNotEmpty() && upgradedMarketplaces.isNotEmpty()) {
            CodexDivider()
        }
        if (upgradedMarketplaces.isNotEmpty()) {
            ValueRow(
                label = stringResource(R.string.plugin_shares_last_run_upgraded),
                value = upgradedMarketplaces.joinToString(", "),
            )
        }
    }
}

/**
 * What one discoverability value reads as.
 *
 * `ui_consts.kt` owns the `label()` extensions for the protocol enums, but this type is rendered by
 * this page alone, so its wording lives beside its only caller — a shared mapping would be a second
 * place to keep in step with a screen that file knows nothing about.
 */
@Composable
private fun discoverabilityLabel(discoverability: PluginShareDiscoverability): String =
    stringResource(
        when (discoverability) {
            PluginShareDiscoverability.Private -> R.string.plugin_shares_discoverability_private
            PluginShareDiscoverability.Unlisted -> R.string.plugin_shares_discoverability_unlisted
            PluginShareDiscoverability.Listed -> R.string.plugin_shares_discoverability_listed
        },
    )
