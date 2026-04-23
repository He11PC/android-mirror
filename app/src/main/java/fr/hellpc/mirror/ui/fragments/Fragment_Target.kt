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

package fr.hellpc.mirror.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import fr.hellpc.mirror.R
import fr.hellpc.mirror.ui.adapters.Adapter_Spinner_Image
import fr.hellpc.mirror.data.Spinner_IconAndText
import fr.hellpc.mirror.databinding.FragmentTargetBinding
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.ui.viewmodels.ViewModel_Edit
import kotlinx.coroutines.launch


class Fragment_Target: Fragment() {

    private var _binding: FragmentTargetBinding? = null
    private val binding get() = _binding!!

    private val protocolID by lazy { requireContext().resources.getStringArray(R.array.protocol_id) }

    // DB access
    private val viewModel: ViewModel_Edit by activityViewModels()

    companion object {
        fun newInstance(tab: String) : Fragment_Target {
            val args = Bundle()
            args.putString("TAB", tab)
            val fragment = Fragment_Target()
            fragment.arguments = args
            return fragment
        }
    }


    // --------
    // Fragment
    // --------

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTargetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initialize(savedInstanceState == null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    // --------------
    // User interface
    // --------------

    /** Initialize UI **/
    private fun initialize(loadData: Boolean) {
        val tab = requireArguments().getString("TAB") ?: throw IllegalArgumentException(getString(R.string.target_tab_error))

        setupTabTitle(tab)
        setupObserver()

        setupTargetSpinner(tab)
        if(loadData || viewModel.backupIsLocked())
            loadDataFromDb(tab)
    }

    /** Setup tab title **/
    private fun setupTabTitle(tab: String) {
        binding.targetProtocolTxtTitle.text = if(tab == viewModel.srcTabName)
            getString(R.string.edit_source)
        else
            getString(R.string.edit_destination)
    }

    /** Hide/show WebDav remote warning **/
    private fun setupObserver() = lifecycleScope.launch {
        viewModel.webdavRemoteWarningIsVisible.observe(viewLifecycleOwner) { visible ->
            binding.webdavRemoteWarning.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    /** Setup protocol spinner **/
    private fun setupTargetSpinner(tab: String) = lifecycleScope.launch {
        // Load spinner values
        val colorGray = ContextCompat.getColor(requireContext(), R.color.gray)
        val spinnerValues = resources.getStringArray(R.array.protocol).mapIndexed { index, s ->
            val icon = when(index) {
                0 -> R.drawable.ic_phone
                1,2 -> R.drawable.ic_lan
                else -> R.drawable.ic_cloud
            }
            Spinner_IconAndText(icon, colorGray, s)
        }
        binding.targetProtocolSpn.adapter = Adapter_Spinner_Image(requireContext(), spinnerValues)

        // Change fragment depending on spinner selection
        binding.targetProtocolSpn.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val protocol = protocolID[position]

                if(viewModel.protocolHasChanged(tab, protocol)) {
                    val newFragment = when (position) {
                        0 -> Fragment_TargetLocal.newInstance(tab, protocol)
                        1 -> Fragment_TargetNFS.newInstance(tab, protocol)
                        2 -> Fragment_TargetSMB.newInstance(tab, protocol)
                        3 -> Fragment_TargetFTP.newInstance(tab, protocol)
                        4 -> Fragment_TargetSFTP.newInstance(tab, protocol)
                        5 -> Fragment_TargetWebDav.newInstance(tab, protocol)
                        else -> throw IllegalArgumentException(getString(R.string.target_location_error))
                    }
                    childFragmentManager.beginTransaction().replace(R.id.target_protocol_fragment_container, newFragment, tab).commit()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { parent?.setSelection(0) }
        }
    }


    // ----
    // Data
    // ----

    /** Get current data from UI **/
    fun getCurrentData(tab: String): Backup_Target? {
        return when(protocolID[binding.targetProtocolSpn.selectedItemPosition]) {
            "LOCAL" -> (childFragmentManager.findFragmentByTag(tab) as Fragment_TargetLocal).getCurrentData()
            "NFS" -> (childFragmentManager.findFragmentByTag(tab) as Fragment_TargetNFS).getCurrentData(false)
            "SMB" -> (childFragmentManager.findFragmentByTag(tab) as Fragment_TargetSMB).getCurrentData(false)
            "FTP" -> (childFragmentManager.findFragmentByTag(tab) as Fragment_TargetFTP).getCurrentData(false)
            "SFTP" -> (childFragmentManager.findFragmentByTag(tab) as Fragment_TargetSFTP).getCurrentData(false)
            "WDAV" -> (childFragmentManager.findFragmentByTag(tab) as Fragment_TargetWebDav).getCurrentData(false)
            else -> throw IllegalArgumentException(getString(R.string.target_location_error))
        }
    }

    /** Load data from Database **/
    private fun loadDataFromDb(tab: String) {
        if(tab == viewModel.srcTabName)
            viewModel.backupSrcProtocol.observe(viewLifecycleOwner) {
                binding.targetProtocolSpn.setSelection(protocolID.indexOf(it))
            }
        else
            viewModel.backupDestProtocol.observe(viewLifecycleOwner) {
                binding.targetProtocolSpn.setSelection(protocolID.indexOf(it))
            }
    }
}