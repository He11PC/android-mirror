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

package fr.hellpc.mirror

import android.app.Application
import android.content.Context
import android.content.res.Resources
import fr.hellpc.mirror.data.repositories.Repository_Autofill
import fr.hellpc.mirror.managers.Manager_Settings
import fr.hellpc.mirror.data.repositories.Repository_Edit
import fr.hellpc.mirror.data.repositories.Repository_FolderExplorer
import fr.hellpc.mirror.data.repositories.Repository_Logs
import fr.hellpc.mirror.data.repositories.Repository_Main
import fr.hellpc.mirror.data.repositories.Repository_Notifications
import fr.hellpc.mirror.data.repositories.Repository_Worker_Backup
import fr.hellpc.mirror.data.repositories.Repository_Worker_Scheduler
import fr.hellpc.mirror.data.room.Room_Database
import fr.hellpc.mirror.utilities.Utility_Locale.setAppLocale

class App: Application() {

    // Database
    private val database by lazy { Room_Database.getDatabase(this) }
    val repositoryMain by lazy { Repository_Main(database.Dao_TBackupData(), database.Dao_TBackupStatus(),database.Dao_VBackupInfos(), database.Dao_VAllBackupsScheduledStats()) }
    val repositoryEdit by lazy { Repository_Edit(database.Dao_TBackupData(), database.Dao_TBackupStatus(),database.Dao_VBackupInfos(), database.Dao_VBackupTargetCredentials()) }
    val repositoryAutofill by lazy { Repository_Autofill(database.Dao_VBackupTargetCredentials()) }
    val repositoryFolderExplorer by lazy { Repository_FolderExplorer() }
    val repositoryLog by lazy { Repository_Logs() }
    val repositoryWorkerBackup by lazy { Repository_Worker_Backup(database.Dao_TBackupData(), database.Dao_TBackupStatus(), database.Dao_TBackupResult(), database.Dao_TBackupScheduledStats(), database.Dao_VBackupInfos()) }
    val repositoryNotifications by lazy { Repository_Notifications(database.Dao_TBackupScheduledStats(), database.Dao_TBackupResult(), database.Dao_VAllBackupsScheduledStats()) }
    val repositoryWorkerScheduler by lazy { Repository_Worker_Scheduler(database.Dao_TBackupData(), database.Dao_VBackupInfos(), database.Dao_TBackupResult(), database.Dao_TBackupScheduledStats()) }

    companion object {
        lateinit var instance: Application
        lateinit var resources: Resources

        fun getLocaleContext(): Context { return instance.setAppLocale() }
    }

    override fun onCreate() {
        super.onCreate()

        instance = this
        App.resources = resources

        Manager_Settings().applyTheme()
    }
}