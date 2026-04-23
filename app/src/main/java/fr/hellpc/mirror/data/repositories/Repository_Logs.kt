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

package fr.hellpc.mirror.data.repositories

import fr.hellpc.mirror.App
import fr.hellpc.mirror.data.Logs_NavigationPosition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.properties.Delegates

class Repository_Logs {

    private var backupId by Delegates.notNull<Int>()
    private var position by Delegates.notNull<Int>()
    private var logCount by Delegates.notNull<Int>()

    private lateinit var logFiles: List<String>
    private lateinit var logsPath: String

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO
    private val dispatcherDefault: CoroutineDispatcher = Dispatchers.Default

    // -------------------------------------

    fun initialize(backupId: Int) {
        this.backupId = backupId
        logsPath = App.instance.filesDir.toString()+"/$backupId/logs/"
    }

    /** Load log files list **/
    suspend fun listLogFolder(): Logs_NavigationPosition? = withContext(dispatcherIO) {
        return@withContext try {
            logFiles = Files.walk(Paths.get(logsPath), 1).filter { it.isRegularFile() }.map { it.name }.sorted().collect(Collectors.toList())

            logCount = logFiles.count()
            position = logCount

            getCurrentPosition()
        }
        catch (exp: Exception) { null }
    }

    /** Load log file data **/
    suspend fun loadLogFile(): List<String>? = withContext(dispatcherIO) {
        return@withContext try {
            val logFile = Paths.get(logsPath + logFiles[position - 1])

            if (Files.exists(logFile))
                Files.lines(logFile).collect(Collectors.toList())
            else
                null
        }
        catch (exp: Exception) { null }
    }

    /** Change position to the previous/next Log file **/
    suspend fun changePosition(offset: Int): Logs_NavigationPosition = withContext(dispatcherDefault) {
        val newPosition = position + offset
        if(newPosition in 1 .. logCount)
            position = newPosition
        return@withContext getCurrentPosition()
    }

    /** Jump position to a specific log file **/
    suspend fun jumpToPosition(newPosition: Int): Logs_NavigationPosition = withContext(dispatcherDefault) {
        val offset = newPosition - position
        return@withContext changePosition(offset)
    }

    /** Get current position **/
    private fun getCurrentPosition() = Logs_NavigationPosition(position, logCount)
}