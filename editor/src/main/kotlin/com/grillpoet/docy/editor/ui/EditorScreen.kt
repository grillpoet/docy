package com.grillpoet.docy.editor.ui

import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.text.input.TextFieldValue
import com.grillpoet.docy.core.plugin.LanguagePlugin
import com.grillpoet.docy.editor.highlight.Highlighter
import kotlin.math.abs

private const val FONT_MIN = 10f
private const val FONT_MAX = 28f

/**
 * 编辑器屏幕。
 *
 * ## 架构
 *
 * 编排层，持有 ViewModel 和三块独立 UI：
 *  - [EditorTopBar]：顶栏
 *  - [DocyCodeEditor]：编辑器主体（单实例 BasicTextField + 行号 + 高亮）
 *  - 辅助浮层：补全 / 查找替换 / 诊断面板 / 键盘辅助行
 *
 * ## 手势缩放
 *
 * 双指捏合缩放字号。这是唯一保留的手势逻辑。缩放通过
 * [graphicsLayer] 的 scale 属性实现，视觉上平滑，实际字号
 * 在动画结束后一次性更新到 ViewModel。
 *
 * @param path 文件绝对路径。
 * @param onBack 返回回调。
 * @param disabledLanguages 用户禁用的语言集合。
 * @param disabledLibraries 用户禁用的库集合。
 * @param forceEnabledLibraries 用户强制启用的库集合。
 * @param fontSize 字号。
 * @param colorfulSyntax 是否彩色语法高亮。
 * @param onFontSizeChange 字号变化回调（手势缩放结束后触发）。
 */
