package com.openlist.client.ui.screen.preview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import java.io.File
import kotlin.math.roundToInt

/**
 * 图片/GIF 预览：Coil 加载本地文件，黑色背景等比缩放居中，GIF 自动播放动画，双击两级放大。
 * 关键：按接近原图尺寸解码（上限 4096px，避免被压缩到屏幕分辨率导致放大模糊），
 * 并用高质量滤波插值消除放大锯齿。
 */
@Composable
internal fun ImagePreview(file: File) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        ZoomableImage(Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .crossfade(true)
                    .size(Size(4096, 4096))
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High
            )
        }
    }
}

/** 图片/GIF 流式预览：Coil 直接加载签名直链（同网页端，支持 GIF 动图），双击两级放大 */
@Composable
internal fun ImagePreview(url: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        ZoomableImage(Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .size(Size(4096, 4096))
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High
            )
        }
    }
}

/**
 * 双击缩放 + 单指平移容器：
 * - 双击：原始(1x) → 放大1(2x) → 放大2(4x) → 再双击回到原始(1x)
 * - 放大状态下单指拖动可平移查看局部，拖动范围限制在不露出黑边（以内容边缘为界）
 * - 回到原始大小时自动归位居中
 */
@Composable
private fun ZoomableImage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var level by remember { mutableIntStateOf(0) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scale by animateFloatAsState(
        targetValue = when (level) {
            0 -> 1f
            1 -> 2f
            else -> 4f
        },
        animationSpec = tween(200),
        label = "zoom"
    )
    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    // 0→1→2→0：超过两次放大后回到原始大小
                    level = (level + 1) % 3
                    if (level == 0) offset = Offset.Zero
                })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (level > 0) {
                        var newOffset = offset + dragAmount
                        // 放大后的内容超出屏幕部分允许平移，但不能拖出边界露出黑边
                        val maxX = containerSize.width * (scale - 1f) / 2f
                        val maxY = containerSize.height * (scale - 1f) / 2f
                        newOffset = Offset(
                            newOffset.x.coerceIn(-maxX, maxX),
                            newOffset.y.coerceIn(-maxY, maxY)
                        )
                        offset = newOffset
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .scale(scale)
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
        ) {
            content()
        }
    }
}
