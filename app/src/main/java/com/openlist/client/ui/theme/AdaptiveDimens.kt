package com.openlist.client.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 自适应界面尺寸参数。
 *
 * 根据屏幕可用宽度自动选择两档：
 * - 紧凑档（Compact）：Wear OS 手表等小屏设备（宽度 < 360dp）
 * - 标准档（Standard）：手机/平板（宽度 >= 360dp）
 *
 * 字号、间距、控件高度、图标尺寸统一走这套参数，
 * 小屏自动缩放，避免溢出、挤压与点击区域过小的问题。
 */
@Stable
data class AdaptiveDimens(
    val isCompact: Boolean,
    /** 页面左右留白 */
    val screenPadding: Dp,
    /** 大间距（区块间隔） */
    val spacingLarge: Dp,
    /** 小间距（控件间隔） */
    val spacingMedium: Dp,
    /** 主标题字号 */
    val titleSize: TextUnit,
    /** 副标题字号 */
    val subtitleSize: TextUnit,
    /** 正文/输入文字号 */
    val bodySize: TextUnit,
    /** 辅助文字字号（label、说明、文件大小） */
    val smallSize: TextUnit,
    /** 输入框最小高度 */
    val fieldMinHeight: Dp,
    /** 按钮高度 */
    val buttonHeight: Dp,
    /** 列表图标尺寸 */
    val iconSize: Dp,
    /** 列表行最小高度 */
    val listRowMinHeight: Dp,
    /** 顶部栏标题字号 */
    val topBarTitleSize: TextUnit,
    /** 登录页 Logo 区留白 */
    val logoSpacing: Dp
) {
    companion object {
        /** 标准档：手机/平板 */
        val Standard = AdaptiveDimens(
            isCompact = false,
            screenPadding = 24.dp,
            spacingLarge = 32.dp,
            spacingMedium = 12.dp,
            titleSize = 28.sp,
            subtitleSize = 16.sp,
            bodySize = 16.sp,
            smallSize = 12.sp,
            fieldMinHeight = 56.dp,
            buttonHeight = 48.dp,
            iconSize = 24.dp,
            listRowMinHeight = 56.dp,
            topBarTitleSize = 22.sp,
            logoSpacing = 64.dp
        )

        /** 紧凑档：Wear OS 手表等小屏 */
        val Compact = AdaptiveDimens(
            isCompact = true,
            screenPadding = 16.dp,
            spacingLarge = 16.dp,
            spacingMedium = 8.dp,
            titleSize = 20.sp,
            subtitleSize = 13.sp,
            bodySize = 14.sp,
            smallSize = 11.sp,
            fieldMinHeight = 48.dp,
            buttonHeight = 44.dp,
            iconSize = 20.dp,
            listRowMinHeight = 44.dp,
            topBarTitleSize = 16.sp,
            logoSpacing = 28.dp
        )
    }
}

/** 获取当前设备的自适应尺寸档位（按屏幕宽度自动切换） */
@Composable
fun rememberAdaptiveDimens(): AdaptiveDimens {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp) {
        if (configuration.screenWidthDp < 360) AdaptiveDimens.Compact else AdaptiveDimens.Standard
    }
}
