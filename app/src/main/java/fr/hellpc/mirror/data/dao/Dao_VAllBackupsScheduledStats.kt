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

package fr.hellpc.mirror.data.dao

import androidx.room.Dao
import androidx.room.Query
import fr.hellpc.mirror.data.room.AllBackupsScheduled_Stats
import kotlinx.coroutines.flow.Flow

@Dao
interface Dao_VAllBackupsScheduledStats {

    @Query("SELECT scheduled AS backups_scheduled FROM v_all_backups_scheduled_stats")
    fun getBackupsScheduledFlow(): Flow<Int>

    @Query("SELECT scheduled AS backups_scheduled FROM v_all_backups_scheduled_stats")
    fun getBackupsScheduledCount(): Int

    @Query("SELECT * FROM v_all_backups_scheduled_stats")
    fun getDailyStatsFlow(): Flow<AllBackupsScheduled_Stats>

    @Query("SELECT * FROM v_all_backups_scheduled_stats")
    fun getDailyStats(): AllBackupsScheduled_Stats
}