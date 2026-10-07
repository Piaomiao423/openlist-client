package com.openlist.client.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openlist.client.ui.theme.rememberAdaptiveDimens
import com.openlist.client.ui.viewmodel.LoginViewModel
import com.openlist.client.util.SessionManager

@Composable
fun LoginScreen(
    sessionManager: SessionManager,
    onLoginSuccess: () -> Unit
) {
    val viewModel: LoginViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LoginViewModel(sessionManager) as T
        }
    )
    val state by viewModel.uiState.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var serverUrl by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // 手表/小屏自动适配
    val dims = rememberAdaptiveDimens()

    // 输入框高对比配色：容器用主题表面色，文字用高对比 onSurface，
    // 避免浅色模式下出现“白底灰字看不清”
    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dims.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(dims.logoSpacing))
        Text(
            "OpenList",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = dims.titleSize)
        )
        Spacer(Modifier.height(dims.spacingMedium))
        Text(
            "连接你的 OpenList 网盘",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.subtitleSize)
        )
        Spacer(Modifier.height(dims.spacingLarge))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("服务器地址", style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.smallSize)) },
            placeholder = {
                Text(
                    "http://192.168.1.100:5244",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize),
            colors = tfColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dims.fieldMinHeight)
        )
        Spacer(Modifier.height(dims.spacingMedium))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名", style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.smallSize)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize),
            colors = tfColors,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dims.fieldMinHeight)
        )
        Spacer(Modifier.height(dims.spacingMedium))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码", style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.smallSize)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize),
            colors = tfColors,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { viewModel.login(serverUrl, username, password, onLoginSuccess) }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dims.fieldMinHeight)
        )

        if (state.error != null) {
            Spacer(Modifier.height(dims.spacingMedium))
            Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.bodySize)
            )
        }

        Spacer(Modifier.height(dims.spacingLarge))
        Button(
            onClick = { viewModel.login(serverUrl, username, password, onLoginSuccess) },
            enabled = !state.isLoading &&
                serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(dims.buttonHeight)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("连接", style = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize))
            }
        }

        // ===== 多账户：已保存账户快速切换 =====
        if (accounts.isNotEmpty()) {
            Spacer(Modifier.height(dims.spacingLarge))
            Text(
                "已保存账户（点击切换）",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.smallSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(dims.spacingMedium))
            accounts.forEach { account ->
                OutlinedCard(
                    onClick = { viewModel.switchAccount(account, onLoginSuccess) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                account.username,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = dims.bodySize),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                account.serverUrl,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = dims.smallSize),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { viewModel.removeAccount(account) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除账户",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
