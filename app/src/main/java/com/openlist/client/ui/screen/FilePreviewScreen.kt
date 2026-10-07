package com.openlist.client.ui.screen

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.openlist.client.data.repository.OpenListRepository
import com.openlist.client.data.repository.UnauthorizedException
import com.openlist.client.ui.theme.rememberAdaptiveDimens
import com.openlist.client.util.DARK_HIGHLIGHT
import com.openlist.client.util.HighlightPalette
import com.openlist.client.util.LIGHT_HIGHLIGHT
import com.openlist.client.util.SessionManager
import com.openlist.client.util.codeLanguageFor
import com.openlist.client.util.highlightLine
import com.openlist.client.util.isArchiveFile
import com.openlist.client.util.isAudioFile
import com.openlist.client.util.isImageFile
import com.openlist.client.util.isPdfFile
import com.openlist.client.util.isTextFile
import com.openlist.client.util.isVideoFile
import com.openlist.client.util.renderMarkdownLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.roundToInt

/** 预览界面状态 */
private sealed interface PreviewUiState {
    object Loading : PreviewUiState
    /** 直链流式预览（图片/视频/音频，与网页端一致，无需下载整个文件） */
    data class StreamReady(val url: String, val fileType: String) : PreviewUiState
    /** 已下载到本地缓存（文本/PDF/需带请求头的第三方直链） */
    data class FileReady(val file: File, val fileType: String) : PreviewUiState
    data class Error(val message: String) : PreviewUiState
}

/** 文本预览最大字节数 */
private const val MAX_TEXT_PREVIEW_BYTES = 2L * 1024 * 1024

/** 文本预览最大行数（超出截断，防止大文件卡顿） */
private const val MAX_TEXT_PREVIEW_LINES = 20000

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

    var attempt by remember(filePath) { mutableStateOf(0) }
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
        // 直链不可用或为第三方链接时才下载到缓存。
        val streamable = isImageFile(fileType) || isVideoFile(fileType) || isAudioFile(fileType)
        val info = withContext(Dispatchers.IO) { repository.getDownloadInfo(filePath) }
        info.fold(
            onSuccess = { downloadInfo ->
                // 需认证的直链（/p/ 代理路由）无法被播放器流式加载，走下载缓存
                if (streamable && !downloadInfo.requiresAuth && downloadInfo.url.startsWith(serverUrl)) {
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
                    isImageFile(state.fileType) -> ImagePreview(url = state.url)
                    isVideoFile(state.fileType) -> VideoPreview(url = state.url)
                    isAudioFile(state.fileType) -> AudioPreview(url = state.url)
                    else -> Text(
                        "暂不支持预览该类型",
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize)
                    )
                }

                is PreviewUiState.FileReady -> when {
                    isImageFile(state.fileType) -> ImagePreview(state.file)
                    isPdfFile(state.fileType) -> PdfPreview(state.file)
                    isAudioFile(state.fileType) -> AudioPreview(state.file)
                    isVideoFile(state.fileType) -> VideoPreview(state.file)
                    isTextFile(state.fileType) -> CodePreview(state.file, state.fileType)
                    isArchiveFile(state.fileType) -> ArchivePreview(state.file)
                    else -> Text(
                        "暂不支持预览该类型，可在列表页获取下载链接",
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize)
                    )
                }
            }
        }
    }
}

/**
 * 图片/GIF 预览：Coil 加载本地文件，黑色背景等比缩放居中，GIF 自动播放动画，双击两级放大。
 * 关键：按接近原图尺寸解码（上限 4096px，避免被压缩到屏幕分辨率导致放大模糊），
 * 并用高质量滤波插值消除放大锯齿。
 */
@Composable
private fun ImagePreview(file: File) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        ZoomableImage(Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .crossfade(true)
                    .size(Size(4096, 4096))
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High
            )
        }
    }
}

/** 图片/GIF 流式预览：Coil 直接加载签名直链（同网页端，支持 GIF 动图），双击两级放大 */
@Composable
private fun ImagePreview(url: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        ZoomableImage(Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .size(Size(4096, 4096))
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High
            )
        }
    }
}

/**
 * 双击缩放 + 单指平移容器：
 * - 双击：原始(1x) → 放大1(2x) → 放大2(4x) → 再双击回到原始(1x)
 * - 放大状态下单指拖动可平移查看局部，拖动范围限制在不露出黑边（以内容边缘为界）
 * - 回到原始大小时自动归位居中
 */
