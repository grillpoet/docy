package com.grillpoet.docy.editor.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.grillpoet.docy.core.plugin.LanguagePlugin
import com.grillpoet.docy.editor.highlight.Highlighter
import com.grillpoet.docy.editor.highlight.LineState
import com.grillpoet.docy.editor.highlight.Span
import com.grillpoet.docy.editor.highlight.SpanKind

/**
 * 全文语法高亮的 [VisualTransformation]。
 *
 * 与 [LineVisualTransformation] 的区别：
 *  - 本类处理的是**整段文本**（可能包含多行），[LineVisualTransformation]
 *    处理的是单行。
 *  - 本类内部负责跨行状态（块注释 / 块字符串）的传递。
 *
 * ## 使用方式
 *
 * 传给单实例 [androidx.compose.foundation.text.BasicTextField]：
 *
 * ```
 * BasicTextField(
 *     value = value,
 *     onValueChange = onValueChange,
 *     visualTransformation = remember(plugin, palette) {
 *         SyntaxHighlightTransformation(plugin, palette)
 *     },
 * )
 * ```
 *
 * ## 性能
 *
 * [filter] 在每次文本或选择变化时被调用。为了避免重复计算，
 * 内部维护**单元素缓存**（key = 文本内容，value = 高亮 span 列表）。
 * 文本内容未变化时直接返回缓存，仅在选择变化时也命中缓存。
 *
 * 缓存只保留最近一次。更精细的增量缓存超出当前需求（512KB 上限，
 * 最坏情况也只是线性重算），保持简单。
 *
 * ## 参数
 *
 * @param plugin 语言插件。null 时不做高亮，直接返回原文。
 * @param palette 语法高亮配色。从主题或用户设置读取。
 */
class SyntaxHighlightTransformation(
    private val plugin: LanguagePlugin?,
    private val palette: Highlighter.Palette,
) : VisualTransformation {

    private var cachedText: String? = null
    private var cachedSpans: List<Span>? = null

    override fun filter(text: AnnotatedString): TransformedText {
        if (plugin == null || plugin.rules.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val raw = text.text
        val spans = if (cachedText == raw && cachedSpans != null) {
            cachedSpans!!
        } else {
            computeSpans(raw).also {
                cachedText = raw
                cachedSpans = it
            }
        }

        if (spans.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder(raw)
        for (span in spans) {
            val s = span.start.coerceIn(0, builder.length)
            val e = span.end.coerceIn(s, builder.length)
            if (e > s) builder.addStyle(spanStyle(span.kind, palette), s, e)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    /**
     * 逐行高亮，把每行的相对偏移转换为全文的绝对偏移。
     *
     * 行分隔符 `\n` 不计入 span。空文本返回空列表。
     */
    private fun computeSpans(text: String): List<Span> {
        if (text.isEmpty()) return emptyList()

        val out = mutableListOf<Span>()
        var state = LineState()
        var offset = 0

        for (line in text.split('\n')) {
            val (lineSpans, nextState) = Highlighter.highlightLine(line, state, plugin)
            for (span in lineSpans) {
                out += Span(
                    start = offset + span.start,
                    end = offset + span.end,
                    kind = span.kind,
                )
            }
            state = nextState
            offset += line.length + 1
        }
        return out
    }
}

/**
 * [SpanKind] 到 [SpanStyle] 的映射。
 *
 * 放在这里而不是单独的映射文件，因为它只服务于语法高亮这一件事。
 * 未来如果要支持主题切换，配色由 [Highlighter.Palette] 携带，
 * 本函数的逻辑不用改。
 */
fun spanStyle(kind: SpanKind, p: Highlighter.Palette): SpanStyle = when (kind) {
    SpanKind.KEYWORD -> SpanStyle(color = p.keyword, fontWeight = FontWeight.Bold)
    SpanKind.TYPE -> SpanStyle(color = p.type, fontWeight = FontWeight.Medium)
    SpanKind.FUNCTION -> SpanStyle(color = p.function, fontWeight = FontWeight.Medium)
    SpanKind.LITERAL -> SpanStyle(color = p.literal, fontWeight = FontWeight.Medium)
    SpanKind.NUMBER -> SpanStyle(color = p.number)
    SpanKind.STRING -> SpanStyle(color = p.string)
    SpanKind.COMMENT -> SpanStyle(color = p.comment, fontStyle = FontStyle.Italic)
    SpanKind.OPERATOR -> SpanStyle(color = p.operator)
    SpanKind.LIBRARY -> SpanStyle(color = p.library)
    SpanKind.ANNOTATION -> SpanStyle(color = p.annotation, fontStyle = FontStyle.Italic)
    SpanKind.PUNCTUATION -> SpanStyle(color = p.punctuation)
}
