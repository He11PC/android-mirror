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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import fr.hellpc.mirror.R
import fr.hellpc.mirror.databinding.ActivityLogsBinding
import fr.hellpc.mirror.managers.Manager_Settings
import fr.hellpc.mirror.ui.adapters.Adapter_Recycler_Logs
import fr.hellpc.mirror.ui.viewmodels.ViewModel_Logs
import fr.hellpc.mirror.data.Logs_NavigationPosition
import fr.hellpc.mirror.utilities.Utility_HorizontalSwipe
import kotlinx.coroutines.launch

class Activity_Logs : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private val viewModel: ViewModel_Logs by viewModels { ViewModel_Logs.Factory }

    private val preserveScrollPosition by lazy { Manager_Settings().getLogsPreserveScrollPosition() }

    private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { finish() }
    }

    companion object {
        const val PARAM_BACKUP_ID = "PARAM_BACKUP_ID"
    }

    // -------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        initialize(savedInstanceState == null)
    }

    /** Initialize interface **/
    private fun initialize(loadData: Boolean) {
        setupListeners()
        setupObservers()
        setupRecycler()

        if(loadData)
            viewModel.initialize(intent.getIntExtra(PARAM_BACKUP_ID, -1))
    }

    // -------------------------------------

    /** Setup RecyclerView **/
    private fun setupRecycler() {
        val recyclerLog = binding.logsRecycler
        recyclerLog.setHasFixedSize(true)
        recyclerLog.setItemViewCacheSize(25)
        recyclerLog.itemAnimator = null

        val recycleAdapter = Adapter_Recycler_Logs()
        recycleAdapter.setHasStableIds(false)
        recycleAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        recyclerLog.adapter = recycleAdapter

        viewModel.logFile.observe(this) {
            if(it == null)
                viewModel.updateErrorMessage(getString(R.string.logs_file_read_error))
            else
                viewModel.updateErrorMessage(null)
            if(!preserveScrollPosition)
                recyclerLog.scrollToPosition(0)
            recycleAdapter.submitList(it ?: emptyList())
        }
    }

    /** Setup observers **/
    private fun setupObservers() {
        viewModel.error.observe(this) { error ->
            val errorView = binding.logsTxtError

            if(error.isNullOrEmpty())
                errorView.visibility = View.GONE
            else {
                errorView.text = error
                errorView.visibility = View.VISIBLE
            }
        }

        viewModel.showNavigationText.observe(this) { visible ->
            binding.logsNavigationTxtPosition.visibility = if(visible) View.VISIBLE else View.INVISIBLE
        }

        viewModel.position.observe(this) { position ->
            if(position == null)
                showInitError(getString(R.string.logs_folder_not_found))
            else {
                when(position.logCount) {
                    0 -> showInitError(getString(R.string.logs_file_not_found))
                    1 -> binding.logsNavigationLyt.visibility = View.GONE
                    else -> {
                        refreshNavigationBar(position)
                        refreshNavigationText(position)
                    }
                }
            }
        }
    }

    /** Setup listeners **/
    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() = lifecycleScope.launch {
        binding.logsRecycler.setOnTouchListener(object : Utility_HorizontalSwipe(applicationContext) {
            override fun onRightSwipe() { viewModel.changePosition(-1) }
            override fun onLeftSwipe() { viewModel.changePosition(1) }
        })

        binding.logsNavigationBtnPrevious.setOnClickListener { viewModel.changePosition(-1) }

        binding.logsNavigationBtnNext.setOnClickListener { viewModel.changePosition(1) }

        binding.logsNavigationBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                if(fromUser)
                    refreshNavigationText(Logs_NavigationPosition(progress, seek.max))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) { }

            override fun onStopTrackingTouch(seek: SeekBar) { viewModel.jumpToPosition(seek.progress) }
        })
    }

    // -------------------------------------

    /** Refresh navigation position text **/
    private fun refreshNavigationBar(position: Logs_NavigationPosition) = lifecycleScope.launch {
        binding.logsNavigationBar.max = position.logCount
        binding.logsNavigationBar.progress = position.position
    }

    /** Refresh navigation position text **/
    private fun refreshNavigationText(position: Logs_NavigationPosition) = lifecycleScope.launch {
        val positionView = binding.logsNavigationTxtPosition
        val positionText = "${position.position}/${position.logCount}"

        positionView.visibility = View.VISIBLE
        positionView.text = positionText
        viewModel.showNavigationText()
    }

    // -------------------------------------

    /** Error while loading logs folder **/
    private fun showInitError(message: String) {
        viewModel.updateErrorMessage(message)
        binding.logsNavigationLyt.visibility = View.GONE
    }
}