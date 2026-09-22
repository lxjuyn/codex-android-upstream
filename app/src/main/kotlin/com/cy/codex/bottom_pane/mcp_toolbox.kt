package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.ButtonRole
import com.cy.codex.CodexButton
import com.cy.codex.CodexDivider
import com.cy.codex.CodexSwitchRow
import com.cy.codex.CodexTextField
import com.cy.codex.R
import com.cy.codex.SectionCard
import com.cy.codex.SurfaceBackButton
import com.cy.codex.SurfaceHeader
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.ValueRow
import com.cy.codex.codeSurface
import com.cy.codex.history_cell.ToolResultBlocks
import com.cy.codex.history_cell.projectMcpResult
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.protocol.v2.McpResourceReadResponse
import com.cy.codex.protocol.protocol.v2.McpServerToolCallResponse
import com.cy.codex.successColor
import com.cy.codex.warningColor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * One MCP server's manual surface: read a resource, call a tool, watch its events.
 *
 * The sibling of `bottom_pane/mcp_server_elicitation.rs`, and its opposite half: that file is the
 * *elicited* MCP surface, where a connected server asks this client a question mid-turn. This page
 * is the half a human drives, which the TUI reaches by typing at a prompt and a phone needs a
 * surface for. The three sections talk to the same server and share nothing else — on the wire each
 * one is its own request family (a resource read, a tool call, and the event-stream start/stop
 * pair), which is why they are three cards rather than one form.
 *
 * There is no refresh in the header on purpose. Nothing here is a snapshot of server state that
 * could have gone stale: a read and a call happen because the user asked for them, and the event
 * list *is* the stream. The header's subtitle names the server instead, because the one thing a
 * page opened from a server list must never leave ambiguous is which server it is talking to.
 *
 * @param server name of the connected server every request on this page is addressed to.
 * @param client transport the reads, the tool call and the stream subscription go through.
 * @param onEvent hands the stream switch to the app, which owns the stream's lifecycle.
 * @param onBack closes the page; the caller owns the page stack.
 * @param modifier layout modifier applied to the page's root.
 */
