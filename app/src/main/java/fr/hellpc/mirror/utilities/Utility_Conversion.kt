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

package fr.hellpc.mirror.utilities

import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.util.Locale
import kotlin.math.abs

object Utility_Conversion {

    /** Convert milliseconds to readable time **/
    fun Long.millisToReadable(verbose: Boolean, accurate: Boolean): String {
        val localeContext = App.getLocaleContext()

        val minutes = (this/60000).toInt()
        val seconds = if(accurate)
            (this%60000).toFloat()/1000
        else
            ((this/1000)%60).toInt()

        return if(verbose) {
            if(minutes > 0)
                "$minutes "+ localeContext.getString(R.string.log_minutes)+" $seconds "+ localeContext.getString(R.string.log_seconds)
            else
                "$seconds "+ localeContext.getString(R.string.log_seconds)
        }
        else
            "$minutes:" + seconds.toString().padStart(2, '0')
    }

    /** Convert file size Long in KiB, MiB, GiB, ... **/
    fun Long.sizeToReadable(): String {
        val absB = if (this == Long.MIN_VALUE) Long.MAX_VALUE else abs(this)
        if (absB < 1024) {
            return "$this B"
        }
        var value = absB
        val ci: CharacterIterator = StringCharacterIterator("KMGTPE")
        var i = 40
        while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
            value = value shr 10
            ci.next()
            i -= 10
        }
        value *= java.lang.Long.signum(this).toLong()
        return String.format(Locale.getDefault(), "%.1f %ciB", value / 1024.0, ci.current())
    }

    /** Convert file size String to Long **/
    fun String.sizeToLong(): Long {
        val size = this.split(" ")
        val multi = when (size[1]) {
            "KiB" -> 1024
            "MiB" -> 1024*1024
            "GiB" -> 1024*1024*1024
            else -> 1
        }
        return (size[0].replace(',', '.').toFloat()*multi).toLong()
    }
}