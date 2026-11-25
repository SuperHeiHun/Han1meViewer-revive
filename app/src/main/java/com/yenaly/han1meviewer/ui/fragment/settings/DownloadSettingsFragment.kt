package com.yenaly.han1meviewer.ui.fragment.settings

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.SeekBarPreference
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.download.DownloadStorage
import com.yenaly.han1meviewer.logic.network.interceptor.SpeedLimitInterceptor
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.fragment.IToolbarFragment
import com.yenaly.han1meviewer.util.setSummaryConverter
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.han1meviewer.worker.HanimeDownloadManagerV2
import com.yenaly.yenaly_libs.base.preference.LongClickablePreference
import com.yenaly.yenaly_libs.base.settings.YenalySettingsFragment
import com.yenaly.yenaly_libs.utils.copyToClipboard
import com.yenaly.yenaly_libs.utils.formatBytesPerSecond
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadSettingsFragment : YenalySettingsFragment(R.xml.settings_download),
    IToolbarFragment<SettingsActivity> {

    companion object {
        const val DOWNLOAD_PATH = "download_path"
        const val DOWNLOAD_COUNT_LIMIT = "download_count_limit"
        const val DOWNLOAD_SPEED_LIMIT = "download_speed_limit"
    }

    private val downloadPath
            by safePreference<LongClickablePreference>(DOWNLOAD_PATH)
    private val downloadCountLimit
            by safePreference<SeekBarPreference>(DOWNLOAD_COUNT_LIMIT)
    private val downloadSpeedLimit
            by safePreference<SeekBarPreference>(DOWNLOAD_SPEED_LIMIT)

    private var changingLocation = false
    private val chooseFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) changeLocation(uri)
    }

    override fun onStart() {
        super.onStart()
        (activity as SettingsActivity).setupToolbar()
    }

    override fun onPreferencesCreated(savedInstanceState: Bundle?) {
        downloadPath.apply {
            summary = DownloadStorage.locationLabel
            isEnabled = !changingLocation
            setOnPreferenceClickListener {
                requireContext().showAlertDialog {
                    setTitle(R.string.download_path)
                    setMessage(getString(R.string.detailed_path_s, DownloadStorage.locationLabel) +
                            "\n\n" + getString(R.string.download_path_change_note))
                    setPositiveButton(R.string.download_path_choose) { _, _ ->
                        try {
                            chooseFolder.launch(Preferences.downloadTreeUri.takeIf(String::isNotBlank)?.toUri())
                        } catch (_: Exception) {
                            showShortToast(R.string.download_path_select_failed)
                        }
                    }
                    setNeutralButton(R.string.download_path_reset) { _, _ -> changeLocation(null) }
                    setNegativeButton(R.string.cancel, null)
                }
                return@setOnPreferenceClickListener true
            }
            setOnPreferenceLongClickListener {
                Preferences.downloadTreeUri.ifBlank { DownloadStorage.locationLabel }.copyToClipboard()
                showShortToast(R.string.copy_to_clipboard)
                return@setOnPreferenceLongClickListener true
            }
        }
        downloadCountLimit.apply {
            setSummaryConverter(
                defValue = HanimeDownloadManagerV2.MAX_CONCURRENT_DOWNLOAD_DEF,
                converter = ::toDownloadCountLimitPrettyString
            ) {
                // HanimeDownloadManager.maxConcurrentDownloadCount = it
                HanimeDownloadManagerV2.maxConcurrentDownloadCount = it
            }
        }
        downloadSpeedLimit.apply {
            min = 0
            max = SpeedLimitInterceptor.SPEED_BYTES.lastIndex
            setSummaryConverter(defValue = SpeedLimitInterceptor.NO_LIMIT_INDEX, converter = { i ->
                SpeedLimitInterceptor.SPEED_BYTES[i].toDownloadSpeedPrettyString()
            })
        }
    }

    override fun onResume() {
        super.onResume()
        downloadPath.summary = DownloadStorage.locationLabel
    }

    private fun changeLocation(uri: Uri?) {
        if (changingLocation) return
        changingLocation = true
        downloadPath.isEnabled = false
        lifecycleScope.launch {
            try {
                // Finish the short grant/save operation even if the settings page is recreated.
                withContext(NonCancellable + Dispatchers.IO) {
                    if (uri == null) DownloadStorage.reset() else DownloadStorage.selectTree(uri)
                }
                if (isAdded && view != null) {
                    downloadPath.summary = DownloadStorage.locationLabel
                    showShortToast(R.string.settings_saved)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (isAdded && view != null) showShortToast(R.string.download_path_select_failed)
            } finally {
                changingLocation = false
                if (isAdded && view != null) downloadPath.isEnabled = true
            }
        }
    }

    private fun Long.toDownloadSpeedPrettyString(): String {
        return if (this == 0L) {
            getString(R.string.no_limit)
        } else {
            this.formatBytesPerSecond()
        }
    }

    private fun toDownloadCountLimitPrettyString(value: Int): String {
        return if (value == 0) {
            getString(R.string.no_limit)
        } else {
            value.toString()
        }
    }

    override fun SettingsActivity.setupToolbar() {
        supportActionBar!!.setTitle(R.string.download_settings)
    }
}
