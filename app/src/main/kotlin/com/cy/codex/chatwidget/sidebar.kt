package com.cy.codex.chatwidget

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.ExpandBar
import com.cy.codex.Motion
import com.cy.codex.pressableRow
import com.cy.codex.raisedSurface
import com.cy.codex.SquircleShape
import com.cy.codex.UiConsts
import com.cy.codex.statusDotColor
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The floating navigation drawer.
 *
 * Mirrors `codex-rs/tui/src/chatwidget/side.rs`: one bar that holds the way in to a new workspace,
 * the project groups and the sessions inside them. The list is built from `thread/list` through
 * [SidebarModel], so the drawer shows the same threads the server knows about.
 *
 * Collapsed it is a chip the size of the status button in the opposite corner; expanded it ends on
 * the same line as the composer. The two are the app's only bottom-anchored pieces, and a drawer
 * that stopped anywhere else — short of the composer, or flush with the screen edge — read as a
 * mistake rather than as a margin.
 *
 * @param maxPanelHeight expanded height, measured so the bottom edge lands on the composer's line.
 */
@Composable
fun SidebarPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<SidebarEntry>,
    onAction: (SidebarEntry) -> Unit,
    projects: List<SidebarProject>,
    projectsCollapsed: Boolean,
    onToggleProjects: () -> Unit,
    expandedProjects: Set<String>,
    onToggleProject: (String) -> Unit,
    selectedSessionId: String?,
    onSessionSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
    panelWidth: Dp,
    collapsedWidth: Dp,
    collapsedHeight: Dp,
    maxPanelHeight: Dp,
    expandedElevation: Dp = 18.dp,
    collapsedElevation: Dp = 12.dp,
    listPadding: PaddingValues = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
    listItemGap: Dp = 2.dp,
    sectionGap: Dp = 6.dp,
    listBottomGap: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val sizeSpec = Motion.PanelDp
    // The silhouette is one continuous shape the whole way: a 48dp chip and a 700dp drawer share the
    // same squircle, and only its radius moves. Swapping between two shapes at the halfway point made
    // the corners jump while the panel was still animating.
    val corner by animateDpAsState(
        targetValue = if (expanded) UiConsts.DrawerCorner else UiConsts.ChipCorner,
        animationSpec = sizeSpec,
        label = "sidebarCorner",
    )
    val shape = remember(corner) { SquircleShape(corner) }
    val width by animateDpAsState(
        targetValue = if (expanded) panelWidth else collapsedWidth,
        animationSpec = sizeSpec,
        label = "sidebarWidth",
    )
    val height by animateDpAsState(
        targetValue = if (expanded) maxPanelHeight else collapsedHeight,
        animationSpec = sizeSpec,
        label = "sidebarHeight",
    )
    val listAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (expanded) Motion.ListFadeIn else Motion.ListFadeOut,
        label = "sidebarListAlpha",
    )
    val projectsTitle = stringResource(R.string.sidebar_projects_header)
    val items = remember(actions, projects, projectsCollapsed, expandedProjects, projectsTitle) {
        sidebarItems(actions, projects, projectsCollapsed, expandedProjects, projectsTitle)
    }
    // The list never scrolls itself: every move happens under the finger that asked for it.
    // Scrolling the selected session into view used to run while the bar was still growing — with a
    // zero-height viewport LazyColumn always answered "not visible" and the offset was clamped as
    // the viewport grew, so a tap aimed at the row being revealed landed on its neighbour.
    val listState = rememberLazyListState()

    ExpandBar(
        width = width,
        height = height,
        shape = shape,
        expanded = expanded,
        onExpandRequest = { onExpandedChange(true) },
        elevation = if (expanded) expandedElevation else collapsedElevation,
        modifier = modifier,
    ) {
        SidebarHeader(
            expanded = expanded,
            onToggle = { onExpandedChange(!expanded) },
            onOpenSettings = onOpenSettings,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .alpha(listAlpha),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(listItemGap),
        ) {
            items(items.size, key = { items[it].key }) { index ->
                when (val item = items[index]) {
                    is SidebarItem.Header -> SectionHeader(
                        title = item.title,
                        collapsed = item.collapsed,
                        onClick = onToggleProjects,
                    )

                    is SidebarItem.Gap -> Spacer(Modifier.height(sectionGap))

                    is SidebarItem.Action -> ActionRow(entry = item.entry, onClick = { onAction(item.entry) })

                    is SidebarItem.Project -> ProjectRow(
                        project = item.project,
                        expanded = item.expanded,
                        onClick = { onToggleProject(item.project.id) },
                    )

                    is SidebarItem.Session -> SessionRow(
                        session = item.session,
                        selected = item.session.id == selectedSessionId,
                        onClick = { onSessionSelected(item.session.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(listBottomGap))
    }
}

@Composable
private fun SidebarHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    headerExtraHeight: Dp = 10.dp,
    headerPadding: Dp = 5.dp,
    iconGap: Dp = 2.dp,
    titleSize: TextUnit = UiType.Composer,
    titleLineHeight: TextUnit = UiType.SheetTitleLine,
    settingsIconSize: Dp = 19.dp,
    pressInDurationMs: Int = Motion.PressMs,
    pressOutDurationMs: Int = Motion.TintMs,
    titleFadeInDurationMs: Int = Motion.DisclosureMs,
    titleFadeOutDurationMs: Int = Motion.ExitMs,
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressOverlay by animateColorAsState(
        targetValue = if (pressed) colors.onBackground.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(durationMillis = if (pressed) pressInDurationMs else pressOutDurationMs),
        label = "sidebarTogglePress",
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) titleFadeInDurationMs else titleFadeOutDurationMs,
        ),
        label = "sidebarTitleAlpha",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(UiConsts.ChipSize + headerExtraHeight)
            .padding(horizontal = headerPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(UiConsts.ChipSize)
                .clip(CircleShape)
                .then(
                    if (expanded) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onToggle,
                        )
                    } else {
                        Modifier
                    },
                )
                .background(pressOverlay, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Sidebar,
                contentDescription = if (expanded) {
                    stringResource(R.string.sidebar_collapse)
                } else {
                    stringResource(R.string.sidebar_expand)
                },
                modifier = Modifier.size(UiConsts.ChipIcon),
                tint = colors.primary,
            )
        }
        Spacer(Modifier.width(iconGap))
        Text(
            text = stringResource(R.string.sidebar_title),
            modifier = Modifier
                .weight(1f)
                .alpha(titleAlpha),
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        // Settings lives here rather than in the status card: this drawer is the app's navigation,
        // and the status card is a read-out of one session. A settings entry inside it mixed the two
        // jobs, and every other list that used to be down here is now a row inside that page.
        //
        // Only composed while the drawer is open. An alpha-0 icon is still hit-testable, and in the
        // 48dp-wide collapsed bar this button lays out exactly on top of the toggle — so tapping the
        // collapsed chip opened settings and the drawer could not be opened at all.
        if (expanded) {
            Box(modifier = Modifier.alpha(titleAlpha)) {
                IconButton(
                    onClick = onOpenSettings,
                    minWidth = UiConsts.ChipSize,
                    minHeight = UiConsts.ChipSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Settings,
                        contentDescription = stringResource(R.string.sidebar_open_settings),
                        modifier = Modifier.size(settingsIconSize),
                        tint = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    collapsed: Boolean,
    onClick: () -> Unit,
    corner: Dp = UiConsts.CornerControl,
    horizontalPadding: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
    titleSize: TextUnit = UiType.Subtitle,
    titleLineHeight: TextUnit = UiType.SheetTitle,
    chevronSize: Dp = 14.dp,
    pressInDurationMs: Int = Motion.PressMs,
    pressOutDurationMs: Int = Motion.TintMs,
    chevronDurationMs: Int = Motion.ContentEnterMs,
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressOverlay by animateColorAsState(
        targetValue = if (pressed) colors.onBackground.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(durationMillis = if (pressed) pressInDurationMs else pressOutDurationMs),
        label = "sectionPress",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) 90f else -90f,
        animationSpec = tween(durationMillis = chevronDurationMs),
        label = "sectionChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .clip(RoundedCornerShape(corner))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .background(pressOverlay, RoundedCornerShape(corner))
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            color = colors.onSurfaceVariantSummary,
        )
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = if (collapsed) {
                stringResource(R.string.sidebar_expand_section, title)
            } else {
                stringResource(R.string.sidebar_collapse_section, title)
            },
            modifier = Modifier
                .size(chevronSize)
                .graphicsLayer { rotationZ = chevronRotation },
            tint = colors.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun ActionRow(
    entry: SidebarEntry,
    onClick: () -> Unit,
    corner: Dp = UiConsts.CornerRow,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    iconSize: Dp = UiConsts.IconLeading,
    iconGap: Dp = UiConsts.Space14,
    titleSize: TextUnit = UiType.Message,
    titleLineHeight: TextUnit = UiType.ComposerLine,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(
                shape = RoundedCornerShape(corner),
                container = Color.Transparent,
                onClick = onClick,
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = colors.onSurfaceSecondary,
        )
        Spacer(Modifier.width(iconGap))
        Text(
            text = entry.title,
            modifier = Modifier.weight(1f),
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProjectRow(
    project: SidebarProject,
    expanded: Boolean,
    onClick: () -> Unit,
    corner: Dp = UiConsts.CornerRow,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    iconSize: Dp = UiConsts.IconLeading,
    iconGap: Dp = UiConsts.Space14,
    nameSize: TextUnit = UiType.Message,
    nameLineHeight: TextUnit = UiType.ComposerLine,
    pathSize: TextUnit = UiType.Chip,
    pathLineHeight: TextUnit = UiType.SheetRowTitle,
    chevronSize: Dp = 15.dp,
    chevronDurationMs: Int = Motion.DisclosureMs,
) {
    val colors = MiuixTheme.colorScheme
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = chevronDurationMs),
        label = "projectChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(
                shape = RoundedCornerShape(corner),
                container = Color.Transparent,
                onClick = onClick,
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.FolderFill,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = Color(0xFFFFC24B),
        )
        Spacer(Modifier.width(iconGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.name,
                fontSize = nameSize,
                lineHeight = nameLineHeight,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = project.path,
                fontSize = pathSize,
                lineHeight = pathLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = if (expanded) {
                stringResource(R.string.sidebar_collapse_project, project.name)
            } else {
                stringResource(R.string.sidebar_expand_project, project.name)
            },
            modifier = Modifier
                .size(chevronSize)
                .graphicsLayer { rotationZ = chevronRotation },
            tint = colors.onSurfaceVariantActions,
        )
    }
}

@Composable
private fun SessionRow(
    session: SidebarSession,
    selected: Boolean,
    onClick: () -> Unit,
    startIndent: Dp = UiConsts.RowIndent,
    corner: Dp = UiConsts.CornerRow,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    dotSize: Dp = 7.dp,
    dotGap: Dp = 12.dp,
    titleSize: TextUnit = UiType.CardTitle,
    titleLineHeight: TextUnit = UiType.Title,
    dateGap: Dp = 8.dp,
    dateSize: TextUnit = UiType.RowDetail,
    dateLineHeight: TextUnit = UiType.Message,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .pressableRow(
                shape = RoundedCornerShape(corner),
                container = if (selected) raisedSurface() else Color.Transparent,
                onClick = onClick,
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(
                    when {
                        session.running -> statusDotColor(com.cy.codex.ThreadStatusTone.Running)
                        session.archived -> colors.onSurfaceVariantSummary.copy(alpha = 0.5f)
                        else -> colors.onSurfaceVariantSummary
                    },
                ),
        )
        Spacer(Modifier.width(dotGap))
        Text(
            text = session.title,
            modifier = Modifier.weight(1f),
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) colors.primary else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(dateGap))
        Text(
            text = session.date,
            fontSize = dateSize,
            lineHeight = dateLineHeight,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

private sealed interface SidebarItem {
    val key: String

    data class Action(val entry: SidebarEntry) : SidebarItem {
        override val key: String = "action-${entry.id}"
    }

    data class Header(val title: String, val collapsed: Boolean) : SidebarItem {
        override val key: String = "header-projects"
    }

    data class Gap(val position: Int) : SidebarItem {
        override val key: String = "gap-$position"
    }

    data class Project(val project: SidebarProject, val expanded: Boolean) : SidebarItem {
        override val key: String = "project-${project.id}"
    }

    data class Session(val session: SidebarSession) : SidebarItem {
        override val key: String = "session-${session.id}"
    }
}

private fun sidebarItems(
    actions: List<SidebarEntry>,
    projects: List<SidebarProject>,
    projectsCollapsed: Boolean,
    expandedProjects: Set<String>,
    projectsTitle: String,
): List<SidebarItem> = buildList {
    actions.forEach { add(SidebarItem.Action(it)) }
    add(SidebarItem.Gap(0))
    add(SidebarItem.Header(projectsTitle, projectsCollapsed))
    if (!projectsCollapsed) {
        projects.forEach { project ->
            val expanded = project.id in expandedProjects
            add(SidebarItem.Project(project, expanded))
            if (expanded) {
                project.sessions.forEach { add(SidebarItem.Session(it)) }
            }
        }
    }
}
