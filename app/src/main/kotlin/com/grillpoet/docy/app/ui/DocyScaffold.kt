package com.grillpoet.docy.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.grillpoet.docy.app.di.DocyViewModel
import com.grillpoet.docy.app.ui.dialogs.ConflictDialog
import com.grillpoet.docy.app.ui.pending.PendingOpsBar
import com.grillpoet.docy.fs.ops.ConflictResolution
import kotlinx.coroutines.launch

@Composable
fun DocyScaffold(
    navController: NavHostController,
    docyVm: DocyViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backEntry by navController.currentBackStackEntryAsState()
    val currentDir = remember(backEntry) {
        backEntry?.arguments?.getString("path")?.let { Uri.decode(it) }
    }

    var conflictCount by remember { mutableIntStateOf(0) }
    var showConflict by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            content()
        }

        PendingOpsBar(
            pending = docyVm.pending,
            canExecute = docyVm.canExecute(currentDir),
            onExecute = {
                val dir = currentDir ?: return@PendingOpsBar
                scope.launch {
                    val conflicts = docyVm.probeConflicts(dir)
                    if (conflicts.isEmpty()) {
                        docyVm.execute(dir, ConflictResolution.RENAME) { ok, fail ->
                            toastResult(ctx, ok, fail)
                        }
                    } else {
                        conflictCount = conflicts.size
                        showConflict = true
                    }
                }
            },
            onCancel = { docyVm.cancel() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                )
        )
    }

    if (showConflict) {
        ConflictDialog(
            count = conflictCount,
            onConfirm = { resolution, _ ->
                showConflict = false
                val dir = currentDir ?: return@ConflictDialog
                docyVm.execute(dir, resolution) { ok, fail ->
                    toastResult(ctx, ok, fail)
                }
            },
            onDismiss = { showConflict = false }
        )
    }
}

private fun toastResult(
    ctx: android.content.Context,
    ok: Int,
    fail: Int
) {
    val msg = when {
        fail == 0 -> "已完成 $ok 项"
        ok == 0 -> "操作失败"
        else -> "完成 $ok 项，失败 $fail 项"
    }
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}
