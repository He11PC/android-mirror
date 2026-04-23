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

package fr.hellpc.mirror.managers

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import fr.hellpc.mirror.App
import fr.hellpc.mirror.workers.Worker_Backup
import fr.hellpc.mirror.workers.Worker_DailyStatsReset
import fr.hellpc.mirror.workers.Worker_Notifications
import fr.hellpc.mirror.workers.Worker_Scheduler
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId.systemDefault
import java.util.*
import java.util.concurrent.TimeUnit

class Manager_Workers {
    
    private val repository = (App.instance.applicationContext as App).repositoryWorkerBackup
    private val prefService by lazy { App.instance.getSharedPreferences("SERVICE", Context.MODE_PRIVATE) }
    private val scopeDefault by lazy { CoroutineScope(Job() + Dispatchers.Default) }
    private val scopeIO by lazy { CoroutineScope(Job() + Dispatchers.IO) }


    // ------
    // Backup
    // ------

    /** Start a new backup **/
    fun launchBackup(id_backup: Int, scheduled: Boolean) = scopeDefault.launch {
        val data: Data = workDataOf("ID_BACKUP" to id_backup, "SCHEDULED" to scheduled)
        val backupWorkRequest: OneTimeWorkRequest = OneTimeWorkRequestBuilder<Worker_Backup>().setInputData(data).build()
        WorkManager.getInstance(App.instance).enqueueUniqueWork("BACKUP$id_backup", ExistingWorkPolicy.KEEP, backupWorkRequest)

        registerWorker(id_backup, backupWorkRequest.id.toString(), false)
        if(scheduled)
            unregisterScheduledWorker(id_backup)
    }

    /** Schedule a backup **/
    @SuppressLint("IdleBatteryChargingConstraints")
    fun setScheduler(id_backup: Int, const_wifi: Boolean, const_charging: Boolean, const_idle: Boolean, interval: Int, intervalUnit: Int, hour: Int?, minute: Int?, lastBackup: Long?): Long {
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true)
        if(const_wifi)
            constraints.setRequiredNetworkType(NetworkType.UNMETERED)
        if(const_charging && !const_idle)
            constraints.setRequiresCharging(true)
        if(const_idle && !const_charging)
            constraints.setRequiresDeviceIdle(true)

        val delay = getNextBackupDelay(lastBackup, interval, intervalUnit, hour, minute)

        // Worker
        val data: Data = workDataOf("ID_BACKUP" to id_backup, "RETRY_JOB" to !const_idle)
        val scheduleWorkRequestBuilder: OneTimeWorkRequest.Builder = OneTimeWorkRequestBuilder<Worker_Scheduler>()
            .setInputData(data)
            .setConstraints(constraints.build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)

