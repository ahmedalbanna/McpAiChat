package com.mcpai.chat.util

/** Inline emphasis a run of text can carry. */
enum class MdSpan { BOLD, ITALIC, CODE }

/**
 * A run of inline text inside a block.
 *
 * Emphasis lives here rather than as spans on the final string so the whole
 * document can be parsed (and tested) without an Android runtime: the UI layer
 * turns [spans] into `CharacterStyle`s and [link] into a `URLSpan`.
 */
data class MdInline(
    val text: String,
    val spans: Set<MdSpan> = emptySet(),
    val link: String? = null,
)

/** One block-level element of a Markdown document. */
sealed interface MdBlock {
    data class Heading(val level: Int, val content: List<MdInline>) : MdBlock
    data class Paragraph(val content: List<MdInline>) : MdBlock
    data class CodeBlock(val language: String?, val code: String) : MdBlock
    data class ListItem(val ordered: Boolean, val index: Int, val content: List<MdInline>) : MdBlock
    data class Quote(val content: List<MdInline>) : MdBlock

    /** A horizontal rule (`---`, `***`, `___`). */
    object Rule : MdBlock
}

/**
 * Parses the Markdown subset models commonly emit.
 *
 * This is deliberately a parser, not a renderer: it has no Android imports, so
 * every rule here is covered by JVM unit tests and the risky decisions stay out
 * of the UI layer. It also has to be safe on a **partial** document, because the
 * assistant stream is styled while it is still arriving.
 *
 * Supported: ATX headings, `**bold**` / `*italic*` / `***both***` (and `_`),
 * `` `code` ``, fenced code blocks, `-`/`*`/`+` bullets, `1.` ordered lists,
 * `>` quotes, `---` rules, `[label](url)` links, and `\` escapes.
 * Tolerated: an unclosed fence, an unclosed emphasis run (rendered literally),
 * and a table dumped as plain text.
 */
object Markdown {

