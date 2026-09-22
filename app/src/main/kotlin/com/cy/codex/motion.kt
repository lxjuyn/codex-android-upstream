package com.cy.codex

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Motion constants.
 *
 * Mirrors `codex-rs/tui/src/motion.rs` and `system_motion.rs`: the TUI keeps every animated
 * transition in one place so panels, drawers and streaming text accelerate the same way. The
 * Android surfaces read the same set instead of each picking its own curve.
 *
 * The durations are a short ladder — 120 / 160 / 200 / 240 / 300 — and every animated property in
 * the app has to pick a rung. A one-off `tween(180)` next to a `tween(160)` is how two panels that
 * are supposed to move together end up 20ms apart, which reads as one of them lagging.
 */
object Motion {
    /**
     * Client "reduce motion" preference, set from [com.cy.codex.theme.Appearance].
     *
     * A global rather than a parameter because every call site reads a spec from this object during
     * composition: when the preference flips, the theme recomposes the tree and each new spec is
     * built without motion. `snap` is a completed animation, not a short one, so nothing lags by a
     * frame when the toggle is on.
     */
    var reduced: Boolean = false

    private fun <T> motion(spec: androidx.compose.animation.core.FiniteAnimationSpec<T>) =
        if (reduced) androidx.compose.animation.core.snap() else spec

    /** Panels and drawers: critically damped, medium-low stiffness, no overshoot. */
    val Panel: androidx.compose.animation.core.FiniteAnimationSpec<Float>
        get() = motion(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))

    /** Same spring for `Dp` targets (widths, heights, corner radii). */
    val PanelDp: androidx.compose.animation.core.FiniteAnimationSpec<Dp>
        get() = motion(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))

    /** Selection and press feedback: fast, no bounce. */
    val Press: androidx.compose.animation.core.FiniteAnimationSpec<Float>
        get() = motion(tween(durationMillis = PressMs))

    /** Color changes on chips, buttons and status pills. */
    val Tint: androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.graphics.Color>
        get() = motion(tween(durationMillis = TintMs))

    /** Chevrons and disclosure arrows. */
    val Disclosure: androidx.compose.animation.core.FiniteAnimationSpec<Float>
        get() = motion(tween(durationMillis = DisclosureMs))

    /** List content that fades in behind a growing panel. */
    val ListFadeIn: androidx.compose.animation.core.FiniteAnimationSpec<Float>
        get() = motion(tween(durationMillis = ContentEnterMs, delayMillis = 140))

    /** List content on the way out; no delay, the panel is already shrinking. */
    val ListFadeOut: androidx.compose.animation.core.FiniteAnimationSpec<Float>
        get() = motion(tween(durationMillis = ExitMs))

    /** Anything entering the screen: a sheet, a card, a row that expands. */
    val EnterEasing: Easing = LinearOutSlowInEasing

    /** Anything leaving it. Leaves are shorter than entrances, so a dismissal feels immediate. */
    val ExitEasing: Easing = FastOutSlowInEasing

    /** A `tween` that enters, so no call site has to spell out the pair. */
    val Enter: androidx.compose.animation.core.FiniteAnimationSpec<Float>
        get() = motion(tween(durationMillis = EnterMs, easing = EnterEasing))

    /** A `tween` that leaves. */
    val Exit: androidx.compose.animation.core.FiniteAnimationSpec<Float>
        get() = motion(tween(durationMillis = ExitMs, easing = ExitEasing))

    /** Streaming text: the cadence the transcript commits buffered deltas at. */
    const val StreamCommitIntervalMs = 33L

    /** Blinking caret/spinner period. */
    const val SpinnerPeriodMs = 900L

    /** Press and selection feedback. */
    const val PressMs = 120

    /** Color and opacity transitions. */
    const val TintMs = 160

    /** Disclosure arrows and expanding rows. */
    const val DisclosureMs = 200

    /**
     * Anything entering the screen that is not a sheet: a card, a chip row, a popup.
     *
     * One value for all of them. The panel, the diff card and the queued banner used to enter at
     * 160, 170 and 180ms — three durations nobody can tell apart on purpose, which means they were
     * three accidents.
     */
    const val EnterMs = 160

    /** Content that appears inside a panel once the panel's own motion has settled. */
    const val ContentEnterMs = 220

    /** Everything leaving: an exit that is not shorter than its entrance reads as a hang. */
    const val ExitMs = 120

}

