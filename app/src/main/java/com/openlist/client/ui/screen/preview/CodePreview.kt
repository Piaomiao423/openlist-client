package com.openlist.client.ui.screen.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlist.client.util.DARK_HIGHLIGHT
import com.openlist.client.util.LIGHT_HIGHLIGHT
import com.openlist.client.util.codeLanguageFor
import com.openlist.client.util.highlightLine
import com.openlist.client.util.renderMarkdownLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 文本预览最大字节数 */
private const val MAX_TEXT_PREVIEW_BYTES = 2L * 1024 * 1024

/** 文本预览最大行数（超出截断，防止大文件卡顿） */
private const val MAX_TEXT_PREVIEW_LINES = 20000

/**
 * 文本/代码预览：语法高亮 + 行号 + 等宽滚动。
 * - JSON / Python / Kotlin / Java / JS / Markdown 等按语言高亮
 * - Markdown 额外渲染标题、列表、代码块、行内样式
 * - 纯文本（log/txt/csv 等）不高亮
 */
@Composable
internal fun CodePreview(file: File, extension: String) {
    val lang = codeLanguageFor(extension)
    val isDark = isSystemInDarkTheme()
    val palette = if (isDark) DARK_HIGHLIGHT else LIGHT_HIGHLIGHT

    val state by produceState<Pair<List<String>, List<AnnotatedString>>?>(
        null, file, lang, isDark
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                if (file.length() > MAX_TEXT_PREVIEW_BYTES) {
                    emptyList<String>() to listOf(AnnotatedString("文件过大（>2MB），暂不支持预览"))
                } else {
                    val bytes = file.readBytes()
                    val text = decodeSmart(bytes)
                    val rawLines = text.lines()
                    val truncated = rawLines.size > MAX_TEXT_PREVIEW_LINES
                    val lines = if (truncated) {
                        rawLines.take(MAX_TEXT_PREVIEW_LINES) + "…（仅显示前 $MAX_TEXT_PREVIEW_LINES 行）"
                    } else {
                        rawLines
                    }
                    val rendered = when {
                        lang == "markdown" -> renderMarkdownLines(lines, palette)
                        lang != null -> lines.map { highlightLine(it, lang, palette) }
                        else -> lines.map { AnnotatedString(it) }
                    }
                    lines to rendered
                }
            } catch (e: Exception) {
                emptyList<String>() to listOf(AnnotatedString("⚠ 文件读取失败（可能是二进制文件或编码不支持）"))
            }
        }
    }

    val pair = state
    if (pair == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val (lines, rendered) = pair
    if (lines.isEmpty()) {
        Text(
            text = rendered.firstOrNull()?.text ?: "此文件为空",
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    SelectionContainer {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            itemsIndexed(rendered) { idx, content ->
                CodeLine(
                    lineNumber = idx + 1,
                    content = content,
                    showNumber = lang != null
                )
            }
        }
    }
}

@Composable
private fun CodeLine(lineNumber: Int, content: AnnotatedString, showNumber: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showNumber) {
            Text(
                text = "$lineNumber",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp)
            )
        }
        Text(
            text = content,
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

/**
 * 智能解码文本字节：
 * 1. UTF-8 BOM / UTF-16 BOM 直接按对应编码
 * 2. 严格校验 UTF-8（存在非法序列则放弃）
 * 3. 回退 GB18030（覆盖 GBK/GB2312 中文场景）
 * 4. 最后 ISO-8859-1 保底（不丢字节）
 */
private fun decodeSmart(bytes: ByteArray): String {
    // UTF-8 BOM
    if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    ) {
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    // UTF-16 BOM
    if (bytes.size >= 2 &&
        ((bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) ||
            (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()))
    ) {
        return String(bytes, Charsets.UTF_16)
    }
    // 严格 UTF-8
    try {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        // 不是合法 UTF-8，尝试中文编码
    }
    return try {
        String(bytes, Charset.forName("GB18030"))
    } catch (e: Exception) {
        String(bytes, Charsets.ISO_8859_1)
    }
}
