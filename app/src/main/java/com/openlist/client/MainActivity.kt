package com.openlist.client

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openlist.client.ui.screen.FileBrowserScreen
import com.openlist.client.ui.screen.FilePreviewScreen
import com.openlist.client.ui.screen.FavoritesScreen
import com.openlist.client.ui.screen.LoginScreen
import com.openlist.client.ui.theme.OpenListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OpenListApplication
        setContent {
            OpenListTheme {
                val authState by app.sessionManager.authState.collectAsState(initial = null)
                when (authState) {
                    null -> {
                        // 正在读取本地会话
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        val navController = rememberNavController()
                        val start = if (authState == true) "browser" else "login"
                        NavHost(navController = navController, startDestination = start) {
                            composable("login") {
                                LoginScreen(
                                    sessionManager = app.sessionManager,
                                    onLoginSuccess = {
                                        navController.navigate("browser") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
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
                                val rawPath = backStackEntry.arguments?.getString("path") ?: "/"
                                FileBrowserScreen(
                                    sessionManager = app.sessionManager,
                                    favoritesManager = app.favoritesManager,
                                    initialPath = Uri.decode(rawPath),
                                    onLogout = {
                                        navController.navigate("login") {
                                            popUpTo("browser") { inclusive = true }
                                        }
                                    },
                                    onOpenPreview = { path ->
                                        navController.navigate("preview/${Uri.encode(path)}")
                                    },
                                    onOpenFavorites = {
                                        navController.navigate("favorites")
                                    }
                                )
                            }
                            composable("preview/{filePath}") { backStackEntry ->
                                val raw = backStackEntry.arguments?.getString("filePath") ?: "/"
                                FilePreviewScreen(
                                    sessionManager = app.sessionManager,
                                    filePath = Uri.decode(raw),
                                    onClose = { navController.popBackStack() }
                                )
                            }
                            composable("favorites") {
                                FavoritesScreen(
                                    favoritesManager = app.favoritesManager,
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
