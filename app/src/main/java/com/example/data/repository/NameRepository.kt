package com.example.data.repository

import android.content.Context
import com.example.util.AppClipboardManager
import com.example.util.GeneratedName
import com.example.util.NameGenerationMode
import com.example.util.NameGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NameRepository(
    private val context: Context,
    private val clipboardManager: AppClipboardManager = AppClipboardManager(context)
) {
    private val _currentName = MutableStateFlow<GeneratedName?>(null)
    val currentName: StateFlow<GeneratedName?> = _currentName.asStateFlow()

    private val _selectedMode = MutableStateFlow(NameGenerationMode.FULL_NAME)
    val selectedMode: StateFlow<NameGenerationMode> = _selectedMode.asStateFlow()

    var autoCopy: Boolean = true

    fun setMode(mode: NameGenerationMode) {
        _selectedMode.value = mode
    }

    /**
     * Generates a realistic name, stores it, and copies to clipboard according to mode.
     */
    fun generateAndCopy(mode: NameGenerationMode = _selectedMode.value): GeneratedName {
        _selectedMode.value = mode
        val generated = NameGenerator.generateName(mode)
        _currentName.value = generated

        if (autoCopy) {
            clipboardManager.copyPlainText("Generated Name", generated.copiedText)
        }

        return generated
    }
}
