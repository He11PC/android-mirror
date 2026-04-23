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

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import fr.hellpc.mirror.App
import fr.hellpc.mirror.utilities.Utility_Conversion.millisToReadable
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.stream.Collectors
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class Manager_Log {

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO

    private val logText: MutableList<String> = mutableListOf()
    private val backupStartDate = System.currentTimeMillis()

    fun initialise(text: String) {
        logText.add("<b><u>" + getCurrentDateTime() + " - $text</u></b>")
        logText.add("")
    }

    /** Append new line to log variable **/
    @Synchronized fun append(hideTime: Boolean, text: String) {
        val message = if(hideTime)
            text
        else
            "<font color=gray>" + measureElapsedTime(false, false) + "|</font> $text"
        logText.add(message)
    }

    /** Create log file **/
    suspend fun write(id: Int) = withContext(dispatcherIO) {
        val directory = Paths.get(App.instance.filesDir.toString()+"/$id/logs")
        val logFile = Paths.get("$directory/$backupStartDate.log")

        try { Files.createDirectories(directory) }
        catch(exp: Exception) { Log.d("Log", exp.message.toString()) }

        try {
            Files.newBufferedWriter(logFile, StandardOpenOption.CREATE).use { writer ->
                logText.forEach { writer.appendLine(it) }
            }
        }
        catch(exp: Exception) { Log.d("Log", exp.message.toString()) }

        removeOldLogs(id)
    }

    /** Get current date as string **/
    private fun getCurrentDateTime(): String {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        return Instant.ofEpochMilli(backupStartDate).atZone(ZoneId.systemDefault()).toLocalDateTime().format(formatter).toString()
    }

    /** Measures elapsed time **/
    fun measureElapsedTime(verbose: Boolean, accurate: Boolean): String {
        val backupDuration = System.currentTimeMillis() - backupStartDate
        return backupDuration.millisToReadable(verbose, accurate)
    }

    /** Remove old log files **/
    private fun removeOldLogs(id: Int) {
        val logsRetention = Manager_Settings().getLogsRetention()
        val logs = try { Files.walk(Paths.get(App.instance.filesDir.toString()+"/$id/logs")).filter { it.isRegularFile() }.map { it.name }.sorted().collect(Collectors.toList()) }
        catch(exp: Exception) { emptyList() }
        val logsCount = logs.count()

        if(logsCount > logsRetention) {
            val oldLogs = logs.subList(0, logsCount-logsRetention)
            try { oldLogs.forEach { Files.delete(Paths.get(App.instance.filesDir.toString() + "/$id/logs/$it")) } }
            catch(exp: Exception) { Log.d("Log", exp.message.toString()) }
        }
    }
}