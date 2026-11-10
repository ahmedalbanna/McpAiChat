package com.mcpai.chat.util

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/** Inline emphasis a run of text can carry. */
enum class MdSpan { BOLD, ITALIC, CODE, STRIKETHROUGH }

/**
 * A run of inline text inside a block.
 *
 * Emphasis lives here rather than as spans on the final string so the whole
 * document can be parsed (and tested) without an Android runtime: the UI layer
 * turns [spans] into `AnnotatedString` styles and [link] into link annotations.
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

    /**
     * A pipe table. [headers] has one cell per column; [rows] holds the data
     * rows, each with the same number of cells as the header.
     */
    data class Table(
        val headers: List<List<MdInline>>,
        val rows: List<List<List<MdInline>>>,
        val alignments: List<MdAlignment>,
    ) : MdBlock

    /** A horizontal rule (`---`, `***`, `___`). */
    object Rule : MdBlock
}

/** Column alignment taken from the table separator line. */
enum class MdAlignment { LEFT, CENTER, RIGHT, NONE }

/**
 * Parses the Markdown subset models commonly emit.
 *
 * This is deliberately a parser, not a renderer: it has no Android imports, so
 * every rule here is covered by JVM unit tests and the risky decisions stay out
 * of the UI layer. It is safe on a **partial** document, because the assistant
 * stream is styled while it is still arriving.
 *
 * The block/inline grammar is the reference CommonMark implementation with the
 * GFM strikethrough and autolink extensions. Tables are intentionally NOT the
 * GFM extension: the reference table implementation splits cells on raw pipes
 * and does not honour code spans or escaped pipes, and the spec's escape
 * counting differs. This module pre-parses GFM pipe tables with its own
 * (spec-plus) scanner before handing the rest of the document to CommonMark.
 * Tolerated on top of the spec: an unclosed fence and an unclosed emphasis run
 * render as the raw text they are.
 */
object Markdown {

    private val parser: Parser = Parser.builder()
        .extensions(
            listOf(
                StrikethroughExtension.create(),
                AutolinkExtension.create(),
            ),
        )
        .build()

    fun parse(markdown: String): List<MdBlock> {
        if (markdown.isBlank()) return emptyList()
        val normalized = normalize(markdown)

        // A stray fence on its own line (all models emit this mid-stream while
        // the language tag is being typed) is shown as the marker itself,
        // never as an empty invisible code block.
        if (BARE_FENCE.matches(normalized.trim())) {
            return listOf(MdBlock.Paragraph(listOf(MdInline(normalized.trim()))))
        }

        val lines = normalized.split('\n')
        val blocks = mutableListOf<MdBlock>()
        val text = StringBuilder()

        fun flushText() {
            if (text.toString().isBlank()) {
                text.clear()
                return
            }
            blocks += blocksOf(parser.parse(text.toString()))
            text.clear()
        }

        var i = 0
        while (i < lines.size) {
            if (startsTableAt(lines, i)) {
                flushText()
                val headerCells = splitRow(lines[i])
                val alignments = tableSeparatorCells(lines[i + 1])?.map(::alignmentOf) ?: emptyList()
                val columns = headerCells.size
                val bodyRows = mutableListOf<List<List<MdInline>>>()
                var j = i + 2
                while (j < lines.size && TABLE_ROW.matches(lines[j])) {
                    val cells = splitRow(lines[j])
                    val row = cells.map { parseInline(it) }.toMutableList()
                    while (row.size < columns) row += emptyList<MdInline>()
                    bodyRows += row.take(columns)
                    j++
                }
                blocks += MdBlock.Table(
                    headers = headerCells.map { parseInline(it) },
                    rows = bodyRows,
                    alignments = alignments,
                )
                i = j
            } else {
                text.append(lines[i]).append('\n')
                i++
            }
        }
        flushText()
        return blocks.filterNot { block ->
            block is MdBlock.Heading && block.content.all { it.text.isBlank() }
        }
    }

    fun parseInline(markdown: String): List<MdInline> {
        if (markdown.isBlank()) return emptyList()
        val normalized = normalize(markdown)
        val node = parser.parse(normalized).firstChild
        // Inline fragment whose first node is not a paragraph (an unclosed
        // fence, a rule) is shown verbatim instead of vanishing.
        if (node !is Paragraph) return listOf(MdInline(normalized))
        return inlinesOf(node)
    }

    /** Strips syntax down to the readable text, for one-line previews. */
    fun plain(markdown: String): String =
        parse(markdown)
            .joinToString(" ") { it.asText() }
            .replace(Regex("""[ \t]{2,}"""), " ")
            .trim()

    private fun normalize(markdown: String): String =
        markdown.replace("\r\n", "\n").replace('\r', '\n')

    // ---- blocks -------------------------------------------------------------

    private fun blocksOf(node: Node): List<MdBlock> {
        val out = mutableListOf<MdBlock>()
        var child = node.firstChild
        while (child != null) {
            out += convertBlock(child)
            child = child.next
        }
        return out
    }

