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
import fr.hellpc.mirror.data.room.Backup_Result

@Dao
interface Dao_TBackupResult {

    @Query("SELECT date FROM t_backup_result WHERE id_backup == :id_backup")
    suspend fun getLastBackupDate(id_backup: Int): Long

    @Query("SELECT * FROM t_backup_result WHERE id_backup == :id_backup")
    suspend fun getLastBackupData(id_backup: Int): Backup_Result

    // -------------------------------------

    @Query("UPDATE t_backup_result SET result = :result, files_count = :numFiles, files_size = :sizeFiles, orphans_count = :numOrphans, orphans_size = :sizeOrphans WHERE id_backup == :id_backup")
    suspend fun setBackupResult(id_backup: Int, result: String, numFiles: Int, sizeFiles: Long, numOrphans: Int, sizeOrphans: Long)

    @Query("UPDATE t_backup_result SET result = :result, date = :date, files_count = :numFiles, files_size = :sizeFiles, orphans_count = :numOrphans, orphans_size = :sizeOrphans WHERE id_backup == :id_backup")
    suspend fun setBackupResult(id_backup: Int, result: String, date: Long, numFiles: Int, sizeFiles: Long, numOrphans: Int, sizeOrphans: Long)

    @Query("UPDATE t_backup_result SET result = 'cancel' WHERE id_backup == :id_backup")
    suspend fun setBackupCancelled(id_backup: Int)
}