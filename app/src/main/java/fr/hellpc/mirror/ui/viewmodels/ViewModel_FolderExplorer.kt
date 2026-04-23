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
import fr.hellpc.mirror.data.repositories.Repository_FolderExplorer
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.data.FolderExplorer_File
import kotlinx.coroutines.launch

class ViewModel_FolderExplorer(private val repository: Repository_FolderExplorer): ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = (this[APPLICATION_KEY] as App).repositoryFolderExplorer
                ViewModel_FolderExplorer(repository = repository)
            }
        }
    }

    private val <T> LiveData<T>.mutable: MutableLiveData<T> get() = this as MutableLiveData<T>

    // -------------------------------------

    val path: LiveData<String> by lazy { MutableLiveData() }
    val folderList: LiveData<List<FolderExplorer_File>> by lazy { MutableLiveData() }
    val isLoading: LiveData<Boolean> by lazy { MutableLiveData() }
    val errorMsg: LiveData<String?> by lazy { MutableLiveData() }

    private var _isConnected = false
    val isConnected get() = _isConnected

    // -------------------------------------

    fun initialize(target: Backup_Target) {
        repository.initialize(target)
        path.mutable.value = target.path
    }

    suspend fun connect() {
        _isConnected = repository.connect()
    }

    suspend fun disconnect() {
        _isConnected = repository.disconnect()
    }

    suspend fun loadFolder(open: String?) {
        if(isConnected) {
            path.value?.let { currentPath ->
                isLoading.mutable.postValue(true)

                val newPath = getNewPath(currentPath, open)
                if(currentPath != newPath)
                    path.mutable.postValue(newPath)

                val newList: MutableList<FolderExplorer_File> = mutableListOf()
                if(newPath.isNotEmpty())
                    newList.add(FolderExplorer_File("..", true))
                newList.addAll(repository.loadFolder(newPath))

                folderList.mutable.postValue(newList)
                isLoading.mutable.postValue(false)
                errorMsg.mutable.postValue(null)
            }
        }
    }

    // -------------------------------------
    
    private fun getNewPath(currentPath: String, open: String?): String {
        return if(open.isNullOrBlank())
            currentPath
        else if(open == "..") {
            if(currentPath.contains("/"))
                currentPath.substringBeforeLast("/")
            else
                ""
        }
        else {
            if(currentPath.isEmpty())
                open
            else
                "$currentPath/$open"
        }
    }

    fun showError(error: String) = viewModelScope.launch {
        errorMsg.mutable.postValue(error)

        path.value?.let { currentPath ->
            val newList: MutableList<FolderExplorer_File> = mutableListOf()
            if(currentPath.isNotEmpty())
                newList.add(FolderExplorer_File("..", true))
            folderList.mutable.postValue(newList)
        }
    }
}