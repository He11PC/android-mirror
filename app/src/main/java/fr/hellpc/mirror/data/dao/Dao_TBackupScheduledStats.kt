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
import fr.hellpc.mirror.data.room.BackupScheduled_Stats

@Dao
interface Dao_TBackupScheduledStats {

    @Query("SELECT * FROM t_backup_scheduled_stats WHERE id_backup = :id_backup")
    suspend fun getScheduledBackupStats(id_backup: Int): BackupScheduled_Stats

    // -------------------------------------

    @Query("UPDATE t_backup_scheduled_stats SET active = 1 WHERE id_backup == :id_backup")
    suspend fun setScheduledBackupActive(id_backup: Int)

    @Query("UPDATE t_backup_scheduled_stats SET active = 0, files_count = :numFiles, files_size = :sizeFiles, orphans_count = :numOrphans, orphans_size = :sizeOrphans, success = :success, warning = :warning, failed = :failed, canceled = :canceled WHERE id_backup = :id_backup")
    suspend fun setScheduledBackupDailyStats(id_backup: Int, numFiles: Int, sizeFiles: Long, numOrphans: Int, sizeOrphans: Long, success: Int, warning: Int, failed: Int, canceled: Int)

    @Query("UPDATE t_backup_scheduled_stats SET canceled = :canceled WHERE id_backup = :id_backup")
    suspend fun setScheduledBackupCanceledDailyStats(id_backup: Int, canceled: Int)

    @Query("UPDATE t_backup_scheduled_stats SET files_count = 0, files_size = 0, orphans_count = 0, orphans_size = 0, success = 0, warning = 0, failed = 0, canceled = 0")
    suspend fun resetScheduledBackupDailyStats()
}