@Composable
private fun ZoomableImage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var level by remember { mutableIntStateOf(0) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scale by animateFloatAsState(
        targetValue = when (level) {
            0 -> 1f
            1 -> 2f
            else -> 4f
        },
        animationSpec = tween(200),
        label = "zoom"
    )
    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    // 0→1→2→0：超过两次放大后回到原始大小
                    level = (level + 1) % 3
                    if (level == 0) offset = Offset.Zero
                })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (level > 0) {
                        var newOffset = offset + dragAmount
                        // 放大后的内容超出屏幕部分允许平移，但不能拖出边界露出黑边
                        val maxX = containerSize.width * (scale - 1f) / 2f
                        val maxY = containerSize.height * (scale - 1f) / 2f
                        newOffset = Offset(
                            newOffset.x.coerceIn(-maxX, maxX),
                            newOffset.y.coerceIn(-maxY, maxY)
                        )
                        offset = newOffset
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .scale(scale)
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
        ) {
            content()
        }
    }
}

/**
 * 文本/代码预览：语法高亮 + 行号 + 等宽滚动。
 * - JSON / Python / Kotlin / Java / JS / Markdown 等按语言高亮
 * - Markdown 额外渲染标题、列表、代码块、行内样式
 * - 纯文本（log/txt/csv 等）不高亮
 */
@Composable
private fun CodePreview(file: File, extension: String) {
    val lang = codeLanguageFor(extension)
    val isDark = isSystemInDarkTheme()
    val palette = if (isDark) DARK_HIGHLIGHT else LIGHT_HIGHLIGHT

    val state by produceState<Pair<List<String>, List<AnnotatedString>>?>(
        null, file, lang, isDark
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                if (file.length() > MAX_TEXT_PREVIEW_BYTES) {
                    emptyList<String>() to listOf(AnnotatedString("文件过大（>2MB），暂不支持预览"))
                } else {
                    val bytes = file.readBytes()
                    val text = decodeSmart(bytes)
                    val rawLines = text.lines()
                    val truncated = rawLines.size > MAX_TEXT_PREVIEW_LINES
                    val lines = if (truncated) {
                        rawLines.take(MAX_TEXT_PREVIEW_LINES) + "…（仅显示前 $MAX_TEXT_PREVIEW_LINES 行）"
                    } else {
                        rawLines
                    }
                    val rendered = when {
                        lang == "markdown" -> renderMarkdownLines(lines, palette)
                        lang != null -> lines.map { highlightLine(it, lang, palette) }
                        else -> lines.map { AnnotatedString(it) }
                    }
                    lines to rendered
                }
            } catch (e: Exception) {
                emptyList<String>() to listOf(AnnotatedString("⚠ 文件读取失败（可能是二进制文件或编码不支持）"))
            }
        }
    }

    val pair = state
    if (pair == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val (lines, rendered) = pair
    if (lines.isEmpty()) {
        Text(
            text = rendered.firstOrNull()?.text ?: "此文件为空",
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    SelectionContainer {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            itemsIndexed(rendered) { idx, content ->
                CodeLine(
                    lineNumber = idx + 1,
                    content = content,
                    showNumber = lang != null
                )
            }
        }
    }
}

@Composable
private fun CodeLine(lineNumber: Int, content: AnnotatedString, showNumber: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showNumber) {
            Text(
                text = "$lineNumber",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp)
            )
        }
        Text(
            text = content,
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

/** PDF 预览：官方 PdfRenderer 逐页渲染 + 翻页 */
@Composable
private fun PdfPreview(file: File) {
    var pageIndex by remember(file) { mutableIntStateOf(0) }
    var pageCount by remember(file) { mutableIntStateOf(0) }
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }

    val renderer = remember(file) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd)
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(renderer) {
        onDispose { renderer?.close() }
    }

    LaunchedEffect(renderer, pageIndex) {
        if (renderer == null) {
            error = "无法打开 PDF"
            return@LaunchedEffect
        }
        pageCount = renderer.pageCount
        bitmap = withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(pageIndex)
                try {
                    val scale = 1.5f
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                } finally {
                    page.close()
                }
            } catch (e: Exception) {
                error = "PDF 渲染失败"
                null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF333333)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else if (error == null) {
                CircularProgressIndicator()
            } else {
                Text(error!!, color = Color.White)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                enabled = pageIndex > 0,
                onClick = { if (pageIndex > 0) pageIndex-- }
            ) { Text("上一页") }
            Text(
                "${pageIndex + 1} / ${pageCount.coerceAtLeast(1)}",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(
                enabled = pageCount > 0 && pageIndex < pageCount - 1,
                onClick = { if (pageIndex < pageCount - 1) pageIndex++ }
            ) { Text("下一页") }
        }
    }
}

