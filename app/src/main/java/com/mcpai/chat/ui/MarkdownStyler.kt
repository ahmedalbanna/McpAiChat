package com.mcpai.chat.ui

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.google.android.material.R as MaterialR
import com.mcpai.chat.util.MdAlignment
import com.mcpai.chat.util.MdBlock
import com.mcpai.chat.util.MdInline
import com.mcpai.chat.util.MdSpan
import com.mcpai.chat.util.Markdown
import com.mcpai.chat.util.Markdown.inlineText

/**
 * Turns parsed Markdown into an Android `Spanned`.
 *
 * All the decisions about *what* a token means live in [Markdown]; this class
 * only maps them onto platform spans, so `android.text` stays out of the
 * testable layer and nothing here needs a unit test of its own.
 */
object MarkdownStyler {

    /** One blank line between blocks; consecutive list items stay tight. */
    private const val BLOCK_GAP = "\n\n"

    fun render(markdown: String, context: Context): SpannableStringBuilder {
        val out = SpannableStringBuilder()
        val colors = Colors.from(context)
        val blocks = Markdown.parse(markdown)
        var previousWasList = false

        for (block in blocks) {
            val isList = block is MdBlock.ListItem
            if (out.isNotEmpty()) {
                out.append(if (previousWasList && isList) "\n" else BLOCK_GAP)
            }
            previousWasList = isList

            when (block) {
                is MdBlock.Heading -> appendHeading(block, out, colors)
                is MdBlock.Paragraph -> appendRuns(block.content, out, colors)
                is MdBlock.CodeBlock -> appendCode(block, out, colors)
                is MdBlock.ListItem -> appendListItem(block, out, colors)
                is MdBlock.Quote -> appendQuote(block, out, colors)
                is MdBlock.Table -> appendTable(block, out, colors)
                MdBlock.Rule -> out.append("─────────")
            }
        }
        return out
    }

    // ---- pipe tables -------------------------------------------------------

