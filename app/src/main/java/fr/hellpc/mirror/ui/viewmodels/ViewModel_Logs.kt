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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.hellpc.mirror.App
import fr.hellpc.mirror.data.repositories.Repository_Logs
import fr.hellpc.mirror.data.Logs_NavigationPosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

class ViewModel_Logs(private val repository: Repository_Logs): ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = (this[APPLICATION_KEY] as App).repositoryLog
                ViewModel_Logs(repository = repository)
            }
        }
    }

    private val <T> LiveData<T>.mutable: MutableLiveData<T> get() = this as MutableLiveData<T>

    // -------------------------------------

    val logFile: LiveData<List<String>?> by lazy { MutableLiveData() }
    val position: LiveData<Logs_NavigationPosition?> by lazy { MutableLiveData() }
    val error: LiveData<String?> by lazy { MutableLiveData() }
    val showNavigationText: LiveData<Boolean> by lazy { MutableLiveData() }

    private val duration by lazy { 2000L }
    private var countDown = 0L

    // -------------------------------------

    fun initialize(backupId: Int) = viewModelScope.launch {
        repository.initialize(backupId)

        val filesInfo = repository.listLogFolder()
        position.mutable.postValue(filesInfo)

        if((filesInfo?.logCount ?: 0) > 0)
            loadLogFile()
    }

    /** Load log file data **/
    private fun loadLogFile() = viewModelScope.launch {
        logFile.mutable.postValue(repository.loadLogFile())
    }

    /** Change position to the previous/next Log file **/
    fun changePosition(offset: Int) = viewModelScope.launch {
        updatePosition(repository.changePosition(offset))
    }

    /** Jump position to a specific log file **/
    fun jumpToPosition(newPosition: Int) = viewModelScope.launch {
        updatePosition(repository.jumpToPosition(newPosition))
    }

    /** Check for position change and update if necessary **/
    private fun updatePosition(newPosition: Logs_NavigationPosition) {
        if(newPosition != position.value) {
            loadLogFile()
            position.mutable.postValue(newPosition)
        }
    }

    /** Start countDown to display/hide navigation text **/
    fun showNavigationText() = viewModelScope.launch {
        showNavigationText.mutable.postValue(true)

        countDown += duration
        delay(duration.milliseconds)
        countDown = max(0L, countDown.minus(duration))

        showNavigationText.mutable.postValue(countDown > 0L)
    }

    // -------------------------------------

    /** Update error message **/
    fun updateErrorMessage(errorMessage: String?) {
        error.mutable.postValue(errorMessage)
    }
}