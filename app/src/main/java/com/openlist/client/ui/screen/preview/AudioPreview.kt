package com.openlist.client.ui.screen.preview

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * 音频预览：播放/暂停 + 拖动进度。
 *
 * 只播放已下载到本地的缓存文件，不把网络直链交给 MediaPlayer：
 * 部分服务器的签名直链/重定向会让 MediaPlayer 原生层直接异常退出（闪退），
 * 下载后本地播放可绕开整条网络播放链路。
 */
@Composable
internal fun AudioPreview(file: File) {
    var error by remember(file) { mutableStateOf<String?>(null) }
    var ready by remember(file) { mutableStateOf(false) }
    var playing by remember(file) { mutableStateOf(false) }
    var completed by remember(file) { mutableStateOf(false) }
    var duration by remember(file) { mutableLongStateOf(0L) }
    var position by remember(file) { mutableLongStateOf(0L) }
    var seeking by remember(file) { mutableStateOf(false) }
    var seekTarget by remember(file) { mutableLongStateOf(0L) }

    val player = remember(file) {
        MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setOnPreparedListener { mp ->
                    duration = runCatching { mp.duration.toLong() }.getOrDefault(0L).coerceAtLeast(0L)
                    ready = true
                    completed = false
                    position = 0L
                    runCatching {
                        mp.start()
                        playing = true
                    }.onFailure {
                        // 解码/输出失败只影响本页，不能让异常抛到主线程
                        error = "无法播放该音频（解码失败）"
                    }
                }
                setOnCompletionListener {
                    playing = false
                    completed = true
                    position = duration
                }
                setOnErrorListener { _, what, extra ->
                    playing = false
                    error = "无法播放该音频（错误码 $what/$extra）"
                    true
                }
                setDataSource(file.absolutePath)
                prepareAsync()
            } catch (e: Exception) {
                error = "无法播放该音频"
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
    }

    // 播放中轮询进度；拖动进度条时暂停轮询，避免滑块被回弹
    LaunchedEffect(playing, seeking) {
        while (playing && !seeking) {
            runCatching { player.currentPosition.toLong() }.getOrNull()?.let { pos ->
                position = if (duration > 0L) pos.coerceIn(0L, duration) else pos.coerceAtLeast(0L)
            }
            delay(300)
        }
    }

    fun toggle() {
        if (error != null || !ready) return
        if (playing) {
            runCatching { player.pause() }
            playing = false
        } else {
            runCatching {
                if (completed || (duration > 0L && position >= duration)) {
                    player.seekTo(0)
                    position = 0L
                }
                player.start()
                playing = true
                completed = false
            }.onFailure {
                error = "无法播放该音频"
            }
        }
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val message = error
        if (message != null) {
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }
        if (!ready) {
            CircularProgressIndicator()
            return@Column
        }
        val displayPosition = if (seeking) seekTarget else position
        val max = duration.coerceAtLeast(1L)
        AudioControls(
            playing = playing,
            position = displayPosition,
            duration = duration,
            enabled = duration > 0L,
            onSeekStart = { value ->
                seeking = true
                seekTarget = value
            },
            onSeekFinished = {
                val target = seekTarget.coerceIn(0L, duration)
                position = target
                seeking = false
                completed = false
                runCatching { player.seekTo(target.toInt()) }
            },
            onToggle = { toggle() },
            sliderMax = max
        )
    }
}

@Composable
private fun AudioControls(
    playing: Boolean,
    position: Long,
    duration: Long,
    enabled: Boolean,
    sliderMax: Long,
    onSeekStart: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onToggle: () -> Unit
) {
    Icon(
        Icons.Default.MusicNote,
        contentDescription = null,
        modifier = Modifier.size(96.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(24.dp))
    Slider(
        value = position.toFloat().coerceIn(0f, sliderMax.toFloat()),
        onValueChange = { onSeekStart(it.toLong()) },
        onValueChangeFinished = onSeekFinished,
        valueRange = 0f..sliderMax.toFloat(),
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    )
    Text(
        "${formatTime(position)} / ${formatTime(duration)}",
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(24.dp))
    IconButton(onClick = onToggle, modifier = Modifier.size(72.dp)) {
        Icon(
            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playing) "暂停" else "播放",
            modifier = Modifier.size(48.dp)
        )
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