/**
 * Shared dimensions.
 *
 * Mirrors `codex-rs/tui/src/ui_consts.rs`.
 *
 * Corners are a ladder, not a free parameter: chrome (drawers, panels, sheets) is [CornerChrome],
 * a card inside one is [CornerCard], a row inside a card is [CornerRow], and a control is
 * [CornerControl]. Nothing in the app should introduce a fifth value.
 */
object UiConsts {
    /** Margin every floating widget keeps from the screen edges. */
    val ScreenMargin = 14.dp

    /** Height of the collapsed composer: 44dp button row plus its 8dp vertical padding. */
    val PromptBarHeight = 60.dp

    /**
     * Corner ladder.
     *
     * 28 / 20 / 16 / 12 / 8: chrome, card, row, control, chip. Every rounded corner in the app
     * resolves to one of these, which is what keeps a dialog, the settings cards and the composer
     * from each inventing their own radius.
     */
    val CornerChrome = 28.dp
    val CornerCard = 20.dp
    val CornerRow = 16.dp
    val CornerControl = 12.dp
    val CornerChip = 8.dp

    /** Corner radius of the two floating drawers, and of a pushed page's top edge. */
    val DrawerCorner = CornerChrome

    /** Corner radius of the collapsed drawer chip and the status button: they are the same chip. */
    val ChipCorner = 18.dp

    /** Edge of the two top-corner chips. They face each other across the transcript, so they match. */
    val ChipSize = 48.dp

    /** Icon inside a top-corner chip. */
    val ChipIcon = 22.dp

    /** Leading icon of a drawer row, and the marker column of a markdown list. */
    val IconLeading = 22.dp

    /** Icon inside a compact header button. */
    val IconHeaderSmall = 30.dp

    /** The square an empty-state or card header puts behind its icon. */
    val IconBoxLarge = 64.dp

    /** Indent that lines a child row up under its parent's icon. */
    val RowIndent = 26.dp

    /**
     * How far a pushed page stops short of the top edge.
     *
     * Two reasons it is not zero: a page that reaches the top edge stops reading as a page over the
     * transcript and starts reading as a screen swap, and the top band is where the system's own
     * edge gestures live — a page flush against it fights the notification shade for the same swipe.
     */
    val SheetTopGap = 58.dp

    /** Corner radius of the status card, and of every other floating panel. */
    val PanelCorner = 30.dp

    /** The rail beside a quoted prompt: thinner than any other corner in the app. */
    val CornerBar = 2.dp

    /** Corner radius of a row inside a card. */
    val RowCorner = CornerRow

    /** Corner radius shared by every section card. */
    val SectionCorner = CornerCard

    /** Preferred width of the sidebar drawer. */
    val SidebarWidth = 252.dp

    /** Ceiling on the drawer's width, so a very wide window does not get a very wide drawer. */
    val SidebarWidthCap = 252.dp

    /**
     * Window width from which the transcript gets its own column beside the drawer.
     *
     * Below it there is no room for two columns and the drawer goes back over the text; above it the
     * text is laid out once at `window - drawer` and only ever translated.
     */
    val WideContentBreakpoint = 720.dp

    /**
     * Space between the drawer's outer edge and the transcript column.
     *
     * Without it the column's left edge lands on the drawer's right edge and the first 18dp gutter is
     * all the separation there is — the text reads as if it were tucked under the drawer.
     */
    val ContentGap = 20.dp

    /** The transcript column never gets narrower than this, whatever the drawer asks for. */
    val MinContentWidth = 320.dp

    /** Space kept between the transcript's first row and the top edge. */
    val TranscriptTopInset = 96.dp

    /** Space kept under the transcript's last row, on top of the composer's own height. */
    val TranscriptBottomInset = 24.dp

