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

import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DefaultRequestCacheKeyProvider
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.emc.ecs.nfsclient.nfs.io.Nfs3File
import com.emc.ecs.nfsclient.nfs.nfs3.Nfs3
import com.emc.ecs.nfsclient.rpc.CredentialNone
import com.emc.ecs.nfsclient.rpc.CredentialUnix
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.FolderExplorer_File
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.utilities.Utility_Encryption.cipherDecrypt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.util.TrustManagerUtils
import java.util.concurrent.ConcurrentHashMap


class Repository_FolderExplorer {

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO

    private lateinit var target: Backup_Target

    private lateinit var nfs: Nfs3

    private lateinit var smbClient: SMBClient
    private lateinit var smbSession: Session
    private lateinit var smbShare: DiskShare

    private val ftp: FTPClient by lazy { FTPClient() }
    private val ftps: FTPSClient by lazy { FTPSClient() }

    private lateinit var sftpSession: com.jcraft.jsch.Session
    private lateinit var sftpChannel: ChannelSftp

    private lateinit var webdavScheme: String
    private lateinit var webdavServer: String
    private lateinit var httpClient: OkHttpClient
    private lateinit var webdav: Sardine

    private val systemFiles by lazy { arrayOf("", ".", "..", ".DAV", "._DAV") }


    // ------
    // Global
    // ------

    fun initialize(target: Backup_Target) {
        this.target = target
    }

    suspend fun connect(): Boolean = withContext(dispatcherIO) {
        return@withContext when(target.protocol) {
            "NFS" -> connectNfs()
            "SMB" -> connectSmb()
            "FTP" -> {
                if(target.ssl == true)
                    connectFtps()
                else
                    connectFtp()
            }
            "SFTP" -> connectSftp()
            "WDAV" -> connectWebdav()
            else -> throw Exception(App.resources.getString(R.string.error_target))
        }
    }

    suspend fun disconnect(): Boolean = withContext(dispatcherIO) {
        return@withContext when(target.protocol) {
            "SMB" -> disconnectSmb()
            "FTP" -> disconnectFtp()
            "SFTP" -> disconnectSftp()
            "WDAV" -> disconnectWebdav()
            else -> true
        }
    }

    suspend fun loadFolder(path: String): List<FolderExplorer_File> = withContext(dispatcherIO) {
        return@withContext when(target.protocol) {
            "NFS" -> loadNfs(path)
            "SMB" -> loadSmb(path)
            "FTP" -> loadFtp(path)
            "SFTP" -> loadSftp(path)
            "WDAV" -> loadWebdav(path)
            else -> emptyList()
        }.sortedWith(compareBy<FolderExplorer_File> { !it.isDirectory }.thenBy { it.name })
    }


    // ---
    // NFS
    // ---

    private fun connectNfs(): Boolean {
        nfs = if(target.uid == null && target.gid == null)
            Nfs3(target.server?.cipherDecrypt(), target.share, CredentialNone(), 3)
        else
            Nfs3(target.server?.cipherDecrypt(), target.share, CredentialUnix(target.uid?.cipherDecrypt()?.toInt()?:0, target.gid?.cipherDecrypt()?.toInt()?:0, null), 3)

        return Nfs3File(nfs, "/").exists()
    }

    private fun loadNfs(path: String): List<FolderExplorer_File> = Nfs3File(nfs, "/$path")
        .listFiles()
        .filterNot { systemFiles.contains(it.name) }
        .map { FolderExplorer_File(it.name, it.isDirectory) }


    // ---
    // SMB
    // ---

    private fun connectSmb(): Boolean {
        val config = SmbConfig.builder()
            .withMultiProtocolNegotiate(true)
            .withNegotiatedBufferSize()
            .withDfsEnabled(true)
            .withEncryptData(true)
            .withSecurityProvider(BCSecurityProvider())

        if(!target.login.isNullOrBlank())
            config.withSigningRequired(true)

        smbClient = SMBClient(config.build())

        val auth = if(target.login.isNullOrBlank())
            AuthenticationContext.anonymous()
        else
            AuthenticationContext(target.login!!.cipherDecrypt(), (target.password?.cipherDecrypt() ?: "").toCharArray(), target.domain ?: "")

        smbClient.connect(target.server?.cipherDecrypt()).apply {
            smbSession = try { authenticate(auth) }
            catch(exp: Exception) {
                if(!target.login.isNullOrBlank())
                    throw exp
                authenticate(AuthenticationContext.guest())
            }
        }

        check(smbSession.connection.isConnected)
        smbShare = smbSession.connectShare(target.share) as DiskShare

        return smbShare.isConnected
    }

    private fun disconnectSmb(): Boolean {
        if(::smbClient.isInitialized)
            smbClient.close()

        return true
    }

    private fun loadSmb(path: String): List<FolderExplorer_File> = smbShare
        .list(path.toSmbPath(), "*")
        .filterNot { systemFiles.contains(it.fileName) }
        .map { FolderExplorer_File(it.fileName, EnumWithValue.EnumUtils.isSet(it.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)) }

    private fun String.toSmbPath() = this.replace('/', '\\')


    // ---
    // FTP
    // ---

