package com.yenaly.han1meviewer.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.yenaly.han1meviewer.R
import com.yenaly.yenaly_libs.utils.activity
import com.yenaly.yenaly_libs.utils.view.hideIme

/**
 * 搜索界面的搜索栏
 *
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2023/08/11 011 15:20
 */
class HanimeSearchBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val window = checkNotNull(context.activity?.window)

    init {
        inflate(context, R.layout.layout_hanime_search_bar, this)
    }

    private val back: MaterialButton = findViewById(R.id.btn_back)
    private val search: MaterialButton = findViewById(R.id.btn_search)
    private val tag: MaterialButton = findViewById(R.id.btn_tag)
    private val etSearch: TextInputEditText = findViewById(R.id.et_search)

    init {
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH && onSearchClickListener != null) {
                etSearch.hideIme(window)
                onSearchClickListener?.invoke(etSearch, etSearch.text?.toString().orEmpty())
                true
            } else false
        }
    }

    var canTextChange: Boolean = true
        set(value) {
            field = value
            etSearch.isEnabled = value
        }

    var onBackClickListener: ((View) -> Unit)? = null
        set(value) {
            field = value
            back.setOnClickListener(value)
        }

    var onSearchClickListener: ((View, String) -> Unit)? = null
        set(value) {
            field = value
            search.setOnClickListener {
                etSearch.hideIme(window)
                value?.invoke(it, etSearch.text?.toString().orEmpty())
            }
        }

    var onTagClickListener: ((View) -> Unit)? = null
        set(value) {
            field = value
            tag.setOnClickListener(value)
        }

    var searchText: String?
        get() = etSearch.text?.toString()
        set(value) {
            etSearch.setText(value)
            etSearch.setSelection(etSearch.length())
        }
}
