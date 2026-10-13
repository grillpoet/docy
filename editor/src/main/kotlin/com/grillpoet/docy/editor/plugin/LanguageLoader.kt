package com.grillpoet.docy.editor.plugin

import android.content.Context
import com.grillpoet.docy.core.plugin.LanguagePlugin
import kotlinx.serialization.json.Json
import java.io.File

class LanguageLoader(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    fun loadBuiltIn(): List<LanguagePlugin> =
        context.assets.list("languages")
            ?.filter { it.endsWith(".json") }
            ?.mapNotNull { name ->
                runCatching {
                    context.assets.open("languages/$name").bufferedReader().use {
                        json.decodeFromString<LanguagePlugin>(it.readText())
                    }
                }.getOrNull()
            } ?: emptyList()

    fun loadFrom(dir: File): List<LanguagePlugin> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching {
                    json.decodeFromString<LanguagePlugin>(f.readText())
                }.getOrNull()
            } ?: emptyList()
    }

    fun loadAll(disabled: Set<String> = emptySet()): List<LanguagePlugin> {
        val builtin = loadBuiltIn()
        val external = loadFrom(File("/storage/emulated/0/docy/languages"))
        return (builtin + external).filterNot { it.name in disabled }
    }

    fun allRaw(): List<LanguagePlugin> =
        loadBuiltIn() + loadFrom(File("/storage/emulated/0/docy/languages"))

    fun forExtension(all: List<LanguagePlugin>, ext: String): LanguagePlugin? =
        all.firstOrNull { ext.lowercase() in it.extensions.map { e -> e.lowercase() } }
}
