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

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.judemanutd.autostarter.AutoStartPermissionHelper
import fr.hellpc.mirror.BuildConfig
import fr.hellpc.mirror.R
import fr.hellpc.mirror.databinding.ActivityPermissionsBinding
import fr.hellpc.mirror.managers.Manager_Permissions
import androidx.core.net.toUri

class Activity_Permissions : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding
    private val permissions by lazy { Manager_Permissions() }

    companion object {
        const val RESET = "RESET"
    }

    private var reset = false
    private val pkgName by lazy { applicationContext.packageName }

    // -------------------------------------

    // Read/write permission
    private val requestRWPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        var readIsGranted = false
        var writeIsGranted = false
        permissions.entries.forEach {
            when(it.key) {
                "android.permission.READ_EXTERNAL_STORAGE" -> readIsGranted = it.value
                else -> writeIsGranted = it.value
            }
        }

        if(!(readIsGranted && writeIsGranted) && !(shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) || shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)))
            permissionFilesPermanentlyDeniedMessage()
        else
            manageSwitchFilesAccess()
    }

    // Read/write permission permanently denied
    private val forceRWPermissions = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        manageSwitchFilesAccess()
    }

    // External storage management permission
    @RequiresApi(Build.VERSION_CODES.R)
    private val requestExternalStoragePermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        manageSwitchFilesAccess()
    }

    // Lan
    /*private val requestLanPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {isGranted: Boolean ->
        if(!isGranted && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_LOCAL_NETWORK))
            permissionLanPermanentlyDeniedMessage()
        else
            manageSwitchLan()
    }

    // Notifications LAN permanently denied
    private val forceLanPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        manageSwitchLan()
    }*/

    // Notifications
    private val requestNotificationsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {isGranted: Boolean ->
        if(!isGranted && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
            permissionNotificationsPermanentlyDeniedMessage()
        else
            manageSwitchNotifications()
    }

    // Notifications permission permanently denied
    private val forceNotificationsPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        manageSwitchNotifications()
    }

    // Battery optimizations
    private val requestBatteryOptimizations = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        manageSwitchBattery()
    }

    // Permanent permission
    @RequiresApi(Build.VERSION_CODES.R)
    private val requestPermanentPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        manageSwitchPermanent()
    }

    // OnBackPressed
    private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { exitActivity() }
    }

    // -------------------------------------

     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         setContentView(R.layout.activity_permissions)

         binding = ActivityPermissionsBinding.inflate(layoutInflater)
         setContentView(binding.root)
         setSupportActionBar(binding.permissionsToolbar)

         reset = intent.getBooleanExtra(RESET, false)

         loadUI()

         // OnBackPressed
         onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun loadUI() {
        setButtonsVisibility()
        setSwitchFilesAccess()
        //setSwitchLan()
        setSwitchNotifications()
        setSwitchBattery()
        setSwitchPermanent()
        setAutoStartup()
    }

    // -------------------------------------

    /** Set cancel/ok buttons visibility **/
    private fun setButtonsVisibility() {
        if(permissions.requiredPermissionsAreGranted()) {
            binding.permissionsBtnOk.visibility = View.VISIBLE
            binding.permissionsBtnCancel.visibility = View.INVISIBLE
            binding.permissionsBtnOk.setOnClickListener { exitActivity() }
        }
        else {
            binding.permissionsBtnOk.visibility = View.INVISIBLE
            binding.permissionsBtnCancel.visibility = View.VISIBLE
            binding.permissionsBtnCancel.setOnClickListener { exitActivity() }
        }
    }

    /** Called when the user quits the activity */
    private fun exitActivity() {
        if(!permissions.requiredPermissionsAreGranted())
            finishAffinity()
        else {
            if(reset)
                startActivity(Intent(this, Activity_Main::class.java))
            finish()
        }
    }

    // -------------------------------------

    /** Set Files access switch **/
    private fun setSwitchFilesAccess() {
        if(permissions.permissionFileAccessIsGranted()) {
            binding.permissionsFilesSwitch.isChecked = true
            binding.permissionsFilesSwitch.isClickable = false
        }
        else binding.permissionsFilesSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    requestExternalStoragePermission.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${BuildConfig.APPLICATION_ID}".toUri()))
                else
                    requestRWPermissions.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }
    }

    /** Message displayed when the user refuse permission twice **/
    private fun permissionFilesPermanentlyDeniedMessage() {
        AlertDialog.Builder(this, R.style.AppTheme_AlertDialogStyle).apply {
            this.setTitle(getString(R.string.permission_locked_title))
            this.setMessage(getString(R.string.permission_files_locked_text))
            this.setPositiveButton(android.R.string.ok) { _, _ -> forceRWPermissions.launch(Intent(ACTION_APPLICATION_DETAILS_SETTINGS, "package:$pkgName".toUri())) }
            this.setNegativeButton(android.R.string.cancel) { _, _ -> manageSwitchFilesAccess() }
            this.show()
        }
    }

    /** Manage Files access switch **/
    private fun manageSwitchFilesAccess() {
        if(permissions.permissionFileAccessIsGranted()) {
            binding.permissionsFilesSwitch.isClickable = false
            setButtonsVisibility()
        }
        else
            binding.permissionsFilesSwitch.isChecked = false
    }

    // -------------------------------------

    /** Set LAN switch **/
    /*private fun setSwitchLan() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN)
            binding.permissionsLanLyt.visibility = View.GONE
        else {
            // Manage switch
            if (permissions.permissionLanIsGranted()) {
                binding.permissionsLanSwitch.isChecked = true
                binding.permissionsLanSwitch.isClickable = false
            } else binding.permissionsLanSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked)
                    requestLanPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }
    }

    /** Message displayed when the user refuse permission twice **/
    private fun permissionLanPermanentlyDeniedMessage() {
        AlertDialog.Builder(this, R.style.AppTheme_AlertDialogStyle).apply {
            this.setTitle(getString(R.string.permission_locked_title))
            this.setMessage(getString(R.string.permission_locked_text))
            this.setPositiveButton(android.R.string.ok) { _, _ -> forceLanPermission.launch(Intent(ACTION_APPLICATION_DETAILS_SETTINGS, "package:$pkgName".toUri())) }
            this.setNegativeButton(android.R.string.cancel) { _, _ -> manageSwitchLan() }
            this.show()
        }
    }

    /** Manage Lan switch **/
    private fun manageSwitchLan() {
        if(permissions.permissionLanIsGranted())
            binding.permissionsLanSwitch.isClickable = false
        else
            binding.permissionsLanSwitch.isChecked = false
    }*/

    // -------------------------------------

    /** Set notifications switch **/
    private fun setSwitchNotifications() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            binding.permissionsNotificationsLyt.visibility = View.GONE
        else {
            // Manage switch
            if (permissions.permissionNotificationsIsGranted()) {
                binding.permissionsNotificationsSwitch.isChecked = true
                binding.permissionsNotificationsSwitch.isClickable = false
            } else binding.permissionsNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked)
                    requestNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /** Message displayed when the user refuse permission twice **/
    private fun permissionNotificationsPermanentlyDeniedMessage() {
        AlertDialog.Builder(this, R.style.AppTheme_AlertDialogStyle).apply {
            this.setTitle(getString(R.string.permission_locked_title))
            this.setMessage(getString(R.string.permission_locked_text))
            this.setPositiveButton(android.R.string.ok) { _, _ -> forceNotificationsPermission.launch(Intent(ACTION_APPLICATION_DETAILS_SETTINGS, "package:$pkgName".toUri())) }
            this.setNegativeButton(android.R.string.cancel) { _, _ -> manageSwitchNotifications() }
            this.show()
        }
    }

    /** Manage notifications switch **/
    private fun manageSwitchNotifications() {
        if(permissions.permissionNotificationsIsGranted())
            binding.permissionsNotificationsSwitch.isClickable = false
        else
            binding.permissionsNotificationsSwitch.isChecked = false
    }

    // -------------------------------------

    /** Set battery optimisations switch **/
    @SuppressLint("BatteryLife")
    private fun setSwitchBattery() {
        if(permissions.batteryOptimizationsAreDisabled()) {
            binding.permissionsBatterySwitch.isChecked = true
            binding.permissionsBatterySwitch.isClickable = false
        }
        else binding.permissionsBatterySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked)
                requestBatteryOptimizations.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$pkgName".toUri()))
        }
    }

    /** Manage battery optimisations switch **/
    private fun manageSwitchBattery() {
        if(permissions.batteryOptimizationsAreDisabled())
            binding.permissionsBatterySwitch.isClickable = false
        else
            binding.permissionsBatterySwitch.isChecked = false
    }

    // -------------------------------------

    /** Set permanent permissions switch **/
    private fun setSwitchPermanent() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            binding.permissionsPermanentLyt.visibility = View.GONE
        else {
            if (permissions.permissionsRevocationIsDisabled()) {
                binding.permissionsPermanentSwitch.isChecked = true
                binding.permissionsPermanentSwitch.isClickable = false
            } else binding.permissionsPermanentSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked)
                    requestPermanentPermission.launch(Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, "package:$pkgName".toUri()))
            }
        }
    }

    /** Manage permanent permission switch **/
    private fun manageSwitchPermanent() {
        if(permissions.permissionsRevocationIsDisabled())
            binding.permissionsPermanentSwitch.isClickable = false
        else
            binding.permissionsPermanentSwitch.isChecked = false
    }

    // -------------------------------------

    /** Set auto-startup permission **/
    private fun setAutoStartup() {
        if(!permissions.autoStartupIsAvailable())
            binding.permissionsStartupLyt.visibility = View.GONE
        else if(!permissions.autoStartupIsManageable()) {
            binding.permissionsStartupTxtTentative.visibility = View.GONE
            binding.permissionsStartupBtnOpen.visibility = View.GONE
            binding.permissionsStartupTxtManual.visibility = View.VISIBLE
        }
        else
            binding.permissionsStartupBtnOpen.setOnClickListener {
                try { AutoStartPermissionHelper.getInstance().getAutoStartPermission(applicationContext, true, true) }
                catch(_: Exception) { manageSwitchBattery() }
            }
    }
}