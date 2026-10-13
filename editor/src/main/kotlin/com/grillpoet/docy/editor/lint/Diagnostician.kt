package com.grillpoet.docy.editor.lint

import android.content.Context
import com.grillpoet.docy.core.plugin.LanguagePlugin
import com.grillpoet.docy.core.plugin.LibraryPlugin
import com.grillpoet.docy.editor.plugin.LanguageLoader

enum class Severity { ERROR, WARNING, INFO }

data class Diagnostic(
    val line: Int,
    val col: Int,
    val endLine: Int,
    val endCol: Int,
    val severity: Severity,
    val message: String
)

interface SourceView {
    val lines: List<String>
    val path: String?
    fun text(): String
    fun line(n: Int): String
}

interface Diagnostician {
    suspend fun diagnose(source: SourceView): List<Diagnostic>
}

object BuiltinLinters {

    private val registry: MutableMap<String, Diagnostician> = mutableMapOf()
    private val genericCache: MutableMap<String, GenericLinter> = mutableMapOf()

    fun get(name: String): Diagnostician? = registry[name]

    fun register(name: String, impl: Diagnostician) {
        registry[name] = impl
    }

    fun getForLanguage(language: LanguagePlugin?): Diagnostician? {
        if (language == null) return null
        language.linter?.let { name ->
            get(name)?.let { return it }
        }
        return genericCache[language.name]
    }

    fun combined(language: LanguagePlugin?, libraries: List<LibraryPlugin>): Diagnostician? {
        val lang = getForLanguage(language)
        val lib = if (libraries.isEmpty()) null else LibraryLinter(libraries)

        return when {
            lang == null && lib == null -> null
            lang != null && lib == null -> lang
            lang == null && lib != null -> lib
            else -> CombinedLinter(lang!!, lib!!)
        }
    }

    fun init(context: Context) {
        // 语言级 linter。库 API 白名单由 LibraryLinter 处理。
        register("lua", LuaLinter())
        register("json", JsonLinter())

        // 为每个语言建 GenericLinter
        runCatching {
            val loader = LanguageLoader(context)
            loader.loadBuiltIn().forEach { lang ->
                val useful = lang.brackets.isNotEmpty() ||
                        lang.quotes.isNotEmpty() ||
                        lang.blockComment.isNotEmpty() ||
                        lang.blockQuotes.isNotEmpty()
                if (useful) {
                    genericCache[lang.name] = GenericLinter(lang)
                }
            }
        }
    }
}

class CombinedLinter(
    private val first: Diagnostician,
    private val second: Diagnostician
) : Diagnostician {
    override suspend fun diagnose(source: SourceView): List<Diagnostic> {
        val a = runCatching { first.diagnose(source) }.getOrDefault(emptyList())
        val b = runCatching { second.diagnose(source) }.getOrDefault(emptyList())
        return (a + b).sortedWith(compareBy({ it.line }, { it.col }))
    }
}
