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

package fr.hellpc.mirror.ui.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.BackupBtn_Action
import fr.hellpc.mirror.data.BackupBtn_Infos
import fr.hellpc.mirror.data.room.Backup_Infos
import fr.hellpc.mirror.databinding.RowRecyclerMainBinding
import fr.hellpc.mirror.utilities.Utility_BackupTarget
import fr.hellpc.mirror.utilities.Utility_Conversion.sizeToReadable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class Adapter_Recycler_Main(val buttonClick: (BackupBtn_Action) -> Unit, val moveItem: (RecyclerView.ViewHolder) -> Unit, val itemMoveDone: () -> Unit): ListAdapter<Backup_Infos, Adapter_Recycler_Main.MainRecyclerViewHolder>(BackupsComparator()) {

    private lateinit var context: Context
    private val utilityPathFormat by lazy { Utility_BackupTarget() }
    private val scopeMain by lazy { CoroutineScope(Job() + Dispatchers.Main) }

    private val colorValueBackground by lazy { context.resources.getIntArray(R.array.color_value_background).toList() }
    private val colorValueBorders by lazy { context.resources.getIntArray(R.array.color_value_borders).toList() }
    private val colorValueIcons by lazy { context.resources.getIntArray(R.array.color_value_icons).toList() }
    private val colorValueProgressbarSecondary by lazy { context.resources.getIntArray(R.array.color_value_progressbar_secondary).map { ColorStateList.valueOf(it) } }
    private val colorBlue by lazy { ColorStateList.valueOf(ContextCompat.getColor(context, R.color.blue)) }

    private val colorButtonInfo by lazy { mapOf(
        "green" to ContextCompat.getColor(context, R.color.green),
        "orange" to ContextCompat.getColor(context, R.color.orange),
        "red" to ContextCompat.getColor(context, R.color.red)
    ) }

    private val btnIcon by lazy { mapOf(
        "edit" to getDrawable(context, R.drawable.ic_edit),
        "edit_protected" to getDrawable(context, R.drawable.ic_edit_protected),
        "disabled" to getDrawable(context, R.drawable.ic_disabled),
        "cancel" to getDrawable(context, R.drawable.ic_close),
        "launch" to getDrawable(context, R.drawable.ic_sync)
    ) }

    private val itemsToMove by lazy { mutableListOf<Int>() }

    // -------------------------------------

    override fun getItemId(position: Int) = getItem(position).id_backup.hashCode().toLong()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainRecyclerViewHolder {
        context = parent.context

        val binding = RowRecyclerMainBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = MainRecyclerViewHolder(binding)

        binding.rowMainTopImgDrag.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN)
                moveItem(viewHolder)
            true
        }

        return viewHolder
    }

    override fun onBindViewHolder(holder: MainRecyclerViewHolder, position: Int, payloads: MutableList<Any>) {
        when(val latestPayload = payloads.lastOrNull()) {
            is BackupPayload.Status -> holder.updateStatus(latestPayload)
            is BackupPayload.ProgressAndStatus -> holder.updateProgressAndStatus(latestPayload)
            is BackupPayload.ResultAndStatus -> holder.updateResultAndStatus(latestPayload)
            is BackupPayload.SettingsAndStatus -> holder.updateSettingsAndStatus(latestPayload)
            else -> onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: MainRecyclerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // -------------------------------------

    fun observeItemMove(from: Int, to: Int) {
        itemsToMove.add(from)
        itemsToMove.add(to)
    }

    // -------------------------------------

    inner class MainRecyclerViewHolder(private val binding: RowRecyclerMainBinding) : RecyclerView.ViewHolder(binding.root) {

        private var editMode = false
        private var currentStatus = ""

        fun bind(item: Backup_Infos) {
            editMode = false
            setupListeners()

            scopeMain.launch {
                updateStatus(
                    BackupPayload.Status(
                        item.status,
                        item.isDisabled,
                        item.position
                    )
                )
            }

            scopeMain.launch {
                updateSettings(
                    BackupPayload.Settings(
                        BackupPayload.Constraints(
                            item.protectEdition,
                            item.schedule,
                            item.const_wifi,
                            item.const_charging,
                            item.const_idle
                        ),
                        BackupPayload.Target(
                            item.src_protocol,
                            item.src_ssl,
                            item.src_share,
                            item.src_path
                        ),
                        BackupPayload.Target(
                            item.dest_protocol,
                            item.dest_ssl,
                            item.dest_share,
                            item.dest_path
                        ),
                        BackupPayload.Theme(
                            item.color_background,
                            item.color_borders,
                            item.color_progressbar,
                            item.color_icons
                        )
                    )
                )
            }

            when(item.status) {
                "idle" -> updateResult(
                    BackupPayload.Result(
                        item.last_result,
                        item.last_date,
                        item.last_files_count,
                        item.last_files_size,
                        item.last_orphans_count,
                        item.last_orphans_size
                    )
                )
                "init", "copy" -> updateProgress(
                    BackupPayload.Progress(
                        item.progress_confirmed,
                        item.progress_current,
                        item.progress_detail_1,
                        item.progress_detail_2,
                        item.progress_detail_3
                    )
                )
            }
        }


        // ------
        // Status
        // ------

        /** Update backup status **/
        fun updateStatus(status: BackupPayload.Status) {
            currentStatus = status.status

            // Disable Edit mode if necessary
            if(editMode && (status.status != "idle" || status.isDisabled)) {
                editMode = false
                val item = getItem(getBindingAdapterPosition())
                val constraints = BackupPayload.Constraints(item.protectEdition, item.schedule, item.const_wifi, item.const_charging, item.const_idle)
                refreshTopIcons(constraints)
            }

            // Card transparency
            if(status.status == "idle")
                binding.rowMainCard.alpha = if(status.isDisabled) 0.6f else 1f

            // Backup queued
            if(status.status == "queue") {
                binding.rowMainBottomTxtDetail1.visibility = View.GONE
                binding.rowMainBottomTxtDetail2.visibility = View.GONE
                binding.rowMainBottomTxtDetail3.visibility = View.GONE
                binding.rowMainBottomImgEnqueued.visibility = View.VISIBLE
            }
            else
                binding.rowMainBottomImgEnqueued.visibility = View.GONE

            // Progressbar
            binding.rowMainBar.apply {
                isIndeterminate = status.status == "init"
                if(status.status == "idle")
                    progress = 100
            }

            // Disable logs button when backup is started
            if(status.status == "queue" || status.status == "init")
                refreshButtonInfo("nc")

            refreshButtons(status.status, status.isDisabled)
            checkItemMoveDone(status.position)
        }


        // --------
        // Settings
        // --------

        /** Update settings and status **/
        fun updateSettingsAndStatus(settingsAndStatus: BackupPayload.SettingsAndStatus) {
            scopeMain.launch {
                if(settingsAndStatus.status.status != currentStatus)
                    updateStatus(settingsAndStatus.status)
            }
            updateSettings(settingsAndStatus.settings)
        }

        /** Update backup settings **/
        private fun updateSettings(settings: BackupPayload.Settings) {
            applyColorTheme(settings.theme)
            refreshTargets(settings.source, settings.destination)
            refreshTopIcons(settings.constraints)
            refreshButtonEdit(settings.constraints.protectEdition)
        }

        /** Refresh source and destination views **/
        private fun refreshTargets(src: BackupPayload.Target, dest: BackupPayload.Target) = scopeMain.launch {
            // Icons
            binding.rowMainTopImgSource.setImageDrawable(getDrawable(context, utilityPathFormat.getProtocolIcon(src.protocol, src.path)))
            binding.rowMainTopImgDestination.setImageDrawable(getDrawable(context, utilityPathFormat.getProtocolIcon(dest.protocol, dest.path)))

            // Text Source
            binding.rowMainTopTxtSourceProtocol.apply {
                if(src.protocol != "LOCAL") {
                    text = utilityPathFormat.getReadableProtocol(src.protocol, src.ssl, src.path)
                    visibility = View.VISIBLE
                }
                else
                    visibility = View.GONE
            }
            binding.rowMainTopTxtSourcePath.text = utilityPathFormat.getReadablePath(src.protocol, src.share, src.path)

            // Text Destination
            binding.rowMainTopTxtDestinationProtocol.apply {
                if(dest.protocol != "LOCAL") {
                    text = utilityPathFormat.getReadableProtocol(dest.protocol, dest.ssl, dest.path)
                    visibility = View.VISIBLE
                }
                else
                    visibility = View.GONE
            }
            binding.rowMainTopTxtDestinationPath.text = utilityPathFormat.getReadablePath(dest.protocol, dest.share, dest.path)
        }

        /** Refresh top icons **/
        private fun refreshTopIcons(constraints: BackupPayload.Constraints) = scopeMain.launch {
            binding.rowMainTopImgWifi.visibility = if(!editMode && constraints.wifi) View.VISIBLE else View.GONE
            binding.rowMainTopImgSchedule.visibility = if(!editMode && constraints.schedule) View.VISIBLE else View.GONE
            binding.rowMainTopImgCharging.visibility = if(!editMode && constraints.charging) View.VISIBLE else View.GONE
            binding.rowMainTopImgIdle.visibility = if(!editMode && constraints.idle) View.VISIBLE else View.GONE
            binding.rowMainTopImgDrag.visibility = if(editMode && itemCount > 1) View.VISIBLE else View.GONE
        }

        /** Apply color theme **/
        private fun applyColorTheme(theme: BackupPayload.Theme) = scopeMain.launch {
            // Background
            binding.rowMainCard.apply {
                setCardBackgroundColor(colorValueBackground[theme.background])
                val colorBorders = theme.borders.takeIf { it > 0 } ?: theme.background
                strokeColor = colorValueBorders[colorBorders]
                strokeWidth = if(theme.borders > 0) 2 else 0
            }

            // Progressbar
            theme.progressbar.let {
                binding.rowMainBar.apply {
                    progressTintList = if(it > 0) ColorStateList.valueOf(colorValueBorders[it]) else colorBlue
                    indeterminateTintList = if(it > 0) ColorStateList.valueOf(colorValueBorders[it]) else colorBlue
                    secondaryProgressTintList = colorValueProgressbarSecondary[it]
                }
            }

            theme.icons.let {
                // Left icons
                binding.rowMainTopImgSource.setColorFilter(colorValueIcons[it])
                binding.rowMainTopImgDestination.setColorFilter(colorValueIcons[it])
                binding.rowMainTopTxtSourceProtocol.setTextColor(colorValueIcons[it])
                binding.rowMainTopTxtDestinationProtocol.setTextColor(colorValueIcons[it])

                // Right icons
                binding.rowMainTopImgWifi.setColorFilter(colorValueIcons[it])
                binding.rowMainTopImgSchedule.setColorFilter(colorValueIcons[it])
                binding.rowMainTopImgCharging.setColorFilter(colorValueIcons[it])
                binding.rowMainTopImgIdle.setColorFilter(colorValueIcons[it])
            }
        }


        // ------
        // Result
        // ------

        /** Update result and status **/
        fun updateResultAndStatus(resultAndStatus: BackupPayload.ResultAndStatus) {
            scopeMain.launch {
                if(resultAndStatus.status.status != currentStatus)
                    updateStatus(resultAndStatus.status)
            }
            updateResult(resultAndStatus.result)
        }

        /** Update backup result **/
        private fun updateResult(result: BackupPayload.Result) {
            // First line of text
            binding.rowMainBottomTxtDetail1.apply {
                if(result.last_date != null) {
                    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    val lastBackup = Instant.ofEpochMilli(result.last_date).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    val txtLastDateValue = context.resources.getString(R.string.backup_last_date)+" "+formatter.format(lastBackup)

                    text = txtLastDateValue
                    visibility = View.VISIBLE
                }
                else
                    visibility = View.GONE
            }

            // Second line of text
            binding.rowMainBottomTxtDetail2.apply {
                if(result.last_files_count > 0) {
                    var txtLastFilesResultValue = context.resources.getString(R.string.notification_files)+": "+result.last_files_count
                    if(result.last_files_size > 0L)
                        txtLastFilesResultValue += " ("+ result.last_files_size.sizeToReadable()+")"

                    text = txtLastFilesResultValue
                    visibility = View.VISIBLE
                }
                else
                    visibility = View.GONE
            }

            // Third line of text
            binding.rowMainBottomTxtDetail3.apply {
                if(result.last_orphans_count > 0) {
                    var txtLastOrphansResultValue = context.resources.getString(R.string.notification_orphans)+": "+result.last_orphans_count
                    if(result.last_orphans_size > 0L)
                        txtLastOrphansResultValue += " ("+ result.last_orphans_size.sizeToReadable()+")"

                    text = txtLastOrphansResultValue
                    visibility = View.VISIBLE
                }
                else
                    visibility = View.GONE
            }

            refreshButtonInfo(result.last_result)
        }


        // --------
        // Progress
        // --------

        /** Update progress and status **/
        fun updateProgressAndStatus(progressAndStatus: BackupPayload.ProgressAndStatus) {
            scopeMain.launch {
                if(progressAndStatus.status.status != currentStatus)
                    updateStatus(progressAndStatus.status)
            }
            updateProgress(progressAndStatus.progress)
        }

        /** Update backup progress **/
        private fun updateProgress(progress: BackupPayload.Progress) {
            // Progressbar
            binding.rowMainBar.apply {
                this.progress = progress.confirmed
                secondaryProgress = progress.current
            }

            // First line of text
            binding.rowMainBottomTxtDetail1.apply {
                if(progress.detail_1.isNullOrBlank())
                    visibility = View.GONE
                else {
                    text = progress.detail_1
                    visibility = View.VISIBLE
                }
            }

            // Second line of text
            binding.rowMainBottomTxtDetail2.apply {
                if(progress.detail_2.isNullOrBlank())
                    visibility = View.GONE
                else {
                    text = progress.detail_2
                    visibility = View.VISIBLE
                }
            }

            // Third line of text
            binding.rowMainBottomTxtDetail3.apply {
                if(progress.detail_3.isNullOrBlank())
                    visibility = View.GONE
                else {
                    text = progress.detail_3
                    visibility = View.VISIBLE
                }
            }
        }


        // -------
        // Buttons
        // -------

        /** Update info button color and availability depending on last backup result **/
        private fun refreshButtonInfo(lastResult: String) {
            binding.rowMainBottomBtnInfo.apply {
                if(lastResult != "nc") {
                    when (lastResult) {
                        "success" -> colorButtonInfo["green"]?.let { setColorFilter(it) }
                        "warning", "cancel" -> colorButtonInfo["orange"]?.let { setColorFilter(it) }
                        "failure" -> colorButtonInfo["red"]?.let { setColorFilter(it) }
                        else -> clearColorFilter()
                    }
                    isEnabled = true
                }
                else {
                    clearColorFilter()
                    isEnabled = false
                }
            }
        }

        /** Refresh edit button icon depending on protect edition setting **/
        private fun refreshButtonEdit(protectEdition: Boolean) {
            binding.rowMainBottomBtnEdit.apply {
                if(protectEdition)
                    setImageDrawable(btnIcon["edit_protected"])
                else
                    setImageDrawable(btnIcon["edit"])
            }
        }

        /** Refresh buttons visibility and availability **/
        private fun refreshButtons(status: String, isDisabled: Boolean) = scopeMain.launch {
            binding.rowMainBottomBtnEdit.visibility = if(editMode) View.VISIBLE else View.GONE

            binding.rowMainBottomBtnBackup.apply {
                if(isDisabled) {
                    isEnabled = false
                    setImageDrawable(btnIcon["disabled"])
                }
                else {
                    if(status != "idle" && status != "edit")
                        setImageDrawable(btnIcon["cancel"])
                    else
                        setImageDrawable(btnIcon["launch"])
                    isEnabled = true
                }
                visibility = if(editMode) View.GONE else View.VISIBLE
            }

            binding.rowMainBottomBtnInfo.visibility = if(editMode) View.GONE else View.VISIBLE

            binding.rowMainBottomBtnDelete.visibility = if(editMode) View.VISIBLE else View.GONE
        }


        // ------------
        // Interactions
        // ------------

        /** When user interacts with interface **/
        private fun performAction(view: View, isLongPress: Boolean) = scopeMain.launch {
            val item = getItem(getBindingAdapterPosition())
            val btnParams = BackupBtn_Infos(item.id_backup, item.last_date, item.protectEdition, item.const_wifi, item.id_worker, item.id_scheduler, item.position)
            
            when(view) {
                binding.rowMainBottomBtnBackup -> {
                    if(item.status != "idle" && item.status != "edit")
                        buttonClick(BackupBtn_Action("cancel", btnParams))
                    else
                        buttonClick(BackupBtn_Action("launch", btnParams))
                }

                binding.rowMainBottomBtnEdit -> {
                    if(editMode)
                        buttonClick(BackupBtn_Action("edit", btnParams))
                }

                binding.rowMainBottomBtnInfo -> {
                    if(item.last_result != "nc" && item.status == "idle")
                        buttonClick(BackupBtn_Action("log", btnParams))
                }

                binding.rowMainBottomBtnDelete -> {
                    if(editMode)
                        buttonClick(BackupBtn_Action("delete", btnParams))
                }

                binding.rowMainTopLyt -> {
                    if(isLongPress) {
                        if(item.isDisabled)
                            buttonClick(BackupBtn_Action("enable", btnParams))
                        else
                            buttonClick(BackupBtn_Action("disable", btnParams))
                    }
                    else {
                        if(item.status == "idle" && !item.isDisabled) {
                            editMode = !editMode
                            refreshButtons(item.status, item.isDisabled)
                            val constraints = BackupPayload.Constraints(item.protectEdition, item.schedule, item.const_wifi, item.const_charging, item.const_idle)
                            refreshTopIcons(constraints)
                        }
                    }
                }
            }
        }

        /** Check if recyclerView is done refreshing after item position change **/
        private fun checkItemMoveDone(position: Int) {
            if(itemsToMove.remove(position) && itemsToMove.isEmpty())
                itemMoveDone()
        }

        /** Setup listeners for buttons and top half of the card **/
        private fun setupListeners() = scopeMain.launch {
            binding.rowMainTopLyt.apply {
                // Edit/backup mode
                setOnClickListener { performAction(this, false) }

                // Enable/disable backup
                setOnLongClickListener {
                    performAction(this, true)
                    true
                }
            }

            binding.rowMainBottomBtnEdit.apply { setOnClickListener { performAction(this, false) } }
            binding.rowMainBottomBtnBackup.apply { setOnClickListener { performAction(this, false) } }
            binding.rowMainBottomBtnInfo.apply { setOnClickListener { performAction(this, false) } }
            binding.rowMainBottomBtnDelete.apply { setOnClickListener { performAction(this, false) } }
        }
    }


    // ----------
    // Comparator
    // ----------

    private class BackupsComparator : DiffUtil.ItemCallback<Backup_Infos>() {
        override fun areItemsTheSame(oldItem: Backup_Infos, newItem: Backup_Infos): Boolean = oldItem.id_backup == newItem.id_backup

        override fun areContentsTheSame(oldItem: Backup_Infos, newItem: Backup_Infos): Boolean {
            val sameStatus = oldItem.status == newItem.status
                    && oldItem.isDisabled == newItem.isDisabled
                    && oldItem.position == newItem.position
            if(!sameStatus)
                return false

            val sameProgress = oldItem.progress_confirmed == newItem.progress_confirmed
                    && oldItem.progress_current == newItem.progress_current
                    && oldItem.progress_detail_1 == newItem.progress_detail_1
                    && oldItem.progress_detail_2 == newItem.progress_detail_2
                    && oldItem.progress_detail_3 == newItem.progress_detail_3
            if(!sameProgress)
                return false

            val sameTheme = newItem.color_background == oldItem.color_background
                    && newItem.color_borders == oldItem.color_borders
                    && newItem.color_progressbar == oldItem.color_progressbar
                    && newItem.color_icons == oldItem.color_icons
            if(!sameTheme)
                return false

            val sameConstraints = newItem.protectEdition == oldItem.protectEdition
                    && newItem.schedule == oldItem.schedule
                    && newItem.const_wifi == oldItem.const_wifi
                    && newItem.const_charging == oldItem.const_charging
                    && newItem.const_idle == oldItem.const_idle
            if(!sameConstraints)
                return false

            val sameDest = newItem.dest_protocol == oldItem.dest_protocol
                    && newItem.dest_ssl == oldItem.dest_ssl
                    && newItem.dest_share == oldItem.dest_share
                    && newItem.dest_path == oldItem.dest_path
            if(!sameDest)
                return false

            val sameSrc = newItem.src_protocol == oldItem.src_protocol
                    && newItem.src_ssl == oldItem.src_ssl
                    && newItem.src_share == oldItem.src_share
                    && newItem.src_path == oldItem.src_path
            if(!sameSrc)
                return false

            val sameResult = newItem.last_result == oldItem.last_result
                    && newItem.last_date == oldItem.last_date
                    && newItem.last_files_count == oldItem.last_files_count
                    && newItem.last_files_size == oldItem.last_files_size
                    && newItem.last_orphans_count == oldItem.last_orphans_count
                    && newItem.last_orphans_size == oldItem.last_orphans_size
            return sameResult
        }

        override fun getChangePayload(oldItem: Backup_Infos, newItem: Backup_Infos): Any {
            val newStatus = BackupPayload.Status(
                newItem.status,
                newItem.isDisabled,
                newItem.position
            )

            // Possible status: "edit", "idle", "queue", "init", "copy"
            return when(newItem.status) {
                "edit", "queue" -> newStatus
                "init", "copy" -> {
                    BackupPayload.ProgressAndStatus(
                        newStatus,
                        BackupPayload.Progress(
                            newItem.progress_confirmed,
                            newItem.progress_current,
                            newItem.progress_detail_1,
                            newItem.progress_detail_2,
                            newItem.progress_detail_3
                        )
                    )
                }
                else -> {   // "idle"
                    if(oldItem.isDisabled != newItem.isDisabled || oldItem.position != newItem.position)
                        newStatus
                    else if(oldItem.status == "edit")
                        BackupPayload.SettingsAndStatus(
                            newStatus,
                            BackupPayload.Settings(
                                BackupPayload.Constraints(
                                    newItem.protectEdition,
                                    newItem.schedule,
                                    newItem.const_wifi,
                                    newItem.const_charging,
                                    newItem.const_idle
                                ),
                                BackupPayload.Target(
                                    newItem.src_protocol,
                                    newItem.src_ssl,
                                    newItem.src_share,
                                    newItem.src_path
                                ),
                                BackupPayload.Target(
                                    newItem.dest_protocol,
                                    newItem.dest_ssl,
                                    newItem.dest_share,
                                    newItem.dest_path
                                ),
                                BackupPayload.Theme(
                                    newItem.color_background,
                                    newItem.color_borders,
                                    newItem.color_progressbar,
                                    newItem.color_icons
                                )
                            )
                        )
                    else
                        BackupPayload.ResultAndStatus(
                            newStatus,
                            BackupPayload.Result(
                                newItem.last_result,
                                newItem.last_date,
                                newItem.last_files_count,
                                newItem.last_files_size,
                                newItem.last_orphans_count,
                                newItem.last_orphans_size
                            )
                        )
                }
            }
        }
    }


    // -------
    // Payload
    // -------

    sealed interface BackupPayload {
        data class Status(
            val status: String,
            val isDisabled: Boolean,
            val position: Int
        )

        data class SettingsAndStatus(
            val status: Status,
            val settings: Settings
        )

        data class ProgressAndStatus(
            val status: Status,
            val progress: Progress
        )

        data class ResultAndStatus(
            val status: Status,
            val result: Result
        )

        // -------------------------------------

        data class Settings(
            val constraints: Constraints,
            val source: Target,
            val destination: Target,
            val theme: Theme
        )

        data class Constraints(
            val protectEdition: Boolean,
            val schedule: Boolean,
            val wifi: Boolean,
            val charging: Boolean,
            val idle: Boolean
        )

        data class Target(
            val protocol: String,
            val ssl: Boolean?,
            val share: String?,
            val path: String,
        )

        data class Theme(
            val background: Int,
            val borders: Int,
            val progressbar: Int,
            val icons: Int
        )

        data class Progress(
            val confirmed: Int,
            val current: Int,
            val detail_1: String?,
            val detail_2: String?,
            val detail_3: String?
        )

        data class Result(
            val last_result: String,
            val last_date: Long?,
            val last_files_count: Int,
            val last_files_size: Long,
            val last_orphans_count: Int,
            val last_orphans_size: Long
        )
    }
}