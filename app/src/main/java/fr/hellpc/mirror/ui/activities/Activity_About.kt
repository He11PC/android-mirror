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
import android.text.method.LinkMovementMethod
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import fr.hellpc.mirror.BuildConfig
import fr.hellpc.mirror.R
import fr.hellpc.mirror.databinding.ActivityAboutBinding

class Activity_About : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    // -------------------------------------

    // OnBackPressed
    private val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { finish() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // -------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.aboutToolbar)

        // Back arrow
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val txtVersion = "v${BuildConfig.VERSION_NAME}"
        binding.aboutAppTxtVersion.text = txtVersion

        // Clickable html links
        binding.aboutAppTxtAuthor.movementMethod = LinkMovementMethod.getInstance()
        binding.aboutAppTxtLicense.movementMethod = LinkMovementMethod.getInstance()
        binding.aboutLibrariesTxtNfs.movementMethod = LinkMovementMethod.getInstance()
        binding.aboutLibrariesTxtSmb.movementMethod = LinkMovementMethod.getInstance()
        binding.aboutLibrariesTxtFtp.movementMethod = LinkMovementMethod.getInstance()
        binding.aboutLibrariesTxtSftp.movementMethod = LinkMovementMethod.getInstance()
        binding.aboutLibrariesTxtWebdav.movementMethod = LinkMovementMethod.getInstance()
        binding.aboutLibrariesTxtOkhttp.movementMethod = LinkMovementMethod.getInstance()
        binding.aboutLibrariesTxtAutostarter.movementMethod = LinkMovementMethod.getInstance()

        // Support icons
        binding.aboutSupportImgCoffee.setOnClickListener {
            val intent = Intent().setAction(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE).setData("https://buymeacoffee.com/hellpc".toUri())
            startActivity(intent)
        }
    }
}