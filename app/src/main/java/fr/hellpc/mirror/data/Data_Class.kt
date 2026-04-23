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

package fr.hellpc.mirror.data

import java.io.Serializable
import java.time.Instant


// --------------------
// Backups RecyclerView
// --------------------

data class BackupBtn_Action(
    val action: String,
    val info: BackupBtn_Infos
)

data class BackupBtn_Infos(
    val id_backup: Int,
    val last_date: Long?,
    val isProtected: Boolean,
    val wifi_only: Boolean,
    val id_worker: String?,
    val id_scheduler: String?,
    val position: Int
)


// ----------------
// Scheduled backup
// ----------------

data class BackupScheduled_Options (
    val schedule: Boolean,
    val interval: Int,
    val interval_unit: Int,
    val hour: Int?,
    val minute: Int?,
    val const_wifi: Boolean,
    val const_charging: Boolean,
    val const_idle: Boolean
)


// ------------
// Backup infos
// ------------

data class BackupInfos_Idle (
    val id_backup: Int,
    val const_wifi: Boolean
)

data class BackupInfos_Running (
    val id_backup: Int,
    val id_worker: String?
)

data class BackupInfos_Failed (
    val id_backup: Int,
    val id_worker: String?,
    val status: String,
    val schedule: Boolean,
    val id_scheduler: String?,
    val last_date: Long?
)

data class BackupInfos_Status (
    val isDisabled: Boolean,
    val status: String,
    val id_worker: String?,
    val id_scheduler: String?,
    val last_date: Long?
)

data class BackupsInfos_StatusCount (
    val idle: Int,
    val running: Int,
    val disabled: Int
)


// -------------
// Worker backup
// -------------

data class WorkerBackup_File (
    val path: String,
    val name: String,
    val last_modified: Instant,
    val size: Long,
    val isDirectory: Boolean
)

data class WorkerBackup_TransferResult (
    val dateIsOK: Boolean,
    val sizeIsOK: Boolean
)

data class WorkerBackup_Progress(
    val sizeCurrent: Long,
    val sizeTotal: Long,
    val timeLeft: String,
    val percentConfirmed: Int,
    val percentCurrent: Int
)


// -------------
// Notifications
// -------------

data class NotificationGlobal_Stats(
    val color: Int,
    val icon: Int,
    val scheduled: String,
    val done: String,
    val files: String
)


// ---------------
// Custom spinners
// ---------------

data class Spinner_IconAndText(
    val iconID: Int,
    val iconTint: Int?,
    val text: String
)

data class Spinner_ColorAndText(
    val text: String,
    val colorBackground: Int,
    val colorBorders: Int,
    val colorIcons: Int?,
    val colorProgressbar: Int?
)


// --------
// Autofill
// --------

data class Target_Credentials (
    val protocol: String,
    val server: String?,
    val domain: String?,
    val port: Int?,
    val ssl: Boolean?,
    val hostKey: String?,
    val login: String?,
    val password: String?,
    val uid: String?,
    val gid: String?,
    val share: String?
): Serializable


// ---------------
// Folder explorer
// ---------------

data class FolderExplorer_File (
    val name: String,
    val isDirectory: Boolean
)


// ----
// Logs
// ----

data class Logs_NavigationPosition(
    val position: Int,
    val logCount: Int
)