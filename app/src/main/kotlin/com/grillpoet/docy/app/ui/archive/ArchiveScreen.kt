package com.grillpoet.docy.app.ui.archive

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grillpoet.docy.designsystem.components.ArchiveEntryItem
import com.grillpoet.docy.designsystem.components.DocyEmpty
import com.grillpoet.docy.designsystem.components.DocySkeletonList
import java.io.File

@Composable
fun ArchiveScreen(
    path: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val vm: ArchiveViewModel = viewModel(
        key = "archive-$path",
        factory = viewModelFactory {
            initializer { ArchiveViewModel(path, appCtx) }
        }
    )

    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(appCtx, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Text(
                    text = File(path).name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    val parent = File(path).parentFile ?: return@IconButton
                    vm.extractTo(parent) { }
                }) {
                    Icon(
                        imageVector = Icons.Default.Unarchive,
                        contentDescription = "解压到此处"
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    vm.loading -> DocySkeletonList()
                    vm.error != null -> DocyEmpty(
                        title = vm.error!!,
                        message = "压缩包可能已损坏或加密"
                    )
                    vm.entries.isEmpty() -> DocyEmpty(title = "压缩包是空的")
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(vm.entries, key = { it.path }) { entry ->
                            ArchiveEntryItem(entry = entry)
                        }
                    }
                }
            }
        }
    }
}