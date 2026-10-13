package com.grillpoet.docy.app.ui.archive

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.docy.core.archive.ArchiveEntry
import com.grillpoet.docy.fs.archive.ArchiveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ArchiveViewModel(
    private val archivePath: String,
    private val appContext: Context
) : ViewModel() {

    var entries by mutableStateOf<List<ArchiveEntry>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    private val manager = ArchiveManager()

    init { load() }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    manager.list(File(archivePath))
                        .sortedWith(compareBy({ !it.isDirectory }, { it.path }))
                }
            }
            result.onSuccess {
                entries = it
                loading = false
            }.onFailure {
                error = it.message ?: "无法读取压缩包"
                loading = false
            }
        }
    }

    fun extractTo(destDir: File, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    destDir.mkdirs()
                    manager.extract(File(archivePath), destDir)
                    "已解压到 ${destDir.name}"
                }.getOrElse { "解压失败：${it.message}" }
            }
            message = msg
            onDone(msg)
        }
    }


    fun clearMessage() { message = null }
}