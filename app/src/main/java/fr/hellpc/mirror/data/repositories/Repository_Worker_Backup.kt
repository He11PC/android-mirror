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
import fr.hellpc.mirror.data.dao.*
import fr.hellpc.mirror.data.room.Backup_Data
import fr.hellpc.mirror.data.room.BackupScheduled_Stats
import fr.hellpc.mirror.data.BackupInfos_Failed
import fr.hellpc.mirror.data.BackupScheduled_Options

class Repository_Worker_Backup(private val daoTBackupData: Dao_TBackupData, private val daoTBackupStatus: Dao_TBackupStatus, private val daoTBackupResult: Dao_TBackupResult, private val daoTBackupScheduledStats: Dao_TBackupScheduledStats, private val daoVBackupInfos: Dao_VBackupInfos) {

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO

    @WorkerThread
    suspend fun loadBackup(id_backup: Int): Backup_Data {
        return withContext(dispatcherIO) { daoTBackupData.getBackupFromId(id_backup) }
    }

    @WorkerThread
    suspend fun setDateComparisonMode(id: Int, strictDate: Boolean) = withContext(dispatcherIO) {
        daoTBackupData.setDateComparisonMode(id, strictDate)
    }

    @WorkerThread
    suspend fun getScheduledBackupOptions(id: Int): BackupScheduled_Options {
        return withContext(dispatcherIO) { daoTBackupData.getScheduledBackupOptions(id) }
    }

    // -------------------------------------

    @WorkerThread
    suspend fun registerBackupWorker(id_backup: Int, uuid: String?, isScheduler: Boolean) = withContext(dispatcherIO) {
        if(isScheduler)
            daoTBackupStatus.setBackupIdScheduler(id_backup, uuid)
        else
            daoTBackupStatus.setBackupIdWorker(id_backup, uuid)
    }

    @WorkerThread
    suspend fun resetBackupStatus(id_backup: Int, status: String) = withContext(dispatcherIO) {
        daoTBackupStatus.resetBackupStatus(id_backup, status)
    }

    @WorkerThread
    suspend fun setBackupStatus(id_backup: Int, status: String) = withContext(dispatcherIO) {
        daoTBackupStatus.setBackupStatus(id_backup, status)
    }

    @WorkerThread
    suspend fun setBackupInitialise(id_backup: Int, status: String, progressDetail1: String?, progressDetail2: String?) {
        daoTBackupStatus.setBackupProgressAndStatusStart(id_backup, status, progressDetail1, progressDetail2)
    }

    @WorkerThread
    suspend fun setBackupProgress(id_backup: Int, progressConfirmed: Int, progressCurrent: Int) = withContext(dispatcherIO) {
        daoTBackupStatus.setBackupProgress(id_backup, progressConfirmed, progressCurrent)
    }

    @WorkerThread
    suspend fun setBackupProgressDetail1(id_backup: Int, detail: String?) = withContext(dispatcherIO) {
        daoTBackupStatus.setBackupProgressDetail1(id_backup, detail)
    }

    @WorkerThread
    suspend fun setBackupProgressDetail2(id_backup: Int, detail: String?) = withContext(dispatcherIO) {
        daoTBackupStatus.setBackupProgressDetail2(id_backup, detail)
    }

    @WorkerThread
    suspend fun setBackupProgressDetail3(id_backup: Int, detail: String?) = withContext(dispatcherIO) {
        daoTBackupStatus.setBackupProgressionDetail3(id_backup, detail)
    }

    // -------------------------------------

    @WorkerThread
    suspend fun setBackupResult(id_backup: Int, status: String, result: String, numFiles: Int, sizeFiles: Long, numOrphans: Int, sizeOrphans: Long) = withContext(dispatcherIO) {
        daoTBackupStatus.setBackupProgressAndStatusEnd(id_backup, status)
        daoTBackupResult.setBackupResult(id_backup, result, numFiles, sizeFiles, numOrphans, sizeOrphans)
    }

    @WorkerThread
    suspend fun setBackupResult(id_backup: Int, status: String, result: String, date: Long, numFiles: Int, sizeFiles: Long, numOrphans: Int, sizeOrphans: Long) = withContext(dispatcherIO) {
        daoTBackupStatus.setBackupProgressAndStatusEnd(id_backup, status)
        daoTBackupResult.setBackupResult(id_backup, result, date, numFiles, sizeFiles, numOrphans, sizeOrphans)
    }

    @WorkerThread
    suspend fun getLastBackupDate(id_backup: Int): Long {
        return withContext(dispatcherIO) { daoTBackupResult.getLastBackupDate(id_backup) }
    }

    // -------------------------------------

    @WorkerThread
    suspend fun getScheduledBackupStats(id_backup: Int): BackupScheduled_Stats {
        return withContext(dispatcherIO) { daoTBackupScheduledStats.getScheduledBackupStats(id_backup) }
    }

    @WorkerThread
    suspend fun setScheduledBackupActive(id_backup: Int) = withContext(dispatcherIO) {
        daoTBackupScheduledStats.setScheduledBackupActive(id_backup)
    }

    @WorkerThread
    suspend fun setScheduledBackupDailyStats(id_backup: Int, numFiles: Int, sizeFiles: Long, numOrphans: Int, sizeOrphans: Long, success: Int, warning: Int, failed: Int, canceled: Int) = withContext(dispatcherIO) {
        daoTBackupScheduledStats.setScheduledBackupDailyStats(id_backup, numFiles, sizeFiles, numOrphans, sizeOrphans, success, warning, failed, canceled)
    }

    // -------------------------------------

    @WorkerThread
    suspend fun getFailedWorkerInfo(id_backup: Int): BackupInfos_Failed {
        return withContext(dispatcherIO) { daoVBackupInfos.getFailedWorkerInfo(id_backup) }
    }
}