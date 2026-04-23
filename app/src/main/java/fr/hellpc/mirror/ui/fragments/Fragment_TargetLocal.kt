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

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.databinding.FragmentTargetLocalBinding
import fr.hellpc.mirror.managers.Manager_Alerts
import fr.hellpc.mirror.ui.viewmodels.ViewModel_Edit
import fr.hellpc.mirror.utilities.Utility_BackupTarget
import kotlin.properties.Delegates


class Fragment_TargetLocal : Fragment() {

    private var _binding: FragmentTargetLocalBinding? = null
    private val binding get() = _binding!!
    private var tabIsSource by Delegates.notNull<Boolean>()

    private val utilityPathFormat by lazy { Utility_BackupTarget() }
    private var fragmentProtocol = "LOCAL"

    // DB access
    private val viewModel: ViewModel_Edit by activityViewModels()

    // -------------------------------------

    // Folder browser result
    private val selectFolder = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val path = result.data?.data?.path?.let { utilityPathFormat.findFullPath(it) } ?: ""
            binding.targetLocalEditPathSelect.setText(path)
            checkAndroidQLimitations(path)
            viewModel.setInfoTimeZoneVisibility(tabIsSource, path)
        }
    }

    companion object {
        fun newInstance(tab: String, protocol: String) : Fragment_TargetLocal {
            val args = Bundle()
            args.putString("TAB", tab)
            args.putString("PROTOCOL", protocol)
            val fragment = Fragment_TargetLocal()
            fragment.arguments = args
            return fragment
        }
    }


    // --------
    // Fragment
    // --------

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTargetLocalBinding.inflate(inflater, container, false)
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
        tabIsSource = requireArguments().getString("TAB") == viewModel.srcTabName
        requireArguments().getString("PROTOCOL")?.let { fragmentProtocol = it }

        binding.targetLocalEditPathSelect.setOnClickListener { openFolderExplorer() }
        manageTimeZoneInfoVisibility()

        if(loadData || viewModel.backupIsLocked())
            loadDataFromDb()
    }

    /** Show information about time zone change if destination is External Storage **/
    private fun manageTimeZoneInfoVisibility() {
        val timeZoneVisibility = if(tabIsSource)
            viewModel.infoSrcTimeZoneIsVisible
        else
            viewModel.infoDestTimeZoneIsVisible

        timeZoneVisibility.observe(viewLifecycleOwner) { visible ->
            binding.targetLocalTxtInfoTimezone.visibility = if(visible) View.VISIBLE else View.GONE
        }

    }

    /** Check if this device runs Android 10 and propose path modification if necessary **/
    private fun checkAndroidQLimitations(path: String) {
        if(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && !tabIsSource && !path.startsWith("/storage/emulated/0/") && !path.contains("Android/data/fr.hellpc.mirror/")) {
            val alertPreferences = Manager_Alerts()
            if(alertPreferences.showAndroid10ExternalFolderAlert())
                binding.targetLocalEditPathSelect.setText(utilityPathFormat.movePathToProprietaryFolder(path))
            else {
                val context = activity
                if(context != null)
                    AlertDialog.Builder(context, R.style.AppTheme_AlertDialogStyle).apply {
                        setTitle(getString(R.string.global_warning))
                        setMessage(getString(R.string.target_local_warning_android_10))
                        setPositiveButton(getString(R.string.global_yes)) { _, _ -> binding.targetLocalEditPathSelect.setText(utilityPathFormat.movePathToProprietaryFolder(path)) }
                        setNegativeButton(getString(R.string.global_no)) { _, _ -> }
                        setNeutralButton(R.string.global_always) { _, _ ->
                            alertPreferences.disableAndroid10ExternalFolderAlert()
                            binding.targetLocalEditPathSelect.setText(utilityPathFormat.movePathToProprietaryFolder(path))
                        }
                        show()
                    }
            }
        }
    }


    // ---------------
    // Folder explorer
    // ---------------

    /** Opens folder browser when user click path TextView **/
    private fun openFolderExplorer() {
        selectFolder.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, "content://com.android.externalstorage.documents/document/primary%3A".toUri())
        )
    }


    // ----
    // Data
    // ----

    /** Get current data from UI **/
    fun getCurrentData(): Backup_Target? {
        return if(binding.targetLocalEditPathSelect.text.toString().isBlank())
            null
        else
            Backup_Target(
                fragmentProtocol,
                null,
                null,
                null,
                binding.targetLocalEditPathSelect.text.toString(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
    }

    /** Load data from Database **/
    private fun loadDataFromDb() {
        val target = if(tabIsSource)
            viewModel.backupSrc
        else
            viewModel.backupDest

        target.observe(viewLifecycleOwner) { data ->
            binding.targetLocalEditPathSelect.setText(data.path)
            viewModel.setInfoTimeZoneVisibility(tabIsSource, data.path)
        }
    }
}