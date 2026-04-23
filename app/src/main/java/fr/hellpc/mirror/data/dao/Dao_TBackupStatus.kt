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

package fr.hellpc.mirror.data.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface Dao_TBackupStatus {

    @Query("UPDATE t_backup_status SET id_worker = null, status = :status WHERE id_backup == :id_backup")
    suspend fun resetBackupStatus(id_backup: Int, status: String)

    @Query("UPDATE t_backup_status SET status = :status WHERE id_backup == :id_backup")
    suspend fun setBackupStatus(id_backup: Int, status: String)

    @Query("UPDATE t_backup_status SET id_worker = :id_worker, status='queue' WHERE id_backup == :id_backup")
    suspend fun setBackupIdWorker(id_backup: Int, id_worker: String?)

    @Query("UPDATE t_backup_status SET id_scheduler = :id_scheduler WHERE id_backup == :id_backup")
    suspend fun setBackupIdScheduler(id_backup: Int, id_scheduler: String?)

    @Query("UPDATE t_backup_status SET progress_confirmed = :progressConfirmed, progress_current = :progressCurrent WHERE id_backup == :id_backup")
    suspend fun setBackupProgress(id_backup: Int, progressConfirmed: Int, progressCurrent: Int)

    @Query("UPDATE t_backup_status SET progress_detail_1 = :detail WHERE id_backup == :id_backup")
    suspend fun setBackupProgressDetail1(id_backup: Int, detail: String?)

    @Query("UPDATE t_backup_status SET progress_detail_2 = :detail WHERE id_backup == :id_backup")
    suspend fun setBackupProgressDetail2(id_backup: Int, detail: String?)

    @Query("UPDATE t_backup_status SET progress_detail_3 = :detail WHERE id_backup == :id_backup")
    suspend fun setBackupProgressionDetail3(id_backup: Int, detail: String?)

    @Query("UPDATE t_backup_status SET status = :status, progress_confirmed = 0, progress_current = 0, progress_detail_1 = :progressDetail1, progress_detail_2 = :progressDetail2, progress_detail_3 = null WHERE id_backup == :id_backup")
    suspend fun setBackupProgressAndStatusStart(id_backup: Int, status: String, progressDetail1: String?, progressDetail2: String?)

    @Query("UPDATE t_backup_status SET status = :status, progress_confirmed = -1, progress_current = -1, progress_detail_1 = null, progress_detail_2 = null, progress_detail_3 = null, id_worker = null WHERE id_backup == :id_backup")
    suspend fun setBackupProgressAndStatusEnd(id_backup: Int, status: String)

    @Query("UPDATE t_backup_status SET status = 'idle' WHERE id_worker is NULL AND status != 'idle'")
    suspend fun resetStuckedStatus()

}