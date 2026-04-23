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

import fr.hellpc.mirror.data.WorkerBackup_File
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

sealed interface Backup_Interface {

    // --------------
    // Initialisation
    // --------------

    /** Initialise server connexion and do initial checks **/
    suspend fun initialise() {
        checkServer()
        checkRootFolder()
    }

    /** Check server availability **/
    suspend fun checkServer() { }
    /** Check folder availability **/
    suspend fun checkRootFolder() { }
    /** Check available space **/
    suspend fun checkFreeSpace(size: Long) { }

    /** Disconnect from the server and close connexions **/
    suspend fun disconnect() { }


    // ----------
    // Read/Write
    // ----------

    /** List files on remote folder **/
    suspend fun getContent(recursive: Boolean, filesOnly: Boolean): MutableList<WorkerBackup_File>

    /** Get InputStream to download the file **/
    suspend fun getReader(file: WorkerBackup_File, connexion: Int): InputStream
    /** Close inputStream after file downloaded (FTP) **/
    suspend fun closeReader(connexion: Int): Boolean { return true }
    /** Upload file from local source (WebDav) **/
    suspend fun write(srcRoot: String, file: WorkerBackup_File, connexion: Int, canRetry: Boolean, reportProgress: (Long) -> Unit): Boolean { return false }
    /** Upload file from InputStream **/
    suspend fun write(reader: InputStream, file: WorkerBackup_File, connexion: Int, canRetry: Boolean, reportProgress: (Long) -> Unit): Boolean { return false }

    /** Create folders if necessary **/
    suspend fun createDirectories(folderList: List<String>, connexion: Int)
    /** Delete a folders list **/
    suspend fun deleteDirectories(foldersList: List<String>, connexion: Int)

    /** Manage orphan file **/
    suspend fun manageOrphan(orphanFile: WorkerBackup_File, action: Int, orphansFolder: String?, connexion: Int)
    /** Create or remove .nomedia file (local) **/
    suspend fun manageNomedia(checked: Boolean) { }


    // -------------
    // Verifications
    // -------------

    /** Check if last modified date is correct **/
    fun dateIsOK(originalDate: Instant, newDate: Instant) = abs(Duration.between(originalDate, newDate).seconds) < 2     // Date is unreliable and can be 1 sec offset

    /** Check, for remote file corruption **/
    fun fileIsOK(originalSize: Long, newSize: Long) = newSize == originalSize

    /** Check if warning has been triggered **/
    fun hasWarning(): Boolean


    // ----------
    // Extensions
    // ----------

    /** Build an absolute path (with root folder) from a list (order matters) **/
    fun getAbsolutePath(folders: List<String>?, rootPath: String, addPrefix: Boolean): String {
        val path = mutableListOf<String>()

        if(rootPath.isNotBlank())
            path.add(rootPath)

        folders?.forEach { folder ->
            folder.trim('/').takeIf { it.isNotBlank() }?.let { path.add(it) }
        }

        return if(addPrefix)
            path.takeIf { it.isNotEmpty() }?.joinToString("/", "/") ?: "/"
        else
            path.takeIf { it.isNotEmpty() }?.joinToString("/") ?: ""
    }

    /** Remove root path from an absolute path **/
    fun String.toRelativePath(rootPath: String) = this.trim('/').removePrefix(rootPath) + "/"
}