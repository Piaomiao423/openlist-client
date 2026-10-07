package com.openlist.client.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openlist.client.BuildConfig
import com.openlist.client.data.model.FileItem
import com.openlist.client.data.repository.OpenListRepository
import com.openlist.client.data.repository.UnauthorizedException
import com.openlist.client.ui.theme.rememberAdaptiveDimens
import com.openlist.client.ui.viewmodel.FileListViewModel
import com.openlist.client.util.Downloader
import com.openlist.client.util.FavoritesManager
import com.openlist.client.util.SessionManager
import com.openlist.client.util.isPreviewableFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileBrowserScreen(
    sessionManager: SessionManager,
    favoritesManager: FavoritesManager,
    initialPath: String = "/",
    onLogout: () -> Unit,
    onOpenPreview: (path: String) -> Unit,
    onOpenFavorites: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
        LaunchedEffect(Unit) { onLogout() }
        return
    }

    val repository = remember(serverUrl, token) {
        OpenListRepository(serverUrl, token, context.cacheDir)
    }
    val viewModel: FileListViewModel = viewModel(
        key = "${serverUrl}_$initialPath",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FileListViewModel(repository, initialPath) as T
        }
    )

    val state by viewModel.uiState.collectAsState()
    val favorites by favoritesManager.favorites.collectAsState(initial = emptySet())

    // 目录列表滚动状态；配合 ViewModel 的 scrollMemory 实现"返回上级回到上次位置"
    val listState = rememberLazyListState()

    // 目录/搜索状态变化后恢复记住的滚动位置（仅浏览模式，搜索模式不恢复）
    LaunchedEffect(state.currentPath, state.searchMode) {
        if (!state.searchMode) {
            viewModel.restoreScroll(state.currentPath)?.let { (index, offset) ->
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0) listState.scrollToItem(index.coerceIn(0, total - 1), offset)
            }
        }
    }

    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTarget by remember { mutableStateOf<FileItem?>(null) }
    var propertiesTarget by remember { mutableStateOf<FileItem?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var menuItem by remember { mutableStateOf<FileItem?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }

    var uploading by remember { mutableStateOf(false) }
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                uploading = true
                // 复制到 IO 线程，大文件也不卡界面
                val file = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
                if (file != null) {
                    viewModel.uploadFile(file) { ok, msg ->
                        uploading = false
                        Toast.makeText(
                            context,
                            if (ok) "上传成功" else (msg ?: "上传失败"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    uploading = false
                    Toast.makeText(context, "无法读取所选文件", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var pendingDownload by remember { mutableStateOf<FileItem?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingDownload?.let { item ->
            pendingDownload = null
            if (granted) {
                scope.launch { doDownload(context, repository, item, state.currentPath) }
            } else {
                Toast.makeText(context, "未授予存储权限，无法下载", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startDownload(item: FileItem) {
        if (Build.VERSION.SDK_INT < 29) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingDownload = item
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        scope.launch { doDownload(context, repository, item, state.currentPath) }
    }

    fun fullPath(item: FileItem): String {
        val current = state.currentPath
        return if (current == "/") "/${item.name}" else "$current/${item.name}"
    }

    BackHandler(enabled = state.searchMode || state.currentPath != "/") {
        if (state.searchMode) {
            showSearch = false
            viewModel.saveScroll(state.currentPath, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            viewModel.exitSearch()
        } else {
            viewModel.saveScroll(state.currentPath, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            viewModel.goBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.searchMode) "搜索：${state.searchKeyword}" else state.currentPath,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = dims.topBarTitleSize)
                    )
                },
                navigationIcon = {
                    if (state.currentPath != "/" && !state.searchMode) {
                        IconButton(onClick = {
                            viewModel.saveScroll(state.currentPath, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                            viewModel.goBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (state.searchMode) {
                            showSearch = false
                            viewModel.exitSearch()
                        } else {
                            showSearch = !showSearch
                        }
                    }) {
                        Icon(
                            if (state.searchMode) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "搜索"
                        )
                    }
                    IconButton(onClick = onOpenFavorites) {
                        Icon(Icons.Default.Star, contentDescription = "收藏夹")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = "关于")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            sessionManager.logout()
                            onLogout()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "断开连接")
                    }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showFabMenu = true }) {
                    Icon(
                        if (uploading) Icons.Default.HourglassEmpty else Icons.Default.Add,
                        contentDescription = "更多操作"
                    )
                }
                DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("上传文件") },
                        onClick = {
                            showFabMenu = false
                            uploadLauncher.launch("*/*")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("新建文件夹") },
                        onClick = {
                            showFabMenu = false
                            showNewFolderDialog = true
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dims.screenPadding, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索文件名…") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search(searchQuery) }),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = dims.fieldMinHeight)
                    )
                    IconButton(onClick = { viewModel.search(searchQuery) }) {
                        Icon(Icons.Default.Search, contentDescription = "执行搜索")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null -> {
                        LaunchedEffect(state.error) {
                            if (state.error!!.contains("登录已过期") ||
                                state.error!!.contains("401")
                            ) {
                                sessionManager.logout()
                                onLogout()
                            }
                        }
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize)
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.refresh() }) {
                                Text("重试", style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize))
                            }
                        }
                    }
                    state.items.isEmpty() -> Text(
                        if (state.searchMode) "未找到匹配的文件" else "此目录为空",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize)
                    )
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(state.items, key = { "${it.name}_${it.isDir}" }) { item ->
                            FileItemRow(
                                item = item,
                                isFavorite = favorites.contains(fullPath(item)),
                                onClick = {
                                    if (item.isDir) {
                                        if (state.searchMode) {
                                            Toast.makeText(context, "搜索模式下请到对应目录操作", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.saveScroll(state.currentPath, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                                            viewModel.openDirectory(item)
                                        }
                                    } else {
                                        val filePath = if (state.searchMode) {
                                            if (item.name.startsWith("/")) item.name else "/${item.name}"
                                        } else {
                                            fullPath(item)
                                        }
                                        if (isPreviewableFile(item.fileExtension)) {
                                            onOpenPreview(filePath)
                                        } else {
                                            viewModel.getDownloadUrl(filePath) { url ->
                                                if (url != null) {
                                                    Toast.makeText(context, "下载链接: $url", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "无法获取下载链接", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                onLongClick = { if (!state.searchMode) menuItem = item },
                                menuExpanded = menuItem == item,
                                onMenuDismiss = { menuItem = null },
                                onMenuPreview = {
                                    menuItem = null
                                    onOpenPreview(fullPath(item))
                                },
                                onMenuDownloadLink = {
                                    menuItem = null
                                    viewModel.getDownloadUrl(fullPath(item)) { url ->
                                        Toast.makeText(
                                            context,
                                            if (url != null) "下载链接: $url" else "无法获取下载链接",
                                            if (url != null) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onMenuDownload = {
                                    menuItem = null
                                    startDownload(item)
                                },
                                onMenuRename = {
                                    menuItem = null
                                    renameTarget = item
                                },
                                onMenuDelete = {
                                    menuItem = null
                                    deleteTarget = item
                                },
                                onMenuProperties = {
                                    menuItem = null
                                    propertiesTarget = item
                                },
                                onMenuFavorite = {
                                    menuItem = null
                                    scope.launch {
                                        favoritesManager.toggle(fullPath(item))
                                    }
                                }
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        NameInputDialog(
            title = "新建文件夹",
            onConfirm = { name ->
                showNewFolderDialog = false
                viewModel.createFolder(name) { ok, msg ->
                    Toast.makeText(context, if (ok) "已创建" else (msg ?: "创建失败"), Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }

    renameTarget?.let { item ->
        NameInputDialog(
            title = "重命名",
            initial = item.name,
            onConfirm = { newName ->
                renameTarget = null
                if (newName != item.name) {
                    viewModel.renameItem(item, newName) { ok, msg ->
                        Toast.makeText(context, if (ok) "已重命名" else (msg ?: "重命名失败"), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除确认") },
            text = { Text("确定要删除「${item.name}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.deleteItem(item) { ok, msg ->
                        Toast.makeText(context, if (ok) "已删除" else (msg ?: "删除失败"), Toast.LENGTH_SHORT).show()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    propertiesTarget?.let { item ->
        PropertiesDialog(item = item, path = fullPath(item), onDismiss = { propertiesTarget = null })
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
private fun NameInputDialog(
    title: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun copyUriToCache(context: Context, uri: Uri): File? {
    return try {
        val resolver = context.contentResolver
        val name = resolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        } ?: "upload_${System.currentTimeMillis()}"
        val dir = File(context.cacheDir, "upload").apply { mkdirs() }
        val target = File(dir, name)
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out, 1 shl 16) } // 64KB 缓冲
        } ?: return null
        target
    } catch (e: Exception) {
        null
    }
}

private suspend fun doDownload(
    context: Context,
    repository: OpenListRepository,
    item: FileItem,
    currentPath: String
) {
    val path = if (currentPath == "/") "/${item.name}" else "$currentPath/${item.name}"
    // 下载在 IO 线程执行（OkHttp 阻塞调用），避免卡死主线程
    val cached = withContext(Dispatchers.IO) { repository.downloadToCache(path) }.fold(
        onSuccess = { it },
        onFailure = { e ->
            Toast.makeText(
                context,
                "下载失败：${e.message ?: "未知原因"}",
                Toast.LENGTH_LONG
            ).show()
            null
        }
    ) ?: return
    val uri = withContext(Dispatchers.IO) {
        Downloader.saveToDownloads(context, cached, item.name)
    }
    Toast.makeText(
        context,
        if (uri != null) "已保存到下载目录" else "保存失败（存储不可用）",
        Toast.LENGTH_SHORT
    ).show()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItemRow(
    item: FileItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onMenuPreview: () -> Unit,
    onMenuDownloadLink: () -> Unit,
    onMenuDownload: () -> Unit,
    onMenuRename: () -> Unit,
    onMenuDelete: () -> Unit,
    onMenuProperties: () -> Unit,
    onMenuFavorite: () -> Unit
) {
    val dims = rememberAdaptiveDimens()
    Box {
        ListItem(
            headlineContent = {
                Text(
                    item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize)
                )
            },
            supportingContent = {
                if (!item.isDir) {
                    Text(
                        item.displaySize,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = dims.smallSize)
                    )
                }
            },
            leadingContent = {
                Icon(
                    imageVector = if (item.isDir) Icons.Default.Folder else getFileIcon(item.fileExtension),
                    contentDescription = null,
                    modifier = Modifier.size(dims.iconSize),
                    tint = if (item.isDir) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isFavorite) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "已收藏",
                            modifier = Modifier.size(dims.iconSize),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (item.isDir) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(dims.iconSize),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            },
            modifier = Modifier
                .heightIn(min = dims.listRowMinHeight)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
            if (!item.isDir) {
                DropdownMenuItem(
                    text = { Text("预览") },
                    onClick = onMenuPreview
                )
                DropdownMenuItem(
                    text = { Text("下载链接") },
                    onClick = onMenuDownloadLink
                )
                DropdownMenuItem(
                    text = { Text("下载到手机") },
                    onClick = onMenuDownload
                )
            }
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = onMenuRename
            )
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = onMenuDelete
            )
            DropdownMenuItem(
                text = { Text("属性") },
                onClick = onMenuProperties
            )
            DropdownMenuItem(
                text = { Text(if (isFavorite) "取消收藏" else "收藏") },
                onClick = onMenuFavorite
            )
        }
    }
}

private fun getFileIcon(extension: String) = when (extension) {
    "mp4", "mkv", "avi", "mov", "webm", "3gp", "ts" -> Icons.Default.PlayCircle
    "mp3", "flac", "wav", "aac", "m4a", "ogg" -> Icons.Default.MusicNote
    "jpg", "jpeg", "png", "gif", "webp" -> Icons.Default.Image
    "pdf" -> Icons.Default.PictureAsPdf
    "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
    "doc", "docx" -> Icons.Default.Description
    "xls", "xlsx" -> Icons.Default.TableChart
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/** 文件/文件夹属性对话框 */
@Composable
private fun PropertiesDialog(item: FileItem, path: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("属性") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PropertyRow("名称", item.name)
                PropertyRow("路径", path)
                PropertyRow(
                    "类型",
                    if (item.isDir) "文件夹" else "文件（${item.fileExtension.ifBlank { "无扩展名" }}）"
                )
                if (!item.isDir) {
                    PropertyRow("大小", item.displaySize.ifBlank { "${item.size} 字节" })
                }
                PropertyRow("修改时间", item.modified.ifBlank { "未知" })
                item.created?.takeIf { it.isNotBlank() }?.let { PropertyRow("创建时间", it) }
                item.sign?.takeIf { it.isNotBlank() }?.let { PropertyRow("签名", it) }
                item.hashInfo?.takeIf { it.isNotBlank() }?.let { PropertyRow("哈希", it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 关于对话框：版本号 + 作者 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("OpenList 客户端", style = MaterialTheme.typography.titleMedium)
                Text("版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("作者：Piaomiao423")
                Text(
                    "连接自建 OpenList 服务器的 Android 文件管理客户端。\n支持浏览、预览、上传、下载、搜索与收藏。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
