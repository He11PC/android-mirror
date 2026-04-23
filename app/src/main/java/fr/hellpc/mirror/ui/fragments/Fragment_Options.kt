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

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.InputFilter
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.room.Backup_Options
import fr.hellpc.mirror.databinding.FragmentOptionsBinding
import fr.hellpc.mirror.ui.viewmodels.ViewModel_Edit
import fr.hellpc.mirror.utilities.Utility_BackupTarget
import fr.hellpc.mirror.utilities.Utility_Conversion.sizeToLong
import fr.hellpc.mirror.utilities.Utility_Conversion.sizeToReadable
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale


class Fragment_Options : Fragment() {

    private var _binding: FragmentOptionsBinding? = null
    private val binding get() = _binding!!

    private val editUtils by lazy { Utility_BackupTarget() }

    // DB access
    private val viewModel: ViewModel_Edit by activityViewModels()

    companion object {
        fun newInstance(): Fragment_Options { return Fragment_Options() }
    }


    // --------
    // Fragment
    // --------

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOptionsBinding.inflate(inflater, container, false)
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
        // Clickable html link
        binding.optionsFiltersTxtBlackList.movementMethod = LinkMovementMethod.getInstance()
        resizeFileSizeTextViews()

        setupInteractionListeners()
        setupVisibilityListeners()
        setupConstraintsListeners()
        setupEditFilters()
        setupObservers()

