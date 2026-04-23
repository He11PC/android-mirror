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
import fr.hellpc.mirror.databinding.FragmentTargetWebdavBinding
import fr.hellpc.mirror.ui.activities.Activity_Autofill
import fr.hellpc.mirror.ui.activities.Activity_FolderExplorer
import fr.hellpc.mirror.ui.viewmodels.ViewModel_Edit
import fr.hellpc.mirror.data.Target_Credentials
import fr.hellpc.mirror.utilities.Utility_BackupTarget
import fr.hellpc.mirror.utilities.Utility_Encryption.cipherDecrypt
import fr.hellpc.mirror.utilities.Utility_Encryption.cipherEncrypt
import kotlinx.coroutines.launch
import java.io.Serializable
import java.util.Locale


class Fragment_TargetWebDav : Fragment() {

    private var _binding: FragmentTargetWebdavBinding? = null
    private val binding get() = _binding!!

    private val editUtils by lazy { Utility_BackupTarget() }
    private var fragmentProtocol = "WDAV"

    // DB access
    private val viewModel: ViewModel_Edit by activityViewModels()

    companion object {
        fun newInstance(tab: String, protocol: String) : Fragment_TargetWebDav {
            val args = Bundle()
            args.putString("TAB", tab)
            args.putString("PROTOCOL", protocol)
            val fragment = Fragment_TargetWebDav()
            fragment.arguments = args
            return fragment
        }
    }

    // -------------------------------------

    private var autoFill = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode == Activity.RESULT_OK) {
            result.data?.getSerializable<Target_Credentials>("CREDENTIALS")?.let { loadCredentialsToUi(it) }
            setFocus(binding.targetWebdavEditPath)
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
                binding.targetWebdavEditPath.setText(it)
                binding.targetWebdavEditPath.setSelection(binding.targetWebdavEditPath.text.length)
            }
    }


    // --------
    // Fragment
    // --------

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTargetWebdavBinding.inflate(inflater, container, false)
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
        // Change port hint depending on SSL checkbox & show self signed certificates warning
        binding.targetWebdavChkSsl.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                binding.targetWebdavEditPort.hint = getString(R.string.target_webdav_port_ssl_hint)
                binding.targetWebdavTxtWarningSsl.visibility = View.VISIBLE
            }
            else {
                binding.targetWebdavEditPort.hint = getString(R.string.target_webdav_port_hint)
                binding.targetWebdavTxtWarningSsl.visibility = View.GONE
            }
        }

        binding.targetWebdavBtnCredentialsFill.setOnClickListener { openAutoFill() }
        binding.targetWebdavBtnPathSearch.setOnClickListener { openFolderExplorer() }
    }

    /** Setup filters for EditText **/
    private fun setupEditFilters() = lifecycleScope.launch {
        binding.targetWebdavEditServer.filters= arrayOf(InputFilter { source, _, _, _, _, _ ->
            source.filter { editUtils.getAllowedCharactersServer().matches(it.toString()) }
        })

        binding.targetWebdavEditPath.filters= arrayOf(InputFilter { source, _, _, _, _, _ ->
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
                binding.targetWebdavBtnCredentialsFill.visibility = View.VISIBLE
            else
                binding.targetWebdavBtnCredentialsFill.visibility = View.GONE
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
            setFocus(binding.targetWebdavEditPath)

            val intent = Intent(this.context, Activity_FolderExplorer::class.java)
                .putExtra(Activity_FolderExplorer.PARAM_PROTOCOL, it.protocol)
                .putExtra(Activity_FolderExplorer.PARAM_SERVER, it.server)
                .putExtra(Activity_FolderExplorer.PARAM_PATH, it.path)
                .putExtra(Activity_FolderExplorer.PARAM_PORT, it.port)
                .putExtra(Activity_FolderExplorer.PARAM_SSL, it.ssl)
                .putExtra(Activity_FolderExplorer.PARAM_LOGIN, it.login)
                .putExtra(Activity_FolderExplorer.PARAM_PASSWORD, it.password)

            folderExplorer.launch(intent)
        }
    }


    // ----
    // Data
    // ----

    /** Get current data from UI **/
    fun getCurrentData(showWarning: Boolean): Backup_Target? {
        val server = editUtils.trimServer(binding.targetWebdavEditServer.text.toString())
        val path = editUtils.trimPath(binding.targetWebdavEditPath.text.toString())
        val port = binding.targetWebdavEditPort.text.toString().toIntOrNull()
            ?: binding.targetWebdavEditPort.hint.toString().toIntOrNull()
            ?: if(binding.targetWebdavChkSsl.isChecked)
                getString(R.string.target_webdav_port_ssl_hint).toInt()
            else
                getString(R.string.target_webdav_port_hint).toInt()

        return if(server.isBlank()) {
            if(showWarning) {
                setFocus(binding.targetWebdavEditServer)
                Snackbar.make(requireActivity().findViewById(R.id.edit_lyt), getString(R.string.folder_explorer_missing_info), Snackbar.LENGTH_SHORT).setBackgroundTint(ContextCompat.getColor(App.instance, R.color.orange)).show()
            }
            null
        }
        else if(!showWarning && !editUtils.pathIsValid(path, true))
            null
        else
            Backup_Target(
                fragmentProtocol,
                server.cipherEncrypt(),
                null,
                null,
                path,
                port,
                binding.targetWebdavChkSsl.isChecked,
                null,
                binding.targetWebdavEditLogin.text.toString().ifBlank { null }?.cipherEncrypt(),
                binding.targetWebdavEditPassword.text.toString().ifBlank { null }?.cipherEncrypt(),
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
            binding.targetWebdavEditPath.setText(data.path)
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
        data.server?.cipherDecrypt().let { binding.targetWebdavEditServer.setText(it) }
        data.port?.let { binding.targetWebdavEditPort.setText(String.format(Locale.getDefault(), "%d", data.port)) }
        data.login?.cipherDecrypt().let { binding.targetWebdavEditLogin.setText(it) }
        data.password?.cipherDecrypt().let { binding.targetWebdavEditPassword.setText(it) }
        if (data.ssl != null) {
            binding.targetWebdavChkSsl.isChecked = data.ssl
            binding.targetWebdavChkSsl.jumpDrawablesToCurrentState()
        }
    }
}