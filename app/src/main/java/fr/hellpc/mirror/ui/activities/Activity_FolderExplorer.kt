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
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.room.Backup_Target
import fr.hellpc.mirror.databinding.ActivityFolderExplorerBinding
import fr.hellpc.mirror.ui.adapters.Adapter_Recycler_FolderExplorer
import fr.hellpc.mirror.ui.viewmodels.ViewModel_FolderExplorer
import kotlinx.coroutines.launch

class Activity_FolderExplorer: AppCompatActivity() {

    private lateinit var binding: ActivityFolderExplorerBinding
    private val viewModel: ViewModel_FolderExplorer by viewModels { ViewModel_FolderExplorer.Factory }

    private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { exitOnBackPressed(true) }
    }

    // -------------------------------------

    companion object {
        const val PARAM_PROTOCOL = "PARAM_PROTOCOL"
        const val PARAM_SERVER = "PARAM_SERVER"
        const val PARAM_DOMAIN = "PARAM_DOMAIN"
        const val PARAM_SHARE = "PARAM_SHARE"
        const val PARAM_PATH = "PARAM_PATH"
        const val PARAM_PORT = "PARAM_PORT"
        const val PARAM_SSL = "PARAM_SSL"
        const val PARAM_HOSTKEY = "PARAM_HOSTKEY"
        const val PARAM_LOGIN = "PARAM_LOGIN"
        const val PARAM_PASSWORD = "PARAM_PASSWORD"
        const val PARAM_UID = "PARAM_UID"
        const val PARAM_GID = "PARAM_GID"
    }

    // -------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFolderExplorerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        initialize(savedInstanceState == null)
    }

    // -------------------------------------

    private fun getExtras() {
        val protocol = intent.getStringExtra(PARAM_PROTOCOL)
        if(protocol.isNullOrBlank())
            showError(getString(R.string.folder_explorer_error_protocol))
        else {
            val server = intent.getStringExtra(PARAM_SERVER)
            val domain = intent.getStringExtra(PARAM_DOMAIN)
            val share = intent.getStringExtra(PARAM_SHARE)
            val path = intent.getStringExtra(PARAM_PATH) ?: ""
            val port = intent.getIntExtra(PARAM_PORT, -1)
            val ssl = intent.getBooleanExtra(PARAM_SSL, true)
            val hostKey = intent.getStringExtra(PARAM_HOSTKEY)
            val login = intent.getStringExtra(PARAM_LOGIN)
            val password = intent.getStringExtra(PARAM_PASSWORD)
            val uid = intent.getStringExtra(PARAM_UID)
            val gid = intent.getStringExtra(PARAM_GID)

            viewModel.initialize(Backup_Target(protocol, server, domain, share, path, port, ssl, hostKey, login, password, uid, gid))
        }
    }

    private fun initialize(loadData: Boolean) {
        setupListeners()
        setupObservers()
        setupRecycler()

        if(loadData) {
            getExtras()
            connect()
        }
    }

    private fun exitOnBackPressed(isCancellation: Boolean) = lifecycleScope.launch {
        try { viewModel.disconnect() }
        catch(_: Exception) { }

        if(isCancellation)
            setResult(RESULT_CANCELED)

        finish()
    }

    // -------------------------------------

    /** Setup OK and Cancel buttons listeners **/
    private fun setupListeners() = lifecycleScope.launch {
        binding.folderExplorerBtnCancel.setOnClickListener {
            exitOnBackPressed(true)
        }
        
        binding.folderExplorerBtnOk.setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra("FOLDER", viewModel.path.value))
            exitOnBackPressed(false)
        }
        
        binding.folderExplorerSwipeRefresh.setOnRefreshListener {
            binding.folderExplorerSwipeRefresh.isRefreshing = false
            loadFolder(null)
        }        
    }

    /** Setup livedata observers **/
    private fun setupObservers() {
        viewModel.path.observe(this) { path -> binding.folderExplorerTxtPath.text = path }

        viewModel.isLoading.observe(this) { isLoading ->
            if(isLoading) {
                binding.folderExplorerBtnOk.visibility = View.GONE
                binding.folderExplorerProgressLoading.visibility = View.VISIBLE
            }
            else {
                binding.folderExplorerProgressLoading.visibility = View.GONE
                binding.folderExplorerBtnOk.visibility = View.VISIBLE
            }
        }

        viewModel.errorMsg.observe(this) { errorMsg ->
            binding.folderExplorerTxtError.text = errorMsg

            if(errorMsg.isNullOrBlank()) {
                binding.folderExplorerBtnOk.visibility = View.VISIBLE
                binding.folderExplorerTxtError.visibility = View.GONE
            }
            else {
                binding.folderExplorerBtnOk.visibility = View.GONE
                binding.folderExplorerProgressLoading.visibility = View.GONE
                binding.folderExplorerTxtError.visibility = View.VISIBLE
            }
        }
    }

    /** Setup RecyclerView **/
    private fun setupRecycler() {
        val recyclerFolders = binding.folderExplorerRecycler
        recyclerFolders.setHasFixedSize(true)
        recyclerFolders.setItemViewCacheSize(24)
        recyclerFolders.itemAnimator = null

        val gridSpanCount = resources.getInteger(R.integer.grid_column_count)
        val gridLayoutManager = GridLayoutManager(applicationContext, gridSpanCount)
        recyclerFolders.layoutManager = gridLayoutManager

        val recycleAdapter = Adapter_Recycler_FolderExplorer(folderSelected = { loadFolder(it) })
        recycleAdapter.setHasStableIds(false)
        recycleAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        recyclerFolders.adapter = recycleAdapter
        
        viewModel.folderList.observe(this) { recycleAdapter.submitList(it) }
    }

    // -------------------------------------

    /** Show error message and manage views visibility accordingly **/
    private fun showError(error: String?) {
        var errorMsg = if(!viewModel.isConnected)
            getString(R.string.folder_explorer_unable_connect)
        else
            getString(R.string.folder_explorer_unable_list)

        if(!error.isNullOrBlank())
             errorMsg += "\n\n" + getString(R.string.folder_explorer_error_details) + " $error"

        viewModel.showError(errorMsg)
    }

    // -------------------------------------

    /** Connect to remote server and load content **/
    private fun connect() = lifecycleScope.launch {
        try { viewModel.connect() }
        catch(exp: Exception) { showError(exp.message.toString()) }

        loadFolder(null)
    }

    /** Load folder content and displays it **/
    private fun loadFolder(open: String?) = lifecycleScope.launch {
        try { viewModel.loadFolder(open) }
        catch(exp: Exception) { showError(exp.message.toString()) }
    }
}