@Composable
fun McpToolboxScreen(
    server: String,
    client: AppServerClient,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()

    var resourceUri by remember(server) { mutableStateOf("") }
    var resource by remember(server) { mutableStateOf<McpResourceReadResponse?>(null) }
    var resourceFailure by remember(server) { mutableStateOf<String?>(null) }
    var reading by remember(server) { mutableStateOf(false) }

    var toolName by remember(server) { mutableStateOf("") }
    var toolArguments by remember(server) { mutableStateOf(NoArguments) }
    var toolResult by remember(server) { mutableStateOf<McpServerToolCallResponse?>(null) }
    var toolFailure by remember(server) { mutableStateOf<String?>(null) }
    var calling by remember(server) { mutableStateOf(false) }

    var streaming by remember(server) { mutableStateOf(false) }
    val streamEvents = remember(server) { mutableStateListOf<String>() }

    // Resolved while composing: the failure branches run inside a coroutine, which cannot read
    // a string resource itself, and a failure with no message of its own would otherwise show
    // nothing at all.
    val readFailureText = stringResource(R.string.mcp_toolbox_resource_failed)
    val callFailureText = stringResource(R.string.mcp_toolbox_tool_failed)

    // The stream being listed is the app's, not a private one: the switch only asks for it, so the
    // notifications are picked back out of the client's single event flow. The wire notification
    // names its stream by subscription id, which is minted by whoever started it — the app, not
    // this page — so what can be shown here is the method of each pushed notification. Only the
    // most recent ones are kept: a chatty server must not grow this page's composition without
    // bound.
    LaunchedEffect(server, client) {
        client.events.collect { event ->
            if (event is AppServerEvent.McpServerEvent) {
                streamEvents += event.delta.notification.method
                if (streamEvents.size > StreamEventLimit) streamEvents.removeAt(0)
            }
        }
    }

    /**
     * Read [resourceUri], replacing whatever the previous read returned.
     *
     * The blank guard is here rather than in the card so the card's button and the field's IME
     * action cannot disagree about whether there is anything to ask for.
     */
    fun readResource() {
        val uri = resourceUri.trim()
        if (uri.isEmpty() || reading) return
        reading = true
        scope.launch {
            client.readMcpResource(server, uri)
                .onSuccess {
                    resource = it
                    resourceFailure = null
                }
                .onFailure {
                    resource = null
                    resourceFailure = it.message ?: readFailureText
                }
            reading = false
        }
    }

    /**
     * Call [toolName] with [toolArguments].
     *
     * The arguments travel as the JSON text the field holds: the server decodes them, and a payload
     * this client refused to send would be a second, weaker decoder disagreeing with the real one.
     * A blank field means "no arguments", which the protocol spells as an empty object.
     */
    fun callTool() {
        val name = toolName.trim()
        if (name.isEmpty() || calling) return
        calling = true
        scope.launch {
            client.callMcpTool(
                server = server,
                tool = name,
                arguments = toolArguments.ifBlank { NoArguments },
            )
                .onSuccess {
                    toolResult = it
                    toolFailure = null
                }
                .onFailure {
                    toolResult = null
                    toolFailure = it.message ?: callFailureText
                }
            calling = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SurfaceHeader(
            title = stringResource(R.string.mcp_toolbox_title),
            subtitle = server,
            leading = { SurfaceBackButton(stringResource(R.string.mcp_toolbox_back), onBack) },
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
            ResourceCard(
                uri = resourceUri,
                onUriChange = { resourceUri = it },
                reading = reading,
                response = resource,
                failure = resourceFailure,
                onRead = { readResource() },
            )
            ToolCard(
                tool = toolName,
                onToolChange = { toolName = it },
                arguments = toolArguments,
                onArgumentsChange = { toolArguments = it },
                calling = calling,
                response = toolResult,
                failure = toolFailure,
                onCall = { callTool() },
            )
        }
    }
}

/**
 * The resource half: a uri in, one resource body out.
 *
 * `mcpServer/resource/read` answers with a list of `contents`, each with the uri it resolved, the
 * mime type it found and either text or base64 bytes. The first content is rendered; the mime type
 * is shown rather than guessed from the body, because a server that answers `application/json` and
 * one that answers nothing at all look the same otherwise.
 */
@Composable
private fun ResourceCard(
    uri: String,
    onUriChange: (String) -> Unit,
    reading: Boolean,
    response: McpResourceReadResponse?,
    failure: String?,
    onRead: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.mcp_toolbox_resource_section),
        icon = MiuixIcons.File,
    ) {
        CodexTextField(
            value = uri,
            onValueChange = onUriChange,
            label = stringResource(R.string.mcp_toolbox_resource_uri),
            placeholder = stringResource(R.string.mcp_toolbox_resource_uri_placeholder),
            onImeAction = onRead,
        )
        Spacer(Modifier.height(UiConsts.Space8))
        CodexButton(
            text = stringResource(R.string.mcp_toolbox_resource_read),
            onClick = onRead,
            modifier = Modifier.fillMaxWidth(),
            role = ButtonRole.Secondary,
            enabled = uri.isNotBlank() && !reading,
        )
        if (failure != null) {
            Spacer(Modifier.height(UiConsts.Space8))
            ServerFailure(text = failure)
        }
        if (response != null) {
            // A read may answer with several contents; the page renders the first, which is the
            // one a single-uri request returns in practice.
            val content = response.contents.firstOrNull()
            Spacer(Modifier.height(UiConsts.Space8))
            ValueRow(
                label = stringResource(R.string.mcp_toolbox_resource_uri_label),
                value = content?.uri.orEmpty(),
                monospace = true,
            )
            CodexDivider()
            ValueRow(
                label = stringResource(R.string.mcp_toolbox_resource_mime),
                value = content?.mimeType.orEmpty(),
            )
            Spacer(Modifier.height(UiConsts.Space8))
            val body = content?.text
            if (body.isNullOrEmpty()) {
                // A resource may carry no text (it is bytes, or it is empty). Saying so is the
                // difference between "the server had nothing" and "the page lost it".
                Text(
                    text = stringResource(R.string.mcp_toolbox_resource_no_text),
                    modifier = Modifier.padding(horizontal = UiConsts.Space4),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = warningColor(),
                )
            } else {
                MonospaceOutput(text = body)
            }
        }
    }
}

/**
 * The tool half: a tool name, its arguments as JSON, and whatever the server answers.
 *
 * The result is rendered even when the tool reports an error of its own: `isError` is the *tool*
 * saying the call went through and failed, which is a different thing from the call not going
 * through, and only the second one is a failure of this page.
 */
