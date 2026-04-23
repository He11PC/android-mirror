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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


internal object Utility_Encryption {

    /** Recover crypto key from Android keystore **/
    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (keyStore.getKey("mirror_cryptoKey", null) ?: generateKey()) as SecretKey
    }

    /** Generate a new crypto key and saves it in Android keystore **/
    private fun generateKey(): SecretKey {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder("mirror_cryptoKey", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            return generateKey()
        }
    }

    // -------------------------------------

    /** Encrypt String **/
    internal fun String.cipherEncrypt(): String? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getKey())

            val iv = cipher.iv
            val encryptedData = cipher.doFinal(this.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + encryptedData.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        }
        catch(exp: Exception) {
            Log.e("Encrypt", exp.message.toString())
            null
        }
    }

    /** Decrypt String **/
    internal fun String.cipherDecrypt(): String? {
        return try {
            val combined = Base64.decode(this, Base64.NO_WRAP)

            val iv = combined.copyOfRange(0, 12)
            val encryptedData = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getKey(), spec)

            String(cipher.doFinal(encryptedData), Charsets.UTF_8)
        }
        catch(exp: Exception) {
            Log.e("Decrypt", exp.message.toString())
            null
        }
    }
}