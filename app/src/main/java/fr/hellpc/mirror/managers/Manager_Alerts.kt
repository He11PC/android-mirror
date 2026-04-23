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

import android.content.Context
import fr.hellpc.mirror.App
import androidx.core.content.edit

class Manager_Alerts {
    private val prefAlerts by lazy { App.instance.getSharedPreferences("ALERTS", Context.MODE_PRIVATE) }


    // --------------------------------
    // Backup between 2 remote machines
    // --------------------------------

    /** Should display alert in case of backup between 2 remote servers ? **/
    fun showRemoteBackupAlert(): Boolean = prefAlerts.getBoolean("SERVER_TO_SERVER", true)

    /** Disable alert for backup between 2 remote servers **/
    fun disableRemoteBackupAlert() = prefAlerts.edit { putBoolean("SERVER_TO_SERVER", false) }


    // ------------------------------------------------
    // [Android 10] Move destination to Mirror folder
    // ------------------------------------------------

    /** [Android 10 only] Should display alert to move Destination folder to Mirror private folder on external storage ? **/
    fun showAndroid10ExternalFolderAlert(): Boolean = prefAlerts.getBoolean("DESTINATION_PATH_MOVE", true)

    /** [Android 10 only] Disable alert to move Destination folder to Mirror private folder on external storage **/
    fun disableAndroid10ExternalFolderAlert() = prefAlerts.edit { putBoolean("DESTINATION_PATH_MOVE", false) }


    // ---------------------
    // Tips on main activity
    // ---------------------

    /** Should display tip on how to switch between edition and backup mode ? **/
    fun showEditModeTip(): Boolean = prefAlerts.getBoolean("EDIT_MODE_TIP", true)

    /** Disable tip on how to switch between edition and backup mode **/
    fun disableEditModeTip()  = prefAlerts.edit { putBoolean("EDIT_MODE_TIP", false) }

    /** Should display tip on how to launch backups groups ? **/
    fun showGroupLaunchTip(): Boolean = prefAlerts.getBoolean("GROUP_LAUNCH_TIP", true)

    /** Disable tip on how to launch backups groups **/
    fun disableGroupLaunchTip()  = prefAlerts.edit { putBoolean("GROUP_LAUNCH_TIP", false) }
}