    private val ATX = Regex("""^ {0,3}(#{1,6})(?:[ \t]+(.*))?[ \t]*$""")
    private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})[ \t]*([^`]*)$""")
    private val BULLET = Regex("""^ {0,3}([-*+])[ \t]+(.*)$""")
    private val ORDERED = Regex("""^ {0,3}(\d{1,3})[.)][ \t]+(.*)$""")
    private val QUOTE = Regex("""^ {0,3}>[ \t]?(.*)$""")
    private val RULE = Regex("""^ {0,3}([-*_])[ \t]*\1[ \t]*\1[\s\-*_]*$""")
    private val LINK = Regex("""\[([^\[\]]+)\]\(([^()\s]+)\)""")

    fun parse(markdown: String): List<MdBlock> {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<MdBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            if (line.trim().isEmpty()) {
                i++
                continue
            }

            val fence = FENCE.matchEntire(line)
            val atx = ATX.matchEntire(line)
            val bullet = BULLET.matchEntire(line)
            val ordered = ORDERED.matchEntire(line)
            val quote = QUOTE.matchEntire(line)

            when {
                // A horizontal rule must be checked before the bullet rule:
                // `---` and `***` match both, and a rule is not a list item.
                RULE.matches(line) -> {
                    blocks += MdBlock.Rule
                    i++
                }

                fence != null -> {
                    val marker = fence.groupValues[1]
                    val language = fence.groupValues[2].trim().ifEmpty { null }
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(marker)) {
                        code += lines[i]
                        i++
                    }
                    // Skip the closing fence if one is there; a stray fence
                    // simply ends the block at the end of the input.
                    if (i < lines.size) i++
                    blocks += MdBlock.CodeBlock(language, code.joinToString("\n").trimEnd())
                }

                atx != null -> {
                    val level = atx.groupValues[1].length
                    // `###` with no text is an empty heading and is dropped.
                    val content = atx.groupValues[2].trim().trimEnd('#').trim()
                    // An empty heading is not worth a block of its own.
                    if (content.isNotEmpty()) blocks += MdBlock.Heading(level, parseInline(content))
                    i++
                }

                bullet != null -> {
                    blocks += MdBlock.ListItem(
                        ordered = false,
                        index = 0,
                        content = parseInline(bullet.groupValues[2].trimEnd()),
                    )
                    i++
                }

                ordered != null -> {
                    blocks += MdBlock.ListItem(
                        ordered = true,
                        index = ordered.groupValues[1].toIntOrNull() ?: 1,
                        content = parseInline(ordered.groupValues[2].trimEnd()),
                    )
                    i++
                }

                quote != null -> {
                    // Consecutive `>` lines are one quote, joined by newlines.
                    val parts = mutableListOf(quote.groupValues[1])
                    i++
                    while (i < lines.size) {
                        val q = QUOTE.matchEntire(lines[i]) ?: break
                        parts += q.groupValues[1]
                        i++
                    }
                    blocks += MdBlock.Quote(parseInline(parts.joinToString("\n").trimEnd()))
                }

                else -> {
                    // Everything else is a paragraph: consume lines until a
                    // blank line or the start of another block.
                    val paragraph = mutableListOf(line.trim())
                    i++
                    while (i < lines.size) {
                        val next = lines[i]
                        if (next.trim().isEmpty() ||
                            RULE.matches(next) ||
                            ATX.matches(next) ||
                            FENCE.matches(next) ||
                            BULLET.matches(next) ||
                            ORDERED.matches(next) ||
                            QUOTE.matches(next)
                        ) break
                        paragraph += next.trim()
                        i++
                    }
                    blocks += MdBlock.Paragraph(parseInline(paragraph.joinToString("\n")))
                }
            }
        }

        return blocks
    }

    /** Strips syntax down to the readable text, for one-line previews. */
    fun plain(markdown: String): String {
        val sb = StringBuilder()
        for (block in parse(markdown).map { it.asText() }) {
            if (block.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(block)
            }
        }
        // A collapsed preview can show leftover markers as punctuation; drop
        // the doubled spaces so the line reads as a sentence.
        return sb.toString().replace(Regex("""[ \t]{2,}"""), " ").trim()
    }

    private fun MdBlock.asText(): String = when (this) {
        is MdBlock.Heading -> inlineText(content)
        is MdBlock.Paragraph -> inlineText(content)
        is MdBlock.ListItem -> inlineText(content)
        is MdBlock.Quote -> inlineText(content)
        is MdBlock.CodeBlock -> code.trim()
        MdBlock.Rule -> ""
    }

    private fun inlineText(runs: List<MdInline>): String =
        runs.joinToString("") { it.text }.trim()

    // ---- inline -------------------------------------------------------------

    /** Splits one line into styled runs. Never throws, never reorders text. */
    internal fun parseInline(text: String): List<MdInline> {
        val out = mutableListOf<MdInline>()
        val plain = StringBuilder()

        fun flush() {
            if (plain.isNotEmpty()) {
                out += MdInline(plain.toString())
                plain.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length && isEscapable(text[i + 1]) -> {
                    plain.append(text[i + 1])
                    i += 2
                }

                c == '`' -> {
                    // Inline code: the content is taken verbatim, no nested runs.
                    val close = text.indexOf('`', i + 1)
                    if (close > i + 1) {
                        flush()
                        out += MdInline(text.substring(i + 1, close), spans = setOf(MdSpan.CODE))
                        i = close + 1
                    } else {
                        plain.append(c); i++
                    }
                }

                c == '*' || c == '_' -> {
                    val emphasis = readEmphasis(text, i)
                    if (emphasis != null) {
                        flush()
                        out += emphasis.first
                        i = emphasis.second
                    } else {
                        plain.append(c); i++
                    }
                }

                c == '[' -> {
                    val link = readLink(text, i)
                    if (link != null) {
                        flush()
                        out += link.first
                        i = link.second
                    } else {
                        plain.append(c); i++
                    }
                }

                else -> {
                    plain.append(c); i++
                }
            }
        }
        flush()
        return out
    }

    /**
     * Reads an emphasis run starting at [start] as (runs, end index).
     *
     * The closer must have the **same length** as the opener, otherwise
     * `*a **b** c*` would close on the inner `**` and swallow the rest of the
     * paragraph. A single `_` inside a word is not emphasis, so
     * `snake_case_name` survives; a doubled `__` or any `*` still is.
     */
    private fun readEmphasis(text: String, start: Int): Pair<List<MdInline>, Int>? {
        val marker = text[start]
        var open = 0
        while (start + open < text.length && text[start + open] == marker) open++
        if (open > 3) return null
        if (marker == '_' && start > 0 && text[start - 1].isLetterOrDigit()) return null

        val spans = when {
            open >= 3 -> setOf(MdSpan.BOLD, MdSpan.ITALIC)
            open == 2 -> setOf(MdSpan.BOLD)
            else -> setOf(MdSpan.ITALIC)
        }

        var search = start + open
        var fallback: Pair<List<MdInline>, Int>? = null

        while (search < text.length) {
            val found = text.indexOf(marker, search)
            if (found < 0) break

            var close = 0
            while (found + close < text.length && text[found + close] == marker) close++

            val content = text.substring(start + open, found)
            val usable = content.isNotEmpty() &&
                !content.startsWith(' ') &&
                !content.endsWith(' ') &&
                !content.contains('\n')

            if (usable) {
                val after = found + close
                // A `_` closer may not sit inside a word (`snake_case_end`).
                val wordTail = marker == '_' && after < text.length && text[after].isLetterOrDigit()
                if (!wordTail) {
                    val inner = parseInline(content).map { it.copy(spans = it.spans + spans) }
                    val candidate = inner to after
                    if (close == open) return candidate
                    // A differently-sized closer is still better than nothing,
                    // so remember it and keep looking for an exact match.
                    if (fallback == null) fallback = candidate
                }
            }
            search = found + maxOf(close, 1)
        }
        return fallback
    }

    private fun readLink(text: String, start: Int): Pair<List<MdInline>, Int>? {
        val m = LINK.find(text.substring(start)) ?: return null
        if (m.range.first != 0) return null
        val url = m.groupValues[2]
        val inner = parseInline(m.groupValues[1]).map { it.copy(link = url) }
        return inner to (start + m.value.length)
    }

    private fun isEscapable(c: Char): Boolean = c in "\\`*_{}[]()#+-.!|>~"
}
