package com.yenaly.han1meviewer.ui.fragment.settings

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.databinding.DialogWebdavConfigBinding
import com.yenaly.han1meviewer.logic.backup.BackupArchive
import com.yenaly.han1meviewer.logic.backup.BackupItem
import com.yenaly.han1meviewer.logic.backup.WebDavConfig
import com.yenaly.han1meviewer.logic.backup.WebDavConfigStore
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.fragment.IToolbarFragment
import com.yenaly.han1meviewer.ui.viewmodel.BackupViewModel
import com.yenaly.han1meviewer.util.createAlertDialog
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.han1meviewer.util.showWithBlurEffect
import com.yenaly.yenaly_libs.base.settings.YenalySettingsFragment
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupSettingsFragment : YenalySettingsFragment(R.xml.settings_backup), IToolbarFragment<SettingsActivity> {
    private val viewModel by viewModels<BackupViewModel>()
    private var restoreDialog: AlertDialog? = null
    private var configDialog: AlertDialog? = null
    private var configLoading = false
    private val createFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        viewModel.pickerResult(it, export = true)
    }
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.pickerResult(it, export = false)
    }

    override fun onStart() {
        super.onStart()
        (activity as SettingsActivity).setupToolbar()
    }

    override fun onPreferencesCreated(savedInstanceState: Bundle?) {
        action("backup_local_export") { selected ->
            if (viewModel.beginPicker(true, selected)) try {
                createFile.launch("Han1meViewer-backup-${System.currentTimeMillis()}.json")
            } catch (_: Exception) { viewModel.pickerFailed() }
        }
        action("backup_local_import") { selected ->
            if (viewModel.beginPicker(false, selected)) try {
                openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            } catch (_: Exception) { viewModel.pickerFailed() }
        }
        action("backup_webdav_upload") { selected ->
            requireContext().showAlertDialog {
                setTitle(R.string.backup_webdav_upload)
                setMessage(R.string.backup_upload_confirm)
                setPositiveButton(R.string.confirm) { _, _ -> viewModel.upload(selected) }
                setNegativeButton(R.string.cancel, null)
            }
        }
        action("backup_webdav_download", viewModel::download)
        findPreference<Preference>("backup_webdav_config")?.setOnPreferenceClickListener {
            showConfig()
            true
        }
    }

    private fun action(key: String, block: (Set<BackupItem>) -> Unit) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener {
            val selected = BackupItem.entries.filterTo(linkedSetOf()) {
                Preferences.preferenceSp.getBoolean(it.key, true)
            }
            if (selected.isEmpty()) showShortToast(R.string.backup_select_items) else block(selected)
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    preferenceScreen.isEnabled = !state.busy && state.preview == null
                    findPreference<Preference>("backup_status")?.summary = state.httpStatus?.let {
                        getString(state.message, it)
                    } ?: getString(state.message)
                    // Restoring settings may change preferences while this page stays open.
                    BackupItem.entries.forEach { item ->
                        findPreference<TwoStatePreference>(item.key)?.isChecked = Preferences.preferenceSp.getBoolean(item.key, true)
                    }
                    if (state.preview != null && restoreDialog == null) showRestore(state.preview)
                }
            }
        }
    }

    private fun showRestore(archive: BackupArchive) {
        val summary = BackupItem.entries.mapNotNull { item ->
            archive.count(item)?.let { count -> getString(R.string.backup_item_count, getString(item.label), count) }
        }.joinToString("\n")
        restoreDialog = requireContext().createAlertDialog {
            setTitle(R.string.backup_restore)
            setMessage(getString(R.string.backup_restore_confirm, summary))
            setPositiveButton(R.string.confirm) { _, _ -> viewModel.confirmRestore() }
            setNegativeButton(R.string.cancel) { _, _ -> viewModel.cancelRestore() }
            setOnCancelListener { viewModel.cancelRestore() }
        }.also { dialog ->
            dialog.setOnDismissListener { restoreDialog = null }
            dialog.showWithBlurEffect()
        }
    }

    private fun showConfig() {
        if (configDialog != null || configLoading) return
        configLoading = true
        viewLifecycleOwner.lifecycleScope.launch {
            val config = try {
                withContext(Dispatchers.IO) { WebDavConfigStore.load() }
            } catch (e: CancellationException) {
                configLoading = false
                throw e
            } catch (_: Exception) {
                showShortToast(R.string.backup_webdav_config_failed)
                null
            }
            configLoading = false
            val binding = DialogWebdavConfigBinding.inflate(layoutInflater)
            binding.etWebdavUrl.setText(config?.url.orEmpty())
            binding.etWebdavUsername.setText(config?.username.orEmpty())
            binding.etWebdavPassword.setText(config?.password.orEmpty())
            val dialog = requireContext().createAlertDialog {
                setTitle(R.string.backup_webdav_config)
                setView(binding.root)
                setPositiveButton(R.string.confirm, null)
                setNegativeButton(R.string.cancel, null)
                setNeutralButton(R.string.backup_webdav_clear, null)
            }
            configDialog = dialog
            dialog.setOnShowListener {
                fun save(clear: Boolean) {
                    val current = WebDavConfig(binding.etWebdavUrl.text.toString().trim(),
                        binding.etWebdavUsername.text.toString(), binding.etWebdavPassword.text.toString())
                    if (!clear) try { current.endpoint() } catch (_: Exception) {
                        binding.etWebdavUrl.error = getString(R.string.backup_webdav_invalid)
                        return
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) { if (clear) WebDavConfigStore.clear() else WebDavConfigStore.save(current) }
                            showShortToast(R.string.settings_saved)
                            dialog.dismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            showShortToast(R.string.backup_webdav_config_failed)
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = true
                        }
                    }
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save(false) }
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { save(true) }
            }
            dialog.setOnDismissListener {
                binding.etWebdavUrl.text?.clear()
                binding.etWebdavUsername.text?.clear()
                binding.etWebdavPassword.text?.clear()
                configDialog = null
            }
            dialog.showWithBlurEffect()
        }
    }

    override fun onDestroyView() {
        restoreDialog?.dismiss()
        restoreDialog = null
        configDialog?.dismiss()
        configDialog = null
        super.onDestroyView()
    }

    override fun SettingsActivity.setupToolbar() { supportActionBar!!.setTitle(R.string.backup_settings) }
}

private val BackupItem.label: Int get() = when (this) {
    BackupItem.WATCH_HISTORY -> R.string.watch_history
    BackupItem.SEARCH_HISTORY -> R.string.search_history
    BackupItem.SETTINGS -> R.string.backup_app_settings
    BackupItem.FAVORITES -> R.string.local_favorites
    BackupItem.WATCH_LATER -> R.string.local_watch_later
}
