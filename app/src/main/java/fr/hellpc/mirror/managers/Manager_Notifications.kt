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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.room.AllBackupsScheduled_Stats
import fr.hellpc.mirror.data.room.Backup_Colors
import fr.hellpc.mirror.data.room.Backup_Result
import fr.hellpc.mirror.services.Service_Notification_Permanent
import fr.hellpc.mirror.ui.activities.Activity_Logs
import fr.hellpc.mirror.data.NotificationGlobal_Stats
import fr.hellpc.mirror.utilities.Utility_BackupTarget
import fr.hellpc.mirror.utilities.Utility_Conversion.sizeToReadable

class Manager_Notifications {

    private var localeContext = App.getLocaleContext()

    private val notificationService by lazy { Intent(App.instance, Service_Notification_Permanent::class.java) }
    private val utilityPathFormat by lazy { Utility_BackupTarget() }

    private val prefService by lazy { App.instance.getSharedPreferences("SERVICE", Context.MODE_PRIVATE) }
    private val settings by lazy { Manager_Settings() }

    private val colorValueBorders by lazy { App.resources.getIntArray(R.array.color_value_borders).toList() }
    private val colorValueIcons by lazy { App.resources.getIntArray(R.array.color_value_icons).toList() }
    private val colorValueProgressbarSecondary by lazy { App.resources.getIntArray(R.array.color_value_progressbar_secondary).toList() }

    private val resultChannelID by lazy { "fr.hellpc.mirror.result_channel" }
    private val resultGroup by lazy { "fr.hellpc.mirror.result_group" }
    private val ongoingChannelID by lazy { "fr.hellpc.mirror.ongoing_channel" }
    private val ongoingGroup by lazy { "fr.hellpc.mirror.ongoing_group" }
    private lateinit var currentChannelID: String
    private lateinit var currentGroup: String

    // -------------------------------------

