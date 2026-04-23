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
import fr.hellpc.mirror.data.dao.Dao_VBackupTargetCredentials
import fr.hellpc.mirror.data.room.BackupTarget_Credentials
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Repository_Autofill(private val daoVBackupsTargetCredentials: Dao_VBackupTargetCredentials) {

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO

    @WorkerThread
    suspend fun getBackupTargetCredentials(protocol: String): List<BackupTarget_Credentials> {
        return withContext(dispatcherIO) { daoVBackupsTargetCredentials.getBackupTargetCredentials(protocol) }
    }
}