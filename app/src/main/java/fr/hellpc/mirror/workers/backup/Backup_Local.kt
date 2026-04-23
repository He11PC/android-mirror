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

import android.os.StatFs
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.WorkerBackup_File
import fr.hellpc.mirror.data.WorkerBackup_TransferResult
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.managers.Manager_Log
import fr.hellpc.mirror.managers.Manager_Workers
import fr.hellpc.mirror.utilities.CriticalException
import fr.hellpc.mirror.utilities.Utility_BackupTarget
import fr.hellpc.mirror.utilities.Utility_Conversion.sizeToReadable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString

class Backup_Local(
    private val backupID: Int,
    private val isSource: Boolean,
    private val originTxt: String,
    private val target : Backup_Target,
    private val accurateDateAuto: Boolean,
    private var accurateDate: Boolean,
    private val reportFrequency: Int,
    private val log: Manager_Log
): Backup_Interface {

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO

    private val localeContext = App.getLocaleContext()

    private var warningTriggered = false


    // --------------
    // Initialisation
    // --------------

    /** Initialisation **/
    override suspend fun initialise() {
        checkRootFolder()
    }

    /** Check folder existence and permissions **/
    override suspend fun checkRootFolder() = withContext(dispatcherIO) {
        val rootPath = getAbsolutePath(null, target.path, false)
        val rootPathNio = rootPath.toLocalPath()

        try {
            if (Files.notExists(rootPathNio)) {
                // Create missing folder only for Destination
                if (isSource)
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_existence), null)

                if (Utility_BackupTarget().pathIsValid(target.path, false)) {
                    generateWarning(localeContext.getString(R.string.log_folder_destination_create), false, false)
                    createDirectory(rootPath)
                    if (Files.notExists(rootPathNio))
                        throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_existence), null)
                }
                else
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_invalid), null)
            }

            if(!Files.isReadable(rootPathNio))
                throw CriticalException(originTxt, localeContext.getString(R.string.error_permission_read), null)
            if(!isSource && !Files.isWritable(rootPathNio))
                throw CriticalException(originTxt, localeContext.getString(R.string.error_permission_write), null)
        }
        catch(_: SecurityException) { generateWarning(localeContext.getString(R.string.error_permission_unknown), true, false) }
        catch(exp: CriticalException) { throw exp }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_invalid), exp.message) }
    }

    /** Check available space **/
    override suspend fun checkFreeSpace(size: Long) = withContext(dispatcherIO) {
        try {
            val freeSpace = StatFs(target.path).availableBytes
            val freeSpaceTxt = ": "+freeSpace.sizeToReadable()
            if(freeSpace < size)
                throw CriticalException(null, localeContext.getString(R.string.error_space_critical)+freeSpaceTxt, null)
            else if (freeSpace < size+size*0.25)
                generateWarning(localeContext.getString(R.string.error_space_low)+freeSpaceTxt, false, true)
        }
        catch(exp: CriticalException) { throw exp }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_space_unverifiable), false, false) }
    }


    // ------------------
    // Get folder content
    // ------------------

    /** List files on local folder **/
    override suspend fun getContent(recursive: Boolean, filesOnly: Boolean): MutableList<WorkerBackup_File> = withContext(dispatcherIO) {
        val maxDepth = if (recursive)
            Integer.MAX_VALUE
        else
            1

        val resources = try { Files.walk(getAbsolutePath(null, target.path, false).toLocalPath(), maxDepth).sorted() }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": ${target.path}", exp.message) }

        val lstFiles = mutableListOf<WorkerBackup_File>()

        resources.forEach {
            val isDirectory = it.isDirectory()
            val fileName = it.name
            val filePath = it.pathString.removePrefix(target.path).substringBeforeLast(fileName)

            val skip = filePath == "" || fileName == ".nomedia" || (isDirectory && filesOnly)

            if(!skip)
                lstFiles.add(WorkerBackup_File(filePath, fileName, it.getLastModifiedTime().toInstant(), it.fileSize(), isDirectory))
        }

        return@withContext lstFiles
    }


    // ----------
    // Read/Write
    // ----------

    /** Get InputStream to download the file **/
    override suspend fun getReader(file: WorkerBackup_File, connexion: Int): InputStream = withContext(dispatcherIO) {
        val srcPath = getAbsolutePath(listOf(file.path, file.name), target.path, false).toLocalPath()

        return@withContext try { FileInputStream(srcPath.toFile()) }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_read), exp.message) }
    }

    /** Upload file from InputStream **/
    override suspend fun write(reader: InputStream, file: WorkerBackup_File, connexion: Int, canRetry: Boolean, reportProgress: (Long) -> Unit): Boolean = withContext(dispatcherIO) {
        val destPath = getAbsolutePath(listOf(file.path, file.name), target.path, false).toLocalPath()

        try {
            Channels.newChannel(reader).use { inputChannel ->
                val buffer = ByteBuffer.allocate(64*1024)
                var loop = 0

                FileOutputStream(destPath.toFile()).channel.use { outputChannel ->
                    while(inputChannel.read(buffer) > 0) {
                        buffer.flip()
                        outputChannel.write(buffer)
                        buffer.clear()
                        val written = outputChannel.position()
                        if(loop.mod(reportFrequency) == 0)
                            reportProgress(written)
                        loop ++
                    }
                }
            }
        }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_copy), exp.message) }

        setDate(destPath, file.last_modified)
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
    private fun verifyTransfer(file: Path, date: Instant, size: Long): WorkerBackup_TransferResult {
        val fileSize = try { Files.size(file) }
        catch (_: Exception) { 0 }

        val fileDate = try { Files.getLastModifiedTime(file).toInstant() }
        catch (_: Exception) { Instant.ofEpochSecond(0) }

        return WorkerBackup_TransferResult(dateIsOK(date, fileDate), fileIsOK(size, fileSize))
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

    /** Change last modified date on local file **/
    private fun setDate(file: Path, date: Instant) {
        try { Files.setLastModifiedTime(file, FileTime.from(date)) }
        catch(_: Exception) { }
    }


    // ----------------------
    // Directories management
    // ----------------------

    /** Create folders on remote server if necessary **/
    override suspend fun createDirectories(folderList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        folderList.filter { it.isNotBlank() }.forEach { createDirectory(getAbsolutePath(listOf(it), target.path, false)) }
    }

    /** Create a folder on local memory if necessary **/
    private fun createDirectory(path: String) {
        try { Files.createDirectories(path.toLocalPath()) }
        catch(exp: Exception) { throw CriticalException(null, localeContext.getString(R.string.error_folder_create)+": $path", exp.message) }
    }

    /** Delete a folders list from local memory **/
    override suspend fun deleteDirectories(foldersList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        foldersList.filter { it.isNotBlank() }.forEach { deleteDirectory(getAbsolutePath(listOf(it), target.path, false)) }
    }

    /** Delete a folder from local memory **/
    private fun deleteDirectory(path: String) {
        try { Files.deleteIfExists(path.toLocalPath()) }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_folder_delete)+": $path", false, true) }
    }


    // ----------------
    // Files management
    // ----------------

    /** Manage orphan files on local memory **/
    override suspend fun manageOrphan(orphanFile: WorkerBackup_File, action: Int, orphansFolder: String?, connexion: Int) = withContext(dispatcherIO) {
        if (action == 1 && orphansFolder != null)
            moveOrphan(orphansFolder, orphanFile.path, orphanFile.name)
        else if (action >= 2)
            deleteOrphan(orphanFile.path, orphanFile.name)
    }

    /** Move file to another folder **/
    private fun moveOrphan(orphansFolder: String, orphanPath: String, orphanName: String) {
        val srcFile = getAbsolutePath(listOf(orphanPath, orphanName), target.path, false).toLocalPath()
        val destFile = getAbsolutePath(listOf(orphansFolder, orphanPath, orphanName), target.path, false).toLocalPath()

        try { Files.move(srcFile, destFile, StandardCopyOption.REPLACE_EXISTING) }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_move), false, true) }
    }

    /** Delete a file from local memory **/
    private fun deleteOrphan(orphanPath: String, orphanName: String) {
        val orphanFile = getAbsolutePath(listOf(orphanPath, orphanName), target.path, false).toLocalPath()
        try { Files.deleteIfExists(orphanFile) }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_delete), false, true) }
    }

    /** Create or remove .nomedia file **/
    override suspend fun manageNomedia(checked: Boolean) = withContext(dispatcherIO) {
        if(checked)
            createNomedia()
        else
            removeNomedia()
    }

    /** Create a .nomedia file to hide content from gallery **/
    private fun createNomedia() {
        val nomediaPath = getAbsolutePath(listOf(".nomedia"), target.path, false).toLocalPath()
        try {
            if (Files.notExists(nomediaPath)) {
                Files.createFile(nomediaPath)
                log.append(false, localeContext.getString(R.string.log_nomedia_created))
            }
        }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_nomedia_create), false, true) }
    }

    /** Remove .nomedia file **/
    private fun removeNomedia() {
        val nomediaPath = getAbsolutePath(listOf(".nomedia"), target.path, false).toLocalPath()
        try {
            if (Files.deleteIfExists(nomediaPath))
                log.append(false, localeContext.getString(R.string.log_nomedia_removed))
        }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_nomedia_remove), false, true) }
    }


    // ----------------
    // Local extensions
    // ----------------

    /** Transforms a standard path to a local path required by nio.file **/
    private fun String.toLocalPath() = Paths.get(this)


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