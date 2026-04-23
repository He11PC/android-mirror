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

package fr.hellpc.mirror.ui.viewmodels

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.hellpc.mirror.App
import fr.hellpc.mirror.data.BackupsInfos_StatusCount
import fr.hellpc.mirror.data.repositories.Repository_Main
import fr.hellpc.mirror.data.room.Backup_Colors
import fr.hellpc.mirror.data.room.Backup_Infos
import fr.hellpc.mirror.managers.Manager_Workers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ViewModel_Main(private val repository: Repository_Main): ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = (this[APPLICATION_KEY] as App).repositoryMain
                ViewModel_Main(repository = repository)
            }
        }
    }

    private val managerWorkers by lazy { Manager_Workers() }

    private val dispatcherIO: CoroutineDispatcher = Dispatchers.IO

    val allBackupsCondensed: LiveData<List<Backup_Infos>> = repository.backupsCondensed.asLiveData()
    val backupsStatusCount: LiveData<BackupsInfos_StatusCount> = repository.backupsStatusCount.asLiveData()
    val backupsScheduledCount: LiveData<Int> = repository.backupsScheduledCount.asLiveData()
    val backupColorThemes: LiveData<List<Backup_Colors>> = repository.backupColorThemes.asLiveData()

    // -------------------------------------

    /** Reset backup status that didn't go idle after worker crash **/
    fun resetStuckedStatus(init: Boolean) = viewModelScope.launch {
        if(init)
            repository.resetStuckedStatus()
    }

    /** Delete backup and clean remaining data **/
    fun deleteBackup(id_backup: Int, id_worker: String?, id_scheduler: String?, position: Int, backupFolder: File) = viewModelScope.launch {
        launch {
            id_worker?.let { managerWorkers.cancelWorker(id_backup, it, false) }
            id_scheduler?.let { managerWorkers.cancelScheduledWorker(id_backup, it) }
            repository.deleteBackup(id_backup, position)
        }

        deleteBackupFolder(backupFolder)
        deleteSharedPref(id_backup)
    }

    /** Delete backup files and folder **/
    private fun deleteBackupFolder(backupFolder: File) = viewModelScope.launch(dispatcherIO) { backupFolder.deleteRecursively() }

    /** Remove WebDav average write speed for this backup if it exists **/
    private fun deleteSharedPref(id_backup: Int) = viewModelScope.launch(dispatcherIO) {
        val sharedPrefs = App.instance.getSharedPreferences("WEBDAV", Context.MODE_PRIVATE)
        if(sharedPrefs.all.containsKey("SPEED$id_backup"))
            sharedPrefs.edit { remove("SPEED$id_backup") }
    }

    /** Disable backup **/
    fun disableBackup(id_backup: Int, id_worker: String?, id_scheduler: String?) = viewModelScope.launch {
        launch {
            id_worker?.let { managerWorkers.cancelWorker(id_backup, it, false) }
            id_scheduler?.let { managerWorkers.cancelScheduledWorker(id_backup, it) }
        }

        repository.disableBackup(id_backup)
    }

    /** Enable backup **/
    fun enableBackup(id_backup: Int, lastDate: Long?) = viewModelScope.launch {
        launch { repository.enableBackup(id_backup) }
        val backup = repository.loadBackup(id_backup)
        if(backup.options.schedule)
            managerWorkers.setScheduler(id_backup, backup.options.const_wifi, backup.options.const_charging, backup.options.const_idle, backup.options.interval, backup.options.interval_unit, backup.options.hour, backup.options.minute, lastDate)
    }

    // -------------------------------------

    /** Update backup cards position while moving **/
    fun backupCardsUpdatePosition(oldPosition: Int, newPosition: Int) = viewModelScope.launch {
        repository.updateBackupPositions(oldPosition, newPosition)
    }

    // -------------------------------------

    /** Get all backups currently IDLE **/
    suspend fun getBackupsIdle() = repository.getBackupsIdle()

    /** Get all backups with the same color theme **/
    suspend fun getBackupsIdleWithTheme(theme: Backup_Colors) = repository.getBackupsIdleWithTheme(theme)

    /** Get all backups currently running **/
    suspend fun getBackupsRunning() = repository.getBackupsRunning()
}