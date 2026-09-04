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
import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DefaultRequestCacheKeyProvider
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.JSch
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import fr.hellpc.mirror.data.BackupInfos_Status
import fr.hellpc.mirror.data.dao.Dao_TBackupData
import fr.hellpc.mirror.data.dao.Dao_TBackupStatus
import fr.hellpc.mirror.data.dao.Dao_VBackupInfos
import fr.hellpc.mirror.data.dao.Dao_VBackupTargetCredentials
import fr.hellpc.mirror.data.room.Backup_Colors
import fr.hellpc.mirror.data.room.Backup_Data
import fr.hellpc.mirror.security.Security_FlexibleTrustManager
import fr.hellpc.mirror.utilities.untrustedCertException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.apache.commons.net.ftp.FTPSClient
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

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


    // ---------
    // Host keys
    // ---------

    /** Connect to FTP server to retrieve host key **/
    @WorkerThread
    suspend fun getFtpHostKey(server: String, port: Int): String = withContext(dispatcherIO) {
        var hostKey: String? = null
        val flexibleTrustManager = Security_FlexibleTrustManager(null)

        FTPSClient().apply {
            trustManager = flexibleTrustManager
            isEndpointCheckingEnabled = false
            autodetectUTF8 = true

            try { connect(server, port) }
            catch (exp: Exception) {
                val untrusted = exp.untrustedCertException
                if (untrusted != null)
                    hostKey = untrusted.capturedHostKey
            }
            finally {
                val clientIsConnected = try { isConnected } catch(_: Exception) { false }
                if(clientIsConnected)
                    disconnect()
            }
        }

        require(!hostKey.isNullOrBlank())

        return@withContext hostKey
    }

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
                if(password.isNotBlank())
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

    /** Connect to WebDAV server to retrieve host key **/
    @WorkerThread
    suspend fun getWebdavHostKey(server: String, port: Int, login: String, password: String): String = withContext(dispatcherIO) {
        val serverUrl = HttpUrl.Builder().apply {
            scheme("https")
            host(server)
            port(port)
            build()
        }.toString()

        var hostKey: String? = null
        val flexibleTrustManager = Security_FlexibleTrustManager(null)

        val authCache: Map<String, CachingAuthenticator> = ConcurrentHashMap()
        val credentials = Credentials(login, password)
        val authenticator = DispatchingAuthenticator.Builder()
            .with("digest", DigestAuthenticator(credentials))
            .with("basic", BasicAuthenticator(credentials))
            .build()

        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(flexibleTrustManager), java.security.SecureRandom()) }
        val okHttpClient = OkHttpClient.Builder()
            .authenticator(CachingAuthenticatorDecorator(authenticator, authCache))
            .addInterceptor(AuthenticationCacheInterceptor(authCache, DefaultRequestCacheKeyProvider()))
            .sslSocketFactory(sslContext.socketFactory, flexibleTrustManager as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()

        OkHttpSardine(okHttpClient).apply {
            setCredentials(login, password)

            try { list(serverUrl) }
            catch (exp: Exception) {
                val untrusted = exp.untrustedCertException
                if (untrusted != null)
                    hostKey = untrusted.capturedHostKey
            }
            finally {
                (authCache as? MutableMap)?.clear()

                okHttpClient.apply {
                    dispatcher.cancelAll()
                    connectionPool.evictAll()
                    cache?.close()
                }
            }
        }

        require(hostKey != null)

        return@withContext hostKey
    }

}