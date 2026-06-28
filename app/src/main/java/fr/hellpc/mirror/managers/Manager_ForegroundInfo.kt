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

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import fr.hellpc.mirror.App
import fr.hellpc.mirror.data.room.Backup_Colors
import fr.hellpc.mirror.utilities.Utility_BackupTarget
import kotlin.properties.Delegates

class Manager_ForegroundInfo {

    private val managerNotifications by lazy { Manager_Notifications() }
    private val notifications by lazy { App.instance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val utilityPathFormat by lazy { Utility_BackupTarget() }

    private var isInitialized = false
    private var lastUpdate = 0L

    private var notificationID by Delegates.notNull<Int>()
    private lateinit var cancelIntent: PendingIntent

    private var srcIcon by Delegates.notNull<Int>()
    private var srcIconDetail: String? = null
    private lateinit var srcText: String

    private var destIcon by Delegates.notNull<Int>()
    private var destIconDetail: String? = null
    private lateinit var destText: String

    private lateinit var colorTheme: Backup_Colors

    // -------------------------------------

    /** Initialise foreground notification **/
    fun initialise(backupID: Int, srcProtocol: String, srcSSL: Boolean?, srcShare: String?, srcPath: String, destProtocol: String, destSSL: Boolean?, destShare: String?, destPath: String, colorTheme: Backup_Colors, cancelIntent: PendingIntent) {
        this.notificationID = backupID
        this.cancelIntent = cancelIntent

        this.srcIcon = utilityPathFormat.getProtocolIcon(srcProtocol, srcPath)
        if(srcProtocol != "LOCAL")
            this.srcIconDetail = utilityPathFormat.getReadableProtocol(srcProtocol, srcSSL, srcPath)
        this.srcText = utilityPathFormat.getReadablePath(srcProtocol, srcShare, srcPath)

        this.destIcon = utilityPathFormat.getProtocolIcon(destProtocol, destPath)
        if(destProtocol != "LOCAL")
            this.destIconDetail = utilityPathFormat.getReadableProtocol(destProtocol, destSSL, destPath)
        this.destText = utilityPathFormat.getReadablePath(destProtocol, destShare, destPath)

        this.colorTheme = colorTheme

        managerNotifications.initialize(true)
        managerNotifications.createNotificationChannel( notifications, true, false)

        lastUpdate = System.currentTimeMillis()
        isInitialized = true
    }

    /** Generate notification **/
    fun getNotification(sizeCurrent: Long, sizeTotal: Long, percentConfirmed: Int, percentCurrent: Int): Notification {
        return managerNotifications.getOngoingNotification(
            srcIcon,
            srcIconDetail,
            srcText,
            destIcon,
            destIconDetail,
            destText,
            sizeCurrent,
            sizeTotal,
            percentConfirmed,
            percentCurrent,
            null,
            colorTheme,
            cancelIntent
        )
    }

    /** Notification failed **/
    fun setForegroundFailed() {
        isInitialized = false
    }

    /** Update notification **/
    @Synchronized fun updateNotification(sizeCurrent: Long, sizeTotal: Long, percentConfirmed: Int, percentCurrent: Int, timeLeft: String?) {
        if(isInitialized && sizeCurrent > 0L) {
            val timeCurrent = System.currentTimeMillis()
            val delay = timeCurrent - lastUpdate
            if(delay >= 500) {
                notifications.notify(
                    notificationID,
                    managerNotifications.getOngoingNotification(
                        srcIcon,
                        srcIconDetail,
                        srcText,
                        destIcon,
                        destIconDetail,
                        destText,
                        sizeCurrent,
                        sizeTotal,
                        percentConfirmed,
                        percentCurrent,
                        timeLeft,
                        colorTheme,
                        cancelIntent
                    )
                )
                lastUpdate = timeCurrent
            }
        }
    }

    /** Cancel notification **/
    fun cancelNotification() {
        if(isInitialized) {
            val activeNotifications = notifications.activeNotifications
            if (activeNotifications.isNotEmpty()) {
                activeNotifications.forEach {
                    if (it.id == notificationID)
                        notifications.cancel(notificationID)
                }
            }
        }
    }
}