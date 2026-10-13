package com.grillpoet.docy.app.markdown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import java.io.File

class MarkdownViewModel(private val filePath: String) : ViewModel() {

    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var document by mutableStateOf<Node?>(null)
        private set
    var fileName by mutableStateOf("")
        private set

    private var loadedLastModified: Long = 0L

    private val parser: Parser = Parser.builder()
        .extensions(
            listOf(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create(),
                TaskListItemsExtension.create()
            )
        )
        .build()

    init { load() }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val file = File(filePath)
                if (!file.exists() || !file.isFile) {
                    error = "文件不存在"
                    loading = false
                    return@launch
                }
                fileName = file.name
                val text = withContext(Dispatchers.IO) { file.readText() }
                document = withContext(Dispatchers.Default) { parser.parse(text) }
                loadedLastModified = file.lastModified()
                loading = false
            } catch (e: Exception) {
                error = e.message ?: "加载失败"
                loading = false
            }
        }
    }

    /** 从编辑器返回时调用。文件改了才重载。 */
    fun reloadIfChanged() {
        val file = File(filePath)
        if (!file.exists()) return
        val m = file.lastModified()
        if (m != loadedLastModified) {
            load()
        }
    }
}
