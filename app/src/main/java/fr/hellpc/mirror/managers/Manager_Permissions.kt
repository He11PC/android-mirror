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

package fr.hellpc.mirror.managers

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.judemanutd.autostarter.AutoStartPermissionHelper
import fr.hellpc.mirror.App

class Manager_Permissions {

    /** Check required permissions **/
    fun requiredPermissionsAreGranted(): Boolean {
        return permissionFileAccessIsGranted()
    }

    /** Check files access permissions **/
    fun permissionFileAccessIsGranted(): Boolean {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else {
            val read = ContextCompat.checkSelfPermission(App.instance, Manifest.permission.READ_EXTERNAL_STORAGE)
            val write = ContextCompat.checkSelfPermission(App.instance, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Check LAN access (above Android 17) **/
    /*fun permissionLanIsGranted():Boolean {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            val lan = ContextCompat.checkSelfPermission(App.instance, Manifest.permission.ACCESS_LOCAL_NETWORK)
            lan == PackageManager.PERMISSION_GRANTED
        }
        else
            true
    }*/

    /** Check Notifications (above Tiramisu) **/
    fun permissionNotificationsIsGranted():Boolean {
        val notif = App.instance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notif.areNotificationsEnabled()
    }

    /** Check battery optimisation **/
    fun batteryOptimizationsAreDisabled(): Boolean {
        val pwrm = App.instance.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pwrm.isIgnoringBatteryOptimizations(App.instance.packageName)
    }

    /**  Check permanent permissions for android above R  **/
    fun permissionsRevocationIsDisabled(): Boolean {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            App.instance.packageManager.isAutoRevokeWhitelisted
        else
            true
    }

    /** Check auto-startup availability **/
    fun autoStartupIsAvailable(): Boolean {
        return AutoStartPermissionHelper.getInstance().isAutoStartPermissionAvailable(App.instance, false)
    }

    /** Check if it is possible to open auto-startup settings **/
    fun autoStartupIsManageable(): Boolean {
        return AutoStartPermissionHelper.getInstance().isAutoStartPermissionAvailable(App.instance, true)
    }
}