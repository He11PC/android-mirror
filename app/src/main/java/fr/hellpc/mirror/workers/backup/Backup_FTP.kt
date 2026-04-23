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

package fr.hellpc.mirror.workers.backup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.managers.Manager_Log
import fr.hellpc.mirror.managers.Manager_Workers
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.data.WorkerBackup_File
import fr.hellpc.mirror.utilities.CriticalException
import fr.hellpc.mirror.data.WorkerBackup_TransferResult
import fr.hellpc.mirror.utilities.Utility_Encryption.cipherDecrypt
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.io.CopyStreamAdapter
import org.apache.commons.net.util.TrustManagerUtils
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.properties.Delegates


class Backup_FTP(
    private val backupID: Int,
    private val isSource: Boolean,
    private val originTxt: String,
    private val target : Backup_Target,
    private val accurateDateAuto: Boolean,
    private var accurateDate: Boolean,
    private val log: Manager_Log
): Backup_Interface {

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO

    private val localeContext = App.getLocaleContext()

    private val ftp1: FTPClient by lazy { FTPClient() }
    private val ftp2: FTPClient by lazy { FTPClient() }
    private val ftp3: FTPClient by lazy { FTPClient() }
    private val ftp4: FTPClient by lazy { FTPClient() }
    private val ftp5: FTPClient by lazy { FTPClient() }

    private val ftps1: FTPSClient by lazy { FTPSClient() }
    private val ftps2: FTPSClient by lazy { FTPSClient() }
    private val ftps3: FTPSClient by lazy { FTPSClient() }
    private val ftps4: FTPSClient by lazy { FTPSClient() }
    private val ftps5: FTPSClient by lazy { FTPSClient() }

    private var ssl by Delegates.notNull<Boolean>()

    private var warningTriggered = false


    // --------------
    // Initialisation
    // --------------

    /** Check server availability **/
    override suspend fun checkServer() = withContext(dispatcherIO) {
        if(target.server.isNullOrBlank())
            throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), null)

        if(target.ssl == true) {
            ssl = true
            makeConnexionSSL(1)
        }
        else {
            ssl = false
            makeConnexion(1)
        }
    }

    /** Check folder availability **/
    override suspend fun checkRootFolder() = withContext(dispatcherIO) {
        val client = if(ssl)
            ftps1
        else
            ftp1

        val rootPath = getAbsolutePath(null, target.path, true)

        // Check if root folder exists
        try {
            if(!client.changeWorkingDirectory(rootPath)) {
                // Create missing folder only for Destination
                if(target.path.isBlank() || isSource)
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_existence), null)

                generateWarning(localeContext.getString(R.string.log_folder_destination_create), false, false)
                createDirectory(rootPath, client)

                if(!client.changeWorkingDirectory(rootPath))
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_existence), null)
            }
        }
        catch(exp: CriticalException) { throw exp }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_invalid), exp.message) }

        checkSSL()
        checkPermissions(client, rootPath)
    }

    /** Check if SSL is usable **/
    private fun checkSSL() {
        if(ssl) {
            try { ftps1.listFiles() }
            catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list), exp.message) }
            val resp = ftps1.replyString
            if ("425" in resp)
                throw CriticalException(originTxt, localeContext.getString(R.string.error_ssl_connect), null)
        }
    }

    /** Check permissions **/
    private fun checkPermissions(client: FTPClient, rootPath: String) {
        // Check permissions with ListDirectories
        try {
            var fList = client.listDirectories(rootPath)
            var attr = fList.find { it.name == target.path }
            if(attr == null)
                attr = fList.find { it.name == "." }
            if(attr != null) {
                if(!attr.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION))
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_permission_read), null)
                if(!isSource && !attr.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION))
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_permission_write), null)
            }
            else {
                // ListDirectories didn't return parent folder => Check with MLST
                var permissions = client.mlistFile(rootPath).toString().split(";").find { "perm" in it }?.replace("^perms?=".toRegex(), "")
                if(permissions.isNullOrEmpty()) {
                    // MLST didn't return permission => check with MLSD
                    fList = client.mlistDir(rootPath)
                    var found = fList.find { it.name == target.path }
                    if(found == null)
                        found = fList.find { it.name == "." }
                    if(found != null)
                        permissions = found.toString().split(";").find { "perm" in it }?.replace("^perms?=".toRegex(), "")
                }
                if(permissions.isNullOrEmpty())
                    generateWarning(localeContext.getString(R.string.error_permission_unknown), true, false)
                else {
                    if("e" !in permissions || "l" !in permissions)
                        throw CriticalException(originTxt, localeContext.getString(R.string.error_permission_read), null)
                    if(!isSource && !("c" in permissions && "m" in permissions))
                        throw CriticalException(originTxt, localeContext.getString(R.string.error_permission_write), null)
                }
            }
        }
        catch(exp: CriticalException) { throw exp }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_permission_unknown), true, false) }
    }


    // ----------------
    // Server connexion
    // ----------------

    /** Get correct SSL connexion **/
    private suspend fun getConnexionSSL(connexion: Int): FTPSClient {
        return when(connexion) {
            1 -> {
                if(!ftps1.isConnected)
                    makeConnexionSSL(connexion)
                ftps1
            }
            2 -> {
                if(!ftps2.isConnected)
                    makeConnexionSSL(connexion)
                ftps2
            }
            3 -> {
                if(!ftps3.isConnected)
                    makeConnexionSSL(connexion)
                ftps3
            }
            4 -> {
                if(!ftps4.isConnected)
                    makeConnexionSSL(connexion)
                ftps4
            }
            else -> {
                if(!ftps5.isConnected)
                    makeConnexionSSL(connexion)
                ftps5
            }
        }
    }

    /** Get correct no SSL connexion **/
    private suspend fun getConnexion(connexion: Int): FTPClient {
        return when(connexion) {
            1 -> {
                if(!ftp1.isConnected)
                    makeConnexion(connexion)
                ftp1
            }
            2 -> {
                if(!ftp2.isConnected)
                    makeConnexion(connexion)
                ftp2
            }
            3 -> {
                if(!ftp3.isConnected)
                    makeConnexion(connexion)
                ftp3
            }
            4 -> {
                if(!ftp4.isConnected)
                    makeConnexion(connexion)
                ftp4
            }
            else -> {
                if(!ftp5.isConnected)
                    makeConnexion(connexion)
                ftp5
            }
        }
    }

    /** Try to connect with SSL/TLS **/
    private suspend fun makeConnexionSSL(connexion: Int) = withContext(dispatcherIO) {
        val userName = target.login.takeIf { !it.isNullOrBlank() }?.cipherDecrypt() ?: "anonymous"

        val password = target.password.takeIf { !it.isNullOrBlank() }?.cipherDecrypt()
            ?: if(target.login.isNullOrBlank())
                "password"
            else
                ""

        when(connexion) {
            1 -> ftps1
            2 -> ftps2
            3 -> ftps3
            4 -> ftps4
            else -> ftps5
        }.apply {
            try{
                trustManager = TrustManagerUtils.getValidateServerCertificateTrustManager()
                isEndpointCheckingEnabled = true
                autodetectUTF8 = true
                bufferSize = 64*1024

                target.port?.let { connect(target.server?.cipherDecrypt(), it) }
                    ?: connect(target.server?.cipherDecrypt())

                require(FTPReply.isPositiveCompletion(replyCode))
                execAUTH("TLS")
                enterLocalPassiveMode()
            }
            catch(exp: Exception) {
                if("certificate" in exp.message.toString().lowercase(Locale.getDefault()))
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_ssl_certificate), exp.message)
                else
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), exp.message)
            }

            try { require(login(userName, password)) }
            catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_server_login), exp.message) }

            try {
                execPBSZ(0)
                execPROT("P")
            }
            catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_ssl_connect), exp.message) }

            try { require(setFileType(FTP.BINARY_FILE_TYPE)) }
            catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_ftp_filetype), exp.message) }
        }
        return@withContext
    }

    /** Try to connect without SSL/TLS **/
    private suspend fun makeConnexion(connexion: Int) = withContext(dispatcherIO) {
        val userName = target.login.takeIf { !it.isNullOrBlank() }?.cipherDecrypt() ?: "anonymous"

        val password = target.password.takeIf { !it.isNullOrBlank() }?.cipherDecrypt()
            ?: if(target.login.isNullOrBlank())
                "password"
            else
                ""

        when(connexion) {
            1 -> ftp1
            2 -> ftp2
            3 -> ftp3
            4 -> ftp4
            else -> ftp5
        }.apply {
            try{
                autodetectUTF8 = true
                bufferSize = 64*1024

                target.port?.let { connect(target.server?.cipherDecrypt(), it) }
                    ?: connect(target.server?.cipherDecrypt())

                require(FTPReply.isPositiveCompletion(replyCode))

                enterLocalPassiveMode()
            }
            catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), exp.message) }

            try { require(login(userName, password)) }
            catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_server_login), exp.message) }

            try { require(setFileType(FTP.BINARY_FILE_TYPE)) }
            catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_ftp_filetype), exp.message) }
        }
        return@withContext
    }

    /** Disconnect from the server **/
    override suspend fun disconnect() = withContext(dispatcherIO) {
        for(i in 1..5) {
            when(i) {
                1 -> if(ssl) ftps1 else ftp1
                2 -> if(ssl) ftps2 else ftp2
                3 -> if(ssl) ftps3 else ftp3
                4 -> if(ssl) ftps4 else ftp4
                else -> if(ssl) ftps5 else ftp5
            }.apply {
                try {
                    if(isConnected) {
                        logout()
                        disconnect()
                    }
                }
                catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_server_disconnect), true, false) }
            }
        }
    }


    // ------------------
    // Get folder content
    // ------------------

    /** List files on remote folder **/
    override suspend fun getContent(recursive: Boolean, filesOnly: Boolean): MutableList<WorkerBackup_File> {
        return listDirectory(getAbsolutePath(null, target.path, true), recursive, filesOnly)
    }

    /** Recursive files listing **/
    private suspend fun listDirectory(directory: String, recursive: Boolean, filesOnly: Boolean): MutableList<WorkerBackup_File> = withContext(dispatcherIO) {
        val client = if(ssl)
            ftps1
        else
            ftp1

        val systemFiles = arrayOf(".", "..", ".DAV", "._DAV")
        val resources = try { client.listFiles(directory).filterNot { systemFiles.contains(it.name) } }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": $directory", exp.message) }

        val filePath = directory.toRelativePath(target.path)
        val lstFiles = mutableListOf<WorkerBackup_File>()

        resources.forEach {
            val isDirectory = it.isDirectory
            val fileName = it.name
            val date = if(!isDirectory)
                try { client.mlistFile("$directory/${it.name}").timestamp.toInstant() }
                catch(exp: Exception) { throw CriticalException(null, localeContext.getString(R.string.error_ftp_mlst), exp.message) }
            else
                it.timestamp.toInstant()

            val skip = isDirectory && filesOnly
            if(!skip)
                lstFiles.add(WorkerBackup_File(filePath, fileName, date, it.size, isDirectory))

            val doRecursive = isDirectory && recursive
            if(doRecursive)
                lstFiles.addAll(listDirectory("$directory/$fileName", recursive, filesOnly))
        }
        return@withContext lstFiles
    }


    // ----------
    // Read/Write
    // ----------

    /** Get InputStream to download the file **/
    override suspend fun getReader(file: WorkerBackup_File, connexion: Int): InputStream = withContext(dispatcherIO) {
        val srcPath = getAbsolutePath(listOf(file.path, file.name), target.path, true)

        return@withContext try {
            if(ssl)
                getConnexionSSL(connexion).retrieveFileStream(srcPath)
            else
                getConnexion(connexion).retrieveFileStream(srcPath)
        }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_read), (exp as? CriticalException)?.error ?: exp.message) }
    }

    /** Close inputStream after file download **/
    override suspend fun closeReader(connexion: Int): Boolean = withContext(dispatcherIO) {
        return@withContext try {
            if(ssl)
                getConnexionSSL(connexion).completePendingCommand()
            else
                getConnexion(connexion).completePendingCommand()
        }
        catch(exp: Exception) { throw CriticalException(null, localeContext.getString(R.string.error_file_read_finish), (exp as? CriticalException)?.error ?: exp.message) }
    }

    /** Upload file from InputStream **/
    override suspend fun write(reader: InputStream, file: WorkerBackup_File, connexion: Int, canRetry: Boolean, reportProgress: (Long) -> Unit): Boolean = withContext(dispatcherIO) {
        val client = if(ssl)
            getConnexionSSL(connexion)
        else
            getConnexion(connexion)

        val destPath = getAbsolutePath(listOf(file.path, file.name), target.path, true)

        val streamListener = object : CopyStreamAdapter() {
            override fun bytesTransferred(totalBytesTransferred: Long, bytesTransferred: Int, streamSize: Long) {
                reportProgress(totalBytesTransferred)
                if(totalBytesTransferred == file.size)
                    removeCopyStreamListener(this)
            }
        }

        try {
            client.copyStreamListener = streamListener
            reader.use { client.storeFile(destPath, it) }
        }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_copy), exp.message) }
        finally { client.copyStreamListener = null }

        setDate(destPath, file.last_modified, client)
        val transferResult = verifyTransfer(destPath, file.last_modified, file.size, client)

        if(!transferResult.sizeIsOK) {
            if(canRetry)
                log.append(true,"<b>["+ localeContext.getString(R.string.log_note)+"]</b> ${file.name}: "+ localeContext.getString(R.string.error_file_corrupted))
            else
                generateWarning("${file.name}: "+ localeContext.getString(R.string.error_file_corrupted_final), false, true)
            return@withContext false
        }

        manageDate(transferResult.dateIsOK)
        return@withContext true
    }


    // ------------
    // Verification
    // ------------

    /** Check if file transfer went wrong **/
    private fun verifyTransfer(file: String, date: Instant, size: Long, client: FTPClient): WorkerBackup_TransferResult {
        val fileInfo = try { client.mlistFile(file) }
        catch (_: Exception) { return WorkerBackup_TransferResult(false, false) }

        if(fileInfo == null)
            throw CriticalException(null, localeContext.getString(R.string.error_ftp_mlst), null)

        return WorkerBackup_TransferResult(dateIsOK(date, fileInfo.timestamp.toInstant()), fileIsOK(size,fileInfo.size))
    }


    // ---------------
    // Date management
    // ---------------

    /** Manage Files LastModifiedDate change **/
    private fun manageDate(dateIsOK: Boolean) {
        if(accurateDateAuto && dateIsOK != accurateDate) {
            accurateDate = dateIsOK

            if(dateIsOK)
                generateWarning(localeContext.getString(R.string.success_file_date_modification), false, true)
            else
                generateWarning(localeContext.getString(R.string.error_file_date_modification), false, true)

            Manager_Workers().setDateComparisonMode(backupID, dateIsOK)
        }
    }

    /** Change last modified date on remote server file **/
    private fun setDate(file: String, date: Instant, client: FTPClient) {
        val dtf = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("GMT"))

        try { client.setModificationTime(file, dtf.format(date)) }
        catch (_: Exception) {  }
    }


    // ----------------------
    // Directories management
    // ----------------------

    /** Create folders on remote server if necessary **/
    override suspend fun createDirectories(folderList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        val client = if(ssl)
            getConnexionSSL(connexion)
        else
            getConnexion(connexion)

        folderList.filter { it.isNotBlank() }.forEach { createDirectory(getAbsolutePath(listOf(it), target.path, true), client) }
    }

    /** Create a folder on remote server if necessary **/
    private fun createDirectory(path: String, client: FTPClient) {
        try {
            require(path.isNotBlank() && path != "/")

            if(!client.changeWorkingDirectory(path)) {
                val parent = path.parentDirectory()
                if(!client.changeWorkingDirectory(parent)) {
                    require(path != parent)
                    createDirectory(parent, client)
                }

                require(client.mkd(path) < 400)
            }
        }
        catch(exp: Exception) { throw CriticalException(null, localeContext.getString(R.string.error_folder_create)+": $path", exp.message) }
    }

    /** Delete a folders list from remote server **/
    override suspend fun deleteDirectories(foldersList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        val client = if(ssl)
            getConnexionSSL(connexion)
        else
            getConnexion(connexion)

        foldersList.filter { it.isNotBlank() }.forEach { deleteDirectory(getAbsolutePath(listOf(it), target.path, true), client) }
    }

    /** Delete a folder from remote server **/
    private fun deleteDirectory(path: String, client: FTPClient) {
        if(!client.changeWorkingDirectory(path))
            return

        try {
            client.changeToParentDirectory()
            if(path.isEmptyDirectory(client))
                require(client.removeDirectory(path))
            else
                removeDirectory(path, client)
        }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_folder_delete)+": $path", false, true) }
    }

    /** Remove system files from a folder and delete it if empty (.DAV) **/
    private fun removeDirectory(path: String, client: FTPClient) {
        try {
            val systemFiles = arrayOf(".", "..")
            val resources = client.listFiles(path).filterNot { systemFiles.contains(it.name) }
            resources.forEach {
                if(it.isDirectory)
                    removeDirectory("$path/${it.name}", client)
                else if(path.contains(".DAV") || path.contains("._DAV"))
                    require(client.deleteFile("$path/${it.name}"))
            }
            if(path.isEmptyDirectory(client))
                require(client.removeDirectory(path))
        }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_folder_delete)+": "+path, false, true) }
    }


    // ----------------
    // Files management
    // ----------------

    /** Manage orphan files on remote server **/
    override suspend fun manageOrphan(orphanFile: WorkerBackup_File, action: Int, orphansFolder: String?, connexion: Int) = withContext(dispatcherIO) {
        val client = if(ssl)
            getConnexionSSL(connexion)
        else
            getConnexion(connexion)

        if (action == 1 && orphansFolder != null)
            moveOrphan(orphansFolder, orphanFile.path, orphanFile.name, client)
        else if (action >= 2)
            deleteOrphan(orphanFile.path, orphanFile.name, client)
    }

    /** Move file on remote server **/
    private fun moveOrphan(orphansFolder: String, orphanPath: String, orphanName: String, client: FTPClient) {
        val srcFile = getAbsolutePath(listOf(orphanPath, orphanName), target.path, true)
        val destFile = getAbsolutePath(listOf(orphansFolder, orphanPath, orphanName), target.path, true)

        try { require(client.rename(srcFile, destFile)) }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_move), false, true) }
    }

    /** Delete a file from remote server **/
    private fun deleteOrphan(orphanPath: String, orphanName: String, client: FTPClient) {
        val orphanFile = getAbsolutePath(listOf(orphanPath, orphanName), target.path, true)
        try {
            if(client.mlistFile(orphanFile) != null)
                require(client.deleteFile(orphanFile))
        }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_delete), false, true) }
    }


    // --------------
    // FTP extensions
    // --------------

    /** Check if a folder on remote server contains data **/
    private fun String.isEmptyDirectory(client: FTPClient): Boolean {
        return try { client.listFiles(this).none { it.name != "." && it.name != ".." } }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": $this", exp.message) }
    }

    /** Get parent directory **/
    private fun String.parentDirectory(): String {
        val path =  this.trim('/')

        return if(path.contains("/"))
            "/" + path.substringBeforeLast('/')
        else
            "/"
    }


    // --------
    // Warnings
    // --------

    /** Create a warning in log file **/
    private fun generateWarning(message: String, addOrigin: Boolean, isImportant: Boolean) {
        val originTxt = if(addOrigin)
            "</b> <i>$originTxt</i><b>"
        else
            ""
        log.append(true,"<font color=orange><b>[" + localeContext.getString(R.string.log_warning) + "$originTxt]</b> $message</font>")

        if(isImportant)
            warningTriggered = true
    }

    /** Check if warning has been triggered **/
    override fun hasWarning(): Boolean = warningTriggered
}