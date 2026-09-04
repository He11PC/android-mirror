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

package fr.hellpc.mirror.workers

import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.room.Backup_Data
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.managers.Manager_ForegroundInfo
import fr.hellpc.mirror.managers.Manager_Log
import fr.hellpc.mirror.managers.Manager_Settings
import fr.hellpc.mirror.managers.Manager_Workers
import fr.hellpc.mirror.utilities.CriticalException
import fr.hellpc.mirror.utilities.SuccessException
import fr.hellpc.mirror.utilities.Utility_Locale.setAppLocale
import fr.hellpc.mirror.data.WorkerBackup_File
import fr.hellpc.mirror.utilities.Utility_Conversion.sizeToReadable
import fr.hellpc.mirror.workers.backup.Backup_FTP
import fr.hellpc.mirror.workers.backup.Backup_Interface
import fr.hellpc.mirror.workers.backup.Backup_Local
import fr.hellpc.mirror.workers.backup.Backup_NFS
import fr.hellpc.mirror.workers.backup.Backup_Progress
import fr.hellpc.mirror.workers.backup.Backup_SFTP
import fr.hellpc.mirror.workers.backup.Backup_SMB
import fr.hellpc.mirror.workers.backup.Backup_WebDav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.net.URLConnection.guessContentTypeFromName
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.Long.Companion.MAX_VALUE
import kotlin.math.abs
import kotlin.math.round


