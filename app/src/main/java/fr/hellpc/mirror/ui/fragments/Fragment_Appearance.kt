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

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import fr.hellpc.mirror.R
import fr.hellpc.mirror.data.room.Backup_Colors
import fr.hellpc.mirror.databinding.FragmentAppearanceBinding
import fr.hellpc.mirror.ui.adapters.Adapter_Spinner_Color
import fr.hellpc.mirror.ui.viewmodels.ViewModel_Edit
import fr.hellpc.mirror.data.Spinner_ColorAndText
import kotlinx.coroutines.launch

class Fragment_Appearance : Fragment() {

    private var _binding: FragmentAppearanceBinding? = null
    private val binding get() = _binding!!

    // DB access
    private val viewModel: ViewModel_Edit by activityViewModels()

    private val colorNameDefault by lazy { resources.getStringArray(R.array.color_name_default).toList() }
    private val colorNameNone by lazy { resources.getStringArray(R.array.color_name_none).toList() }
    private val colorValueBackground by lazy { resources.getIntArray(R.array.color_value_background).toList() }
    private val colorValueBorders by lazy { resources.getIntArray(R.array.color_value_borders).toList() }
    private val colorValueIcons by lazy { resources.getIntArray(R.array.color_value_icons).toList() }
    private val colorValueProgressbarSecondary by lazy { resources.getIntArray(R.array.color_value_progressbar_secondary).toList() }

    companion object {
        fun newInstance(): Fragment_Appearance { return Fragment_Appearance() }
    }


    // --------
    // Fragment
    // --------

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppearanceBinding.inflate(inflater, container, false)
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
        setupColorSpinners()
        resizeTextViews()

