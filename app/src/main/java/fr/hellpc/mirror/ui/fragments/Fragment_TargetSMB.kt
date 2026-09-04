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

package fr.hellpc.mirror.ui.fragments

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.databinding.FragmentTargetSmbBinding
import fr.hellpc.mirror.ui.activities.Activity_Autofill
import fr.hellpc.mirror.ui.activities.Activity_FolderExplorer
import fr.hellpc.mirror.ui.viewmodels.ViewModel_Edit
import fr.hellpc.mirror.data.Target_Credentials
import fr.hellpc.mirror.utilities.Utility_BackupTarget
import fr.hellpc.mirror.security.Security_Encryption.cipherDecrypt
import fr.hellpc.mirror.security.Security_Encryption.cipherEncrypt
import kotlinx.coroutines.launch
import java.io.Serializable


class Fragment_TargetSMB : Fragment() {

    private var _binding: FragmentTargetSmbBinding? = null
    private val binding get() = _binding!!

    private val editUtils by lazy { Utility_BackupTarget() }
    private var fragmentProtocol = "SMB"

    // DB access
    private val viewModel: ViewModel_Edit by activityViewModels()

    companion object {
        fun newInstance(tab: String, protocol: String) : Fragment_TargetSMB {
            val args = Bundle()
            args.putString("TAB", tab)
            args.putString("PROTOCOL", protocol)
            val fragment = Fragment_TargetSMB()
            fragment.arguments = args
            return fragment
        }
    }

    // -------------------------------------

