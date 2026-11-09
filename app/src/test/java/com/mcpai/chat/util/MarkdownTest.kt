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
        is MdBlock.CodeBlock -> block.code
        MdBlock.Rule -> ""
    }
}
