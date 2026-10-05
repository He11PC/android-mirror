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

import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.getSystemService
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.LocalStorageType
import java.util.Locale

class Utility_BackupTarget {

    private val charsBengali = "[\\u0980-\\u09FF]"
    private val charsServer = "\\p{L}\\p{N}-.'"
    private val charsDirectory = "\\p{Sc}\\p{Z}\\p{Pc}\\p{Ps}\\p{Pe}=#&"
    private val charsPath = "/"
    private val charsGlob = "*?!:\\n\\r"
    private val forbiddenDirectoryNames = listOf("aux", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "con", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9", "nul", "prn", ".DAV", "._DAV")
    private val mountPointRegex = """^(?i)/(?:storage/(?:emulated/\d+|[a-f0-9-]+|usb[\w-]*)|sdcard|data/media/\d+|mnt/media_rw/[a-f0-9-]+)/""".toRegex()
    private val mountPointParentRegex = """^(?i)/(?:storage(?:/emulated)?|mnt(?:/media_rw)?|data(?:/media)?)$""".toRegex()

    private val getPathMethod by lazy {
        try { StorageVolume::class.java.getMethod("getPath") }
        catch(_: Exception) { null }
    }

    private val getIdMethod by lazy {
        try { StorageVolume::class.java.getMethod("getId") }
        catch (_: Exception) { null }
    }


    // --------
    // Protocol
    // --------

    /** Get a readable protocol type **/
    fun getReadableProtocol(protocol: String, ssl: Boolean?, path: String): String {
        return when(protocol) {
            "LOCAL" -> {
                when(getLocalStorageType(path)) {
                    LocalStorageType.INTERNAL -> "Internal"
                    LocalStorageType.SD_CARD -> "MicroSD"
                    LocalStorageType.USB_DRIVE -> "USB"
                    else -> "Unknown"
                }
            }
            "FTP" if ssl == true -> protocol + "S"
            else -> protocol
        }
    }

    /** Get the protocol icon **/
    fun getProtocolIcon(protocol: String, path: String): Int {
        return when(protocol) {
            "LOCAL" -> {
                when(getLocalStorageType(path)) {
                    LocalStorageType.INTERNAL -> R.drawable.ic_phone
                    LocalStorageType.SD_CARD -> R.drawable.ic_sd_card
                    LocalStorageType.USB_DRIVE -> R.drawable.ic_usb
                    else -> R.drawable.ic_question
                }
            }
            "NFS", "SMB" -> R.drawable.ic_lan
            else -> R.drawable.ic_cloud
        }
    }


    // -----------
    // Mount point
    // -----------

    /** Find the storage type (internal, sd, usb) of the mount point **/
    fun getLocalStorageType(path: String): LocalStorageType {
        if(path.isBlank())
            return LocalStorageType.UNKNOWN

        val storageManager = App.instance.getSystemService<StorageManager>()
            ?: return LocalStorageType.UNKNOWN

        val volume = storageManager.storageVolumes.firstOrNull {
            val mountPoint = getVolumeMountPoint(it) ?: return@firstOrNull false
            path.startsWith(mountPoint, ignoreCase = true)
        } ?: return LocalStorageType.UNKNOWN

        return getLocalStorageType(volume)
    }

    /** Find the storage type (internal, sd, usb) of the mount point from a volume **/
    fun getLocalStorageType(volume: StorageVolume): LocalStorageType {
        if(!volume.isRemovable)
            return LocalStorageType.INTERNAL

        val volumeId = try { (getIdMethod?.invoke(volume) as? String)?.lowercase() ?: "" }
        catch(_: Exception) { "" }

        if(volumeId.isNotEmpty())
            if(volumeId.contains("public:8,") || volumeId.contains("disk:8,") || volumeId.contains("usb"))
                return LocalStorageType.USB_DRIVE

        val description = volume.getDescription(App.instance).lowercase()
        if(description.contains("usb") || description.contains("otg"))
            return LocalStorageType.USB_DRIVE

        return LocalStorageType.SD_CARD
    }

    // -------------------------------------

    /** Check if a path is the parent directory of a mount point root **/
    fun pathIsMountPointParent(path: String): Boolean {
        val cleanPath = path.trim().removeSuffix("/")
        return mountPointParentRegex.matches(cleanPath)
    }

    /** Get the mount point path of a volume **/
    fun getVolumeMountPoint(volume: StorageVolume): String? {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            volume.directory?.path
        else
            try { getPathMethod?.invoke(volume) as? String }
            catch(_: Exception) { null }
    }

    /** Remove mount point from path **/
    private fun String.removeMountPoint(): String {
         if(this.isBlank())
             return ""

        val normalizedPath = if(this.endsWith("/")) this else "$this/"
        return normalizedPath.replaceFirst(mountPointRegex, "")
    }


    // ------
    // Server
    // ------

    /** Allowed characters for server name/IP **/
    fun getAllowedCharactersServer() = "[$charsServer$charsBengali]".toRegex()

    /** Remove forbidden characters from server name/IP **/
    fun trimServer(server: String) = server.trim('.', '-', ' ')


    // ---------
    // Directory
    // ---------

    /** Characters restriction for directory **/
    fun getAllowedCharactersDirectory() = "[$charsServer$charsDirectory$charsBengali]".toRegex()

    /** Trim a directory name **/
    fun trimDirectory(directory: String) = directory.trim().trimEnd('.')

    /** Check if a folder is valid **/
    fun directoryIsValid(directory: String, allowEmpty: Boolean): Boolean {
        if(directory.isBlank())
            return allowEmpty

        val firstChar = directory[0]
        val lastChar = directory[directory.length-1]
        if (directory == "." || directory == ".." || lastChar == '.' || firstChar.isWhitespace() || lastChar.isWhitespace())
            return false

        return directory.lowercase(Locale.getDefault()) !in forbiddenDirectoryNames
    }


    // ----
    // Path
    // ----

    /** Allowed characters for a path **/
    fun getAllowedCharactersPath() = "[$charsServer$charsDirectory$charsPath$charsBengali]".toRegex()

    /** Trim single line path **/
    fun trimPath(path: String, keepLeadingSlash: Boolean): String {
        val directoryList = path
            .replace('\\', '/')
            .trim()
            .split('/')
            .filter { it.isNotEmpty() }
            .toMutableList()

        for(fi in directoryList.indices) {
            directoryList[fi] = trimDirectory(directoryList[fi])
        }

        return if(path.startsWith('/') && keepLeadingSlash)
            '/' + directoryList.joinToString("/")
        else
            directoryList.joinToString("/")
    }

    /** Check if a path is valid **/
    fun pathIsValid(path: String, allowEmpty: Boolean): Boolean {
        if(path.isBlank())
            return allowEmpty

        path.split('/').filter { it.isNotEmpty() }.forEach {
            if(!directoryIsValid(it, false))
                return false
        }
        return true
    }

    /** Format a path (add/remove information) to be readable by users **/
    fun getReadablePath(protocol: String, share: String?, path: String): String {
        return when(protocol) {
            "LOCAL" -> path.removeMountPoint().trimEnd('/')
            "SMB" -> if(!share.isNullOrBlank()) "$share/$path" else path
            "NFS", "WDAV" -> if(!share.isNullOrBlank()) share.substringAfterLast('/') + "/$path" else path
            else -> path
        }
    }

    /** Move external storage path to Mirror folder **/
    fun movePathToProprietaryFolder(path: String): String {
        val simplePath = path.replace("/storage/", "")
        val externalName = simplePath.substringBefore('/')
        return "/storage/$externalName/Android/data/fr.hellpc.mirror/files/"+simplePath.substringAfter('/')
    }


    // ------------
    // Glob pattern
    // ------------

    /** Allowed characters for a path with glob pattern **/
    fun getAllowedCharactersGlob() = "[$charsServer$charsDirectory$charsPath$charsGlob$charsBengali]".toRegex()

    /** Trim multiline path with glob pattern **/
    fun trimPathsGlob(path: String): String {
        val pathList = path
            .trim()
            .split(System.lineSeparator())
            .filter { it.isNotEmpty() }
            .toMutableList()

        for(pi in pathList.indices) {
            pathList[pi] = trimPath(pathList[pi], true)
        }

        return pathList.joinToString(System.lineSeparator())
    }
}