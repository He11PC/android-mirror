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

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.FolderExplorer_File
import fr.hellpc.mirror.databinding.RowRecyclerFolderExplorerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URLConnection.guessContentTypeFromName

class Adapter_Recycler_FolderExplorer(val folderSelected: (String) -> Unit): ListAdapter<FolderExplorer_File, Adapter_Recycler_FolderExplorer.FolderExplorerRecyclerViewHolder>(FoldersComparator()) {

    private lateinit var context: Context
    private val scopeMain by lazy { CoroutineScope(Job() + Dispatchers.Main) }

    private val iconsMap by lazy { mapOf(
        "up" to getDrawable(context, R.drawable.ic_folder_up),
        "folder" to getDrawable(context, R.drawable.ic_folder),
        "file" to getDrawable(context, R.drawable.ic_file),
        "image" to getDrawable(context, R.drawable.ic_file_image),
        "audio" to getDrawable(context, R.drawable.ic_file_music),
        "video" to getDrawable(context, R.drawable.ic_file_video),
        "text" to getDrawable(context, R.drawable.ic_file_text)
    ) }

    private val colorsMap by lazy { mapOf(
        "blue" to ColorStateList.valueOf(ContextCompat.getColor(context, R.color.blue)),
        "gray" to ColorStateList.valueOf(ContextCompat.getColor(context, R.color.gray))
    ) }

    // -------------------------------------

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderExplorerRecyclerViewHolder {
        context = parent.context

        val binding = RowRecyclerFolderExplorerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return FolderExplorerRecyclerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderExplorerRecyclerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // -------------------------------------

    inner class FolderExplorerRecyclerViewHolder(private val binding: RowRecyclerFolderExplorerBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FolderExplorer_File) {
            setupListeners(item.name, item.isDirectory)
            setupIcon(item.name, item.isDirectory)
            setupColor(item.name, item.isDirectory)

            binding.rowFolderExplorerTxt.text = item.name
        }

        /** Setup buttons click listeners **/
        private fun setupListeners(name: String, isDirectory: Boolean) = scopeMain.launch {
            if(isDirectory)
                binding.rowFolderExplorerLyt.setOnClickListener { folderSelected(name) }
            else
                binding.rowFolderExplorerLyt.setOnClickListener(null)
        }

        /** Setup item icon **/
        @SuppressLint("UseCompatTextViewDrawableApis")
        private fun setupIcon(name: String, isDirectory: Boolean) = scopeMain.launch {
            val icon = when {
                isDirectory -> if(name == "..") iconsMap["up"] else iconsMap["folder"]
                else -> with(guessContentTypeFromName(name)) {
                    when {
                        isNullOrBlank() -> iconsMap["file"]
                        contains("image") -> iconsMap["image"]
                        contains("audio") -> iconsMap["audio"]
                        contains("video") -> iconsMap["video"]
                        contains(Regex("text|document|pdf|oxps")) -> iconsMap["text"]
                        else -> iconsMap["file"]
                    }
                }
            }

            binding.rowFolderExplorerTxt.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        }

        /** Setup item icon color **/
        @SuppressLint("UseCompatTextViewDrawableApis")
        private fun setupColor(name: String, isDirectory: Boolean) = scopeMain.launch {
            binding.rowFolderExplorerTxt.compoundDrawableTintList = if(isDirectory && name != "..") colorsMap["blue"] else colorsMap["gray"]
        }
    }

    // -------------------------------------

    private class FoldersComparator : DiffUtil.ItemCallback<FolderExplorer_File>() {
        override fun areItemsTheSame(oldItem: FolderExplorer_File, newItem: FolderExplorer_File): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: FolderExplorer_File, newItem: FolderExplorer_File): Boolean = oldItem == newItem
    }
}