package com.yenaly.han1meviewer.ui.fragment.settings

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.blocking.BlockKind
import com.yenaly.han1meviewer.logic.blocking.BlockRules
import com.yenaly.han1meviewer.logic.blocking.ContentBlocking
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.fragment.IToolbarFragment
import com.yenaly.han1meviewer.util.createAlertDialog
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.han1meviewer.util.showBlockRuleDialog
import com.yenaly.han1meviewer.util.showWithBlurEffect
import com.yenaly.yenaly_libs.base.settings.YenalySettingsFragment
import com.yenaly.yenaly_libs.utils.dp
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ContentBlockingSettingsFragment : YenalySettingsFragment(R.xml.settings_content_blocking),
    IToolbarFragment<SettingsActivity> {

    override fun onStart() {
        super.onStart()
        (activity as SettingsActivity).setupToolbar()
    }

    override fun onPreferencesCreated(savedInstanceState: Bundle?) {
        findPreference<Preference>("content_blocking_info")?.setOnPreferenceClickListener {
            requireContext().showAlertDialog {
                setTitle(R.string.content_blocking)
                setMessage(R.string.content_blocking_details)
                setPositiveButton(R.string.ok, null)
            }
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ContentBlocking.rules.collect { rules ->
                    updateCategory("blocked_tags", BlockKind.TAG, rules.tags)
                    updateCategory("blocked_authors", BlockKind.AUTHOR, rules.authors)
                }
            }
        }
    }

    private fun updateCategory(key: String, kind: BlockKind, names: Set<String>) {
        val category = findPreference<PreferenceCategory>(key) ?: return
        category.removeAll()
        category.addPreference(Preference(requireContext()).apply {
            setTitle(if (kind == BlockKind.TAG) R.string.add_blocked_tag else R.string.add_blocked_author)
            setIcon(R.drawable.baseline_block_24)
            setOnPreferenceClickListener {
                showAddDialog(kind)
                true
            }
        })
        names.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { name ->
            category.addPreference(Preference(requireContext()).apply {
                title = name
                setSummary(R.string.tap_to_unblock)
                setOnPreferenceClickListener {
                    requireContext().showBlockRuleDialog(kind, name, viewLifecycleOwner.lifecycleScope)
                    true
                }
            })
        }
    }

    private fun showAddDialog(kind: BlockKind) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            setHint(if (kind == BlockKind.TAG) R.string.block_tag_hint else R.string.block_author_hint)
        }
        val container = FrameLayout(requireContext()).apply {
            setPadding(24.dp, 8.dp, 24.dp, 0)
            addView(input)
        }
        val dialog = requireContext().createAlertDialog {
            setTitle(if (kind == BlockKind.TAG) R.string.add_blocked_tag else R.string.add_blocked_author)
            setView(container)
            setPositiveButton(R.string.confirm, null)
            setNegativeButton(R.string.cancel, null)
        }
        dialog.setOnShowListener {
            val button = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val name = BlockRules.cleanName(input.text.toString(), kind)
                when {
                    name.isBlank() -> input.error = getString(R.string.block_name_required)
                    ContentBlocking.rules.value.contains(kind, name) ->
                        input.error = getString(R.string.block_rule_exists)
                    else -> viewLifecycleOwner.lifecycleScope.launch {
                        button.isEnabled = false
                        try {
                            ContentBlocking.store.setBlocked(kind, name, true)
                            dialog.dismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            showShortToast(R.string.block_rule_save_failed)
                            button.isEnabled = true
                        }
                    }
                }
            }
        }
        dialog.showWithBlurEffect()
    }

    override fun SettingsActivity.setupToolbar() {
        supportActionBar!!.setTitle(R.string.content_blocking)
    }
}