    /**
     * Side margin of every surface that slides up over the content: a modal sheet and a pushed page.
     *
     * A sheet is the only thing in the app that covers the screen edge to edge with content of its
     * own, so its gutter is the one the eye reads as a frame rather than as padding — and a frame
     * that is a fixed number of dp stops working across screen sizes. Held at 20dp it was a fifth of
     * a phone's width on a tablet and nothing at all on the phone; the panel simply looked wide
     * rather than inset. So the margin is a share of the window, clamped so it stays sane at both
     * ends: about 43dp on a 776dp phone and 64dp on a 1152dp tablet.
     */
    const val SheetSideMarginFraction = 0.055f
    val SheetSideMarginMin = 40.dp
    val SheetSideMarginMax = 72.dp

    /**
     * Ceiling on the width of a surface that slides up over the content — a pushed page and a modal
     * sheet alike.
     *
     * Deliberately far above any phone and above the widest tablet column this app is read at: past
     * the point where [SheetSideMarginFraction] has already produced a generous gutter, a second
     * ceiling would take over the framing on a large window and make the margin a function of the
     * screen rather than a decision. Its job is only to stop a 2000dp desktop window stretching a
     * paragraph across the room.
     */
    val SheetMaxWidth = 1600.dp

    /** Internal gutter of every sheet: the padding its header and its rows share. */
    val SheetPadding = 18.dp

    /**
     * How dark the layer behind a modal goes.
     *
     * One value for a sheet and for the page stack behind it, so opening a picker over a pushed
     * page does not darken the app in two steps.
     */
    const val ScrimAlpha = 0.36f

    /**
     * Preferred width of the status card body.
     *
     * Deliberately narrow. The card is a status *glance*, not a dashboard: at 540dp it covered half
     * the transcript, and the two things it exists to answer — is the turn running, and how full is
     * the context — are readable at this width without the transcript disappearing behind it.
     */
    val StatusPanelWidth = 372.dp

    /** Preferred width of the diff card that opens beside the status card. */
    val DiffPaneWidth = 520.dp

    /** The diff card never gets less room than this. */
    val MinDiffPaneWidth = 320.dp

    /** Gap between the status card and the diff card beside it. */
    val PanelGap = 8.dp

    /** Transcript horizontal gutter. */
    val TranscriptGutter = 18.dp

    /** Maximum width of a user bubble in the transcript. */
    val UserBubbleMaxWidth = 330.dp

    /**
     * Spacing steps.
     *
     * The pushed pages are dense — most of their gaps and paddings are under 12dp — so this scale is
     * 1dp-granular instead of the usual 4dp grid. Every inline gap in a page reads as a step.
     */
    val Space1 = 1.dp
    val Space2 = 2.dp
    val Space3 = 3.dp
    val Space4 = 4.dp
    val Space5 = 5.dp
    val Space6 = 6.dp
    val Space7 = 7.dp
    val Space8 = 8.dp
    val Space9 = 9.dp
    val Space10 = 10.dp
    val Space11 = 11.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space20 = 20.dp
    val Space24 = 24.dp

    /** Bottom padding under the last card of a pushed page. */
    val PageBottomInset = 28.dp

    /** Gap between the cards of a pushed page. */
    val SectionGap = Space10

    /** Edge of the square icon buttons in a page header (back, refresh). */
    val IconButtonSize = 34.dp

    /** Icon inside a page header's icon button. */
    val IconHeader = 18.dp

    /** Icon inside the account page's refresh button. */
    val IconRefresh = 17.dp

    /** Leading icon of a list row. */
    val IconRow = 16.dp

    /** Icon inside a button or a row's inline action. */
    val IconInline = 15.dp

    /** Trailing chevron on a navigating row. */
    val IconChevron = 14.dp

    /** Leading icon of a miuix preference row. */
    val IconPreference = 20.dp

    /** Status dot in front of a server, thread or agent name. */
    val DotSize = 8.dp

    /** Corner radius of an inline label chip. */
    val BadgeCorner = CornerChip

