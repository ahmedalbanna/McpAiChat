package com.mcpai.chat.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcpai.chat.ui.theme.chatColors
import com.mcpai.chat.util.MdAlignment
import com.mcpai.chat.util.MdBlock
import com.mcpai.chat.util.MdInline
import com.mcpai.chat.util.MdSpan
import com.mcpai.chat.util.Markdown

/**
 * Renders Markdown as Compose text.
 *
 * Streaming stays the caller's job: while the assistant is mid-stream this
 * composable re-parses every new text, so callers should show raw text until
 * the message completes and only then switch to Markdown (as the legacy path
 * did).
 */
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = chatColors.bubbleAiText,
) {
    val blocks = remember(markdown) { Markdown.parse(markdown) }
    val grouped = remember(blocks) { groupBlocks(blocks) }

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            grouped.forEach { group ->
                when (group) {
                    is Group.SingleBlock -> BlockItem(group.block, textColor)
                    is Group.ListGroup ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            group.blocks.forEach { BlockItem(it, textColor) }
                        }
                }
            }
        }
    }
}

private sealed interface Group {
    data class SingleBlock(val block: MdBlock) : Group
    data class ListGroup(val blocks: List<MdBlock>) : Group
}

private fun groupBlocks(blocks: List<MdBlock>): List<Group> {
    val out = mutableListOf<Group>()
    var listBuffer = mutableListOf<MdBlock>()

    fun flushList() {
        if (listBuffer.isNotEmpty()) out += Group.ListGroup(listBuffer.toList())
        listBuffer = mutableListOf()
    }

    for (block in blocks) {
        if (block is MdBlock.ListItem) {
            listBuffer += block
        } else {
            flushList()
            out += Group.SingleBlock(block)
        }
    }
    flushList()
    return out
}

/** Per-render color bundle resolved in composable scope and passed down. */
private data class MdStyles(
    val textColor: Color,
    val muted: Color,
    val primary: Color,
    val codeBackground: Color,
    val strikethrough: Boolean = true,
    val uriHandler: androidx.compose.ui.platform.UriHandler? = null,
)

