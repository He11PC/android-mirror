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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import fr.hellpc.mirror.data.Target_Credentials
import fr.hellpc.mirror.databinding.RowRecyclerAutofillBinding

class Adapter_Recycler_Autofill(val credentialsSelected: (Target_Credentials) -> Unit): ListAdapter<Target_Credentials, Adapter_Recycler_Autofill.AutofillRecyclerViewHolder>(CredentialsComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AutofillRecyclerViewHolder {
        val binding = RowRecyclerAutofillBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return AutofillRecyclerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AutofillRecyclerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // -------------------------------------

    inner class AutofillRecyclerViewHolder(private val binding: RowRecyclerAutofillBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Target_Credentials) {
            binding.rowAutofillTxtServerData.text = item.server

            setData(binding.rowAutofillTxtDomainTitle, binding.rowAutofillTxtDomainData, item.domain)
            setData(binding.rowAutofillTxtPortTitle, binding.rowAutofillTxtPortData, item.port.toString())
            setData(binding.rowAutofillTxtLoginTitle, binding.rowAutofillTxtLoginData, item.login)
            setData(binding.rowAutofillTxtPasswordTitle, binding.rowAutofillTxtPasswordData, item.password)
            setData(binding.rowAutofillTxtUidTitle, binding.rowAutofillTxtUidData, item.uid)
            setData(binding.rowAutofillTxtGidTitle, binding.rowAutofillTxtGidData, item.gid)
            setData(binding.rowAutofillTxtShareTitle, binding.rowAutofillTxtShareData, item.share)
            setData(binding.rowAutofillTxtHostkeyTitle, binding.rowAutofillTxtHostkeyData, item.hostKey)

            if(item.port == null) {
                binding.rowAutofillLytPort.visibility = View.GONE
                binding.rowAutofillTxtPortTitle.visibility = View.GONE
            }
            else {
                binding.rowAutofillLytPort.visibility = View.VISIBLE
                binding.rowAutofillTxtPortTitle.visibility = View.VISIBLE
            }
            binding.rowAutofillTxtSslData.visibility = if(item.ssl == true) View.VISIBLE else View.GONE

            binding.rowAutofillLyt.setOnClickListener { credentialsSelected(item) }
        }

        /** Manage credential value and visibility **/
        private fun setData(title: TextView, credential: TextView, data: String?) {
            if(data.isNullOrBlank()) {
                title.visibility = View.GONE
                credential.visibility = View.GONE
            }
            else {
                credential.text = data
                title.visibility = View.VISIBLE
                credential.visibility = View.VISIBLE
            }
        }
    }

    // -------------------------------------

    private class CredentialsComparator : DiffUtil.ItemCallback<Target_Credentials>() {
        override fun areItemsTheSame(oldItem: Target_Credentials, newItem: Target_Credentials): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: Target_Credentials, newItem: Target_Credentials): Boolean = oldItem == newItem
    }
}