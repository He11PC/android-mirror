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

package fr.hellpc.mirror.ui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fr.hellpc.mirror.R
import fr.hellpc.mirror.ui.adapters.Adapter_Spinner_Image
import fr.hellpc.mirror.databinding.ActivitySettingsBinding
import fr.hellpc.mirror.managers.Manager_Permissions
import fr.hellpc.mirror.managers.Manager_Settings
import kotlinx.coroutines.launch
import java.util.Locale


class Activity_Settings : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings by lazy { Manager_Settings() }

    private var currentTheme = settings.getTheme()
    private var currentLanguage = settings.getLocaleID()
    private var currentLogsRetention = settings.getLogsRetention()
    private var currentLogsErrors = settings.getLogsNativeErrors()
    private var currentLogsWarningSetForeground = settings.getLogsWarningSetForeground()
    private var currentLogsPreserveScrollPosition = settings.getLogsPreserveScrollPosition()
    private var currentNotificationApplyTheme = settings.getNotificationApplyTheme()
    private var currentNotificationType = settings.getNotificationType()

    // -------------------------------------

    // OnBackPressed
    private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { finish() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // -------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.settingsToolbar)

        // Back arrow
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Interface TextViews alignment
        resizeInterfaceTextViews()

        // Load custom spinner with icons
        setSpinnersValues()

        if(savedInstanceState == null)
            loadSettings()

        manageSettingsInterface()
        manageSettingsLogs()
        manageSettingsNotifications()

        setupVisibilityListeners()
    }

    // -------------------------------------

    /** Resize interface textviews for alignment **/
    private fun resizeInterfaceTextViews() = lifecycleScope.launch {
        binding.settingsInterfaceTxtLanguage.measure(0,0)
        binding.settingsInterfaceTxtTheme.measure(0,0)
        val txtMinSize = binding.settingsInterfaceTxtLanguage.measuredWidth
        val txtMaxSize = binding.settingsInterfaceTxtTheme.measuredWidth
        if(txtMinSize < txtMaxSize)
            binding.settingsInterfaceTxtLanguage.layoutParams.apply { width = txtMaxSize }
        else if(txtMaxSize < txtMinSize)
            binding.settingsInterfaceTxtTheme.layoutParams.apply { width = txtMinSize }
    }

    /** Load custom spinner with icons values **/
    private fun setSpinnersValues() {
        binding.settingsInterfaceSpnLanguage.adapter = Adapter_Spinner_Image(this, settings.getLocalesWithFlags())
        binding.settingsInterfaceSpnTheme.adapter = Adapter_Spinner_Image(this, settings.getThemesWithIcon())
    }

    /** Load settings values **/
    private fun loadSettings() = lifecycleScope.launch {
        binding.settingsInterfaceSpnLanguage.setSelection(currentLanguage,false)
        binding.settingsInterfaceSpnTheme.setSelection(currentTheme,false)
        binding.settingsLogsEditRetention.setText(String.format(Locale.getDefault(), "%d", currentLogsRetention))
        binding.settingsLogsChkErrors.isChecked = currentLogsErrors
        binding.settingsLogsChkWarningSetforeground.isChecked = currentLogsWarningSetForeground
        binding.settingsLogsChkPreserveScrollPosition.isChecked = currentLogsPreserveScrollPosition
        binding.settingsNotificationsChkTheme.isChecked = currentNotificationApplyTheme
        binding.settingsNotificationsSpnScheduled.setSelection(currentNotificationType,false)
    }

    /** Manage Interface settings **/
    private fun manageSettingsInterface() = lifecycleScope.launch {
        binding.settingsInterfaceSpnLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { saveLanguage(position) }
        }

        binding.settingsInterfaceSpnTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { settings.saveTheme(position) }
        }
    }

    /** Manage Logs retention settings **/
    private fun manageSettingsLogs() = lifecycleScope.launch {
        binding.settingsLogsEditRetention.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                if(!p0.isNullOrBlank())
                    settings.saveLogsRetention(p0.toString().toInt())
            }

            override fun afterTextChanged(p0: Editable?) { }
        })

        binding.settingsLogsChkErrors.setOnCheckedChangeListener { _, isChecked ->
            settings.saveLogsNativeErrors(isChecked)
        }

        binding.settingsLogsChkWarningSetforeground.setOnCheckedChangeListener { _, isChecked ->
            settings.saveLogsWarningSetForeground(isChecked)
        }

        binding.settingsLogsChkPreserveScrollPosition.setOnCheckedChangeListener { _, isChecked ->
            settings.saveLogsPreserveScrollPosition(isChecked)
        }
    }

    /** Manage Notifications settings **/
    private fun manageSettingsNotifications() = lifecycleScope.launch {
        binding.settingsNotificationsChkTheme.setOnCheckedChangeListener { _, isChecked ->
            settings.saveNotificationApplyTheme(isChecked)
        }

        if(!Manager_Permissions().permissionNotificationsIsGranted()) {
            binding.settingsNotificationsTxtScheduled.isEnabled = false
            binding.settingsNotificationsBtnScheduledInfo.visibility = View.GONE
            binding.settingsNotificationsTxtWarning.visibility = View.VISIBLE
        }
        else {
            binding.settingsNotificationsBtnScheduledInfo.visibility = View.VISIBLE
            binding.settingsNotificationsTxtWarning.visibility = View.GONE

            binding.settingsNotificationsSpnScheduled.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { settings.saveNotificationType(position) }
            }
        }
    }

    /** Setup listeners **/
    private fun setupVisibilityListeners() = lifecycleScope.launch {
        binding.settingsNotificationsBtnScheduledInfo.setOnClickListener {
            binding.settingsNotificationsTxtInfo.visibility = View.VISIBLE
            binding.settingsNotificationsBtnScheduledInfo.visibility = View.GONE
        }

        binding.settingsNotificationsTxtInfo.setOnClickListener {
            binding.settingsNotificationsBtnScheduledInfo.visibility = View.VISIBLE
            binding.settingsNotificationsTxtInfo.visibility = View.GONE
        }
    }

    // -------------------------------------

    /** Save user selected language **/
    private fun saveLanguage(id: Int) {
        settings.saveLocale(id)
        // Refresh interface if System Default is selected on Android < Tiramisu
        if(id == 0 && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val intent = Intent(this, Activity_Settings::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            Runtime.getRuntime().exit(0)
        }
    }

}