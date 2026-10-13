package com.grillpoet.docy.app.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.docy.app.di.DocyPreferences
import com.grillpoet.docy.editor.plugin.LanguageLoader
import com.grillpoet.docy.editor.plugin.LibraryLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    appContext: Context,
    private val prefs: DocyPreferences
) : ViewModel() {

    data class LanguageRow(val name: String, val extensions: List<String>, val enabled: Boolean)
    data class LibraryRow(
        val name: String,
        val language: String,
        val enabled: Boolean,
        val forced: Boolean
    )

    var languages by mutableStateOf<List<LanguageRow>>(emptyList())
        private set
    var libraries by mutableStateOf<List<LibraryRow>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var fontSize by mutableFloatStateOf(14f)
        private set
    var showHidden by mutableStateOf(false)
        private set
    var colorfulSyntax by mutableStateOf(true)
        private set

    private val langLoader = LanguageLoader(appContext)
    private val libLoader = LibraryLoader(appContext)

    init {
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            loading = true
            val allLangs = withContext(Dispatchers.IO) { langLoader.allRaw() }
            val allLibs = withContext(Dispatchers.IO) { libLoader.allRaw() }
            val disabledLangs = prefs.currentDisabledLanguages()
            val disabledLibs = prefs.currentDisabledLibraries()
            val forced = prefs.currentForceEnabledLibraries()

            languages = allLangs.map {
                LanguageRow(it.name, it.extensions, it.name !in disabledLangs)
            }
            libraries = allLibs.map {
                LibraryRow(
                    name = it.name,
                    language = it.language,
                    enabled = it.name !in disabledLibs,
                    forced = it.name in forced
                )
            }
            fontSize = prefs.fontSize.first()
            showHidden = prefs.currentShowHidden()
            colorfulSyntax = prefs.currentColorfulSyntax()
            loading = false
        }
    }

    fun setLanguageEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch {
            prefs.toggleLanguage(name, enabled)
            languages = languages.map {
                if (it.name == name) it.copy(enabled = enabled) else it
            }
        }
    }

    fun setLibraryEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch {
            prefs.toggleLibrary(name, enabled)
            libraries = libraries.map {
                if (it.name == name) it.copy(enabled = enabled) else it
            }
        }
    }

    fun setLibraryForce(name: String, force: Boolean) {
        viewModelScope.launch {
            prefs.toggleLibraryForce(name, force)
            libraries = libraries.map {
                if (it.name == name) it.copy(forced = force) else it
            }
        }
    }

    fun updateFontSize(f: Float) {
        val v = f.coerceIn(10f, 28f)
        fontSize = v
        viewModelScope.launch { prefs.setFontSize(v) }
    }

    fun updateShowHidden(b: Boolean) {
        showHidden = b
        viewModelScope.launch { prefs.setShowHidden(b) }
    }

    fun updateColorfulSyntax(b: Boolean) {
        colorfulSyntax = b
        viewModelScope.launch { prefs.setColorfulSyntax(b) }
    }
}
