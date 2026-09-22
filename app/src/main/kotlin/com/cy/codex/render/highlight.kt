package com.cy.codex.render

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Syntax highlighting for transcript code.
 *
 * The TUI uses syntect (`codex-rs/tui/src/render/highlight.rs`), whose grammar set is far past what
 * a phone needs. This is the same contract with a much smaller surface: one line-oriented lexer
 * that understands comments, strings, numbers, keywords and types, and a keyword table per
 * language. A language it does not know renders as plain monospace, which is what the transcript
 * looked like before.
 */
@Immutable
class SyntaxPalette(
    val plain: Color,
    val keyword: Color,
    val type: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
) {
    /** Style for a token kind; [FontWeight.Normal] keeps the mono ramp uniform. */
    fun style(kind: TokenKind): SpanStyle = when (kind) {
        TokenKind.Plain -> SpanStyle(color = plain)
        TokenKind.Keyword -> SpanStyle(color = keyword, fontWeight = FontWeight.Medium)
        TokenKind.Type -> SpanStyle(color = type)
        TokenKind.String -> SpanStyle(color = string)
        TokenKind.Comment -> SpanStyle(color = comment)
        TokenKind.Number -> SpanStyle(color = number)
        TokenKind.Function -> SpanStyle(color = function)
    }
}

/** Token classes the one lexer can produce. */
enum class TokenKind { Plain, Keyword, Type, String, Comment, Number, Function }

/**
 * Highlighting colours for the current theme.
 *
 * The values follow the same adaptive pair the diff palette uses: one set for dark surfaces and one
 * for light, because the miuix tonal palette has no syntax ramp of its own.
 */
@Composable
fun syntaxPalette(): SyntaxPalette {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        if (dark) {
            SyntaxPalette(
                plain = Color(0xFFE6EDF3),
                keyword = Color(0xFFFF7B72),
                type = Color(0xFFFFCB6B),
                string = Color(0xFFA5D6FF),
                comment = Color(0xFF8B949E),
                number = Color(0xFF79C0FF),
                function = Color(0xFFD2A8FF),
            )
        } else {
            SyntaxPalette(
                plain = Color(0xFF1F2328),
                keyword = Color(0xFFCF222E),
                type = Color(0xFF953800),
                string = Color(0xFF0A3069),
                comment = Color(0xFF6E7781),
                number = Color(0xFF0550AE),
                function = Color(0xFF8250DF),
            )
        }
    }
}

/**
 * Highlight a whole code block, one [AnnotatedString] per line.
 *
 * Returns plain lines for an unknown language or a block past [SyntaxHighlightMaxBytes],
 * [SyntaxHighlightMaxLines] or [SyntaxHighlightMaxLineBytes]; the limits mirror the TUI's
 * `render/highlight.rs` so a giant diff or log cannot stall a frame.
 */
fun highlightCodeLines(
    code: String,
    language: String?,
    palette: SyntaxPalette,
): List<AnnotatedString> {
    val spec = languageSpec(language)
    val lines = code.lines()
    if (spec == null || code.length > SyntaxHighlightMaxBytes || lines.size > SyntaxHighlightMaxLines ||
        lines.any { it.length > SyntaxHighlightMaxLineBytes }
    ) {
        return lines.map { AnnotatedString(it) }
    }
    val lexer = SyntaxLexer(spec, palette)
    return lines.map { lexer.highlight(it) }
}

/** Highlight one line when the caller knows its language, e.g. a diff hunk. */
fun highlightCodeLine(text: String, language: String?, palette: SyntaxPalette): AnnotatedString {
    val spec = languageSpec(language) ?: return AnnotatedString(text)
    if (text.length > SyntaxHighlightMaxLineBytes) return AnnotatedString(text)
    return SyntaxLexer(spec, palette).highlight(text)
}

/** Highlight one shell command line, as the TUI does with `highlight_bash_to_lines`. */
fun highlightShellCommand(command: String, palette: SyntaxPalette): AnnotatedString {
    val spec = languageSpec("bash") ?: return AnnotatedString(command)
    if (command.length > SyntaxHighlightMaxLineBytes) return AnnotatedString(command)
    return SyntaxLexer(spec, palette).highlight(command)
}

