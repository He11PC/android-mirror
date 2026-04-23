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
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import fr.hellpc.mirror.data.dao.Dao_TBackupScheduledStats
import fr.hellpc.mirror.data.dao.Dao_TBackupResult
import fr.hellpc.mirror.data.dao.Dao_VAllBackupsScheduledStats
import fr.hellpc.mirror.data.room.Backup_Result
import fr.hellpc.mirror.data.room.AllBackupsScheduled_Stats

class Repository_Notifications(private val daoTBackupScheduledStats: Dao_TBackupScheduledStats, private val daoTBackupResult: Dao_TBackupResult, private val daoVAllBackupsScheduledStats: Dao_VAllBackupsScheduledStats) {

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO

    val dailyStats: LiveData<AllBackupsScheduled_Stats> = daoVAllBackupsScheduledStats.getDailyStatsFlow().asLiveData()

    @WorkerThread
    suspend fun getDailyStats(): AllBackupsScheduled_Stats {
        return withContext(dispatcherIO) { daoVAllBackupsScheduledStats.getDailyStats() }
    }

    @WorkerThread
    suspend fun getLastBackupData(id_backup: Int): Backup_Result {
        return withContext(dispatcherIO) { daoTBackupResult.getLastBackupData(id_backup) }
    }

    @WorkerThread
    suspend fun getBackupsScheduledCount(): Int {
        return withContext(dispatcherIO) { daoVAllBackupsScheduledStats.getBackupsScheduledCount() }
    }

    @WorkerThread
    suspend fun resetDailyStats() = withContext(dispatcherIO) {
        daoTBackupScheduledStats.resetScheduledBackupDailyStats()
    }
}