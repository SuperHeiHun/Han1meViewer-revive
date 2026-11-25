package com.yenaly.han1meviewer.util

import android.content.Context
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.blocking.BlockKind
import com.yenaly.han1meviewer.logic.blocking.ContentBlocking
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun Context.blockActionLabel(kind: BlockKind, name: String): String {
    val blocked = ContentBlocking.rules.value.contains(kind, name)
    return getString(when (kind) {
        BlockKind.TAG -> if (blocked) R.string.unblock_tag_named else R.string.block_tag_named
        BlockKind.AUTHOR -> if (blocked) R.string.unblock_author_named else R.string.block_author_named
    }, name)
}

fun Context.showBlockRuleDialog(kind: BlockKind, name: String, scope: CoroutineScope) {
    val block = !ContentBlocking.rules.value.contains(kind, name)
    showAlertDialog {
        setTitle(blockActionLabel(kind, name))
        setMessage(if (block) R.string.block_rule_confirm else R.string.unblock_rule_confirm)
        setPositiveButton(R.string.confirm) { _, _ ->
            scope.launch {
                try {
                    ContentBlocking.store.setBlocked(kind, name, block)
                    showShortToast(if (block) R.string.block_rule_added else R.string.block_rule_removed)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    showShortToast(R.string.block_rule_save_failed)
                }
            }
        }
        setNegativeButton(R.string.cancel, null)
    }
}

fun Context.showVideoBlockMenu(video: HanimeVideo, scope: CoroutineScope) {
    val entries = buildList {
        video.artist?.name?.takeIf { it.isNotBlank() }?.let { add(BlockKind.AUTHOR to it) }
        video.tags.distinct().forEach { add(BlockKind.TAG to it) }
    }
    if (entries.isEmpty()) {
        showShortToast(R.string.block_no_metadata)
        return
    }
    showAlertDialog {
        setTitle(R.string.content_blocking)
        setItems(entries.map { (kind, name) -> blockActionLabel(kind, name) }.toTypedArray()) { _, index ->
            val (kind, name) = entries[index]
            showBlockRuleDialog(kind, name, scope)
        }
        setNegativeButton(R.string.cancel, null)
    }
}
