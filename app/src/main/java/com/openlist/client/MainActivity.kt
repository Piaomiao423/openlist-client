package com.openlist.client

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openlist.client.ui.screen.FileBrowserScreen
import com.openlist.client.ui.screen.FavoritesScreen
import com.openlist.client.ui.screen.LoginScreen
import com.openlist.client.ui.screen.ServersScreen
import com.openlist.client.ui.screen.preview.FilePreviewScreen
import com.openlist.client.ui.theme.OpenListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OpenListApplication
        setContent {
            OpenListTheme {
                val authState by app.sessionManager.authState.collectAsState(initial = null)
                val activeAccount by app.sessionManager.activeAccountFlow.collectAsState(initial = null)
                when (authState) {
                    null -> {
                        // 正在读取本地会话
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        val navController = rememberNavController()
                        val currentEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = currentEntry?.destination?.route

                        fun goToServers() {
                            navController.navigate("servers") {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }

                        fun goToBrowser() {
                            navController.navigate("browser") {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }

                        // 会话失效（Token 过期/账户被删除）时回到服务器列表，
                        // 登录页仅作为“添加服务器”表单存在。
                        LaunchedEffect(authState, currentRoute) {
                            if (authState == false && currentRoute != null &&
                                currentRoute != "servers" && currentRoute != "login"
                            ) {
                                goToServers()
                            }
                        }

                        // 每次启动都先进入服务器列表，即使本地已有登录会话
                        NavHost(navController = navController, startDestination = "servers") {
                            composable("login") {
                                LoginScreen(
                                    sessionManager = app.sessionManager,
                                    onLoginSuccess = { goToBrowser() },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = "browser?path={path}",
                                arguments = listOf(
                                    navArgument("path") {
                                        type = NavType.StringType
                                        defaultValue = "/"
                                    }
                                )
                            ) { backStackEntry ->
                                // Navigation 匹配路由时已对 path 参数解码一次，这里不能再解一次
                                val pathArg = backStackEntry.arguments?.getString("path") ?: "/"
                                FileBrowserScreen(
                                    sessionManager = app.sessionManager,
                                    favoritesManager = app.favoritesManager,
                                    initialPath = pathArg,
                                    onLogout = { goToServers() },
                                    onOpenPreview = { path ->
                                        navController.navigate("preview/${Uri.encode(path)}")
                                    },
                                    onOpenFavorites = {
                                        navController.navigate("favorites")
                                    },
                                    onOpenServers = {
                                        navController.navigate("servers")
                                    }
                                )
                            }
                            composable("servers") {
                                ServersScreen(
                                    sessionManager = app.sessionManager,
                                    // 启动时服务器列表是根页面，无返回；从浏览页进入时可返回
                                    onBack = if (navController.previousBackStackEntry != null) {
                                        { navController.popBackStack() }
                                    } else {
                                        null
                                    },
                                    onSwitched = { goToBrowser() },
                                    onAddServer = { navController.navigate("login") },
                                    onLoggedOut = { goToServers() }
                                )
                            }
                            composable("preview/{filePath}") { backStackEntry ->
                                // 同上：路径参数已由 Navigation 解码，避免二次解码破坏含 % 的文件名
                                val filePath = backStackEntry.arguments?.getString("filePath") ?: "/"
                                FilePreviewScreen(
                                    sessionManager = app.sessionManager,
                                    filePath = filePath,
                                    onClose = { navController.popBackStack() }
                                )
                            }
                            composable("favorites") {
                                FavoritesScreen(
                                    favoritesManager = app.favoritesManager,
                                    serverUrl = activeAccount?.serverUrl ?: "",
                                    onBack = { navController.popBackStack() },
                                    onOpenFile = { path ->
                                        navController.navigate("preview/${Uri.encode(path)}")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
