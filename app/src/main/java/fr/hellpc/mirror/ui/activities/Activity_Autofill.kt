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

package fr.hellpc.mirror.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.Target_Credentials
import fr.hellpc.mirror.databinding.ActivityAutofillBinding
import fr.hellpc.mirror.ui.adapters.Adapter_Recycler_Autofill
import fr.hellpc.mirror.ui.viewmodels.ViewModel_Autofill
import fr.hellpc.mirror.utilities.Utility_Encryption.cipherDecrypt
import fr.hellpc.mirror.utilities.Utility_Encryption.cipherEncrypt
import kotlinx.coroutines.launch

class Activity_Autofill: AppCompatActivity() {

    private lateinit var binding: ActivityAutofillBinding
    private val viewModel: ViewModel_Autofill by viewModels { ViewModel_Autofill.Factory }

    private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { exitOnBackPressed(true) }
    }

    companion object {
        const val PARAM_PROTOCOL = "PARAM_PROTOCOL"
    }

    private var protocol: String? = null

    // -------------------------------------

    private val executor by lazy { ContextCompat.getMainExecutor(this) }

    private val biometricPrompt by lazy {
        BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                viewModel.loadCredentials(protocol)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                exitOnBackPressed(true)
            }

        })
    }

    private val promptInfo by lazy {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.autofill_authentication_title))
            .setSubtitle(getString(R.string.autofill_authentication_message))
            .setNegativeButtonText(getString(R.string.global_cancel))
            .build()
    }

    // -------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAutofillBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        initialize(savedInstanceState == null)
    }

    // -------------------------------------

    private fun initialize(loadData: Boolean) {
        protocol = intent.getStringExtra(PARAM_PROTOCOL)
        val canUseBiometric = BiometricManager.from(App.instance).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

        if(protocol.isNullOrEmpty() || !canUseBiometric)
            exitOnBackPressed(true)

        binding.autofillTxtTitle.text = protocol

        setupListeners()
        setupRecycler()

        if(loadData)
            biometricPrompt.authenticate(promptInfo)
    }

    private fun exitOnBackPressed(isCancellation: Boolean) {
        if(isCancellation)
            setResult(RESULT_CANCELED)
        finish()
    }

    // -------------------------------------

    /** Setup back buttons listener **/
    private fun setupListeners() = lifecycleScope.launch {
        binding.autofillBtnBack.setOnClickListener { exitOnBackPressed(true) }
    }

    /** Setup RecyclerView **/
    private fun setupRecycler() {
        val recyclerFolders = binding.autofillRecycler
        recyclerFolders.setHasFixedSize(true)
        recyclerFolders.setItemViewCacheSize(24)
        recyclerFolders.itemAnimator = null

        val gridSpanCount = resources.getInteger(R.integer.grid_column_count)
        val gridLayoutManager = GridLayoutManager(applicationContext, gridSpanCount)
        recyclerFolders.layoutManager = gridLayoutManager

        val recycleAdapter = Adapter_Recycler_Autofill(credentialsSelected = { returnCredentials(it) })
        recycleAdapter.setHasStableIds(false)
        recycleAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        recyclerFolders.adapter = recycleAdapter

        viewModel.credentials.observe(this) { rawList ->
            val credentialsList = mutableListOf<Target_Credentials>()

            rawList.forEach {
                val credentials = Target_Credentials(
                    it.protocol,
                    it.server?.cipherDecrypt(),
                    it.domain,
                    it.port,
                    it.ssl,
                    it.hostKey?.cipherDecrypt(),
                    it.login?.cipherDecrypt(),
                    it.password?.cipherDecrypt(),
                    it.uid?.cipherDecrypt(),
                    it.gid?.cipherDecrypt(),
                    it.share
                )

                if(credentials !in credentialsList)
                    credentialsList.add(credentials)
            }

            recycleAdapter.submitList(credentialsList)
        }
    }

    /** Return credentials to Edit Activity **/
    private fun returnCredentials(credentials: Target_Credentials) {
        val returnCredentials = Target_Credentials(
            credentials.protocol,
            credentials.server?.cipherEncrypt(),
            credentials.domain,
            credentials.port,
            credentials.ssl,
            credentials.hostKey?.cipherEncrypt(),
            credentials.login?.cipherEncrypt(),
            credentials.password?.cipherEncrypt(),
            credentials.uid?.cipherEncrypt(),
            credentials.gid?.cipherEncrypt(),
            credentials.share
        )

        setResult(RESULT_OK, Intent().putExtra("CREDENTIALS", returnCredentials))
        exitOnBackPressed(false)
    }
}