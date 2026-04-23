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
import fr.hellpc.mirror.data.room.Backup_Colors
import kotlinx.coroutines.flow.Flow
import fr.hellpc.mirror.data.room.Backup_Infos
import fr.hellpc.mirror.data.BackupInfos_Failed
import fr.hellpc.mirror.data.BackupInfos_Status
import fr.hellpc.mirror.data.BackupInfos_Idle
import fr.hellpc.mirror.data.BackupInfos_Running
import fr.hellpc.mirror.data.BackupsInfos_StatusCount

@Dao
interface Dao_VBackupInfos {
    @Query("SELECT * FROM v_backup_infos ORDER BY position")
    fun getBackupsCondensed(): Flow<List<Backup_Infos>>

    @Query("SELECT id_backup, const_wifi FROM v_backup_infos WHERE status = 'idle' AND isDisabled = '0'")
    suspend fun getBackupsIdle(): List<BackupInfos_Idle>

    @Query("SELECT id_backup, const_wifi FROM v_backup_infos WHERE status = 'idle' AND isDisabled = '0' AND color_background = :colorBackground AND color_borders = :colorBorders AND color_icons = :colorIcons AND color_progressbar = :colorProgressbar")
    suspend fun getBackupsIdleWithTheme(colorBackground: Int, colorBorders: Int, colorIcons: Int, colorProgressbar: Int): List<BackupInfos_Idle>

    @Query("SELECT id_backup, id_worker FROM v_backup_infos WHERE status != 'idle' AND status != 'edit'")
    suspend fun getBackupsRunning(): List<BackupInfos_Running>

    @Query("SELECT (SELECT COUNT(id_backup) FROM v_backup_infos WHERE status = 'idle' OR status = 'edit') AS 'idle', (SELECT COUNT(id_backup) FROM v_backup_infos WHERE status != 'idle' AND status != 'edit') AS 'running', (SELECT COUNT(id_backup) FROM v_backup_infos WHERE isDisabled = '1') AS 'disabled'")
    fun getBackupsStatusCount(): Flow<BackupsInfos_StatusCount>

    @Query("SELECT isDisabled, status, id_worker, id_scheduler, last_date FROM v_backup_infos WHERE id_backup == :id_backup")
    suspend fun getBackupStatus(id_backup: Int): BackupInfos_Status?

    @Query("SELECT id_backup, id_worker, status, schedule, id_scheduler, last_date FROM v_backup_infos WHERE id_backup == :id_backup")
    suspend fun getFailedWorkerInfo(id_backup: Int): BackupInfos_Failed

    @Query("SELECT DISTINCT color_background AS background, color_borders AS borders, color_icons AS icons, color_progressbar AS progressbar FROM v_backup_infos ORDER BY position")
    fun getBackupColorThemes(): Flow<List<Backup_Colors>>
}