package com.grillpoet.docy.core.plugin

import kotlinx.serialization.Serializable

@Serializable
data class LanguagePlugin(
    val name: String,
    val extensions: List<String> = emptyList(),
    val indent: Int = 4,
    val operators: List<String> = emptyList(),
    val punctuation: String = "",
    val brackets: List<List<String>> = emptyList(),
    val lineComment: String? = null,
    val blockComment: List<String> = emptyList(),
    val quotes: List<String> = emptyList(),
    val blockQuotes: List<List<String>> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val completions: List<Completion> = emptyList(),
    val linter: String? = null,
    val run: RunSpec? = null
)

@Serializable
data class LibraryPlugin(
    val name: String,
    val language: String,
    val autoAttach: Boolean = false,
    val detectBy: List<String> = emptyList(),
    val api: List<String> = emptyList(),
    val completions: List<Completion> = emptyList(),
    val run: RunSpec? = null
)

@Serializable
data class Rule(
    val kind: String,
    val type: String? = null,
    val start: String? = null,
    val end: String? = null,
    val quotes: List<String> = emptyList(),
    val words: List<String> = emptyList(),
    val regex: String? = null
)

@Serializable
data class Completion(
    val label: String,
    val insert: String = label,
    val detail: String = ""
)

@Serializable
data class RunSpec(
    val type: String,
    val action: String = "",
    val enabledBy: String? = null
)