@Composable
private fun BlockItem(block: MdBlock, textColor: Color) {
    val styles = MdStyles(
        textColor = textColor,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
        primary = MaterialTheme.colorScheme.primary,
        codeBackground = chatColors.codeBlockBg,
        uriHandler = LocalUriHandler.current,
    )

    when (block) {
        is MdBlock.Heading -> Text(
            text = inline(block.content, styles),
            style = TextStyle(
                fontSize = when (block.level) {
                    1 -> 20.sp
                    2 -> 18.sp
                    else -> 15.sp
                },
                lineHeight = when (block.level) {
                    1 -> 25.sp
                    2 -> 22.5f.sp
                    else -> 18.75f.sp
                },
                fontWeight = FontWeight.Bold,
                color = textColor,
            ),
        )

        is MdBlock.Paragraph -> BodyText(
            runs = block.content,
            styles = styles,
        )

        is MdBlock.CodeBlock -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(chatColors.codeBlockBg)
                .padding(10.dp),
        ) {
            Text(
                text = block.code,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.8f.sp,
                    color = textColor,
                ),
            )
            if (block.language != null) {
                Text(
                    text = block.language,
                    style = MaterialTheme.typography.labelSmall,
                    color = styles.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        is MdBlock.ListItem -> Row {
            Text(
                text = if (block.ordered) "${block.index}.  " else "•  ",
                style = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 20.7f.sp,
                    color = styles.primary,
                ),
            )
            BodyText(runs = block.content, styles = styles)
        }

        is MdBlock.Quote -> Row {
            Text(
                text = "│  ",
                style = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 20.7f.sp,
                    color = styles.primary,
                ),
            )
            BodyText(runs = block.content, styles = styles.copy(textColor = styles.muted), italic = true)
        }

        MdBlock.Rule -> Divider(
            modifier = Modifier.padding(vertical = 2.dp),
            color = chatColors.divider,
        )

        is MdBlock.Table -> {
            val scroll = rememberScrollState()
            val annotated = remember(block) { tableAnnotated(block, styles) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(chatColors.codeBlockBg)
                    .horizontalScroll(scroll)
                    .padding(10.dp),
            ) {
                Text(
                    text = annotated,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.8f.sp,
                        color = textColor,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BodyText(
    runs: List<MdInline>,
    styles: MdStyles,
    italic: Boolean = false,
) {
    Text(
        text = inline(runs, styles, forceItalic = italic),
        style = TextStyle(
            fontSize = 15.sp,
            lineHeight = 20.7f.sp,
            color = styles.textColor,
        ),
    )
}

private fun inline(
    runs: List<MdInline>,
    styles: MdStyles,
    forceItalic: Boolean = false,
): AnnotatedString = buildAnnotatedString { appendRuns(this@buildAnnotatedString, runs, bold = false, styles, forceItalic) }

private fun appendRuns(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    runs: List<MdInline>,
    bold: Boolean,
    styles: MdStyles,
    forceItalic: Boolean = false,
) {
    for (run in runs) {
        val start = builder.length
        builder.append(run.text)
        val end = builder.length

        for (span in run.spans) {
            when (span) {
                MdSpan.BOLD ->
                    builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)

                MdSpan.ITALIC ->
                    builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)

                MdSpan.CODE ->
                    builder.addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = styles.codeBackground,
                        ),
                        start,
                        end,
                    )

                MdSpan.STRIKETHROUGH ->
                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            }
        }
        if (bold) builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
        if (forceItalic) builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)

        run.link?.let { url ->
            builder.addLink(
                LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(
                        SpanStyle(color = styles.primary, textDecoration = TextDecoration.Underline),
                    ),
                    linkInteractionListener = LinkInteractionListener {
                        runCatching { styles.uriHandler?.openUri(url) }
                    },
                ),
                start,
                end,
            )
        }
    }
}

private fun tableAnnotated(block: MdBlock.Table, styles: MdStyles): AnnotatedString {
    val columns = block.headers.size
    if (columns == 0) return AnnotatedString("")

    val widths = (0 until columns).map { col ->
        (listOf(inlineText(block.headers[col])) +
            block.rows.map { row -> inlineText(row.getOrNull(col)) })
            .maxOf { it.length }
            .coerceAtLeast(3)
    }

    return buildAnnotatedString {
        fun padding(cell: List<MdInline>, col: Int): Pair<Int, Int> {
            val plain = inlineText(cell).length
            val extra = (widths[col] - plain).coerceAtLeast(0)
            return when (block.alignments.getOrNull(col) ?: MdAlignment.NONE) {
                MdAlignment.RIGHT -> extra to 0
                MdAlignment.CENTER -> {
                    val left = extra / 2
                    left to (extra - left)
                }

                else -> 0 to extra
            }
        }

        fun appendRow(row: List<List<MdInline>>, headerRow: Boolean) {
            row.forEachIndexed { index, cell ->
                val (left, right) = padding(cell, index)
                if (left > 0) append(" ".repeat(left))
                val cellStart = length
                appendRuns(this, cell, bold = headerRow, styles)
                if (right > 0) append(" ".repeat(right))
                if (index != columns - 1) append(" | ")
                if (headerRow) {
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                        cellStart,
                        length,
                    )
                }
            }
        }

        appendRow(block.headers, headerRow = true)
        append("\n")

        val sepStart = length
        append(widths.joinToString("-+-") { "-".repeat(it) })
        addStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                color = styles.muted,
            ),
            sepStart,
            length,
        )

        for (row in block.rows) {
            append("\n")
            appendRow(row, headerRow = false)
        }

        addStyle(
            SpanStyle(fontFamily = FontFamily.Monospace),
            0,
            length,
        )
    }
}

private fun inlineText(runs: List<MdInline>?): String =
    runs?.joinToString("") { it.text }?.trim().orEmpty()
