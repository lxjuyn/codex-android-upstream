package com.cy.codex.chatwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.ActionRow
import com.cy.codex.AppEvent
import com.cy.codex.ButtonRole
import com.cy.codex.CatalogState
import com.cy.codex.CodexButton
import com.cy.codex.CodexButtonSize
import com.cy.codex.CodexDivider
import com.cy.codex.CodexTextField
import com.cy.codex.EmptyState
import com.cy.codex.R
import com.cy.codex.SectionCard
import com.cy.codex.SurfaceBackButton
import com.cy.codex.SurfaceHeader
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.ValueRow
import com.cy.codex.pressableRow
import com.cy.codex.protocol.protocol.v2.ThreadRealtimeAudioChunk
import com.cy.codex.warningColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The realtime voice session of one thread.
 *
 * Mirrors `codex-rs/tui/src/chatwidget/realtime.rs` and its `realtime/recording_controls.rs`. The
 * TUI owns the session inside the chat widget — captions, voice, microphone state and speaker
 * activity all live in widget state, because a terminal has exactly one surface. A phone has no
 * terminal to share, so the same session gets a pushed page; what that page can *be* is narrower
 * than the TUI's, and every card below says which half is missing rather than implying it works.
 *
 * Four protocol facts decide the page's shape, and all four are stated on the card they belong to:
 *
 * - **No peer connection.** `thread/realtime/start` takes a transport, and the WebRTC one wants an
 *   SDP offer produced by an `RTCPeerConnection` that already has audio and the realtime events
 *   data channel configured. Nothing in this client creates one, so [AppEvent.StartRealtime] is
 *   emitted with a null offer and the server applies its own transport instead of negotiating
 *   against one.
 * - **No transcript in the page.** The caption notifications — `RealtimeTranscriptDelta`,
 *   `RealtimeTranscriptDone`, `RealtimeItemTranscriptDelta`, `RealtimeOutputAudioDelta` and
 *   `RealtimeError` — arrive on the client's event flow, and a transcript is *app* state, not page
 *   state: it has to survive the user leaving this page while the session keeps talking. That is
 *   why a transcript belongs to the app-level reducer in `CodexApp`, which is also where the shell
 *   hands this page its catalog from. The page is given a [CatalogState] and a callback and never
 *   the client, so it cannot collect the flow itself; its own list is a fold target that this build
 *   fills from nothing, and the captions card renders an empty state pointing at the transcript
 *   instead of captions nobody sent.
 * - **No voice control.** `thread/realtime/listVoices` enumerates voices and there is no call that
 *   sets one; a voice is part of starting a session, which [AppEvent.StartRealtime] does not carry.
 *   The picker is therefore display-only, and its note says so.
 * - **No recorder.** [ThreadRealtimeAudioChunk] is how captured PCM would travel, but no
 *   `AudioRecord` is bound in this build: no frame is read, no chunk carrying audio is built and
 *   [AppEvent.AppendRealtimeAudio] is never emitted from this file.
 *
 * @param threadId the thread the session belongs to; every request on this page is addressed by it.
 * @param catalog read for the voice list — the one part of this page the server can answer.
 * @param onEvent receives the start/stop, text, speech and voice-reload events; see each card.
 * @param onBack pops the page. The session, if one is running, is not stopped by it: the page is a
 *   view of a session that belongs to the thread, and leaving a view must not end a call.
 */