@Composable
fun EditorScreen(
    path: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    disabledLanguages: Set<String> = emptySet(),
    disabledLibraries: Set<String> = emptySet(),
    forceEnabledLibraries: Set<String> = emptySet(),
    fontSize: Float = 14f,
    colorfulSyntax: Boolean = true,
    onFontSizeChange: (Float) -> Unit = {}
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext

    val vm: EditorViewModel = viewModel(
        key = "editor-$path",
        factory = viewModelFactory {
            initializer {
                EditorViewModel(
                    path, appCtx,
                    disabledLanguages, disabledLibraries, forceEnabledLibraries
                )
            }
        }
    )

    val state = vm.editorState
    val loading = vm.loading
    val error = vm.error
    val message = vm.message

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(appCtx, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    // 稳定化 onValueChange。
    //
    // `vm::updateValue` 每次重组都是新对象（方法引用），
    // 会让 DocyCodeEditor 无法被 Compose 跳过重组。
    // 用 remember(vm) 缓存一个稳定的 lambda。
    val stableOnValueChange: (TextFieldValue) -> Unit = remember(vm) {
        { v: TextFieldValue -> vm.updateValue(v) }
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(
                fileName = state.file?.name ?: "未命名",
                dirty = state.dirty,
                readOnly = state.readOnly,
                showRun = vm.runSpec != null,
                runEnabled = vm.runEnabled,
                running = vm.running,
                canUndo = vm.canUndo,
                canRedo = vm.canRedo,
                onBack = onBack,
                onToggleReadOnly = { vm.toggleReadOnly() },
                onFind = { vm.openFind() },
                onUndo = { vm.undo() },
                onRedo = { vm.redo() },
                onSave = { vm.save() },
                onRun = { vm.run() },
                onRunDisabled = {
                    Toast.makeText(
                        appCtx,
                        vm.runDisabledReason ?: "当前不可运行",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        ZoomableEditor(
                            value = state.value,
                            onValueChange = stableOnValueChange,
                            plugin = vm.activeContext.language,
                            palette = docyPalette(colorfulSyntax),
                            readOnly = state.readOnly,
                            externalFontSize = fontSize,
                            onFontSizeChange = onFontSizeChange,
                            modifier = Modifier.weight(1f)
                        )

                        if (vm.completionVisible) {
                            CompletionPopup(
                                candidates = vm.completionCandidates,
                                selectedIndex = vm.completionIndex,
                                onSelect = { vm.acceptCompletion() }
                            )
                        }

                        if (vm.findVisible) {
                            FindReplaceSheet(
                                query = vm.query,
                                onQueryChange = { vm.updateQuery(it) },
                                replaceText = vm.replaceText,
                                onReplaceChange = { vm.updateReplaceText(it) },
                                matchCount = vm.matches.size,
                                currentIndex = vm.currentMatchIndex,
                                caseSensitive = vm.caseSensitive,
                                onToggleCaseSensitive = { vm.toggleCaseSensitive() },
                                onPrev = { vm.prevMatch() },
                                onNext = { vm.nextMatch() },
                                onReplaceOne = { vm.replaceOne() },
                                onReplaceAll = { vm.replaceAll() },
                                onClose = { vm.closeFind() }
                            )
                        }

                        DiagnosticPanel(
                            diagnostics = vm.diagnostics,
                            expanded = vm.diagnosticsExpanded,
                            onToggle = { vm.toggleDiagnosticsExpanded() },
                            onJump = { d -> vm.requestScrollToLine(d.line) }
                        )

                        if (imeVisible) {
                            KeyboardAuxRow(
                                indent = vm.activeContext.language?.indent ?: 4,
                                onInsert = { vm.insertAtCursor(it) },
                                onMoveLeft = { vm.moveCursorHorizontal(-1) },
                                onMoveRight = { vm.moveCursorHorizontal(1) },
                                onMoveUp = { vm.moveCursorVertical(-1) },
                                onMoveDown = { vm.moveCursorVertical(1) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 带手势缩放的编辑器包装。
 *
 * ## 性能设计
 *
 * 手势期间**只改 [visualScale]**，不触发 Compose 重组。
 * [visualScale] 是 [mutableFloatStateOf]，只在
 * [Modifier.graphicsLayer] 的 lambda 里被读取——
 * 那个 lambda 在 **draw 阶段**执行，读它只触发重绘，不触发重组。
 *
 * 手势结束时**一次性**提交新字号。此时才发生唯一一次重组，
 * 全文重新布局一次。视觉上用户看不到差别，因为：
 * ```
 *   手势期间：字号 F1 × 视觉缩放 S
 *   手势结束：字号 F2 = F1 × S × 视觉缩放 1
 * ```
 * 两个视觉上完全等价。
 *
 * ## 为什么不做字号动画
 *
 * 曾用 [animateFloatAsState] 做 150ms 过渡，但那让 fontSize 变化
 * 约 9 次，每次触发全文重排 + 高亮重算。几千行文件一帧一次大重排，
 * 卡顿明显。而"动画"的视觉终点和"直接跳变"完全一致，所以动画本身
 * 是多余的。
 */
@Composable
private fun ZoomableEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    plugin: LanguagePlugin?,
    palette: Highlighter.Palette,
    readOnly: Boolean,
    externalFontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // 滚动状态从 DocyCodeEditor 提升到这里。
    // 缩放锚定需要读取 scrollState.value 并补偿滚动位置。
    val scrollState: ScrollState = rememberScrollState()

    // 当前实际字号。手势结束时更新一次。
    var currentFontSize by remember { mutableFloatStateOf(externalFontSize) }

    // 视觉缩放因子。手势期间实时更新。
    // 用 mutableFloatStateOf 保证 graphicsLayer 的 lambda 可以在 draw
    // 阶段读取它，而读取不会触发重组。
    val visualScale = remember { mutableFloatStateOf(1f) }

    // 待提交的滚动位置。
    //
    // 时序：手势结束后先设字号（触发重组，DocyCodeEditor 用新字号
    // 重新布局），下一帧再滚动到目标位置。这样滚动是在新布局上执行的，
    // 位置准确。
    var pendingScroll by remember { mutableStateOf<Int?>(null) }

    // 同步外部字号变化（设置页改字号、或其他入口触发）
    LaunchedEffect(externalFontSize) {
        if (abs(currentFontSize - externalFontSize) > 0.5f) {
            currentFontSize = externalFontSize
            visualScale.floatValue = 1f
        }
    }

    // 字号生效后执行滚动补偿。
    //
    // LaunchedEffect 的协程在组合后调度，实际执行时通常布局已完成，
    // 因此 scrollTo 会落在新的内容高度上。即使偶尔早一帧，
    // ScrollState 会把值 clamp 到合法范围，不会崩溃。
    LaunchedEffect(currentFontSize, pendingScroll) {
        pendingScroll?.let { target ->
            scrollState.scrollTo(target)
            pendingScroll = null
        }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .graphicsLayer {
                // 在 draw 阶段读取 visualScale。
                // 读 mutableFloatStateOf 只让 draw 阶段失效，
                // 不触发 composition。
                val s = visualScale.floatValue
                scaleX = s
                scaleY = s
                // 顶部锚定。放大时内容从视口顶部向下展开，
                // 用户正在读的那行保持在视口顶部附近。
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)

                    var baseFontSize = 0f
                    var scrollBefore = 0
                    var pinching = false

                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f && zoom > 0f && zoom.isFinite()) {
                                if (!pinching) {
                                    pinching = true
                                    baseFontSize = currentFontSize
                                    // 记录手势开始时的滚动位置。
                                    // 双指手势会消费事件，verticalScroll
                                    // 不响应，因此这个值在手势期间保持不变。
                                    scrollBefore = scrollState.value
                                }
                                // 更新视觉缩放。检查边界，避免缩过头。
                                val minS = FONT_MIN / baseFontSize
                                val maxS = FONT_MAX / baseFontSize
                                val newScale = (visualScale.floatValue * zoom)
                                    .coerceIn(minS, maxS)
                                visualScale.floatValue = newScale
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (pinching) {
                        val s = visualScale.floatValue
                        val newFontSize = (baseFontSize * s)
                            .coerceIn(FONT_MIN, FONT_MAX)

                        // 按比例补偿滚动位置：
                        // 行高放大 s 倍后，同样的像素位置显示不同内容行。
                        // 要让"视口顶部那一行"保持在顶部，滚动位置也乘以 s。
                        val targetScroll = (scrollBefore * s).toInt()

                        // 归位视觉缩放 + 一次性提交字号（视觉等效）。
                        visualScale.floatValue = 1f
                        currentFontSize = newFontSize
                        // 排入滚动补偿，下一帧执行。
                        pendingScroll = targetScroll
                        onFontSizeChange(newFontSize)
                    }
                }
            }
    ) {
        DocyCodeEditor(
            value = value,
            onValueChange = onValueChange,
            plugin = plugin,
            palette = palette,
            scrollState = scrollState,
            fontSize = currentFontSize,
            readOnly = readOnly,
            modifier = Modifier.fillMaxSize()
        )
    }
}
