package com.openlist.client.ui.screen.preview

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

/** 音频预览：播放/暂停 + 进度条（本地文件） */
@Composable
internal fun AudioPreview(file: File) {
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
        AudioControls(
            playing = playing,
            position = position,
            duration = duration,
            onSeek = { pos ->
                position = pos
                try {
                    player.seekTo(position.toInt())
                } catch (e: Exception) {
                }
            },
            onToggle = { toggle() }
        )
    }
}

/** 音频流式预览：MediaPlayer 异步加载签名直链（可拖动进度） */
@Composable
internal fun AudioPreview(url: String) {
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
        AudioControls(
            playing = playing,
            position = position,
            duration = duration,
            onSeek = { pos ->
                position = pos
                try {
                    player.seekTo(position.toInt())
                } catch (e: Exception) {
                }
            },
            onToggle = {
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
            }
        )
    }
}

@Composable
private fun AudioControls(
    playing: Boolean,
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
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
        value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
        onValueChange = { pos -> onSeek(pos.toLong()) },
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
