package com.openlist.client.ui.screen.preview

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/** 视频预览：VideoView 内嵌播放（自带控制器） */
@Composable
internal fun VideoPreview(file: File) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoPath(file.absolutePath)
                setMediaController(MediaController(ctx))
                requestFocus()
                setOnPreparedListener { mp -> mp.isLooping = false; start() }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/** 视频流式预览：VideoView 直接播放签名直链（支持拖动进度，同网页端） */
@Composable
internal fun VideoPreview(url: String) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(android.net.Uri.parse(url))
                setMediaController(MediaController(ctx))
                requestFocus()
                setOnPreparedListener { mp -> mp.isLooping = false; start() }
                setOnErrorListener { _, _, _ -> true }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
