package com.yenaly.han1meviewer.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.backup.BackupArchive
import com.yenaly.han1meviewer.logic.backup.BackupCodec
import com.yenaly.han1meviewer.logic.backup.BackupItem
import com.yenaly.han1meviewer.logic.backup.BackupRepository
import com.yenaly.han1meviewer.logic.backup.BackupTooLargeException
import com.yenaly.han1meviewer.logic.backup.InvalidBackupException
import com.yenaly.han1meviewer.logic.backup.InvalidWebDavConfigException
import com.yenaly.han1meviewer.logic.backup.WebDavBackupClient
import com.yenaly.han1meviewer.logic.backup.WebDavConfigStore
import com.yenaly.han1meviewer.logic.backup.WebDavHttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val busy: Boolean = false,
    val message: Int = R.string.backup_help,
    val httpStatus: Int? = null,
    val preview: BackupArchive? = null,
)

class BackupViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(BackupUiState(busy = savedState.get<String>("picker_action") != null))
    val state = _state.asStateFlow()
    private val client by lazy { WebDavBackupClient() }

    fun beginPicker(export: Boolean, items: Set<BackupItem>): Boolean {
        if (_state.value.busy || items.isEmpty()) return false
        savedState["picker_action"] = if (export) "export" else "import"
        savedState["picker_items"] = ArrayList(items.map { it.name })
        _state.value = BackupUiState(busy = true, message = R.string.backup_choose_file)
        return true
    }

    fun pickerFailed() {
        clearPicker()
        _state.value = BackupUiState(message = R.string.backup_file_picker_failed)
    }

    fun pickerResult(uri: Uri?, export: Boolean) {
        val action = savedState.get<String>("picker_action")
        val items = savedState.get<ArrayList<String>>("picker_items").orEmpty()
            .mapNotNull { name -> BackupItem.entries.find { it.name == name } }.toSet()
        clearPicker()
        _state.value = BackupUiState()
        if (uri == null || action != (if (export) "export" else "import") || items.isEmpty()) return
        task {
            val resolver = getApplication<Application>().contentResolver
            if (export) {
                val bytes = BackupCodec.encode(BackupRepository.create(items))
                (resolver.openOutputStream(uri, "wt") ?: error("Output unavailable")).use { it.write(bytes) }
                BackupUiState(message = R.string.backup_export_success)
            } else {
                val bytes = (resolver.openInputStream(uri) ?: error("Input unavailable")).use(BackupCodec::read)
                preview(BackupCodec.decode(bytes), items)
            }
        }
    }

    fun upload(items: Set<BackupItem>) = task {
        val config = WebDavConfigStore.load() ?: throw InvalidWebDavConfigException()
        val bytes = BackupCodec.encode(BackupRepository.create(items))
        client.upload(config, bytes)
        BackupUiState(message = R.string.backup_export_success)
    }

    fun download(items: Set<BackupItem>) = task {
        val config = WebDavConfigStore.load() ?: throw InvalidWebDavConfigException()
        preview(BackupCodec.decode(client.download(config)), items)
    }

    fun confirmRestore() {
        val archive = _state.value.preview ?: return
        _state.value = BackupUiState()
        task {
            BackupRepository.restore(archive)
            BackupUiState(message = if (archive.settings != null) R.string.backup_restore_settings_success else R.string.backup_restore_success)
        }
    }

    fun cancelRestore() { if (!_state.value.busy) _state.value = BackupUiState() }

    private fun preview(archive: BackupArchive, items: Set<BackupItem>): BackupUiState {
        val selected = archive.selected(items)
        return if (BackupItem.entries.none { selected.count(it) != null }) {
            BackupUiState(message = R.string.backup_no_selected_data)
        } else BackupUiState(preview = selected)
    }

    private fun clearPicker() {
        savedState.remove<String>("picker_action")
        savedState.remove<ArrayList<String>>("picker_items")
    }

    private fun task(block: suspend () -> BackupUiState) {
        if (_state.value.busy) return
        _state.value = BackupUiState(busy = true, message = R.string.backup_working)
        viewModelScope.launch {
            try {
                _state.value = withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never show raw exceptions/URLs, which may contain credentials or personal data.
                _state.value = BackupUiState(message = when (e) {
                    is BackupTooLargeException -> R.string.backup_too_large
                    is InvalidBackupException -> R.string.backup_invalid
                    is InvalidWebDavConfigException -> R.string.backup_webdav_invalid
                    is WebDavHttpException -> R.string.backup_webdav_http_error
                    else -> R.string.backup_failed
                }, httpStatus = (e as? WebDavHttpException)?.status)
            }
        }
    }
}