@Composable
fun RealtimeScreen(
    threadId: String,
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    // The page's own memory of which session it asked for. It is not evidence that one is running —
    // only `thread/realtime/started`, `thread/realtime/closed` and `thread/realtime/error` say
    // that, and this page is not handed them, so the session card states it under its own button.
    var requested by remember(threadId) { mutableStateOf(false) }
    // The voice picker's selection. Local by design: the protocol has no call that sets a voice, so
    // a tap moves this and sends nothing.
    var voice by remember(threadId) { mutableStateOf<String?>(null) }
    // The microphone card's capture flag, held here so it resets with the thread. Nothing behind it
    // reads audio; the card's note changes while it is on to say exactly that.
    var capturing by remember(threadId) { mutableStateOf(false) }
    // The fold target for live captions, fed by nothing in this build: the deltas arrive on the
    // client's event flow, which the page is not given. See the captions card for where they live.
    val transcript = remember(threadId) { mutableStateListOf<String>() }

    // `CatalogState.realtimeVoices` is only ever filled by the answer to
    // `thread/realtime/listVoices`, so the page asks once per thread rather than showing whatever
    // the previous visit left in the catalog.
    LaunchedEffect(threadId) { onEvent(AppEvent.ReloadRealtimeVoices) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SurfaceHeader(
            title = stringResource(R.string.realtime_page_title),
            subtitle = stringResource(R.string.realtime_page_subtitle),
            leading = { SurfaceBackButton(stringResource(R.string.realtime_page_back), onBack) },
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
            RealtimeSessionCard(
                threadId = threadId,
                requested = requested,
                onToggle = { start ->
                    if (start) {
                        onEvent(AppEvent.StartRealtime(threadId, null))
                    } else {
                        onEvent(AppEvent.StopRealtime(threadId))
                    }
                    // Moved on the tap, not on an answer: the answer is a notification this page
                    // does not receive, and a button that waited for one would never move again.
                    requested = start
                },
            )
            RealtimeCaptionsCard(lines = transcript)
            RealtimeVoicesCard(
                voices = catalog.realtimeVoices,
                selected = voice,
                onSelect = { voice = it },
                onEvent = onEvent,
            )
            RealtimeTextCard(threadId = threadId, onEvent = onEvent)
            RealtimeMicrophoneCard(capturing = capturing, onToggle = { capturing = it })
        }
    }
}

/**
 * Starting and stopping the session, and the transport fact the button cannot hide.
 *
 * `thread/realtime/start` carries a transport, and a client that can do WebRTC sends
 * `ThreadRealtimeStartTransport.Webrtc` holding an SDP offer built from a peer connection with
 * audio and the realtime events data channel already attached. This client builds no peer
 * connection, so the offer is `null` and the server falls back to its own transport. A button
 * labelled "start session" that did not say which of the two it is would claim the stronger one.
 *
 * [requested] is the page's own record of the tap, never session state: `thread/realtime/started`
 * confirms a session came up, `thread/realtime/closed` ends it and `thread/realtime/error` refuses
 * it, and none of the three is folded into this page. So the card shows what it knows — that it
 * asked — and says underneath that the server may disagree.
 *
 * @param threadId the thread the request is addressed by.
 * @param requested whether this page has already asked for a session.
 * @param onToggle asked for `true` to start and `false` to stop.
 */
@Composable
private fun RealtimeSessionCard(
    threadId: String,
    requested: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.realtime_session_title),
        icon = MiuixIcons.Play,
        trailing = if (requested) {
            stringResource(R.string.realtime_session_state_requested)
        } else {
            stringResource(R.string.realtime_session_state_idle)
        },
    ) {
        ValueRow(
            label = stringResource(R.string.realtime_session_thread),
            value = threadId,
            monospace = true,
        )
        CodexDivider()
        CardNote(stringResource(R.string.realtime_session_transport_note))
        CodexButton(
            text = if (requested) {
                stringResource(R.string.realtime_session_stop)
            } else {
                stringResource(R.string.realtime_session_start)
            },
            onClick = { onToggle(!requested) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.Space4),
            role = if (requested) ButtonRole.Secondary else ButtonRole.Primary,
        )
        CardNote(
            if (requested) {
                stringResource(R.string.realtime_session_requested_note)
            } else {
                stringResource(R.string.realtime_session_idle_note)
            },
        )
    }
}