@Composable
private fun ToolCard(
    tool: String,
    onToolChange: (String) -> Unit,
    arguments: String,
    onArgumentsChange: (String) -> Unit,
    calling: Boolean,
    response: McpServerToolCallResponse?,
    failure: String?,
    onCall: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val emptyOutput = stringResource(R.string.mcp_toolbox_result_empty)
    SectionCard(
        title = stringResource(R.string.mcp_toolbox_tool_section),
        icon = MiuixIcons.Tasks,
    ) {
        CodexTextField(
            value = tool,
            onValueChange = onToolChange,
            label = stringResource(R.string.mcp_toolbox_tool_name),
            placeholder = stringResource(R.string.mcp_toolbox_tool_name_placeholder),
            onImeAction = onCall,
        )
        Spacer(Modifier.height(UiConsts.Space8))
        CodexTextField(
            value = arguments,
            onValueChange = onArgumentsChange,
            label = stringResource(R.string.mcp_toolbox_tool_arguments),
            placeholder = stringResource(R.string.mcp_toolbox_tool_arguments_placeholder),
            singleLine = false,
        )
        Spacer(Modifier.height(UiConsts.Space8))
        CodexButton(
            text = stringResource(R.string.mcp_toolbox_tool_call),
            onClick = onCall,
            modifier = Modifier.fillMaxWidth(),
            role = ButtonRole.Secondary,
            enabled = tool.isNotBlank() && !calling,
        )
        if (failure != null) {
            Spacer(Modifier.height(UiConsts.Space8))
            ServerFailure(text = failure)
        }
        if (response != null) {
            Spacer(Modifier.height(UiConsts.Space8))
            Text(
                text = if (response.isError) {
                    stringResource(R.string.mcp_toolbox_tool_error)
                } else {
                    stringResource(R.string.mcp_toolbox_tool_ok)
                },
                modifier = Modifier.padding(horizontal = UiConsts.Space4),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = if (response.isError) colors.error else successColor(),
            )
            Spacer(Modifier.height(UiConsts.Space6))
            val blocks = projectMcpResult(response.result)
            if (blocks.isEmpty()) {
                MonospaceOutput(text = response.result.ifEmpty { emptyOutput })
            } else {
                ToolResultBlocks(blocks)
            }
        }
    }
}

/**
 * The stream half: one switch, and the events the server pushed through it.
 *
 * The switch carries a local boolean rather than a server-reported one, because the protocol has no
 * "is this stream open" read to ask: `mcpServer/event/stream/start` and its stop counterpart are
 * requests whose only answer is success or failure. The state is therefore the user's intent, and
 * the app — which owns the stream and every other subscriber to it — is told through [AppEvent].
 *
 * The list is the *arrival* order, oldest first, so a burst reads as a sequence; the trailing count
 * is what tells the user the list is bounded rather than broken.
 */
@Composable
private fun StreamCard(
    streaming: Boolean,
    events: List<String>,
    onStreamingChange: (Boolean) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.mcp_toolbox_stream_section),
        icon = MiuixIcons.Link,
        trailing = events.size.toString(),
    ) {
        CodexSwitchRow(
            title = stringResource(R.string.mcp_toolbox_stream_switch),
            subtitle = stringResource(R.string.mcp_toolbox_stream_switch_detail),
            checked = streaming,
            onCheckedChange = onStreamingChange,
        )
        Spacer(Modifier.height(UiConsts.Space8))
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.mcp_toolbox_stream_empty),
                modifier = Modifier.padding(horizontal = UiConsts.Space4),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            MonospaceOutput(text = events.joinToString("\n"))
        }
    }
}

/**
 * A failure the *server* reported, in the same error treatment the rest of the app uses.
 *
 * Shown instead of an empty result pane: a refused tool name and a tool that answered nothing are
 * indistinguishable once the message is dropped, and only one of them is worth retrying.
 */
@Composable
private fun ServerFailure(text: String) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(OutputShape)
            .background(colors.error.copy(alpha = 0.12f))
            .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space6),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = colors.error,
    )
}

/**
 * Raw server text: monospace, on [codeSurface], bounded, and scrolling on both axes.
 *
 * A resource body is prose and would wrap, but a tool result is JSON whose lines are longer than
 * any phone. A box that only scrolled vertically would either clip those lines or reflow them into
 * something that no longer reads as the payload the server sent.
 */
@Composable
private fun MonospaceOutput(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OutputShape)
            .background(codeSurface())
            .heightIn(min = OutputMinHeight, max = OutputMaxHeight)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(UiConsts.Space10),
    ) {
        Text(
            text = text,
            fontSize = UiType.Code,
            lineHeight = UiType.CodeLine,
            fontFamily = FontFamily.Monospace,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/** Corner of the output box and of a reported failure: a control inside a card, not a card. */
private val OutputShape = RoundedCornerShape(UiConsts.CornerControl)

/**
 * Floor of the output box, so an empty answer still reads as a pane rather than as a missing one.
 */
private val OutputMinHeight = 56.dp

/**
 * Ceiling of the output box.
 *
 * The page scrolls as a whole, so a box taller than this would push the cards below it off the
 * screen and make the user scroll twice to reach the switch. Long payloads scroll inside instead.
 */
private val OutputMaxHeight = 260.dp

/**
 * How many stream events the page keeps.
 *
 * Enough to see a burst arrive and still read the start of it, small enough that a server
 * notifying on every request cannot turn this page into an unbounded transcript of its own.
 */
private const val StreamEventLimit = 50

/** The protocol's spelling of "this tool takes no arguments". */
private const val NoArguments = "{}"
