package com.grillpoet.docy.editor.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.docy.core.plugin.Completion
import com.grillpoet.docy.core.plugin.RunSpec
import com.grillpoet.docy.editor.complete.CompletionEngine
import com.grillpoet.docy.editor.io.CharsetDetector
import com.grillpoet.docy.editor.io.FileSaver
import com.grillpoet.docy.editor.io.SaveOutcome
import com.grillpoet.docy.editor.lint.BuiltinLinters
import com.grillpoet.docy.editor.lint.Diagnostic
import com.grillpoet.docy.editor.lint.Severity
import com.grillpoet.docy.editor.lint.SourceView
import com.grillpoet.docy.editor.love.PackLoveRunner
import com.grillpoet.docy.editor.plugin.ActiveContext
import com.grillpoet.docy.editor.plugin.LanguageLoader
import com.grillpoet.docy.editor.plugin.LibraryLoader
import com.grillpoet.docy.editor.plugin.RunContext
import com.grillpoet.docy.editor.plugin.RunDispatcher
import com.grillpoet.docy.editor.plugin.RunEnablers
import com.grillpoet.docy.editor.search.Finder
import com.grillpoet.docy.editor.state.EditorState
import com.grillpoet.docy.editor.state.EolStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * 编辑器 ViewModel。
 *
 * ## 与旧架构的差异
 *
 * 旧版基于「一行一个 BasicTextField」，每个 `updateLineText` 都是
 * 行级操作。
 *
 * 新版基于「单实例 BasicTextField」，所有文本操作都是
 * [TextFieldValue] 级别。UI 层不再负责行拆分、合并、光标定位，
 * 这些都交给 `BasicTextField` 内部处理。
 *
 * ViewModel 的职责缩小为：
 *  - 文件加载 / 保存 / 编码检测
 *  - undo / redo 历史
 *  - 查找替换
 *  - 补全调度
 *  - 诊断 / 语法高亮的防抖调度
 *  - 运行按钮的插件调度
 */