    private val openAppPendingIntent by lazy {
        PendingIntent.getActivity(
            App.instance,
            0,
            App.instance.packageManager.getLaunchIntentForPackage(App.instance.packageName)
                ?.setPackage(null)
                ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED },
            FLAG_IMMUTABLE)
    }

    // -------------------------------------

    fun initialize(ongoing: Boolean) {
        if(ongoing) {
            currentChannelID = ongoingChannelID
            currentGroup = ongoingGroup
        }
        else {
            currentChannelID = resultChannelID
            currentGroup = resultGroup
        }
    }

    fun updateContext() {
        localeContext = App.getLocaleContext()
    }


    // -------
    // Channel
    // -------

    /** Create notification channel **/
    @Synchronized fun createNotificationChannel(notificationManager: NotificationManager, ongoing: Boolean, showBadge: Boolean) {
        val name: String
        val descriptionText: String

        if(ongoing) {
            name = localeContext.getString(R.string.notification_ongoing_channel_name)
            descriptionText = localeContext.getString(R.string.notification_ongoing_channel_description)
        }
        else {
            name = localeContext.getString(R.string.notification_result_channel_name)
            descriptionText = localeContext.getString(R.string.notification_result_channel_description)
        }

        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(currentChannelID, name, importance).apply {
            description = descriptionText
            setShowBadge(showBadge)
        }
        notificationManager.createNotificationChannel(channel)
    }


    // -------
    // Service
    // -------

    /** Manage permanent notification service **/
    fun managePermanentNotificationService() {
        managePermanentNotificationService(getServiceStatus(), false)
    }

    /** Manage permanent notification service **/
    fun managePermanentNotificationService(runService: Boolean, hasChanged: Boolean) {
        if(hasChanged)
            saveServiceStatus(runService)

        if(runService && settings.getNotificationType() == 3)
            startPermanentNotificationService()
        else
            stopPermanentNotification()
    }

    /** Launch/update permanent notification **/
    @Synchronized fun startPermanentNotificationService() {
        App.instance.startForegroundService(notificationService)
    }

    /** Close permanent notification **/
    private fun stopPermanentNotification() {
        App.instance.stopService(notificationService)
    }

    /** Check if service should be running **/
    fun getServiceStatus(): Boolean = prefService.getBoolean("SHOULD_RUN", false)

    /** Save service running status update **/
    private fun saveServiceStatus(shouldRun: Boolean) = prefService.edit { putBoolean("SHOULD_RUN", shouldRun) }


    // -------
    // Ongoing
    // -------

     /** Returns ongoing notification **/
    fun getOngoingNotification(
         srcIcon: Int,
         srcIconDetail:
         String?,
         srcText: String,
         destIcon: Int,
         destIconDetail: String?,
         destText: String,
         sizeCurrent: Long,
         sizeTotal: Long,
         percentConfirmed: Int,
         percentCurrent: Int,
         timeLeft: String?,
         colorTheme: Backup_Colors,
         cancelIntent: PendingIntent
    ): Notification {
        val applyBackupTheme = settings.getNotificationApplyTheme()

         val progressText = sizeCurrent.sizeToReadable()+" / "+sizeTotal.sizeToReadable()

        // ---

        val notificationCollapsed = RemoteViews(App.instance.packageName, R.layout.notification_ongoing_collapsed).apply {
            setTextViewText(R.id.notification_ongoing_collapsed_progress, progressText)
            setProgressBar(R.id.notification_ongoing_collapsed_progressbar, 100, percentConfirmed, false)
            setInt(R.id.notification_ongoing_collapsed_progressbar, "setSecondaryProgress", percentCurrent)

            timeLeft?.let {
                setTextViewText(R.id.notification_ongoing_collapsed_time_left, it)
                setViewVisibility(R.id.notification_ongoing_collapsed_time_left, View.VISIBLE)
            }

            if(applyBackupTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && colorTheme.progressbar > 0) {
                setColorStateList(R.id.notification_ongoing_collapsed_progressbar, "setProgressTintList", ColorStateList.valueOf(colorValueBorders[colorTheme.progressbar]))
                setColorStateList(R.id.notification_ongoing_collapsed_progressbar, "setSecondaryProgressTintList", ColorStateList.valueOf(colorValueProgressbarSecondary[colorTheme.progressbar]))
            }
        }

         // ---

        val notificationExpanded = RemoteViews(App.instance.packageName, R.layout.notification_ongoing_expanded).apply {
            setTextViewText(R.id.notification_ongoing_expanded_progress, progressText)
            setProgressBar(R.id.notification_ongoing_expanded_progressbar, 100, percentConfirmed, false)
            setInt(R.id.notification_ongoing_expanded_progressbar, "setSecondaryProgress", percentCurrent)
            setImageViewResource(R.id.notification_ongoing_expanded_source_img, srcIcon)
            setTextViewText(R.id.notification_ongoing_expanded_source, srcText)
            setImageViewResource(R.id.notification_ongoing_expanded_destination_img, destIcon)
            setTextViewText(R.id.notification_ongoing_expanded_destination, destText)

            timeLeft?.let {
                setTextViewText(R.id.notification_ongoing_expanded_time_left, it)
                setViewVisibility(R.id.notification_ongoing_expanded_time_left, View.VISIBLE)
            }

            srcIconDetail?.let {
                setTextViewText(R.id.notification_ongoing_expanded_source_protocol, it)
                setViewVisibility(R.id.notification_ongoing_expanded_source_protocol, View.VISIBLE)
            }

            destIconDetail?.let {
                setTextViewText(R.id.notification_ongoing_expanded_destination_protocol, it)
                setViewVisibility(R.id.notification_ongoing_expanded_destination_protocol, View.VISIBLE)
            }

            if(applyBackupTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                colorTheme.icons.takeIf { it > 0 }?.let {
                    setInt(R.id.notification_ongoing_expanded_source_img, "setColorFilter", colorValueIcons[it])
                    setInt(R.id.notification_ongoing_expanded_destination_img, "setColorFilter", colorValueIcons[it])

                    setTextColor(R.id.notification_ongoing_expanded_source_protocol, colorValueIcons[it])
                    setTextColor(R.id.notification_ongoing_expanded_destination_protocol, colorValueIcons[it])
                }

                colorTheme.progressbar.takeIf { it > 0 }?.let {
                    setColorStateList(R.id.notification_ongoing_expanded_progressbar, "setProgressTintList", ColorStateList.valueOf(colorValueBorders[it]))
                    setColorStateList(R.id.notification_ongoing_expanded_progressbar, "setSecondaryProgressTintList", ColorStateList.valueOf(colorValueProgressbarSecondary[it]))
                }
            }
        }

        // ---

        val colorSmallIcon = if(applyBackupTheme)
            colorTheme.borders.takeIf { it > 0 }?.let { colorValueBorders[it] }
                ?: colorTheme.progressbar.takeIf { it > 0 }?.let { colorValueBorders[it] }
                ?: colorTheme.icons.takeIf { it > 0 }?.let { colorValueBorders[it] }
                ?: colorTheme.background.takeIf { it > 0 }?.let { colorValueBorders[it] }
                ?: ContextCompat.getColor(localeContext, R.color.blue)
         else
            ContextCompat.getColor(localeContext, R.color.blue)

        return NotificationCompat.Builder(App.instance, currentChannelID)
            .setShowWhen(false)
            .setGroup(currentGroup)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setSmallIcon(R.drawable.ic_sync)
            .setColor(colorSmallIcon)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentTitle("$percentConfirmed%")
            .setContentText(" - $progressText")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(notificationCollapsed)
            .setCustomBigContentView(notificationExpanded)
            .clearActions()
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_close, localeContext.getString(android.R.string.cancel), cancelIntent)
            .build()
    }


    // ----------
    // Individual
    // ----------

    /** Returns notification summary to regroup individual notifications **/
    @Synchronized fun getIndividualSummary(): Notification {
        return NotificationCompat.Builder(App.instance, currentChannelID)
            .setSmallIcon(R.drawable.ic_sd_card)
            .setColor(ContextCompat.getColor(localeContext, R.color.blue))
            .setGroup(currentGroup)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setAutoCancel(false)
            .setGroupSummary(true)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setSummaryText(localeContext.getString(R.string.notification_result))
            )
            .build()
    }

    /** Returns per-Backup notification **/
    fun getIndividualNotification(backupID: Int, srcProtocol: String, srcSSL: Boolean, srcShare: String?, srcPath: String, destProtocol: String, destSSL: Boolean, destShare: String?, destPath: String, colorIcons: Int, backupResult: Backup_Result): Notification {
        val smallIconColor = when(backupResult.result) {
            "success" -> ContextCompat.getColor(localeContext, R.color.green)
            "warning" -> ContextCompat.getColor(localeContext, R.color.orange)
            "cancel" -> ContextCompat.getColor(localeContext, R.color.orange)
            else -> ContextCompat.getColor(localeContext, R.color.red)
        }

        val resultText = when (backupResult.result) {
            "success" -> localeContext.getString(R.string.notification_success)
            "warning" -> localeContext.getString(R.string.notification_warning)
            "cancel" -> localeContext.getString(R.string.notification_canceled)
            else -> localeContext.getString(R.string.notification_failed)
        }

        val intent = Intent(App.instance, Activity_Logs::class.java).putExtra(Activity_Logs.PARAM_BACKUP_ID, backupID)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(App.instance, backupID, intent, FLAG_IMMUTABLE)

        val resultData = formatFilesDetails(backupResult)

        val srcIcon = utilityPathFormat.getProtocolIcon(srcProtocol, srcPath)
        val srcText = utilityPathFormat.getReadablePath(srcProtocol, srcShare, srcPath)
        val destIcon = utilityPathFormat.getProtocolIcon(destProtocol, destPath)
        val destText = utilityPathFormat.getReadablePath(destProtocol, destShare, destPath)

        // ---

        val notificationCollapsed = RemoteViews(App.instance.packageName, R.layout.notification_result_collapsed).apply {
            if(resultData.isNotBlank())
                setTextViewText(R.id.notification_result_collapsed_info, resultData)
            else
                setViewVisibility(R.id.notification_result_collapsed_info, View.GONE)
        }

        // ---

        val notificationExpanded = RemoteViews(App.instance.packageName, R.layout.notification_result_expanded).apply {
            if(resultData.isNotBlank())
                setTextViewText(R.id.notification_result_expanded_info, resultData)
            else
                setViewVisibility(R.id.notification_result_expanded_info, View.GONE)

            setImageViewResource(R.id.notification_result_expanded_source_img, srcIcon)
            setTextViewText(R.id.notification_result_expanded_source, srcText)
            setImageViewResource(R.id.notification_result_expanded_destination_img, destIcon)
            setTextViewText(R.id.notification_result_expanded_destination, destText)

            if(srcProtocol != "LOCAL") {
                val iconDetail = utilityPathFormat.getReadableProtocol(srcProtocol, srcSSL, srcPath)
                setTextViewText(R.id.notification_result_expanded_source_protocol, iconDetail)
                setViewVisibility(R.id.notification_result_expanded_source_protocol, View.VISIBLE)
            }

            if(destProtocol != "LOCAL") {
                val iconDetail = utilityPathFormat.getReadableProtocol(destProtocol, destSSL, destPath)
                setTextViewText(R.id.notification_result_expanded_destination_protocol, iconDetail)
                setViewVisibility(R.id.notification_result_expanded_destination_protocol, View.VISIBLE)
            }

            if(settings.getNotificationApplyTheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && colorIcons > 0) {
                setInt(R.id.notification_result_expanded_source_img, "setColorFilter", colorValueIcons[colorIcons])
                setInt(R.id.notification_result_expanded_destination_img, "setColorFilter", colorValueIcons[colorIcons])

                setTextColor(R.id.notification_result_expanded_source_protocol, colorValueIcons[colorIcons])
                setTextColor(R.id.notification_result_expanded_destination_protocol, colorValueIcons[colorIcons])
            }
        }

        // ---

        return NotificationCompat.Builder(App.instance, currentChannelID)
            .setSmallIcon(R.drawable.ic_sd_card)
            .setColor(smallIconColor)
            .setGroup(currentGroup)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentTitle(resultText)
            .setContentText(resultData)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(notificationCollapsed)
            .setCustomBigContentView(notificationExpanded)
            .setSubText(resultText)
            .build()
    }

    /** Generate a line of text with number of files & orphans backed up **/
    private fun formatFilesDetails(backupResult: Backup_Result): String {
        var filesDetails = ""
        if(backupResult.files_count > 0) {
            filesDetails += localeContext.getString(R.string.notification_files) + ": "+backupResult.files_count
            if (backupResult.files_size > 0L)
                filesDetails += " (" + backupResult.files_size.sizeToReadable() + ")"
        }

        if(backupResult.orphans_count > 0) {
            if(filesDetails != "")
                filesDetails += " | "
            filesDetails += localeContext.getString(R.string.notification_orphans) + ": "+backupResult.orphans_count
            if (backupResult.orphans_size > 0L)
                filesDetails += " (" + backupResult.orphans_size.sizeToReadable() + ")"
        }

        if(filesDetails.isEmpty() && backupResult.result != "failure")
            filesDetails = localeContext.getString(R.string.notification_no_file)

        return filesDetails
    }


    // -----
    // Global
    // -----

    /** Returns global notification **/
    fun getGlobalNotification(rawStats: AllBackupsScheduled_Stats, permanent: Boolean): Notification {
        val stats = formatStats(rawStats)

        // ---

        val notificationCollapsed = RemoteViews(App.instance.packageName, R.layout.notification_global_collapsed).apply {
            if(stats.done.isBlank() && stats.files.isBlank())
                setTextViewText(R.id.notification_global_collapsed_info, localeContext.getString(R.string.notification_no_backup))
            else
                setTextViewText(R.id.notification_global_collapsed_info, stats.files.ifBlank { localeContext.getString(R.string.notification_no_file) })
        }

        // ---

        val notificationExpanded = RemoteViews(App.instance.packageName, R.layout.notification_global_expanded).apply {
            setTextViewText(R.id.notification_global_expanded_done, stats.done.ifBlank { localeContext.getString(R.string.notification_no_backup) })

            if(stats.done.isBlank() && stats.files.isBlank())
                setViewVisibility(R.id.notification_global_expanded_files, View.GONE)
            else {
                setViewVisibility(R.id.notification_global_expanded_files, View.VISIBLE)
                setTextViewText(R.id.notification_global_expanded_files, stats.files.ifBlank { localeContext.getString(R.string.notification_no_file) })
            }
        }

        // ---

        val notification = NotificationCompat.Builder(App.instance, currentChannelID)
            .setSmallIcon(stats.icon)
            .setColor(stats.color)
            .setGroup(currentGroup)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(permanent)
            .setAutoCancel(!permanent)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(notificationCollapsed)
            .setCustomBigContentView(notificationExpanded)
            .setSubText(stats.scheduled)

        // Recreate notification in Android 14 if dismissed
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && permanent) {
            val dismissedIntent = Intent("DISMISSED_ACTION")
            dismissedIntent.setPackage(App.instance.packageName)
            val dismissedPendingIntent = PendingIntent.getBroadcast(App.instance, 0, dismissedIntent, FLAG_IMMUTABLE)
            notification.setDeleteIntent(dismissedPendingIntent)
        }

        return notification.build()
    }

    /** Format notification raw data **/
    private fun formatStats(rawStats: AllBackupsScheduled_Stats): NotificationGlobal_Stats {
        val numBackups = rawStats.scheduled
        val backupsActive = rawStats.actives
        val numFiles = rawStats.files_count
        val sizeFiles = rawStats.files_size
        val numOrphans = rawStats.orphans_count
        val sizeOrphans = rawStats.orphans_size
        val numSuccess = rawStats.success
        val numWarnings = rawStats.warning
        val numFailed = rawStats.failed
        val numCancel = rawStats.canceled
        val backupsDone = numSuccess+numWarnings

        val scheduled = localeContext.getString(R.string.notification_scheduled)+": $numBackups"

        // ---

        var done = ""
        if(backupsActive > 0)
            done += localeContext.getString(R.string.notification_active)+": $backupsActive"

        if(numSuccess > 0) {
            if(done != "")
                done += " | "
            done += localeContext.getString(R.string.notification_success) + ": $numSuccess"
        }

        if(numWarnings > 0) {
            if(done != "")
                done += " | "
            done += localeContext.getString(R.string.notification_warning)+": $numWarnings"
        }

        if(numFailed > 0) {
            if(done != "")
                done += " | "
            done += localeContext.getString(R.string.notification_failed)+": $numFailed"
        }

        if(numCancel > 0) {
            if(done != "")
                done += " | "
            done += localeContext.getString(R.string.notification_canceled)+": $numCancel"
        }

        // ---

        var files = ""
        if(numFiles > 0) {
            files += localeContext.getString(R.string.notification_files) + ": $numFiles"
            if (sizeFiles > 0L)
                files += " (" + sizeFiles.sizeToReadable() + ")"
        }

        if(numOrphans > 0) {
            if(files != "")
                files += " | "
            files += localeContext.getString(R.string.notification_orphans) + ": $numOrphans"
            if (sizeOrphans > 0L)
                files += " (" + sizeOrphans.sizeToReadable() + ")"
        }

        // ---

        val color = if(backupsActive > 0)
            ContextCompat.getColor(localeContext, R.color.blue)
        else if(numFailed > 0)
            ContextCompat.getColor(localeContext, R.color.red)
        else if(numCancel > 0 || numWarnings > 0)
            ContextCompat.getColor(localeContext, R.color.orange)
        else if(backupsDone > 0)
            ContextCompat.getColor(localeContext, R.color.green)
        else
            ContextCompat.getColor(localeContext, R.color.blue)

        val icon = if(backupsActive > 0)
            R.drawable.ic_wait
        else
            R.drawable.ic_sd_card

        return NotificationGlobal_Stats(color, icon, scheduled, done, files)
    }
}