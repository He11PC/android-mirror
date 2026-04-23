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

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.room.Backup_Colors
import fr.hellpc.mirror.data.room.Backup_Options
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.databinding.ActivityEditBinding
import fr.hellpc.mirror.managers.Manager_Alerts
import fr.hellpc.mirror.ui.adapters.Adapter_ViewPager_Edit
import fr.hellpc.mirror.ui.fragments.Fragment_Appearance
import fr.hellpc.mirror.ui.fragments.Fragment_Options
import fr.hellpc.mirror.ui.fragments.Fragment_Target
import fr.hellpc.mirror.ui.viewmodels.ViewModel_Edit
import kotlinx.coroutines.launch


class Activity_Edit: AppCompatActivity() {

    private lateinit var binding: ActivityEditBinding

    // Used to change backup status onPause/onResume
    private var backupId = -1

    // DB access
    private val viewModel: ViewModel_Edit by viewModels { ViewModel_Edit.Factory }

    // OnBackPressed
    private val doubleBackButtonSnackbar by lazy { Snackbar.make(binding.editLyt, getString(R.string.edit_exit_alert), Snackbar.LENGTH_SHORT).setBackgroundTint(ContextCompat.getColor(this, R.color.orange)) }
    private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { exitOnBackPressed() }
    }

    companion object {
        const val PARAM_BACKUP_ID = "PARAM_BACKUP_ID"
        const val PARAM_IS_PROTECTED = "PARAM_IS_PROTECTED"
    }


    // ---------
    // Biometric
    // ---------

    private val executor by lazy { ContextCompat.getMainExecutor(this) }

    private val biometricPrompt by lazy {
        BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                viewModel.unlockBackup(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                cancelEdition()
            }

        })
    }

    private val promptInfo by lazy {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.edit_authentication_title))
            .setSubtitle(getString(R.string.edit_authentication_message))
            .setNegativeButtonText(getString(R.string.global_cancel))
            .build()
    }


    // --------
    // Activity
    // --------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.editToolbar)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        initialise(savedInstanceState == null)
    }

    override fun onPause() {
        super.onPause()
        viewModel.updateStatus(backupId, "idle")
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateStatus(backupId, "edit")
    }


    // --------------
    // User interface
    // --------------

    /** Initialize UI **/
    private fun initialise(loadData: Boolean) {
        backupId = intent.getIntExtra(PARAM_BACKUP_ID, -1)

        setupActivityTitle(backupId == -1)

        setupListeners()
        setupObservers()
        loadViewPager()

        if(loadData)
            loadBackup()
    }

    /** Change activity title from edition to creation if necessary **/
    private fun setupActivityTitle(isNew: Boolean) = lifecycleScope.launch {
        if(isNew)
            binding.editToolbar.title = getString(R.string.edit_title_new)
    }

    /** Setup buttons click listeners **/
    private fun setupListeners() = lifecycleScope.launch {
        binding.editBtnSave.setOnClickListener { saveBackup() }
        binding.editBtnCancel.setOnClickListener { cancelEdition() }
    }

    /** Setup security observers **/
    private fun setupObservers() {
        viewModel.backupLocked.observe(this) { locked ->
            if(locked)
                biometricPrompt.authenticate(promptInfo)
        }

        viewModel.securityFailed.observe(this) { failed ->
            if(failed)
                cancelEdition()
        }
    }

    /** Load ViewPager tabs **/
    private fun loadViewPager() {
        val viewPager = binding.editViewpager
        val fragmentList = arrayListOf(
            Fragment_Target.newInstance(viewModel.srcTabName),
            Fragment_Target.newInstance(viewModel.destTabName),
            Fragment_Options.newInstance(),
            Fragment_Appearance.newInstance()
        )
        viewPager.adapter = Adapter_ViewPager_Edit(this, fragmentList)
        viewPager.offscreenPageLimit = 3

        // Attach ViewPager2 fragments to tabLayout
        val tabLayout = binding.editTabLyt
        tabLayout.setupWithViewPager(
            viewPager,
            listOf(
                R.drawable.ic_source,
                R.drawable.ic_destination,
                R.drawable.ic_options,
                R.drawable.ic_appearance
            )
        )
    }

    /** Link ViewPager2 fragments to TabLayout */
    private fun TabLayout.setupWithViewPager(viewPager: ViewPager2, icons: List<Int>) {
        if (icons.size != viewPager.adapter?.itemCount)
            throw Exception("The size of list and the tab count should be equal!")

        TabLayoutMediator(this, viewPager) { tab, position ->
            tab.icon = ContextCompat.getDrawable(this.context, icons[position])
        }.attach()
    }


    // -----------
    // Load Backup
    // -----------

    /** Load Backup if needed **/
    private fun loadBackup() {
        val isProtected = intent.getBooleanExtra(PARAM_IS_PROTECTED, true)
        viewModel.initialise(backupId, isProtected)
    }


    // ---------------
    // Buttons actions
    // ---------------

    /** Called when the user taps the Cancel button or in case of security failure **/
    private fun cancelEdition() {
        finish()
    }

    /** Return gesture **/
    private fun exitOnBackPressed() {
        // Security to prevent exit without saving when pressing back button
        if (doubleBackButtonSnackbar.isShown) {
            doubleBackButtonSnackbar.dismiss()
            cancelEdition()
        }
        doubleBackButtonSnackbar.show()
    }

    /** Called when the user taps the Save button */
    private fun saveBackup() {
        val errorMsg by lazy { Snackbar.make(binding.editLyt, getString(R.string.edit_invalid_alert), Snackbar.LENGTH_SHORT).setBackgroundTint(ContextCompat.getColor(this, R.color.red)) }
        val errorCredentials by lazy { Snackbar.make(binding.editLyt, getString(R.string.edit_invalid_credentials_alert), Snackbar.LENGTH_SHORT).setBackgroundTint(ContextCompat.getColor(this, R.color.red)) }
        val errorHostKey by lazy { Snackbar.make(binding.editLyt, getString(R.string.edit_invalid_hostkey_alert), Snackbar.LENGTH_SHORT).setBackgroundTint(ContextCompat.getColor(this, R.color.red)) }

        try {
            // Recovering Data
            val sourceFragment = supportFragmentManager.findFragmentByTag("f0") as Fragment_Target
            val src = sourceFragment.getCurrentData(viewModel.srcTabName)
            val destFragment = supportFragmentManager.findFragmentByTag("f1") as Fragment_Target
            val dest = destFragment.getCurrentData(viewModel.destTabName)

            // Verification and saving
            if (src == null || dest == null || src == dest)
                errorMsg.show()
            else {
                val requireCredentials = listOf("FTP", "SFTP", "WDAV")
                val srcCredentialsAreWrong = src.login.isNullOrBlank() && src.protocol in requireCredentials
                val destCredentialsAreWrong = dest.login.isNullOrBlank() && dest.protocol in requireCredentials

                val srcHostKeyIsWrong = src.protocol == "SFTP" && src.hostKey.isNullOrBlank()
                val destHostKeyIsWrong = dest.protocol == "SFTP" && dest.hostKey.isNullOrBlank()

                if(srcCredentialsAreWrong || destCredentialsAreWrong)
                    errorCredentials.show()
                else if(srcHostKeyIsWrong || destHostKeyIsWrong)
                    errorHostKey.show()
                else {
                    val optFragment = supportFragmentManager.findFragmentByTag("f2") as Fragment_Options
                    val opt = optFragment.getCurrentData()

                    val appearanceFragment = supportFragmentManager.findFragmentByTag("f3") as Fragment_Appearance
                    val colors = appearanceFragment.getCurrentData()

                    if(src.protocol != "LOCAL" && dest.protocol != "LOCAL")
                        warningRemoteBackup(src, dest, opt, colors)
                    else
                        saveBackupAndClose(src, dest, opt, colors)
                }
            }
        }
        catch(_: NullPointerException){ errorMsg.show() }
    }

    /** Save backup and close Activity */
    private fun saveBackupAndClose(src: Backup_Target, dest: Backup_Target, opt: Backup_Options, colors: Backup_Colors) {
        viewModel.saveBackup(src, dest, opt, colors)
        finish()
    }

    /** Warning dialog if source and destination are on a remote server */
    private fun warningRemoteBackup(src: Backup_Target, dest: Backup_Target, opt: Backup_Options, colors: Backup_Colors) {
        val alertPreferences = Manager_Alerts()
        if(alertPreferences.showRemoteBackupAlert())
            AlertDialog.Builder(this, R.style.AppTheme_InfoDialogStyle).apply {
                setTitle(getString(R.string.global_information))
                setMessage(getString(R.string.edit_remote_alert_message))
                setPositiveButton(android.R.string.ok) { _, _ -> saveBackupAndClose(src, dest, opt, colors) }
                setNegativeButton(android.R.string.cancel) { _, _ -> }
                setNeutralButton(R.string.global_understood) { _, _ ->
                    alertPreferences.disableRemoteBackupAlert()
                    saveBackupAndClose(src, dest, opt, colors)
                }
                show()
            }
        else
            saveBackupAndClose(src, dest, opt, colors)
    }
}