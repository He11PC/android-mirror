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

import fr.hellpc.mirror.R
import java.util.Locale

class Utility_BackupTarget {

    private val charsBengali by lazy { "[\\u0980-\\u09FF]" }
    private val charsServer by lazy { "\\p{L}\\p{N}-.'" }
    private val charsDirectory by lazy { "\\p{Sc}\\p{Z}\\p{Pc}\\p{Ps}\\p{Pe}=#&" }
    private val charsPath by lazy { "/" }
    private val charsGlob by lazy { "*?!:\\n\\r" }
    private val forbiddenDirectoryNames by lazy { listOf("aux", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "con", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9", "nul", "prn", ".DAV", "._DAV") }


    // --------
    // Protocol
    // --------

    /** Get a readable protocol type **/
    fun getReadableProtocol (protocol: String, ssl: Boolean?, path: String): String {
        return if(protocol == "LOCAL" && !path.contains("/storage/emulated/0/"))
            "MicroSD"
        else if(protocol == "FTP" && ssl == true)
            protocol+"S"
        else
            protocol
    }

    /** Get the protocol icon **/
    fun getProtocolIcon(protocol: String, path: String): Int {
        return when(protocol) {
            "LOCAL" -> {
                if (path.contains("/storage/emulated/0/"))
                    R.drawable.ic_phone
                else
                    R.drawable.ic_sd_card
            }
            "NFS", "SMB" -> R.drawable.ic_lan
            else -> R.drawable.ic_cloud
        }
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
            "LOCAL" -> path.replace("""(/storage/emulated/0/)|(/storage/[A-Z-\d]*/)""".toRegex(), "")
            "SMB" -> if(!share.isNullOrBlank()) "$share/$path" else path
            "NFS" -> if(!share.isNullOrBlank()) share.substringAfterLast('/') + "/$path" else path
            else -> path
        }
    }

    /** Converts wired android "path" to real useful one (thanks Google -_-') **/
    fun findFullPath(path: String): String {
        var cleanedPath = path
        val actualResult: String
        cleanedPath = cleanedPath.substring(5)
        var index = 0
        val result = StringBuilder("/storage")
        run {
            var i = 0
            while (i < cleanedPath.length) {
                if (cleanedPath[i] != ':') {
                    result.append(cleanedPath[i])
                } else {
                    index = ++i
                    result.append('/')
                    break
                }
                i++
            }
        }
        for (i in index until cleanedPath.length) {
            result.append(cleanedPath[i])
        }
        actualResult = if (result.substring(9, 16).equals("primary", ignoreCase = true)) {
            result.substring(0, 8) + "/emulated/0/" + result.substring(17)
        } else {
            result.toString()
        }
        return actualResult
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