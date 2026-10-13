package com.grillpoet.docy.editor.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grillpoet.docy.core.plugin.LanguagePlugin
import com.grillpoet.docy.editor.highlight.Highlighter

private const val LINE_HEIGHT_FACTOR = 1.5f
private const val GUTTER_MIN_DIGITS = 2
private const val GUTTER_HORIZONTAL_PADDING_DP = 20f

/**
 * 单实例代码编辑器。
 *
 * ## 架构
 *
 * ```
 * Row(verticalScroll) {
 *   行号列           BasicTextField
 *   (按视觉行)        (单实例，全文)
 * }
 * ```
 *
 * 行号和文本共享同一个 [rememberScrollState]，
 * 滚动天然同步，不需要额外同步代码。
 *
 * ## 行号与折行
 *
 * Compose 的 [BasicTextField] 不支持禁止软换行。
 * 长行必然折成多个视觉行，逻辑行数 != 视觉行数。
 *
 * 本组件采用 Android Studio / VS Code 的做法：
 * 监听 [BasicTextField.onTextLayout] 拿到 [TextLayoutResult]，
 * 按视觉行渲染行号：
 *  - 逻辑行的第一个视觉行显示行号
 *  - 折行的续行显示空
 *
 * @param value 当前文本与选择。
 * @param onValueChange 文本或选择变化时的回调。
 * @param plugin 语言插件。null 时不渲染语法高亮。
 * @param palette 语法高亮配色。
 * @param fontSize 字号，单位 sp。
 * @param readOnly 只读模式。
 * @param modifier 外部修饰符。
 */
@Composable
fun DocyCodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    plugin: LanguagePlugin?,
    palette: Highlighter.Palette,
    scrollState: ScrollState,
    fontSize: Float = 14f,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val lineHeightSp = fontSize * LINE_HEIGHT_FACTOR

    // MaterialTheme.colorScheme 是 @Composable getter，
    // 不能在 remember 的 lambda 里直接读取。
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val textStyle = remember(fontSize, lineHeightSp, onSurface) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            lineHeight = lineHeightSp.sp,
            color = onSurface,
            // 显式指定行盒对齐方式。
            //
            // Text 组件默认把文本垂直居中在 lineHeight 框内，
            // BasicTextField 内部文本是顶部对齐。两边策略不一致
            // 会导致行号和代码行的基线错位。
            //
            // Alignment.Top 让两边都从行盒顶部开始绘制；
            // Trim.None 保留行高首尾的额外空间。
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Top,
                trim = LineHeightStyle.Trim.None,
            ),
        )
    }

    // transformation 实例必须稳定，否则内部缓存每次都失效。
    val transformation = remember(plugin, palette) {
        SyntaxHighlightTransformation(plugin, palette)
    }

    // 布局回调产出的视觉行信息。第一次组合时为 null，
    // 由 BasicTextField 渲染后回调填充。
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // 从布局结果反推每个视觉行对应的逻辑行号。
    val visualLines = remember(value.text, layoutResult) {
        computeVisualLines(value.text, layoutResult)
    }

    // 行号宽度的测量样本是最大行号。折行续行不影响宽度，因为续行是空的。
    val maxLineNumber = visualLines.maxOfOrNull { it.lineNumber } ?: 1
    val gutterWidth = rememberGutterWidth(maxLineNumber, textStyle)

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(scrollState)
    ) {
        LineNumberGutter(
            visualLines = visualLines,
            width = gutterWidth,
            textStyle = textStyle,
            numberColor = onSurfaceVariant,
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            textStyle = textStyle,
            readOnly = readOnly,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = transformation,
            onTextLayout = { layoutResult = it },
        )
    }
}

/**
 * 视觉行信息。
 *
 * @param lineNumber 该视觉行所属的逻辑行号（1-based）。
 * @param isContinuation 是否是折行的续行。
 *                       true 表示前面的视觉行已经显示过这个行号。
 */
private data class VisualLine(
    val lineNumber: Int,
    val isContinuation: Boolean,
)

