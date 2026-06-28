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

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.msfscc.fileinformation.FileBasicInformation.DONT_UPDATE
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.transport.tcp.direct.DirectTcpTransportFactory
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.WorkerBackup_File
import fr.hellpc.mirror.data.WorkerBackup_TransferResult
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.managers.Manager_Log
import fr.hellpc.mirror.managers.Manager_Workers
import fr.hellpc.mirror.utilities.CriticalException
import fr.hellpc.mirror.utilities.Utility_Conversion.sizeToReadable
import fr.hellpc.mirror.utilities.Utility_Encryption.cipherDecrypt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.time.Instant
import java.util.EnumSet


class Backup_SMB(
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

    private lateinit var client: SMBClient
    private lateinit var session: Session
    private lateinit var share: DiskShare

    private var warningTriggered = false


    // --------------
    // Initialisation
    // --------------

    /** Check server availability **/
    override suspend fun checkServer() = withContext(dispatcherIO) {
        if (target.server.isNullOrBlank())
            throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), null)

        makeConnexion()
    }

    /** Check folder availability **/
    override suspend fun checkRootFolder() = withContext(dispatcherIO) {
        // Check if root folder exists in share
        val pathRoot = getAbsolutePath(null, target.path, false).toSmbPath()
        try {
            if(!share.folderExists(pathRoot)) {
                // Create missing folder only for Destination
                if(target.path.isBlank() || isSource)
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_existence), null)

                generateWarning(localeContext.getString(R.string.log_folder_destination_create), false, false)
                createDirectory(pathRoot)

                if(!share.folderExists(pathRoot))
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_existence), null)
            }
        }
        catch(exp: CriticalException) { throw exp }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_invalid), exp.message) }

        checkPermissions(pathRoot)
    }

    /** Check permissions **/
    @OptIn(ExperimentalStdlibApi::class)
    private fun checkPermissions(pathRoot: String) {
        val flags = try { share.getFileInformation(pathRoot).accessInformation.accessFlags }
        catch(_: Exception) {
            generateWarning(localeContext.getString(R.string.error_permission_unknown), true, false)
            return
        }

        val permissions = try { EnumWithValue.EnumUtils.toEnumSet(flags.toHexString().toLong(), AccessMask::class.java) }
        catch(_: Exception) {
            generateWarning(localeContext.getString(R.string.error_permission_unknown), true, false)
            return
        }

        if(!permissions.contains(AccessMask.FILE_READ_EA))
            throw CriticalException(originTxt, localeContext.getString(R.string.error_permission_read), null)
        if(!isSource && !permissions.contains(AccessMask.FILE_WRITE_EA))
            throw CriticalException(originTxt, localeContext.getString(R.string.error_permission_write), null)
    }

    /** Check available space **/
    override suspend fun checkFreeSpace(size: Long) = withContext(dispatcherIO) {
        try{
            val freeSpace = share.shareInformation.freeSpace
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

    /** Make connexion **/
    private suspend fun makeConnexion() = withContext(dispatcherIO) {
        val config = SmbConfig.builder()
            .withMultiProtocolNegotiate(true)
            .withNegotiatedBufferSize()
            .withDfsEnabled(true)
            .withEncryptData(true)
            .withSecurityProvider(BCSecurityProvider())
            .withTransportLayerFactory(DirectTcpTransportFactory()) // AsyncDirectTcpTransportFactory leads to SMB credits saturation

        if(!target.login.isNullOrBlank())
            config.withSigningRequired(true)

        client = SMBClient(config.build())

        val connection = try { client.connect(target.server?.cipherDecrypt()) }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), exp.message) }

        val auth = if(target.login.isNullOrBlank())
            AuthenticationContext.anonymous()
        else
            AuthenticationContext(target.login.cipherDecrypt(), (target.password?.cipherDecrypt()?:"").toCharArray(), target.domain?:"")

        session = try { connection.authenticate(auth) }
        catch(exp: Exception) {
            if(!target.login.isNullOrBlank())
                throw CriticalException(originTxt, localeContext.getString(R.string.error_server_login), exp.message)
            try { connection.authenticate(AuthenticationContext.guest()) }
            catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_server_login), exp.message) }
        }

        if(!session.connection.isConnected)
            throw CriticalException(originTxt, localeContext.getString(R.string.error_server_connect), null)

        try {
            share = session.connectShare(target.share) as DiskShare
            require(share.isConnected)
        }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_share_connect), exp.message) }
    }

    /** Disconnect from the server **/
    override suspend fun disconnect() = withContext(dispatcherIO) {
        try {
             if(::client.isInitialized)
                client.close()
        } catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_server_disconnect), true, false) }
    }


    // ------------------
    // Get folder content
    // ------------------

    /** List files on remote folder **/
    override suspend fun getContent(recursive: Boolean, filesOnly: Boolean): MutableList<WorkerBackup_File> {
        return listDirectory(getAbsolutePath(null, target.path, false), recursive, filesOnly)
    }

    /** Recursive files listing **/
    private suspend fun listDirectory(directory: String, recursive: Boolean, filesOnly: Boolean): MutableList<WorkerBackup_File> = withContext(dispatcherIO) {
        val systemFiles = arrayOf(".", "..", ".DAV", "._DAV")
        val resources = try { share.list(directory.toSmbPath(), "*").filterNot { systemFiles.contains(it.fileName) } }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": $directory", exp.message) }

        val filePath = directory.toRelativePath(target.path)
        val lstFiles = mutableListOf<WorkerBackup_File>()

        resources.forEach {
            val fileName = it.fileName
            val isDirectory = EnumWithValue.EnumUtils.isSet(it.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
            val lastModified = it.lastWriteTime.toInstant()

            val skip = isDirectory && filesOnly
            if(!skip)
                lstFiles.add(WorkerBackup_File(filePath, fileName, lastModified, it.endOfFile, isDirectory))

            val doRecursive = isDirectory && recursive
            if(doRecursive) {
                val subDirectory = if(directory == "") fileName else "$directory/$fileName"
                lstFiles.addAll(listDirectory(subDirectory, recursive, filesOnly))
            }
        }
        return@withContext lstFiles
    }


    // ----------
    // Read/Write
    // ----------

    /** Get InputStream to download the file **/
    override suspend fun getReader(file: WorkerBackup_File, connexion: Int): InputStream = withContext(dispatcherIO) {
        return@withContext try {
            share.openFile(
                getAbsolutePath(listOf(file.path, file.name), target.path, false).toSmbPath(),
                EnumSet.of(AccessMask.FILE_READ_DATA),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            ).inputStream
        } catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_read), (exp as? CriticalException)?.error ?: exp.message) }
    }

    /** Upload file from InputStream **/
    override suspend fun write(reader: InputStream, file: WorkerBackup_File, connexion: Int, canRetry: Boolean, reportProgress: (Long) -> Unit): Boolean = withContext(dispatcherIO) {
        val destPath = getAbsolutePath(listOf(file.path, file.name), target.path, false).toSmbPath()

        try {
            Channels.newChannel(reader).use { inputChannel ->
                var written = 0L
                val buffer = ByteBuffer.allocate(1024*1024)
                var lastReportTime = 0L

                share.openFile(
                    destPath,
                    EnumSet.of(AccessMask.GENERIC_WRITE),
                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE),
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    EnumSet.noneOf(SMB2CreateOptions::class.java)
                ).apply {
                    // Set file date
                    val info = FileBasicInformation(DONT_UPDATE, DONT_UPDATE, FileTime.fromInstant(file.last_modified), DONT_UPDATE, FileAttributes.FILE_ATTRIBUTE_NORMAL.value)
                    try { setFileInformation(info) }
                    catch(_: Exception) { }

                    // Upload content
                    Channels.newChannel(outputStream).use { outputChannel ->
                        while(inputChannel.read(buffer) > 0) {
                            buffer.flip()
                            written += outputChannel.write(buffer)
                            buffer.clear()

                            val currentTime = System.currentTimeMillis()
                            if(currentTime - lastReportTime >= reportFrequency) {
                                reportProgress(written)
                                lastReportTime = currentTime
                            }
                        }

                        reportProgress(file.size)
                    }

                    // Close file
                    close()
                }
            }
        }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_copy), exp.message) }

        val transferResult = verifyTransfer(destPath, file.last_modified, file.size)

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
    private fun verifyTransfer(file: String, date: Instant, size: Long): WorkerBackup_TransferResult {
        val fileInfo = try { share.getFileInformation(file) }
        catch (_: Exception) { return WorkerBackup_TransferResult(false, false) }

        return WorkerBackup_TransferResult(dateIsOK(date, fileInfo.basicInformation.lastWriteTime.toInstant()), fileIsOK(size, fileInfo.standardInformation.endOfFile))
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


    // ----------------------
    // Directories management
    // ----------------------

    /** Create folders on remote server if necessary **/
    override suspend fun createDirectories(folderList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        folderList.filter { it.isNotBlank() }.forEach { createDirectory(getAbsolutePath(listOf(it), target.path, false).toSmbPath()) }
    }

    /** Create a folder on remote server if necessary **/
    private fun createDirectory(path: String) {
        try {
            require(path.isNotBlank())

            if(!share.folderExists(path)) {
                val parent = path.parentDirectory()
                if(!share.folderExists(parent)) {
                    require(path != parent)
                    createDirectory(parent)
                }

                share.mkdir(path)
            }
        }
        catch(exp: Exception) { throw CriticalException(null, localeContext.getString(R.string.error_folder_create)+": $path", (exp as? CriticalException)?.error ?: exp.message) }
    }

    /** Delete a folders list from remote server **/
    override suspend fun deleteDirectories(foldersList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        foldersList.filter { it.isNotBlank() }.forEach { deleteDirectory(getAbsolutePath(listOf(it), target.path, false).toSmbPath()) }
    }

    /** Delete a folder from remote server **/
    private fun deleteDirectory(path: String) {
        if(!share.folderExists(path))
            return

        try {
            if(path.isEmptyDirectory())
                share.rmdir(path, false)
            else
                removeDirectory(path)
        }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_folder_delete)+": $path", false, true) }
    }

    /** Remove system files from a folder and delete it if empty (.DAV) **/
    private fun removeDirectory(path: String) {
        try {
            val systemFiles = arrayOf(".", "..")
            val resources = share.list(path, "*").filterNot { systemFiles.contains(it.fileName) }
            resources.forEach {
                if(share.getFileInformation("$path\\${it.fileName}").standardInformation.isDirectory)
                    removeDirectory("$path\\${it.fileName}")
            }
            if (path.isEmptyDirectory() || path.contains(".DAV") || path.contains("._DAV"))
                share.rmdir(path, true)
        }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_folder_delete)+": $path", false, true) }
    }


    // ----------------
    // Files management
    // ----------------

    /** Manage orphan files on remote server **/
    override suspend fun manageOrphan(orphanFile: WorkerBackup_File, action: Int, orphansFolder: String?, connexion: Int) = withContext(dispatcherIO) {
        if (action == 1 && orphansFolder != null)
                moveOrphan(orphansFolder, orphanFile.path, orphanFile.name)
        else if (action >= 2)
            deleteOrphan(orphanFile.path, orphanFile.name)
    }

     /** Move file on remote server **/
    private fun moveOrphan(orphansFolder: String, orphanPath: String, orphanName: String) {
        val srcFile = getAbsolutePath(listOf(orphanPath, orphanName), target.path, false).toSmbPath()
        val destFile = getAbsolutePath(listOf(orphansFolder, orphanPath, orphanName), target.path, false).toSmbPath()

        try {
            share.openFile(
                srcFile,
                EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            ).use { it.rename(destFile) }
        }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_move), false, true) }
    }

    /** Delete a file from remote server **/
    private fun deleteOrphan(orphanPath: String, orphanName: String) {
        val orphanFile = getAbsolutePath(listOf(orphanPath, orphanName), target.path, false).toSmbPath()
        try {
            if(share.fileExists(orphanFile))
                share.rm(orphanFile)
        }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_delete), false, true) }
    }


    // --------------
    // SMB extensions
    // --------------

    /** Check if a folder on remote server contains data **/
    private fun String.isEmptyDirectory(): Boolean {
        return try { share.list(this, "*").none { it.fileName != "." && it.fileName != ".." } }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": $this", exp.message) }
    }

    /** Replace / with \ **/
    private fun String.toSmbPath() = this.replace('/', '\\')

    /** Get parent directory **/
    private fun String.parentDirectory(): String {
        val path =  this.trimEnd('\\')

        return if(path.contains("\\"))
            path.substringBeforeLast('\\')
        else
            ""
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