        if(loadData || viewModel.backupIsLocked())
            loadDataFromDb()
    }

    // -------------------------------------

    /** Resize appearance textViews for alignment **/
    private fun resizeTextViews() = lifecycleScope.launch {
        binding.appearanceTxtBackground.measure(0,0)
        binding.appearanceTxtBorders.measure(0,0)
        binding.appearanceTxtIcons.measure(0,0)
        binding.appearanceTxtProgressbar.measure(0,0)

        val maxWidth = listOf(
            binding.appearanceTxtBackground.measuredWidth,
            binding.appearanceTxtBorders.measuredWidth,
            binding.appearanceTxtIcons.measuredWidth,
            binding.appearanceTxtProgressbar.measuredWidth
        ).maxOf { it }

        binding.appearanceTxtBackground.layoutParams.apply { width = maxWidth }
        binding.appearanceTxtBorders.layoutParams.apply { width = maxWidth }
        binding.appearanceTxtIcons.layoutParams.apply { width = maxWidth }
        binding.appearanceTxtProgressbar.layoutParams.apply { width = maxWidth }
    }

    // -------------------------------------

    /** Setup color spinners **/
    private fun setupColorSpinners() {
        setupSpinnerBackground()
        setupSpinnerBorders()
        setupSpinnerIcons()
        setupSpinnerProgressbar()
        setupSpinnerTheme()
    }

    /** Setup background color spinner **/
    private fun setupSpinnerBackground() = lifecycleScope.launch {
        val colorList = colorNameDefault.mapIndexed { index, s ->
            Spinner_ColorAndText(s, index, 0, null, null)
        }

        val adapter = Adapter_Spinner_Color(requireContext(), colorList)
        binding.appearanceSpnBackground.adapter = adapter

        // Listener
        binding.appearanceSpnBackground.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updatePreviewBackground(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { parent?.setSelection(0) }
        }
    }

    /** Setup borders color spinner **/
    private fun setupSpinnerBorders() = lifecycleScope.launch {
        val colorList = colorNameNone.mapIndexed { index, s ->
            Spinner_ColorAndText(s, 0, index, null, null)
        }

        val adapter = Adapter_Spinner_Color(requireContext(), colorList)
        binding.appearanceSpnBorders.adapter = adapter

        // Listener
        binding.appearanceSpnBorders.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updatePreviewBorders(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { parent?.setSelection(0) }
        }
    }

    /** Setup icons color spinner **/
    private fun setupSpinnerIcons() = lifecycleScope.launch {
        val colorList = colorNameDefault.mapIndexed { index, s ->
            Spinner_ColorAndText(s, 0, 0, index, null)
        }

        val adapter = Adapter_Spinner_Color(requireContext(), colorList)
        binding.appearanceSpnIcons.adapter = adapter

        // Listener
        binding.appearanceSpnIcons.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updatePreviewIcon(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { parent?.setSelection(0) }
        }
    }

    /** Setup progressbar color spinner **/
    private fun setupSpinnerProgressbar() = lifecycleScope.launch {
        val colorList = colorNameDefault.mapIndexed { index, s ->
            Spinner_ColorAndText(s, 0, 0, null, index)
        }

        val adapter = Adapter_Spinner_Color(requireContext(), colorList)
        binding.appearanceSpnProgressbar.adapter = adapter

        // Listener
        binding.appearanceSpnProgressbar.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updatePreviewProgressbar(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { parent?.setSelection(0) }
        }
    }

    /** Setup theme spinner **/
    private fun setupSpinnerTheme() = lifecycleScope.launch {
        var init = true

        val themesDefault = colorNameDefault.mapIndexed { index, s ->
            Spinner_ColorAndText(s, index, index, index, index)
        }

        val themesUser = viewModel.getUserThemes().mapIndexed { index, backupColors ->
            Spinner_ColorAndText(
                getString(R.string.backup_appearance_theme_custom) + (index + 1),
                backupColors.background,
                backupColors.borders,
                backupColors.icons,
                backupColors.progressbar
            )
        }

        val adapter = Adapter_Spinner_Color(requireContext(), themesUser + themesDefault)
        binding.appearanceSpnTheme.adapter = adapter

        // Listener - Open theme list
        binding.previewLyt.setOnClickListener {
            binding.appearanceSpnTheme.performClick()
        }

        // Listener - Load selected theme
        binding.appearanceSpnTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if(!init) {
                    val theme = parent.getItemAtPosition(position) as Spinner_ColorAndText
                    val colors = Backup_Colors(
                        theme.colorBackground,
                        theme.colorBorders,
                        theme.colorIcons ?: 0,
                        theme.colorProgressbar ?: 0
                    )
                    loadDataToUI(colors)
                }
                else
                    init = false
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
    }

    // -------------------------------------

    /** Get mutated background **/
    private fun getPreviewBackground(): GradientDrawable {
        val background = binding.previewLyt.background as GradientDrawable
        background.mutate()
        return background
    }

    /** Update appearance preview background **/
    private fun updatePreviewBackground(color: Int) {
        val background = getPreviewBackground()
        background.setColor(colorValueBackground[color])
    }

    /** Update appearance preview borders **/
    private fun updatePreviewBorders(color: Int) {
        val background = getPreviewBackground()
        color.takeIf { it > 0 }?.let { background.setStroke(2, colorValueBorders[it]) }
            ?: background.setStroke(0, null)
    }

    /** Update appearance preview icon **/
    private fun updatePreviewIcon(color: Int) {
        (color.takeIf { it >=0 } ?: 0).let { colorIcon ->
            val icon = binding.previewTxt.compoundDrawables[0]
            icon?.let {
                it.mutate()
                it.setTint(colorValueIcons[colorIcon])
            }
        }
    }

    /** Update appearance preview progressbar **/
    private fun updatePreviewProgressbar(color: Int) {
        binding.previewProgressbar.progressTintList = ColorStateList.valueOf(
            color.takeIf { it > 0 }?.let { colorValueBorders[it] }
                ?: ContextCompat.getColor(requireContext(), R.color.blue)
        )

        binding.previewProgressbar.secondaryProgressTintList = ColorStateList.valueOf(colorValueProgressbarSecondary[color])
    }


    // ----
    // Data
    // ----

    /** Get current data from UI **/
    fun getCurrentData(): Backup_Colors {
        return Backup_Colors(
            binding.appearanceSpnBackground.selectedItemPosition,
            binding.appearanceSpnBorders.selectedItemPosition,
            binding.appearanceSpnIcons.selectedItemPosition,
            binding.appearanceSpnProgressbar.selectedItemPosition
        )
    }

    /** Load data from Database **/
    private fun loadDataFromDb() {
        viewModel.backupColors.observe(viewLifecycleOwner) { loadDataToUI(it) }
    }

    /** Load data to UI **/
    private fun loadDataToUI(colors: Backup_Colors) {
        binding.appearanceSpnBackground.setSelection(colors.background, false)
        binding.appearanceSpnBorders.setSelection(colors.borders, false)
        binding.appearanceSpnIcons.setSelection(colors.icons, false)
        binding.appearanceSpnProgressbar.setSelection(colors.progressbar, false)
    }

}