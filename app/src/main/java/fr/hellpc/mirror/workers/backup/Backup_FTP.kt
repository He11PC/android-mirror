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

import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.WorkerBackup_File
import fr.hellpc.mirror.data.WorkerBackup_TransferResult
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.managers.Manager_Log
import fr.hellpc.mirror.managers.Manager_Workers
import fr.hellpc.mirror.security.Security_Encryption.cipherDecrypt
import fr.hellpc.mirror.security.Security_FlexibleTrustManager
import fr.hellpc.mirror.utilities.CriticalException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.io.CopyStreamAdapter
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.milliseconds


class Backup_FTP(
    private val backupID: Int,
    private val isSource: Boolean,
    private val originTxt: String,
    private val target : Backup_Target,
    private val accurateDateAuto: Boolean,
    private var accurateDate: Boolean,
    private val reportFrequency: Long,
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

    private var useMlistFile = true

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
                    generateWarning(localeContext.getString(R.string.error_permission_read), true, false)
                if(!isSource && !attr.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION))
                    generateWarning(localeContext.getString(R.string.error_permission_write), true, false)
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
                        generateWarning(localeContext.getString(R.string.error_permission_read), true, false)
                    if(!isSource && !("c" in permissions && "m" in permissions))
                        generateWarning(localeContext.getString(R.string.error_permission_write), true, false)
                }
            }
        }
        catch(exp: CancellationException) { throw exp }
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
                trustManager = Security_FlexibleTrustManager(target.hostKey?.cipherDecrypt())
                isEndpointCheckingEnabled = target.hostKey.isNullOrBlank()
                autodetectUTF8 = true
                bufferSize = 256*1024

                target.port?.let { connect(target.server?.cipherDecrypt(), it) }
                    ?: connect(target.server?.cipherDecrypt())

                require(FTPReply.isPositiveCompletion(replyCode))
                execAUTH("TLS")
                enterLocalPassiveMode()
            }
            catch(exp: Exception) {
                if("certificat" in exp.message.toString().lowercase(Locale.getDefault()))
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
                bufferSize = 256*1024

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
                        try { logout() } catch(_: Exception) { }
                        disconnect()
                    }
                }
                catch(exp: CancellationException) { throw exp }
                catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_server_disconnect), true, false) }
            }
        }
    }


    // ------------------
    // Get folder content
    // ------------------

    /** List files on remote folder **/
    override suspend fun getContent(recursive: Boolean, filesOnly: Boolean): MutableList<WorkerBackup_File> {
        return listDirectory(getAbsolutePath(null, target.path, true), recursive, filesOnly, true)
    }

    /** Recursive files listing **/
    private suspend fun listDirectory(directory: String, recursive: Boolean, filesOnly: Boolean, useMlistDir: Boolean): MutableList<WorkerBackup_File> = withContext(dispatcherIO) {
        val client = if(ssl)
            ftps1
        else
            ftp1

        val resources = try {
            val files = if(useMlistDir)
                client.mlistDir(directory)
            else
                client.listFiles(directory)
            files?.filterNot { it.name.isVirtualDirectory() || it.name.isSystemFile() } ?: emptyList()
        }
        catch(exp: CancellationException) { throw exp }
        catch(exp: Exception) {
            if(useMlistDir)
                return@withContext listDirectory(directory, recursive, filesOnly, false)
            else
                throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": $directory", exp.message)
        }

        val filePath = directory.toRelativePath(target.path)
        val lstFiles = mutableListOf<WorkerBackup_File>()

        for(resource in resources) {
            val isDirectory = resource.isDirectory
            val fileName = resource.name
            val date = if(isDirectory || useMlistDir)
                resource.timestamp!!.toInstant()
            else {
                val itemPath = "$directory/$fileName"
                var preciseInstant: Instant? = null

                if(useMlistFile) {
                    try { preciseInstant = client.mlistFile(itemPath).timestamp.toInstant() }
                    catch (_: Exception) { useMlistFile = false }
                }

                preciseInstant ?: getTimestampFromMDTM(itemPath, client)
            }


            val skip = isDirectory && filesOnly
            if(!skip)
                lstFiles.add(WorkerBackup_File(filePath, fileName, date, resource.size, isDirectory))

            val doRecursive = isDirectory && recursive
            if(doRecursive)
                lstFiles.addAll(listDirectory("$directory/$fileName", recursive, filesOnly, useMlistDir))
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

        val written = AtomicLong(0L)
        val progressJob = CoroutineScope(Dispatchers.Default).launch {
            var lastReported = 0L
            while(isActive) {
                delay(reportFrequency.milliseconds)
                val currentWritten = written.get()
                if(currentWritten > lastReported) {
                    reportProgress(currentWritten)
                    lastReported = currentWritten
                }
            }
        }

        val streamListener = object : CopyStreamAdapter() {
            override fun bytesTransferred(totalBytesTransferred: Long, bytesTransferred: Int, streamSize: Long) {
                written.set(totalBytesTransferred)
            }
        }

        val success = try {
            client.copyStreamListener = streamListener
            reader.use { client.storeFile(destPath, it) }
        }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_copy), exp.message) }
        finally {
            client.copyStreamListener = null
            progressJob.cancel()
        }

        if(!success) {
            if(canRetry)
                log.append(true,"<b>["+ localeContext.getString(R.string.log_note)+"]</b> ${file.name}: "+ localeContext.getString(R.string.error_file_corrupted))
            else
                generateWarning("${file.name}: "+ localeContext.getString(R.string.error_file_corrupted_final), false, true)
            return@withContext false
        }

        reportProgress(file.size)

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
        var ftpDate: Instant
        var ftpSize: Long

        if(useMlistFile) {
            try {
                val fileInfo = client.mlistFile(file)
                ftpDate = fileInfo.timestamp.toInstant()
                ftpSize = fileInfo.size
            }
            catch(_: Exception) {
                useMlistFile = false
                ftpDate = getTimestampFromMDTM(file, client)
                ftpSize = getFileSize(file, client)
            }
        }
        else {
            ftpDate = getTimestampFromMDTM(file, client)
            ftpSize = getFileSize(file, client)
        }

        return WorkerBackup_TransferResult(dateIsOK(date, ftpDate), fileIsOK(size,ftpSize))
    }

    /** Get file size **/
    private fun getFileSize(path: String, client: FTPClient): Long {
        try {
            val ftpSize = client.getSize(path)?.toLong()
            require(ftpSize != null)
            return ftpSize
        }
        catch(exp: Exception) { throw CriticalException(null, localeContext.getString(R.string.error_ftp_file_size), exp.message) }
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

    /** Get file timestamp from MDTM **/
    private fun getTimestampFromMDTM(path: String, client: FTPClient): Instant {
        try {
            val timeRaw = client.getModificationTime(path)
            val timeString = timeRaw.trim().takeLast(14)
            require(timeString.length == 14 && timeString.all { it.isDigit() })
            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            return java.time.LocalDateTime.parse(timeString, formatter).atZone(java.time.ZoneOffset.UTC).toInstant()
        }
        catch(exp: Exception) { throw CriticalException(null, localeContext.getString(R.string.error_ftp_file_date), exp.message) }
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

        folderList.forEach {
            if(it.isNotBlank())
                createDirectory(getAbsolutePath(it, target.path, true), client)
        }
    }

    /** Create a folder on remote server if necessary **/
    private fun createDirectory(path: String, client: FTPClient) {
        if (path.trimEnd('/').isBlank())
            return

        try {
            if(!client.makeDirectory(path)) {
                  // 550 / 500+ : Directory creation failed
                if(client.replyCode >= 400) {
                    val parent = path.parentDirectory()
                    if(path != parent && parent.trimEnd('/').isNotBlank()) {
                        createDirectory(parent, client)
                        if(client.makeDirectory(path))
                            return
                    }

                    // Fail-safe: Check if directory already exists
                    if(client.changeWorkingDirectory(path))
                        return

                    throw IOException("FTP MKD failed with code ${client.replyCode}: ${client.replyString}")
                }
            }
        }
        catch(exp: Exception) { throw CriticalException(null, localeContext.getString(R.string.error_folder_create)+": $path", exp.message) }
    }

    /** Delete a folders list from remote server **/
    override suspend fun deleteDirectories(folderList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        val client = if(ssl)
            getConnexionSSL(connexion)
        else
            getConnexion(connexion)

        folderList.forEach {
            if(it.isNotBlank())
                deleteDirectory(getAbsolutePath(it, target.path, true), client)
        }
    }

    /** Delete a folder from remote server **/
    private fun deleteDirectory(path: String, client: FTPClient) {
        try {
            if(client.removeDirectory(path)) return

            // Deletion failed, check content
            val resources = client.listFiles(path) ?: throw IOException()
            var entryCount = 0

            // Delete WebDav system files if they exist
            for(resource in resources) {
                val name = resource.name
                if(name.isVirtualDirectory()) continue

                entryCount++
                val resourcePath = if(path.endsWith("/")) "$path$name" else "$path/$name"

                if(resource.isDirectory)
                    deleteDirectory(resourcePath, client)
                else if(name.isSystemFile())
                    require(client.deleteFile(resourcePath))
                else
                    throw IOException()
            }

            // Retry folder deletion after cleanup
            if(entryCount == 0 || !client.removeDirectory(path))
                throw IOException()
        }
        catch(exp: CancellationException) { throw exp }
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
        catch(exp: CancellationException) { throw exp }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_move), false, true) }
    }

    /** Delete a file from remote server **/
    private fun deleteOrphan(orphanPath: String, orphanName: String, client: FTPClient) {
        val orphanFile = getAbsolutePath(listOf(orphanPath, orphanName), target.path, true)
        try {
            if(!client.deleteFile(orphanFile) && client.replyCode != 550)
                throw IOException()
        }
        catch(exp: CancellationException) { throw exp }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_delete), false, true) }
    }


    // --------------
    // FTP extensions
    // --------------

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