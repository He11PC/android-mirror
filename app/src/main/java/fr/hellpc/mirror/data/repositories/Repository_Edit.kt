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
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.JSch
import fr.hellpc.mirror.data.BackupInfos_Status
import fr.hellpc.mirror.data.dao.Dao_TBackupData
import fr.hellpc.mirror.data.dao.Dao_TBackupStatus
import fr.hellpc.mirror.data.dao.Dao_VBackupInfos
import fr.hellpc.mirror.data.dao.Dao_VBackupTargetCredentials
import fr.hellpc.mirror.data.room.Backup_Colors
import fr.hellpc.mirror.data.room.Backup_Data
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

class Repository_Edit(private val daoTBackupData: Dao_TBackupData, private val daoTBackupStatus: Dao_TBackupStatus, private val daoVBackupsInfos: Dao_VBackupInfos, private val daoVBackupsTargetCredentials: Dao_VBackupTargetCredentials) {

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO

    @WorkerThread
    suspend fun backupIsProtected(id_backup: Int): Boolean {
        return withContext(dispatcherIO) { daoTBackupData.backupIsProtected(id_backup) }
    }

    @WorkerThread
    suspend fun loadBackup(id_backup: Int): Backup_Data {
        return withContext(dispatcherIO) { daoTBackupData.getBackupFromId(id_backup) }
    }

    @WorkerThread
    suspend fun loadStatus(id_backup: Int): BackupInfos_Status? {
        return withContext(dispatcherIO) { daoVBackupsInfos.getBackupStatus(id_backup) }
    }

    @WorkerThread
    suspend fun insertBackup(backup: Backup_Data): Long {
        return withContext(dispatcherIO) { daoTBackupData.insertBackup(backup) }
    }

    @WorkerThread
    suspend fun getBackupId(rowid: Long): Int {
        return withContext(dispatcherIO) { daoTBackupData.getBackupId(rowid) }
    }

    @WorkerThread
    suspend fun updateBackup(backup: Backup_Data) = withContext(dispatcherIO) {
        daoTBackupData.updateBackup(backup)
    }

    @WorkerThread
    suspend fun getBackupMaxPosition(): Int? {
        return withContext(dispatcherIO) { daoTBackupData.getBackupMaxPosition() }
    }

    @WorkerThread
    suspend fun getUserThemes(): List<Backup_Colors> {
        return withContext(dispatcherIO) { daoTBackupData.getUserThemes() }
    }

    @WorkerThread
    suspend fun updateStatus(id_backup: Int, status: String) = withContext(dispatcherIO) {
        daoTBackupStatus.setBackupStatus(id_backup, status)
    }

    @WorkerThread
    suspend fun backupTargetCredentialsExist(protocol: String): Boolean  {
        return withContext(dispatcherIO) { daoVBackupsTargetCredentials.backupTargetCredentialsExist(protocol) }
    }


    // -------------
    // SFTP host key
    // -------------

    /** Connect to SFTP server to retrieve host key **/
    @WorkerThread
    suspend fun getSftpHostKey(server: String, port: Int, login: String, password: String): HostKey = withContext(dispatcherIO) {
        val hostKey: HostKey?

        val session = JSch().getSession(login, server, port)

        try {
            val config = Properties()
            config["StrictHostKeyChecking"] = "no"

            session.apply {
                setConfig(config)
                setPassword(password.toByteArray())
                timeout = 30*1000
                connect()
            }

            hostKey = session.hostKey
        }
        finally {
            if(session.isConnected)
                session.disconnect()
        }

        require(hostKey != null)

        return@withContext hostKey
    }

}