    private fun connectFtp(): Boolean {
        val userName = target.login.takeIf { !it.isNullOrBlank() }?.cipherDecrypt() ?: "anonymous"

        val password = target.password.takeIf { !it.isNullOrBlank() }?.cipherDecrypt()
            ?: if(target.login.isNullOrBlank())
                "password"
            else
                ""

        // ---

        ftp.apply {
            autodetectUTF8 = true
            bufferSize = 16384

            target.port?.let { connect(target.server?.cipherDecrypt(), it) }
                ?: connect(target.server?.cipherDecrypt())

            check(FTPReply.isPositiveCompletion(replyCode))
            enterLocalPassiveMode()
            check(login(userName, password))

            return setFileType(FTP.BINARY_FILE_TYPE)
        }
    }

    private fun connectFtps(): Boolean {
        val userName = target.login.takeIf { !it.isNullOrBlank() }?.cipherDecrypt() ?: "anonymous"

        val password = target.password.takeIf { !it.isNullOrBlank() }?.cipherDecrypt()
            ?: if(target.login.isNullOrBlank())
                "password"
            else
                ""

        // ---

        ftps.apply {
            trustManager = TrustManagerUtils.getValidateServerCertificateTrustManager()
            isEndpointCheckingEnabled = true
            autodetectUTF8 = true
            bufferSize = 16384

            target.port?.let { connect(target.server?.cipherDecrypt(), it) }
                ?: connect(target.server?.cipherDecrypt())

            check(FTPReply.isPositiveCompletion(replyCode))

            execAUTH("TLS")
            enterLocalPassiveMode()
            check(login(userName, password))
            execPBSZ(0)
            execPROT("P")

            return setFileType(FTP.BINARY_FILE_TYPE)
        }
    }

    private fun disconnectFtp(): Boolean {
        val client = if(target.ssl == true)
            ftps
        else
            ftp

        val clientIsConnected = try { client.isConnected } catch(_: Exception) { false }
        if(clientIsConnected) {
            client.logout()
            client.disconnect()
        }

        return true
    }

    private fun loadFtp(path: String): List<FolderExplorer_File> {
        val client = if(target.ssl == true)
            ftps
        else
            ftp

        return client.listFiles("/$path")
            .filterNot { systemFiles.contains(it.name) }
            .map { FolderExplorer_File(it.name, it.isDirectory) }
    }


    // ----
    // SFTP
    // ----

    private fun connectSftp(): Boolean {
        JSch().apply {
            setKnownHosts(target.hostKey?.cipherDecrypt()?.toByteArray()?.inputStream())
            sftpSession = getSession(target.login?.cipherDecrypt(), target.server?.cipherDecrypt(), target.port ?: 22)
        }

        sftpSession.apply {
            setPassword(target.password?.cipherDecrypt()?.toByteArray())
            connect()
            sftpChannel = openChannel("sftp") as ChannelSftp
        }

        sftpChannel.connect()

        return sftpChannel.isConnected
    }

    private fun disconnectSftp(): Boolean {
        if (::sftpChannel.isInitialized && sftpChannel.isConnected)
            sftpChannel.disconnect()
        if (::sftpSession.isInitialized && sftpSession.isConnected)
            sftpSession.disconnect()

        return true
    }

    private fun loadSftp(path: String): List<FolderExplorer_File> = sftpChannel
        .ls("/$path")
        .filterNot { systemFiles.contains(it.filename) }
        .map { FolderExplorer_File(it.filename, it.attrs.isDir) }


    // ------
    // WEBDAV
    // ------

    private fun connectWebdav(): Boolean {
        webdavScheme = if(target.ssl == true) "https" else "http"
        webdavServer = target.server?.cipherDecrypt()!!

        val authCache: Map<String, CachingAuthenticator> = ConcurrentHashMap()
        val credentials = Credentials(target.login?.cipherDecrypt(), target.password?.cipherDecrypt())
        val authenticator = DispatchingAuthenticator.Builder()
            .with("digest", DigestAuthenticator(credentials))
            .with("basic", BasicAuthenticator(credentials))
            .build()

        httpClient = OkHttpClient.Builder()
            .authenticator(CachingAuthenticatorDecorator(authenticator, authCache))
            .addInterceptor(AuthenticationCacheInterceptor(authCache, DefaultRequestCacheKeyProvider()))
            .build()

        webdav = OkHttpSardine(httpClient)

        return true
    }

    private fun disconnectWebdav(): Boolean {
        if (::httpClient.isInitialized && httpClient.connectionPool.connectionCount() > 0)
            httpClient.apply {
                dispatcher.executorService.shutdown()
                connectionPool.evictAll()
                cache?.close()
            }

        return true
    }

    private fun loadWebdav(path: String): List<FolderExplorer_File> = webdav
        .list(path.toURL(), 1)
        .filterNot { it.path == "/$path/" || it.name.startsWith("#") || systemFiles.contains(it.name) }
        .map { FolderExplorer_File(it.name, it.isDirectory) }

    private fun String.toURL(): String {
        return HttpUrl.Builder().apply {
            scheme(webdavScheme)
            host(webdavServer)
            target.port?.let { port(it) }
            if(this@toURL.isNotBlank())
                addPathSegments(this@toURL)
            addPathSegment("")
            build()
        }.toString()
    }
}