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
import android.text.Html.FROM_HTML_MODE_COMPACT
import android.text.Html.fromHtml
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import fr.hellpc.mirror.R
import fr.hellpc.mirror.databinding.RowRecyclerLogsBinding

class Adapter_Recycler_Logs: ListAdapter<String, Adapter_Recycler_Logs.LogRecyclerViewHolder>(LineComparator()) {

    private lateinit var context: Context

    private val colorsMap by lazy { mapOf(
        "<font color=gray>" to "<font color=" + ContextCompat.getColor(context, R.color.pale_gray) + ">",
        "<font color=blue>" to "<font color=" + ContextCompat.getColor(context, R.color.blue) + ">",
        "<font color=green>" to "<font color=" + ContextCompat.getColor(context, R.color.green) + ">",
        "<font color=orange>" to "<font color=" + ContextCompat.getColor(context, R.color.orange) + ">",
        "<font color=red>" to "<font color=" + ContextCompat.getColor(context, R.color.red) + ">"
    ) }

    private val colorBlack by lazy { "<font color=" + ContextCompat.getColor(context, R.color.text_black) + ">" }

    // -------------------------------------

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogRecyclerViewHolder {
        context = parent.context

        val binding = RowRecyclerLogsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogRecyclerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogRecyclerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // -------------------------------------

    inner class LogRecyclerViewHolder(private val binding: RowRecyclerLogsBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(line: String) {
            binding.rowLogsTxt.text = fromHtml(applyColors(line), FROM_HTML_MODE_COMPACT)
        }

        /** Replace color text by color value **/
        private fun applyColors(line: String): String {
            if(line.isEmpty())
                return line

            return Regex("<font color=.*?>").replace(line) { colorsMap[it.value] ?: colorBlack }
        }
    }

    // -------------------------------------

    private class LineComparator : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}