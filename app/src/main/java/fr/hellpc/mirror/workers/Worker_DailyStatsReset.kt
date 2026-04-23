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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import fr.hellpc.mirror.App
import fr.hellpc.mirror.managers.Manager_Workers
import androidx.core.content.edit

class Worker_DailyStatsReset(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {

    private val repository by lazy { (applicationContext as App).repositoryNotifications }

    override suspend fun doWork(): Result {
        return try {
            repository.resetDailyStats()
            Result.success()
        }
        catch(exp: Exception) {
            Result.failure()
        }
        finally {
            withContext(NonCancellable) {
                App.instance.getSharedPreferences("SERVICE", Context.MODE_PRIVATE).edit {
                    putBoolean(
                        "DAILY_STATS_RESET_IS_SCHEDULED",
                        false
                    )
                }
                Manager_Workers().scheduleDailyStatsReset(repository.getBackupsScheduledCount() > 0)
            }
        }
    }
}