    private fun convertBlock(node: Node): List<MdBlock> = when (node) {
        is Heading -> {
            val content = inlinesOf(node)
            if (content.all { it.text.isBlank() }) emptyList()
            else listOf(MdBlock.Heading(node.level, content))
        }

        is Paragraph -> listOf(MdBlock.Paragraph(inlinesOf(node)))

        is FencedCodeBlock -> {
            val language = node.info.trim().ifEmpty { null }
            listOf(MdBlock.CodeBlock(language, node.literal.trimEnd('\n')))
        }

        is IndentedCodeBlock ->
            listOf(MdBlock.CodeBlock(null, node.literal.trimEnd('\n')))

        is ThematicBreak -> listOf(MdBlock.Rule)

        is BulletList -> listItemsOf(node, ordered = false)
        is OrderedList -> listItemsOf(node, ordered = true)
        is ListItem -> listOf(convertListItem(node, ordered = false, index = 0))

        is BlockQuote -> {
            // Consecutive quoted paragraphs stay one block, joined by newlines.
            val paragraphs = mutableListOf<String>()
            var inner = node.firstChild
            while (inner != null) {
                if (inner is Paragraph) paragraphs += inner.text()
                inner = inner.next
            }
            if (paragraphs.isEmpty()) emptyList()
            else listOf(MdBlock.Quote(parseInline(paragraphs.joinToString("\n"))))
        }

        // Raw HTML is shown as raw text, never executed, matching how the
        // stream renderer used to treat it.
        is HtmlBlock -> listOf(MdBlock.Paragraph(listOf(MdInline(node.literal.trimEnd('\n')))))
        else -> emptyList()
    }

    /** Flattens nested lists to one item per source row, in document order. */
    private fun listItemsOf(list: Node, ordered: Boolean): List<MdBlock> {
        val items = mutableListOf<MdBlock>()
        val start = (list as? OrderedList)?.markerStartNumber ?: 1
        var child = list.firstChild
        var number = start
        while (child != null) {
            when (child) {
                is ListItem -> {
                    items += convertListItem(child, ordered, number)
                    number++
                }
                is BulletList -> items += listItemsOf(child, ordered = false)
                is OrderedList -> items += listItemsOf(child, ordered = true)
            }
            child = child.next
        }
        return items
    }

    private fun convertListItem(item: ListItem, ordered: Boolean, index: Int): MdBlock.ListItem {
        val paragraph = item.findFirstChild { it is Paragraph } as? Paragraph
        val content = if (paragraph != null) inlinesOf(paragraph) else emptyList()
        return MdBlock.ListItem(ordered, index, content)
    }

    // ---- inlines ------------------------------------------------------------

    private fun inlinesOf(node: Node): List<MdInline> {
        val out = mutableListOf<MdInline>()

        fun walk(n: Node, spans: Set<MdSpan>, link: String?) {
            when (n) {
                is Text -> out += MdInline(n.literal, spans, link)
                is Code -> out += MdInline(n.literal, spans + MdSpan.CODE, link)
                is Emphasis -> children(n).forEach { walk(it, spans + MdSpan.ITALIC, link) }
                is StrongEmphasis -> children(n).forEach { walk(it, spans + MdSpan.BOLD, link) }
                is Strikethrough -> children(n).forEach { walk(it, spans + MdSpan.STRIKETHROUGH, link) }
                is Link -> children(n).forEach { walk(it, spans, n.destination) }
                is Image -> out += MdInline(imageAltOf(n), spans, link)
                is SoftLineBreak, is HardLineBreak -> out += MdInline("\n", spans, link)
                is HtmlInline -> out += MdInline(n.literal, spans, link)
                else -> children(n).forEach { walk(it, spans, link) }
            }
        }

        children(node).forEach { walk(it, emptySet(), null) }
        return mergeAdjacent(out)
    }

    private fun children(node: Node): List<Node> {
        val out = mutableListOf<Node>()
        var child = node.firstChild
        while (child != null) {
            out += child
            child = child.next
        }
        return out
    }

    private fun Node.findFirstChild(predicate: (Node) -> Boolean): Node? {
        var child = firstChild
        while (child != null) {
            if (predicate(child)) return child
            child = child.next
        }
        return null
    }

    private fun imageAltOf(image: Node): String {
        val sb = StringBuilder()

        fun walk(n: Node) {
            when (n) {
                is Text -> sb.append(n.literal)
                is SoftLineBreak -> sb.append('\n')
                else -> n.firstChild?.let(::walk)
            }
        }

        image.firstChild?.let(::walk)
        return sb.toString()
    }

    private fun mergeAdjacent(runs: List<MdInline>): List<MdInline> {
        val out = mutableListOf<MdInline>()
        for (run in runs) {
            val last = out.lastOrNull()
            if (last != null && last.spans == run.spans && last.link == run.link) {
                out[out.size - 1] = last.copy(text = last.text + run.text)
            } else {
                out += run
            }
        }
        return out
    }