/**
 * Where the captions would be, and why they are not here.
 *
 * The realtime transcript is a stream, not a snapshot: `thread/realtime/transcript/delta` and
 * `thread/realtime/item/transcript/delta` append to it while the session talks, and the page can
 * be popped at any point in that stream. That is what puts the transcript in the app-level reducer
 * rather than in a page's `remember` — a list held here would die on back and take the captions
 * with it — and it is the same reasoning that makes the TUI keep them in the chat widget instead
 * of a pane.
 *
 * This page is not handed the client, so it cannot collect that flow, and `CodexApp` currently
 * folds none of the five notifications into the catalog it does hand over. [lines] is the fold
 * target that will render them the day it does; today nothing appends to it, and the card shows
 * the empty state that sends the user to the transcript rather than a blank list that reads as a
 * bug.
 *
 * @param lines caption lines, in arrival order; empty in this build.
 */
@Composable
private fun RealtimeCaptionsCard(lines: List<String>) {
    SectionCard(
        title = stringResource(R.string.realtime_transcript_title),
        icon = MiuixIcons.Messages,
        trailing = lines.size.toString(),
    ) {
        if (lines.isEmpty()) {
            EmptyState(
                icon = MiuixIcons.Messages,
                title = stringResource(R.string.realtime_transcript_empty),
                detail = stringResource(R.string.realtime_transcript_empty_detail),
            )
        } else {
            lines.forEachIndexed { index, line ->
                if (index > 0) CodexDivider()
                Text(
                    text = line,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * The voices the server offers, with a selection that goes nowhere.
 *
 * `thread/realtime/listVoices` enumerates the voices; there is no `setVoice` to go with it. A
 * voice is chosen as part of *starting* a session, and [AppEvent.StartRealtime] carries the thread
 * and an SDP offer and nothing else — so a tap on a row moves the tick on this page and is not
 * sent. The card is still worth its space: it is the only place the user can see what the server
 * supports, and the tick is the selection the start request would carry once it can. A card that
 * let the tap look like a saved setting would be worse than one that says it is local.
 *
 * The reload is a row inside the card rather than an icon in its header because [SectionCard]'s
 * header slot takes a string, not a composable; [ActionRow] is the app's row for an action that
 * relists something.
 *
 * @param voices [CatalogState.realtimeVoices]; empty until `thread/realtime/listVoices` answers.
 * @param selected the voice whose row carries the tick, or `null` before any tap.
 * @param onSelect moves the tick. Display-only; nothing is sent.
 * @param onEvent receives [AppEvent.ReloadRealtimeVoices] from the reload row.
 */
@Composable
private fun RealtimeVoicesCard(
    voices: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onEvent: (AppEvent) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.realtime_voices_title),
        icon = MiuixIcons.Tune,
        trailing = voices.size.toString(),
    ) {
        ActionRow(
            title = stringResource(R.string.realtime_voices_refresh),
            subtitle = stringResource(R.string.realtime_voices_refresh_detail),
            icon = MiuixIcons.Refresh,
            onClick = { onEvent(AppEvent.ReloadRealtimeVoices) },
        )
        CodexDivider()
        if (voices.isEmpty()) {
            EmptyState(
                icon = MiuixIcons.Tune,
                title = stringResource(R.string.realtime_voices_empty),
                detail = stringResource(R.string.realtime_voices_empty_detail),
            )
        } else {
            voices.forEachIndexed { index, name ->
                if (index > 0) CodexDivider()
                VoiceRow(
                    name = name,
                    selected = name == selected,
                    onClick = { onSelect(name) },
                )
            }
            CardNote(stringResource(R.string.realtime_voices_selection_note))
        }
    }
}

/**
 * One selectable voice.
 *
 * A filled row with a tick, matching every other single-choice list in the app: a radio button
 * would promise a commit that this page cannot make, and the tick reads as "this is the one you
 * picked" without implying it was saved anywhere.
 *
 * @param name the voice id as the server spells it; never translated.
 * @param selected whether this row is the page's current pick.
 * @param onClick moves the pick to this row.
 */
@Composable
private fun VoiceRow(name: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(
                shape = remember { RoundedCornerShape(UiConsts.RowCorner) },
                container = if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent,
                onClick = onClick,
                onClickLabel = name,
            )
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            fontSize = UiType.RowTitle,
            lineHeight = UiType.RowTitleLine,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = stringResource(R.string.realtime_voices_selected),
                modifier = Modifier
                    .padding(start = UiConsts.Space8)
                    .size(UiConsts.IconRow),
                tint = colors.primary,
            )
        }
    }
}

