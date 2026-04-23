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

class Backup_WebDav_Monitor(private val backupID: Int, private val reportFrequency: Long) {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val prefDav by lazy { App.instance.getSharedPreferences("WEBDAV", Context.MODE_PRIVATE) }
    private val writeSpeedSavedAvg by lazy { prefDav.getLong("SPEED", 0L) }         // Average write speed of all backups
    private val writeSpeedSaved by lazy { prefDav.getLong("SPEED$backupID", 0L) }   // Average write speed of this backup
    private var writeSpeedSession = 0L   // Average write speed for this session

    private var activeConnexions = 0    // Number of currently active connexions
    private val avgConnexions = mutableListOf(0.0, 0.0, 0.0)    // Average number of active connexions during upload on a specific connexion
    private val startTime = mutableListOf(0L, 0L, 0L)

    // -------------------------------------

    /** Start monitoring to estimate write progress **/
    fun startMonitor(fileSize: Long, connexion: Int, reportProgression: (Long) -> Unit) = scope.launch {
        activeConnexions += 1
        avgConnexions[connexion-1] = activeConnexions.toDouble()
        startTime[connexion-1] = System.currentTimeMillis()

        delay(reportFrequency)

        var written = 0L
        while(isActive) {
            avgConnexions[connexion-1] = (avgConnexions[connexion-1] + activeConnexions) / 2
            written += (getWriteSpeed() / avgConnexions[connexion-1] * reportFrequency).roundToLong()

            if(written < fileSize)
                reportProgression(written)
            else {
                reportProgression(fileSize)
                break
            }

            delay(reportFrequency)
        }
    }

    /** Stop monitoring **/
    fun stopMonitor(monitor: Job, fileSize: Long, connexion: Int) {
        activeConnexions -= 1
        if(monitor.isActive) monitor.cancel()
        measureWriteSpeed(fileSize, connexion)
    }

    // -------------------------------------

    /** Get write speed **/
    private fun getWriteSpeed(): Long {
        return if(writeSpeedSession != 0L)
            writeSpeedSession
        else if(writeSpeedSaved != 0L)
            writeSpeedSaved
        else
            writeSpeedSavedAvg
    }

    /** Measure write speed **/
    private fun measureWriteSpeed(fileSize: Long, connexion: Int) {
        val previous = getWriteSpeed()

        val timeSpent = System.currentTimeMillis() - startTime[connexion-1]
        val measured = (fileSize / timeSpent * avgConnexions[connexion-1]).roundToLong()

        writeSpeedSession = if(previous == 0L)
            measured
        else {
            val maxSpeed = max(measured, previous).toFloat()
            val minSpeed = min(measured, previous).toFloat()
            val weight = (maxSpeed - minSpeed) / maxSpeed
            ((previous * (1 + weight) + measured * (1 - weight)) / 2).roundToLong()
        }
    }

    /** Save average write speed for next session **/
    fun saveWriteSpeed() {
        if(writeSpeedSession > 0L) {
            val writeSpeedId = if(writeSpeedSaved == 0L)
                writeSpeedSession
            else {
                val maxSpeed = max(writeSpeedSession, writeSpeedSaved).toFloat()
                val minSpeed = min(writeSpeedSession, writeSpeedSaved).toFloat()
                val weight = (maxSpeed - minSpeed) / maxSpeed
                ((writeSpeedSaved * (1 + weight) + writeSpeedSession * (1 - weight)) / 2).roundToLong()
            }

            val writeSpeedAvg = if(writeSpeedSavedAvg == 0L)
                writeSpeedSession
            else {
                val maxSpeed = max(writeSpeedSession, writeSpeedSavedAvg).toFloat()
                val minSpeed = min(writeSpeedSession, writeSpeedSavedAvg).toFloat()
                val weight = (maxSpeed - minSpeed) / maxSpeed
                ((writeSpeedSavedAvg * (1 + weight) + writeSpeedSession * (1 - weight)) / 2).roundToLong()
            }

            prefDav.edit { putLong("SPEED$backupID", writeSpeedId).putLong("SPEED", writeSpeedAvg) }
        }
    }
}