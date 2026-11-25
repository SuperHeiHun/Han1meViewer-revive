package com.yenaly.han1meviewer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.entity.LocalVideoEntity
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalVideoListViewModel : ViewModel() {
    private val repository = DatabaseRepo.LocalVideoList
    private val mutationMutex = Mutex()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    val errors = _errors.asSharedFlow()

    fun observeList(listType: String) = repository.observeList(listType)
        .map { videos -> videos.map(LocalVideoEntity::toHanimeInfo) }
        .distinctUntilChanged()

    fun observeMembership(videoCode: String) = repository.observeMembership(videoCode)
        .map { it.toSet() }
        .distinctUntilChanged()

    fun setSaved(videoCode: String, video: HanimeVideo, listType: String, saved: Boolean) {
        mutate {
            if (saved) {
                repository.insert(LocalVideoEntity(videoCode, listType, video.title, video.coverUrl))
            } else {
                repository.delete(listType, videoCode)
            }
        }
    }

    fun remove(listType: String, videoCode: String) = mutate {
        repository.delete(listType, videoCode)
    }

    fun clear(listType: String) = mutate {
        repository.clear(listType)
    }

    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutationMutex.withLock {
                _isSaving.value = true
                try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _errors.emit(e)
                } finally {
                    _isSaving.value = false
                }
            }
        }
    }
}
