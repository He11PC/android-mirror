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

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelSftp.SSH_FX_NO_SUCH_FILE
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.SftpProgressMonitor
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.WorkerBackup_File
import fr.hellpc.mirror.data.WorkerBackup_TransferResult
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.managers.Manager_Log
import fr.hellpc.mirror.managers.Manager_Workers
import fr.hellpc.mirror.utilities.CriticalException
import fr.hellpc.mirror.security.Security_Encryption.cipherDecrypt
import fr.hellpc.mirror.utilities.Utility_Conversion.sizeToReadable
import fr.hellpc.mirror.security.Security_Encryption.cipherDecryptToBytes
import fr.hellpc.mirror.security.Security_Encryption.use
import fr.hellpc.mirror.security.Security_Encryption.useAsInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds


class Backup_SFTP(
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

    private lateinit var session: Session
    private lateinit var channel1: ChannelSftp
    private lateinit var channel2: ChannelSftp
    private lateinit var channel3: ChannelSftp
    private lateinit var channel4: ChannelSftp
    private lateinit var channel5: ChannelSftp

    private var warningTriggered = false


    // --------------
    // Initialisation
    // --------------

    /** Check server availability **/
    override suspend fun checkServer() = withContext(dispatcherIO) {
        if(target.server.isNullOrBlank())
            throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), null)

        try {
            JSch().apply {
                target.hostKey?.cipherDecryptToBytes()?.useAsInputStream { setKnownHosts(it) }
                session = getSession(target.login?.cipherDecrypt().orEmpty(), target.server.cipherDecrypt(), target.port ?: 22)
            }

            session.apply {
                target.password?.cipherDecryptToBytes()?.use { setPassword(it) }
                connect()
            }
        }
        catch(exp: Exception) {
            val errorMsg = exp.message.toString()
            when {
                "auth" in errorMsg.lowercase(Locale.getDefault()) -> throw CriticalException(originTxt, localeContext.getString(R.string.error_server_login), exp.message)
                "host" in errorMsg.lowercase(Locale.getDefault()) -> throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), exp.message)
                "timed out" in errorMsg.lowercase(Locale.getDefault()) -> throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), exp.message)
                else -> throw CriticalException(originTxt, localeContext.getString(R.string.error_server_connect), exp.message)
            }
        }

        makeConnexion(1)
    }

    /** Check folder availability **/
    override suspend fun checkRootFolder() = withContext(dispatcherIO) {
        val rootPath = getAbsolutePath(null, target.path, true)

        // Check if root folder exists
        val attr = try { channel1.stat(rootPath) }
        catch(_: SftpException) {
            // Create missing folder only for Destination
            if(target.path.isBlank() || isSource)
                throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_existence), null)

            generateWarning(localeContext.getString(R.string.log_folder_destination_create), false, false)
            createDirectory(rootPath, channel1)

            try { channel1.stat(rootPath) }
            catch(exp: SftpException) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_existence), exp.message) }
        }

        checkPermissions(attr)
    }

    /** Check permissions **/
    private fun checkPermissions(attr: SftpATTRS) {
        if(!attr.permissionsString.contains('r', true))
            generateWarning(localeContext.getString(R.string.error_permission_read), true, false)
        if(!isSource && !attr.permissionsString.contains('w', true))
            generateWarning(localeContext.getString(R.string.error_permission_write), true, false)
    }

    /** Check available space **/
    override suspend fun checkFreeSpace(size: Long) = withContext(dispatcherIO) {
        try {
            val fsInfo = channel1.statVFS(getAbsolutePath(null, target.path, true))
            val freeSpace = fsInfo.avail*1024
            if(freeSpace <= 0L) {
                generateWarning(localeContext.getString(R.string.error_space_unverifiable), false, false)
                return@withContext
            }

            val freeSpaceTxt = ": " + freeSpace.sizeToReadable()
            when {
                freeSpace < size -> generateWarning(localeContext.getString(R.string.error_space_critical) + freeSpaceTxt, false, true)
                freeSpace < (size + (size * 0.25).toLong()) -> generateWarning(localeContext.getString(R.string.error_space_low) + freeSpaceTxt, false, true)
            }
        }
        catch(exp: CancellationException) { throw exp }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_space_unverifiable), false, false) }
    }


    // ----------------
    // Server connexion
    // ----------------

    /** Get correct connexion **/
    private suspend fun getConnexion(connexion: Int): ChannelSftp {
        return when(connexion) {
            1 -> {
                if (!::channel1.isInitialized || !channel1.isConnected)
                    makeConnexion(connexion)
                channel1
            }
            2 -> {
                if (!::channel2.isInitialized || !channel2.isConnected)
                    makeConnexion(connexion)
                channel2
            }
            3 -> {
                if (!::channel3.isInitialized || !channel3.isConnected)
                    makeConnexion(connexion)
                channel3
            }
            4 -> {
                if (!::channel4.isInitialized || !channel4.isConnected)
                    makeConnexion(connexion)
                channel4
            }
            else -> {
                if (!::channel5.isInitialized || !channel5.isConnected)
                    makeConnexion(connexion)
                channel5
            }
        }
    }
    
    /** Make connexion **/
    private suspend fun makeConnexion(connexion: Int) = withContext(dispatcherIO) {
        try {
            (session.openChannel("sftp") as ChannelSftp).apply {
                connect()
                require(isConnected)

                when(connexion) {
                    1 -> channel1 = this
                    2 -> channel2 = this
                    3 -> channel3 = this
                    4 -> channel4 = this
                    else -> channel5 = this
                }
            }
        }
        catch(exp: Exception) {
            val errorMsg = exp.message.toString()
            when {
                "auth" in errorMsg.lowercase(Locale.getDefault()) -> throw CriticalException(originTxt, localeContext.getString(R.string.error_server_login), exp.message)
                "host" in errorMsg.lowercase(Locale.getDefault()) -> throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), exp.message)
                "timed out" in errorMsg.lowercase(Locale.getDefault()) -> throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), exp.message)
                else -> throw CriticalException(originTxt, localeContext.getString(R.string.error_server_connect), exp.message)
            }
        }
        return@withContext
    }

    /** Disconnect from the server **/
    override suspend fun disconnect() = withContext(dispatcherIO) {
        try {
            if (::channel1.isInitialized && channel1.isConnected)
                channel1.disconnect()

            if (::channel2.isInitialized && channel2.isConnected)
                channel2.disconnect()

            if (::channel3.isInitialized && channel3.isConnected)
                channel3.disconnect()

            if (::channel4.isInitialized && channel4.isConnected)
                channel4.disconnect()

            if (::channel5.isInitialized && channel5.isConnected)
                channel5.disconnect()

            if (::session.isInitialized && session.isConnected)
                session.disconnect()
        }
        catch(exp: CancellationException) { throw exp }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_server_disconnect), true, false) }
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
        val resources = try { channel1.ls(directory.ifBlank { "/" }).filterNot { it.filename.isVirtualDirectory() || it.filename.isSystemFile() } }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": $directory", exp.message) }

        val filePath = directory.toRelativePath(target.path)
        val lstFiles = mutableListOf<WorkerBackup_File>()

        for(resource in resources) {
            val isDirectory = resource.attrs.isDir
            val fileName = resource.filename

            val skip = isDirectory && filesOnly
            if(!skip)
                lstFiles.add(WorkerBackup_File(filePath, fileName, Instant.ofEpochSecond(resource.attrs.mTime.toLong()), resource.attrs.size, isDirectory))

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

        return@withContext try { getConnexion(connexion).get(srcPath) }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_read), (exp as? CriticalException)?.error ?: exp.message) }
    }

    /** Upload file from InputStream **/
    override suspend fun write(reader: InputStream, file: WorkerBackup_File, connexion: Int, canRetry: Boolean, reportProgress: (Long) -> Unit): Boolean = withContext(dispatcherIO) {
        val sftp = getConnexion(connexion)
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

        val streamListener = object : SftpProgressMonitor {
            override fun init(op: Int, src: String?, dest: String?, max: Long) { }
            override fun end() { }

            override fun count(count: Long): Boolean {
                written.addAndGet(count)
                return true
            }
        }

        try {
            reader.use { sftp.put(it, destPath, streamListener, ChannelSftp.OVERWRITE) }
            reportProgress(file.size)
        }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_copy), exp.message) }
        finally { progressJob.cancel() }

        setDate(destPath, file.last_modified, sftp)
        val transferResult = verifyTransfer(destPath, file.last_modified, file.size, sftp)

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
    private fun verifyTransfer(file: String, date: Instant, size: Long, sftp: ChannelSftp): WorkerBackup_TransferResult {
        val fileInfo = try { sftp.lstat(file) }
        catch (_: Exception) { return WorkerBackup_TransferResult(false, false) }

        return WorkerBackup_TransferResult(dateIsOK(date, Instant.ofEpochSecond(fileInfo.mTime.toLong())), fileIsOK(size,fileInfo.size))
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
    private fun setDate(file: String, date: Instant, sftp: ChannelSftp) {
        try { sftp.setMtime(file, date.epochSecond.toInt()) }
        catch(_: Exception) { }
    }


    // ----------------------
    // Directories management
    // ----------------------

    /** Create folders on remote server if necessary **/
    override suspend fun createDirectories(folderList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        val sftp = getConnexion(connexion)

        folderList.forEach {
            if(it.isNotBlank())
                createDirectory(getAbsolutePath(it, target.path, true), sftp)
        }
    }

    /** Create a folder on remote server if necessary **/
    private fun createDirectory(path: String, sftp: ChannelSftp) {
        if(path.trimEnd('/').isBlank())
            return

        try {
            try { sftp.mkdir(path) }
            catch(exp: SftpException) {
                val parent = path.parentDirectory()
                if(path != parent && parent.trimEnd('/').isNotBlank()) {
                    createDirectory(parent, sftp)
                    try {
                        sftp.mkdir(path)
                        return
                    }
                    catch(_: SftpException) { }
                }

                // Fail-safe: Check if directory already exists
                if(exists(path, sftp))
                    return

                throw exp
            }
        }
        catch(exp: Exception) { throw CriticalException(null, localeContext.getString(R.string.error_folder_create)+": $path", exp.message) }
    }

    /** Delete a folder list from remote server **/
    override suspend fun deleteDirectories(folderList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        val sftp = getConnexion(connexion)

        folderList.forEach {
            if(it.isNotBlank())
                deleteDirectory(getAbsolutePath(it, target.path, true), sftp)
        }
    }

    /** Delete a folder from remote server **/
    private fun deleteDirectory(path: String, sftp: ChannelSftp) {
        try { sftp.rmdir(path) }
        catch(exp: CancellationException) { throw exp }
        catch(_: Exception) {
            try {
                // Deletion failed, check content
                val resources = sftp.ls(path) ?: throw IOException()
                var entryCount = 0

                // Delete WebDav system files if they exist
                for(resource in resources) {
                    val name = resource.filename
                    if (name.isVirtualDirectory()) continue

                    entryCount++
                    val resourcePath = if(path.endsWith("/")) "$path$name" else "$path/$name"

                    if(resource.attrs.isDir)
                        deleteDirectory(resourcePath, sftp)
                    else if(name.isSystemFile())
                        sftp.rm(resourcePath)
                    else
                        throw IOException()
                }

                // Retry folder deletion after cleanup
                if(entryCount > 0)
                    sftp.rmdir(path)
                else
                    throw IOException()
            }
            catch(exp: CancellationException) { throw exp }
            catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_folder_delete)+": "+path, false, true) }
        }
    }


    // ----------------
    // Files management
    // ----------------

    /** Manage orphan files on remote server **/
    override suspend fun manageOrphan(orphanFile: WorkerBackup_File, action: Int, orphansFolder: String?, connexion: Int) = withContext(dispatcherIO) {
        val sftp = getConnexion(connexion)

        if (action == 1 && orphansFolder != null)
            moveOrphan(orphansFolder, orphanFile.path, orphanFile.name, sftp)
        else if (action >= 2)
            deleteOrphan(orphanFile.path, orphanFile.name, sftp)
    }

    /** Move file on remote server **/
    private fun moveOrphan(orphansFolder: String, orphanPath: String, orphanName: String, sftp: ChannelSftp) {
        val srcFile = getAbsolutePath(listOf(orphanPath, orphanName), target.path, true)
        val destFile = getAbsolutePath(listOf(orphansFolder, orphanPath, orphanName), target.path, true)

        try { sftp.rename(srcFile, destFile) }
        catch(exp: CancellationException) { throw exp }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_move), false, true) }
    }

    /** Delete a file from remote server **/
    private fun deleteOrphan(orphanPath: String, orphanName: String, sftp: ChannelSftp) {
        val orphanFile = getAbsolutePath(listOf(orphanPath, orphanName), target.path, true)
        try { sftp.rm(orphanFile) }
        catch(exp: SftpException) {
            if(exp.id != SSH_FX_NO_SUCH_FILE)
                throw exp
        }
        catch(exp: CancellationException) { throw exp }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_delete), false, true) }
    }


    // ---------------
    // SFTP extensions
    // ---------------

    /** Check if a file/folder exists **/
    private fun exists(path: String, sftp: ChannelSftp): Boolean {
        try { sftp.lstat(path) }
        catch(e: SftpException) {
            if (e.id == SSH_FX_NO_SUCH_FILE)
                return false
            else
                throw e
        }
        return true
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