package com.openlist.client.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
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
import com.openlist.client.util.FileTimeFormat
import com.openlist.client.util.SessionManager
import com.openlist.client.util.UploadManager
import com.openlist.client.util.UploadStatus
import com.openlist.client.util.isPreviewableFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileBrowserScreen(
    sessionManager: SessionManager,
    favoritesManager: FavoritesManager,
    uploadManager: UploadManager,
    initialPath: String = "/",
    onLogout: () -> Unit,
    onOpenPreview: (path: String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenServers: () -> Unit
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
    val treeState by viewModel.treeState.collectAsState()
    // 旧版收藏未区分服务器，登录后迁移一次
    LaunchedEffect(serverUrl) { favoritesManager.migrateLegacy(serverUrl) }
    val favoritesFlow = remember(serverUrl) { favoritesManager.favorites(serverUrl) }
    val favorites by favoritesFlow.collectAsState(initial = emptySet())

    // 目录列表滚动状态；配合 ViewModel 的 scrollMemory 实现"返回上级回到上次位置"
    val listState = rememberLazyListState()

    // 目录/搜索状态变化后恢复记住的滚动位置（仅浏览模式，搜索模式不恢复）
    LaunchedEffect(state.currentPath, state.searchMode, state.isLoading) {
        if (state.searchMode || state.isLoading) return@LaunchedEffect
        val saved = viewModel.restoreScroll(state.currentPath) ?: return@LaunchedEffect
        // 列表完成布局前 totalItemsCount 仍为 0，等有数据后再滚动
        val total = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        listState.scrollToItem(saved.first.coerceIn(0, total - 1), saved.second)
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
    var showDeleteSelected by remember { mutableStateOf(false) }
    var selectionMode by remember(serverUrl) { mutableStateOf(false) }
    var selectedNames by remember(serverUrl) { mutableStateOf<Set<String>>(emptySet()) }
    val selectedItems = remember(state.items, selectedNames) {
        state.items.filter { it.name in selectedNames }
    }
    val allSelected = state.items.isNotEmpty() && state.items.all { it.name in selectedNames }

    fun exitSelectionMode() {
        selectionMode = false
        selectedNames = emptySet()
        menuItem = null
    }

    // 切换目录或退出搜索时结束多选，避免选中项与当前列表不一致
    LaunchedEffect(state.currentPath, state.searchMode) {
        if (selectionMode) exitSelectionMode()
    }

    // 上传任务由应用级 UploadManager 维护：多选文件依次上传，离开页面后仍继续
    val allUploadTasks by uploadManager.tasks.collectAsState()
    val uploadTasks = remember(allUploadTasks, serverUrl) {
        allUploadTasks.filter { it.serverUrl == serverUrl }
    }
    val hasActiveUpload = uploadTasks.any { it.isActive }
    // 上传完成后自动刷新当前目录；清除任务后计数归零，避免下次误判
    var successSeen by remember(serverUrl) { mutableIntStateOf(0) }
    LaunchedEffect(uploadTasks.map { it.id to it.status }) {
        val successCount = uploadTasks.count { it.status == UploadStatus.SUCCESS }
        if (successCount > successSeen) viewModel.refresh()
        successSeen = successCount
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uploadManager.enqueue(repository, serverUrl, uris, state.currentPath)
            Toast.makeText(context, "已添加 ${uris.size} 个上传任务", Toast.LENGTH_SHORT).show()
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

    /** 搜索结果里 name 只是文件名，完整路径由 parent 拼接；浏览模式则由当前目录拼接 */
    fun pathOf(item: FileItem): String {
        if (state.searchMode) {
            val name = item.name
            if (name.startsWith("/")) return name
            val parent = item.parent.orEmpty().trimEnd('/')
            return if (parent.isEmpty()) "/$name" else "$parent/$name"
        }
        return fullPath(item)
    }

    BackHandler(enabled = selectionMode || state.searchMode || state.currentPath != "/") {
        when {
            selectionMode -> exitSelectionMode()
            state.searchMode -> {
                showSearch = false
                viewModel.saveScroll(state.currentPath, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                viewModel.exitSearch()
            }
            else -> {
                viewModel.saveScroll(state.currentPath, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                viewModel.goBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            selectionMode -> "已选择 ${selectedItems.size} 项"
                            state.searchMode -> "搜索：${state.searchKeyword}"
                            else -> state.currentPath
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = dims.topBarTitleSize)
                    )
                },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "退出多选")
                        }
                    } else if (state.currentPath != "/" && !state.searchMode) {
                        IconButton(onClick = {
                            viewModel.saveScroll(state.currentPath, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                            viewModel.goBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            selectedNames = if (allSelected) {
                                emptySet()
                            } else {
                                state.items.map { it.name }.toSet()
                            }
                        }) {
                            Icon(
                                if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "取消全选" else "全选"
                            )
                        }
                    } else {
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
                        IconButton(onClick = onOpenServers) {
                            Icon(Icons.Default.Dns, contentDescription = "服务器列表")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectionMode) {
                BottomAppBar {
                    Text(
                        "已选择 ${selectedItems.size} 项",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { showDeleteSelected = true },
                        enabled = selectedItems.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                Box {
                    FloatingActionButton(onClick = { showFabMenu = true }) {
                        Icon(
                            if (hasActiveUpload) Icons.Default.HourglassEmpty else Icons.Default.Add,
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
                        if (!state.searchMode) {
                            DropdownMenuItem(
                                text = { Text("多选") },
                                onClick = {
                                    showFabMenu = false
                                    menuItem = null
                                    selectionMode = true
                                    selectedNames = emptySet()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("导出目录树") },
                            onClick = {
                                showFabMenu = false
                                viewModel.exportTree()
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showSearch && !selectionMode) {
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

            if (uploadTasks.isNotEmpty()) {
                UploadPanel(
                    tasks = uploadTasks,
                    onClearFinished = { uploadManager.clearFinished(serverUrl) }
                )
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
                        // 不设置自定义 key：搜索结果可能重名，重复 key 会直接抛异常退出
                        items(state.items) { item ->
                            val selected = selectionMode && item.name in selectedNames
                            FileItemRow(
                                item = item,
                                isFavorite = !item.isDir && favorites.contains(pathOf(item)),
                                parentHint = if (state.searchMode) item.parent else null,
                                selectionMode = selectionMode,
                                selected = selected,
                                onClick = {
                                    if (selectionMode) {
                                        selectedNames = if (selected) {
                                            selectedNames - item.name
                                        } else {
                                            selectedNames + item.name
                                        }
                                    } else if (item.isDir) {
                                        if (state.searchMode) {
                                            Toast.makeText(context, "搜索模式下请到对应目录操作", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.saveScroll(state.currentPath, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                                            viewModel.openDirectory(item)
                                        }
                                    } else {
                                        val filePath = pathOf(item)
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
                                onLongClick = {
                                    when {
                                        selectionMode -> {
                                            selectedNames = if (selected) {
                                                selectedNames - item.name
                                            } else {
                                                selectedNames + item.name
                                            }
                                        }
                                        !state.searchMode -> menuItem = item
                                    }
                                },
                                menuExpanded = !selectionMode && menuItem == item,
                                onMenuDismiss = { menuItem = null },
                                onMenuPreview = {
                                    menuItem = null
                                    onOpenPreview(pathOf(item))
                                },
                                onMenuDownloadLink = {
                                    menuItem = null
                                    viewModel.getDownloadUrl(pathOf(item)) { url ->
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
                                onMenuMultiSelect = {
                                    menuItem = null
                                    selectionMode = true
                                    selectedNames = setOf(item.name)
                                },
                                onMenuFavorite = {
                                    menuItem = null
                                    if (!item.isDir) {
                                        scope.launch {
                                            favoritesManager.toggle(serverUrl, pathOf(item))
                                        }
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

    if (showDeleteSelected) {
        AlertDialog(
            onDismissRequest = { showDeleteSelected = false },
            title = { Text("删除所选") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("确定要删除选中的 ${selectedItems.size} 项吗？此操作不可恢复。")
                    if (selectedItems.any { it.isDir }) {
                        Text(
                            "文件夹及其中的内容会被一并删除。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (selectedItems.isNotEmpty()) {
                        Text(
                            selectedItems.take(5).joinToString("\n") { "• ${it.name}" } +
                                if (selectedItems.size > 5) "\n…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelected = false
                    val items = selectedItems
                    if (items.isEmpty()) return@TextButton
                    viewModel.deleteItems(items) { ok, msg ->
                        Toast.makeText(
                            context,
                            if (ok) "已删除 ${items.size} 项" else (msg ?: "删除失败"),
                            Toast.LENGTH_SHORT
                        ).show()
                        if (ok) exitSelectionMode()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelected = false }) { Text("取消") }
            }
        )
    }

    propertiesTarget?.let { item ->
        PropertiesDialog(item = item, path = fullPath(item), onDismiss = { propertiesTarget = null })
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    if (treeState.visible) {
        TreeExportDialog(
            state = treeState,
            onDismiss = { viewModel.dismissTreeExport() }
        )
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
    parentHint: String?,
    selectionMode: Boolean,
    selected: Boolean,
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
    onMenuMultiSelect: () -> Unit,
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
                val hint = listOfNotNull(
                    parentHint?.takeIf { it.isNotBlank() },
                    item.displaySize.takeIf { !item.isDir && it.isNotBlank() },
                    FileTimeFormat.display(item.modified)
                ).joinToString(" · ")
                if (hint.isNotEmpty()) {
                    Text(
                        hint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = dims.smallSize)
                    )
                }
            },
            leadingContent = {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = null,
                        modifier = Modifier.size(dims.iconSize)
                    )
                } else {
                    Icon(
                        imageVector = if (item.isDir) Icons.Default.Folder else getFileIcon(item.fileExtension),
                        contentDescription = null,
                        modifier = Modifier.size(dims.iconSize),
                        tint = if (item.isDir) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
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
                    if (item.isDir && !selectionMode) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(dims.iconSize),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
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
                text = { Text("多选") },
                onClick = onMenuMultiSelect
            )
            if (!item.isDir) {
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "取消收藏" else "收藏") },
                    onClick = onMenuFavorite
                )
            }
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
                PropertyRow("修改时间", FileTimeFormat.display(item.modified) ?: "未知")
                item.created?.takeIf { it.isNotBlank() }?.let {
                    PropertyRow("创建时间", FileTimeFormat.display(it) ?: it)
                }
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