    private fun Node.text(): String {
        val sb = StringBuilder()

        fun walk(n: Node) {
            when (n) {
                is Text -> sb.append(n.literal)
                is Code -> sb.append(n.literal)
                is SoftLineBreak, is HardLineBreak -> sb.append('\n')
                else -> children(n).forEach(::walk)
            }
        }

        children(this).forEach(::walk)
        return sb.toString()
    }

    /** Plain text of an inline run list, for the table renderer and previews. */
    internal fun inlineText(runs: List<MdInline>): String =
        runs.joinToString("") { it.text }.trim()

    private fun MdBlock.asText(): String = when (this) {
        is MdBlock.Heading -> inlineText(content)
        is MdBlock.Paragraph -> inlineText(content)
        is MdBlock.ListItem -> inlineText(content)
        is MdBlock.Quote -> inlineText(content)
        is MdBlock.CodeBlock -> code.trim()
        is MdBlock.Table ->
            (headers + rows.flatten()).joinToString(" ") { cell -> inlineText(cell) }.trim()

        MdBlock.Rule -> ""
    }

    // ---- pipe tables ----------------------------------------------------------

    private val BARE_FENCE = Regex("""^(`{3,}|~{3,})\s*$""")

    /**
     * A pipe table row: a `|` anywhere, with optional leading/trailing pipes.
     * What actually gates table parsing is the following separator line.
     */
    private val TABLE_ROW = Regex("""^ {0,3}(?:\|)?[^\n]*\|[^\n]*$""")

    /**
     * A separator line is made only of pipes, dashes, colons and spaces, with
     * at least one dash per column. Anything else means this is not a table.
     */
    private val TABLE_SEPARATOR_CELL = Regex("""^\s*:?-{1,}:?\s*$""")

    /** True iff [line] splits into all-valid dash cells. */
    private fun tableSeparatorCells(line: String): List<String>? {
        if (!line.contains('-')) return null
        val cells = splitRow(line)
        if (cells.isEmpty()) return null
        return if (cells.all { TABLE_SEPARATOR_CELL.matches(it) }) {
            cells
        } else {
            null
        }
    }

    /** True when [lines][index] starts a table whose next line is a separator. */
    private fun startsTableAt(lines: List<String>, index: Int): Boolean {
        val header = lines.getOrNull(index) ?: return false
        val separator = lines.getOrNull(index + 1) ?: return false
        if (!TABLE_ROW.matches(header)) return false

        val headerCells = splitRow(header)
        val alignments = tableSeparatorCells(separator)?.map(::alignmentOf)
        return alignments != null && alignments.size == headerCells.size
    }

    private fun alignmentOf(cell: String): MdAlignment {
        val trimmed = cell.trim()
        val left = trimmed.startsWith(":")
        val right = trimmed.endsWith(":")
        return when {
            left && right -> MdAlignment.CENTER
            right -> MdAlignment.RIGHT
            left -> MdAlignment.LEFT
            else -> MdAlignment.NONE
        }
    }

    /**
     * Splits `| a | b |` into `["a", "b"]`.
     *
     * Outer pipes are optional, `\|` contributes a literal pipe and pipes
     * inside backtick code spans do not split the cell. Code spans follow the
     * Markdown pairing rule: a run of backticks opens only when a later run of
     * the same size can close it, and a backtick preceded by an odd number of
     * backslashes does not open a span.
     */
    private fun splitRow(line: String): List<String> {
        val trimmed = line.trim()
        val body = trimmed.removePrefix("|").removeSuffix("|")
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        val code = codeMask(body)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                code[i] -> {
                    current.append(c)
                    i++
                }

                c == '\\' && i + 1 < body.length && body[i + 1] == '\\' -> {
                    current.append('\\')
                    current.append('\\')
                    i += 2
                }

                c == '\\' && i + 1 < body.length && body[i + 1] == '|' -> {
                    current.append('|')
                    i += 2
                }

                c == '|' -> {
                    cells += current.toString().trim()
                    current.setLength(0)
                    i++
                }

                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        cells += current.toString().trim()
        return cells
    }

    /** Marks every character that belongs to a backtick code span. */
    private fun codeMask(body: String): BooleanArray {
        data class BacktickRun(val start: Int, val endExclusive: Int, val size: Int)

        val runs = mutableListOf<BacktickRun>()
        var i = 0
        while (i < body.length) {
            if (body[i] != '`') {
                i++
                continue
            }
            var j = i
            while (j < body.length && body[j] == '`') j++
            var backslashes = 0
            var k = i - 1
            while (k >= 0 && body[k] == '\\') {
                backslashes++
                k--
            }
            if (backslashes % 2 == 0) runs += BacktickRun(i, j, j - i)
            i = j
        }

        val mask = BooleanArray(body.length)
        val used = BooleanArray(runs.size)
        for (a in runs.indices) {
            if (used[a]) continue
            for (b in a + 1 until runs.size) {
                if (!used[b] && runs[b].size == runs[a].size) {
                    for (t in runs[a].start until runs[b].endExclusive) mask[t] = true
                    used[a] = true
                    used[b] = true
                    break
                }
            }
        }
        return mask
    }
}
