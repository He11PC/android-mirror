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

import com.emc.ecs.nfsclient.nfs.NfsSetAttributes
import com.emc.ecs.nfsclient.nfs.NfsTime
import com.emc.ecs.nfsclient.nfs.io.Nfs3File
import com.emc.ecs.nfsclient.nfs.io.NfsFileInputStream
import com.emc.ecs.nfsclient.nfs.io.NfsFileOutputStream
import com.emc.ecs.nfsclient.nfs.nfs3.Nfs3
import com.emc.ecs.nfsclient.rpc.CredentialNone
import com.emc.ecs.nfsclient.rpc.CredentialUnix
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.WorkerBackup_File
import fr.hellpc.mirror.data.WorkerBackup_TransferResult
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.managers.Manager_Log
import fr.hellpc.mirror.managers.Manager_Workers
import fr.hellpc.mirror.utilities.CriticalException
import fr.hellpc.mirror.utilities.Utility_Conversion.sizeToReadable
import fr.hellpc.mirror.security.Security_Encryption.cipherDecrypt
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
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds


class Backup_NFS(
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

    private lateinit var nfs1: Nfs3
    private lateinit var nfs2: Nfs3
    private lateinit var nfs3: Nfs3
    private lateinit var nfs4: Nfs3
    private lateinit var nfs5: Nfs3

    private var warningTriggered = false


    // --------------
    // Initialisation
    // --------------

    /** Check server availability **/
    override suspend fun checkServer() = withContext(dispatcherIO) {
        if (target.server.isNullOrBlank())
            throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), null)

        makeConnexion(1)
    }

    /** Check folder availability **/
    override suspend fun checkRootFolder() = withContext(dispatcherIO) {
        val rootPath = getAbsolutePath(null, target.path, true)

        // Check if root folder exists in share
        try {
            if(!getAccess(nfs1, rootPath).exists()) {
                // Create missing folder only for Destination
                if(target.path.isBlank() || isSource)
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_existence), null)

                generateWarning(localeContext.getString(R.string.log_folder_destination_create), false, false)
                createDirectory(rootPath, nfs1, getMkdirAttributes(nfs1))

                if(!getAccess(nfs1, rootPath).exists())
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_existence), null)
            }
        }
        catch(exp: CriticalException) { throw exp }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_invalid), exp.message) }

        checkPermissions(rootPath)
    }

    /** Check permissions **/
    private fun checkPermissions(rootPath: String) {
        try {
            val nfsRoot = getAccess(nfs1, rootPath)

            if(!nfsRoot.canLookup() || !nfsRoot.canRead())
                generateWarning(localeContext.getString(R.string.error_permission_read), true, false)
            if(!nfsRoot.canExtend() || !nfsRoot.canModify())
                generateWarning(localeContext.getString(R.string.error_permission_write), true, false)
        }
        catch(exp: CancellationException) { throw exp }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_permission_unknown), true, false) }
    }

    /** Check available space **/
    override suspend fun checkFreeSpace(size: Long) = withContext(dispatcherIO) {
        try {
            val freeSpace = getAccess(nfs1, getAbsolutePath(null, target.path, true)).freeSpace
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
    private suspend fun getConnexion(connexion: Int): Nfs3 {
        return when(connexion) {
            1 -> {
                if (!::nfs1.isInitialized || nfs1.exportedPath.isNullOrBlank())
                    makeConnexion(connexion)
                nfs1
            }
            2 -> {
                if (!::nfs2.isInitialized || nfs2.exportedPath.isNullOrBlank())
                    makeConnexion(connexion)
                nfs2
            }
            3 -> {
                if (!::nfs3.isInitialized || nfs3.exportedPath.isNullOrBlank())
                    makeConnexion(connexion)
                nfs3
            }
            4 -> {
                if (!::nfs4.isInitialized || nfs4.exportedPath.isNullOrBlank())
                    makeConnexion(connexion)
                nfs4
            }
            else -> {
                if (!::nfs5.isInitialized || nfs5.exportedPath.isNullOrBlank())
                    makeConnexion(connexion)
                nfs5
            }
        }
    }

    /** Make connexion **/
    private suspend fun makeConnexion(connexion: Int) = withContext(dispatcherIO) {
        val nfs = try {
            if(target.uid.isNullOrBlank() && target.gid.isNullOrBlank())
                Nfs3(target.server?.cipherDecrypt(), target.share, CredentialNone(), 3)
            else
                Nfs3(target.server?.cipherDecrypt(), target.share, CredentialUnix(target.uid?.cipherDecrypt()?.toInt()?:0, target.gid?.cipherDecrypt()?.toInt()?:0, null), 3)
        }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_server_login), exp.message) }

        when(connexion) {
            1 -> nfs1 = nfs
            2 -> nfs2 = nfs
            3 -> nfs3 = nfs
            4 -> nfs4 = nfs
            else -> nfs5 = nfs
        }
    }

    /** Get access to a file/folder **/
    private fun getAccess(nfs: Nfs3, path: String): Nfs3File {
        return try { Nfs3File(nfs, path) }
        catch (exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_path_reach)+": $path", exp.message) }
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
        val nfsPath = getAccess(nfs1, directory)
        val resources = try { nfsPath.listFiles().filterNot { it.name.isVirtualDirectory() || it.name.isSystemFile() } }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": $directory", exp.message) }

        val filePath = directory.toRelativePath(target.path)
        val lstFiles = mutableListOf<WorkerBackup_File>()

        for(resource in resources) {
            val fileName = resource.name
            val isDirectory = resource.isDirectory
            val lastModified = Instant.ofEpochMilli(resource.lastModified())

            val skip = isDirectory && filesOnly
            if(!skip)
                lstFiles.add(WorkerBackup_File(filePath, fileName, lastModified, resource.attributes.size, isDirectory))

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
        val srcFile = getAccess(getConnexion(connexion), getAbsolutePath(listOf(file.path, file.name), target.path, true))

        return@withContext try { NfsFileInputStream(srcFile, 128*1024) }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_read), exp.message) }
    }

    /** Upload file from InputStream **/
    override suspend fun write(reader: InputStream, file: WorkerBackup_File, connexion: Int, canRetry: Boolean, reportProgress: (Long) -> Unit): Boolean = withContext(dispatcherIO) {
        val destFile = getAccess(getConnexion(connexion), getAbsolutePath(listOf(file.path, file.name), target.path, true))

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

        try {
            Channels.newChannel(reader).use { inputChannel ->
                val buffer = ByteBuffer.allocate(1024*1024)
                Channels.newChannel(NfsFileOutputStream(destFile)).use { outputChannel ->
                    while(inputChannel.read(buffer) > 0) {
                        buffer.flip()
                        val bytesWritten = outputChannel.write(buffer)
                        written.addAndGet(bytesWritten.toLong())
                        buffer.clear()
                    }

                    reportProgress(file.size)
                }
            }
        }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_copy), exp.message) }
        finally { progressJob.cancel() }

        setDate(destFile, file.last_modified)
        val transferResult = verifyTransfer(destFile, file.last_modified, file.size)

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
    private fun verifyTransfer(file: Nfs3File, date: Instant, size: Long): WorkerBackup_TransferResult {
        val fileDate = try { file.lastModified() }
        catch (_: Exception) { return WorkerBackup_TransferResult(false, false) }

        val fileSize = try { file.attributes.size }
        catch (_: Exception) { return WorkerBackup_TransferResult(false, false) }

        return WorkerBackup_TransferResult(dateIsOK(date, Instant.ofEpochMilli(fileDate)), fileIsOK(size, fileSize))
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
    private fun setDate(file: Nfs3File, date: Instant) {
        try { file.setLastModified(date.toEpochMilli()) }
        catch (_: Exception) {  }
    }


    // ----------------------
    // Directories management
    // ----------------------

    /** Get default attributes for directory creation **/
    private fun getMkdirAttributes(nfs: Nfs3): NfsSetAttributes {
        val rootProtectionMode = try { getAccess(nfs, "/").attributes.mode }
        catch(_: Exception) { 511 }
        return NfsSetAttributes(rootProtectionMode, target.uid?.cipherDecrypt()?.toLong()?:0, target.gid?.cipherDecrypt()?.toLong()?:0, NfsTime.DO_NOT_CHANGE, NfsTime.DO_NOT_CHANGE)
    }

    /** Create folders on remote server if necessary **/
    override suspend fun createDirectories(folderList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        val nfs = getConnexion(connexion)
        val attr = getMkdirAttributes(nfs)

        folderList.forEach {
            if(it.isNotBlank())
                createDirectory(getAbsolutePath(it, target.path, true), nfs, attr)
        }
    }

    /** Create a folder on remote server if necessary **/
    private fun createDirectory(path: String, nfs: Nfs3, attr: NfsSetAttributes) {
        if(path.trimEnd('/').isBlank())
            return

        try {
            val nfsFolder = getAccess(nfs, path)

            try { nfsFolder.mkdir(attr) }
            catch(exp: Exception) {
                val parentPath = path.parentDirectory()
                if(path != parentPath && parentPath.trimEnd('/').isNotBlank()) {
                    createDirectory(parentPath, nfs, attr)
                    try {
                        nfsFolder.mkdir(attr)
                        return
                    } catch (_: Exception) { }
                }

                // Fail-safe: Check if directory already exists
                if (nfsFolder.exists())
                    return

                throw exp
            }
        }
        catch(exp: Exception) { throw CriticalException(null, localeContext.getString(R.string.error_folder_create)+": $path", exp.message) }
    }

    /** Delete a folders list from remote server **/
    override suspend fun deleteDirectories(folderList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        val nfs = getConnexion(connexion)

        folderList.forEach {
            if(it.isNotBlank())
                deleteDirectory(getAbsolutePath(it, target.path, true), nfs)
        }
    }

    /** Delete a folder from remote server **/
    private fun deleteDirectory(path: String, nfs: Nfs3) {
        val nfsFolder = getAccess(nfs, path)

        try { nfsFolder.delete() }
        catch(exp: CancellationException) { throw exp }
        catch(_: Exception) {
            try {
                // Deletion failed, check content
                val resources = nfsFolder.listFiles() ?: throw IOException()
                var entryCount = 0

                // Delete WebDav system files if they exist
                for(resource in resources) {
                    val name = resource.name
                    if (name.isVirtualDirectory()) continue

                    entryCount++
                    val resourcePath = if(path.endsWith("/")) "$path$name" else "$path/$name"

                    if(resource.isDirectory)
                        deleteDirectory(resourcePath, nfs)
                    else if(name.isSystemFile())
                        resource.delete()
                    else
                        throw IOException()
                }

                // Retry folder deletion after cleanup
                if(entryCount > 0)
                    nfsFolder.delete()
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
        val nfs = getConnexion(connexion)
        val nfsOrphan = getAccess(nfs, getAbsolutePath(listOf(orphanFile.path, orphanFile.name), target.path, true))

        if (action == 1 && orphansFolder != null) {
            val newLocation = getAccess(nfs, getAbsolutePath(listOf(orphansFolder, orphanFile.path, orphanFile.name), target.path, true))
            moveOrphan(nfsOrphan, newLocation)
        }
        else if (action >= 2)
            deleteOrphan(nfsOrphan)
    }

    /** Move file on remote server **/
    private fun moveOrphan(srcFile: Nfs3File, destFile: Nfs3File) {
        try { require(srcFile.renameTo(destFile)) }
        catch(exp: CancellationException) { throw exp }
        catch(_: Exception) { generateWarning(srcFile.name+": "+localeContext.getString(R.string.error_file_move), false, true) }
    }

    /** Delete a file from remote server **/
    private fun deleteOrphan(orphanFile: Nfs3File) {
        try { orphanFile.delete() }
        catch(exp: IOException) {
            if(orphanFile.exists())
                throw exp
        }
        catch(exp: CancellationException) { throw exp }
        catch(_: Exception) { generateWarning(orphanFile.name+": "+localeContext.getString(R.string.error_file_delete), false, true) }
    }


    // --------------
    // NFS extensions
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