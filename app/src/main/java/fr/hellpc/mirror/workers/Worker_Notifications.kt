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

import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import fr.hellpc.mirror.App
import fr.hellpc.mirror.managers.Manager_Notifications
import fr.hellpc.mirror.managers.Manager_Settings

class Worker_Notifications(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {

    private val backupID = inputData.getInt("ID_BACKUP", -1)
    private val srcProtocol = inputData.getString("SOURCE_PROTOCOL")
    private val srcSSL = inputData.getBoolean("SOURCE_SSL", false)
    private val srcShare = inputData.getString("SOURCE_SHARE")
    private val srcPath = inputData.getString("SOURCE_PATH")
    private val destProtocol = inputData.getString("DESTINATION_PROTOCOL")
    private val destSSL = inputData.getBoolean("DESTINATION_SSL", false)
    private val destShare = inputData.getString("DESTINATION_SHARE")
    private val destPath = inputData.getString("DESTINATION_PATH")
    private val colorIcons = inputData.getInt("COLOR_ICONS", 0)

    private val repository by lazy { (applicationContext as App).repositoryNotifications }

    private val managerNotifications by lazy { Manager_Notifications() }
    private val notifications by lazy { App.instance.getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    private val dispatcherDefault: CoroutineDispatcher = Dispatchers.Default

    override suspend fun doWork(): Result {
        return withContext(dispatcherDefault) {
            try {
                when(Manager_Settings().getNotificationType()) {
                    1 -> createIndividualNotification()
                    2 -> createGlobalNotification()
                    3 -> managerNotifications.startPermanentNotificationService()
                }
                Result.success()
            }
            catch(exp: Exception) {
                Result.failure()
            }
        }
    }

    /** Initialize notification manager and create channel **/
    private fun initialize() {
        managerNotifications.initialize(false)
        managerNotifications.createNotificationChannel(notifications, false, true)
    }

    /** Create a new individual discardable notification for scheduled backup result **/
    private suspend fun createIndividualNotification() {
        if(backupID >= 0 && !srcProtocol.isNullOrEmpty() && !srcPath.isNullOrEmpty() && !destProtocol.isNullOrEmpty() && !destPath.isNullOrEmpty()) {
            initialize()

            notifications.apply {
                notify(5000, managerNotifications.getIndividualSummary())
                notify(backupID+5001, managerNotifications.getIndividualNotification(
                    backupID,
                    srcProtocol,
                    srcSSL,
                    srcShare,
                    srcPath,
                    destProtocol,
                    destSSL,
                    destShare,
                    destPath,
                    colorIcons,
                    repository.getLastBackupData(backupID)))
            }
        }
    }

    /** Create a new global discardable notification for scheduled backup result **/
    private suspend fun createGlobalNotification() {
        initialize()
        notifications.notify(5000, managerNotifications.getGlobalNotification(repository.getDailyStats(), false))
    }
}