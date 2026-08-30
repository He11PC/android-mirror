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
import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DefaultRequestCacheKeyProvider
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import com.thegrizzlylabs.sardineandroid.util.SardineUtil
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
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.xml.namespace.QName

class Backup_WebDav(
    private val backupID: Int,
    private val isSource: Boolean,
    private val originTxt: String,
    private val target: Backup_Target,
    private val accurateDateAuto: Boolean,
    private var accurateDate: Boolean,
    private val tmpDirectoryRequired: Boolean,
    private val reportFrequency: Long,
    private val log: Manager_Log
): Backup_Interface {

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO

    private val localeContext = App.getLocaleContext()

    private lateinit var httpClient: OkHttpClient
    private val webdav1: Sardine by lazy { OkHttpSardine(httpClient) }
    private val webdav2: Sardine by lazy { OkHttpSardine(httpClient) }
    private val webdav3: Sardine by lazy { OkHttpSardine(httpClient) }
    private val webdav4: Sardine by lazy { OkHttpSardine(httpClient) }
    private val webdav5: Sardine by lazy { OkHttpSardine(httpClient) }

    private lateinit var scheme: String
    private lateinit var server: String
    private val tmpDirectory by lazy { Paths.get(App.instance.filesDir.toString()+"/$backupID/tmp") }

    private var useDefaultExists = true

    private var warningTriggered = false

    private val progress by lazy { Backup_WebDav_Monitor(backupID, reportFrequency) }


    // --------------
    // Initialisation
    // --------------

    /** Check server availability **/
    override suspend fun checkServer() = withContext(dispatcherIO) {
        if(target.server.isNullOrBlank())
            throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), null)

        scheme = if(target.ssl == true) "https" else "http"
        server = target.server.cipherDecrypt()!!

        makeConnexion()
    }

    /** Check folder availability **/
    override suspend fun checkRootFolder() = withContext(dispatcherIO) {
        val rootPath = getAbsolutePath(null, target.path, false)
        val rootUrl = rootPath.toURL(false)

        // Create tmp directory to upload from inputStream if necessary
        if(tmpDirectoryRequired)
            try { Files.createDirectories(tmpDirectory) }
            catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_create_tmp), exp.message) }

        // Check if root folder exists
        try {
            if(!rootUrl.exists(webdav1)) {
                // Create missing folder only for Destination
                if(target.path.isBlank() || isSource)
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_existence), null)

                generateWarning(localeContext.getString(R.string.log_folder_destination_create), false, false)
                createDirectory(rootPath, webdav1)

                if (!rootUrl.exists(webdav1))
                    throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_existence), null)
            }
        }
        catch(exp: CriticalException) { throw exp }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_invalid), exp.message) }
    }

    /** Check available space **/
    override suspend fun checkFreeSpace(size: Long) = withContext(dispatcherIO) {
        try {
            val freeSpace = webdav1.getQuota(getAbsolutePath(null, target.path, false).toURL(false)).quotaAvailableBytes
            val freeSpaceTxt = ": "+freeSpace.sizeToReadable()
            if(freeSpace < size)
                throw CriticalException(null, localeContext.getString(R.string.error_quota_critical)+freeSpaceTxt, null)
            else if (freeSpace < size+size*0.25)
                generateWarning(localeContext.getString(R.string.error_quota_low)+freeSpaceTxt, false, true)
        }
        catch(exp: CriticalException) { throw exp }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_quota_unverifiable), false, false) }
    }


    // ----------------
    // Server connexion
    // ----------------

    /** Get correct connexion **/
    private suspend fun getConnexion(connexion: Int): Sardine {
        if (!::httpClient.isInitialized)
            makeConnexion()

        return when(connexion) {
            1 -> webdav1
            2 -> webdav2
            3 -> webdav3
            4 -> webdav4
            else -> webdav5
        }
    }

    /** Make connexion **/
    private suspend fun makeConnexion() = withContext(dispatcherIO) {
        try{
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

            // Some servers seem to not allow root url listing
            val checkUrl = if(target.path.isNotBlank()) {
                val cleanPath = target.path.trim('/')
                val rootSegment = if(cleanPath.contains(".php/", ignoreCase = true)) {
                    val phpIndex = cleanPath.indexOf(".php/", ignoreCase = true) + 5
                    val beforePhp = cleanPath.substring(0, phpIndex)
                    val firstDir = cleanPath.substring(phpIndex).substringBefore('/')

                    beforePhp + firstDir
                }
                else
                    cleanPath.substringBefore('/')

                rootSegment.toURL(false)
            }
            else
                "".toURL(false)

            require(webdav1.list(checkUrl, 0) != null)
        }
        catch(exp: Exception) {
            val errorMsg = exp.message.toString()
            when {
                "auth" in errorMsg.lowercase(Locale.getDefault()) -> throw CriticalException(originTxt, localeContext.getString(R.string.error_server_login), exp.message)
                "host" in errorMsg.lowercase(Locale.getDefault()) -> throw CriticalException(originTxt, localeContext.getString(R.string.error_server_reach), exp.message)
                "certification" in errorMsg.lowercase(Locale.getDefault()) -> throw CriticalException(originTxt, localeContext.getString(R.string.error_ssl_certificate), exp.message)
                else -> throw CriticalException(originTxt, localeContext.getString(R.string.error_server_connect), exp.message)
            }
        }
    }

    /** Save average write speed for next session and disconnects from the server **/
    override suspend fun disconnect() = withContext(dispatcherIO) {
        progress.saveWriteSpeed()

        try {
            if (::httpClient.isInitialized && httpClient.connectionPool.connectionCount() > 0)
                httpClient.apply {
                    dispatcher.executorService.shutdown()
                    connectionPool.evictAll()
                    cache?.close()
                }
        }
        catch (_: Exception) { generateWarning(localeContext.getString(R.string.error_server_disconnect), true, false) }
    }


    // ------------------
    // Get folder content
    // ------------------

    /** List files on remote folder **/
    override suspend fun getContent(recursive: Boolean, filesOnly: Boolean): MutableList<WorkerBackup_File> = withContext(dispatcherIO) {
        val maxDepth = if (recursive) -1 else 1
        val path = getAbsolutePath(null, target.path, false)

        val resources = try { webdav1.list(path.toURL(false), maxDepth) }
        catch(exp: Exception) {
            // Some servers seem to refuse recursive listing
            if(recursive && exp.message.toString().contains("403"))
                return@withContext listDirectoryRecursive(path, filesOnly)
            else
                throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": ${target.path}", exp.message)
        }

        val lstFiles = mutableListOf<WorkerBackup_File>()

        resources.forEach {
            val isDirectory = it.isDirectory
            val fileName = it.name
            val filePath = if(it.displayName.isNullOrEmpty() && it.path == "/${target.path}/")
                ""
            else
                it.path.removePrefix("/${target.path}").substringBeforeLast(fileName)

            val skip = filePath == "" || (isDirectory && filesOnly)
            if(!skip)
                lstFiles.add(WorkerBackup_File(filePath, fileName, it.modified.toInstant(), it.contentLength, isDirectory))
        }
        return@withContext lstFiles
    }

    /** Recursive files listing if embedded recursive fails **/
    private suspend fun listDirectoryRecursive(path: String, filesOnly: Boolean): MutableList<WorkerBackup_File> = withContext(dispatcherIO) {
        val resources = try { webdav1.list(path.toURL(false), 1).filterNot { it.path == "/$path/" } }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": ${target.path}", exp.message) }

        val filePath = path.toRelativePath(target.path)
        val lstFiles = mutableListOf<WorkerBackup_File>()

        resources.forEach {
            val isDirectory = it.isDirectory
            val fileName = it.name

            val skip = filePath == "" || (isDirectory && filesOnly)
            if(!skip)
                lstFiles.add(WorkerBackup_File(filePath, fileName, it.modified.toInstant(), it.contentLength, isDirectory))

            if(isDirectory)
                lstFiles.addAll(listDirectoryRecursive(getAbsolutePath(listOf(filePath, fileName), target.path, false), filesOnly))
        }

        return@withContext lstFiles
    }


    // ----------
    // Read/Write
    // ----------

    /** Get InputStream to download the file **/
    override suspend fun getReader(file: WorkerBackup_File, connexion: Int): InputStream = withContext(dispatcherIO) {
        val srcUrl = getAbsolutePath(listOf(file.path, file.name), target.path, false).toURL(true)

        return@withContext try { getConnexion(connexion).get(srcUrl) }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_read), exp.message) }
    }

    /** Upload file from local source **/
    override suspend fun write(srcRoot: String, file: WorkerBackup_File, connexion: Int, canRetry: Boolean, reportProgress: (Long) -> Unit): Boolean = withContext(dispatcherIO) {
        val webdav = getConnexion(connexion)
        val srcPath = Paths.get("$srcRoot${file.path}${file.name}")
        val destUrl = getAbsolutePath(listOf(file.path, file.name), target.path, false).toURL(true)

        val monitor = progress.startMonitor(file.size, connexion, reportProgress)
        val success = try { upload(webdav, srcPath, destUrl, file, canRetry) }
        catch(exp: Exception) { throw exp }
        finally { progress.stopMonitor(monitor, file.size, connexion) }

        return@withContext success
    }

    /** Upload file from InputStream **/
    override suspend fun write(reader: InputStream, file: WorkerBackup_File, connexion: Int, canRetry: Boolean, reportProgress: (Long) -> Unit): Boolean = withContext(dispatcherIO) {
        val webdav = getConnexion(connexion)
        val tmpPath = Paths.get("$tmpDirectory/$connexion.tmp")
        val destUrl = getAbsolutePath(listOf(file.path, file.name), target.path, false).toURL(true)

        // Sardine can't use InputStream => intermediate tmp file on device local memory required
        if(hasEnoughTmpSpace(file)) {
            val monitor = progress.startMonitor(file.size, connexion, reportProgress)
            val success = try { createTmpFile(reader, tmpPath, file, canRetry) && upload(webdav, tmpPath, destUrl, file, canRetry) }
            catch(exp: Exception) { throw exp }
            finally {
                deleteTmpFile(tmpPath)
                progress.stopMonitor(monitor, file.size, connexion)
            }
            return@withContext success
        }

        // Return success (true) if file is skipped (not enough free space for tmp file) to prevent retry
        return@withContext true
    }

    /** Upload file to WebDav server **/
    private fun upload(webdav: Sardine, srcPath: Path, destUrl: String, file: WorkerBackup_File, canRetry: Boolean): Boolean {
        try { webdav.put(destUrl, srcPath.toFile(), null) }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_copy), exp.message) }

        setDate(destUrl, file.last_modified, webdav)
        val transferResult = verifyTransfer(destUrl, file.last_modified, file.size, webdav)

        return if(!transferResult.sizeIsOK) {
            alertWrongFileSize(file.name, canRetry)
            false
        }
        else {
            manageDate(transferResult.dateIsOK)
            true
        }
    }

    /** Log/warn in case of wrong file size **/
    private fun alertWrongFileSize(fileName: String, canRetry: Boolean) {
        if(canRetry)
            log.append(true,"<b>["+ localeContext.getString(R.string.log_note)+"]</b> ${fileName}: "+ localeContext.getString(R.string.error_file_corrupted))
        else
            generateWarning("${fileName}: "+ localeContext.getString(R.string.error_file_corrupted_final), false, true)
    }


    // --------------
    // Temporary file
    // --------------

    /** Check available space on internal memory for tmp file **/
    private fun hasEnoughTmpSpace(file: WorkerBackup_File): Boolean {
        try {
            val freeSpace = StatFs(App.instance.filesDir.toString()).availableBytes
            if(freeSpace < file.size+file.size*0.1) {
                generateWarning("${file.name} (${file.size.sizeToReadable()}): " + localeContext.getString(R.string.error_file_tmp_space) + " (${freeSpace.sizeToReadable()}). " + localeContext.getString(R.string.error_file_tmp_skipped), false, true)
                return false
            }
        }
        catch(_: Exception) { }

        // Proceed anyway if unable to check
        return true
    }

    /** Create tmp file on device internal memory **/
    private fun createTmpFile(reader: InputStream, tmpPath: Path, file: WorkerBackup_File, canRetry: Boolean): Boolean {
        try {
            Channels.newChannel(reader).use { inputChannel ->
                val buffer = ByteBuffer.allocate(16*1024)
                FileOutputStream(tmpPath.toFile()).channel.use { outputChannel ->
                    while(inputChannel.read(buffer) > 0) {
                        buffer.flip()
                        outputChannel.write(buffer)
                        buffer.clear()
                    }
                }
            }

            return tmpFileIsOK(tmpPath, file, canRetry)
        }
        catch(exp: Exception) { throw CriticalException(null, "${file.name}: "+ localeContext.getString(R.string.error_file_tmp), exp.message) }
    }

    /** Check tmp file **/
    private fun tmpFileIsOK(tmpPath: Path, file: WorkerBackup_File, canRetry: Boolean): Boolean {
        val tmpFileSize = try { Files.size(tmpPath) }
        catch (_: Exception) { 0 }

        return if(tmpFileSize != file.size) {
            alertWrongFileSize(file.name, canRetry)
            false
        }
        else true
    }

    /** Delete tmp file **/
    private fun deleteTmpFile(tmpPath: Path) {
        try { Files.deleteIfExists(tmpPath) }
        catch(_: Exception) { }
    }


    // ------------
    // Verification
    // ------------

    /** Check if file transfer went wrong **/
    private fun verifyTransfer(file: String, date: Instant, size: Long, webdav: Sardine): WorkerBackup_TransferResult {
        val fileInfo = try { webdav.list(file)[0] }
        catch (_: Exception) { return WorkerBackup_TransferResult(false, false) }

        return WorkerBackup_TransferResult(dateIsOK(date, fileInfo.modified.toInstant()), fileIsOK(size,fileInfo.contentLength))
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

    /** Change last modified date on remote server file (usually prohibited by servers) **/
    private fun setDate(fileUrl: String, date: Instant, webdav: Sardine) {
        val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"))
        val props: HashMap<QName, String> = HashMap()
        props[SardineUtil.createQNameWithDefaultNamespace("getlastmodified")] = dtf.format(date)

        try { webdav.patch(fileUrl, props) }
        catch (_: Exception) { }
    }


    // ----------------------
    // Directories management
    // ----------------------

    /** Create folders on remote server if necessary **/
    override suspend fun createDirectories(folderList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        val webdav = getConnexion(connexion)
        folderList.filter { it.isNotBlank() }.forEach { createDirectory(getAbsolutePath(listOf(it), target.path, false), webdav) }
    }

    /** Create a folder on remote server if necessary **/
    private fun createDirectory(path: String, webdav: Sardine) {
        val url = path.toURL(false)
        try {
            require(path.isNotBlank())

            if(!url.exists(webdav)) {
                val parent = path.parentDirectory()
                val parentUrl = parent.toURL(false)
                if(!parentUrl.exists(webdav)) {
                    require(path != parent)
                    createDirectory(parent, webdav)
                }

                webdav.createDirectory(url)
            }
        }
        catch(exp: Exception) { throw CriticalException(null, localeContext.getString(R.string.error_folder_create)+": $path", exp.message) }
    }

    /** Delete a folders list from remote server **/
    override suspend fun deleteDirectories(foldersList: List<String>, connexion: Int) = withContext(dispatcherIO) {
        val webdav = getConnexion(connexion)
        foldersList.filter { it.isNotBlank() }.forEach { deleteDirectory(getAbsolutePath(listOf(it), target.path, false), webdav) }
    }

    /** Delete a folder from remote server **/
    private fun deleteDirectory(path: String, webdav: Sardine) {
        val url = path.toURL(false)
        try {
            if(url.exists(webdav) && path.isEmptyDirectory(webdav))
                webdav.delete(url)
        }
        catch(_: Exception) { generateWarning(localeContext.getString(R.string.error_folder_delete)+": $path", false, true) }
    }


    // ----------------
    // Files management
    // ----------------

    /** Manage orphan files on remote server **/
    override suspend fun manageOrphan(orphanFile: WorkerBackup_File, action: Int, orphansFolder: String?, connexion: Int) = withContext(dispatcherIO) {
        val webdav = getConnexion(connexion)

        if (action == 1 && orphansFolder != null)
            moveOrphan(orphansFolder, orphanFile.path, orphanFile.name, webdav)
        else if (action >= 2)
            deleteOrphan(orphanFile.path, orphanFile.name, webdav)
    }

    /** Move file on remote server **/
    private fun moveOrphan(orphansFolder: String, orphanPath: String, orphanName: String, webdav: Sardine) {
        val srcFile = getAbsolutePath(listOf(orphanPath, orphanName), target.path, false).toURL(true)
        val destFile = getAbsolutePath(listOf(orphansFolder, orphanPath, orphanName), target.path, false).toURL(true)

        try { webdav.move(srcFile, destFile) }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_move), false, true) }
    }

    /** Delete a file from remote server **/
    private fun deleteOrphan(orphanPath: String, orphanName: String, webdav: Sardine) {
        val orphanFile = getAbsolutePath(listOf(orphanPath, orphanName), target.path, false).toURL(true)
        try {
            if(orphanFile.exists(webdav))
                webdav.delete(orphanFile)
        }
        catch(_: Exception) { generateWarning("$orphanName: "+localeContext.getString(R.string.error_file_delete), false, true) }
    }


    // ------------------
    // Sardine extensions
    // ------------------

    /** Check if a resource exists on WebDav Server (Sardine.exists seems to fail on some servers) **/
    private fun String.exists(webdav: Sardine): Boolean {
        return if(useDefaultExists)
            try { webdav.exists(this) }
            catch(_: Exception) {
                useDefaultExists = false
                this.exists(webdav)
            }
        else
            try { webdav.list(this, 0) != null }
            catch(_: Exception) { false }
    }

    /** Check if a directory on remote server contains data **/
    private fun String.isEmptyDirectory(webdav: Sardine): Boolean {
        return try { webdav.list(this.toURL(false), 1, false).size == 1 }
        catch(exp: Exception) { throw CriticalException(originTxt, localeContext.getString(R.string.error_folder_list)+": $this", exp.message) }
    }

    /** Create an http url from a path **/
    private fun String.toURL(isFile: Boolean): String {
        return HttpUrl.Builder().apply {
            scheme(scheme)
            host(server)
            target.port?.let { port(it) }
            if(this@toURL.isNotBlank())
                addPathSegments(this@toURL)
            if(!isFile)
                addPathSegment("")
            build()
        }.toString()
    }

    /** Get parent directory **/
    private fun String.parentDirectory(): String {
        val path =  this.trim('/')

        return if(path.contains("/"))
            path.substringBeforeLast('/')
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
