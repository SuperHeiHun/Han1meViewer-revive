package com.yenaly.han1meviewer.ui.view.video

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.SwitchCompat
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.player.PlaybackAspectRatio
import com.yenaly.han1meviewer.logic.player.PlaybackMode
import com.yenaly.han1meviewer.logic.player.PlaybackSettings

/** A dedicated in-player panel, deliberately unrelated to the app preference screen. */
internal class PlaybackSettingsControls(
    view: View,
    private val onMode: (PlaybackMode) -> Unit,
    private val onAspect: (PlaybackAspectRatio) -> Unit,
    private val onAutoRotate: (Boolean) -> Unit,
) {
    private val mode = view.findViewById<AppCompatSpinner>(R.id.playback_mode_choice)
    private val aspect = view.findViewById<AppCompatSpinner>(R.id.playback_aspect_choice)
    private val rotate = view.findViewById<SwitchCompat>(R.id.playback_auto_rotate)
    private var rendered = PlaybackSettings()

    init {
        fun setup(spinner: AppCompatSpinner, labels: List<Int>) {
            spinner.setPopupBackgroundDrawable(ColorDrawable(Color.rgb(32, 32, 32)))
            spinner.adapter = object : ArrayAdapter<String>(
                view.context, android.R.layout.simple_spinner_item, labels.map { view.context.getString(it) },
            ) {
                init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getView(position, convertView, parent).apply { (this as TextView).setTextColor(Color.WHITE) }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                    super.getDropDownView(position, convertView, parent).apply { (this as TextView).setTextColor(Color.WHITE) }
            }
        }
        setup(mode, listOf(R.string.playback_mode_list_loop, R.string.playback_mode_repeat_one, R.string.playback_mode_pause_at_end))
        setup(aspect, listOf(R.string.playback_aspect_fit, R.string.playback_aspect_16_9, R.string.playback_aspect_4_3, R.string.playback_aspect_fill, R.string.playback_aspect_crop))
        mode.onItemSelectedListener = selection { index ->
            PlaybackMode.entries.getOrNull(index)?.takeIf { it != rendered.mode }?.let {
                rendered = rendered.copy(mode = it)
                onMode(it)
            }
        }
        aspect.onItemSelectedListener = selection { index ->
            PlaybackAspectRatio.entries.getOrNull(index)?.takeIf { it != rendered.aspectRatio }?.let {
                rendered = rendered.copy(aspectRatio = it)
                onAspect(it)
            }
        }
    }

    fun render(settings: PlaybackSettings) {
        rendered = settings
        mode.setSelection(settings.mode.ordinal, false)
        aspect.setSelection(settings.aspectRatio.ordinal, false)
        rotate.setOnCheckedChangeListener(null)
        rotate.isChecked = settings.autoRotate
        rotate.setOnCheckedChangeListener { _, enabled ->
            if (enabled != rendered.autoRotate) {
                rendered = rendered.copy(autoRotate = enabled)
                onAutoRotate(enabled)
            }
        }
    }

    private fun selection(action: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action(position)
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}
