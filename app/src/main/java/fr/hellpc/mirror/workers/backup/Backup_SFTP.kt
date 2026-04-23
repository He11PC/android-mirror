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
import fr.hellpc.mirror.utilities.Utility_Encryption.cipherDecrypt
import fr.hellpc.mirror.utilities.Utility_Conversion.sizeToReadable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.Instant
import java.util.Locale


class Backup_SFTP(
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
                setKnownHosts(target.hostKey?.cipherDecrypt()?.toByteArray()?.inputStream())
                session = getSession(target.login?.cipherDecrypt(), target.server.cipherDecrypt(), target.port ?: 22)
            }

            session.apply {
                setPassword(target.password?.cipherDecrypt())
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
            throw CriticalException(originTxt, localeContext.getString(R.string.error_permission_read), null)
        if(!isSource && !attr.permissionsString.contains('w', true))
            throw CriticalException(originTxt, localeContext.getString(R.string.error_permission_write), null)
    }

    /** Check available space **/
    override suspend fun checkFreeSpace(size: Long) = withContext(dispatcherIO) {
        try{
            val fsInfo = channel1.statVFS(getAbsolutePath(null, target.path, true))
            val freeSpace = fsInfo.avail*1024
            val freeSpaceTxt = ": "+freeSpace.sizeToReadable()
            if(freeSpace < size)
                throw CriticalException(null, localeContext.getString(R.string.error_space_critical)+freeSpaceTxt, null)
            else if (freeSpace < size+size*0.25)
                generateWarning(localeContext.getString(R.string.error_space_low)+freeSpaceTxt, false, true)
        }
        catch(exp: CriticalException) { throw exp }
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
        val systemFiles = arrayOf(".", "..", ".DAV", "._DAV")
        val resources = try { channel1.ls(directory.ifBlank { "/" }).filterNot { systemFiles.contains(it.filename) } }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": $directory", exp.message) }

        val filePath = directory.toRelativePath(target.path)
        val lstFiles = mutableListOf<WorkerBackup_File>()

        resources.forEach {
            val isDirectory = it.attrs.isDir
            val fileName = it.filename

            val skip = isDirectory && filesOnly
            if(!skip)
                lstFiles.add(WorkerBackup_File(filePath, fileName, Instant.ofEpochSecond(it.attrs.mTime.toLong()), it.attrs.size, isDirectory))

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

        val streamListener = object : SftpProgressMonitor {
            var bytesTransferred = 0L
            override fun init(op: Int, src: String?, dest: String?, max: Long) { }
            override fun end() { }

            override fun count(count: Long): Boolean {
                bytesTransferred += count
                reportProgress(bytesTransferred)
                return true
            }
        }

        try { reader.use { sftp.put(it, destPath, streamListener, ChannelSftp.OVERWRITE) } }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_copy), exp.message) }

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
        folderList.filter { it.isNotBlank() }.forEach { createDirectory(getAbsolutePath(listOf(it), target.path, true), sftp) }
    }

    /** Create a folder on remote server if necessary **/
    private fun createDirectory(path: String, sftp: ChannelSftp) {
        try {
            require(path.isNotBlank() && path != "/")

            if(!exists(path, sftp)) {
                require(path != "/")

                val parent = path.parentDirectory()
                if(!exists(parent, sftp)) {
                    require(path != parent)
                    createDirectory(parent, sftp)
                }

                sftp.mkdir(path)
            }
        }
        catch(exp: Exception) { throw CriticalException(null, localeContext.getString(R.string.error_folder_create)+": $path", exp.message) }
    }

    /** Delete a folder list from remote server **/
    override suspend fun deleteDirectories(foldersList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        val sftp = getConnexion(connexion)
        foldersList.filter { it.isNotBlank() }.forEach { deleteDirectory(getAbsolutePath(listOf(it), target.path, true), sftp) }
    }

    /** Delete a folder from remote server **/
    private fun deleteDirectory(path: String, sftp: ChannelSftp) {
        try {
            if(exists(path, sftp)) {
                if(path.isEmptyDirectory(sftp))
                    sftp.rmdir(path)
                else
                    removeDirectory(path, sftp)
            }
        }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_folder_delete)+": "+path, false, true) }
    }

    /** Remove system files from a folder and delete it if empty (.DAV) **/
    private fun removeDirectory(path: String, sftp: ChannelSftp) {
        try {
            val systemFiles = arrayOf(".", "..")
            val resources = sftp.ls(path).filterNot { systemFiles.contains(it.filename) }

            resources.forEach {
                if(it.attrs.isDir)
                    removeDirectory("$path/${it.filename}", sftp)
                else if(path.contains(".DAV") || path.contains("._DAV"))
                    sftp.rm("$path/${it.filename}")
            }
            if(path.isEmptyDirectory(sftp))
                sftp.rmdir(path)
        }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_folder_delete)+": "+path, false, true) }
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
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_move), false, true) }
    }

    /** Delete a file from remote server **/
    private fun deleteOrphan(orphanPath: String, orphanName: String, sftp: ChannelSftp) {
        val orphanFile = getAbsolutePath(listOf(orphanPath, orphanName), target.path, true)
        try {
            if(exists(orphanFile, sftp))
                sftp.rm(orphanFile)
        }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_delete), false, true) }
    }


    // ---------------
    // SFTP extensions
    // ---------------

    /** Check if a folder on remote server contains data **/
    private fun String.isEmptyDirectory(sftp: ChannelSftp): Boolean {
        return try { sftp.ls(this).none { it.filename != "." && it.filename != ".." } }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": $this", exp.message) }
    }

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