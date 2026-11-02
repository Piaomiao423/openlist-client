package com.openlist.client.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openlist.client.ui.theme.rememberAdaptiveDimens
import com.openlist.client.util.TransferFormat
import com.openlist.client.util.UploadStatus
import com.openlist.client.util.UploadTask

/** 文件浏览页顶部的上传任务面板：展示每个文件的进度、速度和预计剩余时间 */
@Composable
fun UploadPanel(
    tasks: List<UploadTask>,
    onClearFinished: () -> Unit
) {
    val dims = rememberAdaptiveDimens()
    var expanded by rememberSaveable { mutableStateOf(true) }
    val activeTasks = tasks.filter { it.isActive }
    val finishedCount = tasks.count { it.isFinished }
    val failedCount = tasks.count { it.status == UploadStatus.FAILED }
    val totalSpeed = activeTasks.filter { it.status == UploadStatus.UPLOADING }
        .sumOf { it.speedBytesPerSec }

    LaunchedEffect(activeTasks.size) {
        if (activeTasks.isNotEmpty()) expanded = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dims.screenPadding, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(dims.iconSize),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (activeTasks.isNotEmpty()) "正在上传 ${activeTasks.size} 个文件" else "上传任务",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = dims.bodySize),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildSummary(activeTasks.size, finishedCount, failedCount, totalSpeed),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = dims.smallSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (finishedCount > 0) {
                    TextButton(
                        onClick = onClearFinished,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            "清除已完成",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = dims.smallSize)
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起上传列表" else "展开上传列表"
                    )
                }
            }
            if (expanded) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        UploadTaskRow(task)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private fun buildSummary(
    activeCount: Int,
    finishedCount: Int,
    failedCount: Int,
    totalSpeed: Long
): String {
    if (activeCount > 0) {
        val parts = mutableListOf("$activeCount 个进行中")
        if (totalSpeed > 0) parts += "共 ${TransferFormat.speed(totalSpeed)}"
        if (finishedCount > 0) parts += "$finishedCount 个已结束"
        return parts.joinToString(" · ")
    }
    return when {
        failedCount > 0 -> "$finishedCount 个已结束，其中 $failedCount 个失败"
        finishedCount > 0 -> "全部完成 · $finishedCount 个文件"
        else -> "等待开始"
    }
}

@Composable
private fun UploadTaskRow(task: UploadTask) {
    val dims = rememberAdaptiveDimens()
    val statusColor = when (task.status) {
        UploadStatus.FAILED -> MaterialTheme.colorScheme.error
        UploadStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                task.status.icon(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = statusColor
            )
            Spacer(Modifier.width(8.dp))
            Text(
                task.fileName,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (task.status == UploadStatus.UPLOADING && task.totalBytes > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "${task.uploadedBytes * 100 / task.totalBytes}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = dims.smallSize),
                    color = statusColor
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        when (task.status) {
            UploadStatus.UPLOADING, UploadStatus.PREPARING, UploadStatus.VERIFYING -> {
                val fraction = if (
                    (task.status == UploadStatus.UPLOADING || task.status == UploadStatus.PREPARING) &&
                    task.totalBytes > 0
                ) {
                    (task.uploadedBytes.toFloat() / task.totalBytes).coerceIn(0f, 1f)
                } else {
                    null
                }
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
            }
            UploadStatus.SUCCESS -> {
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
                Spacer(Modifier.height(3.dp))
            }
            else -> Unit
        }
        Text(
            task.detailText(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = dims.smallSize),
            color = if (task.status == UploadStatus.FAILED) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun UploadTask.detailText(): String = when (status) {
    UploadStatus.QUEUED -> "排队等待上传"
    UploadStatus.PREPARING -> if (totalBytes > 0) {
        "准备中 · 已读取 ${TransferFormat.bytes(uploadedBytes)} / ${TransferFormat.bytes(totalBytes)}"
    } else {
        "准备中…"
    }
    UploadStatus.UPLOADING -> {
        if (totalBytes <= 0) {
            "正在上传…"
        } else {
            val speedText = if (speedBytesPerSec > 0) {
                TransferFormat.speed(speedBytesPerSec)
            } else {
                "计算速度中"
            }
            val etaText = etaSeconds?.let { "剩余 ${TransferFormat.duration(it)}" } ?: "剩余时间计算中"
            "$speedText · $etaText"
        }
    }
    UploadStatus.VERIFYING -> "发送完成，正在等待服务器校验…"
    UploadStatus.SUCCESS -> "已完成 · ${TransferFormat.bytes(totalBytes)}"
    UploadStatus.FAILED -> "失败：${error ?: "未知错误"}"
}

private fun UploadStatus.icon(): ImageVector = when (this) {
    UploadStatus.QUEUED -> Icons.Default.Schedule
    UploadStatus.PREPARING -> Icons.Default.HourglassEmpty
    UploadStatus.UPLOADING -> Icons.Default.CloudUpload
    UploadStatus.VERIFYING -> Icons.Default.HourglassEmpty
    UploadStatus.SUCCESS -> Icons.Default.CheckCircle
    UploadStatus.FAILED -> Icons.Default.Error
}
