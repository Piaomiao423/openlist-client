package com.openlist.client.ui.screen.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.openlist.client.data.remote.CleartextGuard
import com.openlist.client.data.repository.OpenListRepository
import com.openlist.client.data.repository.UnauthorizedException
import com.openlist.client.ui.theme.rememberAdaptiveDimens
import com.openlist.client.util.SessionManager
import com.openlist.client.util.isArchiveFile
import com.openlist.client.util.isAudioFile
import com.openlist.client.util.isImageFile
import com.openlist.client.util.isPdfFile
import com.openlist.client.util.isTextFile
import com.openlist.client.util.isVideoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 预览界面状态 */
private sealed interface PreviewUiState {
    object Loading : PreviewUiState
    /** 直链流式预览（图片/视频/音频，与网页端一致，无需下载整个文件） */
    data class StreamReady(val url: String, val fileType: String) : PreviewUiState
    /** 已下载到本地缓存（文本/PDF/需带请求头的第三方直链） */
    data class FileReady(val file: File, val fileType: String) : PreviewUiState
    data class Error(val message: String) : PreviewUiState
}

/**
 * 全屏文件预览：
 * - 图片/GIF：Coil 加载（GIF 动图自动播放）
 * - 文本：等宽字体滚动
 * - PDF：官方 PdfRenderer 逐页渲染
 * - 音频：MediaPlayer 播放/暂停/进度
 * - 视频：VideoView 内嵌播放
 * 其他类型提示不支持（列表页仍可获取下载链接）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    sessionManager: SessionManager,
    filePath: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val dims = rememberAdaptiveDimens()

    val session by produceState<Pair<String, String>?>(initialValue = null) {
        val url = sessionManager.getServerUrl()
        val token = sessionManager.getToken()
        value = if (url.isNotBlank() && token.isNotBlank()) url to token else "" to ""
    }
    val s = session
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val (serverUrl, token) = s
    if (serverUrl.isBlank() || token.isBlank()) {
        // 会话缺失，退回上一页
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val repository = remember(serverUrl, token) {
        OpenListRepository(serverUrl, token, context.cacheDir)
    }

    var attempt by remember(filePath) { mutableIntStateOf(0) }
    val fileType = filePath.substringAfterLast('.').lowercase()
    val uiState by produceState<PreviewUiState>(
        initialValue = PreviewUiState.Loading,
        key1 = filePath,
        key2 = attempt
    ) {
        suspend fun onFail(e: Throwable) {
            if (e is UnauthorizedException) {
                sessionManager.logout()
                onClose()
                value = PreviewUiState.Error(e.message ?: "登录已过期")
            } else {
                value = PreviewUiState.Error(e.message ?: "加载失败")
            }
        }
        // 图片/视频/音频：优先用 /api/fs/link 的签名直链流式加载（网页端同款，大文件秒开）；
        // 直链不可用、为第三方链接或公网明文地址时才下载到缓存。
        val streamable = isImageFile(fileType) || isVideoFile(fileType) || isAudioFile(fileType)
        val info = withContext(Dispatchers.IO) { repository.getDownloadInfo(filePath) }
        info.fold(
            onSuccess = { downloadInfo ->
                // 需认证的直链（/p/ 代理路由）无法被播放器流式加载，走下载缓存；
                // 公网 HTTP 直链会被媒体组件绕过 OkHttp，因此这里同样按明文策略拦截。
                val streamAllowed = streamable && !downloadInfo.requiresAuth &&
                    downloadInfo.url.startsWith(serverUrl) &&
                    CleartextGuard.isAllowed(downloadInfo.url)
                if (streamAllowed) {
                    value = PreviewUiState.StreamReady(downloadInfo.url, fileType)
                } else {
                    withContext(Dispatchers.IO) { repository.downloadToCache(filePath) }.fold(
                        onSuccess = { file -> value = PreviewUiState.FileReady(file, fileType) },
                        onFailure = { onFail(it) }
                    )
                }
            },
            onFailure = { onFail(it) }
        )
    }

    val fileName = filePath.substringAfterLast('/')

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = dims.topBarTitleSize)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is PreviewUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is PreviewUiState.Error -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { attempt++ }) {
                        Text("重试", style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize))
                    }
                }

                is PreviewUiState.StreamReady -> when {
                    isImageFile(state.fileType) -> ImagePreview(state.url)
                    isVideoFile(state.fileType) -> VideoPreview(state.url)
                    isAudioFile(state.fileType) -> AudioPreview(state.url)
                    else -> UnsupportedHint(dims.bodySize)
                }

                is PreviewUiState.FileReady -> when {
                    isImageFile(state.fileType) -> ImagePreview(state.file)
                    isPdfFile(state.fileType) -> PdfPreview(state.file)
                    isAudioFile(state.fileType) -> AudioPreview(state.file)
                    isVideoFile(state.fileType) -> VideoPreview(state.file)
                    isTextFile(state.fileType) -> CodePreview(state.file, state.fileType)
                    isArchiveFile(state.fileType) -> ArchivePreview(state.file)
                    else -> UnsupportedHint(dims.bodySize)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.UnsupportedHint(fontSize: TextUnit) {
    Text(
        "暂不支持预览该类型，可在列表页获取下载链接",
        Modifier.align(Alignment.Center),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize)
    )
}