/**
 * A stateful line lexer.
 *
 * State is only the multi-line constructs a line-oriented renderer cannot otherwise see: block
 * comments and triple-quoted strings. [snapshot]/[restore] let a streaming block re-highlight its
 * partial line without advancing the state it will need for the next complete one.
 */
class SyntaxLexer internal constructor(
    private val spec: LanguageSpec,
    private val palette: SyntaxPalette,
) {
    private var blockCommentEnd: String? = null
    private var tripleEnd: String? = null

    fun snapshot(): LexerState = LexerState(blockCommentEnd, tripleEnd)

    fun restore(state: LexerState) {
        blockCommentEnd = state.blockCommentEnd
        tripleEnd = state.tripleEnd
    }

    fun highlight(text: String): AnnotatedString = buildAnnotatedString {
        var index = 0
        var plainStart = 0
        fun flush(end: Int) {
            if (end > plainStart) {
                withStyle(palette.style(TokenKind.Plain)) { append(text.substring(plainStart, end)) }
            }
        }

        while (index < text.length) {
            val commentEnd = blockCommentEnd
            if (commentEnd != null) {
                val end = text.indexOf(commentEnd, index)
                if (end < 0) {
                    withStyle(palette.style(TokenKind.Comment)) { append(text.substring(index)) }
                    plainStart = text.length
                    index = text.length
                } else {
                    val stop = end + commentEnd.length
                    withStyle(palette.style(TokenKind.Comment)) { append(text.substring(index, stop)) }
                    plainStart = stop
                    index = stop
                    blockCommentEnd = null
                }
                continue
            }
            val stringEnd = tripleEnd
            if (stringEnd != null) {
                val end = text.indexOf(stringEnd, index)
                if (end < 0) {
                    withStyle(palette.style(TokenKind.String)) { append(text.substring(index)) }
                    plainStart = text.length
                    index = text.length
                } else {
                    val stop = end + stringEnd.length
                    withStyle(palette.style(TokenKind.String)) { append(text.substring(index, stop)) }
                    plainStart = stop
                    index = stop
                    tripleEnd = null
                }
                continue
            }

            val char = text[index]
            when {
                char.isWhitespace() -> index++

                spec.lineComments.any { text.startsWith(it, index) } -> {
                    flush(index)
                    withStyle(palette.style(TokenKind.Comment)) { append(text.substring(index)) }
                    plainStart = text.length
                    index = text.length
                }

                spec.blockComments.any { text.startsWith(it.first, index) } -> {
                    val pair = spec.blockComments.first { text.startsWith(it.first, index) }
                    flush(index)
                    val start = index
                    val end = text.indexOf(pair.second, start + pair.first.length)
                    if (end < 0) {
                        withStyle(palette.style(TokenKind.Comment)) { append(text.substring(start)) }
                        plainStart = text.length
                        index = text.length
                        blockCommentEnd = pair.second
                    } else {
                        val stop = end + pair.second.length
                        withStyle(palette.style(TokenKind.Comment)) { append(text.substring(start, stop)) }
                        plainStart = stop
                        index = stop
                    }
                }

                spec.triples.any { text.startsWith(it, index) } -> {
                    val triple = spec.triples.first { text.startsWith(it, index) }
                    flush(index)
                    val start = index
                    val end = text.indexOf(triple, start + triple.length)
                    if (end < 0) {
                        withStyle(palette.style(TokenKind.String)) { append(text.substring(start)) }
                        plainStart = text.length
                        index = text.length
                        tripleEnd = triple
                    } else {
                        val stop = end + triple.length
                        withStyle(palette.style(TokenKind.String)) { append(text.substring(start, stop)) }
                        plainStart = stop
                        index = stop
                    }
                }

                char == '"' || char == '\'' || (char == '`' && spec.backtickStrings) -> {
                    flush(index)
                    val start = index
                    index++
                    var closed = false
                    while (index < text.length) {
                        val current = text[index]
                        if (current == '\\' && index + 1 < text.length) {
                            index += 2
                            continue
                        }
                        index++
                        if (current == char) {
                            closed = true
                            break
                        }
                    }
                    if (!closed && spec.multilineStrings && index >= text.length) {
                        tripleEnd = char.toString()
                    }
                    withStyle(palette.style(TokenKind.String)) { append(text.substring(start, index)) }
                    plainStart = index
                }

                spec.numberStart(char) -> {
                    flush(index)
                    val start = index
                    while (index < text.length && (spec.numberPart(text[index]) || text[index] == '.')) index++
                    withStyle(palette.style(TokenKind.Number)) { append(text.substring(start, index)) }
                    plainStart = index
                }

                spec.identifierStart(char) -> {
                    val start = index
                    index++
                    while (index < text.length && spec.identifierPart(text[index])) index++
                    val word = text.substring(start, index)
                    val kind = spec.classify(word, nextNonSpace(text, index))
                    if (kind != TokenKind.Plain) {
                        flush(start)
                        withStyle(palette.style(kind)) { append(word) }
                        plainStart = index
                    }
                }

                else -> index++
            }
        }
        flush(text.length)
    }
}

