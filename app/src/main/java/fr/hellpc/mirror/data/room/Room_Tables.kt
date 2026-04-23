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

package fr.hellpc.mirror.data.room

import androidx.room.*


// ------
// Tables
// ------

@Entity(tableName = "t_backup_data")
data class Backup_Data (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val isDisabled: Boolean,
    @ColumnInfo(defaultValue = "0") val position: Int,
    @Embedded(prefix = "src_") val source: Backup_Target,
    @Embedded(prefix = "dest_") val destination: Backup_Target,
    @Embedded(prefix = "opt_") val options: Backup_Options,
    @Embedded(prefix = "color_") val colors: Backup_Colors
)

// ---

@Entity (tableName = "t_backup_status",
    foreignKeys = [ForeignKey(
        entity = Backup_Data::class,
        parentColumns = ["id"],
        childColumns = ["id_backup"],
        onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["id_backup"])]
)
data class Backup_Status (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val id_backup: Int,
    val id_worker: String?,
    val id_scheduler: String?,
    @ColumnInfo(defaultValue = "idle") val status: String,
    @ColumnInfo(defaultValue = "-1") val progress_confirmed: Int,    // Files 100% transferred and validated as non corrupted
    @ColumnInfo(defaultValue = "-1") val progress_current: Int,      // Files ongoing + files confirmed
    val progress_detail_1: String?,
    val progress_detail_2: String?,
    val progress_detail_3: String?
)

// ---

@Entity (tableName = "t_backup_result",
    foreignKeys = [ForeignKey(
        entity = Backup_Data::class,
        parentColumns = ["id"],
        childColumns = ["id_backup"],
        onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["id_backup"])]
)
data class Backup_Result (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val id_backup: Int,
    @ColumnInfo(defaultValue = "nc") val result: String,
    val date: Long?,
    @ColumnInfo(defaultValue = "0") val files_count: Int,
    @ColumnInfo(defaultValue = "0") val files_size: Long,
    @ColumnInfo(defaultValue = "0") val orphans_count: Int,
    @ColumnInfo(defaultValue = "0") val orphans_size: Long
)

// ---

@Entity (tableName = "t_backup_scheduled_stats",
    foreignKeys = [ForeignKey(
        entity = Backup_Data::class,
        parentColumns = ["id"],
        childColumns = ["id_backup"],
        onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["id_backup"])]
)
data class BackupScheduled_Stats (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val id_backup: Int,
    @ColumnInfo(defaultValue = "0") val active: Boolean,
    @ColumnInfo(defaultValue = "0") val files_count: Int,
    @ColumnInfo(defaultValue = "0") val files_size: Long,
    @ColumnInfo(defaultValue = "0") val orphans_count: Int,
    @ColumnInfo(defaultValue = "0") val orphans_size: Long,
    @ColumnInfo(defaultValue = "0") val success: Int,
    @ColumnInfo(defaultValue = "0") val warning: Int,
    @ColumnInfo(defaultValue = "0") val failed: Int,
    @ColumnInfo(defaultValue = "0") val canceled: Int
)


// -----
// Views
// -----

@DatabaseView(
    "SELECT bkp.id AS id_backup, " +
            "bkp.position," +
            "bkp.isDisabled, " +
            "bkp.color_background AS color_background, "+
            "bkp.color_borders AS color_borders, "+
            "bkp.color_icons AS color_icons, "+
            "bkp.color_progressbar AS color_progressbar, "+
            "bkp.src_protocol, " +
            "bkp.src_ssl, " +
            "bkp.src_server, " +
            "bkp.src_share, "+
            "bkp.src_path, " +
            "bkp.dest_protocol, " +
            "bkp.dest_ssl, " +
            "bkp.dest_server, " +
            "bkp.dest_share, "+
            "bkp.dest_path, " +
            "bkp.opt_protectEdition AS protectEdition, " +
            "bkp.opt_schedule AS schedule, " +
            "stu.id_scheduler, " +
            "bkp.opt_const_wifi AS const_wifi, " +
            "bkp.opt_const_charging AS const_charging, " +
            "bkp.opt_const_idle AS const_idle, " +
            "stu.id_worker, " +
            "stu.status, " +
            "stu.progress_confirmed, " +
            "stu.progress_current, " +
            "stu.progress_detail_1, " +
            "stu.progress_detail_2, " +
            "stu.progress_detail_3, " +
            "lst.result AS last_result, " +
            "lst.date AS last_date, " +
            "lst.files_count AS last_files_count, " +
            "lst.files_size AS last_files_size, " +
            "lst.orphans_count AS last_orphans_count, " +
            "lst.orphans_size AS last_orphans_size " +
        "FROM t_backup_data bkp " +
        "LEFT JOIN t_backup_status stu ON bkp.id = stu.id_backup "+
        "LEFT JOIN t_backup_result lst ON bkp.id = lst.id_backup",
    viewName = "v_backup_infos")
