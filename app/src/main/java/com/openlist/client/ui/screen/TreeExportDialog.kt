package com.openlist.client.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openlist.client.ui.theme.rememberAdaptiveDimens
import com.openlist.client.ui.viewmodel.TreeExportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_PREVIEW_LINES = 3000

/** 目录树扫描结果预览与保存（写入系统文件选择器指定的位置，可保存到手机或电脑） */
@Composable
fun TreeExportDialog(
    state: TreeExportState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dims = rememberAdaptiveDimens()
    var saving by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = state.text
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        saving = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.bufferedWriter().use { writer -> writer.write(content) }
                    } != null
                } catch (e: Exception) {
                    false
                }
            }
            saving = false
            Toast.makeText(
                context,
                if (ok) "目录树已保存" else "保存失败，请检查存储权限",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxSize(0.92f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(dims.iconSize)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "目录树",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = dims.subtitleSize)
                        )
                        Text(
                            state.rootPath,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = dims.smallSize),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                HorizontalDivider()

                when {
                    state.running -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(20.dp))
                            Text(
                                "正在递归读取目录…",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "已扫描 ${state.directories} 个文件夹、${state.files} 个文件",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.currentPath,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = dims.smallSize),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(20.dp))
                            TextButton(onClick = onDismiss) { Text("取消") }
                        }
                    }

                    state.error != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "导出失败：${state.error}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize)
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onDismiss) { Text("关闭") }
                        }
                    }

                    state.text != null -> {
                        val lines = remember(state.text) {
                            state.text.lineSequence().take(MAX_PREVIEW_LINES).toList()
                        }
                        val lineCount = remember(state.text) { state.text.count { it == '\n' } }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${state.directories} 个文件夹 · ${state.files} 个文件" +
                                    if (state.failedDirectories > 0) " · ${state.failedDirectories} 个目录读取失败" else "",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = dims.smallSize),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.truncated) {
                                Text(
                                    "结果已截断",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = dims.smallSize),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                items(lines) { line ->
                                    Text(
                                        line,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = dims.smallSize),
                                        softWrap = false,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (lineCount + 1 > MAX_PREVIEW_LINES) {
                                    item {
                                        Text(
                                            "… 仅预览前 $MAX_PREVIEW_LINES 行，保存文件包含完整内容",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = dims.smallSize),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(Modifier.weight(1f)) {
                                Text(
                                    "共 ${state.text.length} 字符",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = dims.smallSize),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = onDismiss) { Text("关闭") }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { saveLauncher.launch(defaultTreeFileName(state.rootPath)) },
                                enabled = !saving
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (saving) "保存中…" else "保存文件")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 文件名去掉各平台非法字符，默认保存为「目录名-目录树.txt」 */
internal fun defaultTreeFileName(rootPath: String): String {
    val base = rootPath.trimEnd('/').substringAfterLast('/').ifBlank { "OpenList" }
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return "$base-目录树.txt"
}
