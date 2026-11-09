package com.mcpai.chat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    // ---- blocks -------------------------------------------------------------

    @Test
    fun `plain text is one paragraph`() {
        val blocks = Markdown.parse("hello world")
        assertEquals(1, blocks.size)
        val paragraph = blocks.single() as MdBlock.Paragraph
        assertEquals("hello world", paragraph.content.joinToString("") { it.text })
    }

    @Test
    fun `consecutive lines join into one paragraph`() {
        val blocks = Markdown.parse("first line\nsecond line")
        val paragraph = blocks.single() as MdBlock.Paragraph
        assertEquals("first line\nsecond line", paragraph.content.joinToString("") { it.text })
    }

    @Test
    fun `headings keep their level and drop the hashes`() {
        val blocks = Markdown.parse("# One\n\n## Two\n\n###### Six")
        val levels = blocks.filterIsInstance<MdBlock.Heading>().map { it.level }
        assertEquals(listOf(1, 2, 6), levels)
        assertEquals("One", textOf(blocks[0] as MdBlock.Heading))
    }

    @Test
    fun `empty heading is dropped`() {
        assertEquals(0, Markdown.parse("###").size)
        assertEquals(0, Markdown.parse("###   ").size)
    }

    @Test
    fun `fenced code block captures every line and the language`() {
        val blocks = Markdown.parse("```kotlin\nfun main() {\n  println(1)\n}\n```")
        val code = blocks.single() as MdBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("fun main() {\n  println(1)\n}", code.code)
    }

    @Test
    fun `an unclosed fence still renders its content`() {
        // A stream that stops mid-fence must not swallow the rest of the text.
        val blocks = Markdown.parse("```\nsome code")
        val code = blocks.single() as MdBlock.CodeBlock
        assertEquals("some code", code.code)
    }

    @Test
    fun `markdown inside a code fence stays verbatim`() {
        val blocks = Markdown.parse("```\n**not bold** and `code`\n```")
        val code = blocks.single() as MdBlock.CodeBlock
        assertEquals("**not bold** and `code`", code.code)
    }

    @Test
    fun `bullets become unordered list items`() {
        val blocks = Markdown.parse("- one\n- two\n- three")
        val items = blocks.filterIsInstance<MdBlock.ListItem>()
        assertEquals(3, items.size)
        assertTrue(items.all { !it.ordered })
        assertEquals(listOf("one", "two", "three"), items.map { textOf(it) })
    }

    @Test
    fun `ordered items keep their number`() {
        val blocks = Markdown.parse("1. alpha\n2. beta")
        val items = blocks.filterIsInstance<MdBlock.ListItem>()
        assertEquals(listOf(1, 2), items.map { it.index })
        assertTrue(items.all { it.ordered })
    }

    @Test
    fun `consecutive quote lines become one block`() {
        val blocks = Markdown.parse("> first\n> second")
        val quote = blocks.single() as MdBlock.Quote
        assertEquals("first\nsecond", textOf(quote))
    }

    @Test
    fun `horizontal rules are not list items`() {
        val blocks = Markdown.parse("above\n\n---\n\nbelow")
        assertEquals(3, blocks.size)
        assertTrue(blocks[1] is MdBlock.Rule)
    }

    @Test
    fun `blank input produces nothing`() {
        assertTrue(Markdown.parse("").isEmpty())
        assertTrue(Markdown.parse("\n\n   \n").isEmpty())
    }

    // ---- inline -------------------------------------------------------------

    @Test
    fun `bold and italic runs carry the right span`() {
        val runs = Markdown.parseInline("a **b** c *d* e ***f***")
        assertEquals(
            listOf("a ", "b", " c ", "d", " e ", "f"),
            runs.map { it.text },
        )
        assertEquals(setOf(MdSpan.BOLD), runs[1].spans)
        assertEquals(setOf(MdSpan.ITALIC), runs[3].spans)
        assertEquals(setOf(MdSpan.BOLD, MdSpan.ITALIC), runs[5].spans)
    }

    @Test
    fun `underscores work outside a word`() {
        val runs = Markdown.parseInline("_italic_ and __bold__")
        assertEquals(setOf(MdSpan.ITALIC), runs[0].spans)
        assertEquals(setOf(MdSpan.BOLD), runs[2].spans)
    }

    @Test
    fun `a lone underscore inside a word is not emphasis`() {
        // Identifiers come out of tool results and code every day.
        val runs = Markdown.parseInline("call snake_case_name now")
        assertEquals(1, runs.size)
        assertTrue(runs.single().spans.isEmpty())
        assertEquals("call snake_case_name now", runs.single().text)
    }

    @Test
    fun `inline code keeps its markers out of the text`() {
        val runs = Markdown.parseInline("use `npm install` first")
        assertEquals("use ", runs[0].text)
        assertEquals("npm install", runs[1].text)
        assertEquals(setOf(MdSpan.CODE), runs[1].spans)
    }

    @Test
    fun `an unclosed emphasis run stays literal`() {
        val runs = Markdown.parseInline("half **bold")
        assertEquals(1, runs.size)
        assertEquals("half **bold", runs.single().text)
        assertTrue(runs.single().spans.isEmpty())
    }

    @Test
    fun `an unclosed backtick stays literal`() {
        val runs = Markdown.parseInline("half `code")
        assertEquals("half `code", runs.joinToString("") { it.text })
    }

    @Test
    fun `an unmatched backtick does not hide later pipes`() {
        val blocks = Markdown.parse(
            """
            | a | b |
            | - | - |
            | `x | y |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(2, table.rows[0].size)
        assertEquals("y", inline(table.rows[0][1]))
    }

    @Test
    fun `code spans pair runs of the same size`() {
        val blocks = Markdown.parse(
            """
            | a | b |
            | - | - |
            | ``code ` | hidden`` | ok |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(2, table.rows[0].size)
        assertEquals("ok", inline(table.rows[0][1]))
    }

    @Test
    fun `an unmatched double backtick does not hide later pipes`() {
        val blocks = Markdown.parse(
            """
            | a | b |
            | - | - |
            | ``code ` | b |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(2, table.rows[0].size)
        assertEquals("b", inline(table.rows[0][1]))
    }

    @Test
    fun `an escaped backtick does not hide pipes`() {
        val blocks = Markdown.parse(
            """
            | a | b |
            | - | - |
            | \`x | y |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(2, table.rows[0].size)
        assertEquals("y", inline(table.rows[0][1]))
    }

    @Test
    fun `a code-span pipe does not split the cell`() {
        val blocks = Markdown.parse(
            """
            | Cell | Note |
            | ---- | ---- |
            | `a | b` | ok |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(2, table.headers.size)
        assertEquals(1, table.rows.size)
        assertEquals("a | b", inline(table.rows[0][0]))
        assertEquals("ok", inline(table.rows[0][1]))
    }

    @Test
    fun `an escaped pipe keeps the cell together`() {
        val blocks = Markdown.parse(
            """
            | Cell |
            | ---- |
            | a \| b |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals("a | b", inline(table.rows[0][0]))
    }

    @Test
    fun `a double backslash before a pipe is a delimiter`() {
        val blocks = Markdown.parse(
            """
            | a | b |
            | - | - |
            | x\\|y z |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(2, table.rows[0].size)
        assertEquals("x\\", inline(table.rows[0][0]))
        assertEquals("y z", inline(table.rows[0][1]))
    }

    @Test
    fun `an escaped backslash and escaped pipe stay one cell`() {
        val blocks = Markdown.parse(
            """
            | a |
            | - |
            | x\\\|y |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(1, table.rows[0].size)
        assertEquals("x\\|y", inline(table.rows[0][0]))
    }

    @Test
    fun `a table may omit the outer pipes`() {
        val blocks = Markdown.parse(
            """
            Name | Built
            ---- | -----
            A | MIT
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals("Name", inline(table.headers[0]))
        assertEquals("MIT", inline(table.rows[0][1]))
    }

    @Test
    fun `a separator with mixed dashes and colons is accepted`() {
        val blocks = Markdown.parse(
            """
            | a | b | c | d | e |
            | --- | :-- | :---: | ---: | :---|
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(
            listOf(MdAlignment.NONE, MdAlignment.LEFT, MdAlignment.CENTER, MdAlignment.RIGHT, MdAlignment.LEFT),
            table.alignments,
        )
    }

    @Test
    fun `short and long body rows are normalised to the header`() {
        val blocks = Markdown.parse(
            """
            | a | b |
            | --- | --- |
            | only |
            | 1 | 2 | 3 |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(2, table.rows.size)
        // A short row is padded with empty cells.
        assertEquals(2, table.rows[0].size)
        assertEquals(listOf("only", ""), table.rows[0].map { inline(it) })
        // Extra cells beyond the header count are dropped.
        assertEquals(listOf("1", "2"), table.rows[1].map { inline(it) })
    }

    @Test
    fun `a table survives prose before and after`() {
        val blocks = Markdown.parse(
            """
            Runway.

            | a | b |
            | - | - |
            | 1 | 2 |

            End noise.
            """.trimIndent(),
        )
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
        assertTrue(blocks[1] is MdBlock.Table)
        assertTrue(blocks[2] is MdBlock.Paragraph)
        assertEquals("End noise.", inline((blocks[2] as MdBlock.Paragraph).content))
    }

    @Test
    fun `a single-column table parses`() {
        val blocks = Markdown.parse(
            """
            | Only |
            | ---- |
            | one |
            | two |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(1, table.headers.size)
        assertEquals(listOf("one", "two"), table.rows.map { inline(it[0]) })
    }

    @Test
    fun `a table is not confused with a horizontal rule`() {
        val blocks = Markdown.parse("| a | b |\n| --- |\n| 1 |")
        // Separator has one cell, header has two: no table.
        assertTrue(blocks.any { it is MdBlock.Paragraph })
    }

    @Test
    fun `a pipe table becomes rows with parsed cells`() {
        val blocks = Markdown.parse(
            """
            | Name | Built |
            | ---- | -----: |
            | DeepWiki | MIT |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(2, table.headers.size)
        assertEquals(1, table.rows.size)
        assertEquals("Name", inline(table.headers[0]))
        assertEquals("DeepWiki", inline(table.rows[0][0]))
        assertEquals("MIT", inline(table.rows[0][1]))
        assertEquals(MdAlignment.NONE, table.alignments[0])
        assertEquals(MdAlignment.RIGHT, table.alignments[1])
    }

    @Test
    fun `pipe rows without a separator stay prose`() {
        // GFM: no separator means no table, so both rows are plain text - one
        // paragraph, not two.
        val blocks = Markdown.parse("| a | b |\n| c | d |")
        assertEquals(1, blocks.size)
        val paragraph = blocks.single() as MdBlock.Paragraph
        assertEquals("| a | b |\n| c | d |", paragraph.content.joinToString("") { it.text })
    }

    @Test
    fun `a table with a leading pipe on every line parses`() {
        val blocks = Markdown.parse("| x |\n| - |\n| y |")
        val table = blocks.single() as MdBlock.Table
        assertEquals(listOf("y"), table.rows.map { row -> inline(row[0]) })
    }

    @Test
    fun `alignment markers are read from the separator`() {
        val blocks = Markdown.parse(
            """
            | a | b | c | d |
            | :-- | :-: | --: | --- |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(
            listOf(MdAlignment.LEFT, MdAlignment.CENTER, MdAlignment.RIGHT, MdAlignment.NONE),
            table.alignments,
        )
    }

    @Test
    fun `emphasis inside a table cell keeps its span`() {
        val blocks = Markdown.parse(
            """
            | What | Meaning |
            | --- | --- |
            | **bold** | plain |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        val first = table.rows[0][0]
        assertEquals(setOf(MdSpan.BOLD), first.single().spans)
        assertEquals("bold", first.single().text)
    }

    @Test
    fun `a table in the middle of prose is one block`() {
        val blocks = Markdown.parse(
            """
            Intro text.

            | a | b |
            | - | - |
            | 1 | 2 |

            Trailing text.
            """.trimIndent(),
        )
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
        assertTrue(blocks[1] is MdBlock.Table)
        assertTrue(blocks[2] is MdBlock.Paragraph)
    }

    @Test
    fun `plain strips a table into words`() {
        val text = Markdown.plain("| Tool | Use |\n| --- | --- |\n| read_file | read |")
        assertTrue(text.contains("Tool"))
        assertTrue(text.contains("read_file"))
        assertTrue(text.contains("read"))
    }

    private fun inline(runs: List<MdInline>): String = runs.joinToString("") { it.text }

    @Test
    fun `links carry their url and no emphasis`() {
        val runs = Markdown.parseInline("see [the docs](https://example.com/x) now")
        assertNull(runs.first().link)
        assertEquals("https://example.com/x", runs[1].link)
        assertTrue(runs[1].spans.isEmpty())
    }

    @Test
    fun `a bracket without a url is literal`() {
        val runs = Markdown.parseInline("array[0] is one")
        assertEquals("array[0] is one", runs.joinToString("") { it.text })
        assertTrue(runs.all { it.link == null })
    }

    @Test
    fun `escaped markers are literal`() {
        val runs = Markdown.parseInline("""2 \* 3 \* 4 and \`not code\`""")
        assertEquals("2 * 3 * 4 and `not code`", runs.joinToString("") { it.text })
        assertTrue(runs.all { it.spans.isEmpty() })
    }

    @Test
    fun `emphasis can nest`() {
        val runs = Markdown.parseInline("*outer **inner** outer*")
        assertEquals(setOf(MdSpan.ITALIC), runs[0].spans)
        assertEquals(setOf(MdSpan.ITALIC, MdSpan.BOLD), runs[1].spans)
        assertEquals(setOf(MdSpan.ITALIC), runs[2].spans)
    }

    @Test
    fun `a link can sit inside emphasis`() {
        val runs = Markdown.parseInline("**[the docs](https://example.com)**")
        assertEquals(1, runs.size)
        assertEquals("the docs", runs.single().text)
        assertEquals(setOf(MdSpan.BOLD), runs.single().spans)
        assertEquals("https://example.com", runs.single().link)
    }

    @Test
    fun `an opener longer than any closer falls back to the first usable one`() {
        // `**bold*` is malformed: no closer of the same length exists, so the
        // first usable one is taken and the leftover marker stays visible.
        val runs = Markdown.parseInline("**bold* tail")
        assertEquals(listOf("bold", " tail"), runs.map { it.text })
        assertEquals(setOf(MdSpan.BOLD), runs[0].spans)
    }

    @Test
    fun `arabic text survives emphasis`() {
        val runs = Markdown.parseInline("**نص عربي** عادي")
        assertEquals(setOf(MdSpan.BOLD), runs[0].spans)
        assertEquals("نص عربي", runs[0].text)
    }

    @Test
    fun `nothing is ever lost from the input`() {
        // The invariant that matters most for a streamed renderer: syntax
        // disappears, the words it wrapped never do.
        val inputs = listOf(
            "**a", "*b", "`c", "[d](e", "\\**f", "a_b_c", "```", "~~~",
            "***", "____", "** a **",
            "https://x.y/z", "1. not", "> not", "- not",
        )
        for (input in inputs) {
            val rendered = Markdown.parseInline(input).joinToString("") { it.text }
            assertEquals(
                "text was altered for: <$input>",
                visibleTextOf(input),
                rendered,
            )
        }
    }

    /** What the reader should end up seeing: escapes gone, link syntax gone. */
    private fun visibleTextOf(raw: String): String =
        raw
            .replace(Regex("""\[([^\[\]]+)\]\([^()\s]+\)"""), "$1")
            .replace(Regex("""\\([\\`*_{}\[\]()#+.!|>~-])"""), "$1")

    // ---- plain --------------------------------------------------------------

    @Test
    fun `plain strips markers into one sentence`() {
        val text = """
            ## Title

            A **bold** claim with `code` and a [link](https://example.com).

            - one
            - two

            > quoted

            ```kotlin
            val x = 1
            ```
        """.trimIndent()

        assertEquals(
            "Title A bold claim with code and a link. one two quoted val x = 1",
            Markdown.plain(text),
        )
    }

    @Test
    fun `plain of plain text is a no-op`() {
        assertEquals("just words here", Markdown.plain("just words here"))
    }

    private fun textOf(block: MdBlock): String = when (block) {
        is MdBlock.Heading -> block.content.joinToString("") { it.text }
        is MdBlock.Paragraph -> block.content.joinToString("") { it.text }
        is MdBlock.ListItem -> block.content.joinToString("") { it.text }
        is MdBlock.Quote -> block.content.joinToString("") { it.text }
        is MdBlock.Table -> (block.headers + block.rows.flatten())
            .joinToString(" ") { row -> row.joinToString(" ") { c -> c.text } }
        is MdBlock.CodeBlock -> block.code
        MdBlock.Rule -> ""
    }
}
