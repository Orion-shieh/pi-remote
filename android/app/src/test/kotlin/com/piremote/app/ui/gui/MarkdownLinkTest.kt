package com.piremote.app.ui.gui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLinkTest {

    @Test
    fun testNormalizeMarkdownUrl() {
        assertEquals("http://127.0.0.1:8765/", normalizeMarkdownUrl("http://127.0.0.1:8765/"))
        assertEquals("http://127.0.0.1:8765", normalizeMarkdownUrl("127.0.0.1:8765"))
        assertEquals("http://localhost:3000/app", normalizeMarkdownUrl("localhost:3000/app"))
        assertEquals("https://github.com", normalizeMarkdownUrl("https://github.com"))
        assertEquals("mailto:support@piremote.com", normalizeMarkdownUrl("mailto:support@piremote.com"))
        assertEquals("file:///storage/emulated/0/file.txt", normalizeMarkdownUrl("file:///storage/emulated/0/file.txt"))
        assertEquals("https://www.google.com", normalizeMarkdownUrl("www.google.com"))
        assertEquals("#overview", normalizeMarkdownUrl("#overview"))
    }

    @Test
    fun testMarkdownLinkParsing() {
        val markdown = "查看项目详情：[点击访问 GitHub](https://github.com/piremote/app)。"
        val annotated = buildMarkdownAnnotatedString(
            markdown = markdown,
            primaryColor = Color.White,
            accentColor = Color.Cyan,
            codeBgColor = Color.DarkGray,
        )

        // The displayed text should have the label "点击访问 GitHub", not the raw [label](url) syntax
        val text = annotated.text
        assertTrue("Should contain link label", text.contains("点击访问 GitHub"))
        assertTrue("Should not contain raw markdown parentheses", !text.contains("](") && !text.contains("[点击访问"))

        // Check link annotations
        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)

        val linkRange = linkAnnotations.first()
        val urlAnnotation = linkRange.item as? LinkAnnotation.Url
        assertNotNull("Should be a LinkAnnotation.Url", urlAnnotation)
        assertEquals("https://github.com/piremote/app", urlAnnotation?.url)

        val linkText = text.substring(linkRange.start, linkRange.end)
        assertEquals("点击访问 GitHub", linkText)
    }

    @Test
    fun testAutolinkParsing() {
        val markdown = "本地服务器启动于 <http://127.0.0.1:8765/demo>，欢迎访问！"
        val annotated = buildMarkdownAnnotatedString(
            markdown = markdown,
            primaryColor = Color.White,
            accentColor = Color.Cyan,
            codeBgColor = Color.DarkGray,
        )

        val text = annotated.text
        assertTrue(text.contains("http://127.0.0.1:8765/demo"))
        assertTrue(!text.contains("<http://"))

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)

        val urlAnnotation = linkAnnotations.first().item as? LinkAnnotation.Url
        assertEquals("http://127.0.0.1:8765/demo", urlAnnotation?.url)
    }

    @Test
    fun testBareUrlParsing() {
        val markdown = "服务已启动，请访问 http://localhost:8765/ 或 https://example.com/api。测试结束。"
        val annotated = buildMarkdownAnnotatedString(
            markdown = markdown,
            primaryColor = Color.White,
            accentColor = Color.Cyan,
            codeBgColor = Color.DarkGray,
        )

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(2, linkAnnotations.size)

        val firstLink = linkAnnotations[0].item as? LinkAnnotation.Url
        val secondLink = linkAnnotations[1].item as? LinkAnnotation.Url

        assertEquals("http://localhost:8765/", firstLink?.url)
        // Ensure trailing punctuation '。' is stripped from the URL
        assertEquals("https://example.com/api", secondLink?.url)
    }

    @Test
    fun testLocalhostWithoutProtocol() {
        val markdown = "本地开发端口是 127.0.0.1:8765/index.html 已经就绪"
        val annotated = buildMarkdownAnnotatedString(
            markdown = markdown,
            primaryColor = Color.White,
            accentColor = Color.Cyan,
            codeBgColor = Color.DarkGray,
        )

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)

        val link = linkAnnotations.first().item as? LinkAnnotation.Url
        assertEquals("http://127.0.0.1:8765/index.html", link?.url)
    }

    @Test
    fun testUserMessageWithModelAndProvider() {
        val msg = UserMessage(
            text = "请编写一个网页",
            model = "deepseek-chat",
            provider = "deepseek",
        )
        assertEquals("请编写一个网页", msg.text)
        assertEquals("deepseek-chat", msg.model)
        assertEquals("deepseek", msg.provider)
    }

    @Test
    fun testBoldItalicWithInlineCode() {
        val markdown = "已更新 ***`D:\\Program\\Pi_Agent\\HANDOFF.md`*** 文件"
        val cyan = Color(0xFF00E5FF)
        val annotated = buildMarkdownAnnotatedString(
            markdown = markdown,
            primaryColor = Color.White,
            accentColor = cyan,
            codeBgColor = Color.DarkGray,
        )

        assertEquals("已更新 D:\\Program\\Pi_Agent\\HANDOFF.md 文件", annotated.text)

        val spanStyles = annotated.spanStyles
        val target = "D:\\Program\\Pi_Agent\\HANDOFF.md"
        val start = annotated.text.indexOf(target)
        val end = start + target.length

        val matchingSpan = spanStyles.find { it.start == start && it.end == end }
        assertNotNull("Should have span for inline code within bold-italic", matchingSpan)
        assertEquals(cyan, matchingSpan?.item?.color)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, matchingSpan?.item?.fontFamily)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, matchingSpan?.item?.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontStyle.Italic, matchingSpan?.item?.fontStyle)
    }

    @Test
    fun testBoldWithInlineCode() {
        val markdown = "文件是 **`D:\\Program\\Pi_Agent\\HANDOFF.md`**"
        val cyan = Color(0xFF00E5FF)
        val annotated = buildMarkdownAnnotatedString(
            markdown = markdown,
            primaryColor = Color.White,
            accentColor = cyan,
            codeBgColor = Color.DarkGray,
        )

        assertEquals("文件是 D:\\Program\\Pi_Agent\\HANDOFF.md", annotated.text)

        val target = "D:\\Program\\Pi_Agent\\HANDOFF.md"
        val start = annotated.text.indexOf(target)
        val end = start + target.length

        val matchingSpan = annotated.spanStyles.find { it.start == start && it.end == end }
        assertNotNull("Should have span for inline code within bold", matchingSpan)
        assertEquals(cyan, matchingSpan?.item?.color)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, matchingSpan?.item?.fontFamily)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, matchingSpan?.item?.fontWeight)
    }

    @Test
    fun testInlineCodeWithWrappedBoldItalic() {
        val markdown = "路径：`***D:\\Program\\Pi_Agent\\HANDOFF.md***`"
        val cyan = Color(0xFF00E5FF)
        val annotated = buildMarkdownAnnotatedString(
            markdown = markdown,
            primaryColor = Color.White,
            accentColor = cyan,
            codeBgColor = Color.DarkGray,
        )

        assertEquals("路径：D:\\Program\\Pi_Agent\\HANDOFF.md", annotated.text)

        val target = "D:\\Program\\Pi_Agent\\HANDOFF.md"
        val start = annotated.text.indexOf(target)
        val end = start + target.length

        val matchingSpan = annotated.spanStyles.find { it.start == start && it.end == end }
        assertNotNull("Should have span for inline code with wrapped bold-italic", matchingSpan)
        assertEquals(cyan, matchingSpan?.item?.color)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, matchingSpan?.item?.fontFamily)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, matchingSpan?.item?.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontStyle.Italic, matchingSpan?.item?.fontStyle)
    }

    @Test
    fun testBoldLinkOutside() {
        val markdown = "查看 **[点击访问 GitHub](https://github.com/piremote/app)** 项目"
        val cyan = Color(0xFF00E5FF)
        val annotated = buildMarkdownAnnotatedString(
            markdown = markdown,
            primaryColor = Color.White,
            accentColor = cyan,
            codeBgColor = Color.DarkGray,
        )

        assertEquals("查看 点击访问 GitHub 项目", annotated.text)

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)
        val urlAnnotation = linkAnnotations.first().item as? LinkAnnotation.Url
        assertNotNull(urlAnnotation)
        assertEquals("https://github.com/piremote/app", urlAnnotation?.url)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, urlAnnotation?.styles?.style?.fontWeight)
    }

    @Test
    fun testBoldLinkInside() {
        val markdown = "查看 [**点击访问 GitHub**](https://github.com/piremote/app) 项目"
        val cyan = Color(0xFF00E5FF)
        val annotated = buildMarkdownAnnotatedString(
            markdown = markdown,
            primaryColor = Color.White,
            accentColor = cyan,
            codeBgColor = Color.DarkGray,
        )

        assertEquals("查看 点击访问 GitHub 项目", annotated.text)

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)
        val urlAnnotation = linkAnnotations.first().item as? LinkAnnotation.Url
        assertNotNull(urlAnnotation)
        assertEquals("https://github.com/piremote/app", urlAnnotation?.url)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, urlAnnotation?.styles?.style?.fontWeight)
    }

    @Test
    fun testLinkWithInlineCode() {
        val markdown = "参考 [`HANDOFF.md`](https://github.com/piremote/app/blob/main/HANDOFF.md) 文件"
        val cyan = Color(0xFF00E5FF)
        val annotated = buildMarkdownAnnotatedString(
            markdown = markdown,
            primaryColor = Color.White,
            accentColor = cyan,
            codeBgColor = Color.DarkGray,
        )

        assertEquals("参考 HANDOFF.md 文件", annotated.text)

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)
        val urlAnnotation = linkAnnotations.first().item as? LinkAnnotation.Url
        assertNotNull(urlAnnotation)
        assertEquals("https://github.com/piremote/app/blob/main/HANDOFF.md", urlAnnotation?.url)

        val target = "HANDOFF.md"
        val start = annotated.text.indexOf(target)
        val end = start + target.length
        val codeSpan = annotated.spanStyles.find { it.start == start && it.end == end }
        assertNotNull("Should have monospace span for code inside link", codeSpan)
        assertEquals(androidx.compose.ui.text.font.FontFamily.Monospace, codeSpan?.item?.fontFamily)
    }
}
