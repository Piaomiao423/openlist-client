package com.openlist.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openlist.client.ui.theme.rememberAdaptiveDimens
import com.openlist.client.util.FavoritesManager
import kotlinx.coroutines.launch

/**
 * 收藏夹页：列出本地收藏的文件路径，点击直接预览。
 * 收藏入口在文件浏览页的长按菜单（仅文件可收藏）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favoritesManager: FavoritesManager,
    onBack: () -> Unit,
    onOpenFile: (path: String) -> Unit
) {
    val favorites by favoritesManager.favorites.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    val dims = rememberAdaptiveDimens()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "收藏夹",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = dims.topBarTitleSize)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        val sorted = favorites.sortedBy { it }
        if (sorted.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无收藏\n长按文件选择「收藏」后，会出现在这里",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize)
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(sorted, key = { it }) { path ->
                    val name = path.substringAfterLast('/')
                    ListItem(
                        headlineContent = {
                            Text(
                                name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize)
                            )
                        },
                        supportingContent = {
                            Text(
                                path,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = dims.smallSize)
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                modifier = Modifier.size(dims.iconSize),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch { favoritesManager.remove(path) }
                            }) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "取消收藏",
                                    modifier = Modifier.size(dims.iconSize),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = dims.listRowMinHeight)
                            .clickable { onOpenFile(path) }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}
