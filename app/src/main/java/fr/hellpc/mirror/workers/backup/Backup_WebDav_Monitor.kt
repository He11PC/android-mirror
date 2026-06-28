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

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import fr.hellpc.mirror.App
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import androidx.core.content.edit
import kotlin.time.Duration.Companion.milliseconds

class Backup_WebDav_Monitor(private val backupID: Int, private val reportFrequency: Long) {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val prefDav by lazy { App.instance.getSharedPreferences("WEBDAV", Context.MODE_PRIVATE) }
    private val writeSpeedSavedAvg by lazy { prefDav.getLong("SPEED", 0L) }         // Average write speed of all backups
    private val writeSpeedSaved by lazy { prefDav.getLong("SPEED$backupID", 0L) }   // Average write speed of this backup
    @Volatile private var writeSpeedSession = 0L   // Average write speed for this session

    @Volatile private var activeConnexions = 0    // Number of currently active connexions
    private val avgConnexions = DoubleArray(3)    // Average number of active connexions during upload on a specific connexion
    private val startTime = LongArray(3)
    private val lock = Any()

    // -------------------------------------

    /** Start monitoring to estimate write progress **/
    fun startMonitor(fileSize: Long, connexion: Int, reportProgress: (Long) -> Unit) = scope.launch {
        val idx = connexion - 1
        synchronized(lock) {
            activeConnexions += 1
            avgConnexions[idx] = activeConnexions.toDouble()
            startTime[idx] = System.currentTimeMillis()
        }

        delay(reportFrequency.milliseconds)

        var lastExecutionTime = System.currentTimeMillis()
        var written = 0L
        while(isActive) {
            var currentAvgConnexion = 0.0
            synchronized(lock) {
                avgConnexions[idx] = (avgConnexions[idx] + activeConnexions) / 2
                currentAvgConnexion = avgConnexions[idx]
            }

            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - lastExecutionTime
            lastExecutionTime = currentTime

            written += (getWriteSpeed() / currentAvgConnexion * elapsedTime).roundToLong()

            if(written < fileSize)
                reportProgress(written)
            else {
                reportProgress(fileSize)
                break
            }

            delay(reportFrequency.milliseconds)
        }
    }

    /** Stop monitoring **/
    fun stopMonitor(monitor: Job, fileSize: Long, connexion: Int) {
        synchronized(lock) { activeConnexions -= 1 }
        if(monitor.isActive) monitor.cancel()
        measureWriteSpeed(fileSize, connexion)
    }

    // -------------------------------------

    /** Get write speed **/
    private fun getWriteSpeed(): Long {
        return if(writeSpeedSession != 0L) writeSpeedSession
        else if(writeSpeedSaved != 0L) writeSpeedSaved
        else writeSpeedSavedAvg
    }

    /** Measure write speed **/
    private fun measureWriteSpeed(fileSize: Long, connexion: Int) {
        val idx = connexion - 1
        val previous = getWriteSpeed()

        val timeSpent = System.currentTimeMillis() - startTime[idx]
        if (timeSpent <= 0) return

        val currentAvg = synchronized(lock) { avgConnexions[idx] }
        val measured = (fileSize / timeSpent.toDouble() * currentAvg).roundToLong()

        writeSpeedSession = if(previous == 0L) {
            measured
        } else {
            val maxSpeed = max(measured, previous).toFloat()
            val minSpeed = min(measured, previous).toFloat()
            val weight = if (maxSpeed > 0) (maxSpeed - minSpeed) / maxSpeed else 0f
            ((previous * (1 + weight) + measured * (1 - weight)) / 2).roundToLong()
        }
    }

    /** Save average write speed for next session **/
    fun saveWriteSpeed() {
        if(writeSpeedSession > 0L) {
            val currentSession = writeSpeedSession
            val writeSpeedId = if(writeSpeedSaved == 0L) currentSession
            else {
                val maxSpeed = max(currentSession, writeSpeedSaved).toFloat()
                val minSpeed = min(currentSession, writeSpeedSaved).toFloat()
                val weight = (maxSpeed - minSpeed) / maxSpeed
                ((writeSpeedSaved * (1 + weight) + currentSession * (1 - weight)) / 2).roundToLong()
            }

            val writeSpeedAvg = if(writeSpeedSavedAvg == 0L) currentSession
            else {
                val maxSpeed = max(currentSession, writeSpeedSavedAvg).toFloat()
                val minSpeed = min(currentSession, writeSpeedSavedAvg).toFloat()
                val weight = (maxSpeed - minSpeed) / maxSpeed
                ((writeSpeedSavedAvg * (1 + weight) + currentSession * (1 - weight)) / 2).roundToLong()
            }

            prefDav.edit { putLong("SPEED$backupID", writeSpeedId).putLong("SPEED", writeSpeedAvg) }
        }
    }
}