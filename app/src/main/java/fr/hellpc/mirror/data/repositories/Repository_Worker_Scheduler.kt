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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import fr.hellpc.mirror.data.dao.Dao_TBackupData
import fr.hellpc.mirror.data.dao.Dao_TBackupScheduledStats
import fr.hellpc.mirror.data.dao.Dao_TBackupResult
import fr.hellpc.mirror.data.dao.Dao_VBackupInfos
import fr.hellpc.mirror.data.room.Backup_Data
import fr.hellpc.mirror.data.BackupInfos_Status
import fr.hellpc.mirror.data.room.BackupScheduled_Stats

class Repository_Worker_Scheduler(private val daoTBackupData: Dao_TBackupData, private val daoVBackupInfos: Dao_VBackupInfos, private val daoTBackupResult: Dao_TBackupResult, private val daoTBackupScheduledStats: Dao_TBackupScheduledStats) {

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO

    @WorkerThread
    suspend fun loadBackup(id_backup: Int): Backup_Data {
        return withContext(dispatcherIO) { daoTBackupData.getBackupFromId(id_backup) }
    }

    // -------------------------------------

    @WorkerThread
    suspend fun loadStatus(id_backup: Int): BackupInfos_Status? {
        return withContext(dispatcherIO) { daoVBackupInfos.getBackupStatus(id_backup) }
    }

    // -------------------------------------

    @WorkerThread
    suspend fun setBackupCanceled(id_backup: Int) = withContext(dispatcherIO) {
        daoTBackupResult.setBackupCancelled(id_backup)
    }

    // -------------------------------------

    @WorkerThread
    suspend fun getScheduledBackupStats(id_backup: Int): BackupScheduled_Stats {
        return withContext(dispatcherIO) { daoTBackupScheduledStats.getScheduledBackupStats(id_backup) }
    }

    @WorkerThread
    suspend fun setScheduledBackupCanceledDailyStats(id_backup: Int, canceled: Int) = withContext(dispatcherIO) {
        daoTBackupScheduledStats.setScheduledBackupCanceledDailyStats(id_backup, canceled)
    }
}