    /**
     * Renders a GFM table as an aligned monospace block.
     *
     * Each cell shows its plain text (emphasis inside cells is flattened), so
     * column alignment stays exact. The header row is bold, the separating
     * dash line is muted, and the whole block gets a subtle background, the
     * same treatment as a fenced code block.
     */
    private fun appendTable(block: MdBlock.Table, out: SpannableStringBuilder, colors: Colors) {
        val mono = TypefaceSpan("monospace")
        val columns = block.headers.size
        if (columns == 0) return

        fun CellText(row: List<List<MdInline>>): List<String> =
            row.map { inlineText(it) }

        val headerText = CellText(block.headers)
        val bodyText = block.rows.map(::CellText)

        fun plainWidth(cell: String): Int = cell.length
        val widths = (0 until columns).map { col ->
            (listOf(headerText.getOrNull(col).orEmpty()) +
                bodyText.map { it.getOrNull(col).orEmpty() })
                .maxOf { plainWidth(it) }
                .coerceAtLeast(3)
        }

        fun alignment(col: Int): MdAlignment =
            block.alignments.getOrNull(col) ?: MdAlignment.NONE

        fun align(cell: String, col: Int): String {
            val w = widths[col]
            return when (alignment(col)) {
                MdAlignment.RIGHT -> cell.padStart(w)
                MdAlignment.CENTER -> {
                    val total = w - cell.length
                    val left = total / 2
                    " ".repeat(left) + cell.padEnd(w - left)
                }

                else -> cell.padEnd(w)
            }
        }

        fun renderRow(row: List<List<MdInline>>, bold: Boolean) {
            val cells = row.mapIndexed { i, cell -> align(inlineText(cell), i) }
            val start = out.length
            out.append(cells.joinToString(" | "))
            val end = out.length
            out.setSpan(mono, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(
                AbsoluteSizeSpan(colors.codeTextSizePx, true),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (bold) {
                out.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // Header
        val tableStart = out.length
        renderRow(block.headers, bold = true)

        // Separator line
        val sepStart = out.length
        out.append("\n")
        out.append(widths.joinToString("-+-") { "-".repeat(it) })
        out.setSpan(mono, sepStart, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(
            AbsoluteSizeSpan(colors.codeTextSizePx, true),
            sepStart,
            out.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        out.setSpan(
            ForegroundColorSpan(colors.meta),
            sepStart,
            out.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        // Body rows
        for (row in block.rows) {
            out.append("\n")
            renderRow(row, bold = false)
        }

        // The whole table gets a subtle background
        out.setSpan(
            BackgroundColorSpan(colors.codeBackground),
            tableStart,
            out.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    // ---- block appenders ----------------------------------------------------

    private fun appendHeading(block: MdBlock.Heading, out: SpannableStringBuilder, colors: Colors) {
        val start = out.length
        appendRuns(block.content, out, colors)
        // A heading is only distinct if it is also emphasised: the size change
        // alone is easy to miss inside a chat bubble.
        val scale = when (block.level) {
            1 -> 1.30f
            2 -> 1.18f
            3 -> 1.08f
            else -> 1.0f
        }
        if (scale != 1.0f) {
            out.setSpan(RelativeSizeSpan(scale), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun appendCode(block: MdBlock.CodeBlock, out: SpannableStringBuilder, colors: Colors) {
        val start = out.length
        out.append(block.code)
        val end = out.length
        out.setSpan(TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(BackgroundColorSpan(colors.codeBackground), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(
            AbsoluteSizeSpan(colors.codeTextSizePx, true),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        // A tag like ```kotlin is metadata, not code: show it smaller and muted.
        val language = block.language
        if (language != null) {
            val labelStart = out.length
            out.append('\n')
            out.append(language)
            out.setSpan(RelativeSizeSpan(0.82f), labelStart, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(
                ForegroundColorSpan(colors.meta),
                labelStart,
                out.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun appendListItem(block: MdBlock.ListItem, out: SpannableStringBuilder, colors: Colors) {
        val marker = if (block.ordered) "${block.index}." else "•"
        val markerStart = out.length
        out.append("$marker  ")
        out.setSpan(
            ForegroundColorSpan(colors.primary),
            markerStart,
            out.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        appendRuns(block.content, out, colors)
    }

    private fun appendQuote(block: MdBlock.Quote, out: SpannableStringBuilder, colors: Colors) {
        val start = out.length
        out.append("│ ")
        out.setSpan(
            ForegroundColorSpan(colors.primary),
            start,
            out.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        appendRuns(block.content, out, colors)
        out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    // ---- inline appends -----------------------------------------------------

    private fun appendRuns(runs: List<MdInline>, out: SpannableStringBuilder, colors: Colors) {
        for (run in runs) {
            val start = out.length
            out.append(run.text)
            val end = out.length

            for (span in run.spans) {
                when (span) {
                    MdSpan.BOLD ->
                        out.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    MdSpan.ITALIC ->
                        out.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    MdSpan.CODE -> {
                        out.setSpan(TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        out.setSpan(
                            BackgroundColorSpan(colors.codeBackground),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                        out.setSpan(
                            RelativeSizeSpan(0.92f),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                }
            }

            if (run.link != null) {
                out.setSpan(URLSpan(run.link), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(
                    ForegroundColorSpan(colors.primary),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    // ---- theme lookup -------------------------------------------------------

    /** The colour roles the rendered text needs, resolved once per render. */
    private class Colors private constructor(
        val primary: Int,
        val meta: Int,
        val codeBackground: Int,
        val codeTextSizePx: Int,
    ) {
        companion object {
            fun from(context: Context): Colors {
                val resources = context.resources
                // Code and tables are shown at 11sp — code reads better small,
                // and tables keep more columns on one line inside a bubble.
                val codeTextSizePx = (11 * resources.displayMetrics.scaledDensity + 0.5f).toInt()
                return Colors(
                    primary = resolve(context, MaterialR.attr.colorPrimary, android.graphics.Color.GRAY),
                    meta = resolve(context, android.R.attr.textColorSecondary, android.graphics.Color.GRAY),
                    // M3 role; older themes fall back to the M2 surface variant.
                    codeBackground = resolve(
                        context,
                        MaterialR.attr.colorSurfaceContainerHighest,
                        resolve(context, MaterialR.attr.colorSurfaceVariant, 0x11000000),
                    ),
                    codeTextSizePx = codeTextSizePx,
                )
            }

            private fun resolve(context: Context, attr: Int, fallback: Int): Int {
                val typed = TypedValue()
                if (!context.theme.resolveAttribute(attr, typed, true)) return fallback
                return if (typed.resourceId != 0) {
                    ContextCompat.getColor(context, typed.resourceId)
                } else {
                    typed.data
                }
            }
        }
    }
}
