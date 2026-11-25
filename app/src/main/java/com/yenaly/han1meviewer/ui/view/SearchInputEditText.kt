package com.yenaly.han1meviewer.ui.view

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.textfield.TextInputEditText

/** Notify completion when the caret moves without changing the input text. */
class SearchInputEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : TextInputEditText(context, attrs) {
    var onSelectionChangedListener: ((Int, Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke(selStart, selEnd)
    }
}