/** Saved multi-line lexer state, for re-highlighting a streaming partial line. */
class LexerState internal constructor(
    internal val blockCommentEnd: String?,
    internal val tripleEnd: String?,
)

/** A language's lexical rules; `null` from [languageSpec] means "render plain". */
internal class LanguageSpec(
    val keywords: Set<String>,
    val types: Set<String>,
    val lineComments: List<String> = emptyList(),
    val blockComments: List<Pair<String, String>> = emptyList(),
    val triples: List<String> = emptyList(),
    val backtickStrings: Boolean = false,
    val multilineStrings: Boolean = false,
    val caseInsensitive: Boolean = false,
) {
    fun numberStart(char: Char): Boolean = char.isDigit()

    fun numberPart(char: Char): Boolean = char.isLetterOrDigit() || char == '_' || char == 'x'

    fun identifierStart(char: Char): Boolean =
        char.isLetter() || char == '_' || char == '$' || (caseInsensitive && char.isDigit())

    fun identifierPart(char: Char): Boolean = char.isLetterOrDigit() || char == '_' || char == '$'

    fun classify(word: String, followedBy: Char?): TokenKind {
        val lookup = if (caseInsensitive) word.lowercase() else word
        return when {
            keywords.contains(lookup) -> TokenKind.Keyword
            types.contains(lookup) -> TokenKind.Type
            followedBy == '(' -> TokenKind.Function
            else -> TokenKind.Plain
        }
    }
}

private fun nextNonSpace(text: String, from: Int): Char? {
    var index = from
    while (index < text.length && text[index].isWhitespace()) index++
    return text.getOrNull(index)
}

private fun keyword(vararg words: String): Set<String> = words.toSet()

private val ShellKeywords = keyword(
    "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case", "esac",
    "function", "in", "select", "time", "coproc", "return", "exit", "break", "continue", "local",
    "export", "readonly", "declare", "typeset", "unset", "set", "shift", "source", "alias",
    "echo", "printf", "cd", "pwd", "read", "eval", "exec", "trap", "wait", "test", "true", "false",
)