class Worker_Backup(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {

    private val dispatcherDefault = Dispatchers.Default
    private val scopeDefault = CoroutineScope(Job() + dispatcherDefault)
    private val scopeIO = CoroutineScope(Job() + Dispatchers.IO)

    // -------------------------------------

    private val repository by lazy { (applicationContext as App).repositoryWorkerBackup }

    private val appSettings by lazy { Manager_Settings() }
    private val foregroundInfo by lazy { Manager_ForegroundInfo() }
    private val log by lazy { Manager_Log() }
    private val progress by lazy { Backup_Progress() }

    // -------------------------------------

    private val backupID by lazy { inputData.getInt("ID_BACKUP", -1) }
    private val backupScheduled by lazy { inputData.getBoolean("SCHEDULED", false) }
    private val localeContext by lazy { applicationContext.setAppLocale() }

    // -------------------------------------

    private lateinit var targetSrc: Backup_Interface
    private lateinit var targetDest: Backup_Interface

    private lateinit var backupData: Backup_Data
    private lateinit var workerResult: String

    private var warningTriggered = false

    // -------------------------------------

    private val backupPool by lazy { mutableListOf<WorkerBackup_File>() }

    private var numberFiles = 0
    private var numberOrphans = 0
    private var numberOrphansAfterBackup = 0
    private var sizeOrphansAfterBackup = 0L

    // -------------------------------------

    private val srcTxt by lazy { localeContext.getString(R.string.log_source) }
    private val destTxt by lazy { localeContext.getString(R.string.log_destination) }

    private val colorBlue = "<font color=blue>"
    private val colorGreen = "<font color=green>"
    private val colorOrange = "<font color=orange>"
    private val colorRed = "<font color=red>"

    // -------------------------------------

    override suspend fun doWork(): Result {
        return withContext(dispatcherDefault) {
            try {

                // ---------
                // Variables
                // ---------

                val srcFolders by lazy { mutableListOf<String>() }
                val currentDestFolders by lazy { mutableListOf<String>() }
                val currentDestFilesOrphan by lazy { mutableListOf<WorkerBackup_File>() }
                val currentDestFoldersOrphan by lazy { mutableListOf<String>() }
                val newFilesOrphan by lazy { mutableListOf<WorkerBackup_File>() }
                val newFilesToCopy by lazy { mutableListOf<WorkerBackup_File>() }
                val newFilesOrphanObsolete by lazy { mutableListOf<WorkerBackup_File>() }
                val finalDestFilesOrphan by lazy { mutableListOf<WorkerBackup_File>() }
                val finalDestFoldersOrphan by lazy { mutableListOf<String>() }
                val foldersToDelete by lazy { mutableListOf<String>() }
                val foldersToCreate by lazy { mutableListOf<String>() }


                // ---------------------
                // Backup initialisation
                // ---------------------

                if(backupScheduled)
                    log.initialise(localeContext.getString(R.string.log_backup_start_scheduled))
                else
                    log.initialise(localeContext.getString(R.string.log_backup_start))

                launch {
                    val progressDetail1 = "[$srcTxt] " + localeContext.getString(R.string.log_backup_initialisation)
                    val progressDetail2 = "[$destTxt] " + localeContext.getString(R.string.log_backup_initialisation)
                    repository.setBackupInitialise(backupID, "init", progressDetail1, progressDetail2)
                    updateDailyStats(true)
                }

                backupData = repository.loadBackup(backupID)

                log.append(false, localeContext.getString(R.string.log_backup_initialisation))

                // WiFi
                checkWifi()


                // -------------------
                // Get folders content
                // -------------------

                // Source & Destination
                val asyncSrcContent = async {
                    initialise(true, backupData.source)
                    applyFilters(getContent(true))
                }

                initialise(false, backupData.destination)
                val currentDestFiles = getContent(false).toMutableList()

                // Extract Destination content (folders, files, orphans)
                val orphansFolder = if(backupData.options.orph_folder != null && backupData.options.orph_action == 1) "/" + backupData.options.orph_folder else null
                val currentDestFilesIterator = currentDestFiles.iterator()
                while(currentDestFilesIterator.hasNext()) {
                    val file = currentDestFilesIterator.next()
                    val fullPath = file.path+file.name.substringBeforeLast('/')
                    if(orphansFolder != null && fullPath.startsWith(orphansFolder)) {
                        if(file.isDirectory)
                            currentDestFoldersOrphan.add(fullPath)
                        else
                            currentDestFilesOrphan.add(file)
                        currentDestFilesIterator.remove()
                    }
                    else if(file.isDirectory) {
                        currentDestFolders.add(fullPath)
                        currentDestFilesIterator.remove()
                    }
                }

                // Async copy of current Destination files for later use
                val jobCloneCurrentDestFiles = launch { newFilesOrphan.addAll(currentDestFiles) }

                // Get Source files and extract folders
                val srcFiles = asyncSrcContent.await()
                val jobExtractSrcFolders = launch {
                    srcFolders.addAll(srcFiles.map { it.path.substringBeforeLast('/') }.distinctBy { it }.filter { it != "" })
                    // Warning if orphans folder name is present in Source
                    if(backupData.options.orph_action == 1 && srcFolders.find { it.startsWith("/" + backupData.options.orph_folder) } != null)
                        generateWarning(localeContext.getString(R.string.error_orphan_folder_in_use))
                }


                // ---------------------------------------
                // Find files/orphans to backup/delete/etc
                // ---------------------------------------

                val jobLogCheckingFiles = launch {
                    log.append(true, "")
                    updateProgressDetail(localeContext.getString(R.string.log_checking_backup), 1)
                    updateProgressDetail(null, 2)

                    if(!backupData.options.dateComparison_Strict)
                        log.append(false, localeContext.getString(R.string.log_checking_backup).replace("…", localeContext.getString(R.string.log_flexible_mode)))
                    else
                        log.append(false, localeContext.getString(R.string.log_checking_backup))
                }

                jobCloneCurrentDestFiles.join()

                if(currentDestFiles.isEmpty())
                    newFilesToCopy.addAll(srcFiles)
                else {
                    val isStrictDate = backupData.options.dateComparison_Strict
                    for(src in srcFiles) {
                        val jobExtractFilesAndOrphans = launch {
                            var toCopy = true
                            val newFilesOrphanIterator = newFilesOrphan.iterator()
                            while(newFilesOrphanIterator.hasNext()) {
                                val dest = newFilesOrphanIterator.next()
                                if(src.path == dest.path && src.name == dest.name) {
                                    if(src.size == dest.size) {
                                        val dateOK = if(isStrictDate)
                                            abs(Duration.between(src.last_modified, dest.last_modified).seconds) < 2 // Date is unreliable and can be 1 sec offset
                                        else
                                            src.last_modified.isBefore(dest.last_modified.plusSeconds(2))

                                        if(dateOK)
                                            toCopy = false
                                    }

                                    newFilesOrphanIterator.remove()
                                    break
                                }
                            }
                            if(toCopy)
                                newFilesToCopy.add(src)
                        }

                        if(backupData.options.orph_action == 1) {
                            val obsoleteOrphan = currentDestFilesOrphan.find { "/"+backupData.options.orph_folder+src.path == it.path && src.name == it.name }
                            if(obsoleteOrphan != null)
                                newFilesOrphanObsolete.add(obsoleteOrphan)
                        }

                        jobExtractFilesAndOrphans.join()
                    }
                }


                // ------------------------------------------
                // Find orphan files and folders after backup
                // ------------------------------------------

                finalDestFilesOrphan.addAll(currentDestFilesOrphan)
                val orphanRoot = if(backupData.options.orph_folder != null && backupData.options.orph_action == 1)
                    "/"+backupData.options.orph_folder
                else
                    ""
                finalDestFilesOrphan.addAll(newFilesOrphan.map { WorkerBackup_File(orphanRoot+it.path, it.name, it.last_modified, it.size, it.isDirectory) })
                finalDestFilesOrphan.removeAll(newFilesOrphanObsolete.toSet())

                val jobOrphansAfterBackup = launch {
                    if(backupData.options.orph_action == 1) {
                        numberOrphansAfterBackup = finalDestFilesOrphan.count()
                        sizeOrphansAfterBackup = finalDestFilesOrphan.sumOf { it.size }
                    }
                }

                if(backupData.options.orph_action != 2)
                    finalDestFoldersOrphan.addAll(finalDestFilesOrphan.map { it.path.substringBeforeLast('/') }.distinctBy { it })


                // -----------------------------
                // Find folders to create/remove
                // -----------------------------

                jobExtractSrcFolders.join()

                val jobExtractFoldersToDelete = launch {
                    if(backupData.options.orph_action > 0) {
                        foldersToDelete.addAll(currentDestFolders)
                        foldersToDelete.removeAll(srcFolders.toSet())
                    }

                    if(backupData.options.orph_action == 2)
                        foldersToDelete.addAll(currentDestFoldersOrphan)
                    else {
                        foldersToDelete.addAll(currentDestFoldersOrphan)
                        foldersToDelete.removeAll(finalDestFoldersOrphan.toSet())
                    }

                    // Don't delete folders with no file but with subfolders
                    val foldersToDeleteIterator = foldersToDelete.iterator()
                    while(foldersToDeleteIterator.hasNext()) {
                        val folder = foldersToDeleteIterator.next()
                        val prefix = "$folder/"
                        val toKeep = srcFolders.any { it.startsWith(prefix) } || finalDestFoldersOrphan.any { it.startsWith(prefix) }
                        if(toKeep)
                            foldersToDeleteIterator.remove()
                    }

                    foldersToDelete.sortByDescending { it }
                }

                foldersToCreate.addAll(srcFolders)
                foldersToCreate.removeAll(currentDestFolders.toSet())
                if(backupData.options.orph_action == 1) {
                    foldersToCreate.addAll(finalDestFoldersOrphan)
                    foldersToCreate.removeAll(currentDestFoldersOrphan.toSet())
                }
                foldersToCreate.sortBy { it }

                jobExtractFoldersToDelete.join()


                // ----------
                // Log result
                // ----------

                jobLogCheckingFiles.join()

                // Log files to backup
                var sizeFiles = 0L
                val jobSizeToCopy = launch {
                    numberFiles = newFilesToCopy.count()
                    sizeFiles = newFilesToCopy.sumOf { it.size }
                    val sizeToCopyTxt = if(sizeFiles != 0L)
                        " ("+sizeFiles.sizeToReadable()+")"
                    else
                        ""
                    log.append(false, localeContext.getString(R.string.log_to_backup)+" "+numberFiles+sizeToCopyTxt)
                }

                // Log orphans
                var sizeOrphans = 0L
                val jobSizeOrphans = launch {
                    numberOrphans = newFilesOrphan.count()
                    sizeOrphans = newFilesOrphan.sumOf { it.size }
                    val sizeOrphansTxt = if(sizeOrphans != 0L)
                        " ("+sizeOrphans.sizeToReadable()+")"
                    else
                        ""
                    val logOrphansAction = when (backupData.options.orph_action) {
                        0 -> localeContext.getString(R.string.log_orphans_keep)
                        1 -> localeContext.getString(R.string.log_orphans_move)
                        else -> localeContext.getString(R.string.log_orphans_delete)
                    }
                    log.append(false, "$logOrphansAction $numberOrphans$sizeOrphansTxt")
                }

                // Log obsolete orphans
                val sizeObsoletes = newFilesOrphanObsolete.sumOf { it.size }
                if(sizeObsoletes > 0)
                    log.append(false, localeContext.getString(R.string.log_obsolete)+" "+newFilesOrphanObsolete.count()+" ("+sizeObsoletes.sizeToReadable()+")")

                jobSizeToCopy.join()
                jobSizeOrphans.join()


                // -------------------------------
                // If nothing to do => stop worker
                // -------------------------------

                if (numberFiles == 0 && (numberOrphans == 0 || backupData.options.orph_action == 0) && foldersToDelete.isEmpty() && newFilesOrphanObsolete.isEmpty()) {
                    manageNomedia()
                    jobOrphansAfterBackup.join()
                    throw SuccessException()    // Exception required to stop backup
                }


                // -----------------------------
                // Initialize Backup progression
                // -----------------------------

                launch {
                    updateProgressDetail(null, 1)
                    progress.initialize(sizeFiles, sizeObsoletes, sizeOrphans, backupData.options.orph_action > 0)
                }


                // ---------------------------------------
                // Checking available space on destination
                // ---------------------------------------

                if(sizeFiles > 0)
                    checkFreeSpace(sizeFiles)


                // ------------
                // Backup start
                // ------------

                launch {
                    repository.setBackupStatus(backupID, "copy")
                    setForegroundNotification()
                }

                log.append(true, "")
                log.append(false, localeContext.getString(R.string.log_files_copy_started))

                val jobManageObsoleteOrphans = launch { manageOrphans(newFilesOrphanObsolete, true, true) }
                val jobManageNomedia = launch { manageNomedia() }
                manageDirectories(foldersToCreate, true)
                jobManageObsoleteOrphans.join()

                launch { progress.copyStarted() }
                val jobManageOrphans = launch { manageOrphans(newFilesOrphan, false, newFilesToCopy.count() < 9) }
                manageBackup(newFilesToCopy)
                jobManageOrphans.join()


                // --------
                // Cleaning
                // --------

                manageDirectories(foldersToDelete, false)
                jobManageNomedia.join()
                jobOrphansAfterBackup.join()


                // --------------
                // Backup success
                // --------------
                Result.success()
            }


            // ------
            // Errors
            // ------

            catch(exp: Exception) {
                workerResult = when(exp) {
                    is SuccessException -> checkWarning()
                    is CancellationException -> {
                        if(exp.cause is CriticalException)
                            "failure"
                        else
                            "cancel"
                    }
                    else -> "failure"
                }

                if(workerResult == "failure") {
                    if(exp is CriticalException) {
                        val msg = if(appSettings.getLogsNativeErrors() && exp.error != null)
                            exp.error
                        else
                            exp.message

                        if(exp.origin != null)
                            log.append(true, "$colorRed<b>[" + localeContext.getString(R.string.log_error) + "</b> <i>${exp.origin}</i><b>]</b> " + "$msg</font>")
                        else
                            log.append(true, "$colorRed<b>[" + localeContext.getString(R.string.log_error) + "]</b> " + "$msg</font>")
                    }
                    else
                        log.append(true, "$colorRed<b>[" + localeContext.getString(R.string.log_error) + "]</b> " + exp.message + "</font>")
                }

                if(exp is SuccessException)
                    Result.success()
                else
                    Result.failure()
            }


            // ---------
            // Finishing
            // ---------

            finally {
                withContext(NonCancellable) {
                    // Disconnect from server
                    disconnect()

                    if(!::workerResult.isInitialized)
                        workerResult = checkWarning()

                    // Log result
                    val resultText = when (workerResult) {
                        "success" -> "$colorGreen<b>" + localeContext.getString(R.string.log_backup_success) + "</b></font>"
                        "warning" -> "$colorOrange<b>" + localeContext.getString(R.string.log_backup_warning) + "</b></font>"
                        "cancel" -> "$colorOrange<b>" + localeContext.getString(R.string.log_backup_canceled) + "</b></font>"
                        else -> "$colorRed<b>" + localeContext.getString(R.string.error_backup_failed) + "</b></font>"
                    }
                    log.append(true, "")
                    log.append(false, resultText)

                    // Orphans information
                    if((workerResult == "success" || workerResult == "warning") && backupData.options.orph_action == 1 && numberOrphansAfterBackup > 0) {
                        log.append(true, "")
                        log.append(true, localeContext.getString(R.string.log_orphans_after_backup)+ " $numberOrphansAfterBackup"+" ("+sizeOrphansAfterBackup.sizeToReadable()+")")
                    }

                    // Backup duration
                    log.append(true, "")
                    log.append(true, "<i>"+localeContext.getString(R.string.log_backup_duration)+" "+log.measureElapsedTime(true, true)+"</i>")

                    // Update result, reset status & progress
                    if(workerResult == "success" || workerResult == "warning")
                        repository.setBackupResult(backupID, "idle", workerResult, System.currentTimeMillis(), progress.statFilesCount, progress.statFilesBytes, progress.statOrphansCount, progress.statOrphansBytes)
                    else
                        repository.setBackupResult(backupID, "idle", workerResult, progress.statFilesCount, progress.statFilesBytes, progress.statOrphansCount, progress.statOrphansBytes)

                    // Schedule next backup
                    scheduleNextBackup(repository.getLastBackupDate(backupID))

                    // Write backup log file
                    log.write(backupID)

                    // Notifications
                    updateDailyStats(false)
                    scheduleNotification()
                    foregroundInfo.cancelNotification()
                }
            }
        }
    }

    /** Disconnect from remote servers **/
    private suspend fun disconnect() {
        if(::targetSrc.isInitialized)
            targetSrc.disconnect()
        if(::targetDest.isInitialized)
            targetDest.disconnect()
    }


    // --------
    // Warnings
    // --------

    /** Create a warning in log file **/
    private fun generateWarning(message: String) {
        log.append(true,"$colorOrange<b>[" + localeContext.getString(R.string.log_warning) + "]</b> $message</font>")
        warningTriggered = true
    }

    /** Check if warning has been triggered **/
    private fun checkWarning(): String {
        val srcWarning = targetSrc.hasWarning()
        val destWarning = targetDest.hasWarning()

        return if(srcWarning || destWarning || warningTriggered)
            "warning"
        else
            "success"
    }


    // -------------
    // Verifications
    // -------------

    /** Check wifi state and cancel worker if necessary **/
    private fun checkWifi() {
        if(!backupData.options.const_wifi)
            return
        else {
            val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if(capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                return
            else {
                generateWarning(localeContext.getString(R.string.error_wifi))
                throw CancellationException()
            }
        }
    }

    /** Check folder existence and permissions if possible **/
    private suspend fun initialise(isSource: Boolean, target: Backup_Target) {
        val reportFrequency = 250L // Milliseconds between ongoing copy reports

        if(isSource) {
            targetSrc = when(target.protocol) {
                "LOCAL" -> Backup_Local(backupID, isSource, srcTxt, target, backupData.options.dateComparison_Auto, backupData.options.dateComparison_Strict, reportFrequency, log)
                "NFS" -> Backup_NFS(backupID, isSource, srcTxt, target, backupData.options.dateComparison_Auto, backupData.options.dateComparison_Strict, reportFrequency, log)
                "SMB" -> Backup_SMB(backupID, isSource, srcTxt, target, backupData.options.dateComparison_Auto, backupData.options.dateComparison_Strict, reportFrequency, log)
                "FTP" -> Backup_FTP(backupID, isSource, srcTxt, target, backupData.options.dateComparison_Auto, backupData.options.dateComparison_Strict, reportFrequency, log)
                "SFTP" -> Backup_SFTP(backupID, isSource, srcTxt, target, backupData.options.dateComparison_Auto, backupData.options.dateComparison_Strict, reportFrequency, log)
                "WDAV" -> Backup_WebDav(backupID, isSource, srcTxt, target, backupData.options.dateComparison_Auto, backupData.options.dateComparison_Strict, false, reportFrequency, log)
                else -> throw CriticalException(srcTxt, localeContext.getString(R.string.error_target), null)
            }
            targetSrc.initialise()
        }
        else {
            targetDest = when(target.protocol) {
                "LOCAL" -> Backup_Local(backupID, isSource, destTxt, target, backupData.options.dateComparison_Auto, backupData.options.dateComparison_Strict, reportFrequency, log)
                "NFS" -> Backup_NFS(backupID, isSource, destTxt, target, backupData.options.dateComparison_Auto, backupData.options.dateComparison_Strict, reportFrequency, log)
                "SMB" -> Backup_SMB(backupID, isSource, destTxt, target, backupData.options.dateComparison_Auto, backupData.options.dateComparison_Strict, reportFrequency, log)
                "FTP" -> Backup_FTP(backupID, isSource, destTxt, target, backupData.options.dateComparison_Auto, backupData.options.dateComparison_Strict, reportFrequency, log)
                "SFTP" -> Backup_SFTP(backupID, isSource, destTxt, target, backupData.options.dateComparison_Auto, backupData.options.dateComparison_Strict, reportFrequency, log)
                "WDAV" -> Backup_WebDav(backupID, isSource, destTxt, target, backupData.options.dateComparison_Auto, backupData.options.dateComparison_Strict, backupData.source.protocol != "LOCAL", reportFrequency, log)
                else -> throw CriticalException(destTxt, localeContext.getString(R.string.error_target), null)
            }
            targetDest.initialise()
        }
    }

    /** Check available free space/quota if possible **/
    private suspend fun checkFreeSpace(size: Long) {
        targetDest.checkFreeSpace(size)
    }


    // ------------------
    // Get folder content
    // ------------------

    /** List files on local/remote folder **/
    private suspend fun getContent(isSource: Boolean): List<WorkerBackup_File> = withContext(dispatcherDefault) {
        val target = if(isSource) srcTxt else destTxt
        val position = if(isSource) 1 else 2

        updateProgressDetail("[$target] " + localeContext.getString(R.string.log_searching_files), position)
        log.append(false, "$colorBlue<i>$target</i></font> " + localeContext.getString(R.string.log_searching_files))

        val fileList = if(isSource)
            targetSrc.getContent(backupData.options.recursive, true)
        else
            targetDest.getContent(true, false)

        val numberFiles = fileList.count { !it.isDirectory }
        val sizeFiles = fileList.sumOf { it.size }

        var txt = localeContext.getString(R.string.log_found) + " $numberFiles"
        if(sizeFiles > 0L)
            txt += " (" + sizeFiles.sizeToReadable() + ")"
        updateProgressDetail("[$target] $txt", position)
        log.append(false, "$colorBlue<i>$target</i></font> $txt")

        return@withContext fileList
    }


    // -------
    // Filters
    // -------

    /** Remove files from list accordingly to user selected options **/
    private suspend fun applyFilters(backupFileList: List<WorkerBackup_File>): List<WorkerBackup_File> = withContext(dispatcherDefault) {
        if(backupData.options.flt_images && backupData.options.flt_audio && backupData.options.flt_videos && backupData.options.flt_documents && backupData.options.flt_others
            && backupData.options.flt_minSize == null && backupData.options.flt_maxSize == null && backupData.options.flt_blackList.isNullOrBlank())
            return@withContext backupFileList

        updateProgressDetail("[$srcTxt] " + localeContext.getString(R.string.log_excluding), 1)
        log.append(false, "$colorBlue<i>$srcTxt</i></font> " + localeContext.getString(R.string.log_excluding))

        val blackListMatchers: List<PathMatcher> = backupData.options.flt_blackList
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.map { rule ->
                try { FileSystems.getDefault().getPathMatcher("glob:$rule") }
                catch (exp: Exception) { throw CriticalException(srcTxt, localeContext.getString(R.string.error_blacklist), exp.message) }
            }
            ?.toList() ?: emptyList()

        val filesResult = mutableListOf<WorkerBackup_File>()
        for(file in backupFileList) {
            if(file.isDirectory)
                filesResult.add(file)
            else if(mimeIsOK(file.name) && sizeIsOK(file.size) && !isBlackListed("${file.path}${file.name}", blackListMatchers))
                filesResult.add(file)
        }

        val numberFiles = filesResult.count { !it.isDirectory }
        val sizeFiles = filesResult.sumOf { it.size }

        var txt =  localeContext.getString(R.string.log_remaining) + " $numberFiles"
        if(sizeFiles > 0L)
            txt += " (" + sizeFiles.sizeToReadable() + ")"
        updateProgressDetail("[$srcTxt] $txt", 1)
        log.append(false, "$colorBlue<i>$srcTxt</i></font> $txt")

        return@withContext filesResult
    }

    /** Check if mime type is compliant to user selected options **/
    private fun mimeIsOK(name: String): Boolean {
        val mime = guessContentTypeFromName(name)

        if(mime.isNullOrBlank())
            return backupData.options.flt_others

        return ((mime.contains("image") && backupData.options.flt_images)
                || (mime.contains("audio") && backupData.options.flt_audio)
                || (mime.contains("video") && backupData.options.flt_videos)
                || (mime.contains(Regex("text|document|pdf|oxps")) && backupData.options.flt_documents)
                || (!mime.contains(Regex("image|audio|video|text|document|pdf|oxps")) && backupData.options.flt_others))
    }

    /** Check if file size is compliant to user selected options **/
    private fun sizeIsOK(size: Long) = size in (backupData.options.flt_minSize ?: 0L)..(backupData.options.flt_maxSize ?: MAX_VALUE)

    /** Check if folder is part of user black list **/
    private fun isBlackListed(pathString: String, blackListMatchers: List<PathMatcher>): Boolean {
        if(blackListMatchers.isEmpty())
            return false

        val path = Paths.get(pathString)
        val fileName = path.fileName

        for(matcher in blackListMatchers) {
            if(matcher.matches(path) || (fileName != null && matcher.matches(fileName)))
                return true
        }
        return false
    }


    // ----------------------
    // Directories management
    // ----------------------

    /** Manage directories **/
    private suspend fun manageDirectories(folderList: List<String>, create: Boolean) = withContext(dispatcherDefault) {
        val foldersCount = folderList.count()

        if(foldersCount == 0)
            return@withContext

        val jobProgressDetail = launch {
            if(create) {
                updateProgressDetail(localeContext.getString(R.string.log_folders_create)+" ($foldersCount)", 1)
                log.append(false, localeContext.getString(R.string.log_folders_create)+" ($foldersCount)")
            }
            else {
                updateProgressDetail(localeContext.getString(R.string.log_empty_folders_delete)+" ($foldersCount)", 1)
                log.append(false, localeContext.getString(R.string.log_empty_folders_delete)+" ($foldersCount)")
            }
        }

        if(create)
            createDirectories(folderList)
        else
            deleteDirectories(folderList)

        jobProgressDetail.join()
        updateProgressDetail(null, 1)
    }

    /** Create directories if necessary **/
    private suspend fun createDirectories(folderList: List<String>) {
        targetDest.createDirectories(folderList, 1)
    }

    /** Delete unused/empty folders **/
    private suspend fun deleteDirectories(folderList: List<String>) {
        targetDest.deleteDirectories(folderList, 1)
    }


    // -----------------
    // Backup management
    // -----------------

    /** Proceed to files backup **/
    private suspend fun manageBackup(backupFileList: List<WorkerBackup_File>) = withContext(dispatcherDefault) {
        val filesCount = backupFileList.count()

        if(filesCount == 0)
            return@withContext

        backupPool.addAll(backupFileList)

        when (filesCount) {
            in 1..3 -> doBackup(1)
            in 4..8 -> {
                val jobBackup = launch { doBackup(1) }
                doBackup(2)
                jobBackup.join()
            }
            else -> {
                val jobBackup1 = launch { doBackup(1) }
                val jobBackup2 = launch { doBackup(2) }
                doBackup(3)
                jobBackup1.join()
                jobBackup2.join()
            }
        }

        backupPool.clear()
    }

    /** Get file to backup from pool **/
    @Synchronized private fun getFileToBackup(): WorkerBackup_File {
        val file = backupPool[0]
        backupPool.removeAt(0)
        return file
    }

    /** Proceed to files backup **/
    private suspend fun doBackup(connexion: Int) = withContext(dispatcherDefault) {
        do {
            val file = getFileToBackup()

            launch {
                updateProgressDetail(file.name+" ("+file.size.sizeToReadable()+")", connexion)
                log.append(false, localeContext.getString(R.string.log_copy)+" ${file.name} ("+file.size.sizeToReadable()+")")
            }

            var tentative = 0
            val maxRetry = getMaxCopyRetry(file.size)
            var canRetry: Boolean
            var success: Boolean

            do {
                tentative++
                canRetry = tentative <= maxRetry
                // Sardine (WebDav) can't use InputStreams => upload from local file instead
                success = if(backupData.source.protocol == "LOCAL" && backupData.destination.protocol == "WDAV")
                        targetDest.write(backupData.source.path, file, connexion, canRetry, reportProgress = { updateProgressOnGoing(it, connexion) })
                    else
                        targetDest.write(targetSrc.getReader(file, connexion), file, connexion, canRetry, reportProgress = { updateProgressOnGoing(it, connexion) })
                // Close InputStream if source is FTP
                targetSrc.closeReader(connexion)
                // Update time left in case of corruption
                if(!success)
                    progress.fileCorruptionDetected(file.size)
            } while (!success && canRetry && isActive)

            updateProgressConfirmed(file.size, false, false, connexion)

        } while(backupPool.isNotEmpty() && isActive)

        updateProgressDetail(null, connexion)
    }

    /** Get how many copy tentative should be done in case of file corruption **/
    private fun getMaxCopyRetry(fileSize: Long): Int {
        return if(!backupData.options.retryCorrupted)
            0
        else {
            if(fileSize <= 5*1024*1024)
                3
            else if(fileSize <= 25*1024*1024)
                2
            else 1
        }
    }

    /** Creates or remove .nomedia file **/
    private suspend fun manageNomedia() {
        targetDest.manageNomedia(backupData.options.nomedia)
    }


    // ------------------
    // Orphans management
    // ------------------

    /** Manage orphan files **/
    private suspend fun manageOrphans(backupFileList: List<WorkerBackup_File>, obsoleteOrphans: Boolean, displayProgressDetail: Boolean) = withContext(dispatcherDefault) {
        val filesCount = backupFileList.count()

        if(filesCount == 0)
            return@withContext

        val requestedAction: String
        val position = if(obsoleteOrphans) 2 else 3
        val connexion = if(obsoleteOrphans) 2 else 4

        if(obsoleteOrphans) {
            requestedAction = localeContext.getString(R.string.log_delete)
            updateProgressDetail(localeContext.getString(R.string.log_delete_obsolete_orphans)+" ($filesCount)", position)
        }
        else {
            when(backupData.options.orph_action) {
                0 -> return@withContext
                1 -> {
                    if(backupData.options.orph_folder.isNullOrBlank()) {
                        generateWarning(localeContext.getString(R.string.error_orphan_folder_not_specified))
                        return@withContext
                    }

                    requestedAction = localeContext.getString(R.string.log_move)
                    if(displayProgressDetail)
                        updateProgressDetail(localeContext.getString(R.string.log_orphans_move_long)+" ($filesCount)", position)
                }
                else -> {
                    requestedAction = localeContext.getString(R.string.log_delete)
                    if(displayProgressDetail)
                        updateProgressDetail(localeContext.getString(R.string.log_orphans_delete_long)+" ($filesCount)", position)
                }
            }
        }

        if(filesCount < 10)
            doOrphans(backupFileList, requestedAction, obsoleteOrphans, connexion)
        else {
            val split = round((filesCount/2).toDouble()).toInt()
            val jobManageOrphans = launch { doOrphans(backupFileList.subList(0, split), requestedAction, obsoleteOrphans, connexion) }
            doOrphans(backupFileList.subList(split, filesCount), requestedAction, obsoleteOrphans,connexion+1)
            jobManageOrphans.join()
        }

        if(obsoleteOrphans || displayProgressDetail)
            updateProgressDetail(null, position)
    }

    /** Proceed orphans files **/
    private suspend fun doOrphans(backupFileList: List<WorkerBackup_File>, requestedAction: String, isObsolete: Boolean, connexion: Int) = withContext(dispatcherDefault) {
        backupFileList.forEach {
            yield()
            log.append(false, "$requestedAction ${it.name} ("+it.size.sizeToReadable()+")")

            val orphAction = if(isObsolete)
                3
            else
                backupData.options.orph_action

            targetDest.manageOrphan(it, orphAction, backupData.options.orph_folder, connexion)

            updateProgressConfirmed(it.size, true, isObsolete, null)
        }
    }


    // ---------------
    // Backup progress
    // ---------------

    /** Update progress data during file transfer **/
    private fun updateProgressOnGoing(sizeDone: Long, connexion: Int) = scopeDefault.launch {
        progress.incrementOnGoing(sizeDone, connexion)
        updateProgressBar()
    }

    /** Update progress data after each file confirmed **/
    private fun updateProgressConfirmed(sizeDone: Long, isOrphan: Boolean, isObsolete: Boolean, connexion: Int?) = scopeDefault.launch {
        progress.incrementConfirmed(sizeDone, isOrphan, isObsolete, connexion)
        updateProgressBar()
    }

    /** Update progress bar **/
    private fun updateProgressBar() {
        val progress = progress.getProgress()
        scopeIO.launch { repository.setBackupProgress(backupID, progress.percentConfirmed, progress.percentCurrent) }
        foregroundInfo.updateNotification(progress.sizeCurrent, progress.sizeTotal, progress.percentConfirmed, progress.percentCurrent, progress.timeLeft)
    }

    /** Update current file being transferred during backup **/
    private fun updateProgressDetail(text: String?, position: Int) = scopeIO.launch {
        when(position) {
            1 -> repository.setBackupProgressDetail1(backupID, text)
            2 -> repository.setBackupProgressDetail2(backupID, text)
            else -> repository.setBackupProgressDetail3(backupID, text)
        }
    }


    // -------------
    // Notifications
    // -------------

    /** Create a foreground notification to extend worker duration above 10 min **/
    private suspend fun setForegroundNotification() {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

        foregroundInfo.initialise(
            backupID,
            backupData.source.protocol,
            backupData.source.ssl,
            backupData.source.share,
            backupData.source.path,
            backupData.destination.protocol,
            backupData.destination.ssl,
            backupData.destination.share,
            backupData.destination.path,
            backupData.colors,
            cancelIntent
        )

        val progress = progress.getProgress()

        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                setForeground(ForegroundInfo(backupID, foregroundInfo.getNotification(progress.sizeCurrent, progress.sizeTotal, progress.percentConfirmed, progress.percentCurrent), FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            else
                setForeground(ForegroundInfo(backupID, foregroundInfo.getNotification(progress.sizeCurrent, progress.sizeTotal, progress.percentConfirmed, progress.percentCurrent)))
        }
        catch(_: Exception) {
            foregroundInfo.setForegroundFailed()
            if(appSettings.getLogsWarningSetForeground())
                generateWarning(localeContext.getString(R.string.error_setforeground))
        }
    }

    /** Update scheduled backup daily stats **/
    private suspend fun updateDailyStats(startup: Boolean) {
        if(backupScheduled) {
            if(startup)
                repository.setScheduledBackupActive(backupID)
            else {
                val backupStats = repository.getScheduledBackupStats(backupID)

                var numSuccess = backupStats.success
                var numWarning = backupStats.warning
                var numFailed = backupStats.failed
                var numCanceled = backupStats.canceled

                when(workerResult) {
                    "success" -> numSuccess += 1
                    "warning" -> numWarning += 1
                    "failure" -> numFailed += 1
                    "cancel" -> numCanceled += 1
                }

                repository.setScheduledBackupDailyStats(
                    backupID,
                    backupStats.files_count+progress.statFilesCount,
                    backupStats.files_size+progress.statFilesBytes,
                    backupStats.orphans_count+progress.statOrphansCount,
                    backupStats.orphans_size+progress.statOrphansBytes,
                    numSuccess,
                    numWarning,
                    numFailed,
                    numCanceled
                )
            }
        }
    }

    /** Schedule creation/update post backup notification **/
    private fun scheduleNotification() {
        if(backupScheduled && appSettings.getNotificationType() != 0)
            Manager_Workers().scheduleNotification(
                backupID,
                backupData.
                source.protocol,
                backupData.source.ssl,
                backupData.source.share,
                backupData.source.path,
                backupData.destination.protocol,
                backupData.destination.ssl,
                backupData.destination.share,
                backupData.destination.path,
                backupData.colors.icons
            )
    }


    // --------
    // Schedule
    // --------

    /** Schedule next backup **/
    private fun scheduleNextBackup(lastBackupMillis: Long?) {
        if(backupData.options.schedule) {
            val nextBackupMillis = Manager_Workers().setScheduler(
                backupID,
                backupData.options.const_wifi,
                backupData.options.const_charging,
                backupData.options.const_idle,
                backupData.options.interval,
                backupData.options.interval_unit,
                backupData.options.hour,
                backupData.options.minute,
                lastBackupMillis
            )

            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            val nextBackupDate = Instant.ofEpochMilli(nextBackupMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
            val nextBackupText = if(workerResult == "success")
                localeContext.getString(R.string.log_next_schedule_success)
            else
                localeContext.getString(R.string.log_next_schedule_failed)

            log.append(true, "\n$nextBackupText "+formatter.format(nextBackupDate))
        }
    }
}