/**
 * 从 [TextLayoutResult] 反推每个视觉行的逻辑行号。
 *
 * 算法：
 *  1. 预计算全文所有换行符的字符位置
 *  2. 对每个视觉行 v，取它的起始字符 offset
 *  3. offset 之前有几个换行符，这个视觉行就属于第几逻辑行
 *  4. 同一个逻辑行的第一个视觉行 isContinuation = false，
 *     后续的续行 isContinuation = true
 *
 * @param text 当前文本。
 * @param layout [BasicTextField] 的布局结果。null 时返回单行默认值。
 */
private fun computeVisualLines(
    text: String,
    layout: TextLayoutResult?,
): List<VisualLine> {
    if (layout == null || layout.lineCount == 0) {
        return listOf(VisualLine(lineNumber = 1, isContinuation = false))
    }

    // 预计算所有换行符位置。
    val newlinePositions = ArrayList<Int>()
    for (i in text.indices) {
        if (text[i] == '\n') newlinePositions.add(i)
    }

    val result = ArrayList<VisualLine>(layout.lineCount)
    var lastLogicLineIdx = -1

    // 单调指针：视觉行按顺序遍历，起始 offset 递增，
    // newlinePositions 也是递增的。指针只前进不后退，
    // 将每行的 O(换行数) 扫描降为整个函数 O(视觉行 + 换行)。
    //
    // nlScanIdx 同时充当「已数过的换行数」= count { it < startOffset }，
    // 所以无需额外计数变量。
    var nlScanIdx = 0

    for (v in 0 until layout.lineCount) {
        val startOffset = layout.getLineStart(v)
        while (nlScanIdx < newlinePositions.size &&
            newlinePositions[nlScanIdx] < startOffset
        ) {
            nlScanIdx++
        }
        val logicLineIdx = nlScanIdx
        val isContinuation = logicLineIdx == lastLogicLineIdx
        result.add(VisualLine(logicLineIdx + 1, isContinuation))
        lastLogicLineIdx = logicLineIdx
    }

    return result
}

/**
 * 行号区域。
 *
 * 每个视觉行渲染一行：首行显示数字，续行显示空。
 * 这样行号与代码行严格 1:1 对应，不会因折行而错位。
 *
 * ## 性能
 *
 * 一次性创建 [visualLines] 尺寸个 [Text]。对于 1000 逻辑行的文件，
 * 通常对应 1000-1100 个视觉行。初始组合约 100-200ms。
 * 之后 Compose 的 diff 机制保证只有变化的行会重组。
 */
@Composable
private fun LineNumberGutter(
    visualLines: List<VisualLine>,
    width: Dp,
    textStyle: TextStyle,
    numberColor: Color,
) {
    Column(
        modifier = Modifier
            .width(width)
            .padding(end = 4.dp)
    ) {
        for (line in visualLines) {
            Text(
                text = if (line.isContinuation) "" else line.lineNumber.toString(),
                style = textStyle.copy(color = numberColor),
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 测量行号区宽度。
 *
 * 取最大行号的文本（如 9999），用 [TextMeasurer] 真实测量其渲染宽度，
 * 再补上水平 padding。
 *
 * ## 为什么用测量而非估算
 *
 * 等宽字体的字符宽度因字体而异，常见区间是 0.55-0.65 倍字号。
 * 估算如果偏小，Text 会被横向裁剪只剩末位数字。
 * 真实测量一次，结果被 [remember] 缓存，性能开销可忽略。
 *
 * @param maxLineNumber 最大行号。
 * @param textStyle 行号使用的文字样式。
 */
@Composable
private fun rememberGutterWidth(
    maxLineNumber: Int,
    textStyle: TextStyle,
): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    return remember(maxLineNumber, textStyle, measurer, density) {
        val digits = maxLineNumber.toString().length
            .coerceAtLeast(GUTTER_MIN_DIGITS)
        val sample = "9".repeat(digits)
        val layout = measurer.measure(sample, textStyle)
        with(density) { layout.size.width.toDp() } + GUTTER_HORIZONTAL_PADDING_DP.dp
    }
}
