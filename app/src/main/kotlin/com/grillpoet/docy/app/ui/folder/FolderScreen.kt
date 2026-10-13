package com.grillpoet.docy.app.ui.folder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.grillpoet.docy.app.DocyApp
import com.grillpoet.docy.app.di.DocyViewModel
import com.grillpoet.docy.app.navigation.Routes
import com.grillpoet.docy.app.ui.common.FileOpener
import com.grillpoet.docy.app.ui.common.InstallResult
import com.grillpoet.docy.app.ui.dialogs.ArchiveActionSheet
import com.grillpoet.docy.app.ui.dialogs.BatchRenameDialog
import com.grillpoet.docy.app.ui.dialogs.CompressDialog
import com.grillpoet.docy.app.ui.dialogs.DeleteConfirmDialog
import com.grillpoet.docy.app.ui.dialogs.FileDetailsDialog
import com.grillpoet.docy.app.ui.dialogs.NewNameDialog
import com.grillpoet.docy.app.ui.dialogs.SelectionMoreSheet
import com.grillpoet.docy.app.ui.selection.SelectionBottomBar
import com.grillpoet.docy.app.ui.selection.SelectionTopBar
import com.grillpoet.docy.app.ui.selection.slideMultiSelect
import com.grillpoet.docy.core.model.FileItem
import com.grillpoet.docy.core.model.FileKind
import com.grillpoet.docy.core.sort.ViewMode
import com.grillpoet.docy.designsystem.components.DocyEmpty
import com.grillpoet.docy.designsystem.components.DocyFileIcon
import com.grillpoet.docy.designsystem.components.DocyGridItem
import com.grillpoet.docy.designsystem.components.DocyListItem
import com.grillpoet.docy.designsystem.components.DocySkeletonList
import java.io.File


/**
 * [FolderScreen] 里所有对话框的互斥状态。
 *
 * 用 sealed interface 而非 10 个布尔值的原因：
 *  - 同一时间只允许显示一个，用类型系统强制
 *  - 携带数据的对话框（[ArchiveSheet] / [Details]）用 data class
 *  - 加对话框时编译器会提示 where 分支不穷尽
 */
sealed interface FolderDialog {
    data object NewFolder : FolderDialog
    data object NewFile : FolderDialog
    data object Rename : FolderDialog
    data object BatchRename : FolderDialog
    data object Delete : FolderDialog
    data object Compress : FolderDialog
    data object SelectionMore : FolderDialog
    data object ViewSettings : FolderDialog
    data class ArchiveSheet(val item: FileItem) : FolderDialog
    data class Details(val item: FileItem) : FolderDialog
}