/** 音频预览：播放/暂停 + 进度条（本地文件） */
@Composable
private fun AudioPreview(file: File) {
    var error by remember(file) { mutableStateOf(false) }
    var playing by remember(file) { mutableStateOf(false) }
    var duration by remember(file) { mutableLongStateOf(0L) }
    var position by remember(file) { mutableLongStateOf(0L) }

    val player = remember(file) {
        MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                if (this.duration <= 0) error = true
            } catch (e: Exception) {
                error = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (player.isPlaying) player.stop()
            } catch (e: Exception) {
            }
            player.release()
        }
    }

    LaunchedEffect(Unit) {
        try {
            duration = player.duration.toLong().coerceAtLeast(0)
        } catch (e: Exception) {
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            try {
                position = player.currentPosition.toLong().coerceAtMost(duration)
            } catch (e: Exception) {
            }
            delay(300)
        }
    }

    fun toggle() {
        if (error) return
        if (playing) {
            try {
                player.pause()
            } catch (e: Exception) {
            }
            playing = false
        } else {
            try {
                player.start()
                playing = true
            } catch (e: Exception) {
                error = true
            }
        }
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (error) {
            Text(
                "无法播放该音频",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Slider(
            value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
            onValueChange = { pos ->
                position = pos.toLong()
                try {
                    player.seekTo(position.toInt())
                } catch (e: Exception) {
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
        Text(
            "${formatTime(position)} / ${formatTime(duration)}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(24.dp))
        IconButton(onClick = { toggle() }, modifier = Modifier.size(72.dp)) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "暂停" else "播放",
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

/** 视频预览：VideoView 内嵌播放（自带控制器） */
@Composable
private fun VideoPreview(file: File) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoPath(file.absolutePath)
                setMediaController(MediaController(ctx))
                requestFocus()
                setOnPreparedListener { mp -> mp.isLooping = false; start() }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/** 视频流式预览：VideoView 直接播放签名直链（支持拖动进度，同网页端） */
@Composable
private fun VideoPreview(url: String) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(android.net.Uri.parse(url))
                setMediaController(MediaController(ctx))
                requestFocus()
                setOnPreparedListener { mp -> mp.isLooping = false; start() }
                setOnErrorListener { _, _, _ -> true }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/** 音频流式预览：MediaPlayer 异步加载签名直链（可拖动进度） */
@Composable
private fun AudioPreview(url: String) {
    var error by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    var ready by remember(url) { mutableStateOf(false) }
    var duration by remember(url) { mutableLongStateOf(0L) }
    var position by remember(url) { mutableLongStateOf(0L) }

    val player = remember(url) {
        MediaPlayer().apply {
            try {
                setDataSource(url)
                setOnPreparedListener { mp ->
                    duration = mp.duration.toLong().coerceAtLeast(0)
                    ready = true
                    try {
                        mp.start()
                        playing = true
                    } catch (e: Exception) {
                    }
                }
                setOnErrorListener { _, _, _ -> error = true; true }
                prepareAsync()
            } catch (e: Exception) {
                error = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (player.isPlaying) player.stop()
            } catch (e: Exception) {
            }
            try {
                player.release()
            } catch (e: Exception) {
            }
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            try {
                position = player.currentPosition.toLong().coerceAtMost(duration)
            } catch (e: Exception) {
            }
            delay(300)
        }
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (error) {
            Text(
                "无法播放该音频",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }
        if (!ready) {
            CircularProgressIndicator()
            return@Column
        }
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Slider(
            value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
            onValueChange = { pos ->
                position = pos.toLong()
                try {
                    player.seekTo(position.toInt())
                } catch (e: Exception) {
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
        Text(
            "${formatTime(position)} / ${formatTime(duration)}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(24.dp))
        IconButton(onClick = {
            if (playing) {
                try {
                    player.pause()
                } catch (e: Exception) {
                }
                playing = false
            } else {
                try {
                    player.start()
                    playing = true
                } catch (e: Exception) {
                }
            }
        }, modifier = Modifier.size(72.dp)) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "暂停" else "播放",
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

/**
 * 压缩包预览：列出 zip/jar/apk 内的文件条目（名称 + 大小）。
 * 大包仅显示前 5000 项，避免卡顿。
 */
@Composable
private fun ArchivePreview(file: File) {
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
            itemsIndexed(list) { index, (name, size) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (name.endsWith("/")) Icons.Default.Folder
                        else Icons.Default.InsertDriveFile,
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
                    if (!name.endsWith("/")) {
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

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

/**
 * 智能解码文本字节：
 * 1. UTF-8 BOM / UTF-16 BOM 直接按对应编码
 * 2. 严格校验 UTF-8（存在非法序列则放弃）
 * 3. 回退 GB18030（覆盖 GBK/GB2312 中文场景）
 * 4. 最后 ISO-8859-1 保底（不丢字节）
 */
private fun decodeSmart(bytes: ByteArray): String {
    // UTF-8 BOM
    if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    ) {
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    // UTF-16 BOM
    if (bytes.size >= 2 &&
        ((bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) ||
            (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()))
    ) {
        return String(bytes, Charsets.UTF_16)
    }
    // 严格 UTF-8
    try {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        // 不是合法 UTF-8，尝试中文编码
    }
    return try {
        String(bytes, Charset.forName("GB18030"))
    } catch (e: Exception) {
        String(bytes, Charsets.ISO_8859_1)
    }
}
