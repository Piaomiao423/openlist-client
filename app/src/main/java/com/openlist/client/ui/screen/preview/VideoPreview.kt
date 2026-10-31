package com.openlist.client.ui.screen.preview

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.openlist.client.R
import com.openlist.client.data.remote.ApiClient
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "VideoPreview"

/**
 * 视频预览：Media3/ExoPlayer 播放。
 * 网络视频走应用内的 OkHttp 取流——系统 MediaPlayer 由媒体服务进程联网，
 * 在模拟器/部分设备上会卡在建立连接且不回调错误（表现为永久黑屏）；
 * PlayerView 使用 TextureView 而非 SurfaceView，也避免了拿不到 Surface 的情况。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoPreview(file: File) {
    VideoPlayer(Uri.fromFile(file), streamOverHttp = false)
}

/** 视频流式预览：直接播放签名直链，支持边下边播与拖动进度 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoPreview(url: String) {
    VideoPlayer(Uri.parse(url), streamOverHttp = true)
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(uri: Uri, streamOverHttp: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var failed by remember { mutableStateOf(false) }
    var slowNetwork by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    // 高码率视频在慢速网络上会长时间停留在缓冲状态，界面上需要给出解释
    LaunchedEffect(uri, streamOverHttp) {
        if (!streamOverHttp) return@LaunchedEffect
        delay(20_000)
        val p = player
        if (!failed && p != null &&
            p.playbackState == Player.STATE_BUFFERING && p.currentPosition == 0L
        ) {
            slowNetwork = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player?.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx)
                    .inflate(R.layout.view_video_player, null) as PlayerView
                val dataSourceFactory = if (streamOverHttp) {
                    // 复用 App 的连接池：全新客户端会重新做 DNS，模拟器上可能卡住数十秒
                    val client = ApiClient.sharedClient.newBuilder()
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build()
                    DefaultDataSource.Factory(ctx, OkHttpDataSource.Factory(client))
                } else {
                    DefaultDataSource.Factory(ctx)
                }
                val exoPlayer = ExoPlayer.Builder(ctx)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                    .build()
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) slowNetwork = false
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "playback error ${error.errorCodeName}", error)
                        // 解码/取流失败后控制条无意义，且会与错误提示重叠
                        view.useController = false
                        failed = true
                    }
                })
                view.player = exoPlayer
                exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                player = exoPlayer
                view
            },
            modifier = Modifier.fillMaxSize()
        )
        if (failed) {
            Text(
                "视频播放失败，请稍后重试或下载到手机观看",
                Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (slowNetwork) {
            Text(
                "加载较慢：当前网络带宽可能不足以流畅播放此视频，可在列表页下载后观看",
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 96.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
