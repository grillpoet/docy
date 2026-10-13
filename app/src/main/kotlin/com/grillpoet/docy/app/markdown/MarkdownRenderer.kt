package com.grillpoet.docy.app.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak

@Composable
fun MarkdownBody(
    document: Node?,
    onOpenUrl: (String) -> Unit,
    scale: Float = 1f
) {
    if (document == null) return
    val topNodes = remember(document) { collectSiblings(document.firstChild) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        topNodes.forEach { node ->
            key(System.identityHashCode(node)) {
                BlockNode(node, onOpenUrl, scale)
            }
        }
    }
}

private fun TextStyle.scaled(scale: Float): TextStyle =
    if (scale == 1f) this else copy(fontSize = (fontSize.value * scale).sp)

@Composable
private fun BlockNode(node: Node, onOpenUrl: (String) -> Unit, scale: Float) {
    when (node) {
        is Heading -> HeadingBlock(node, onOpenUrl, scale)
        is Paragraph -> ParagraphBlock(node, onOpenUrl, scale)
        is BulletList -> BulletListBlock(node, onOpenUrl, scale)
        is OrderedList -> OrderedListBlock(node, onOpenUrl, scale)
        is BlockQuote -> BlockQuoteBlock(node, onOpenUrl, scale)
        is FencedCodeBlock -> CodeBlock(node.literal ?: "", node.info?.trim(), scale)
        is IndentedCodeBlock -> CodeBlock(node.literal ?: "", null, scale)
        is ThematicBreak -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
        is TableBlock -> TableBlockComposable(node, scale)
        is HtmlBlock -> {
            Text(
                text = node.literal ?: "",
                style = MaterialTheme.typography.bodySmall.scaled(scale),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        else -> {
            val children = remember(node) { collectSiblings(node.firstChild) }
            children.forEach { c ->
                key(System.identityHashCode(c)) {
                    BlockNode(c, onOpenUrl, scale)
                }
            }
        }
    }
}

@Composable
private fun HeadingBlock(node: Heading, onOpenUrl: (String) -> Unit, scale: Float) {
    val base = MaterialTheme.typography
    val onBg = MaterialTheme.colorScheme.onBackground
    val style = when (node.level) {
        1 -> base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, color = onBg)
        2 -> base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, color = onBg)
        3 -> base.titleLarge.copy(fontWeight = FontWeight.SemiBold, color = onBg)
        4 -> base.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = onBg)
        5 -> base.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = onBg)
        else -> base.bodyLarge.copy(fontWeight = FontWeight.SemiBold, color = onBg)
    }.scaled(scale)
    val topPad = when (node.level) {
        1 -> 16.dp
        2 -> 12.dp
        else -> 8.dp
    }
    InlineText(
        node = node,
        style = style,
        onOpenUrl = onOpenUrl,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPad, bottom = 4.dp)
    )
}

@Composable
private fun ParagraphBlock(node: Paragraph, onOpenUrl: (String) -> Unit, scale: Float) {
    InlineText(
        node = node,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onBackground
        ).scaled(scale),
        onOpenUrl = onOpenUrl,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun BulletListBlock(node: BulletList, onOpenUrl: (String) -> Unit, scale: Float) {
    val items = remember(node) { collectSiblings(node.firstChild) }
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
        items.forEach { item ->
            if (item is ListItem) {
                key(System.identityHashCode(item)) {
                    ListItemRow(item, bullet = "•", onOpenUrl = onOpenUrl, scale = scale)
                }
            }
        }
    }
}

@Composable
private fun OrderedListBlock(node: OrderedList, onOpenUrl: (String) -> Unit, scale: Float) {
    val start = runCatching { node.markerStartNumber }.getOrDefault(1)
    val items = remember(node) { collectSiblings(node.firstChild) }
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
        var idx = start
        items.forEach { item ->
            if (item is ListItem) {
                key(System.identityHashCode(item)) {
                    ListItemRow(item, bullet = "$idx.", onOpenUrl = onOpenUrl, scale = scale)
                }
                idx++
            }
        }
    }
}

