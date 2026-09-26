/*
 * Copyright (c) 2026 HellPC (https://github.com/He11PC).
 * This file is part of Mirror, multiprotocol backup application.
 *
 * Mirror is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * See AGENTS.md for AI usage policy.
 *
 * This program is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details <https://www.gnu.org/licenses/>.
 */

package fr.hellpc.mirror.ui.customViews

import android.content.Context
import android.graphics.Rect
import android.text.Selection
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class Custom_AutoLinkFocusTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        movementMethod = LinkMovementMethod.getInstance()
        defaultFocusHighlightEnabled = false
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)

        val spannable = text as? Spannable ?: return

        if(focused) {
            val spans = spannable.getSpans(0, spannable.length, ClickableSpan::class.java)
            if(spans.isNotEmpty()) {
                val targetSpan = if (direction == FOCUS_UP) spans.last() else spans.first()
                val start = spannable.getSpanStart(targetSpan)
                val end = spannable.getSpanEnd(targetSpan)

                Selection.setSelection(spannable, start, end)
            }
        }
        else
            Selection.removeSelection(spannable)
    }
}