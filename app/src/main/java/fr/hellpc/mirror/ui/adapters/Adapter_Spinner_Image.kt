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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.Spinner_IconAndText

class Adapter_Spinner_Image(ctx: Context, data: List<Spinner_IconAndText>): ArrayAdapter<Spinner_IconAndText>(ctx, 0, data) {

    override fun getView(position: Int, recycledView: View?, parent: ViewGroup): View {
        return this.createView(position, recycledView, parent)
    }

    override fun getDropDownView(position: Int, recycledView: View?, parent: ViewGroup): View {
        return this.createView(position, recycledView, parent)
    }

    private fun createView(position: Int, recycledView: View?, parent: ViewGroup): View {
        val item = getItem(position)
        val spinner = recycledView ?: LayoutInflater.from(context).inflate(R.layout.spinner_image, parent, false)

        if(item != null) {
            val textView: TextView = spinner.findViewById(R.id.spn_img_txt)
            textView.text = item.text

            val icon = ContextCompat.getDrawable(context, item.iconID)
            if(item.iconTint != null)
                icon?.let {
                    it.mutate()
                    it.setTint(item.iconTint)
                }
            textView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        }

        return spinner
    }
}