class EditorViewModel(
    private val filePath: String,
    private val appContext: Context,
    private val disabledLanguages: Set<String> = emptySet(),
    private val disabledLibraries: Set<String> = emptySet(),
    private val forceEnabledLibraries: Set<String> = emptySet()
) : ViewModel() {

    var editorState by mutableStateOf(EditorState())
        private set

    var activeContext by mutableStateOf(ActiveContext.EMPTY)
        private set

    var loading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var runSpec: RunSpec? by mutableStateOf(null)
        private set
    var runEnabled: Boolean by mutableStateOf(false)
        private set
    var runDisabledReason: String? by mutableStateOf(null)
        private set
    var running: Boolean by mutableStateOf(false)
        private set

    var diagnostics by mutableStateOf<List<Diagnostic>>(emptyList())
        private set
    var diagnosticsExpanded by mutableStateOf(false)
        private set

    // ---------- 查找替换 ----------

    var findVisible by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    var replaceText by mutableStateOf("")
        private set
    var caseSensitive by mutableStateOf(false)
        private set
    var matches by mutableStateOf<List<Finder.Match>>(emptyList())
        private set
    var currentMatchIndex by mutableIntStateOf(0)
        private set

    // ---------- 补全 ----------

    var completionVisible by mutableStateOf(false)
        private set
    var completionCandidates by mutableStateOf<List<Completion>>(emptyList())
        private set
    var completionIndex by mutableIntStateOf(0)
        private set
    private var completionPrefixStart = -1
    private var completionPrefixEnd = -1

    // ---------- 历史 ----------

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private val maxHistory = 50
    private val historyBudget = 4L * 1024L * 1024L
    private var historySize = 0L

    private var lastEditTimeMs = 0L
    private var typingSessionChars = 0
    private val typingMergeMs = 500L
    private val typingMergeChars = 20

    /**
     * 光标垂直移动时保留的列偏好。详见 [moveCursorVertical]。
     * -1 表示未设置；水平移动或文本编辑会重置为 -1。
     */
    private var preferredColumn = -1

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    // ---------- 依赖 ----------

    private val langLoader = LanguageLoader(appContext)
    private val libLoader = LibraryLoader(appContext)
    private val dispatcher = RunDispatcher(appContext)
    private var diagnoseJob: Job? = null
    private var completionJob: Job? = null
    private var matchJob: Job? = null

    init {
        dispatcher.register("packLove", PackLoveRunner(appContext))
        load()
        loadPlugins()
    }

    // ==================== 插件加载 ====================

    private fun loadPlugins() {
        viewModelScope.launch {
            val allLangs = withContext(Dispatchers.IO) { langLoader.loadAll(disabledLanguages) }
            val allLibs = withContext(Dispatchers.IO) { libLoader.loadAll(disabledLibraries) }
            val ext = File(filePath).extension.lowercase()
            val lang = langLoader.forExtension(allLangs, ext)
            val dirFiles = withContext(Dispatchers.IO) {
                collectNamesUpward(File(filePath).parentFile, maxDepth = 6, maxTotal = 5000)
            }
            val activeLibs = lang?.let {
                libLoader.resolveActive(
                    all = allLibs,
                    languageName = it.name,
                    currentDirFiles = dirFiles,
                    forceEnabled = forceEnabledLibraries
                )
            } ?: emptyList()

            val ctx = ActiveContext(lang, activeLibs)
            activeContext = ctx
            runSpec = ctx.runSpec
            refreshRunState()
            scheduleDiagnose(immediate = true)
        }
    }

    // ==================== 运行 ====================

    fun refreshRunState() {
        val spec = runSpec ?: run {
            runEnabled = false
            runDisabledReason = null
            return
        }
        val ctx = buildRunContext() ?: run {
            runEnabled = false
            runDisabledReason = "无文件上下文"
            return
        }
        runEnabled = RunEnablers.check(spec.enabledBy, ctx)
        runDisabledReason = when {
            runEnabled -> null
            spec.enabledBy == "loveProject" -> "当前目录没有 main.lua"
            else -> "当前不满足运行条件"
        }
    }

    fun run() {
        val spec = runSpec ?: return
        if (!runEnabled || running) return
        viewModelScope.launch {
            running = true
            val ctx = buildRunContext()
            if (ctx == null) {
                running = false
                message = "无法构造运行上下文"
                return@launch
            }
            val result = dispatcher.dispatch(spec, ctx)
            running = false
            result.onSuccess { message = it }
                .onFailure { e -> message = e.message ?: "运行失败" }
            refreshRunState()
        }
    }

    private fun buildRunContext(): RunContext? {
        val file = File(filePath)
        val dir = file.parentFile ?: return null
        val allFiles = dir.listFiles()?.toList() ?: emptyList()
        return object : RunContext {
            override val file: File = file
            override val dir: File = dir
            override val allFilesInDir: List<File> = allFiles
        }
    }

    // ==================== 加载 ====================

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
                if (file.length() > MAX_SIZE_BYTES) {
                    error = "文件过大（${file.length() / 1024}KB），暂不支持编辑"
                    loading = false
                    return@launch
                }
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                val (charset, _) = CharsetDetector.detect(bytes)
                val text = String(bytes, charset)
                val eol = EolStyle.detect(text)

                val st = EditorState().apply {
                    this.file = file
                    this.charset = charset
                    this.eol = eol
                    // 加载不算用户修改。虽然 markSaved 会清 dirty，
                    // 但显式传 false 让意图清楚。
                    setFullText(text, eol.value, markDirty = false)
                    markSaved(file.lastModified())
                }
                editorState = st
                loading = false
                undoStack.clear()
                redoStack.clear()
                historySize = 0
                lastEditTimeMs = 0
                refreshHistoryFlags()
                refreshRunState()
                scheduleDiagnose(immediate = true)
                    refreshMatches()
            } catch (e: Exception) {
                error = e.message ?: "加载失败"
                loading = false
            }
        }
    }

    // ==================== 编辑 ====================

    /**
     * UI 层的 `onValueChange` 转发入口。
     *
     * 所有文本编辑都经过这里：输入、删除、粘贴、选择变化。
     */
    fun updateValue(newValue: TextFieldValue) {
        val old = editorState.value
        // 任何输入/选择变化都让列偏好失效
        preferredColumn = -1
        // 只有文本内容变化时才推入 undo，选择变化不推
        if (old.text != newValue.text) {
            pushUndoForTyping()
            editorState.updateValue(newValue)
            scheduleDiagnose(immediate = false)
            if (findVisible) scheduleRefreshMatches()
            scheduleCompletion(newValue)
        } else {
            // 纯选择变化。只更新 value，不触发热重。
            editorState.updateValue(newValue)
        }
    }

    /** 键盘辅助行：插入文本。插入改变光标位置，清除列偏好。 */
    fun insertAtCursor(text: String) {
        pushUndoNow()
        editorState.insertAtCursor(text)
        preferredColumn = -1
        scheduleDiagnose(immediate = false)
        dismissCompletion()
    }

    /** 键盘辅助行：左右移动光标。水平移动会清除列偏好。 */
    fun moveCursorHorizontal(dx: Int) {
        val cur = editorState.value.selection.end
        val target = (cur + dx).coerceIn(0, editorState.value.text.length)
        editorState.moveCursorTo(target)
        preferredColumn = -1
    }

    /**
     * 键盘辅助行：上下移动行。
     *
     * 保留列偏好：从长行下移到短行时，列会被 clamp 到短行行尾；
     * 再下移回长行时恢复到原始列，而不是停在短行的行尾。
     * 水平移动或文本编辑会清除偏好（见 [moveCursorHorizontal] / [updateValue]）。
     */
    fun moveCursorVertical(dy: Int) {
        val text = editorState.value.text
        val cur = editorState.value.selection.end
        val line = EditorState.lineAtOffset(text, cur)
        // 首次进入垂直移动序列时以当前列为准
        if (preferredColumn < 0) {
            preferredColumn = EditorState.colAtOffset(text, cur)
        }
        val targetLine = (line + dy).coerceIn(0, editorState.lineCount - 1)
        val lineStart = EditorState.offsetAtLineStart(text, targetLine)
        val lineEnd = EditorState.offsetAtLineEnd(text, targetLine)
        val target = (lineStart + preferredColumn).coerceIn(lineStart, lineEnd)
        editorState.moveCursorTo(target)
    }

    fun toggleReadOnly() {
        editorState.readOnly = !editorState.readOnly
    }

    fun save() {
        viewModelScope.launch {
            val saver = FileSaver()
            when (val outcome = saver.save(editorState)) {
                is SaveOutcome.Ok -> {
                    editorState.markSaved(outcome.lastModified)
                    message = "已保存"
                }
                SaveOutcome.NoFile -> message = "无文件"
                SaveOutcome.ExternallyModified -> message = "文件已被外部修改"
                is SaveOutcome.Error -> message = outcome.msg
            }
        }
    }

    // ==================== 历史 ====================

    private fun pushUndoForTyping() {
        val now = System.currentTimeMillis()
        // 合并条件：距离上次输入 < 500ms 且本次会话累计 < 20 字符。
        // 只有时间条件会导致连续打字永不可撤销。
        val withinTime = now - lastEditTimeMs < typingMergeMs
        val withinChars = typingSessionChars < typingMergeChars
        if (!(withinTime && withinChars)) pushUndoNow()
        typingSessionChars++
        lastEditTimeMs = now
    }

    private fun pushUndoNow() {
        val snap = editorState.fullText()
        undoStack.addLast(snap)
        historySize += snap.length.toLong() * 2
        while (undoStack.size > maxHistory ||
            (historySize > historyBudget && undoStack.size > 1)
        ) {
            val removed = undoStack.removeFirst()
            historySize -= removed.length.toLong() * 2
        }
        redoStack.clear()
        lastEditTimeMs = 0
        typingSessionChars = 0
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun undo() {
        val snap = undoStack.removeLastOrNull() ?: return
        val cur = editorState.fullText()
        redoStack.addLast(cur)
        historySize -= snap.length.toLong() * 2
        editorState.setFullText(snap, "\n", markDirty = true)
        refreshHistoryFlags()
        lastEditTimeMs = 0
        scheduleDiagnose(immediate = false)
        if (findVisible) refreshMatches()
        dismissCompletion()
    }

    fun redo() {
        val snap = redoStack.removeLastOrNull() ?: return
        val cur = editorState.fullText()
        undoStack.addLast(cur)
        historySize += cur.length.toLong() * 2
        editorState.setFullText(snap, "\n", markDirty = true)
        refreshHistoryFlags()
        lastEditTimeMs = 0
        scheduleDiagnose(immediate = false)
        if (findVisible) refreshMatches()
        dismissCompletion()
    }

    // ==================== 诊断 / 高亮 ====================

    fun toggleDiagnosticsExpanded() { diagnosticsExpanded = !diagnosticsExpanded }
    fun clearMessage() { message = null }

    /** 诊断面板点击后跳转到指定行。 */
    fun requestScrollToLine(line: Int) {
        editorState.moveCursorToLine(line)
    }

    private fun scheduleDiagnose(immediate: Boolean) {
        diagnoseJob?.cancel()
        diagnoseJob = viewModelScope.launch {
            if (!immediate) delay(400.milliseconds)
            val linter = BuiltinLinters.combined(
                activeContext.language,
                activeContext.libraries
            ) ?: return@launch

            val snapshot = editorState.fullText().split('\n')
            val source = object : SourceView {
                override val lines: List<String> = snapshot
                override val path: String? get() = filePath
                override fun text(): String = snapshot.joinToString("\n")
                override fun line(n: Int): String = snapshot.getOrElse(n) { "" }
            }
            val result: List<Diagnostic> = withContext(Dispatchers.Default) {
                runCatching { linter.diagnose(source) }.getOrDefault(emptyList())
            }
            diagnostics = result
            if (result.none { it.severity == Severity.ERROR }) {
                diagnosticsExpanded = false
            }
        }
    }

    // ==================== 查找替换 ====================

    fun openFind() { findVisible = true; refreshMatches() }
    fun closeFind() { findVisible = false; matches = emptyList(); currentMatchIndex = 0 }
    /**
     * 查找框输入变化。防抖 150ms 后重算匹配。
     * 用户在查找框里打字很频繁，每次按键全量扫描全文代价大。
     */
    fun updateQuery(q: String) {
        query = q
        currentMatchIndex = 0
        scheduleRefreshMatches()
    }
    fun updateReplaceText(t: String) { replaceText = t }
    fun toggleCaseSensitive() { caseSensitive = !caseSensitive; refreshMatches() }

    fun nextMatch() {
        if (matches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + 1) % matches.size
        jumpToCurrentMatch()
    }

    fun prevMatch() {
        if (matches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex - 1 + matches.size) % matches.size
        jumpToCurrentMatch()
    }

    private fun jumpToCurrentMatch() {
        val m = matches.getOrNull(currentMatchIndex) ?: return
        val lineStart = EditorState.offsetAtLineStart(editorState.fullText(), m.line)
        editorState.moveCursorTo(lineStart + m.col)
    }

    fun replaceOne() {
        if (matches.isEmpty() || query.isEmpty()) return
        val m = matches.getOrNull(currentMatchIndex) ?: return
        pushUndoNow()
        val lineStart = EditorState.offsetAtLineStart(editorState.fullText(), m.line)
        val offset = lineStart + m.col
        editorState.replaceRange(offset, offset + m.len, replaceText)
        scheduleDiagnose(immediate = false)
        refreshMatches()
    }

    fun replaceAll() {
        if (query.isEmpty()) return
        pushUndoNow()
        val text = editorState.fullText()
        val cmp = if (caseSensitive) query else query.lowercase()
        val hay = if (caseSensitive) text else text.lowercase()
        val sb = StringBuilder()
        var i = 0
        while (true) {
            val idx = hay.indexOf(cmp, i)
            if (idx < 0) {
                sb.append(text, i, text.length)
                break
            }
            sb.append(text, i, idx).append(replaceText)
            i = idx + query.length
        }
        editorState.setFullText(sb.toString(), "\n", markDirty = true)
        scheduleDiagnose(immediate = false)
        refreshMatches()
        message = "已替换所有匹配"
    }

    /**
     * 防抖版本的 [refreshMatches]。用户连续输入时只在停顿后触发一次全量扫描。
     *
     * 使用场景：
     *  - [updateValue]（编辑器打字）
     *  - [updateQuery]（查找框打字）
     *
     * 其他用户操作（切换大小写、上一个/下一个、替换）直接调
     * [refreshMatches] 立即刷新。
     */
    private fun scheduleRefreshMatches() {
        matchJob?.cancel()
        matchJob = viewModelScope.launch {
            delay(150.milliseconds)
            refreshMatches()
        }
    }

    private fun refreshMatches() {
        if (query.isEmpty()) { matches = emptyList(); currentMatchIndex = 0; return }
        matches = Finder.findAll(
            editorState.fullText().split('\n'),
            query,
            caseSensitive
        )
        if (currentMatchIndex >= matches.size) currentMatchIndex = 0
    }

    // ==================== 补全 ====================

    private fun scheduleCompletion(value: TextFieldValue) {
        completionJob?.cancel()
        val text = value.text
        val cursor = value.selection.end
        val start = prefixStart(text, cursor)
        if (start < 0 || start >= cursor) { dismissCompletion(); return }
        val prefix = text.substring(start, cursor)
        if (prefix.isEmpty()) { dismissCompletion(); return }
        val candidates = CompletionEngine.candidates(activeContext, prefix)
        if (candidates.isEmpty()) { dismissCompletion(); return }
        completionPrefixStart = start
        completionPrefixEnd = cursor
        completionCandidates = candidates
        completionIndex = 0
        completionVisible = true
    }

    private fun prefixStart(text: String, cursor: Int): Int {
        var i = cursor - 1
        while (i >= 0) {
            val c = text[i]
            if (c.isLetterOrDigit() || c == '_' || c == '.') i-- else break
        }
        return i + 1
    }

    fun dismissCompletion() {
        completionVisible = false
        completionCandidates = emptyList()
        completionIndex = 0
        completionPrefixStart = -1
        completionPrefixEnd = -1
    }

    fun acceptCompletion() {
        val c = completionCandidates.getOrNull(completionIndex) ?: return
        val start = completionPrefixStart
        val end = completionPrefixEnd
        if (start < 0 || end < start) { dismissCompletion(); return }
        pushUndoNow()
        editorState.replaceRange(start, end, c.insert)
        dismissCompletion()
        scheduleDiagnose(immediate = false)
    }

    // ==================== 工具 ====================

    /**
     * 从 startDir 向上遍历最多 maxDepth 层，收集所有文件/目录名。
     * 用于库的 detectBy：只要任一父目录里有 main.lua，就算 LÖVE 项目。
     */
    private fun collectNamesUpward(startDir: File?, maxDepth: Int, maxTotal: Int): Set<String> {
        if (startDir == null) return emptySet()
        val out = mutableSetOf<String>()
        var dir: File? = startDir
        var depth = 0
        while (dir != null && depth < maxDepth && out.size < maxTotal) {
            dir.listFiles()?.forEach { f ->
                if (out.size < maxTotal) out += f.name
            }
            val p = dir.absolutePath
            if (p == "/" || p == "/storage/emulated/0" || p == "/sdcard") break
            dir = dir.parentFile
            depth++
        }
        return out
    }

    companion object {
        const val MAX_SIZE_BYTES = 512L * 1024L
    }
}
