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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.managers.Manager_Log
import fr.hellpc.mirror.managers.Manager_Workers
import fr.hellpc.mirror.utilities.Utility_Locale.setAppLocale
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class Worker_Scheduler(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {

    private val backupID = inputData.getInt("ID_BACKUP", -1)
    private val retryJob = inputData.getBoolean("RETRY_JOB", false)
    private val repository by lazy { (applicationContext as App).repositoryWorkerScheduler }
    private var workerFailed = false


    override suspend fun doWork(): Result {
        val backupStatus = repository.loadStatus(backupID)

        require(backupStatus != null)

        // After 3 retry, reschedule
        if(runAttemptCount > 3) {
            workerFailed = true
            Result.failure()
        }

        return try {
            if(backupStatus.status == "idle" && backupStatus.id_worker == null)
                Manager_Workers().launchBackup(backupID, true)
            else
                throw Exception()
            Result.success()
        }
        catch(exp: Exception) {
            if(retryJob)
                Result.retry()
            else {
                workerFailed = true
                Result.failure()
            }
        }
        finally {
            withContext(NonCancellable) {
                if(workerFailed) {
                    val backupData = repository.loadBackup(backupID)
                    val localeContext = applicationContext.setAppLocale()

                    // Create log file
                    val log = Manager_Log()
                    log.initialise(localeContext.getString(R.string.error_backup_not_idle))

                    // Schedule next backup
                    val nextBackupMillis = Manager_Workers().setScheduler(backupID, backupData.options.const_wifi, backupData.options.const_charging, backupData.options.const_idle, backupData.options.interval, backupData.options.interval_unit, backupData.options.hour, backupData.options.minute, backupStatus.last_date)
                    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    val nextBackupDate = Instant.ofEpochMilli(nextBackupMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    val nextBackupText = localeContext.getString(R.string.log_next_schedule_failed)

                    log.append(true, "$nextBackupText "+formatter.format(nextBackupDate))

                    // Write backup log file
                    log.write(backupID)

                    // Notify about backup cancellation
                    repository.setBackupCanceled(backupID)
                    val backupStats = repository.getScheduledBackupStats(backupID)
                    val numCanceled = backupStats.canceled+1
                    repository.setScheduledBackupCanceledDailyStats(backupID, numCanceled)
                }
            }
        }
    }
}