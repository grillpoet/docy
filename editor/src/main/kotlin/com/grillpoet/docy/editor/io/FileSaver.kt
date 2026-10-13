package com.grillpoet.docy.editor.io

import com.grillpoet.docy.editor.state.EditorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 原子写：先写 .tmp，再 rename。
 *
 * ## 依赖方向
 *
 * 本类只**读** [EditorState]，不修改它。保存完成后由 ViewModel 决定
 * 何时调 [EditorState.markSaved]。避免 Saver 反向修改 State。
 */
class FileSaver {

    suspend fun save(state: EditorState): SaveOutcome = withContext(Dispatchers.IO) {
        val file = state.file ?: return@withContext SaveOutcome.NoFile

        // 外部修改检测：文件在本会话打开后被动过，且用户有未保存修改。
        if (file.lastModified() != state.originalLastModified && state.dirty) {
            return@withContext SaveOutcome.ExternallyModified
        }

        val tmp = File(file.parentFile, ".${file.name}.tmp")
        try {
            val text = state.serialize(state.eol.value)
            tmp.outputStream().bufferedWriter(state.charset).use { it.write(text) }

            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }

            SaveOutcome.Ok(file.lastModified())
        } catch (e: IOException) {
            tmp.delete()
            SaveOutcome.Error(e.message ?: "写入失败")
        }
    }
}

/**
 * 保存结果。
 *
 * [Ok] 携带新的 lastModified，由调用方写回 [EditorState]。
 */
sealed interface SaveOutcome {
    data class Ok(val lastModified: Long) : SaveOutcome
    data object NoFile : SaveOutcome
    data object ExternallyModified : SaveOutcome
    data class Error(val msg: String) : SaveOutcome
}