/** Normalize a fence info string or file extension into one of the known grammars. */
internal fun languageSpec(language: String?): LanguageSpec? {
    val name = language?.trim()?.lowercase()?.substringBefore(',')?.substringBefore(' ') ?: return null
    return when (name) {
        "kotlin", "kt", "kts", "java", "gradle" -> LanguageSpec(
            keywords = keyword(
                "fun", "val", "var", "class", "object", "interface", "enum", "data", "sealed",
                "abstract", "open", "override", "private", "public", "protected", "internal",
                "return", "if", "else", "when", "while", "for", "do", "break", "continue", "try",
                "catch", "finally", "throw", "throws", "this", "super", "null", "true", "false",
                "is", "in", "as", "typeof", "new", "package", "import", "companion", "init",
                "constructor", "suspend", "inline", "operator", "infix", "lateinit", "by", "where",
                "out", "reified", "vararg", "crossinline", "noinline", "typealias", "annotation",
                "const", "expect", "actual", "external", "final", "static", "void", "int", "long",
                "boolean", "double", "float", "char", "short", "byte", "extends", "implements",
                "synchronized", "volatile", "transient", "instanceof", "native", "strictfp",
            ),
            types = keyword(
                "String", "Int", "Long", "Boolean", "Double", "Float", "Char", "Short", "Byte",
                "Any", "Unit", "Nothing", "List", "MutableList", "Map", "MutableMap", "Set",
                "MutableSet", "Array", "Pair", "Triple", "Sequence", "Result", "Exception",
                "Throwable", "Runnable", "Thread", "Object", "Integer", "Number", "Comparable",
            ),
            lineComments = listOf("//"),
            blockComments = listOf("/*" to "*/"),
            triples = listOf("\"\"\""),
            multilineStrings = true,
        )

        "rust", "rs" -> LanguageSpec(
            keywords = keyword(
                "fn", "let", "mut", "const", "static", "struct", "enum", "trait", "impl", "for",
                "while", "loop", "if", "else", "match", "return", "break", "continue", "move",
                "ref", "pub", "crate", "super", "self", "Self", "use", "mod", "as", "in", "where",
                "unsafe", "async", "await", "dyn", "true", "false", "type", "default", "extern",
                "macro_rules", "union", "box", "yield", "try",
            ),
            types = keyword(
                "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128",
                "usize", "f32", "f64", "bool", "char", "str", "String", "Vec", "Option", "Result",
                "Box", "Rc", "Arc", "Cell", "RefCell", "HashMap", "HashSet", "BTreeMap", "Cow",
            ),
            lineComments = listOf("//"),
            blockComments = listOf("/*" to "*/"),
            multilineStrings = false,
        )

        "python", "py", "python3" -> LanguageSpec(
            keywords = keyword(
                "def", "class", "return", "if", "elif", "else", "for", "while", "break",
                "continue", "pass", "import", "from", "as", "try", "except", "finally", "raise",
                "with", "lambda", "yield", "global", "nonlocal", "del", "assert", "async", "await",
                "and", "or", "not", "is", "in", "None", "True", "False", "match", "case", "self",
            ),
            types = keyword(
                "int", "float", "str", "bool", "bytes", "list", "dict", "set", "tuple", "object",
                "Exception", "ValueError", "TypeError", "Optional", "List", "Dict", "Any",
                "Iterator", "Iterable", "Path",
            ),
            lineComments = listOf("#"),
            triples = listOf("\"\"\"", "'''"),
            multilineStrings = true,
        )

        "javascript", "js", "jsx", "typescript", "ts", "tsx", "node", "bun" -> LanguageSpec(
            keywords = keyword(
                "function", "const", "let", "var", "class", "extends", "implements", "interface",
                "type", "enum", "return", "if", "else", "for", "while", "do", "break", "continue",
                "switch", "case", "default", "try", "catch", "finally", "throw", "new", "delete",
                "typeof", "instanceof", "in", "of", "this", "super", "null", "undefined", "true",
                "false", "async", "await", "yield", "import", "export", "from", "as", "static",
                "get", "set", "public", "private", "protected", "readonly", "declare", "namespace",
                "satisfies", "keyof", "infer", "is",
            ),
            types = keyword(
                "string", "number", "boolean", "any", "unknown", "never", "void", "object", "Array",
                "Promise", "Map", "Set", "Date", "Error", "RegExp", "JSON", "Math", "Symbol",
            ),
            lineComments = listOf("//"),
            blockComments = listOf("/*" to "*/"),
            backtickStrings = true,
            multilineStrings = false,
        )

        "go" -> LanguageSpec(
            keywords = keyword(
                "func", "var", "const", "type", "struct", "interface", "map", "chan", "package",
                "import", "return", "if", "else", "for", "range", "switch", "case", "default",
                "break", "continue", "goto", "fallthrough", "defer", "go", "select", "nil", "true",
                "false", "make", "new", "append", "len", "cap", "copy", "delete", "panic", "recover",
            ),
            types = keyword(
                "int", "int8", "int16", "int32", "int64", "uint", "uint8", "uint16", "uint32",
                "uint64", "uintptr", "float32", "float64", "complex64", "complex128", "string",
                "bool", "byte", "rune", "error", "any",
            ),
            lineComments = listOf("//"),
            blockComments = listOf("/*" to "*/"),
            backtickStrings = true,
            multilineStrings = true,
        )

        "c", "h", "cpp", "cc", "cxx", "hpp", "objectivec", "objc" -> LanguageSpec(
            keywords = keyword(
                "if", "else", "for", "while", "do", "switch", "case", "default", "break",
                "continue", "return", "goto", "struct", "union", "enum", "typedef", "static",
                "extern", "const", "volatile", "register", "inline", "sizeof", "void", "class",
                "public", "private", "protected", "virtual", "override", "final", "template",
                "typename", "namespace", "using", "new", "delete", "this", "nullptr", "true",
                "false", "try", "catch", "throw", "noexcept", "constexpr", "auto", "decltype",
                "operator", "friend", "explicit", "mutable",
            ),
            types = keyword(
                "int", "long", "short", "char", "float", "double", "bool", "signed", "unsigned",
                "size_t", "ssize_t", "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t",
                "uint16_t", "uint32_t", "uint64_t", "string", "vector", "map", "set", "pair",
                "shared_ptr", "unique_ptr", "std",
            ),
            lineComments = listOf("//"),
            blockComments = listOf("/*" to "*/"),
        )

        "json", "jsonc" -> LanguageSpec(
            keywords = keyword("true", "false", "null"),
            types = emptySet(),
            lineComments = if (name == "jsonc") listOf("//") else emptyList(),
            blockComments = if (name == "jsonc") listOf("/*" to "*/") else emptyList(),
        )

        "yaml", "yml" -> LanguageSpec(
            keywords = keyword("true", "false", "null", "yes", "no", "on", "off", "~"),
            types = emptySet(),
            lineComments = listOf("#"),
        )

        "toml", "ini", "properties", "conf" -> LanguageSpec(
            keywords = keyword("true", "false"),
            types = emptySet(),
            lineComments = listOf("#", ";"),
        )

        "sh", "bash", "shell", "zsh", "fish", "console" -> LanguageSpec(
            keywords = ShellKeywords,
            types = emptySet(),
            lineComments = listOf("#"),
            backtickStrings = true,
            multilineStrings = true,
        )

        "sql" -> LanguageSpec(
            keywords = keyword(
                "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
                "create", "table", "alter", "drop", "index", "view", "join", "inner", "left",
                "right", "outer", "on", "group", "by", "order", "having", "limit", "offset",
                "union", "all", "distinct", "as", "and", "or", "not", "null", "is", "in", "like",
                "between", "exists", "case", "when", "then", "else", "end", "primary", "key",
                "foreign", "references", "default", "constraint", "unique",
            ),
            types = keyword(
                "int", "integer", "bigint", "smallint", "text", "varchar", "char", "boolean",
                "date", "timestamp", "timestamptz", "numeric", "decimal", "real", "double",
                "json", "jsonb", "uuid", "bytea",
            ),
            lineComments = listOf("--"),
            blockComments = listOf("/*" to "*/"),
            caseInsensitive = true,
        )

        "xml", "html", "svg" -> LanguageSpec(
            keywords = emptySet(),
            types = emptySet(),
            blockComments = listOf("<!--" to "-->"),
        )

        "css", "scss", "less" -> LanguageSpec(
            keywords = keyword("important", "media", "import", "keyframes", "supports", "from", "to"),
            types = emptySet(),
            blockComments = listOf("/*" to "*/"),
        )

        "makefile", "make", "dockerfile", "docker", "diff", "patch", "text", "plain", "log" -> null
        else -> null
    }
}

/** The grammar implied by a file extension, for diffs and file rows. */
internal fun languageFromPath(path: String): String? {
    val name = path.substringAfterLast('/').lowercase()
    return when {
        name == "dockerfile" -> "dockerfile"
        name == "makefile" -> "makefile"
        !name.contains('.') -> null
        else -> name.substringAfterLast('.')
    }
}

/** Highlight limits, mirroring `codex-rs/tui/src/render/highlight.rs`. */
internal const val SyntaxHighlightMaxBytes = 512 * 1024
internal const val SyntaxHighlightMaxLines = 10_000
internal const val SyntaxHighlightMaxLineBytes = 4 * 1024
