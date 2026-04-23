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

package fr.hellpc.mirror.data.repositories

import androidx.annotation.WorkerThread
import fr.hellpc.mirror.data.BackupInfos_Idle
import fr.hellpc.mirror.data.BackupInfos_Running
import fr.hellpc.mirror.data.BackupsInfos_StatusCount
import fr.hellpc.mirror.data.dao.Dao_TBackupData
import fr.hellpc.mirror.data.dao.Dao_TBackupStatus
import fr.hellpc.mirror.data.dao.Dao_VAllBackupsScheduledStats
import fr.hellpc.mirror.data.dao.Dao_VBackupInfos
import fr.hellpc.mirror.data.room.Backup_Colors
import fr.hellpc.mirror.data.room.Backup_Data
import fr.hellpc.mirror.data.room.Backup_Infos
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class Repository_Main(private val daoTBackupData: Dao_TBackupData, private val daoTBackupStatus: Dao_TBackupStatus, private val daoVBackupInfos: Dao_VBackupInfos, daoVAllBackupsScheduledStats: Dao_VAllBackupsScheduledStats) {

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO

    val backupsCondensed: Flow<List<Backup_Infos>> = daoVBackupInfos.getBackupsCondensed()
    val backupsStatusCount: Flow<BackupsInfos_StatusCount> = daoVBackupInfos.getBackupsStatusCount()
    val backupsScheduledCount: Flow<Int> = daoVAllBackupsScheduledStats.getBackupsScheduledFlow()
    val backupColorThemes: Flow<List<Backup_Colors>> = daoVBackupInfos.getBackupColorThemes()

    // -------------------------------------

    @WorkerThread
    suspend fun resetStuckedStatus() = withContext(dispatcherIO) {
        daoTBackupStatus.resetStuckedStatus()
    }

    @WorkerThread
    suspend fun loadBackup(id_backup: Int): Backup_Data {
        return withContext(dispatcherIO) { daoTBackupData.getBackupFromId(id_backup) }
    }

    @WorkerThread
    suspend fun deleteBackup(id_backup: Int, position: Int) = withContext(dispatcherIO) {
        daoTBackupData.deleteBackup(id_backup)
        daoTBackupData.reorderPositions(position)
    }

    @WorkerThread
    suspend fun disableBackup(id_backup: Int) = withContext(dispatcherIO) {
        daoTBackupData.disableBackup(id_backup)
    }

    @WorkerThread
    suspend fun enableBackup(id_backup: Int) = withContext(dispatcherIO) {
        daoTBackupData.enableBackup(id_backup)
    }

    // -------------------------------------

    @WorkerThread
    suspend fun updateBackupPositions(oldPosition: Int, newPosition: Int) = withContext(dispatcherIO) {
        if(oldPosition > newPosition)
            daoTBackupData.moveBackupUp(oldPosition, newPosition)
        else
            daoTBackupData.moveBackupDown(oldPosition, newPosition)
    }

    // -------------------------------------

    @WorkerThread
    suspend fun getBackupsIdleWithTheme(theme: Backup_Colors): List<BackupInfos_Idle> {
        return withContext(dispatcherIO) { daoVBackupInfos.getBackupsIdleWithTheme(theme.background, theme.borders, theme.icons, theme.progressbar) }
    }

    @WorkerThread
    suspend fun getBackupsIdle(): List<BackupInfos_Idle> {
        return withContext(dispatcherIO) { daoVBackupInfos.getBackupsIdle() }
    }

    @WorkerThread
    suspend fun getBackupsRunning(): List<BackupInfos_Running> {
        return withContext(dispatcherIO) { daoVBackupInfos.getBackupsRunning() }
    }
}