    private var autoFill = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode == Activity.RESULT_OK) {
            result.data?.getSerializable<Target_Credentials>("CREDENTIALS")?.let { loadCredentialsToUi(it) }
            setFocus(binding.targetSmbEditPath)
        }
    }

    private inline fun <reified T : Serializable> Intent.getSerializable(key: String): T? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializableExtra(key, T::class.java)
        else -> @Suppress("DEPRECATION") getSerializableExtra(key) as? T
    }

    // -------------------------------------

    private var folderExplorer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode == Activity.RESULT_OK)
            result.data?.getStringExtra("FOLDER")?.let {
                binding.targetSmbEditPath.setText(it)
                binding.targetSmbEditPath.setSelection(binding.targetSmbEditPath.text.length)
            }
    }


    // --------
    // Fragment
    // --------

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTargetSmbBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initialize(savedInstanceState == null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    // --------------
    // User interface
    // --------------

    /** Initialize UI **/
    private fun initialize(loadData: Boolean) {
        requireArguments().getString("PROTOCOL")?.let { fragmentProtocol = it }
        val isSource = requireArguments().getString("TAB") == viewModel.srcTabName

        setupListeners()
        setupEditFilters()
        setupAutofill(isSource)

        if(loadData)
            loadDataFromDb(isSource)
    }

    /** Setup listeners **/
    private fun setupListeners() = lifecycleScope.launch {
        binding.targetSmbBtnCredentialsFill.setOnClickListener { openAutoFill() }
        binding.targetSmbBtnPathSearch.setOnClickListener { openFolderExplorer() }
    }

    /** Setup filters for EditText **/
    private fun setupEditFilters() = lifecycleScope.launch {
        binding.targetSmbEditServer.filters= arrayOf(InputFilter { source, _, _, _, _, _ ->
            source.filter { editUtils.getAllowedCharactersServer().matches(it.toString()) }
        })

        binding.targetSmbEditShare.filters= arrayOf(InputFilter { source, _, _, _, _, _ ->
            source.filter { editUtils.getAllowedCharactersDirectory().matches(it.toString()) }
        })

        binding.targetSmbEditPath.filters= arrayOf(InputFilter { source, _, _, _, _, _ ->
            source.filter { editUtils.getAllowedCharactersPath().matches(it.toString()) }
        })
    }

    /** Setup Autofill button **/
    private fun setupAutofill(isSource: Boolean) {
        val credentialsExist = if(isSource)
            viewModel.srcCredentialsExist
        else
            viewModel.destCredentialsExist

        credentialsExist.observe(viewLifecycleOwner) { exist ->
            if(exist && BiometricManager.from(App.instance).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS)
                binding.targetSmbBtnCredentialsFill.visibility = View.VISIBLE
            else
                binding.targetSmbBtnCredentialsFill.visibility = View.GONE
        }

        viewModel.checkBackupCredentials(isSource, fragmentProtocol)
    }

    /** Change editText focus **/
    private fun setFocus(view: View) = lifecycleScope.launch {
        if(!view.hasFocus())
            view.requestFocus()
    }


    // ---------------
    // Open activities
    // ---------------

    /** Open AutoFill activity **/
    private fun openAutoFill() {
        val intent = Intent(this.context, Activity_Autofill::class.java)
            .putExtra(Activity_Autofill.PARAM_PROTOCOL, fragmentProtocol)

        autoFill.launch(intent)
    }

    /** Open Folder Explorer activity **/
    private fun openFolderExplorer() {
        getCurrentData(true)?.let {
           setFocus(binding.targetSmbEditPath)

            val intent = Intent(this.context, Activity_FolderExplorer::class.java)
                .putExtra(Activity_FolderExplorer.PARAM_PROTOCOL, it.protocol)
                .putExtra(Activity_FolderExplorer.PARAM_SERVER, it.server)
                .putExtra(Activity_FolderExplorer.PARAM_DOMAIN, it.domain)
                .putExtra(Activity_FolderExplorer.PARAM_LOGIN, it.login)
                .putExtra(Activity_FolderExplorer.PARAM_PASSWORD, it.password)
                .putExtra(Activity_FolderExplorer.PARAM_SHARE, it.share)
                .putExtra(Activity_FolderExplorer.PARAM_PATH, it.path)

            folderExplorer.launch(intent)
        }
    }


    // ----
    // Data
    // ----

    /** Get current data from UI **/
    fun getCurrentData(showWarning: Boolean): Backup_Target? {
        val server = editUtils.trimServer(binding.targetSmbEditServer.text.toString())
        val domain = editUtils.trimServer(binding.targetSmbEditDomain.text.toString())
        val share = editUtils.trimDirectory(binding.targetSmbEditShare.text.toString())
        val path = editUtils.trimPath(binding.targetSmbEditPath.text.toString(), false)

        return if(server.isBlank()) {
            if(showWarning) {
                setFocus(binding.targetSmbEditServer)
                Snackbar.make(requireActivity().findViewById(R.id.edit_lyt), getString(R.string.folder_explorer_missing_address), Snackbar.LENGTH_SHORT).setBackgroundTint(ContextCompat.getColor(App.instance, R.color.orange)).show()
            }
            null
        }
        else if(!editUtils.directoryIsValid(share, false)) {
            if (showWarning) {
                setFocus(binding.targetSmbEditShare)
                Snackbar.make(requireActivity().findViewById(R.id.edit_lyt), getString(R.string.folder_explorer_missing_share), Snackbar.LENGTH_SHORT).setBackgroundTint(ContextCompat.getColor(App.instance, R.color.orange)).show()
            }
            null
        }
        else if(!showWarning && !editUtils.pathIsValid(path, true))
            null
        else
            Backup_Target(
                fragmentProtocol,
                server.cipherEncrypt(),
                domain,
                share,
                path,
                null,
                null,
                null,
                binding.targetSmbEditLogin.text.toString().ifBlank { null }?.cipherEncrypt(),
                binding.targetSmbEditPassword.text.toString().ifBlank { null }?.cipherEncrypt(),
                null,
                null
            )
    }

    /** Load data from Database **/
    private fun loadDataFromDb(isSource: Boolean) {
        val target = if(isSource)
            viewModel.backupSrc
        else
            viewModel.backupDest

        target.observe(viewLifecycleOwner) { data ->
            binding.targetSmbEditPath.setText(data.path)
            loadCredentialsToUi(
                Target_Credentials(
                    fragmentProtocol,
                    data.server,
                    data.domain,
                    data.port,
                    data.ssl,
                    data.hostKey,
                    data.login,
                    data.password,
                    data.uid,
                    data.gid,
                    data.share
                )
            )
        }
    }

    /** Load target credentials to UI **/
    private fun loadCredentialsToUi(data: Target_Credentials) {
        data.server?.cipherDecrypt()?.let { binding.targetSmbEditServer.setText(it) }
        data.domain?.let { binding.targetSmbEditDomain.setText(it) }
        data.share?.let { binding.targetSmbEditShare.setText(it) }
        data.login?.cipherDecrypt()?.let { binding.targetSmbEditLogin.setText(it) }
        data.password?.cipherDecrypt()?.let { binding.targetSmbEditPassword.setText(it) }
    }
}