@Composable
fun FolderScreen(
    navController: NavHostController,
    docyVm: DocyViewModel,
    path: String?,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as DocyApp

    val vm: FolderViewModel = viewModel(
        key = "folder-${path ?: "root"}",
        factory = viewModelFactory {
            initializer { FolderViewModel(path, app.prefs) }
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(path, docyVm.refreshVersion) { vm.load() }

    // 把加载后的真实目录路径上报给共享 VM。
    // 主页（HOME 路由）没有 path 参数，靠这里让待定区执行按钮拿到目标目录。
    LaunchedEffect(state.path) {
        docyVm.setCurrentDir(state.path)
    }

    // 拦截系统返回：优先回到上层目录，而不是退出或回首页。
    // 到达存储根目录（"/" / "/storage/emulated/0" / "/sdcard"）时
    // parentPath 为 null，BackHandler 不启用，放行默认行为。
    val parentPath = remember(state.path) {
        state.path?.let { p ->
            if (p == "/" || p == "/storage/emulated/0" || p == "/sdcard") {
                null
            } else {
                File(p).parentFile?.absolutePath
            }
        }
    }
    BackHandler(enabled = parentPath != null) {
        parentPath?.let { up ->
            navController.navigate(Routes.folder(up)) {
                // 弹到 HOME（不含），再压入父目录，
                // 避免反复返回时导航栈无限增长。
                popUpTo(Routes.HOME) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    // 所有对话框用一个状态变量表达。同一时间只允许显示一个。
    var dialog: FolderDialog? by remember { mutableStateOf(null) }

    val selectedItems = if (state.searchVisible) {
        state.searchResults.map { it.item }.filter { it.path in state.selection }
    } else {
        state.items.filter { it.path in state.selection }
    }
    val allArchives = selectedItems.isNotEmpty() &&
            selectedItems.all { it.kind == FileKind.ARCHIVE }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            if (state.searchVisible) {
                SearchTopBar(
                    query = state.searchQuery,
                    onQueryChange = { vm.updateSearchQuery(it) },
                    onClose = { vm.exitSearch() }
                )
            } else if (state.selectionMode) {
                SelectionTopBar(
                    selectedCount = state.selection.size,
                    totalCount = state.items.size,
                    onClose = { vm.exitSelection() },
                    onSelectAll = { vm.selectAll() }
                )
            } else {
                TopBar(
                    crumbs = state.breadcrumb,
                    onNavigate = { p ->
                        navController.navigate(Routes.folder(p)) { launchSingleTop = true }
                    },
                    onOpenViewSettings = { dialog = FolderDialog.ViewSettings },
                    onSearch = { vm.enterSearch() },
                    onNewFolder = { dialog = FolderDialog.NewFolder },
                    onNewFile = { dialog = FolderDialog.NewFile },
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    }
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.searchVisible -> SearchResults(
                        state = state,
                        listState = listState,
                        onOpen = { item -> openSearchResult(navController, ctx, item) },
                        onLongPress = { item -> vm.enterSelection(item.path) },
                        onToggle = { item -> vm.toggleSelection(item.path) },
                        onSelectRange = { a, b -> vm.selectRange(a, b) }
                    )
                    state.loading -> DocySkeletonList()
                    state.error != null -> DocyEmpty(
                        title = state.error!!,
                        message = "检查存储权限或目录是否存在"
                    )
                    state.items.isEmpty() -> DocyEmpty(title = "此文件夹为空")
                    state.view == ViewMode.LIST -> LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .slideMultiSelect(
                                listState = listState,
                                enabled = state.selectionMode,
                                onRange = { a, b -> vm.selectRange(a, b) }
                            ),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(state.items, key = { it.path }) { item ->
                            DocyListItem(
                                item = item,
                                onClick = {
                                    if (state.selectionMode) vm.toggleSelection(item.path)
                                    else openItem(navController, ctx, item) {
                                        dialog = FolderDialog.ArchiveSheet(item)
                                    }
                                },
                                selected = item.path in state.selection,
                                onLongClick = {
                                    if (!state.selectionMode) vm.enterSelection(item.path)
                                }
                            )
                        }
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(state.gridColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(state.items, key = { it.path }) { item ->
                            DocyGridItem(
                                item = item,
                                onClick = {
                                    if (state.selectionMode) vm.toggleSelection(item.path)
                                    else openItem(navController, ctx, item) {
                                        dialog = FolderDialog.ArchiveSheet(item)
                                    }
                                },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .aspectRatio(0.85f),
                                selected = item.path in state.selection,
                                onLongClick = {
                                    if (!state.selectionMode) vm.enterSelection(item.path)
                                }
                            )
                        }
                    }
                }
            }

            if (state.selectionMode) {
                SelectionBottomBar(
                    selectedCount = state.selection.size,
                    onCopy = {
                        docyVm.startCopy(state.selection.toList())
                        vm.exitSelection()
                        Toast.makeText(ctx, "切换到目标目录后点击执行", Toast.LENGTH_SHORT).show()
                    },
                    onMove = {
                        docyVm.startMove(state.selection.toList())
                        vm.exitSelection()
                        Toast.makeText(ctx, "切换到目标目录后点击执行", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = { dialog = FolderDialog.Delete },
                    onRename = {
                        dialog = if (state.selection.size == 1) FolderDialog.Rename
                                 else FolderDialog.BatchRename
                    },
                    onMore = { dialog = FolderDialog.SelectionMore }
                )
            }
        }
    }

    // ---------- 对话框 ----------

    when (val d = dialog) {
        null -> Unit  // 无对话框

        FolderDialog.NewFolder -> NewNameDialog(
            title = "新建文件夹",
            onConfirm = { name ->
                dialog = null
                vm.createFolder(name) { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
            },
            onDismiss = { dialog = null }
        )

        FolderDialog.NewFile -> NewNameDialog(
            title = "新建文件",
            onConfirm = { name ->
                dialog = null
                vm.createFile(name) { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
            },
            onDismiss = { dialog = null }
        )

        FolderDialog.Rename -> {
            // 选中的项可能已被删除（异步竞态）。拿不到就静默关闭。
            val item = state.items.firstOrNull { it.path in state.selection }
            if (item == null) {
                dialog = null
            } else {
                NewNameDialog(
                    title = "重命名",
                    initialName = item.name,
                    confirmLabel = "重命名",
                    onConfirm = { name ->
                        dialog = null
                        vm.rename(item.path, name) {
                            vm.exitSelection()
                            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismiss = { dialog = null }
                )
            }
        }

        FolderDialog.BatchRename -> BatchRenameDialog(
            items = state.items.filter { it.path in state.selection },
            onConfirm = { plans ->
                dialog = null
                vm.batchRename(plans) {
                    vm.exitSelection()
                    Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { dialog = null }
        )

        FolderDialog.Delete -> DeleteConfirmDialog(
            count = state.selection.size,
            onConfirm = {
                dialog = null
                vm.delete(state.selection.toList()) {
                    Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { dialog = null }
        )

        FolderDialog.Compress -> CompressDialog(
            defaultName = "archive.zip",
            onConfirm = { name, format ->
                dialog = null
                vm.compress(selectedItems.map { it.path }, name, format) {
                    Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { dialog = null }
        )

        FolderDialog.SelectionMore -> SelectionMoreSheet(
            canCompress = selectedItems.isNotEmpty(),
            canExtract = allArchives,
            canShare = selectedItems.isNotEmpty(),
            // 从「更多」切到「压缩」，dialog 状态一换一
            onCompress = { dialog = FolderDialog.Compress },
            onExtractHere = {
                val first = selectedItems.firstOrNull()
                val dir = first?.let { File(it.path).parentFile?.absolutePath }
                if (first != null && dir != null) {
                    dialog = null
                    vm.extract(first.path, dir) {
                        Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    dialog = null
                }
            },
            onExtractToNewFolder = {
                val first = selectedItems.firstOrNull()
                if (first != null) {
                    val src = File(first.path)
                    val baseName = src.name.substringBeforeLast('.')
                    val dir = File(src.parentFile, baseName).absolutePath
                    dialog = null
                    vm.extract(first.path, dir) {
                        Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    dialog = null
                }
            },
            onShare = {
                val files = selectedItems.map { File(it.path) }
                vm.exitSelection()
                dialog = null
                FileOpener.shareMany(ctx, files)
                    .onFailure { e ->
                        Toast.makeText(
                            ctx,
                            "分享失败：${e.message ?: "未知错误"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            },
            onCopyPath = {
                val paths = state.selection.joinToString("\n")
                copyToClipboard(ctx, paths)
                vm.exitSelection()
                dialog = null
                Toast.makeText(ctx, "已复制路径", Toast.LENGTH_SHORT).show()
            },
            onDetails = {
                val first = selectedItems.firstOrNull()
                vm.exitSelection()
                // 直接从「更多」切到「详情」
                dialog = first?.let { FolderDialog.Details(it) }
            },
            onDismiss = { dialog = null }
        )

        FolderDialog.ViewSettings -> ViewSettingsSheet(
            sort = state.sort,
            view = state.view,
            gridColumns = state.gridColumns,
            onSortMode = { vm.setSortMode(it) },
            onSortOrder = { vm.setSortOrder(it) },
            onFoldersFirst = { vm.setFoldersFirst(it) },
            onView = { vm.setViewMode(it) },
            onGridColumns = { vm.setGridColumns(it) },
            onDismiss = { dialog = null }
        )

        is FolderDialog.Details -> FileDetailsDialog(
            item = d.item,
            onDismiss = { dialog = null }
        )

        is FolderDialog.ArchiveSheet -> ArchiveActionSheet(
            fileName = d.item.name,
            onView = {
                val p = d.item.path
                dialog = null
                navController.navigate(Routes.archive(p)) { launchSingleTop = true }
            },
            onExtractHere = {
                val p = d.item.path
                val dir = File(p).parentFile?.absolutePath
                if (dir != null) {
                    dialog = null
                    vm.extract(p, dir) {
                        Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    dialog = null
                }
            },
            onExtractToNewFolder = {
                val p = d.item.path
                val src = File(p)
                val dir = File(src.parentFile, src.name.substringBeforeLast('.')).absolutePath
                dialog = null
                vm.extract(p, dir) {
                    Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                }
            },
            onDetails = {
                // 从 ArchiveSheet 切到 Details，dialog 状态一换一
                dialog = FolderDialog.Details(d.item)
            },
            onDismiss = { dialog = null }
        )
    }

    // 进度对话框。先读成局部 val，避免 Compose 委托属性的 smart cast 问题。
    val busyMessage = state.busy
    if (busyMessage != null) {
        val progress = state.busyProgress
        val current = state.busyCurrent
        val total = state.busyTotal

        AlertDialog(
            // 压缩 / 解压进行中，不允许取消。
            // 空 lambda 表示屏蔽返回键和点击外部关闭。
            onDismissRequest = { },
            confirmButton = { },
            title = { Text(busyMessage) },
            text = {
                Column {
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (total > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "$current / $total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var tfv by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出搜索")
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = tfv,
                        onValueChange = {
                            tfv = it
                            onQueryChange(it.text)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (tfv.text.isEmpty()) {
                                    Text(
                                        text = "搜索当前目录…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (tfv.text.isNotEmpty()) {
                        IconButton(onClick = {
                            tfv = TextFieldValue("", TextRange(0))
                            onQueryChange("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "清空")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: FolderViewModel.UiState,
    listState: LazyListState,
    onOpen: (FileItem) -> Unit,
    onLongPress: (FileItem) -> Unit,
    onToggle: (FileItem) -> Unit,
    onSelectRange: (Int, Int) -> Unit
) {
    when {
        state.searching && state.searchResults.isEmpty() -> DocySkeletonList()
        state.searchResults.isEmpty() && state.searchQuery.isNotBlank() -> {
            DocyEmpty(title = "未找到匹配项", message = "试试其他关键词")
        }
        state.searchResults.isEmpty() -> {
            DocyEmpty(
                title = "输入关键词搜索",
                message = "在当前目录及子目录中查找文件"
            )
        }
        else -> LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .slideMultiSelect(
                    listState = listState,
                    enabled = state.selectionMode,
                    onRange = onSelectRange
                ),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item {
                Text(
                    text = if (state.searchTruncated) {
                        "显示前 ${state.searchResults.size} 条（结果过多）"
                    } else {
                        "${state.searchResults.size} 条结果"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            items(state.searchResults, key = { it.item.path }) { result ->
                SearchResultItem(
                    result = result,
                    selected = result.item.path in state.selection,
                    onClick = {
                        if (state.selectionMode) onToggle(result.item)
                        else onOpen(result.item)
                    },
                    onLongClick = {
                        if (!state.selectionMode) onLongPress(result.item)
                    }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: FolderViewModel.SearchResult,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DocyFileIcon(
                        kind = result.item.kind,
                        path = result.item.path,
                        size = 40.dp
                    )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = result.item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = result.relativePath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("docy.path", text))
}

private fun openSearchResult(
    navController: NavHostController,
    ctx: Context,
    item: FileItem
) {
    if (item.isDirectory) {
        navController.navigate(Routes.folder(item.path)) { launchSingleTop = true }
    } else {
        when (item.kind) {
            FileKind.TEXT, FileKind.CODE -> {
                navController.navigate(Routes.editor(item.path)) { launchSingleTop = true }
            }
            FileKind.APK -> {
                FileOpener.installApk(ctx, File(item.path))
                    .onSuccess { r ->
                        if (r == InstallResult.NeedPermission) {
                            Toast.makeText(ctx, "请允许 docy 安装应用", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .onFailure {
                        Toast.makeText(ctx, "安装失败：${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            else -> {
                FileOpener.openWith(ctx, File(item.path))
                    .onFailure { e ->
                        Toast.makeText(
                            ctx,
                            "无法打开：${e.message ?: "没有可用的应用"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }
    }
}

private fun openItem(
    navController: NavHostController,
    ctx: Context,
    item: FileItem,
    onArchive: () -> Unit
) {
    if (item.isDirectory) {
        navController.navigate(Routes.folder(item.path)) { launchSingleTop = true }
        return
    }
    val ext = item.extension.lowercase()
    if (ext == "md" || ext == "markdown") {
        navController.navigate(Routes.markdown(item.path)) { launchSingleTop = true }
        return
    }
    when (item.kind) {
        FileKind.ARCHIVE -> onArchive()
        FileKind.TEXT, FileKind.CODE -> {
            navController.navigate(Routes.editor(item.path)) { launchSingleTop = true }
        }
        FileKind.APK -> {
            FileOpener.installApk(ctx, File(item.path))
                .onSuccess { r ->
                    if (r == InstallResult.NeedPermission) {
                        Toast.makeText(ctx, "请允许 docy 安装应用", Toast.LENGTH_SHORT).show()
                    }
                }
                .onFailure {
                    Toast.makeText(ctx, "安装失败：${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
        else -> {
            FileOpener.openWith(ctx, File(item.path))
                .onFailure { e ->
                    Toast.makeText(
                        ctx,
                        "无法打开：${e.message ?: "没有可用的应用"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}

@Composable
private fun TopBar(
    crumbs: List<FolderViewModel.Crumb>,
    onNavigate: (String) -> Unit,
    onOpenViewSettings: () -> Unit,
    onSearch: () -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth()) {
            // 第一行：操作图标，右对齐。紧邻状态栏，
            // 符合 Material 惯例（actions 在顶部）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
                IconButton(onClick = onOpenViewSettings) {
                    Icon(Icons.Default.Tune, contentDescription = "视图设置")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "新建")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("新建文件夹") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                            onClick = {
                                menuOpen = false
                                onNewFolder()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("新建文件") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                            onClick = {
                                menuOpen = false
                                onNewFile()
                            }
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            }

            // 第二行：面包屑整行宽度。长路径时可以横向滚动，
            // 不再和图标按钮争空间。
            Breadcrumb(
                crumbs = crumbs,
                onNavigate = onNavigate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 12.dp)
            )
        }
    }
}
