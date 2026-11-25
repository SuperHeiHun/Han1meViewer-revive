package com.yenaly.han1meviewer.logic.blocking

import android.content.Context
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.Parser
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import com.yenaly.han1meviewer.logic.network.HanimeNetwork
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object ContentBlocking {
    val store by lazy {
        BlocklistStore(applicationContext.getSharedPreferences("content_blocklist", Context.MODE_PRIVATE))
    }
    val rules get() = store.rules

    private const val CACHE_LIMIT = 512
    private const val CACHE_TTL_MS = 30 * 60 * 1_000L
    private val cache = object : LinkedHashMap<String, Pair<Long, VideoBlockMetadata>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, VideoBlockMetadata>>?) =
            size > CACHE_LIMIT
    }
    private val locks = Array(32) { Mutex() }
    private val requests = Semaphore(3)
    private val filter = VideoBlockFilter(::cached, ::resolve)

    // A domain/language change must not reuse potentially different author/tag names.
    private fun key(code: String) = "${Preferences.baseUrl}|${Preferences.videoLanguage}|$code"

    private fun cached(code: String): VideoBlockMetadata? = synchronized(cache) {
        cache[key(code)]?.takeIf { System.currentTimeMillis() - it.first < CACHE_TTL_MS }?.second
    }

    fun remember(code: String, video: HanimeVideo) {
        // The player model omits the artist if an avatar/genre could not be parsed.
        // Do not mistake that partial model for proof that the page has no author.
        val author = video.artist?.name?.takeIf { it.isNotBlank() } ?: return
        remember(key(code), VideoBlockMetadata(video.tags.toSet(), author))
    }

    private fun remember(key: String, metadata: VideoBlockMetadata) {
        synchronized(cache) { cache[key] = System.currentTimeMillis() to metadata }
    }

    private suspend fun resolve(code: String): VideoBlockMetadata? {
        val key = key(code)
        return locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            cached(code) ?: requests.withPermit {
                withTimeoutOrNull(10_000) {
                    withContext(Dispatchers.IO) {
                        try {
                            val response = HanimeNetwork.hanimeService.getHanimeVideo(code)
                            val body = response.body()?.use { it.string() }
                            response.errorBody()?.close()
                            if (!response.isSuccessful || body == null) return@withContext null
                            // No player, local watch history or login prompts are triggered by this lookup.
                            Parser.videoBlockMetadata(body)?.also { remember(key, it) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Failed lookups are not cached; refresh can retry them.
                            null
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(items: List<HanimeInfo>) = rules.flatMapLatest { filter.observe(items, it) }

    /** Explicit card-menu lookup shares the bounded cache and never records viewing history. */
    suspend fun metadataFor(code: String): VideoBlockMetadata? = cached(code) ?: resolve(code)
}
