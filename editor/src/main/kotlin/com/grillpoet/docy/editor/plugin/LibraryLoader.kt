package com.grillpoet.docy.editor.plugin

import android.content.Context
import com.grillpoet.docy.core.plugin.LibraryPlugin
import kotlinx.serialization.json.Json
import java.io.File

class LibraryLoader(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    fun loadBuiltIn(): List<LibraryPlugin> =
        context.assets.list("libraries")
            ?.filter { it.endsWith(".json") }
            ?.mapNotNull { name ->
                runCatching {
                    context.assets.open("libraries/$name").bufferedReader().use {
                        json.decodeFromString<LibraryPlugin>(it.readText())
                    }
                }.getOrNull()
            } ?: emptyList()

    fun loadFrom(dir: File): List<LibraryPlugin> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching {
                    json.decodeFromString<LibraryPlugin>(f.readText())
                }.getOrNull()
            } ?: emptyList()
    }

    fun loadAll(disabled: Set<String> = emptySet()): List<LibraryPlugin> {
        val builtin = loadBuiltIn()
        val external = loadFrom(File("/storage/emulated/0/docy/libraries"))
        return (builtin + external).filterNot { it.name in disabled }
    }

    fun allRaw(): List<LibraryPlugin> =
        loadBuiltIn() + loadFrom(File("/storage/emulated/0/docy/libraries"))

    /**
     * 组合活跃库：
     *   - autoAttach=true 且 language 匹配
     *   - detectBy 命中当前目录
     *   - 手动启用的（forceEnabled 里）
     */
    fun resolveActive(
        all: List<LibraryPlugin>,
        languageName: String,
        currentDirFiles: Set<String>,
        forceEnabled: Set<String> = emptySet()
    ): List<LibraryPlugin> {
        return all.filter { lib ->
            lib.language.equals(languageName, ignoreCase = true) &&
                    (lib.autoAttach ||
                            lib.name in forceEnabled ||
                            lib.detectBy.any { it in currentDirFiles })
        }
    }
}