        if(!const_idle)
            scheduleWorkRequestBuilder.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)

        val scheduleWorkRequest = scheduleWorkRequestBuilder.build()

        WorkManager.getInstance(App.instance).enqueueUniqueWork("SCHEDULER$id_backup", ExistingWorkPolicy.REPLACE, scheduleWorkRequest)

        registerWorker(id_backup, scheduleWorkRequest.id.toString(), true)

        return System.currentTimeMillis() + delay
    }

    /** Calculate when scheduled worker should start **/
    private fun getNextBackupDelay(lastBackup: Long?, interval: Int, intervalUnit: Int, hour: Int?, minute: Int?): Long {
        val dateNow = LocalDateTime.now()
        var dateNextBackup = if(lastBackup != null)
            Instant.ofEpochMilli(lastBackup).atZone(systemDefault()).toLocalDateTime()
        else
            dateNow

        when (intervalUnit) {
            0 -> {
                dateNextBackup = dateNextBackup.withHour(hour ?: 0)
                dateNextBackup = dateNextBackup.withMinute(minute ?: 0)
                dateNextBackup = dateNextBackup.withSecond(0)
                if(lastBackup != null)
                    dateNextBackup = dateNextBackup.plusDays(interval.toLong())
            }
            1 -> dateNextBackup = dateNextBackup.plusHours(interval.toLong())
            2 -> dateNextBackup = dateNextBackup.plusMinutes(interval.toLong())
        }

        if (dateNextBackup.isBefore(dateNow)) {
            when(intervalUnit) {
                0 -> {
                    dateNextBackup = dateNow.withHour(dateNextBackup.hour).withMinute(dateNextBackup.minute).withSecond(0)
                    if(dateNextBackup.isBefore(dateNow))
                        dateNextBackup = dateNextBackup.plusDays(1)
                }
                1 -> dateNextBackup = dateNow.plusHours(interval.toLong())
                2 -> dateNextBackup = dateNow.plusMinutes(interval.toLong())
            }
        }

        // Return delay
        return Duration.between(dateNow, dateNextBackup).toMillis()
    }

    /** Cancel a worker **/
    fun cancelWorker(id_backup: Int, id_worker: String?, reschedule: Boolean) = scopeDefault.launch {
        try {
            // Cancel worker if it exists and is running or enqueued
            val workerInstance = WorkManager.getInstance(App.instance).getWorkInfoById(UUID.fromString(id_worker)).get()
            require(workerInstance != null)
            if(workerInstance.state == WorkInfo.State.RUNNING || workerInstance.state == WorkInfo.State.ENQUEUED)
                WorkManager.getInstance(App.instance).cancelWorkById(UUID.fromString(id_worker))
            // Reset status if it doesn't (probably worker crashed and status stuck)
            else
                resetBackupStatus(id_backup, reschedule)
        }
        catch(_: Exception) { resetBackupStatus(id_backup, reschedule) }
    }

    /** Reset backup status if worker crashed **/
    private suspend fun resetBackupStatus(id_backup: Int, reschedule: Boolean) {
        val backupInfo = repository.getFailedWorkerInfo(id_backup)
        if(backupInfo.status != "idle")
            repository.resetBackupStatus(id_backup, "idle")

        if(reschedule) {
            // Cancel current scheduler if necessary
            backupInfo.id_scheduler?.let { cancelScheduledWorker(id_backup, it) }
            // New scheduler
            if (backupInfo.schedule) {
                val scheduleOptions = repository.getScheduledBackupOptions(id_backup)
                setScheduler(id_backup, scheduleOptions.const_wifi, scheduleOptions.const_charging, scheduleOptions.const_idle, scheduleOptions.interval, scheduleOptions.interval_unit, scheduleOptions.hour, scheduleOptions.minute, backupInfo.last_date)
            }
        }
    }

    /** Cancel a scheduled worker **/
    fun cancelScheduledWorker(id_backup: Int, id_scheduler: String) {
        unregisterScheduledWorker(id_backup)
        try { WorkManager.getInstance(App.instance).cancelWorkById(UUID.fromString(id_scheduler)) }
        catch(_: Exception) { }
    }

    /** Save/delete worker uuid in database **/
    private fun registerWorker(id_backup: Int, uuid: String, isScheduler: Boolean) = scopeIO.launch {
        repository.registerBackupWorker(id_backup, uuid, isScheduler)
    }

    /** Remove worker uuid from database **/
    private fun unregisterScheduledWorker(id_backup: Int) = scopeIO.launch {
        repository.registerBackupWorker(id_backup, null, true)
    }

    /** Update date comparison mode (dateSource != dateDestination || dateSource > dateDestination) **/
    fun setDateComparisonMode(id_backup: Int, strictDate: Boolean) = scopeIO.launch{
        repository.setDateComparisonMode(id_backup, strictDate)
    }


    // -----------
    // Daily Stats
    // -----------

    /** Schedule daily stats reset worker **/
    fun scheduleDailyStatsReset(schedule: Boolean) = scopeDefault.launch {
        val isScheduled = prefService.getBoolean("DAILY_STATS_RESET_IS_SCHEDULED", false)

        if(schedule && !isScheduled) {
            val scheduleWorkRequest: OneTimeWorkRequest = OneTimeWorkRequestBuilder<Worker_DailyStatsReset>()
                .setInitialDelay(getDailyStatsResetDelay(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(App.instance).enqueueUniqueWork("DAILY_STATS_RESET", ExistingWorkPolicy.REPLACE, scheduleWorkRequest)

            // Prevents creation of a new daily stats reset worker if one is already scheduled
            prefService.edit { putBoolean("DAILY_STATS_RESET_IS_SCHEDULED", true) }
        }
    }

    /** Calculate when worker should start **/
    private fun getDailyStatsResetDelay(): Long {
        val dateNow = LocalDateTime.now()

        var dateNextReset = dateNow
        dateNextReset = dateNextReset.withHour(0)
        dateNextReset = dateNextReset.withMinute(0)
        dateNextReset = dateNextReset.withSecond(0)

        if(dateNextReset.isBefore(dateNow))
            dateNextReset = dateNextReset.plusDays(1)

        return Duration.between(dateNow, dateNextReset).toMillis()
    }


    // ------------
    // Notification
    // ------------

    /** Schedule post backup notification **/
    fun scheduleNotification(backupID: Int, srcProtocol: String, srcSSL: Boolean?, srcShare: String?, srcPath: String, destProtocol: String, destSSL: Boolean?, destShare: String?, destPath: String, colorIcons: Int) {
        val data: Data = workDataOf(
            "ID_BACKUP" to backupID,
            "SOURCE_PROTOCOL" to srcProtocol,
            "SOURCE_SSL" to srcSSL,
            "SOURCE_SHARE" to srcShare,
            "SOURCE_PATH" to srcPath,
            "DESTINATION_PROTOCOL" to destProtocol,
            "DESTINATION_SSL" to destSSL,
            "DESTINATION_SHARE" to destShare,
            "DESTINATION_PATH" to destPath,
            "COLOR_ICONS" to colorIcons
        )

        val scheduleWorkRequest: OneTimeWorkRequest = OneTimeWorkRequestBuilder<Worker_Notifications>()
            .setInitialDelay(2500, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(App.instance).enqueueUniqueWork("NOTIFICATION_$backupID", ExistingWorkPolicy.REPLACE, scheduleWorkRequest)
    }
}