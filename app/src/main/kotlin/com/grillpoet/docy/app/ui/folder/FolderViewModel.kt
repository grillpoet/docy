package com.grillpoet.docy.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.docy.app.di.DocyPreferences
import com.grillpoet.docy.core.archive.ArchiveFormat
import com.grillpoet.docy.core.model.FileItem
import com.grillpoet.docy.core.sort.FileSorter
import com.grillpoet.docy.core.sort.SortMode
import com.grillpoet.docy.core.sort.SortOrder
import com.grillpoet.docy.core.sort.SortSpec
import com.grillpoet.docy.core.sort.ViewMode
import com.grillpoet.docy.fs.access.StorageAccess
import com.grillpoet.docy.fs.archive.ArchiveManager
import com.grillpoet.docy.fs.batch.BatchRename
import com.grillpoet.docy.fs.ops.FileOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class FolderViewModel(
    private val path: String?,
    private val prefs: DocyPreferences
) : ViewModel() {

    data class Crumb(val name: String, val path: String)

    data class SearchResult(
        val item: FileItem,
        val relativePath: String
    )

    data class UiState(
        val path: String? = null,
        val items: List<FileItem> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val sort: SortSpec = SortSpec(),
        val view: ViewMode = ViewMode.LIST,
        val gridColumns: Int = 3,
        val breadcrumb: List<Crumb> = emptyList(),
        val selection: Set<String> = emptySet(),
        val selectionMode: Boolean = false,
        val busy: String? = null,
        val busyProgress: Float? = null,
        val busyCurrent: Int = 0,
        val busyTotal: Int = 0,
        val showHidden: Boolean = false,
        // 搜索
        val searchVisible: Boolean = false,
        val searchQuery: String = "",
        val searching: Boolean = false,
        val searchResults: List<SearchResult> = emptyList(),
        val searchTruncated: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val archiveManager = ArchiveManager()
    private var searchJob: Job? = null

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            val mode = prefs.currentSortMode()
            val order = prefs.currentSortOrder()
            val foldersFirst = prefs.currentFoldersFirst()
            val view = prefs.currentViewMode()
            val cols = prefs.currentGridColumns()
            val showHidden = prefs.currentShowHidden()
            val sortSpec = SortSpec(mode, order, foldersFirst)

            val result = withContext(Dispatchers.IO) {
                val dir = resolveDir()
                if (dir == null || !dir.isDirectory) return@withContext null
                val raw = FileOps.list(dir)
                val items = if (showHidden) raw else raw.filterNot { it.name.startsWith(".") }
                val crumbs = buildBreadcrumb(dir)
                Triple(dir, items, crumbs)
            }

            if (result == null) {
                _state.update { it.copy(loading = false, error = "目录不存在或不可访问") }
                return@launch
            }

            val (dir, items, crumbs) = result
            _state.update { s ->
                s.copy(
                    path = dir.absolutePath,
                    items = FileSorter.sort(items, sortSpec),
                    breadcrumb = crumbs,
                    loading = false,
                    sort = sortSpec,
                    view = view,
                    gridColumns = cols,
                    showHidden = showHidden,
                    selection = emptySet(),
                    selectionMode = false
                )
            }
        }
    }

    // ---------- 搜索 ----------

    fun enterSearch() {
        _state.update {
            it.copy(
                searchVisible = true,
                searchQuery = "",
                searchResults = emptyList(),
                searchTruncated = false,
                selection = emptySet(),
                selectionMode = false
            )
        }
    }

    fun exitSearch() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                searchVisible = false,
                searchQuery = "",
                searchResults = emptyList(),
                searchTruncated = false,
                searching = false
            )
        }
    }

    fun updateSearchQuery(q: String) {
        _state.update { it.copy(searchQuery = q) }

        searchJob?.cancel()
        if (q.isBlank()) {
            _state.update {
                it.copy(searching = false, searchResults = emptyList(), searchTruncated = false)
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(250.milliseconds)
            _state.update { it.copy(searching = true) }
            val rootDir = _state.value.path?.let(::File) ?: return@launch
            val showHidden = _state.value.showHidden
            val result = withContext(Dispatchers.IO) {
                recursiveSearch(rootDir, q.trim(), showHidden)
            }
            _state.update {
                it.copy(
                    searching = false,
                    searchResults = result.first,
                    searchTruncated = result.second
                )
            }
        }
    }

    private fun recursiveSearch(
        root: File,
        query: String,
        showHidden: Boolean
    ): Pair<List<SearchResult>, Boolean> {
        val results = mutableListOf<SearchResult>()
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(root to 0)
        val lowerQuery = query.lowercase()
        var truncated = false

        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            if (depth > MAX_SEARCH_DEPTH) continue

            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (!showHidden && child.name.startsWith(".")) continue

                if (child.name.lowercase().contains(lowerQuery)) {
                    val item = FileItem(
                        name = child.name,
                        path = child.absolutePath,
                        isDirectory = child.isDirectory,
                        size = if (child.isFile) child.length() else 0L,
                        lastModified = child.lastModified()
                    )
                    val rel = child.absolutePath.removePrefix(root.absolutePath).trimStart('/')
                    results.add(SearchResult(item, rel))
                    if (results.size >= MAX_SEARCH_RESULTS) {
                        truncated = true
                        return results to truncated
                    }
                }

                if (child.isDirectory) {
                    queue.add(child to (depth + 1))
                }
            }
        }
        return results to truncated
    }

    // ---------- 视图 ----------

    fun setViewMode(mode: ViewMode) {
        _state.update { it.copy(view = mode) }
        viewModelScope.launch { prefs.setViewMode(mode) }
    }

    fun setGridColumns(n: Int) {
        val v = n.coerceIn(2, 5)
        _state.update { it.copy(gridColumns = v) }
        viewModelScope.launch { prefs.setGridColumns(v) }
    }

    fun setSortMode(mode: SortMode) {
        _state.update { s ->
            val next = s.sort.copy(mode = mode)
            s.copy(sort = next, items = FileSorter.sort(s.items, next))
        }
        viewModelScope.launch { prefs.setSortMode(mode) }
    }

    fun setSortOrder(order: SortOrder) {
        _state.update { s ->
            val next = s.sort.copy(order = order)
            s.copy(sort = next, items = FileSorter.sort(s.items, next))
        }
        viewModelScope.launch { prefs.setSortOrder(order) }
    }

    fun setFoldersFirst(b: Boolean) {
        _state.update { s ->
            val next = s.sort.copy(foldersFirst = b)
            s.copy(sort = next, items = FileSorter.sort(s.items, next))
        }
        viewModelScope.launch { prefs.setFoldersFirst(b) }
    }

    fun setShowHidden(b: Boolean) {
        _state.update { it.copy(showHidden = b) }
        viewModelScope.launch {
            prefs.setShowHidden(b)
            load()
        }
    }

    // ---------- 选择 ----------

    fun enterSelection(path: String) = _state.update {
        it.copy(selection = setOf(path), selectionMode = true)
    }

    fun toggleSelection(path: String) = _state.update {
        val sel = if (path in it.selection) it.selection - path else it.selection + path
        it.copy(selection = sel, selectionMode = sel.isNotEmpty())
    }

    fun exitSelection() = _state.update {
        it.copy(selection = emptySet(), selectionMode = false)
    }

    fun selectAll() = _state.update { s ->
        val all = if (s.searchVisible) {
            s.searchResults.map { it.item.path }.toSet()
        } else {
            s.items.map { it.path }.toSet()
        }
        s.copy(selection = all)
    }

    fun selectRange(from: Int, to: Int) = _state.update { s ->
        val list = if (s.searchVisible) s.searchResults.map { it.item } else s.items
        if (list.isEmpty()) return@update s
        val range = from.coerceIn(0, list.lastIndex)..to.coerceIn(0, list.lastIndex)
        val paths = range.mapNotNull { list.getOrNull(it)?.path }.toSet()
        s.copy(selection = s.selection + paths, selectionMode = true)
    }

    // ---------- 操作 ----------

    fun createFolder(name: String, onResult: (String) -> Unit) = runOp {
        val parent = File(_state.value.path ?: return@runOp "无当前目录")
        val created = FileOps.mkdir(parent, name)
        if (created != null) "已创建 ${created.name}" else "创建失败：同名已存在"
    } then { onResult(it) }

    fun createFile(name: String, onResult: (String) -> Unit) = runOp {
        val parent = File(_state.value.path ?: return@runOp "无当前目录")
        val created = FileOps.newFile(parent, name)
        if (created != null) "已创建 ${created.name}" else "创建失败：同名已存在"
    } then { onResult(it) }

    fun rename(path: String, newName: String, onResult: (String) -> Unit) = runOp {
        val src = File(path)
        val dst = FileOps.rename(src, newName)
        if (dst != null) "已重命名为 ${dst.name}" else "重命名失败"
    } then { onResult(it) }

    fun batchRename(plans: Map<String, String>, onResult: (String) -> Unit) = runOp {
        val list = plans.map { (p, n) -> File(p) to File(File(p).parentFile, n) }
        val (ok, errs) = BatchRename.apply(list)
        if (errs.isEmpty()) "已重命名 $ok 项"
        else "重命名 $ok 项，失败 ${errs.size} 项"
    } then { onResult(it) }

    fun delete(paths: List<String>, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                val (ok, failed) = FileOps.delete(paths.map(::File))
                when {
                    failed.isEmpty() -> "已删除 $ok 项"
                    ok == 0 -> "删除失败"
                    else -> "已删除 $ok 项，失败 ${failed.size} 项"
                }
            }
            exitSelection()
            reloadAfterSearch()
            onResult(msg)
        }
    }

    fun compress(
        sources: List<String>,
        destName: String,
        format: ArchiveFormat,
        onResult: (String) -> Unit
    ) {
        val dir = _state.value.path ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = "正在压缩…",
                    busyProgress = null,
                    busyCurrent = 0,
                    busyTotal = 0
                )
            }
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = File(dir, destName)
                    if (dest.exists()) return@runCatching "目标文件已存在：$destName"
                    archiveManager.compress(sources.map(::File), dest, format) { cur, tot ->
                        _state.update {
                            it.copy(
                                busyCurrent = cur,
                                busyTotal = tot,
                                busyProgress = if (tot > 0) cur.toFloat() / tot else null
                            )
                        }
                    }
                    "已压缩为 $destName"
                }.getOrElse { "压缩失败：${it.message}" }
            }
            _state.update {
                it.copy(busy = null, busyProgress = null, busyCurrent = 0, busyTotal = 0)
            }
            exitSelection()
            reloadAfterSearch()
            onResult(msg)
        }
    }

    fun extract(
        archivePath: String,
        destDirPath: String,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = "正在解压…",
                    busyProgress = null,
                    busyCurrent = 0,
                    busyTotal = 0
                )
            }
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = File(destDirPath)
                    dest.mkdirs()
                    archiveManager.extract(File(archivePath), dest) { cur, tot ->
                        _state.update {
                            it.copy(
                                busyCurrent = cur,
                                busyTotal = tot,
                                busyProgress = if (tot > 0) cur.toFloat() / tot else null
                            )
                        }
                    }
                    "已解压到 ${dest.name}"
                }.getOrElse { "解压失败：${it.message}" }
            }
            _state.update {
                it.copy(busy = null, busyProgress = null, busyCurrent = 0, busyTotal = 0)
            }
            exitSelection()
            reloadAfterSearch()
            onResult(msg)
        }
    }

    private fun reloadAfterSearch() {
        if (_state.value.searchVisible) {
            updateSearchQuery(_state.value.searchQuery)
        } else {
            load()
        }
    }

    private fun runOp(block: () -> String): OpBuilder = OpBuilder(block)

    inner class OpBuilder(private val block: () -> String) {
        infix fun then(cb: (String) -> Unit) {
            viewModelScope.launch {
                val msg = withContext(Dispatchers.IO) { block() }
                reloadAfterSearch()
                cb(msg)
            }
        }
    }

    // ---------- 内部 ----------

    private fun resolveDir(): File? {
        if (path != null) return File(path)
        return StorageAccess.primaryRoots().firstOrNull()?.let(::File)
    }

    private fun buildBreadcrumb(dir: File): List<Crumb> {
        val out = mutableListOf<Crumb>()
        var cur: File? = dir
        while (cur != null) {
            out.add(0, Crumb(displayName(cur), cur.absolutePath))
            if (isKnownRoot(cur.absolutePath)) break
            cur = cur.parentFile
        }
        return out
    }

    private fun isKnownRoot(absPath: String): Boolean =
        absPath == "/storage/emulated/0" ||
                absPath == "/sdcard" ||
                StorageAccess.primaryRoots().contains(absPath) ||
                (absPath.startsWith("/storage/") &&
                        absPath.count { it == '/' } == 2 &&
                        absPath != "/storage/emulated")

    private fun displayName(f: File): String = when (f.absolutePath) {
        "/storage/emulated/0" -> "内部存储"
        "/" -> "根目录"
        else -> f.name.ifEmpty { f.absolutePath }
    }

    companion object {
        const val MAX_SEARCH_DEPTH = 6
        const val MAX_SEARCH_RESULTS = 500
    }
}
