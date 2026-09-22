package com.cy.codex.bottom_pane

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.MentionSuggestion
import com.cy.codex.R
import com.cy.codex.chatwidget.SlashCommand
import com.cy.codex.Motion
import com.cy.codex.SquircleShape
import com.cy.codex.UiConsts
import com.cy.codex.codeSurface
import com.cy.codex.fileName
import com.cy.codex.floatingSurface
import com.cy.codex.glassTint
import com.cy.codex.keymap.KeyAction
import com.cy.codex.keymap.KeyContext
import com.cy.codex.keymap.LocalChatKeyFocus
import com.cy.codex.keymap.LocalShortcutsHelp
import com.cy.codex.keymap.codexHardwareKeys
import com.cy.codex.parentPath
import com.cy.codex.pressableRow
import com.cy.codex.raisedSurface
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The bottom composer.
 *
 * A direct port of the prompt bar this file replaced — same stacked layout, same spring corner, same
 * glass surface — plus the three things the port of `bottom_pane/chat_composer.rs` needs on top:
 * a running turn turns the trailing button into an interrupt, queued messages are announced above
 * the field, and a leading `/` opens the slash-command popup.
 *
 * Hardware keys are read here rather than at the chat root because the popups and the caret are this
 * widget's own state: the host only hears about [KeyAction.Submit], [KeyAction.InterruptTurn] and the
 * global chords, while Enter/Shift+Enter and the popup cursor are resolved against the live draft.
 */

private val ButtonSize = 42.dp

/** Glyph of the leading button and of the idle send button. */
private val IdleGlyphSize = 21.dp

/** Glyph of the interrupt button and of the send button over a non-empty field. */
private val ActiveGlyphSize = 20.dp

/** Corner of the composer while the text sits inline, and once it has wrapped to a second row. */
private val InlineCornerRadius = 29.dp
private val StackedCornerRadius = 26.dp

/** How long the composer takes to change corner as the text stacks or unstack. */
private val CornerAnimationMs = Motion.ContentEnterMs

/** How long the trailing button takes to change colour as the turn starts or the field fills. */
private val TintAnimationMs = Motion.TintMs

/** Fade of a popup sliding in above the field, and of the rows that appear beside it. */
private val PopupFadeInMs = Motion.EnterMs
private val PopupFadeOutMs = Motion.ExitMs
private val RowFadeInMs = Motion.EnterMs

/** Vertical room the composer keeps around its button row, and inside its text column. */
private val ComposerPaddingHorizontal = 7.dp
private val ComposerPaddingVertical = 8.dp
private val FieldPaddingHorizontal = 7.dp

/** Gap between the button row and a popup above it, and above the stacked button row. */
private val PopupBottomGap = 6.dp
private val StackedRowGap = 6.dp

/** Room the queued-message line keeps inside the composer. */
private val QueuedPaddingHorizontal = 12.dp
private val QueuedPaddingBottom = 6.dp
private val QueuedFontSize = UiType.Meta
private val QueuedLineHeight = UiType.FootnoteLine

/** Type of the prompt and of its hint. */
private val PromptFontSize = UiType.Composer
private val PromptLineHeight = UiType.ComposerLine

/** Height of the input row: a button plus the two pixels that keep its ring inside the composer. */
private val InputRowMinHeight = ButtonSize + 2.dp

/**
 * Glass of the composer: its blur, and the elevation its shadow is cast from.
 *
 * Paired with `saturation = 1f` at the call site: the boost did nothing the tint was not already
 * doing, and it amplified whatever low-frequency colour the blur left behind.
 */
private val ComposerBlurRadius = 14.dp
private val NoBlurRadius = 0.dp
private val ComposerElevation = 12.dp

/** Room the two buttons and their paddings take out of the inline width of the field. */
private val InlineChromeWidth = 26.dp

/** Rows the slash popup shows before it scrolls; one page, like the TUI popup. */
private const val MaxPopupRows = 5

/** Room one suggestion row keeps inside itself, matching the tap-only popup cards. */
private val SuggestionRowPaddingHorizontal = 11.dp
private val SuggestionRowPaddingVertical = 8.dp
private val SuggestionRowGap = 4.dp

/** Width reserved for the command column, and the gap before its description. */
private val SuggestionCommandWidth = 104.dp
private val SuggestionCommandGap = 10.dp

