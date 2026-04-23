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

package fr.hellpc.mirror.ui.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.Spinner_ColorAndText

class Adapter_Spinner_Color(ctx: Context, data: List<Spinner_ColorAndText>): ArrayAdapter<Spinner_ColorAndText>(ctx, 0, data) {

    private val colorValueBackground by lazy { context.resources.getIntArray(R.array.color_value_background).toList() }
    private val colorValueBorders by lazy { context.resources.getIntArray(R.array.color_value_borders).toList() }
    private val colorValueIcons by lazy { context.resources.getIntArray(R.array.color_value_icons).toList() }
    private val colorValueProgressbarSecondary by lazy { context.resources.getIntArray(R.array.color_value_progressbar_secondary).toList() }

    override fun getView(position: Int, recycledView: View?, parent: ViewGroup): View {
        return this.createView(position, recycledView, parent)
    }

    override fun getDropDownView(position: Int, recycledView: View?, parent: ViewGroup): View {
        return this.createView(position, recycledView, parent)
    }

    private fun createView(position: Int, recycledView: View?, parent: ViewGroup): View {
        val item = getItem(position)
        val spinner = recycledView ?: LayoutInflater.from(context).inflate(R.layout.spinner_color, parent, false)

        if(item != null) {

            val background = spinner.findViewById<LinearLayout>(R.id.spn_color_lyt).background as GradientDrawable
            background.mutate()
            (item.colorBackground.takeIf { it >= 0 } ?: 0).let { background.setColor(colorValueBackground[it]) }
            item.colorBorders.takeIf { it > 0 }?.let { background.setStroke(2, colorValueBorders[it]) }
                ?: background.setStroke(0, null)

            val textView: TextView = spinner.findViewById(R.id.spn_color_txt)
            textView.text = item.text
            if(item.colorIcons == null) {
                textView.setCompoundDrawables(null, null, null, null)
            }
            else {
                (item.colorIcons.takeIf { it >=0 } ?: 0).let { color ->
                    val icon = textView.compoundDrawables[0]
                    icon?.let {
                        it.mutate()
                        it.setTint(colorValueIcons[color])
                    }
                }
            }

            val progressBar: ProgressBar = spinner.findViewById(R.id.spn_color_progressbar)
            if(item.colorProgressbar == null)
                progressBar.visibility = View.GONE
            else {
                progressBar.progressTintList = ColorStateList.valueOf(
                    item.colorProgressbar.takeIf { it > 0 }?.let { colorValueBorders[it] }
                        ?: ContextCompat.getColor(context, R.color.blue)
                )

                progressBar.secondaryProgressTintList = ColorStateList.valueOf(colorValueProgressbarSecondary[item.colorProgressbar])
                progressBar.visibility = View.VISIBLE
            }
        }

        return spinner
    }
}