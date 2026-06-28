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

import fr.hellpc.mirror.data.WorkerBackup_Progress
import fr.hellpc.mirror.utilities.Utility_Conversion.millisToReadable

class Backup_Progress {

    // Progress bar
    private var progressBytesTotal = 0L
    @Volatile private var progressBytesConfirmed = 0L
    private val progressBytesPending = LongArray(3)

    // Time left
    private var timeStartDate = -1L
    private var timeProcessOrphans = false
    private var timeBytesTotal = 0L
    private var timeBytesDone = 0L

    // End of backup stats
    @Volatile var statFilesCount = 0
    @Volatile var statFilesBytes = 0L
    @Volatile var statOrphansCount = 0
    @Volatile var statOrphansBytes = 0L


    /** Calculate total size of files to manage for progress bar **/
    fun initialize(sizeToCopy: Long, sizeObsoletes: Long, sizeOrphans: Long, addOrphans: Boolean) {
        progressBytesTotal = if(addOrphans)
            sizeToCopy + sizeObsoletes + sizeOrphans
        else
            sizeToCopy + sizeObsoletes

        timeBytesTotal = if(sizeToCopy > 0L)
            sizeToCopy
        else
            sizeObsoletes + sizeOrphans

        timeProcessOrphans = sizeToCopy == 0L
    }

    /** Update copyStartDate **/
    fun copyStarted() {
        timeStartDate = System.currentTimeMillis()
    }

    /** Increment counters during file transfer **/
    @Synchronized fun incrementOnGoing(size: Long, connexion: Int) {
        progressBytesPending[connexion-1] = size
    }

    /** Increment counters when a file has been treated **/
    @Synchronized fun incrementConfirmed(size: Long, isOrphan: Boolean, isObsolete: Boolean, connexion: Int?) {
        if(connexion != null)
            progressBytesPending[connexion-1] = 0L

        if(isOrphan)
            orphanDone(size, isObsolete)
        else
            fileDone(size)
    }

    /** Update files done data **/
    private fun fileDone(size: Long) {
        statFilesCount += 1
        statFilesBytes += size
        progressBytesConfirmed += size
        timeBytesDone += size
    }

    /** Update orphans done data **/
    private fun orphanDone(size: Long, isObsolete: Boolean) {
        if(!isObsolete) {
            statOrphansCount += 1
            statOrphansBytes += size
        }
        progressBytesConfirmed += size
        if(timeProcessOrphans)
            timeBytesDone += size
    }

    /** Increment timeSize in case of file corruption to recalculate time left **/
    fun fileCorruptionDetected(size: Long) {
        timeBytesTotal += size
        timeBytesDone += size
    }


    // --------
    // Progress
    // --------

    /** Get current progress **/
    @Synchronized fun getProgress(): WorkerBackup_Progress {
        val progressBytesCurrent = getProgressBytesCurrent()
        return WorkerBackup_Progress(progressBytesCurrent, progressBytesTotal, getTimeLeft(), getProgressPercentConfirmed(), getProgressPercentCurrent(progressBytesCurrent))
    }

    private fun getProgressBytesCurrent(): Long = progressBytesConfirmed + progressBytesPending[0] + progressBytesPending[1] + progressBytesPending[2]

    private fun getProgressPercentCurrent(progressBytesCurrent: Long): Int {
        return if(progressBytesTotal == 0L)
                100
        else
            (progressBytesCurrent * 100 / progressBytesTotal).toInt()
    }

    private fun getProgressPercentConfirmed(): Int {
        return if (progressBytesTotal == 0L)
            100
        else
            (progressBytesConfirmed * 100 / progressBytesTotal).toInt()
    }

    private fun getTimeLeft(): String {
        val timeBytesCurrent = timeBytesDone + progressBytesPending[0] + progressBytesPending[1] + progressBytesPending[2]
        return if(timeStartDate > 0L && timeBytesCurrent >= 1L && timeBytesCurrent < timeBytesTotal) {
            val timeSpent = System.currentTimeMillis() - timeStartDate
            val timeLeft = timeSpent * (timeBytesTotal - timeBytesCurrent) / timeBytesCurrent
            timeLeft.millisToReadable(true, false)
        } else ""
    }
}