        if(loadData || viewModel.backupIsLocked())
            loadDataFromDb()
    }

    // -------------------------------------

    /** Resize files size filter textViews for alignment **/
    private fun resizeFileSizeTextViews() = lifecycleScope.launch {
        binding.optionsFiltersTxtMinSize.measure(0,0)
        binding.optionsFiltersTxtMaxSize.measure(0,0)
        val txtMinSize = binding.optionsFiltersTxtMinSize.measuredWidth
        val txtMaxSize = binding.optionsFiltersTxtMaxSize.measuredWidth
        if(txtMinSize < txtMaxSize)
            binding.optionsFiltersTxtMinSize.layoutParams.apply { width = txtMaxSize }
        else if(txtMaxSize < txtMinSize)
            binding.optionsFiltersTxtMaxSize.layoutParams.apply { width = txtMinSize }
    }

    /** Check if orphan folder is valid and show the alert message if not **/
    private fun checkOrphanFolderValidity() {
        val isValid = editUtils.directoryIsValid(binding.optionsOrphansEditFolder.text.toString(), true)
        binding.optionsOrphansTxtFolderInvalid.visibility = if(!isValid) View.VISIBLE else View.GONE
    }

    // -------------------------------------

    /** Setup user interaction listeners **/
    private fun setupInteractionListeners() = lifecycleScope.launch {
        // Time picker
        binding.optionsScheduleEditTime.setOnClickListener {
            val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute -> viewModel.loadScheduledTime(hour, minute) }

            TimePickerDialog(
                activity,
                R.style.AppTheme_TimePicker,
                timeSetListener,
                viewModel.timePickerValue.value!!.hour,
                viewModel.timePickerValue.value!!.minute,
                android.text.format.DateFormat.is24HourFormat(activity)
            ).show()
        }
    }

    /** Setup visibility listeners **/
    private fun setupVisibilityListeners() = lifecycleScope.launch {
        // Biometric authentication
        when(BiometricManager.from(App.instance).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> { binding.optionsSecurityTxtProtectEditionWarning.visibility = View.GONE }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                binding.optionsSecurityChkProtectEdition.isClickable = false
                binding.optionsSecurityTxtProtectEditionWarning.visibility = View.VISIBLE
            }
            else -> binding.optionsSecurityLyt.visibility = View.GONE
        }

        // File date comparison mode
        binding.optionsBackupChkDateComparisonAuto.setOnCheckedChangeListener { _, isChecked ->
            binding.optionsBackupFlowDateComparison.visibility = if(isChecked) View.GONE else View.VISIBLE
        }

        // Orphans options
        binding.optionsOrphansSpnAction.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                binding.optionsOrphansFlowFolder.visibility = if(position == 1) View.VISIBLE else View.GONE
                checkOrphanFolderValidity()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { parent?.setSelection(0) }
        }

        // Automatic backup options
        binding.optionsScheduleChkEnable.setOnCheckedChangeListener { _, isChecked ->
            binding.optionsScheduleDetailsLyt.visibility = if(isChecked) View.VISIBLE else View.GONE
        }

        // Hour of the day option
        binding.optionsScheduleSpnIntervalUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                binding.optionsScheduleLytTime.visibility = if(position == 0) View.VISIBLE else View.GONE
                binding.optionsScheduleTxtWarningShortDelay.visibility = if(position == 2) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { parent?.setSelection(0) }
        }
    }

    /** Set charging & idle constraints **/
    private fun setupConstraintsListeners() = lifecycleScope.launch {
        binding.optionsScheduleChkCharging.setOnCheckedChangeListener { _, isChecked ->
            val idleIsChecked = binding.optionsScheduleChkIdle.isChecked
            binding.optionsScheduleChkIdle.isChecked = !isChecked && idleIsChecked
        }

        binding.optionsScheduleChkIdle.setOnCheckedChangeListener { _, isChecked ->
            val chargingIsChecked = binding.optionsScheduleChkCharging.isChecked
            binding.optionsScheduleChkCharging.isChecked = !isChecked && chargingIsChecked
            binding.optionsScheduleTxtWarningIdle.visibility = if(isChecked) View.VISIBLE else View.GONE
        }
    }

    // -------------------------------------

    /** Setup filters for EditText **/
    private fun setupEditFilters() = lifecycleScope.launch {
        binding.optionsOrphansEditFolder.filters= arrayOf(InputFilter { source, _, _, _, _, _ ->
            source.filter { editUtils.getAllowedCharactersDirectory().matches(it.toString()) }
        })

        binding.optionsFiltersEditBlackList.filters= arrayOf(InputFilter { source, _, _, _, _, _ ->
            source.filter { editUtils.getAllowedCharactersGlob().matches(it.toString()) }
        })
    }

    // -------------------------------------

    /** Hide/show global interface elements **/
    private fun setupObservers() = lifecycleScope.launch {
        // Wifi Only option
        viewModel.optWifiIsVisible.observe(viewLifecycleOwner) { visible ->
            binding.optionsBackupChkWifi.visibility = if(visible) View.VISIBLE else View.GONE
        }

        // Nomedia option
        viewModel.optNomediaIsVisible.observe(viewLifecycleOwner) { visible ->
            binding.optionsBackupChkNomedia.visibility = if(visible) View.VISIBLE else View.GONE
        }

        // Time picker
        viewModel.timePickerValue.observe(viewLifecycleOwner) { time ->
            val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            binding.optionsScheduleEditTime.setText(formatter.format(time))
        }
    }


    // ----
    // Data
    // ----

    /** Get current data from UI **/
    fun getCurrentData(): Backup_Options {
        val accurateDate = binding.optionsBackupSpnDateComparisonMode.selectedItemPosition == 0

        var orphanFolder = editUtils.trimDirectory(binding.optionsOrphansEditFolder.text.toString())
        if(!editUtils.directoryIsValid(orphanFolder, false) && binding.optionsOrphansSpnAction.selectedItemPosition == 1)
            orphanFolder = getString(R.string.orphans_folder_name)

        val minSize = binding.optionsFiltersEditMinSize.text.toString().ifBlank { null }?.let { (it.trim()+" "+binding.optionsFiltersSpnMinSize.selectedItem.toString()).sizeToLong() }
        val maxSize = binding.optionsFiltersEditMaxSize.text.toString().ifBlank { null }?.let { (it.trim()+" "+binding.optionsFiltersSpnMaxSize.selectedItem.toString()).sizeToLong() }

        val interval = binding.optionsScheduleEditInterval.text.toString().trim().toIntOrNull()
            ?: when(binding.optionsScheduleSpnIntervalUnit.selectedItemPosition) {
                0 -> 1
                1 -> 12
                else -> 30
            }

        // Hour and Minute are recovered on ViewModel
        return Backup_Options(
            binding.optionsSecurityChkProtectEdition.isChecked,
            binding.optionsBackupChkRecursive.isChecked,
            binding.optionsBackupChkRetryCorrupted.isChecked,
            binding.optionsBackupChkDateComparisonAuto.isChecked,
            accurateDate,
            binding.optionsBackupChkNomedia.isChecked,
            binding.optionsOrphansSpnAction.selectedItemPosition,
            orphanFolder,
            binding.optionsFiltersChkImages.isChecked,
            binding.optionsFiltersChkAudio.isChecked,
            binding.optionsFiltersChkVideos.isChecked,
            binding.optionsFiltersChkDocuments.isChecked,
            binding.optionsFiltersChkOthers.isChecked,
            minSize,
            maxSize,
            binding.optionsFiltersEditBlackList.text.toString(),
            binding.optionsScheduleChkEnable.isChecked,
            interval,
            binding.optionsScheduleSpnIntervalUnit.selectedItemPosition,
            null,
            null,
            binding.optionsBackupChkWifi.isChecked,
            binding.optionsScheduleChkCharging.isChecked,
            binding.optionsScheduleChkIdle.isChecked
        )
    }

    /** Load data from Database **/
    private fun loadDataFromDb() {
        binding.optionsFiltersSpnMaxSize.setSelection(2, false)

        viewModel.backupOpt.observe(viewLifecycleOwner) { options ->

            binding.optionsSecurityChkProtectEdition.isChecked = options.protectEdition

            binding.optionsBackupChkRecursive.isChecked = options.recursive
            binding.optionsBackupChkRetryCorrupted.isChecked = options.retryCorrupted
            binding.optionsBackupChkDateComparisonAuto.isChecked = options.dateComparison_Auto
            binding.optionsBackupSpnDateComparisonMode.setSelection(if(options.dateComparison_Strict) 0 else 1, false)
            binding.optionsBackupChkNomedia.isChecked = options.nomedia
            binding.optionsBackupChkWifi.isChecked = options.const_wifi

            binding.optionsOrphansSpnAction.setSelection(options.orph_action, false)
            binding.optionsOrphansEditFolder.setText(options.orph_folder)

            binding.optionsFiltersChkImages.isChecked = options.flt_images
            binding.optionsFiltersChkAudio.isChecked = options.flt_audio
            binding.optionsFiltersChkVideos.isChecked = options.flt_videos
            binding.optionsFiltersChkDocuments.isChecked = options.flt_documents
            binding.optionsFiltersChkOthers.isChecked = options.flt_others
            binding.optionsFiltersEditBlackList.setText(options.flt_blackList)

            options.flt_minSize?.let {
                val minSize = it.sizeToReadable().split(" ")
                binding.optionsFiltersEditMinSize.setText(minSize[0])
                binding.optionsFiltersSpnMinSize.setSelection(resources.getStringArray(R.array.file_size_unit).indexOf(minSize[1]), false)
            }

            options.flt_maxSize?.let {
                val maxSize = it.sizeToReadable().split(" ")
                binding.optionsFiltersEditMaxSize.setText(maxSize[0])
                binding.optionsFiltersSpnMaxSize.setSelection(resources.getStringArray(R.array.file_size_unit).indexOf(maxSize[1]), false)
            }

            binding.optionsScheduleChkEnable.isChecked = options.schedule
            binding.optionsScheduleEditInterval.setText(String.format(Locale.getDefault(), "%d", options.interval))
            binding.optionsScheduleSpnIntervalUnit.setSelection(options.interval_unit, false)
            binding.optionsScheduleChkCharging.isChecked = options.const_charging
            binding.optionsScheduleChkIdle.isChecked = options.const_idle

            binding.optionsSecurityChkProtectEdition.jumpDrawablesToCurrentState()
            binding.optionsBackupChkRecursive.jumpDrawablesToCurrentState()
            binding.optionsBackupChkRetryCorrupted.jumpDrawablesToCurrentState()
            binding.optionsBackupChkDateComparisonAuto.jumpDrawablesToCurrentState()
            binding.optionsBackupChkWifi.jumpDrawablesToCurrentState()
            binding.optionsBackupChkNomedia.jumpDrawablesToCurrentState()
            binding.optionsFiltersChkImages.jumpDrawablesToCurrentState()
            binding.optionsFiltersChkAudio.jumpDrawablesToCurrentState()
            binding.optionsFiltersChkVideos.jumpDrawablesToCurrentState()
            binding.optionsFiltersChkDocuments.jumpDrawablesToCurrentState()
            binding.optionsFiltersChkOthers.jumpDrawablesToCurrentState()
            binding.optionsScheduleChkEnable.jumpDrawablesToCurrentState()
            binding.optionsScheduleChkCharging.jumpDrawablesToCurrentState()
            binding.optionsScheduleChkIdle.jumpDrawablesToCurrentState()
        }
    }
}