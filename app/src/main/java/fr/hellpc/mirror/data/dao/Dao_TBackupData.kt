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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import fr.hellpc.mirror.data.room.Backup_Colors
import fr.hellpc.mirror.data.room.Backup_Data
import fr.hellpc.mirror.data.BackupScheduled_Options

@Dao
interface Dao_TBackupData {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: Backup_Data): Long

    @Update
    suspend fun updateBackup(backup: Backup_Data)

    @Query("DELETE FROM t_backup_data WHERE id == :id")
    suspend fun deleteBackup(id: Int)

    @Query("UPDATE t_backup_data SET isDisabled = '1' WHERE id == :id")
    suspend fun disableBackup(id: Int)

    @Query("UPDATE t_backup_data SET isDisabled = '0' WHERE id == :id")
    suspend fun enableBackup(id: Int)

    // -------------------------------------

    @Query("SELECT opt_protectEdition FROM t_backup_data WHERE id == :id")
    suspend fun backupIsProtected(id: Int): Boolean

    @Query("SELECT id FROM t_backup_data WHERE rowid == :rowid")
    suspend fun getBackupId(rowid: Long): Int

    @Query("SELECT * FROM t_backup_data WHERE id == :id")
    suspend fun getBackupFromId(id: Int): Backup_Data

    @Query("SELECT opt_schedule AS schedule, opt_interval AS interval, opt_interval_unit AS interval_unit, opt_hour AS hour, opt_minute AS minute, opt_const_wifi AS const_wifi, opt_const_charging AS const_charging, opt_const_idle AS const_idle FROM t_backup_data WHERE id = :id")
    suspend fun getScheduledBackupOptions(id: Int): BackupScheduled_Options

    // -------------------------------------

    @Query("SELECT MAX(position) FROM t_backup_data")
    suspend fun getBackupMaxPosition(): Int?

    @Query("UPDATE t_backup_data SET position = CASE WHEN position = :oldPosition THEN :newPosition ELSE (position+1) END WHERE position BETWEEN :newPosition AND :oldPosition")
    suspend fun moveBackupUp(oldPosition: Int, newPosition: Int)

    @Query("UPDATE t_backup_data SET position = CASE WHEN position = :oldPosition THEN :newPosition ELSE (position-1) END WHERE position BETWEEN :oldPosition AND :newPosition")
    suspend fun moveBackupDown(oldPosition: Int, newPosition: Int)

    @Query("UPDATE t_backup_data SET position = (position-1) WHERE position > :removedBackupPosition")
    suspend fun reorderPositions(removedBackupPosition: Int)

    // -------------------------------------

    @Query("SELECT DISTINCT color_background AS background, color_borders AS borders, color_icons AS icons, color_progressbar AS progressbar FROM t_backup_data ORDER BY position")
    suspend fun getUserThemes(): List<Backup_Colors>

    // -------------------------------------

    @Query("UPDATE t_backup_data SET opt_dateComparison_Strict = :strictDate WHERE id == :id")
    suspend fun setDateComparisonMode(id: Int, strictDate: Boolean)

}