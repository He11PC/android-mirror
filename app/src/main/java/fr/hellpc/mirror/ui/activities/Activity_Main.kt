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
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.END
import androidx.recyclerview.widget.ItemTouchHelper.START
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import fr.hellpc.mirror.App
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.Spinner_ColorAndText
import fr.hellpc.mirror.data.room.Backup_Colors
import fr.hellpc.mirror.databinding.ActivityMainBinding
import fr.hellpc.mirror.managers.Manager_Alerts
import fr.hellpc.mirror.managers.Manager_Notifications
import fr.hellpc.mirror.managers.Manager_Permissions
import fr.hellpc.mirror.managers.Manager_Workers
import fr.hellpc.mirror.ui.adapters.Adapter_Recycler_Main
import fr.hellpc.mirror.ui.adapters.Adapter_Spinner_Color
import fr.hellpc.mirror.ui.viewmodels.ViewModel_Main
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File

class Activity_Main : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val managerWorkers by lazy { Manager_Workers() }
    private val managerAlert by lazy { Manager_Alerts() }
    private val managerPermissions by lazy { Manager_Permissions() }
    private val managerNotifications by lazy { Manager_Notifications() }

    private var menuBackupAllIsVisible = false
    private var menuCancelAllIsVisible = false
    private var recyclerViewIsRefreshing = false

    // DB access
    private val viewModel: ViewModel_Main by viewModels { ViewModel_Main.Factory }


    // -----------------
    // Move backup cards
    // -----------------

    private val moveBackupCardsHelper by lazy {
        val simpleItemTouchCallback = object: ItemTouchHelper.SimpleCallback(UP or DOWN or START or END, START or END) {

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                if(!recyclerViewIsRefreshing) {
                    recyclerViewIsRefreshing = true
                    val from = viewHolder.absoluteAdapterPosition
                    val to = target.absoluteAdapterPosition
                    val adapter = recyclerView.adapter as Adapter_Recycler_Main
                    adapter.observeItemMove(from, to)
                    viewModel.backupCardsUpdatePosition(from, to)
                }
                return true
            }

            override fun isLongPressDragEnabled() = false
            override fun isItemViewSwipeEnabled() = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }
        }

        ItemTouchHelper(simpleItemTouchCallback)
    }


    // -------------
    // Load Activity
    // -------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.mainToolbar)

        // Open permissions activity if files access is not granted
        if(!managerPermissions.requiredPermissionsAreGranted())
            openPermissions(true)
        else {
            val init = savedInstanceState == null

            viewModel.resetStuckedStatus(init)

            toggleElementsVisibility()
            loadBackupsList()
            setupColorThemesSpinner()
            managePermanentNotificationService(init)
        }
    }


    // ------------
    // Backups list
    // ------------

    /** Load BackupsCondensed from DB to RecyclerView **/
    private fun loadBackupsList() {
        val recyclerBackup = binding.mainRecycler
        recyclerBackup.setHasFixedSize(true)
        recyclerBackup.setItemViewCacheSize(6)
        recyclerBackup.itemAnimator = null

        val gridSpanCount = resources.getInteger(R.integer.grid_column_count)
        val gridLayoutManager = GridLayoutManager(applicationContext, gridSpanCount)
        recyclerBackup.layoutManager = gridLayoutManager

        val adapter = Adapter_Recycler_Main(
            buttonClick = {
                when(it.action) {
                    "enable" -> enableBackup(it.info.id_backup, it.info.last_date)
                    "disable" -> disableBackup(it.info.id_backup, it.info.id_worker, it.info.id_scheduler)
                    "edit" -> editBackup(it.info.id_backup, it.info.isProtected)
                    "delete" -> deleteBackup(it.info.id_backup, it.info.id_worker, it.info.id_scheduler, it.info.position)
                    "launch" -> launchBackup(it.info.id_backup, it.info.wifi_only, true)
                    "cancel" -> cancelBackup(it.info.id_backup, it.info.id_worker)
                    "log" -> openBackupLog(it.info.id_backup)
                }
            },
            moveItem = { moveBackupCardsHelper.startDrag(it) },
            itemMoveDone = { recyclerViewIsRefreshing = false }
        )
        adapter.setHasStableIds(true)
        adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        recyclerBackup.adapter = adapter

        moveBackupCardsHelper.attachToRecyclerView(recyclerBackup)

        viewModel.allBackupsCondensed.observe(this) { adapter.submitList(it) }
    }


    // ----------------------
    // Menu buttons & actions
    // ----------------------

    /** Add long click listener to menu item **/
    private fun MenuItem.onMenuItemLongClickListener(menu: Menu, function: () -> (Unit)) {
        setActionView(R.layout.menu_button)
        actionView?.let {
            it.findViewById<ImageButton>(R.id.item).setImageDrawable(icon)
            it.setOnLongClickListener {
                function()
                true
            }
            it.setOnClickListener { menu.performIdentifierAction(itemId, 0) }
        }
    }

    /** Add menu **/
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    /** Update menu **/
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (menu != null) {
            menu.findItem(R.id.menu_launch_all).apply {
                isVisible = menuBackupAllIsVisible
                onMenuItemLongClickListener(menu) { openBackupColorThemesSpinner() }
            }
            menu.findItem(R.id.menu_cancel_all).isVisible = menuCancelAllIsVisible
        }
        return super.onPrepareOptionsMenu(menu)
    }

    /** Manage menu click events **/
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_launch_all -> {
                launchAllBackups()
                true
            }
            R.id.menu_cancel_all -> {
                cancelAllBackups()
                true
            }
            R.id.menu_add_new -> {
                addNewBackup()
                true
            }
            R.id.menu_settings -> {
                openSettings()
                true
            }
            R.id.menu_permissions -> {
                openPermissions(false)
                true
            }
            R.id.menu_about -> {
                openAbout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // -------------------------------------

    /** Launch all backups **/
    private fun launchAllBackups() = lifecycleScope.launch {
        if(checkEnoughBattery()) {
            var wifiRequired = false

            viewModel.getBackupsIdle().forEach {
                ensureActive()
                if(it.const_wifi)
                    wifiRequired = true
                launchBackup(it.id_backup, it.const_wifi, false)
            }
            checkWifi(wifiRequired)
        }
    }

    /** Launch backups with similar theme **/
    private fun launchThemeBackups(theme: Backup_Colors) = lifecycleScope.launch {
        if(checkEnoughBattery()) {
            var wifiRequired = false

            viewModel.getBackupsIdleWithTheme(theme).forEach {
                ensureActive()
                if(it.const_wifi)
                    wifiRequired = true
                launchBackup(it.id_backup, it.const_wifi, false)
            }
            checkWifi(wifiRequired)
        }
    }

    /** Cancel all backups **/
    private fun cancelAllBackups() = lifecycleScope.launch {
        viewModel.getBackupsRunning().forEach {
            ensureActive()
            it.id_worker?.let { id_worker -> cancelBackup(it.id_backup, id_worker) }
        }
    }

    /** Add new Backup **/
    private fun addNewBackup() = startActivity(Intent(this, Activity_Edit::class.java))

    /** Open Settings Activity **/
    private fun openSettings() = startActivity(Intent(this, Activity_Settings::class.java))

    /** Open Permissions Activity **/
    private fun openPermissions(reset: Boolean) {
        startActivity(Intent(this, Activity_Permissions::class.java).putExtra(Activity_Permissions.RESET, reset))
        if(reset)
            finish()
    }

    /** Open Permissions Activity **/
    private fun openAbout() = startActivity(Intent(this, Activity_About::class.java))

    // -------------------------------------

    /** Open backup themes list to let user launch all backups with similar theme **/
    private fun openBackupColorThemesSpinner() = lifecycleScope.launch {
        val themesSpinner = binding.mainSpnTheme
        if(themesSpinner.count > 1)
            themesSpinner.performClick()
    }


    // ---------
    // Observers
    // ---------

    /** Toggle tooltip and BackupAll/CancelAll buttons visibility **/
    private fun toggleElementsVisibility() {
        viewModel.backupsStatusCount.observe(this) { count ->
            // Tooltips
            val countTotal = count.idle + count.running
            if(countTotal == 0) {
                binding.mainTooltipAddTxt.visibility = View.VISIBLE
                binding.mainTooltipLyt.visibility = View.GONE
            }
            else {
                binding.mainTooltipAddTxt.visibility = View.GONE

                val shouldShowEditModeTip = managerAlert.showEditModeTip()
                val shouldShowGroupLaunchTip = countTotal > 1 && managerAlert.showGroupLaunchTip()

                if(!shouldShowEditModeTip && !shouldShowGroupLaunchTip)
                    binding.mainTooltipLyt.visibility = View.GONE
                else {
                    binding.mainTooltipLyt.visibility = View.VISIBLE

                    if(shouldShowEditModeTip) {
                        binding.mainTooltipBtnEditMode.visibility = View.VISIBLE
                        binding.mainTooltipBtnEditMode.setOnClickListener {
                            managerAlert.disableEditModeTip()
                            binding.mainTooltipBtnEditMode.visibility = View.GONE
                        }
                    }
                    else
                        binding.mainTooltipBtnEditMode.visibility = View.GONE

                    if(shouldShowGroupLaunchTip) {
                        binding.mainTooltipBtnGroupLaunch.visibility = View.VISIBLE
                        binding.mainTooltipBtnGroupLaunch.setOnClickListener {
                            managerAlert.disableGroupLaunchTip()
                            binding.mainTooltipBtnGroupLaunch.visibility = View.GONE
                        }
                    }
                    else
                        binding.mainTooltipBtnGroupLaunch.visibility = View.GONE
                }
            }

            // Menu buttons
            if(count.running > 0) {
                menuBackupAllIsVisible = false
                menuCancelAllIsVisible = true
            }
            else {
                menuCancelAllIsVisible = false
                menuBackupAllIsVisible = count.idle - count.disabled > 0
            }
            invalidateOptionsMenu()
        }
    }

    /** Add user configured themes to spinner for group backups launch **/
    private fun setupColorThemesSpinner() {
        var init = true

        val themesSpinner = binding.mainSpnTheme
        val adapter = Adapter_Spinner_Color(this, mutableListOf())
        themesSpinner.adapter = adapter

        viewModel.backupColorThemes.observe(this) {
            val spinnerData = it.mapIndexed { index, backupColors ->
                Spinner_ColorAndText(
                    getString(R.string.main_color_theme_group) + (index + 1),
                    backupColors.background,
                    backupColors.borders,
                    backupColors.icons,
                    backupColors.progressbar
                )
            }

            adapter.clear()
            adapter.addAll(spinnerData)
        }

        themesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if(!init) {
                    val theme = parent.getItemAtPosition(position) as Spinner_ColorAndText
                    val colors = Backup_Colors(
                        theme.colorBackground,
                        theme.colorBorders,
                        theme.colorIcons ?: 0,
                        theme.colorProgressbar ?: 0
                    )
                    launchThemeBackups(colors)
                }
                else
                    init = false
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
    }


    // ----------------------
    // Permanent Notification
    // ----------------------

    /** Observe scheduled backups count and manage permanent notification service accordingly **/
    private fun managePermanentNotificationService(init: Boolean) {
        viewModel.backupsScheduledCount.observe(this) { count ->
            val savedStatus = managerNotifications.getServiceStatus()
            val currentStatus = count > 0
            val hasChanged = savedStatus != currentStatus

            if(hasChanged || init) {
                managerNotifications.managePermanentNotificationService(currentStatus, hasChanged)
                managerWorkers.scheduleDailyStatsReset(currentStatus)
            }
        }
    }


    // --------------------
    // Backup cards actions
    // --------------------

    /** Edit Backup from RecyclerView **/
    private fun editBackup(id: Int, isProtected: Boolean) {
        if(isProtected && BiometricManager.from(App.instance).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS)
            Snackbar.make(binding.mainLyt, getString(R.string.backup_protect_warning), Snackbar.LENGTH_SHORT).setBackgroundTint(ContextCompat.getColor(this, R.color.red)).show()
        else
            startActivity(
                Intent(this, Activity_Edit::class.java)
                    .putExtra(Activity_Edit.PARAM_BACKUP_ID, id)
                    .putExtra(Activity_Edit.PARAM_IS_PROTECTED, isProtected)
            )
    }

    /** Delete Backup from database **/
    private fun deleteBackup(id_backup: Int, id_worker: String?, id_scheduler: String?, position: Int) {
        AlertDialog.Builder(this, R.style.AppTheme_AlertDialogStyle).apply {
            setTitle(getString(R.string.global_confirmation))
            setMessage(getString(R.string.backup_delete_warning_message))
            setPositiveButton(R.string.global_yes) { _,_ -> viewModel.deleteBackup(id_backup, id_worker, id_scheduler, position, File(applicationContext.filesDir.toString() + "/$id_backup")) }
            setNegativeButton(R.string.global_no) { _,_ -> }
            show()
        }
    }

    /** Start single backup **/
    private fun launchBackup(id_backup: Int, wifiOnly: Boolean, showAlerts: Boolean) = lifecycleScope.launch {
        val enoughBattery = if(showAlerts)
            checkEnoughBattery()
        else
            true

        if(enoughBattery)
            managerWorkers.launchBackup(id_backup, false)
        if(showAlerts)
            checkWifi(wifiOnly)
    }

    /** Cancel backup **/
    private fun cancelBackup(id_backup: Int, id_worker: String?) = lifecycleScope.launch {
        managerWorkers.cancelWorker(id_backup, id_worker, true)
    }

    /** Open backup Log **/
    private fun openBackupLog(id_backup: Int) {
        val intent = Intent(this, Activity_Logs::class.java).putExtra(Activity_Edit.PARAM_BACKUP_ID, id_backup)
        startActivity(intent)
    }

    /** Disable backup **/
    private fun disableBackup(id_backup: Int, id_worker: String?, id_scheduler: String?) {
        viewModel.disableBackup(id_backup, id_worker, id_scheduler)
    }

    /** Enable backup **/
    private fun enableBackup(id_backup: Int, lastDate: Long?) {
        viewModel.enableBackup(id_backup, lastDate)
    }


    // -------------
    // Verifications
    // -------------

    private fun checkEnoughBattery(): Boolean {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter -> this.registerReceiver(null, ifilter) }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val batteryPct: Float? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }

        return if (batteryPct != null) {
            if (batteryPct > 15 || isCharging)
                true
            else {
                Snackbar.make(binding.mainLyt, getString(R.string.alert_low_battery), Snackbar.LENGTH_SHORT).setBackgroundTint(ContextCompat.getColor(this, R.color.orange)).show()
                false
            }
        } else
            true
    }

    private fun checkWifi(wifiOnly: Boolean) = lifecycleScope.launch {
        if(wifiOnly) {
            val connectivityManager = applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

            if(capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                AlertDialog.Builder(this@Activity_Main, R.style.AppTheme_AlertDialogStyle).apply {
                    setTitle(R.string.wifi_required_title)
                    setMessage(R.string.wifi_required_text)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                            startActivity(Intent(Settings.Panel.ACTION_WIFI))
                        else {
                            val wifiMgr = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                            wifiMgr.isWifiEnabled = true
                        }
                    }
                    setNegativeButton(android.R.string.cancel) { _, _ -> }
                    show()
                }
        }
    }
}