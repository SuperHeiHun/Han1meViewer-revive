package com.yenaly.han1meviewer

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.TextView
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yenaly.han1meviewer.util.createUpdateMarkdown
import io.noties.markwon.core.spans.BlockQuoteSpan
import io.noties.markwon.core.spans.BulletListItemSpan
import io.noties.markwon.core.spans.CodeBlockSpan
import io.noties.markwon.core.spans.CodeSpan
import io.noties.markwon.core.spans.EmphasisSpan
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.core.spans.LinkSpan
import io.noties.markwon.core.spans.OrderedListItemSpan
import io.noties.markwon.core.spans.StrongEmphasisSpan
import io.noties.markwon.ext.tables.TableRowSpan
import io.noties.markwon.ext.tasklist.TaskListSpan
import org.commonmark.node.Image
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateMarkdownTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val markdown = context.createUpdateMarkdown()

    @Test fun rendersHeadingsEmphasisAndStrikethrough() {
        val text = markdown.toMarkdown("# 更新说明\n\n**粗体** *斜体* ~~删除线~~")

        assertTrue(text.toString().contains("更新说明"))
        assertFalse(text.toString().contains("**"))
        assertTrue(text.spans<HeadingSpan>().isNotEmpty())
        assertTrue(text.spans<StrongEmphasisSpan>().isNotEmpty())
        assertTrue(text.spans<EmphasisSpan>().isNotEmpty())
        assertTrue(text.spans<StrikethroughSpan>().isNotEmpty())
    }

    @Test fun rendersListsAndBlockQuotes() {
        val text = markdown.toMarkdown("- 新功能\n- 修复\n\n1. 下载\n2. 安装\n\n> 注意事项")

        assertEquals(2, text.spans<BulletListItemSpan>().size)
        assertEquals(2, text.spans<OrderedListItemSpan>().size)
        assertEquals(1, text.spans<BlockQuoteSpan>().size)
    }

    @Test fun keepsCodeLiteralWithoutLinkifyingItsContents() {
        val text = markdown.toMarkdown(
            "`**literal**`\n\n```text\nhttps://example.com\n**not bold**\n```"
        )

        assertTrue(text.toString().contains("**literal**"))
        assertTrue(text.toString().contains("**not bold**"))
        assertEquals(1, text.spans<CodeSpan>().size)
        assertEquals(1, text.spans<CodeBlockSpan>().size)
        assertTrue(text.spans<StrongEmphasisSpan>().isEmpty())
        assertTrue(text.spans<LinkSpan>().isEmpty())
    }

    @Test fun rendersNamedAndBareWebLinks() {
        val text = markdown.toMarkdown(
            "[发布页](https://example.com/releases)\nhttps://example.com/help"
        )

        assertEquals(
            setOf("https://example.com/releases", "https://example.com/help"),
            text.spans<LinkSpan>().map { it.link }.toSet(),
        )
        assertFalse(text.toString().contains("[发布页]"))
    }

    @Test fun preservesPlainTextAndCiLineBreaks() {
        val source = "↓ (@contributor)\r\n修复作者头像\r\n保留签名参数"

        assertEquals(source.replace("\r\n", "\n"), markdown.toMarkdown(source).toString())
        assertEquals("", markdown.toMarkdown("").toString())
        assertEquals("**原文**", markdown.toMarkdown("\\*\\*原文\\*\\*").toString())
    }

    @Test fun rendersTableRows() {
        val text = markdown.toMarkdown("| 功能 | 状态 |\n| --- | --- |\n| Markdown | 支持 |")

        assertEquals(2, text.spans<TableRowSpan>().size)
    }

    @Test fun rendersCheckedAndUncheckedTasks() {
        val text = markdown.toMarkdown("- [x] 已完成\n- [ ] 待处理")
        val tasks = text.spans<TaskListSpan>()

        assertEquals(2, tasks.size)
        assertEquals(setOf(true, false), tasks.map { it.isDone }.toSet())
        assertFalse(text.toString().contains("[x]"))
        assertFalse(text.toString().contains("[ ]"))
    }

    @Test fun showsImageAltTextWithoutInstallingAnImageLoader() {
        val text = markdown.toMarkdown("![版本截图](https://example.com/tracking.png)")

        assertEquals("版本截图", text.toString())
        assertNull(markdown.configuration().spansFactory().get(Image::class.java))
    }

    @Test fun refusesNonWebLinksWithoutLaunchingAnActivity() {
        val recordingContext = RecordingContext(context)
        val blocked = listOf(
            "javascript:alert", "intent://example.com", "file:///sdcard/private",
            "content://example.com/private", "tel:123456", "mailto:user@example.com",
            "https:///missing-host",
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = View(recordingContext)
            blocked.forEach { link ->
                markdown.configuration().linkResolver().resolve(view, link)
            }
        }

        assertTrue(recordingContext.intents.isEmpty())
    }

    @Test fun followsWebLinksWithoutChangingTheirQueryParameters() {
        val recordingContext = RecordingContext(context)
        val links = listOf(
            "https://example.com/releases?tag=v1.1.0&from=notes#changes",
            "http://example.com/help",
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = View(recordingContext)
            links.forEach { link ->
                markdown.configuration().linkResolver().resolve(view, link)
            }
        }

        assertEquals(links, recordingContext.intents.map { it.dataString })
        assertTrue(recordingContext.intents.all { it.action == Intent.ACTION_VIEW })
    }

    @Test fun appliesRenderedSpansAndCompatLinkHandlingToTheMessageView() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val message = TextView(context)
            val text = markdown.toMarkdown("**更新**\n\n[发布页](https://example.com/releases)")

            markdown.setParsedMarkdown(message, text)

            assertSame(LinkMovementMethodCompat.getInstance(), message.movementMethod)
            assertTrue((message.text as Spanned).spans<StrongEmphasisSpan>().isNotEmpty())
            assertEquals(1, (message.text as Spanned).spans<LinkSpan>().size)
        }
    }

    private inline fun <reified T> Spanned.spans(): Array<T> = getSpans(0, length, T::class.java)

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val intents = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            intents += Intent(intent)
        }
    }
}
