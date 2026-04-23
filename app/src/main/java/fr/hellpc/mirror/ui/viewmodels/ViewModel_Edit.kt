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
import fr.hellpc.mirror.data.repositories.Repository_Edit
import fr.hellpc.mirror.data.room.Backup_Colors
import fr.hellpc.mirror.data.room.Backup_Data
import fr.hellpc.mirror.data.room.Backup_Options
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.managers.Manager_Workers
import fr.hellpc.mirror.utilities.Utility_BackupTarget
import kotlinx.coroutines.launch
import java.time.LocalTime


class ViewModel_Edit(private val repository: Repository_Edit): ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = (this[APPLICATION_KEY] as App).repositoryEdit
                ViewModel_Edit(repository = repository)
            }
        }
    }

    private val <T> LiveData<T>.mutable: MutableLiveData<T> get() = this as MutableLiveData<T>

    private val managerWorkers by lazy { Manager_Workers() }
    private val editUtils by lazy { Utility_BackupTarget() }

    // -------------------------------------

    val srcTabName by lazy { "Source" }
    val destTabName by lazy { "Destination" }

    private var backupId = -1
    private var isDisabled = false
    private var position = 0
    private var srcCurrentProtocol = ""
    private var destCurrentProtocol = ""

    // -------------------------------------

    val backupSrcProtocol: LiveData<String> by lazy { MutableLiveData() }
    val backupDestProtocol: LiveData<String> by lazy { MutableLiveData() }
    val backupSrc: LiveData<Backup_Target> by lazy { MutableLiveData() }
    val backupDest: LiveData<Backup_Target> by lazy { MutableLiveData() }
    val backupOpt: LiveData<Backup_Options> by lazy { MutableLiveData() }
    val backupColors: LiveData<Backup_Colors> by lazy { MutableLiveData() }

    val backupLocked: LiveData<Boolean> by lazy { MutableLiveData() }
    val securityFailed: LiveData<Boolean> by lazy { MutableLiveData() }

    val optWifiIsVisible: LiveData<Boolean> by lazy { MutableLiveData() }
    val optNomediaIsVisible: LiveData<Boolean> by lazy { MutableLiveData() }
    val webdavRemoteWarningIsVisible: LiveData<Boolean> by lazy { MutableLiveData() }
    val infoSrcTimeZoneIsVisible: LiveData<Boolean> by lazy { MutableLiveData() }
    val infoDestTimeZoneIsVisible: LiveData<Boolean> by lazy { MutableLiveData() }
    val timePickerValue: LiveData<LocalTime> by lazy { MutableLiveData() }

    val srcCredentialsExist: LiveData<Boolean> by lazy { MutableLiveData() }
    val destCredentialsExist: LiveData<Boolean> by lazy { MutableLiveData() }

    val sftpHostKeyLoadingIsVisible: LiveData<Boolean> by lazy { MutableLiveData() }
    val sftpHostKeyError: LiveData<String> by lazy { MutableLiveData() }


    // -----------
    // Load Backup
    // -----------

    /** Initialise backup ViewModel **/
    fun initialise(id: Int, isProtected: Boolean) = viewModelScope.launch {
        if(id != -1) {
            backupId = id
            checkSecurity(repository.backupIsProtected(id), isProtected)
        }
        else {
            getNextPosition()
            loadScheduledTime(null, null)
        }
    }

    /** Use this to reload database observers in case of screen rotation while biometric prompt is visible **/
    fun backupIsLocked() = backupLocked.value ?: true

    /** Unlock backup **/
    fun unlockBackup(authenticated: Boolean) {
        if(authenticated) {
            backupLocked.mutable.value = false
            securityFailed.mutable.value = false
            loadBackup(true)
        }
        else
            securityFailed.mutable.value = true
    }

    /** Check if backup can be loaded **/
    private fun checkSecurity(isProtectedFromDB: Boolean, isProtectedFromCall: Boolean) {
        if(isProtectedFromDB != isProtectedFromCall)
            securityFailed.mutable.value = true
        else if(isProtectedFromDB)
            backupLocked.mutable.value = true
        else {
            backupLocked.mutable.value = false
            securityFailed.mutable.value = false
            loadBackup(false)
        }
    }

    /** Load backup from database **/
    private fun loadBackup(isProtectedFromDB: Boolean) = viewModelScope.launch {
        if(backupLocked.value == false && securityFailed.value == false) {
            val backupData = repository.loadBackup(backupId)

            if(backupData.options.protectEdition != isProtectedFromDB)
                securityFailed.mutable.value = true
            else {
                backupSrcProtocol.mutable.postValue(backupData.source.protocol)
                backupDestProtocol.mutable.postValue(backupData.destination.protocol)
                backupSrc.mutable.postValue(backupData.source)
                backupDest.mutable.postValue(backupData.destination)
                backupOpt.mutable.postValue(backupData.options)
                backupColors.mutable.postValue(backupData.colors)

                isDisabled = backupData.isDisabled
                position = backupData.position

                loadScheduledTime(backupData.options.hour, backupData.options.minute)
            }
        }
    }

    /** Check if credentials already exist in Database **/
    fun checkBackupCredentials(isSource: Boolean, protocol: String) = viewModelScope.launch {
        if(isSource)
            srcCredentialsExist.mutable.postValue(repository.backupTargetCredentialsExist(protocol))
        else
            destCredentialsExist.mutable.postValue(repository.backupTargetCredentialsExist(protocol))
    }

    /** Find next backup position number inside recyclerView **/
    private fun getNextPosition() = viewModelScope.launch {
        repository.getBackupMaxPosition()?.let { position = it + 1 }
    }

    /** Recover the scheduled backup time **/
    fun loadScheduledTime(hour: Int?, minute: Int?) = viewModelScope.launch {
        val time = if(hour != null && minute != null)
            LocalTime.of(hour, minute)
        else
            LocalTime.now().plusHours(1).withMinute(0)

        timePickerValue.mutable.postValue(time)
    }


    // -----------
    // Update Data
    // -----------

    /** When user selects a "Location" from spinner **/
    fun protocolHasChanged(origin: String, protocol: String): Boolean {
        val hasChanged = protocol != if(origin == srcTabName) srcCurrentProtocol else destCurrentProtocol

        return if(hasChanged) {
            if(origin == srcTabName)
                srcCurrentProtocol = protocol
            else {
                setNomediaOptionVisibility(protocol)
                destCurrentProtocol = protocol
            }

            setWebdavRemoteWarningVisibility(srcCurrentProtocol, destCurrentProtocol)
            setWifiOptionVisibility(srcCurrentProtocol, destCurrentProtocol)

            true
        }
        else
            false
    }


    // -----------
    // Save Backup
    // -----------

    /** Save backup to Database **/
    fun saveBackup(src: Backup_Target, dest: Backup_Target, opt: Backup_Options, colors: Backup_Colors) = viewModelScope.launch {
        val fixedOpt = manageOptions(src.protocol, dest.protocol, opt)
        if(backupId == -1)
            insertBackup(Backup_Data(isDisabled = isDisabled, position = position, source = src, destination = dest, options = fixedOpt, colors = colors))
        else
            updateBackup(Backup_Data(id = backupId, isDisabled = isDisabled, position = position, source = src, destination = dest, options = fixedOpt, colors = colors))
        setScheduler(fixedOpt)
    }

    /** Insert new backup to Database **/
    private suspend fun insertBackup(backup: Backup_Data) {
        val rowid = repository.insertBackup(backup)
        backupId = repository.getBackupId(rowid)
    }

    /** Update existing backup in Database **/
    private suspend fun updateBackup(backup: Backup_Data) {
        repository.updateBackup(backup)
    }

    /** Update backup status to prevent launching scheduled backup while editing **/
    fun updateStatus(id: Int, status: String) = viewModelScope.launch {
        if(id != -1)
            repository.updateStatus(id, status)
    }

    // -------------------------------------

    /**  Manage options and apply corrections if necessary */
    private fun manageOptions(protocolSrc: String, protocolDest: String, opt: Backup_Options): Backup_Options {
        val optNomedia = if(protocolDest != "LOCAL" && opt.nomedia) false else opt.nomedia
        val optWifi = if(protocolSrc == "LOCAL" && protocolDest == "LOCAL" && opt.const_wifi) false else opt.const_wifi
        val optCharging = if(!opt.schedule) false else opt.const_charging
        val optIdle = if(!opt.schedule) false else opt.const_idle

        var minSize = opt.flt_minSize
        var maxSize = opt.flt_maxSize
        if(opt.flt_minSize != null && opt.flt_maxSize != null && opt.flt_minSize > opt.flt_maxSize) {
            minSize = opt.flt_maxSize
            maxSize = opt.flt_minSize
        }

        val blackList = opt.flt_blackList?.let { editUtils.trimPathsGlob(it).ifBlank { null } }

        val hour = if(opt.schedule && opt.interval_unit == 0) timePickerValue.value?.hour else null
        val minute = if(opt.schedule && opt.interval_unit == 0) timePickerValue.value?.minute else null

        return Backup_Options(
            opt.protectEdition,
            opt.recursive,
            opt.retryCorrupted,
            opt.dateComparison_Auto,
            opt.dateComparison_Strict,
            optNomedia,
            opt.orph_action,
            opt.orph_folder,
            opt.flt_images,
            opt.flt_audio,
            opt.flt_videos,
            opt.flt_documents,
            opt.flt_others,
            minSize,
            maxSize,
            blackList,
            opt.schedule,
            opt.interval,
            opt.interval_unit,
            hour,
            minute,
            optWifi,
            optCharging,
            optIdle
        )
    }


    // ---------
    // Scheduler
    // ---------

    /** Schedule backup **/
    private suspend fun setScheduler(opt: Backup_Options) {
        // Existing backup
        if(backupId != -1 && backupSrc.value != null && backupDest.value != null) {
            val status = repository.loadStatus(backupId)

            require(status != null)

            if(status.id_scheduler != null)
                managerWorkers.cancelScheduledWorker(backupId, status.id_scheduler)
            // New scheduler
            if (opt.schedule && !status.isDisabled)
                managerWorkers.setScheduler(backupId, opt.const_wifi, opt.const_charging, opt.const_idle, opt.interval, opt.interval_unit, opt.hour, opt.minute, status.last_date)
        }
        // New backup
        else if(opt.schedule)
            managerWorkers.setScheduler(backupId, opt.const_wifi, opt.const_charging, opt.const_idle, opt.interval, opt.interval_unit, opt.hour, opt.minute, null)
    }


    // -------------
    // UI visibility
    // -------------

    /** Manage "Only on WiFi" option visibility **/
    private fun setWifiOptionVisibility(source: String, destination: String) = viewModelScope.launch {
        optWifiIsVisible.mutable.postValue(source != "LOCAL" || destination != "LOCAL")
    }

    /** Manage "Hide Destination from gallery" option visibility **/
    private fun setNomediaOptionVisibility(destination: String) = viewModelScope.launch {
        optNomediaIsVisible.mutable.postValue(destination == "LOCAL")
    }

    /** Manage WebDav warning option visibility **/
    private fun setWebdavRemoteWarningVisibility(source: String, destination: String) = viewModelScope.launch {
        webdavRemoteWarningIsVisible.mutable.postValue(source != "LOCAL" && destination == "WDAV")
    }

    /** Manage Time zone information visibility **/
    fun setInfoTimeZoneVisibility(tabIsSource: Boolean, path: String) = viewModelScope.launch {
        if(path.isBlank() || path.startsWith("/storage/emulated/0/")) {
            if(tabIsSource)
                infoSrcTimeZoneIsVisible.mutable.postValue(false)
            else
                infoDestTimeZoneIsVisible.mutable.postValue(false)
        }
        else {
            if(tabIsSource)
                infoSrcTimeZoneIsVisible.mutable.postValue(true)
            else
                infoDestTimeZoneIsVisible.mutable.postValue(true)
        }
    }


    // ----------
    // Appearance
    // ----------

    /** Recover user customised themes from database **/
    suspend fun getUserThemes() = repository.getUserThemes().filterNot {
        // Remove default themes
        listOf(it.background, it.borders, it.icons, it.progressbar).distinct().size == 1
    }


    // -------------
    // SFTP host key
    // -------------

    /** Retrieve SFTP server host key and format it **/
    suspend fun getSftpHostKey(server: String, port: Int, login: String, password: String): String? {
        sftpHostKeyLoadingIsVisible.mutable.postValue(true)

        return try {
            val hostKey = repository.getSftpHostKey(server, port, login, password)
            hostKey.host.toString() + " " + hostKey.type.toString() + " " + hostKey.key.toString()
        }
        catch(exp: Exception) {
            sftpHostKeyError.mutable.postValue(exp.message.toString())
            null
        }
        finally {
            sftpHostKeyLoadingIsVisible.mutable.postValue(false)
        }
    }

    /** Reset SFTP host key error message **/
    fun resetSftpHostKeyError() = sftpHostKeyError.mutable.postValue(null)
}