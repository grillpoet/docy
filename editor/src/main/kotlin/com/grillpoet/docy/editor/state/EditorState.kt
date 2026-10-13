package com.grillpoet.docy.editor.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import java.io.File
import java.nio.charset.Charset

/**
 * 编辑器状态。
 *
 * ## 真源
 *
 * [value] 是**唯一真源**。它是 Compose 提供的 [TextFieldValue]，
 * 同时携带文本、光标位置和选择范围。
 *
 * ## 为什么用 TextFieldValue 而非 String + 单独的光标字段
 *
 * 系统原生的全选、跨行选择、拖动扩展选择都依赖 `selection: TextRange`。
 * 把文本和选择放在同一个值对象里，才能让 `BasicTextField` 参与这些
 * 原生行为。
 *
 * ## 派生视图
 *
 * [lineCount] / [cursorLine] / [cursorCol] 都从 [value] 派生，
 * 是"读属性"而非"可写状态"，每次读时按需计算。行/列计算是 O(n)，
 * 但只在 lint 面板、查找替换等少数位置使用，不构成性能瓶颈。
 */
class EditorState {

    /** 关联的文件。null 表示尚未加载。 */
    var file: File? by mutableStateOf(null)

    /** 字符编码。默认 UTF-8。 */
    var charset: Charset = Charsets.UTF_8

    /** 原始换行风格。序列化时还原。 */
    var eol: EolStyle = EolStyle.LF

    /** 只读模式。 */
    var readOnly: Boolean by mutableStateOf(false)

    /** 是否有未保存的修改。 */
    var dirty: Boolean by mutableStateOf(false)
        private set

    /** 文件最后修改时间。用于保存前检测外部修改。 */
    var originalLastModified: Long = 0L

    /**
     * 全文编辑状态。**编辑器唯一真源**。
     *
     * 内部换行统一为 `\n`，与 [EolStyle] 无关。
     * 只有在 [setFullText] / [serialize] 时才做转换。
     */
    var value: TextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    /**
     * 外部版本号。每次文本变化时递增。
     *
     * 用于让诊断和语法高亮知道"文本变了，需要重新计算"。
     */
    var externalVersion by mutableIntStateOf(0)
        private set

    /** 全文行数。空文本视为 1 行。 */
    val lineCount: Int
        get() = if (value.text.isEmpty()) 1 else value.text.count { it == '\n' } + 1

    /** 光标所在行（0-based）。 */
    val cursorLine: Int
        get() = lineAtOffset(value.text, value.selection.end)

    /** 光标所在行的列（0-based）。 */
    val cursorCol: Int
        get() = colAtOffset(value.text, value.selection.end)

    // ==================== 加载 / 保存 ====================

    /**
     * 设置全文。用于文件加载和 undo/redo 恢复。
     *
     * 统一换行符为 `\n`。光标置于 0（避免大文本定位性能问题）。
     *
     * @param markDirty 是否置 [dirty]。
     *   - 文件加载时传 false（内容来自磁盘，不算用户修改）
     *   - undo / redo / 全文替换时传 true
     */
    fun setFullText(
        text: String,
        eolValue: String,
        markDirty: Boolean = true,
    ) {
        val normalized = if (eolValue == "\n") text else text.replace(eolValue, "\n")
        value = TextFieldValue(normalized, TextRange(0))
        if (markDirty) dirty = true
        externalVersion++
    }

    /** 序列化为字符串，还原原始换行符。 */
    fun serialize(eolValue: String): String =
        if (eolValue == "\n") value.text else value.text.replace("\n", eolValue)

    /** 全文（内部换行为 `\n`）。 */
    fun fullText(): String = value.text

    // ==================== 修改 ====================

    /**
     * 用新的 [TextFieldValue] 替换当前值。
     *
     * 由 UI 层的 `onValueChange` 调用。
     *
     * 只有**文本内容**发生变化时才置 dirty 和递增版本号。
     * 纯选择变化（拖动光标、选中文字）不应把文件标记为已修改。
     */
    fun updateValue(new: TextFieldValue) {
        val textChanged = new.text != value.text
        value = new
        if (textChanged) {
            dirty = true
            externalVersion++
        }
    }

    /** 标记为已修改。仅供模块内调用。 */
    internal fun markDirty() { dirty = true }

    /** 标记为已保存。 */
    fun markSaved(lastModified: Long) {
        dirty = false
        originalLastModified = lastModified
    }

    // ==================== 光标 / 选择操作 ====================

    /**
     * 把光标移到指定字符 offset，清除当前选择。
     *
     * 用于诊断面板跳转、查找替换定位等场景。
     */
    fun moveCursorTo(offset: Int) {
        val clamped = offset.coerceIn(0, value.text.length)
        value = value.copy(selection = TextRange(clamped))
    }

    /**
     * 移动光标到指定行的行首。
     *
     * `BasicTextField` 会在光标位置变化时自动滚动到可视区，
     * 因此不需要额外的 scrollTo 逻辑。
     */
    fun moveCursorToLine(line: Int) {
        val offset = offsetAtLineStart(value.text, line)
        moveCursorTo(offset)
    }

    /**
     * 在光标处插入文本。如果有选择，替换选择。
     *
     * 用于键盘辅助行插入符号、补全接受等场景。
     */
    fun insertAtCursor(text: String) {
        val sel = value.selection
        val start = sel.min
        val end = sel.max
        val newText = value.text.substring(0, start) + text + value.text.substring(end)
        val newCursor = start + text.length
        value = TextFieldValue(newText, TextRange(newCursor))
        dirty = true
        externalVersion++
    }

    /**
     * 用 [replacement] 替换 [start, end) 区间的文本。
     *
     * 用于查找替换。
     */
    fun replaceRange(start: Int, end: Int, replacement: String) {
        val s = start.coerceIn(0, value.text.length)
        val e = end.coerceIn(s, value.text.length)
        val newText = value.text.substring(0, s) + replacement + value.text.substring(e)
        val newCursor = s + replacement.length
        value = TextFieldValue(newText, TextRange(newCursor))
        dirty = true
        externalVersion++
    }

    // ==================== 工具 ====================

    companion object {

        /** 字符 offset -> 行号（0-based）。 */
        fun lineAtOffset(text: String, offset: Int): Int {
            val o = offset.coerceIn(0, text.length)
            var line = 0
            for (i in 0 until o) if (text[i] == '\n') line++
            return line
        }

        /** 字符 offset -> 行内列号（0-based）。 */
        fun colAtOffset(text: String, offset: Int): Int {
            val o = offset.coerceIn(0, text.length)
            var lastNl = -1
            for (i in 0 until o) if (text[i] == '\n') lastNl = i
            return o - lastNl - 1
        }

        /** 指定行的行首 offset。 */
        fun offsetAtLineStart(text: String, line: Int): Int {
            if (line <= 0) return 0
            var count = 0
            for (i in text.indices) {
                if (text[i] == '\n') {
                    count++
                    if (count == line) return i + 1
                }
            }
            return text.length
        }

        /** 指定行的行尾 offset。 */
        fun offsetAtLineEnd(text: String, line: Int): Int {
            val start = offsetAtLineStart(text, line)
            val nextNl = text.indexOf('\n', start)
            return if (nextNl < 0) text.length else nextNl
        }
    }
}
