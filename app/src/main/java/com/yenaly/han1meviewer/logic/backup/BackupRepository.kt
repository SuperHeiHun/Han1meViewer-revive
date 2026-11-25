package com.yenaly.han1meviewer.logic.backup

import androidx.room.withTransaction
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.blocking.ContentBlocking
import com.yenaly.han1meviewer.logic.dao.HistoryDatabase
import com.yenaly.han1meviewer.logic.dao.LocalVideoDatabase
import com.yenaly.han1meviewer.logic.entity.LocalVideoEntity
import com.yenaly.han1meviewer.logic.entity.SearchHistoryEntity
import com.yenaly.han1meviewer.logic.history.HistoryPrivacy
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.logic.network.HanimeNetwork
import com.yenaly.han1meviewer.logout
import com.yenaly.han1meviewer.ui.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int

object BackupRepository {
    suspend fun create(items: Set<BackupItem>): BackupArchive = withContext(Dispatchers.IO) {
        require(items.isNotEmpty())
        val (watch, search) = if (BackupItem.WATCH_HISTORY in items || BackupItem.SEARCH_HISTORY in items) {
            val history = HistoryDatabase.instance
            history.withTransaction {
                (if (BackupItem.WATCH_HISTORY in items) history.watchHistory.snapshot().map(BackupWatch::from) else null) to
                        (if (BackupItem.SEARCH_HISTORY in items) history.searchHistory.snapshot().map { it.query } else null)
            }
        } else null to null
        val (favorites, later) = if (BackupItem.FAVORITES in items || BackupItem.WATCH_LATER in items) {
            val local = LocalVideoDatabase.instance
            local.withTransaction {
                (if (BackupItem.FAVORITES in items) local.localVideoDao.snapshot(LocalVideoEntity.FAVORITES).map(BackupVideo::from) else null) to
                        (if (BackupItem.WATCH_LATER in items) local.localVideoDao.snapshot(LocalVideoEntity.WATCH_LATER).map(BackupVideo::from) else null)
            }
        } else null to null
        val settings = if (BackupItem.SETTINGS in items) {
            val stored = Preferences.preferenceSp.all
            val rules = ContentBlocking.rules.value
            BackupSettings(BackupSettingRules.defaults.mapValues { (key, default) ->
                when (val value = stored[key]) {
                    is Boolean -> JsonPrimitive(value)
                    is Int -> JsonPrimitive(value)
                    is String -> JsonPrimitive(value)
                    else -> default
                }
            }, rules.tags, rules.authors)
        } else null
        BackupArchive(createdAt = System.currentTimeMillis(), watchHistory = watch, searchHistory = search,
            settings = settings, favorites = favorites, watchLater = later).also(BackupCodec::validate)
    }

    /** Explicit user restore bypasses history recording policy, but never clears a database. */
    suspend fun restore(archive: BackupArchive) = withContext(NonCancellable + Dispatchers.IO) {
        BackupCodec.validate(archive)
        if (archive.watchHistory != null || archive.searchHistory != null) {
            val history = HistoryDatabase.instance
            history.withTransaction {
                archive.watchHistory?.let { history.watchHistory.mergeBackup(it.map(BackupWatch::toEntity)) }
                archive.searchHistory?.let { rows -> history.searchHistory.mergeBackup(rows.map { SearchHistoryEntity(it) }) }
            }
        }
        if (archive.favorites != null || archive.watchLater != null) {
            val local = LocalVideoDatabase.instance
            local.withTransaction {
                archive.favorites?.let { local.localVideoDao.mergeBackup(it.map { row -> row.toEntity(LocalVideoEntity.FAVORITES) }) }
                archive.watchLater?.let { local.localVideoDao.mergeBackup(it.map { row -> row.toEntity(LocalVideoEntity.WATCH_LATER) }) }
            }
        }
        archive.settings?.let { restoreSettings(it) }
    }

    private suspend fun restoreSettings(settings: BackupSettings) {
        val oldDomain = Preferences.baseUrl
        settings.values[HistoryPrivacy.KEY]?.let { HistoryPrivacy.setEnabled(it.boolean) }
        val editor = Preferences.preferenceSp.edit()
        settings.values.filterKeys { it != HistoryPrivacy.KEY }.forEach { (key, value) ->
            when {
                value.isString -> editor.putString(key, value.content)
                value.booleanOrNull != null -> editor.putBoolean(key, value.boolean)
                else -> editor.putInt(key, value.int)
            }
        }
        check(editor.commit())
        ContentBlocking.store.mergeBackup(settings.blockedTags, settings.blockedAuthors)
        withContext(Dispatchers.Main) {
            if (oldDomain != Preferences.baseUrl) logout()
            if (Preferences.disableAutoUpdate) AppViewModel.cancelAutomaticUpdateCheck()
            HProxySelector.rebuildNetwork()
            HanimeNetwork.rebuildNetwork()
        }
    }
}