@Composable
private fun ListItemRow(
    item: ListItem,
    bullet: String,
    onOpenUrl: (String) -> Unit,
    scale: Float
) {
    var taskMarker: TaskListItemMarker? = null
    var firstParagraph: Paragraph? = null
    var scan = item.firstChild
    while (scan != null) {
        when (scan) {
            is TaskListItemMarker -> taskMarker = scan
            is Paragraph -> if (firstParagraph == null) firstParagraph = scan
        }
        scan = scan.next
    }

    val marker = when {
        taskMarker != null -> if (taskMarker.isChecked) "☑" else "☐"
        else -> bullet
    }
    val onBg = MaterialTheme.colorScheme.onBackground
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyLarge.copy(color = onVariant).scaled(scale),
            modifier = Modifier.width(24.dp)
        )
        Column(Modifier.weight(1f)) {
            val p = firstParagraph
            if (p != null) {
                InlineText(
                    node = p,
                    style = MaterialTheme.typography.bodyLarge.copy(color = onBg).scaled(scale),
                    onOpenUrl = onOpenUrl
                )
            } else if (taskMarker != null) {
                InlineText(
                    node = item,
                    style = MaterialTheme.typography.bodyLarge.copy(color = onBg).scaled(scale),
                    onOpenUrl = onOpenUrl,
                    skipTaskMarker = true
                )
            }
            val children = remember(item) { collectSiblings(item.firstChild) }
            children.forEach { c ->
                if (c !== firstParagraph && c !is TaskListItemMarker && c !is Paragraph) {
                    if (c is BulletList || c is OrderedList || c is Paragraph) {
                        key(System.identityHashCode(c)) {
                            BlockNode(c, onOpenUrl, scale)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockQuoteBlock(node: BlockQuote, onOpenUrl: (String) -> Unit, scale: Float) {
    val children = remember(node) { collectSiblings(node.firstChild) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Box(
                Modifier
                    .width(3.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                children.forEach { c ->
                    key(System.identityHashCode(c)) {
                        BlockNode(c, onOpenUrl, scale)
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(literal: String, lang: String?, scale: Float) {
    val scroll = rememberScrollState()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(12.dp)) {
            if (!lang.isNullOrBlank()) {
                Text(
                    text = lang,
                    style = MaterialTheme.typography.labelSmall.scaled(scale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
            ) {
                Text(
                    text = literal.trimEnd('\n'),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = (20 * scale).sp
                    ).scaled(scale),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun TableBlockComposable(table: TableBlock, scale: Float) {
    val headerRows = remember(table) {
        mutableListOf<TableRow>().also { it.addAll(collectTableRows(table, true)) }
    }
    val bodyRows = remember(table) {
        mutableListOf<TableRow>().also { it.addAll(collectTableRows(table, false)) }
    }
    val allRows = headerRows + bodyRows

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        allRows.forEachIndexed { rowIdx, row ->
            key(System.identityHashCode(row)) {
                val isHeader = rowIdx < headerRows.size
                Row(Modifier.fillMaxWidth()) {
                    val cells = remember(row) {
                        collectSiblings(row.firstChild).filterIsInstance<TableCell>()
                    }
                    cells.forEach { cell ->
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(6.dp)
                        ) {
                            val style = if (isHeader) {
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }.scaled(scale)
                            InlineText(
                                node = cell,
                                style = style,
                                onOpenUrl = {}
                            )
                        }
                    }
                }
                if (rowIdx < allRows.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun InlineText(
    node: Node,
    style: TextStyle,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    skipTaskMarker: Boolean = false
) {
    val annotated = remember(node, skipTaskMarker) {
        buildMarkdownText(node, skipTaskMarker)
    }
    Text(
        text = annotated,
        style = style,
        modifier = modifier
    )
}

// ============================================================
// 辅助
// ============================================================

private fun collectSiblings(start: Node?): List<Node> {
    val list = mutableListOf<Node>()
    var n = start
    while (n != null) {
        list.add(n)
        n = n.next
    }
    return list
}

private fun collectTableRows(table: TableBlock, header: Boolean): List<TableRow> {
    val out = mutableListOf<TableRow>()
    fun walk(n: Node?) {
        var cur = n
        while (cur != null) {
            when (cur) {
                is TableHead -> {
                    if (header) {
                        var r = cur.firstChild
                        while (r != null) {
                            if (r is TableRow) out.add(r)
                            r = r.next
                        }
                    }
                }
                is TableBody -> {
                    if (!header) {
                        var r = cur.firstChild
                        while (r != null) {
                            if (r is TableRow) out.add(r)
                            r = r.next
                        }
                    }
                }
                else -> walk(cur.firstChild)
            }
            cur = cur.next
        }
    }
    walk(table.firstChild)
    return out
}

private fun buildMarkdownText(root: Node, skipTaskMarker: Boolean): AnnotatedString {
    val builder = AnnotatedString.Builder()
    appendChildren(builder, root.firstChild, skipTaskMarker)
    return builder.toAnnotatedString()
}

private fun appendChildren(
    builder: AnnotatedString.Builder,
    start: Node?,
    skipTaskMarker: Boolean
) {
    var node = start
    while (node != null) {
        appendNode(builder, node, skipTaskMarker)
        node = node.next
    }
}

private fun appendNode(
    builder: AnnotatedString.Builder,
    node: Node,
    skipTaskMarker: Boolean
) {
    when (node) {
        is Text -> builder.append(node.literal ?: "")

        is Code -> {
            builder.pushStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x33888888)
                )
            )
            builder.append(node.literal ?: "")
            builder.pop()
        }

        is Emphasis -> {
            builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            appendChildren(builder, node.firstChild, skipTaskMarker)
            builder.pop()
        }

        is StrongEmphasis -> {
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            appendChildren(builder, node.firstChild, skipTaskMarker)
            builder.pop()
        }

        is Strikethrough -> {
            builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
            appendChildren(builder, node.firstChild, skipTaskMarker)
            builder.pop()
        }

        is Link -> {
            builder.pushStyle(
                SpanStyle(
                    color = Color(0xFF2563EB),
                    textDecoration = TextDecoration.Underline
                )
            )
            appendChildren(builder, node.firstChild, skipTaskMarker)
            builder.pop()
        }

        is SoftLineBreak -> builder.append(" ")
        is HardLineBreak -> builder.append("\n")
        is HtmlInline -> builder.append(node.literal ?: "")

        is TaskListItemMarker -> {}

        else -> appendChildren(builder, node.firstChild, skipTaskMarker)
    }
}
