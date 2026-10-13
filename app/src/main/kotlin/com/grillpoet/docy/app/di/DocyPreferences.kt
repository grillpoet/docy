package com.grillpoet.docy.app.di

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grillpoet.docy.core.sort.SortMode
import com.grillpoet.docy.core.sort.SortOrder
import com.grillpoet.docy.core.sort.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("docy")

class DocyPreferences(private val ctx: Context) {

    private object Keys {
        val sortMode = stringPreferencesKey("sort.mode")
        val sortOrder = stringPreferencesKey("sort.order")
        val foldersFirst = booleanPreferencesKey("sort.foldersFirst")
        val viewMode = stringPreferencesKey("view.mode")
        val gridColumns = intPreferencesKey("view.gridColumns")
        val fontSize = floatPreferencesKey("editor.fontSize")
        val colorfulSyntax = booleanPreferencesKey("editor.colorfulSyntax")
        val disabledLanguages = stringSetPreferencesKey("editor.languages.disabled")
        val disabledLibraries = stringSetPreferencesKey("editor.libraries.disabled")
        val forceEnabledLibraries = stringSetPreferencesKey("editor.libraries.forceEnabled")
        val showHidden = booleanPreferencesKey("browser.showHidden")
    }

    val sortMode: Flow<SortMode> = ctx.dataStore.data.map {
        runCatching { SortMode.valueOf(it[Keys.sortMode] ?: "NAME") }.getOrDefault(SortMode.NAME)
    }
    val sortOrder: Flow<SortOrder> = ctx.dataStore.data.map {
        runCatching { SortOrder.valueOf(it[Keys.sortOrder] ?: "ASC") }.getOrDefault(SortOrder.ASC)
    }
    val foldersFirst: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.foldersFirst] ?: true }
    val viewMode: Flow<ViewMode> = ctx.dataStore.data.map {
        runCatching { ViewMode.valueOf(it[Keys.viewMode] ?: "LIST") }.getOrDefault(ViewMode.LIST)
    }
    val gridColumns: Flow<Int> = ctx.dataStore.data.map { it[Keys.gridColumns] ?: 3 }
    val fontSize: Flow<Float> = ctx.dataStore.data.map { it[Keys.fontSize] ?: 14f }
    val colorfulSyntax: Flow<Boolean> = ctx.dataStore.data.map {
        it[Keys.colorfulSyntax] ?: true
    }
    val disabledLanguages: Flow<Set<String>> = ctx.dataStore.data.map {
        it[Keys.disabledLanguages] ?: emptySet()
    }
    val disabledLibraries: Flow<Set<String>> = ctx.dataStore.data.map {
        it[Keys.disabledLibraries] ?: emptySet()
    }
    val forceEnabledLibraries: Flow<Set<String>> = ctx.dataStore.data.map {
        it[Keys.forceEnabledLibraries] ?: emptySet()
    }
    val showHidden: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.showHidden] ?: false }

    suspend fun currentSortMode(): SortMode = sortMode.first()
    suspend fun currentSortOrder(): SortOrder = sortOrder.first()
    suspend fun currentFoldersFirst(): Boolean = foldersFirst.first()
    suspend fun currentViewMode(): ViewMode = viewMode.first()
    suspend fun currentGridColumns(): Int = gridColumns.first()
    suspend fun currentColorfulSyntax(): Boolean = colorfulSyntax.first()
    suspend fun currentDisabledLanguages(): Set<String> = disabledLanguages.first()
    suspend fun currentDisabledLibraries(): Set<String> = disabledLibraries.first()
    suspend fun currentForceEnabledLibraries(): Set<String> = forceEnabledLibraries.first()
    suspend fun currentShowHidden(): Boolean = showHidden.first()

    suspend fun setSortMode(m: SortMode) { ctx.dataStore.edit { it[Keys.sortMode] = m.name } }
    suspend fun setSortOrder(o: SortOrder) { ctx.dataStore.edit { it[Keys.sortOrder] = o.name } }
    suspend fun setFoldersFirst(b: Boolean) { ctx.dataStore.edit { it[Keys.foldersFirst] = b } }
    suspend fun setViewMode(m: ViewMode) { ctx.dataStore.edit { it[Keys.viewMode] = m.name } }
    suspend fun setGridColumns(n: Int) { ctx.dataStore.edit { it[Keys.gridColumns] = n } }
    suspend fun setFontSize(f: Float) { ctx.dataStore.edit { it[Keys.fontSize] = f } }
    suspend fun setColorfulSyntax(b: Boolean) {
        ctx.dataStore.edit { it[Keys.colorfulSyntax] = b }
    }
    suspend fun setShowHidden(b: Boolean) { ctx.dataStore.edit { it[Keys.showHidden] = b } }

    suspend fun setDisabledLanguages(set: Set<String>) {
        ctx.dataStore.edit { it[Keys.disabledLanguages] = set }
    }

    suspend fun toggleLanguage(name: String, enabled: Boolean) {
        ctx.dataStore.edit { prefs ->
            val cur = prefs[Keys.disabledLanguages] ?: emptySet()
            prefs[Keys.disabledLanguages] = if (enabled) cur - name else cur + name
        }
    }

    suspend fun toggleLibrary(name: String, enabled: Boolean) {
        ctx.dataStore.edit { prefs ->
            val cur = prefs[Keys.disabledLibraries] ?: emptySet()
            prefs[Keys.disabledLibraries] = if (enabled) cur - name else cur + name
        }
    }

    suspend fun toggleLibraryForce(name: String, force: Boolean) {
        ctx.dataStore.edit { prefs ->
            val cur = prefs[Keys.forceEnabledLibraries] ?: emptySet()
            prefs[Keys.forceEnabledLibraries] = if (force) cur + name else cur - name
        }
    }
}
