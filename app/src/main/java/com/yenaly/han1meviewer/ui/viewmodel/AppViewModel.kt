package com.yenaly.han1meviewer.ui.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.NetworkRepo
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.worker.HUpdateWorker
import com.yenaly.han1meviewer.worker.HanimeDownloadManagerV2
import com.yenaly.han1meviewer.worker.HanimeDownloadWorker
import com.yenaly.yenaly_libs.base.YenalyViewModel
import com.yenaly.yenaly_libs.utils.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2024/03/29 029 18:00
 */
object AppViewModel : YenalyViewModel(application), IHCsrfToken {

    /**
     * csrfToken 全局唯一，只需要在首页拉起或点击视频页时更新一下就可以了
     */
    override var csrfToken: String? = null

    // null 表示尚未檢查，禁用自動更新時仍允許使用者手動發起檢查。
    private val _versionFlow = MutableStateFlow<WebsiteState<Latest?>?>(null)
    val versionFlow = _versionFlow.asStateFlow()
    private var updateCheckJob: Job? = null
    private var isAutomaticUpdateCheck = false

    val runningWorkInfoCountFlow = MutableStateFlow(0)

    init {
        // 取消，防止每次启动都有残留的更新任务
        WorkManager.getInstance(application).pruneWork()

        viewModelScope.launch(Dispatchers.IO) {
            // HanimeDownloadManager.init()
            HanimeDownloadManagerV2.init()
        }

        viewModelScope.launch(Dispatchers.Main) {
            HUpdateWorker.collectOutput(application)
        }

        viewModelScope.launch(Dispatchers.IO) {
            HanimeDownloadWorker.getRunningWorkInfoCount(application).collect { count ->
                Log.d(HanimeDownloadWorker.TAG, "getRunningWorkInfoCount: $count")
                runningWorkInfoCountFlow.value = count
            }
        }
    }

    fun getLatestVersion(
        forceCheck: Boolean = true,
        delayMillis: Long = 0,
        automatic: Boolean = false,
    ) {
        if (automatic && Preferences.disableAutoUpdate) return
        // 啟動時的延後回呼不能打斷使用者已發起的手動檢查。
        if (automatic && !isAutomaticUpdateCheck && updateCheckJob?.isActive == true) return
        updateCheckJob?.cancel()
        isAutomaticUpdateCheck = automatic
        updateCheckJob = viewModelScope.launch {
            delay(delayMillis)
            if (automatic && Preferences.disableAutoUpdate) return@launch
            getLatestVersionSuspend(forceCheck)
        }
    }

    fun cancelAutomaticUpdateCheck() {
        if (isAutomaticUpdateCheck && updateCheckJob?.isActive == true) {
            updateCheckJob?.cancel()
            updateCheckJob = null
            _versionFlow.value = null
        }
    }

    private suspend fun getLatestVersionSuspend(forceCheck: Boolean = true) {
        NetworkRepo.getLatestVersion(forceCheck).collect {
            _versionFlow.value = it
        }
    }
}
