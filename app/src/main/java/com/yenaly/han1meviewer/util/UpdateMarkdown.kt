package com.yenaly.han1meviewer.util

import android.content.Context
import android.net.Uri
import android.text.util.Linkify
import androidx.core.text.method.LinkMovementMethodCompat
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin

/** Only used for update notes; no HTML, WebView or remote image loading. */
internal fun Context.createUpdateMarkdown(): Markwon = Markwon.builder(this)
    .usePlugin(SoftBreakAddsNewLinePlugin.create())
    .usePlugin(StrikethroughPlugin.create())
    .usePlugin(TablePlugin.create(this))
    .usePlugin(TaskListPlugin.create(this))
    .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS, true))
    .usePlugin(MovementMethodPlugin.create(LinkMovementMethodCompat.getInstance()))
    .usePlugin(object : AbstractMarkwonPlugin() {
        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
            val resolver = LinkResolverDef()
            builder.linkResolver { view, link ->
                // Release notes are remote content, not permission to launch arbitrary intents.
                val uri = Uri.parse(link)
                val isWebLink = uri.scheme.equals("https", ignoreCase = true) ||
                        uri.scheme.equals("http", ignoreCase = true)
                if (isWebLink && !uri.host.isNullOrBlank()) {
                    resolver.resolve(view, link)
                }
            }
        }
    })
    .build()
