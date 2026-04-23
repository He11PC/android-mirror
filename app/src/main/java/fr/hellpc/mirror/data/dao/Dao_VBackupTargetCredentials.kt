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
import fr.hellpc.mirror.data.room.BackupTarget_Credentials

@Dao
interface  Dao_VBackupTargetCredentials {
    @Query("SELECT count(*) > 0 FROM v_backup_target_credentials WHERE protocol = :protocol")
    fun backupTargetCredentialsExist(protocol: String): Boolean

    @Query("SELECT * FROM v_backup_target_credentials WHERE protocol = :protocol ORDER BY id_backup DESC")
    fun getBackupTargetCredentials(protocol: String): List<BackupTarget_Credentials>
}