private fun PromptTextStyle(color: Color) = TextStyle(
    color = color,
    fontSize = PromptFontSize,
    lineHeight = PromptLineHeight,
)

@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onInterrupt: () -> Unit,
    running: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    hint: String = stringResource(R.string.composer_hint_idle),
    queuedCount: Int = 0,
    slashSuggestions: List<SlashCommand> = emptyList(),
    onSuggestionPicked: (SlashCommand) -> Unit = {},
    /** `@` rows: plugins, tasks and fuzzy-searched files, already filtered by the host. */
    mentionSuggestions: List<MentionSuggestion> = emptyList(),
    onMentionPicked: (String) -> Unit = {},
    /** Called with the trailing `@` token, or `null` when it is gone; drives the search session. */
    onMentionQueryChange: (String?) -> Unit = {},
    /** Enabled skill names, offered behind the `$` trigger the way the TUI's mentions popup does. */
    skillCandidates: List<String> = emptyList(),
    onSkillPicked: (String) -> Unit = {},
    /** Called on every text edit, so the host can defer an approval dialog while the user types. */
    onActivity: () -> Unit = {},
    /** Submitted drafts, newest first; the reverse search behind Ctrl+R walks this list. */
    history: List<String> = emptyList(),
    /** Ctrl+O: the host copies the last agent message; null leaves the chord unbound. */
    onCopyLastResponse: (() -> Unit)? = null,
    /** Ctrl+G: the host launches `ACTION_EDIT` on a temp file; null leaves the chord unbound. */
    onOpenExternalEditor: (() -> Unit)? = null,
    backdrop: Backdrop? = null,
    maxInputLines: Int = 6,
    /** Opens the system picker; the host registers the launcher, a composable cannot. */
    onAttach: () -> Unit = {},
    inlineCornerRadius: Dp = InlineCornerRadius,
    stackedCornerRadius: Dp = StackedCornerRadius,
    buttonSize: Dp = ButtonSize,
) {
    val colors = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    // Where the chat root lives, so Esc can leave this field without leaving the window with no
    // key target at all. Null when the composer is hosted without a chat root (previews, tests).
    val chatKeyFocus = LocalChatKeyFocus.current
    val shortcutsHelp = LocalShortcutsHelp.current
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    var inlineWidthPx by remember { mutableFloatStateOf(0f) }
    val measurer = rememberTextMeasurer()
    // The caret-carrying mirror of [value]. The host owns the draft as plain text, but a newline has
    // to land where the caret is and a String cannot say where that is.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (field.text != value) {
            // The host rewrote the draft (a picked command, an inserted attachment path): follow it
            // and put the caret at the end, which is where typing would have left it.
            field = TextFieldValue(value, TextRange(value.length))
        }
    }
    // Esc hides a popup until the draft changes again; otherwise every keystroke that keeps the
    // trigger alive (a longer `/query`) would reopen what the user just closed.
    var popupDismissed by remember { mutableStateOf(false) }
    var popupIndex by remember { mutableIntStateOf(0) }
    // Reverse history search. [searchIndex] points into [history]; the matched entry is pushed into
    // the field while search is active so the user sees exactly what Enter would use, and the
    // original draft is restored on cancel.
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchIndex by remember { mutableIntStateOf(-1) }
    var searchOriginal by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val searchMatches = remember(history, searchQuery, searchActive) {
        if (searchActive) historySearchMatches(history, searchQuery) else emptyList()
    }
    LaunchedEffect(searchActive) {
        // The bar owns typing while it is up; without focus a hardware keyboard would keep editing
        // the matched entry.
        if (searchActive) runCatching { searchFocusRequester.requestFocus() }
    }
    val stacked = remember(value, inlineWidthPx, measurer) {
        if (value.isEmpty()) {
            false
        } else if (value.contains('\n')) {
            true
        } else if (inlineWidthPx <= 0f) {
            false
        } else {
            val measured = measurer.measure(
                text = AnnotatedString(value),
                style = PromptTextStyle(colors.onSurface),
                maxLines = 1,
                softWrap = false,
            )
            measured.size.width > inlineWidthPx
        }
    }
    val showSuggestions = !searchActive && !popupDismissed && slashSuggestions.isNotEmpty() && value.startsWith("/")
    // A mention is the trailing `@token`: everything after the last `@` counts as the query, and a
    // whitespace ends it. Mirrors the trigger rule in `bottom_pane/mentions_v2/filter.rs`.
    val mentionQuery = remember(value) {
        val at = value.lastIndexOf('@')
        when {
            at < 0 -> null
            value.substring(at + 1).any { it.isWhitespace() } -> null
            else -> value.substring(at + 1)
        }
    }
    // The host already narrowed the list: files come scored from the search session, plugins and
    // tasks from a local match. Re-filtering here would drop matches the server weighted highly.
    val mentionRows = remember(mentionQuery, mentionSuggestions) {
        if (mentionQuery == null) emptyList() else mentionSuggestions.take(MaxPopupRows)
    }
    val commandRows = remember(slashSuggestions) { slashSuggestions.take(MaxPopupRows) }
    // A skill is the trailing `$token`, same trigger rule as `@`: `$` is not part of the query when
    // a whitespace follows it, so an ordinary dollar amount never opens the popup.
    val skillQuery = remember(value) {
        val dollar = value.lastIndexOf('$')
        when {
            dollar < 0 -> null
            value.substring(dollar + 1).any { it.isWhitespace() } -> null
            else -> value.substring(dollar + 1)
        }
    }
    val skillRows = remember(skillQuery, skillCandidates) {
        if (skillQuery == null) {
            emptyList()
        } else {
            filterPaths(skillQuery, skillCandidates).take(MaxPopupRows)
        }
    }
    val showMentions = !searchActive && !popupDismissed && !showSuggestions && mentionRows.isNotEmpty()
    val showSkills = !searchActive && !popupDismissed && !showSuggestions && !showMentions && skillRows.isNotEmpty()
    val popupCount = when {
        showSuggestions -> commandRows.size
        showMentions -> mentionRows.size
        showSkills -> skillRows.size
        else -> 0
    }
    val popupSelection = if (popupCount == 0) 0 else popupIndex.coerceIn(0, popupCount - 1)

    // `command_popup.rs` re-selects the first match when the filtered list changes, so a keystroke
    // never leaves the cursor on a row that no longer exists.
    LaunchedEffect(showSuggestions, commandRows, showMentions, mentionRows, showSkills, skillRows) {
        popupIndex = 0
    }
    // The host owns the search session: it is told which `@` token is live, and told `null` when
    // the token or the whole composer goes away.
    LaunchedEffect(mentionQuery, showSuggestions) {
        onMentionQueryChange(if (showSuggestions) null else mentionQuery)
    }
    DisposableEffect(Unit) {
        onDispose { onMentionQueryChange(null) }
    }

    fun leaveField() {
        val target = chatKeyFocus
        if (target == null || runCatching { target.requestFocus() }.isFailure) {
            focusManager.clearFocus()
        }
    }

    fun applyDraft(next: String) {
        field = TextFieldValue(next, TextRange(next.length))
        popupDismissed = false
        onValueChange(next)
    }

    fun insertNewline() {
        val selection = field.selection
        val start = minOf(selection.start, selection.end)
        val end = maxOf(selection.start, selection.end)
        val text = field.text
        val next = text.substring(0, start) + "\n" + text.substring(end)
        field = TextFieldValue(next, TextRange(start + 1))
        popupDismissed = false
        onValueChange(next)
    }

    fun pickMention(path: String) {
        // Replace the trailing token with the picked path, keeping what came before.
        val at = value.lastIndexOf('@')
        if (at < 0) return
        applyDraft(value.substring(0, at) + "@" + path + " ")
        onMentionPicked(path)
    }

    fun pickSkill(name: String) {
        // Same splice as a mention: the `$token` being typed is replaced by the full `$name`.
        val dollar = value.lastIndexOf('$')
        if (dollar < 0) return
        applyDraft(value.substring(0, dollar) + "$" + name + " ")
        onSkillPicked(name)
    }

    fun pickSuggestion(index: Int) {
        if (showSuggestions) {
            commandRows.getOrNull(index)?.let(onSuggestionPicked)
        } else if (showMentions) {
            mentionRows.getOrNull(index)?.let { pickMention(it.insert) }
        } else if (showSkills) {
            skillRows.getOrNull(index)?.let(::pickSkill)
        }
    }

    fun selectSearchMatch(index: Int) {
        searchIndex = index
        if (index in history.indices) applyDraft(history[index])
    }

    fun beginHistorySearch() {
        if (!enabled) return
        searchActive = true
        searchOriginal = value
        searchQuery = ""
        selectSearchMatch(history.indices.firstOrNull() ?: -1)
    }

    fun updateSearchQuery(next: String) {
        searchQuery = next
        selectSearchMatch(historySearchMatches(history, next).firstOrNull() ?: -1)
    }

    fun moveHistorySearch(older: Boolean) {
        selectSearchMatch(nextHistoryMatch(searchMatches, searchIndex, older))
    }

    fun acceptHistorySearch() {
        if (searchIndex in history.indices) applyDraft(history[searchIndex])
        searchActive = false
        runCatching { focusRequester.requestFocus() }
    }

    fun cancelHistorySearch() {
        applyDraft(searchOriginal)
        searchActive = false
        runCatching { focusRequester.requestFocus() }
    }

    fun handle(action: KeyAction): Boolean = when (action) {
        KeyAction.Submit -> {
            if (searchActive) {
                acceptHistorySearch()
            } else if (enabled && value.isNotBlank()) {
                onActivity()
                onSubmit()
                leaveField()
            }
            true
        }

        KeyAction.InsertNewline -> {
            if (enabled) insertNewline()
            true
        }

        KeyAction.PopupNext -> {
            if (popupCount > 0) popupIndex = (popupSelection + 1) % popupCount
            popupCount > 0
        }

        KeyAction.PopupPrev -> {
            if (popupCount > 0) popupIndex = (popupSelection - 1 + popupCount) % popupCount
            popupCount > 0
        }

        KeyAction.PopupAccept -> {
            if (popupCount > 0) pickSuggestion(popupSelection)
            popupCount > 0
        }

        KeyAction.PopupDismiss -> {
            if (popupCount > 0) popupDismissed = true
            popupCount > 0
        }

        // `?` is the composer binding for `toggle_shortcuts`; it must never eat a printable
        // character the user is typing, so it only acts on an empty field.
        KeyAction.ShowShortcuts -> {
            val help = shortcutsHelp
            if (value.isEmpty() && help != null) {
                help.toggle()
                // The overlay is modal: leaving the caret here would let a soft keyboard keep
                // typing into the field behind it.
                leaveField()
                true
            } else {
                false
            }
        }

        KeyAction.ClearFocus -> {
            if (searchActive) cancelHistorySearch() else leaveField()
            true
        }

        // `history_search_previous` / `history_search_next`: Ctrl+R begins the search on the newest
        // entry, Ctrl+S only moves while a search is already up (it is not a "newest entry" key).
        KeyAction.HistoryOlder -> {
            if (!enabled) {
                false
            } else {
                if (searchActive) moveHistorySearch(older = true) else beginHistorySearch()
                true
            }
        }

        KeyAction.HistoryNewer -> {
            if (enabled && searchActive) {
                moveHistorySearch(older = false)
                true
            } else {
                false
            }
        }

        KeyAction.CopyLastResponse -> {
            val copy = onCopyLastResponse
            if (enabled && copy != null) {
                copy()
                true
            } else {
                false
            }
        }

        KeyAction.OpenExternalEditor -> {
            val editor = onOpenExternalEditor
            if (enabled && editor != null) {
                editor()
                true
            } else {
                false
            }
        }

        else -> false
    }

    val tint = glassTint(alpha = 0.86f)
    val cornerRadius by animateDpAsState(
        targetValue = if (stacked) stackedCornerRadius else inlineCornerRadius,
        animationSpec = tween(durationMillis = CornerAnimationMs),
        label = "promptCorner",
    )
    val shape = remember(cornerRadius) { SquircleShape(cornerRadius) }

    val leading: @Composable () -> Unit = {
        // The attachment button opens the system picker. `rememberLauncherForActivityResult` is
        // registered by the screen rather than here, because the contract it launches has to
        // outlive this composable: a picker that is re-registered on every recomposition loses the
        // result of a selection made while the sheet was open.
        IconButton(
            onClick = { if (enabled) onAttach() },
            minWidth = buttonSize,
            minHeight = buttonSize,
        ) {
            Icon(
                imageVector = MiuixIcons.Add,
                contentDescription = stringResource(R.string.composer_add_attachment),
                modifier = Modifier.size(IdleGlyphSize),
                tint = colors.onSurfaceSecondary,
            )
        }
    }
    val trailing: @Composable () -> Unit = {
        when {
            running && value.isBlank() -> {
                val background by animateColorAsState(
                    targetValue = colors.error,
                    animationSpec = tween(durationMillis = TintAnimationMs),
                    label = "interruptColor",
                )
                IconButton(
                    onClick = onInterrupt,
                    backgroundColor = background,
                    cornerRadius = buttonSize / 2,
                    minWidth = buttonSize,
                    minHeight = buttonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Pause,
                        contentDescription = stringResource(R.string.composer_interrupt_turn),
                        modifier = Modifier.size(ActiveGlyphSize),
                        tint = colors.onError,
                    )
                }
            }

            value.isBlank() || !enabled -> IconButton(
                onClick = {},
                minWidth = buttonSize,
                minHeight = buttonSize,
            ) {
                Icon(
                    imageVector = MiuixIcons.Send,
                    contentDescription = stringResource(R.string.composer_send),
                    modifier = Modifier.size(IdleGlyphSize),
                    tint = colors.onSurfaceSecondary,
                )
            }

            else -> {
                val background by animateColorAsState(
                    targetValue = if (focused) colors.primary else colors.primaryVariant,
                    animationSpec = tween(durationMillis = TintAnimationMs),
                    label = "sendColor",
                )
                IconButton(
                    onClick = {
                        onActivity()
                        onSubmit()
                        leaveField()
                    },
                    backgroundColor = background,
                    cornerRadius = buttonSize / 2,
                    minWidth = buttonSize,
                    minHeight = buttonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Send,
                        contentDescription = stringResource(R.string.composer_send),
                        modifier = Modifier.size(ActiveGlyphSize),
                        tint = colors.onPrimary,
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            // Preview, not bubble: while a suggestion list is open the same arrow keys move its
            // cursor, and otherwise the field below keeps them for caret movement.
            .codexHardwareKeys(
                if (popupCount > 0 && !searchActive) KeyContext.Popup else KeyContext.Composer,
                ::handle,
            )
            .floatingSurface(
                shape = shape,
                tint = tint,
                backdrop = backdrop,
                blurRadius = if (backdrop != null) ComposerBlurRadius else NoBlurRadius,
                // No saturation boost: it amplifies exactly the low-frequency colour the blur left
                // behind, which is what the blotches were.
                saturation = 1f,
                elevation = ComposerElevation,
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { focusRequester.requestFocus() },
            )
            .onSizeChanged { coords ->
                inlineWidthPx = coords.width - with(density) {
                    (buttonSize * 2 + InlineChromeWidth).toPx()
                }
            }
            .padding(
                horizontal = ComposerPaddingHorizontal,
                vertical = ComposerPaddingVertical,
            ),
    ) {
        if (queuedCount > 0) {
            Text(
                text = stringResource(R.string.composer_queued_count, queuedCount),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = QueuedPaddingHorizontal,
                        end = QueuedPaddingHorizontal,
                        bottom = QueuedPaddingBottom,
                    ),
                fontSize = QueuedFontSize,
                lineHeight = QueuedLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
        AnimatedVisibility(
            visible = showSuggestions,
            enter = fadeIn(tween(PopupFadeInMs)),
            exit = fadeOut(tween(PopupFadeOutMs)),
        ) {
            Column(modifier = Modifier.padding(bottom = PopupBottomGap)) {
                CommandSuggestionList(
                    commands = commandRows,
                    selectedIndex = popupSelection,
                    onPick = onSuggestionPicked,
                )
            }
        }
        AnimatedVisibility(
            visible = showMentions,
            enter = fadeIn(tween(PopupFadeInMs)),
            exit = fadeOut(tween(PopupFadeOutMs)),
        ) {
            Column(modifier = Modifier.padding(bottom = PopupBottomGap)) {
                MentionSuggestionList(
                    candidates = mentionRows,
                    selectedIndex = popupSelection,
                    onPick = { pickMention(it.insert) },
                )
            }
        }
        AnimatedVisibility(
            visible = showSkills,
            enter = fadeIn(tween(PopupFadeInMs)),
            exit = fadeOut(tween(PopupFadeOutMs)),
        ) {
            Column(modifier = Modifier.padding(bottom = PopupBottomGap)) {
                SkillSuggestionList(
                    candidates = skillRows,
                    selectedIndex = popupSelection,
                    onPick = ::pickSkill,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = InputRowMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = !stacked,
                enter = fadeIn(tween(RowFadeInMs)),
                exit = fadeOut(tween(PopupFadeOutMs)),
            ) {
                leading()
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = FieldPaddingHorizontal),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && !searchActive) {
                    Text(
                        text = hint,
                        fontSize = PromptFontSize,
                        lineHeight = PromptLineHeight,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    enabled = enabled,
                    // While searching the field is a preview of the match: typing belongs to the
                    // search bar, not to this text.
                    readOnly = searchActive,
                    value = field,
                    onValueChange = { next ->
                        // Typing is what un-dismisses a popup: Esc closed it, the next character
                        // reopens it if the trigger is still there.
                        popupDismissed = false
                        field = next
                        onActivity()
                        onValueChange(next.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                    textStyle = PromptTextStyle(colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    maxLines = maxInputLines,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            onActivity()
                            onSubmit()
                            leaveField()
                        },
                    ),
                    interactionSource = interactionSource,
                )
            }
            AnimatedVisibility(
                visible = !stacked,
                enter = fadeIn(tween(RowFadeInMs)),
                exit = fadeOut(tween(PopupFadeOutMs)),
            ) {
                trailing()
            }
        }
        AnimatedVisibility(
            visible = stacked,
            enter = fadeIn(tween(RowFadeInMs)),
            exit = fadeOut(tween(PopupFadeOutMs)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = StackedRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading()
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
        if (searchActive) {
            val searchStatus = when {
                history.isEmpty() -> stringResource(R.string.composer_history_search_empty)
                searchMatches.isEmpty() -> stringResource(R.string.composer_history_search_no_match)
                else -> stringResource(
                    R.string.composer_history_search_position,
                    searchMatches.indexOf(searchIndex) + 1,
                    searchMatches.size,
                )
            }
            HistorySearchBar(
                query = searchQuery,
                status = searchStatus,
                onQueryChange = ::updateSearchQuery,
                onOlder = { moveHistorySearch(older = true) },
                onNewer = { moveHistorySearch(older = false) },
                onAccept = ::acceptHistorySearch,
                onCancel = ::cancelHistorySearch,
                focusRequester = searchFocusRequester,
                enabled = enabled,
            )
        }
    }
}

/**
 * Corner radius of the popup card; it is a card of rows, so it takes the shared row corner.
 */
private val PopupCorner = RoundedCornerShape(UiConsts.RowCorner)

/** Corner radius of one popup row, shared by the slash and `@`-mention lists. */
private val PopupRowCorner = 11.dp

/** Gap between two popup rows, and the padding inside the popup shell. */
private val PopupRowGap = 4.dp
private val PopupPadding = 6.dp

/**
 * The raised card both suggestion lists draw into.
 *
 * Each list already narrows itself to `MaxPopupRows` items, so the shell only has to wrap them: a
 * clipped scroll region would cut a row in half and hide the fact that more matches exist.
 */
@Composable
private fun PopupShell(
    modifier: Modifier = Modifier,
    rows: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(codeSurface(), PopupCorner)
            .padding(PopupPadding),
        verticalArrangement = Arrangement.spacedBy(PopupRowGap),
        content = { rows() },
    )
}

/**
 * The slash popup with a keyboard cursor.
 *
 * The first row is only highlighted when the cursor points at it; a hardware keyboard needs the
 * highlight to move, so this draws rows with the cursor on whichever row Enter would take. Row
 * style is shared through [PopupShell], [PopupRowCorner] and `pressableRow`, so a tap still picks
 * exactly the row it lands on.
 */
@Composable
private fun CommandSuggestionList(
    commands: List<SlashCommand>,
    selectedIndex: Int,
    onPick: (SlashCommand) -> Unit,
) {
    PopupShell {
        commands.forEachIndexed { index, command ->
            val rowShape = RoundedCornerShape(PopupRowCorner)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableRow(
                        shape = rowShape,
                        container = if (index == selectedIndex) {
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            raisedSurface()
                        },
                        onClick = { onPick(command) },
                    )
                    .padding(
                        horizontal = SuggestionRowPaddingHorizontal,
                        vertical = SuggestionRowPaddingVertical,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = command.command,
                    modifier = Modifier.width(SuggestionCommandWidth),
                    fontSize = UiType.Subtitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(SuggestionCommandGap))
                Text(
                    text = command.description,
                    modifier = Modifier.weight(1f),
                    fontSize = UiType.RowDetail,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index != commands.lastIndex) Spacer(Modifier.height(SuggestionRowGap))
        }
    }
}

/** The `@`-mention popup, with the same keyboard cursor as [CommandSuggestionList]. */
@Composable
private fun MentionSuggestionList(
    candidates: List<MentionSuggestion>,
    selectedIndex: Int,
    onPick: (MentionSuggestion) -> Unit,
) {
    PopupShell {
        candidates.forEachIndexed { index, candidate ->
            val rowShape = RoundedCornerShape(PopupRowCorner)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableRow(
                        shape = rowShape,
                        container = if (index == selectedIndex) {
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            raisedSurface()
                        },
                        onClick = { onPick(candidate) },
                    )
                    .padding(
                        horizontal = SuggestionRowPaddingHorizontal,
                        vertical = SuggestionRowPaddingVertical,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = candidate.label,
                    modifier = Modifier.weight(1f),
                    fontSize = UiType.Subtitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // A file shows the directory it lives in; a plugin or task brings its own detail.
                val detail = candidate.detail ?: parentPath(candidate.insert).takeIf { it.isNotEmpty() }
                if (detail != null) {
                    Spacer(Modifier.width(SuggestionCommandGap))
                    Text(
                        text = detail,
                        modifier = Modifier.weight(1f),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (index != candidates.lastIndex) Spacer(Modifier.height(SuggestionRowGap))
        }
    }
}

/**
 * The `$`-skill popup. Rows are the bare skill names; the `$` is the trigger the user is typing and
 * is not repeated in the list, so two names that differ only by scope stay tellable apart.
 */
@Composable
private fun SkillSuggestionList(
    candidates: List<String>,
    selectedIndex: Int,
    onPick: (String) -> Unit,
) {
    PopupShell {
        candidates.forEachIndexed { index, name ->
            val rowShape = RoundedCornerShape(PopupRowCorner)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableRow(
                        shape = rowShape,
                        container = if (index == selectedIndex) {
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            raisedSurface()
                        },
                        onClick = { onPick(name) },
                    )
                    .padding(
                        horizontal = SuggestionRowPaddingHorizontal,
                        vertical = SuggestionRowPaddingVertical,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$" + name,
                    modifier = Modifier.weight(1f),
                    fontSize = UiType.Subtitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index != candidates.lastIndex) Spacer(Modifier.height(SuggestionRowGap))
        }
    }
}

/**
 * Case-insensitive subsequence match, the same shape of filter the TUI's file search uses: `cpu`
 * matches `com/cy/codex/ui/...`. Falls back to a plain `contains` in the caller, so a short query
 * still finds exact substrings.
 */private fun isSubsequence(query: String, candidate: String): Boolean {
    if (query.isEmpty()) return true
    var index = 0
    for (char in candidate) {
        if (char.lowercaseChar() == query[index].lowercaseChar()) {
            index++
            if (index == query.length) return true
        }
    }
    return false
}

/**
 * Narrow [candidates] by [query].
 *
 * A subsequence hit (`cs` matches `CodexScreen.kt`) is what the TUI's fuzzy matcher rewards, so
 * those come first; a plain case-insensitive `contains` pass follows so a query whose characters
 * are not in order still finds something instead of showing an empty popup.
 */
private fun filterPaths(query: String, candidates: List<String>): List<String> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return candidates
    val subsequence = mutableListOf<String>()
    val contains = mutableListOf<String>()
    for (candidate in candidates) {
        val haystack = candidate.lowercase()
        when {
            isSubsequence(needle, haystack) -> subsequence += candidate
            haystack.contains(needle) -> contains += candidate
        }
    }
    return subsequence + contains
}
