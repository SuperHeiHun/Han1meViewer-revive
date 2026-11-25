package com.yenaly.han1meviewer.util

import android.content.Context
import android.content.Intent
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.getHanimeShareText
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.entity.LocalVideoEntity
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.activity.SettingsRouter
import com.yenaly.yenaly_libs.utils.copyTextToClipboard
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Share one menu without losing page-specific removal confirmations or touching online queues. */
fun Context.showVideoCardMenu(
    item: HanimeInfo,
    scope: CoroutineScope,
    extraLabel: String? = null,
    includeWatchLater: Boolean = true,
    extraAction: (() -> Unit)? = null,
) {
    scope.launch {
        val pending = if (!includeWatchLater) null else try {
            withContext(Dispatchers.IO) {
                LocalVideoEntity.WATCH_LATER in DatabaseRepo.LocalVideoList.observeMembership(item.videoCode).first()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            showShortToast(R.string.local_list_save_failed)
            null
        }
        val entries = buildList<Pair<String, () -> Unit>> {
            if (extraLabel != null && extraAction != null) add(extraLabel to extraAction)
            add(getString(R.string.copy_to_clipboard) to {
                copyTextToClipboard(getHanimeShareText(item.title, item.videoCode))
                showShortToast(R.string.copy_to_clipboard)
            })
            if (pending != null) add(getString(if (pending) R.string.remove_local_watch_later else R.string.card_watch_later) to {
                scope.launch {
                    try {
                        withContext(NonCancellable + Dispatchers.IO) {
                            if (pending) DatabaseRepo.LocalVideoList.delete(LocalVideoEntity.WATCH_LATER, item.videoCode)
                            else DatabaseRepo.LocalVideoList.insert(LocalVideoEntity(item.videoCode,
                                LocalVideoEntity.WATCH_LATER, item.title, item.coverUrl))
                        }
                        showShortToast(if (pending) R.string.delete_success else R.string.add_success)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) { showShortToast(R.string.local_list_save_failed) }
                }
            })
            add(getString(R.string.card_block_video) to { showVideoCardBlockMenu(item, scope) })
            add(getString(R.string.content_blocking) to { openContentBlockingSettings() })
        }
        showAlertDialog {
            setTitle(item.title)
            setItems(entries.map { it.first }.toTypedArray()) { _, index -> entries[index].second() }
            setNegativeButton(R.string.cancel, null)
        }
    }
}

fun Context.openContentBlockingSettings() {
    startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsRouter.DESTINATION, R.id.contentBlockingSettingsFragment))
}
