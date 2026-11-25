package com.yenaly.han1meviewer.logic.blocking

import com.yenaly.han1meviewer.logic.model.HanimeInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Unknown cards stay hidden until verified; successful cards appear progressively, in source order. */
class VideoBlockFilter(
    private val cached: (String) -> VideoBlockMetadata?,
    private val resolve: suspend (String) -> VideoBlockMetadata?,
) {
    fun observe(items: List<HanimeInfo>, rules: BlockRules): Flow<List<HanimeInfo>> = channelFlow {
        if (rules.isEmpty) {
            send(items)
            return@channelFlow
        }
        val visible = hashSetOf<String>()
        val pending = linkedSetOf<String>()
        items.groupBy { it.videoCode }.forEach { (code, copies) ->
            val metadata = cached(code)
            when {
                copies.any { it.uploader?.let { name -> rules.contains(BlockKind.AUTHOR, name) } == true } -> Unit
                metadata != null -> if (!rules.blocks(metadata)) visible.add(code)
                rules.tags.isEmpty() && copies.any { !it.uploader.isNullOrBlank() } -> visible.add(code)
                else -> pending.add(code)
            }
        }
        send(items.filter { it.videoCode in visible })
        val mutex = Mutex()
        pending.forEach { code ->
            launch {
                val metadata = resolve(code)
                if (metadata != null && !rules.blocks(metadata)) {
                    mutex.withLock {
                        visible.add(code)
                        send(items.filter { it.videoCode in visible })
                    }
                }
            }
        }
    }.distinctUntilChanged()
}
