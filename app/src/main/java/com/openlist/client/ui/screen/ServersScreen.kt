package com.openlist.client.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openlist.client.ui.theme.rememberAdaptiveDimens
import com.openlist.client.util.Account
import com.openlist.client.util.SessionManager
import kotlinx.coroutines.launch

/**
 * 服务器列表：展示已保存的服务器/账户，点击即可切换（复用已保存的 Token）。
 * 同时提供添加服务器、删除服务器与退出当前登录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    sessionManager: SessionManager,
    onBack: (() -> Unit)?,
    onSwitched: () -> Unit,
    onAddServer: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val dims = rememberAdaptiveDimens()
    val scope = rememberCoroutineScope()
    val accounts by sessionManager.accountsFlow.collectAsState(initial = null)
    val active by sessionManager.activeAccountFlow.collectAsState(initial = null)

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Account?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    fun switchTo(account: Account) {
        if (busy) return
        // 当前服务器也允许点击，用于从服务器列表进入浏览页
        if (account.id == active?.id) {
            onSwitched()
            return
        }
        busy = true
        error = null
        scope.launch {
            val ok = sessionManager.switchAccount(account.serverUrl, account.username)
            busy = false
            if (ok) onSwitched() else error = "服务器已失效，请重新登录"
        }
    }

    fun deleteAccount(account: Account) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val wasActive = account.id == active?.id
            sessionManager.removeAccount(account.serverUrl, account.username)
            busy = false
            if (wasActive) onLoggedOut()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "服务器",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = dims.topBarTitleSize)
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onAddServer, enabled = !busy) {
                        Icon(Icons.Default.Add, contentDescription = "添加服务器")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Text(
                if (active != null) "点击当前服务器进入，点击其他服务器切换（无需重新登录）" else "选择一个已保存的服务器，或添加新服务器",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.smallSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dims.screenPadding, vertical = dims.spacingMedium)
            )
            if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize),
                    modifier = Modifier.padding(horizontal = dims.screenPadding)
                )
            }

            val list = accounts
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (list.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dims.screenPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "暂无已保存的服务器",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize)
                    )
                    Spacer(Modifier.height(dims.spacingMedium))
                    OutlinedButton(
                        onClick = onAddServer,
                        modifier = Modifier.heightIn(min = dims.buttonHeight)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("添加服务器", style = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(dims.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(dims.spacingMedium)
                ) {
                    items(list, key = { it.id }) { account ->
                        val isActive = account.id == active?.id
                        Card(
                            onClick = { switchTo(account) },
                            enabled = !busy,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        account.username.ifBlank { "未命名账户" },
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        account.serverUrl,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = dims.smallSize),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        if (isActive) Icons.Default.CheckCircle else Icons.Default.Dns,
                                        contentDescription = null,
                                        modifier = Modifier.size(dims.iconSize),
                                        tint = if (isActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        }
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isActive) {
                                            Text(
                                                "当前",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = dims.smallSize)
                                            )
                                        }
                                        IconButton(
                                            onClick = { deleteTarget = account },
                                            enabled = !busy
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "删除服务器",
                                                modifier = Modifier.size(dims.iconSize),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(dims.spacingMedium))
                        OutlinedButton(
                            onClick = onAddServer,
                            enabled = !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = dims.buttonHeight)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("添加服务器", style = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize))
                        }
                    }
                    if (active != null) {
                        item {
                            TextButton(
                                onClick = { showLogoutConfirm = true },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "退出当前服务器",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除服务器？") },
            text = {
                Text("将移除 ${account.serverUrl} 上的账户「${account.username}」，下次使用需要重新登录。")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    deleteAccount(account)
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出当前服务器？") },
            text = { Text("将清除当前服务器的登录信息，其他已保存服务器不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    if (!busy) {
                        busy = true
                        scope.launch {
                            sessionManager.logout()
                            busy = false
                            onLoggedOut()
                        }
                    }
                }) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            }
        )
    }
}