data class Backup_Infos (
    val id_backup: Int,
    val position: Int,
    val isDisabled: Boolean,
    val color_background: Int,
    val color_borders: Int,
    val color_icons: Int,
    val color_progressbar: Int,
    val src_protocol: String,
    val src_ssl: Boolean?,
    val src_server: String?,
    val src_share: String?,
    val src_path: String,
    val dest_protocol: String,
    val dest_ssl: Boolean?,
    val dest_server: String?,
    val dest_share: String?,
    val dest_path: String,
    val protectEdition: Boolean,
    val schedule: Boolean,
    val id_scheduler: String?,
    val const_wifi: Boolean,
    val const_charging: Boolean,
    val const_idle: Boolean,
    val id_worker: String?,
    val status: String,
    val progress_confirmed: Int,
    val progress_current: Int,
    val progress_detail_1: String?,
    val progress_detail_2: String?,
    val progress_detail_3: String?,
    val last_result: String,
    val last_date: Long?,
    val last_files_count: Int,
    val last_files_size: Long,
    val last_orphans_count: Int,
    val last_orphans_size: Long
)

// ---

@DatabaseView(
    "SELECT (SELECT COUNT(id) FROM t_backup_data WHERE opt_schedule = '1' AND isDisabled = '0') AS scheduled, " +
            "(SELECT COUNT(id) FROM t_backup_scheduled_stats WHERE active = '1') AS actives, " +
            "SUM(files_count) AS files_count, " +
            "SUM(files_size) AS files_size, " +
            "SUM(orphans_count) AS orphans_count, " +
            "SUM(orphans_size) AS orphans_size, " +
            "SUM(success) AS success, " +
            "SUM(warning) AS warning, " +
            "SUM(failed) AS failed, " +
            "SUM(canceled) AS canceled " +
        "FROM t_backup_scheduled_stats",
    viewName = "v_all_backups_scheduled_stats"
)
data class AllBackupsScheduled_Stats (
    val scheduled: Int,
    val actives: Int,
    val files_count: Int,
    val files_size: Long,
    val orphans_count: Int,
    val orphans_size: Long,
    val success: Int,
    val warning: Int,
    val failed: Int,
    val canceled: Int
)

// ---

@DatabaseView(
    "SELECT DISTINCT " +
            "id AS id_backup, " +
            "src_protocol AS protocol, " +
            "src_server AS server, " +
            "src_domain AS domain, " +
            "src_port AS port, " +
            "src_ssl AS ssl, " +
            "src_hostKey AS hostKey, "+
            "src_login AS login, " +
            "src_password AS password, " +
            "src_uid AS uid, " +
            "src_gid AS gid, " +
            "src_share AS share " +
        "FROM t_backup_data WHERE protocol <> 'LOCAL' " +
    "UNION " +
    "SELECT DISTINCT " +
            "id AS id_backup, " +
            "dest_protocol AS protocol, " +
            "dest_server AS server, " +
            "dest_domain AS domain, " +
            "dest_port AS port, " +
            "dest_ssl AS ssl, " +
            "dest_hostKey AS hostKey, " +
            "dest_login AS login, " +
            "dest_password AS password, " +
            "dest_uid AS uid, " +
            "dest_gid AS gid, " +
            "dest_share AS share " +
        "FROM t_backup_data WHERE protocol <> 'LOCAL'",
    viewName = "v_backup_target_credentials"
)
data class BackupTarget_Credentials (
    val id_backup: Int,
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
)


// --------------
// Embedded class
// --------------

data class Backup_Target (
    val protocol: String,
    val server: String?,
    val domain: String?,
    val share: String?,
    val path: String,
    val port: Int?,
    val ssl: Boolean?,
    val hostKey: String ?,
    val login: String?,
    val password: String?,
    val uid: String?,
    val gid: String?
)

data class Backup_Options (
    val protectEdition: Boolean,
    val recursive: Boolean,
    val retryCorrupted: Boolean,
    val dateComparison_Auto: Boolean,
    val dateComparison_Strict: Boolean,    // Used for files date comparison, false if lastModifiedDate can't be updated
    val nomedia: Boolean,
    val orph_action: Int,   // string-array: orphan_options
    val orph_folder: String?,
    val flt_images: Boolean,
    val flt_audio: Boolean,
    val flt_videos: Boolean,
    val flt_documents: Boolean,
    val flt_others: Boolean,
    val flt_minSize: Long?,
    val flt_maxSize: Long?,
    val flt_blackList: String?,
    val schedule: Boolean,
    val interval: Int,
    val interval_unit: Int, // string-array: interval_unit
    val hour: Int?,
    val minute: Int?,
    val const_wifi: Boolean,
    val const_charging: Boolean,
    val const_idle: Boolean
)

data class Backup_Colors(
    val background: Int,
    val borders: Int,
    val icons: Int,
    val progressbar: Int
)