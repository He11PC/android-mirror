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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.hellpc.mirror.data.dao.*


@Database(entities = [Backup_Data::class, Backup_Status::class, Backup_Result::class, BackupScheduled_Stats::class], views = [Backup_Infos::class, AllBackupsScheduled_Stats::class, BackupTarget_Credentials::class], version = 1, exportSchema = false)
abstract class Room_Database : RoomDatabase() {

    abstract fun Dao_TBackupData(): Dao_TBackupData
    abstract fun Dao_TBackupScheduledStats(): Dao_TBackupScheduledStats
    abstract fun Dao_TBackupResult(): Dao_TBackupResult
    abstract fun Dao_TBackupStatus(): Dao_TBackupStatus
    abstract fun Dao_VBackupInfos(): Dao_VBackupInfos
    abstract fun Dao_VAllBackupsScheduledStats(): Dao_VAllBackupsScheduledStats
    abstract fun Dao_VBackupTargetCredentials(): Dao_VBackupTargetCredentials

    companion object {
        // Singleton prevents multiple instances of database opening at the same time.
        @Volatile
        private var INSTANCE: Room_Database? = null
        private val CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL("CREATE TRIGGER create_backup_status AFTER INSERT ON t_backup_data BEGIN INSERT INTO t_backup_status VALUES(null, new.id, null, null, null, null, null, null, null, null); END;")
                db.execSQL("CREATE TRIGGER create_backup_result AFTER INSERT ON t_backup_data BEGIN INSERT INTO t_backup_result VALUES(null, new.id, null, null, null, null, null, null); END;")
                db.execSQL("CREATE TRIGGER create_backup_scheduled_stats AFTER INSERT ON t_backup_data BEGIN INSERT INTO t_backup_scheduled_stats VALUES(null, new.id, null, null, null, null, null, null, null, null, null); END;")
            }
        }

        fun getDatabase(context: Context): Room_Database {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    Room_Database::class.java,
                    "FoldersBackup_Database"
                ).addCallback(CALLBACK).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }

        //fun destroyDataBase() { INSTANCE = null }
    }
}