package com.grillpoet.docy.app.di

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.docy.fs.ops.ConflictResolution
import com.grillpoet.docy.fs.ops.FileOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Activity 级共享状态：待定区（跨目录可见）+ 刷新信号 + 当前目录路径。
 *
 * currentDirPath 用于让 DocyScaffold 知道「用户当前看的目录」。
 * HOME 路由（主页目录）没有 path 参数，靠这个字段拿到真实路径，
 * 待定区的执行按钮在主页才能启用。
 */
class DocyViewModel : ViewModel() {

    var pending: PendingOp? by mutableStateOf(null)
        private set

    var refreshVersion: Int by mutableIntStateOf(0)
        private set

    /**
     * 当前显示的目录路径。由 FolderScreen 在加载完成后上报。
     * HOME 路由没有 path 参数，DocyScaffold 靠这个拿。
     */
    var currentDirPath: String? by mutableStateOf(null)
        private set

    fun startMove(sources: List<String>) { pending = PendingOp.Move(sources) }
    fun startCopy(sources: List<String>) { pending = PendingOp.Copy(sources) }
    fun cancel() { pending = null }
    fun requestRefresh() { refreshVersion++ }

    fun setCurrentDir(path: String?) {
        currentDirPath = path
    }

    fun canExecute(targetDir: String?): Boolean {
        val op = pending ?: return false
        if (targetDir == null) return false
        return when (op) {
            is PendingOp.Copy -> true
            is PendingOp.Move -> op.sources.none { s ->
                targetDir == s || targetDir.startsWith("$s/")
            }
        }
    }

    suspend fun probeConflicts(targetDir: String): List<File> = withContext(Dispatchers.IO) {
        val op = pending ?: return@withContext emptyList()
        val target = File(targetDir)
        op.sources.map(::File).filter { File(target, it.name).exists() }
    }

    fun execute(
        targetDir: String,
        resolution: ConflictResolution,
        onDone: (Int, Int) -> Unit
    ) {
        val op = pending ?: return
        viewModelScope.launch {
            val (ok, fail) = withContext(Dispatchers.IO) {
                val target = File(targetDir)
                var ok = 0
                var fail = 0
                op.sources.map(::File).forEach { src ->
                    val isMove = op is PendingOp.Move
                    val existed = File(target, src.name).exists()
                    val result = FileOps.applyAndTransfer(src, target, resolution, isMove)
                    when {
                        result != null -> ok++
                        existed && resolution == ConflictResolution.SKIP -> { /* skipped */ }
                        else -> fail++
                    }
                }
                ok to fail
            }
            pending = null
            refreshVersion++
            onDone(ok, fail)
        }
    }
}

sealed class PendingOp {
    abstract val sources: List<String>
    data class Move(override val sources: List<String>) : PendingOp()
    data class Copy(override val sources: List<String>) : PendingOp()
}