/**
 * The two ways to put text into a live session, which are not interchangeable.
 *
 * `thread/realtime/appendText` adds a conversation item to the session — with the protocol's
 * default role that is the *user*, typing what they would otherwise have said — so it is input the
 * model answers. `thread/realtime/appendSpeech` hands the server text the *client* produced and
 * asks for it to be voiced; it is output the server reads aloud, the model does not answer it, and
 * it is not transcribed back. One box and one button for both would hide the only decision this
 * card exists to make, so each has its own field, its own action and a note saying which side of
 * the conversation it lands on.
 *
 * Both [AppEvent]s carry one string, so an `appendText` item always takes the protocol's default
 * role: this card cannot send a developer or assistant item even though the wire allows one.
 *
 * @param threadId the thread every append is addressed by.
 * @param onEvent receives [AppEvent.AppendRealtimeText] or [AppEvent.AppendRealtimeSpeech].
 */
@Composable
private fun RealtimeTextCard(threadId: String, onEvent: (AppEvent) -> Unit) {
    var typed by remember(threadId) { mutableStateOf("") }
    var speech by remember(threadId) { mutableStateOf("") }
    val sendTyped: () -> Unit = {
        val text = typed.trim()
        if (text.isNotEmpty()) {
            onEvent(AppEvent.AppendRealtimeText(threadId, text))
            // Cleared only on a send the page actually made, so a blank tap cannot wipe a draft.
            typed = ""
        }
    }
    val sendSpeech: () -> Unit = {
        val text = speech.trim()
        if (text.isNotEmpty()) {
            onEvent(AppEvent.AppendRealtimeSpeech(threadId, text))
            speech = ""
        }
    }

    SectionCard(
        title = stringResource(R.string.realtime_inject_title),
        icon = MiuixIcons.Send,
    ) {
        CodexTextField(
            value = typed,
            onValueChange = { typed = it },
            label = stringResource(R.string.realtime_inject_text_label),
            placeholder = stringResource(R.string.realtime_inject_text_placeholder),
            onImeAction = sendTyped,
        )
        CardNote(stringResource(R.string.realtime_inject_text_note))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.Space4),
            horizontalArrangement = Arrangement.End,
        ) {
            CodexButton(
                text = stringResource(R.string.realtime_inject_text_send),
                onClick = sendTyped,
                size = CodexButtonSize.Compact,
                enabled = typed.isNotBlank(),
            )
        }
        CodexDivider()
        CodexTextField(
            value = speech,
            onValueChange = { speech = it },
            label = stringResource(R.string.realtime_inject_speech_label),
            placeholder = stringResource(R.string.realtime_inject_speech_placeholder),
            onImeAction = sendSpeech,
        )
        CardNote(stringResource(R.string.realtime_inject_speech_note))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.Space4),
            horizontalArrangement = Arrangement.End,
        ) {
            CodexButton(
                text = stringResource(R.string.realtime_inject_speech_send),
                onClick = sendSpeech,
                size = CodexButtonSize.Compact,
                role = ButtonRole.Secondary,
                enabled = speech.isNotBlank(),
            )
        }
    }
}

