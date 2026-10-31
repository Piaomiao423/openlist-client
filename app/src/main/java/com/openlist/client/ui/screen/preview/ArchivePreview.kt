package com.openlist.client.ui.screen.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

/**
 * 压缩包预览：列出 zip/jar/apk 内的文件条目（名称 + 大小）。
 * 大包仅显示前 5000 项，避免卡顿。
 */
@Composable
internal fun ArchivePreview(file: File) {
    val entries by produceState<List<Pair<String, Long>>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            try {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence()
                        .take(5000)
                        .map { it.name to it.size }
                        .toList()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    val list = entries
    when {
        list == null -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("无法打开压缩包（文件损坏或格式不支持）", color = MaterialTheme.colorScheme.error)
        }
        list.isEmpty() -> Text(
            "压缩包为空",
            Modifier.fillMaxSize().wrapContentHeight(Alignment.CenterVertically),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        else -> LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(list) { _, (name, size) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (name.endsWith("/")) Icons.Default.Folder
                        else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                    if (!name.endsWith("/") && size >= 0) {
                        Text(
                            formatSize(size),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
            if (list.size == 5000) {
                item {
                    Text(
                        "… 仅显示前 5000 项",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun formatSize(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
    size < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", size / (1024.0 * 1024 * 1024))
}
