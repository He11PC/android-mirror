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

package fr.hellpc.mirror.services

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import fr.hellpc.mirror.App
import fr.hellpc.mirror.managers.Manager_Notifications
import fr.hellpc.mirror.data.room.AllBackupsScheduled_Stats

class Service_Notification_Permanent: LifecycleService() {

    private val managerNotifications by lazy { Manager_Notifications() }
    private val notifications by lazy { App.instance.getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private val repository by lazy { (application as App).repositoryNotifications }

    private var localeContext = App.getLocaleContext()

    // -------------------------------------

    private lateinit var previousStats: AllBackupsScheduled_Stats
    private lateinit var currentStats: AllBackupsScheduled_Stats

    private val observer = Observer<AllBackupsScheduled_Stats> { data ->
        currentStats = data
        if(currentStats.scheduled > 0 && (!::previousStats.isInitialized || currentStats != previousStats)) {
            previousStats = currentStats
            updateNotification()
        }
    }

    private val onNotificationDismissedReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateNotification()
            }
        }
    }

    private var serviceIsStarted = false
    private var serviceIsAlive = true


    // -------
    // Service
    // -------

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if(serviceIsStarted)
            updateNotification()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        initialise()
        repository.dailyStats.observeForever(observer)

        // Recreate notification in Android 14 if dismissed
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            registerReceiver(onNotificationDismissedReceiver, IntentFilter("DISMISSED_ACTION"), RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceIsAlive = false
        repository.dailyStats.removeObserver(observer)

        // Recreate notification in Android 14 if dismissed
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            unregisterReceiver(onNotificationDismissedReceiver)
    }

    /** Restart service when closed by Android **/
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        if(currentStats.scheduled > 0) {
            val restartServiceIntent = Intent(applicationContext, Service_Notification_Permanent::class.java).also { it.setPackage(packageName) }
            val restartServicePendingIntent: PendingIntent = PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
            applicationContext.getSystemService(ALARM_SERVICE)
            val alarmService: AlarmManager = applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
            alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent)
        }
    }

    /** Create notification channel **/
    private fun initialise() {
        managerNotifications.initialize(false)
        managerNotifications.createNotificationChannel(notifications, false, false)
    }


    // ------------
    // Notification
    // ------------

    /** Create/update notification **/
    private fun updateNotification() {
        if(serviceIsAlive) {
            // In case of language change after service launched
            val currentLocaleContext = App.getLocaleContext()
            if(currentLocaleContext != localeContext) {
                localeContext = currentLocaleContext
                managerNotifications.updateContext()
            }

            if(!serviceIsStarted) {
                serviceIsStarted = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    startForeground(5000, managerNotifications.getGlobalNotification(currentStats, true), FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else
                    startForeground(5000, managerNotifications.getGlobalNotification(currentStats, true))
            }
            else
                notifications.notify(5000, managerNotifications.getGlobalNotification(currentStats, true))
        }
    }
}