    /** Tint of an inline label chip over the row it sits in. */
    const val BadgeTintAlpha = 0.16f

    /** Thickness of the hairline dividers inside a card. */
    val DividerThickness = 0.7.dp

    /** Thickness of a control's outline, and of the two floating drawers' edge. */
    val OutlineThickness = 0.7.dp

    /** Height of the thin progress meters. */
    val ProgressHeight = 7.dp

    /** Height of the daily token chart on the account page. */
    val UsageChartHeight = 118.dp

    /** Width of the timeline rule beside a subagent's task and activity rows. */
    val TimelineBarWidth = 2.5.dp

    /**
     * The floating sheets in `ui/overlays`.
     *
     * They are one shell — scrim, handle, header, scrolling body — so their corner radius, handle
     * and elevation are named once instead of being repeated at every call site.
     */
    val SheetCorner = CornerChrome
    val SheetElevation = 24.dp
    val PanelElevation = 18.dp

    /** The fraction of the window a sheet may cover before it has to scroll internally. */
    const val SheetHeightFraction = 0.72f

    /** The taller fraction the agent dashboard uses: its cards are read, not glanced at. */
    const val SheetHeightFractionTall = 0.9f

    /**
     * Button ladder.
     *
     * Two heights, not five: [ButtonHeight] for a decision — a dialog footer, a sheet's confirm, a
     * form's submit — and [ButtonHeightCompact] for a control that sits inline in a row. The corner
     * is fully rounded at both heights, so every button in the app is the same pill.
     */
    val ButtonHeight = 44.dp
    val ButtonHeightCompact = 34.dp
    val ButtonPaddingHorizontal = 20.dp
    val ButtonPaddingHorizontalCompact = 14.dp

    /** Fully rounded pill corner, as a percentage of the shorter side. */
    const val PillCorner = 50

    /** Minimum width of a dialog footer's primary action. */
    val ButtonMinWidth = 96.dp

    /** Minimum width of the confirm button at the bottom of the workspace picker. */
    val ActionButtonMinWidth = ButtonMinWidth

    /**
     * Geometry of the approval dialog.
     *
     * The dialog is the one surface the user cannot scroll past, so its rhythm is fixed here rather
     * than at the call site: one gap between two blocks of a body, a wider one between the body and
     * the footer, and a cap on the scrolling part.
     */
    val DialogFieldGap = 16.dp
    val DialogHeaderGap = 18.dp
    val DialogFooterGap = 20.dp

    /** The square that holds a dialog header's icon. */
    val DialogIconBox = 38.dp

    /** Share of the window the scrolling part of a dialog may take, and the floor under it. */
    const val DialogBodyMaxHeightFraction = 0.42f
    val DialogBodyMinHeight = 180.dp

    /** Lines of one file's diff a patch dialog shows before it clips the body. */
    const val PatchBodyMaxLines = 14

    /** Files a patch dialog lists before it folds the rest behind a "more files" affordance. */
    const val PatchPreviewFiles = 4

    /** Threads the cross-thread approval notice lists before it stops; upstream caps at three. */
    const val ApprovalNoticeThreads = 3

    /** Review details the aggregated auto-review notice shows before "+N more". */
    const val ApprovalNoticeReviews = 3

    /** Height of the thin usage meters inside an overlay row. */
    val ProgressHeightRow = 4.dp

    /** Width of the compact progress bar beside the plan chip. */
    val ProgressWidthCompact = 44.dp

    /** Node of the plan timeline: the circle drawn on the rail. */
    val PlanNodeSize = 14.dp
    val PlanNodeStroke = 1.6.dp

    /** Tick that marks the selected row of a sheet. */
    val IconCheck = 14.dp

    /** Square button that collapses an inline panel. */
    val IconButtonCompact = 26.dp

    /** Width reserved for the token count at the end of a usage bar. */
    val TokenValueWidth = 52.dp

    /** Ordinal badge in front of a queued message. */
    val QueueIndexSize = 18.dp

    /** Spacing steps the sheets need on top of the 1dp scale. */
    val Space0 = 0.dp
    val Space14 = 14.dp
}

