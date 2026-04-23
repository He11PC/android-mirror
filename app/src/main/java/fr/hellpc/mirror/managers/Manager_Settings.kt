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

package fr.hellpc.mirror.managers

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.os.LocaleListCompat
import androidx.core.os.LocaleListCompat.getEmptyLocaleList
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.Spinner_IconAndText

class Manager_Settings {

    private val prefSettings by lazy { App.instance.getSharedPreferences("SETTINGS", Context.MODE_PRIVATE) }
    private val permissions by lazy { Manager_Permissions() }
    private val localeContext = App.getLocaleContext()

    private val locales by lazy { localeContext.resources.getStringArray(R.array.locales) }
    private val localesID by lazy { App.instance.resources.getStringArray(R.array.locales_id) }
    private val themes by lazy { localeContext.resources.getStringArray(R.array.theme) }

    private val colorGreen = "#A4C639".toColorInt()
    private val colorYellow = "#FCDC5C".toColorInt()
    private val colorSilver = "#A8A9AE".toColorInt()


    // --------
    // Language
    // --------

    /** Generate a list of locales with flag **/
    fun getLocalesWithFlags(): List<Spinner_IconAndText> {
        val localesWithImages = mutableListOf<Spinner_IconAndText>()
        val flags = App.resources.obtainTypedArray(R.array.country_flags)
        locales.forEachIndexed { index, locale ->
            if(index == 0)
                localesWithImages.add(Spinner_IconAndText(R.drawable.ic_android, colorGreen, locale))
            else
                localesWithImages.add(Spinner_IconAndText(flags.getResourceId(index,0), null, locale))
        }
        flags.recycle()
        return localesWithImages.toList()
    }

    /** Return the index of the language currently in use **/
    fun getLocaleID(): Int {
        val code = AppCompatDelegate.getApplicationLocales()
        return if(code.isEmpty) 0
        else localesID.indexOfFirst { it == code[0].toString().take(2) } + 1
    }

    /** Apply selected language to the app **/
    fun saveLocale(id: Int) {
        if(id == 0)
            AppCompatDelegate.setApplicationLocales(getEmptyLocaleList())
        else
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localesID[id-1]))
        // Update permanent notification
        Manager_Notifications().managePermanentNotificationService()
    }


    // ---------------
    // Interface theme
    // ---------------

    /** Generate a list of themes with icon **/
    fun getThemesWithIcon(): List<Spinner_IconAndText> {
        val themesWithIcon = mutableListOf<Spinner_IconAndText>()
        themes.forEachIndexed { index, theme ->
            val icon: Int
            val tint: Int
            when(index) {
                0 -> {
                    icon = R.drawable.ic_android
                    tint = colorGreen
                }
                1 -> {
                    icon = R.drawable.ic_sun
                    tint = colorYellow
                }
                else -> {
                    icon = R.drawable.ic_moon
                    tint = colorSilver
                }
            }
            themesWithIcon.add(Spinner_IconAndText(icon, tint, theme))
        }
        return themesWithIcon
    }

    /** Return the current theme ID
     * - 0 = system default
     * - 1 = light
     * - 2 = dark **/
    fun getTheme(): Int = prefSettings.getInt("THEME", 0)

    /** Save and apply selected theme
     * - 0 = system default
     * - 1 = light
     * - 2 = dark **/
    fun saveTheme(id: Int) {
        prefSettings.edit { putInt("THEME", id) }
        applyTheme(id)
    }

    /** Get Theme ID and apply it
     * - 0 = system default
     * - 1 = light
     * - 2 = dark **/
    fun applyTheme() {
        applyTheme(getTheme())
    }

    /** Apply theme corresponding to ID
     * - 0 = system default
     * - 1 = light
     * - 2 = dark **/
    private fun applyTheme(id: Int) {
        when (id) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }


    // ----
    // Logs
    // ----

    /** Return the number of logs to keep **/
    fun getLogsRetention(): Int {
        return prefSettings.getInt("LOGS_RETENTION", 15)
    }

    /** Save number of logs to keep **/
    fun saveLogsRetention(num: Int) = prefSettings.edit { putInt("LOGS_RETENTION", num) }

    /** Return type of errors to log (native vs simplified) **/
    fun getLogsNativeErrors() = prefSettings.getBoolean("LOGS_NATIVE_ERRORS", false)

    /** Save type of errors to log (native vs simplified) **/
    fun saveLogsNativeErrors(chk: Boolean) = prefSettings.edit { putBoolean("LOGS_NATIVE_ERRORS", chk) }

    /** Return setforeground warning preference **/
    fun getLogsWarningSetForeground() = prefSettings.getBoolean("LOGS_WARNING_SETFOREGROUND", true)

    /** Save setforeground warning preference **/
    fun saveLogsWarningSetForeground(chk: Boolean) = prefSettings.edit { putBoolean("LOGS_WARNING_SETFOREGROUND", chk) }

    /** Return preserve scroll position preference **/
    fun getLogsPreserveScrollPosition() = prefSettings.getBoolean("LOGS_PRESERVE_SCROLL_POSITION", false)

    /** Save setforeground warning preference **/
    fun saveLogsPreserveScrollPosition(chk: Boolean) = prefSettings.edit { putBoolean("LOGS_PRESERVE_SCROLL_POSITION", chk) }


    // -------------
    // Notifications
    // -------------

    /** Return if the backup colors theme should be applied to its notifications **/
    fun getNotificationApplyTheme() = prefSettings.getBoolean("NOTIFICATIONS_APPLY_THEME", true)

    /** Save if the backup colors theme should be applied to its notifications **/
    fun saveNotificationApplyTheme(chk: Boolean) = prefSettings.edit { putBoolean("NOTIFICATIONS_APPLY_THEME", chk) }

    /** Return what type of notification user selected for scheduled backup result
     * - 0 = none
     * - 1 = individual - discardable
     * - 2 = global - discardable
     * - 3 = global - permanent **/
    fun getNotificationType(): Int {
        return if(!permissions.permissionNotificationsIsGranted())
            0
        else if(permissions.batteryOptimizationsAreDisabled())
            prefSettings.getInt("NOTIFICATIONS_TYPE", 2)
        else
            // Worker setForeground can be refused when the application is in background and battery optimizations are enabled
            prefSettings.getInt("NOTIFICATIONS_TYPE", 3)
    }

    /** Save the type of notification user selected for scheduled backup result
     * - 0 = none
     * - 1 = individual - discardable
     * - 2 = global - discardable
     * - 3 = global - permanent **/
    fun saveNotificationType(id: Int) {
        prefSettings.edit { putInt("NOTIFICATIONS_TYPE", id) }
        // Start/stop permanent notification service
        Manager_Notifications().managePermanentNotificationService()
    }
}