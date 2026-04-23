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
import fr.hellpc.mirror.data.repositories.Repository_Autofill
import fr.hellpc.mirror.data.room.BackupTarget_Credentials
import kotlinx.coroutines.launch

class ViewModel_Autofill(private val repository: Repository_Autofill): ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = (this[APPLICATION_KEY] as App).repositoryAutofill
                ViewModel_Autofill(repository = repository)
            }
        }
    }

    private val <T> LiveData<T>.mutable: MutableLiveData<T> get() = this as MutableLiveData<T>

    val credentials: LiveData<List<BackupTarget_Credentials>> by lazy { MutableLiveData() }

    fun loadCredentials(protocol: String?) = viewModelScope.launch {
        if(protocol != null)
            credentials.mutable.postValue(repository.getBackupTargetCredentials(protocol))
    }
}