/**
 * Type ramp.
 *
 * The pages used to spell font sizes and line heights out at every call site, which is how the same
 * row title ended up 13.5sp in one file and 13sp in another. Naming the steps by the job they do
 * keeps them together; the line height is always the second half of a step, except where a page
 * sets no leading at all and lets [UiType.LineRatio] derive it.
 *
 * Nine sizes, 10sp to 20sp: every text in the app is one of them. If a new piece of text seems to
 * need a tenth, it is almost always one of the existing nine being used at the wrong job.
 */
object UiType {
    /** Chart ticks. */
    val Tick = 10.sp

    /** Text inside a badge that only ever holds one or two words. */
    val Badge = 10.sp
    val BadgeLine = 13.sp

    /** Text inside a status chip, and every other inline pill label. */
    val Chip = 11.sp
    val ChipLine = 14.sp

    /** Ids, paths and file sizes under a row. */
    val Caption = 11.sp
    val CaptionLine = 14.sp

    /** Footnote under a card's content. */
    val Footnote = 11.sp
    val FootnoteLine = 15.sp

    /** A monospace command line. */
    val Code = 11.5.sp
    val CodeLine = 16.sp

    /** Row summary under a row title. */
    val Meta = 12.sp
    val MetaLine = 16.sp

    /** Monospace value and breadcrumb: [Meta]'s size, one step more leading. */
    val Value = 12.sp
    val ValueLine = 16.sp

    /** Button and inline action labels. */
    val Action = 14.sp
    val ActionLine = 19.sp

    /** Detail line under an activity row. */
    val Detail = 13.sp
    val DetailLine = 18.sp

    /** Default body text. */
    val Body = 13.sp
    val BodyLine = 19.sp

    /** Sub-section title inside a card. */
    val Subtitle = 13.sp
    val SubtitleLine = 18.sp

    /** Row title. */
    val RowTitle = 13.sp
    val RowTitleLine = 18.sp

    /** A quoted prompt body. */
    val Quote = 14.sp
    val QuoteLine = 21.sp

    /** Title of a floating sheet, and of a modal; `title4` in the miuix ramp. */
    val SheetTitle = 18.sp
    val SheetTitleLine = 24.sp

    /** Detail line inside a sheet row: what the row says under its title. */
    val RowDetail = 12.sp
    val RowDetailLine = 16.sp

    /** Title of a roster or overview card inside a sheet. */
    val CardTitle = 15.sp
    val CardTitleLine = 20.sp

    /** Title of a row inside a sheet. */
    val SheetRowTitle = 14.sp
    val SheetRowTitleLine = 19.sp

    /** Objective body shown in the goal sheet. */
    val SheetBody = 14.sp
    val SheetBodyLine = 20.sp

    /**
     * The conversation itself.
     *
     * The transcript, the composer and the status card's headline are the three biggest texts in
     * the app, and they used to be 16, 17 and 16sp with two different leadings. They are one step
     * apart so the transcript reads as the body of the app and the composer reads as the thing you
     * are typing into.
     */
    val Message = 15.sp
    val MessageLine = 22.sp

    /** The composer's input line and hint: `main` of the miuix ramp, one step over the transcript. */
    val Composer = 17.sp
    val ComposerLine = 24.sp

    /** Headline of a session or a status card; `title3` in the miuix ramp. */
    val Title = 20.sp
    val TitleLine = 26.sp

    /** The one text bigger than a title, on an empty transcript. */
    val Display = 28.sp
    val DisplayLine = 34.sp

    /** Title of a modal: the one line that says what is being asked. */
    val DialogTitle = 18.sp
    val DialogTitleLine = 24.sp

    /** The line under a modal's title: why it is being asked. */
    val DialogSummary = 12.5.sp
    val DialogSummaryLine = 17.sp

    /** The markdown renderer steps a heading up by these two amounts, not by a fixed size. */
    val HeadingSizeStep = 2.sp
    val HeadingLeadingStep = 4.sp

    /** Leading the page text helpers apply when no line height is given. */
    const val LineRatio = 1.36f
}
