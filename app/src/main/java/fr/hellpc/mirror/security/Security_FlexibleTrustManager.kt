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

package fr.hellpc.mirror.security

import android.annotation.SuppressLint
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

@SuppressLint("CustomX509TrustManager")
class Security_FlexibleTrustManager(private val expectedHostKey: String? = null) : X509ExtendedTrustManager() {

    private val defaultTrustManager: X509ExtendedTrustManager by lazy {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.first { it is X509TrustManager } as X509ExtendedTrustManager
    }

    private fun verifyChain(chain: Array<out X509Certificate>?, delegateBlock: () -> Unit) {
        // Try default validation
        try { delegateBlock() }
        catch (exp: CertificateException) {
            // No certificate provided
            if (chain.isNullOrEmpty())
                throw exp

            val serverCert = chain[0]
            val currentHostKey = calculateSha256(serverCert)

            // Try host key validation
            if (!expectedHostKey.isNullOrBlank()) {
                if (currentHostKey.equals(expectedHostKey, ignoreCase = true))
                    return
                else
                    throw CertificateException(exp.message, exp)
            }

            // Custom exception with current host key
            throw UntrustedCertificateException(currentHostKey, exp)
        }
    }


    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        verifyChain(chain) { defaultTrustManager.checkServerTrusted(chain, authType) }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        verifyChain(chain) { defaultTrustManager.checkServerTrusted(chain, authType, socket) }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        verifyChain(chain) { defaultTrustManager.checkServerTrusted(chain, authType, engine) }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        defaultTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        defaultTrustManager.checkClientTrusted(chain, authType, socket)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        defaultTrustManager.checkClientTrusted(chain, authType, engine)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = defaultTrustManager.acceptedIssuers

    private fun calculateSha256(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.encoded)
        return hash.joinToString(":") { "%02X".format(it) }
    }
}

class UntrustedCertificateException(
    val capturedHostKey: String,
    cause: Throwable
) : CertificateException(cause.message, cause)