/**
 * The microphone, and the recorder that is not behind it.
 *
 * `thread/realtime/appendAudio` is how audio reaches a live session: one [ThreadRealtimeAudioChunk]
 * per push, carrying base64 PCM in `data` plus the channel count and sample rate it was captured
 * at, and optionally the id of the transcript item it belongs to. The card shows that format and a
 * capture control, and it has to be read with this sentence: **no recorder is bound in this
 * build**. Nothing constructs an `AudioRecord`, so no frame is ever read, no chunk is ever built,
 * and [AppEvent.AppendRealtimeAudio] — which exists and works — is never emitted from this page.
 *
 * The start/stop button therefore toggles a page-local flag and nothing else. That is why the note
 * under it changes while the flag is on, in the warning colour: a capture indicator that stayed
 * quiet would be the one lie on the page, and the two sentences the user sees instead are exactly
 * what is happening.
 *
 * @param capturing the page's own capture flag; no audio is captured while it is true.
 * @param onToggle asked for the new value of that flag.
 */
@Composable
private fun RealtimeMicrophoneCard(capturing: Boolean, onToggle: (Boolean) -> Unit) {
    SectionCard(
        title = stringResource(R.string.realtime_mic_title),
        icon = MiuixIcons.Mic,
        trailing = if (capturing) {
            stringResource(R.string.realtime_mic_state_on)
        } else {
            stringResource(R.string.realtime_mic_state_off)
        },
    ) {
        CardNote(stringResource(R.string.realtime_mic_format_note))
        ValueRow(
            label = stringResource(R.string.realtime_mic_sample_rate),
            value = stringResource(
                R.string.realtime_mic_sample_rate_value,
                CaptureFormat.sampleRate,
            ),
        )
        CodexDivider()
        ValueRow(
            label = stringResource(R.string.realtime_mic_channels),
            value = CaptureFormat.numChannels.toString(),
        )
        CodexDivider()
        // The dash is [ValueRow]'s own rendering of an empty value, and it is the exact answer: the
        // chunk's optional item id is unset because this client never builds the chunk it would go
        // on. The row is here so the format the note describes is complete.
        ValueRow(
            label = stringResource(R.string.realtime_mic_item_id),
            value = "",
        )
        CodexButton(
            text = if (capturing) {
                stringResource(R.string.realtime_mic_stop)
            } else {
                stringResource(R.string.realtime_mic_start)
            },
            onClick = { onToggle(!capturing) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.Space4),
            role = if (capturing) ButtonRole.Secondary else ButtonRole.Primary,
        )
        CardNote(
            text = if (capturing) {
                stringResource(R.string.realtime_mic_capturing_note)
            } else {
                stringResource(R.string.realtime_mic_idle_note)
            },
            tint = if (capturing) warningColor() else null,
        )
    }
}

/**
 * The explanatory paragraph shared by the cards on this page.
 *
 * One composable so the notes on this page agree on leading, colour and padding: a page that
 * explains several different protocol limits in several hand-styled paragraphs reads as if the
 * explanations were decoration, and the reader stops believing them.
 *
 * [tint] is for the one note that must not be skimmed as body text — the microphone card's "the
 * flag is on and nothing is being captured" — and for nothing else.
 *
 * @param text the sentence; always a string resource, because each one is a claim about the
 *   protocol.
 * @param tint colour override, or `null` for the standard summary colour.
 */
@Composable
private fun CardNote(text: String, tint: Color? = null) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space6),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = tint ?: MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * The capture format a chunk would carry, read off the protocol type instead of copied.
 *
 * A literal 24000 written here would be a second source of truth for
 * [ThreadRealtimeAudioChunk.sampleRate] and would drift the day the server's default changes; the
 * readout exists to be trusted, so it reads the type. Kotlin has no way to read a data class's
 * defaults without an instance, so this is one — empty, holding no audio, never sent, and never
 * joined by a second chunk anywhere in this file. Only the microphone card's readout touches it.
 */
private val CaptureFormat = ThreadRealtimeAudioChunk(data = "")
