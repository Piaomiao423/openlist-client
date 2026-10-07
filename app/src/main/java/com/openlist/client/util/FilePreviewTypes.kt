package com.openlist.client.util

/** 支持内嵌预览的图片扩展名 */
private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico")

/** 支持内嵌预览的文本扩展名（含代码文件） */
private val TEXT_EXTS = setOf(
    "txt", "md", "markdown", "log", "json", "xml", "csv", "ini", "conf", "cfg",
    "properties", "env", "gitignore", "dockerfile", "sh", "bash", "zsh", "bat", "ps1",
    "kt", "kts", "java", "py", "c", "cpp", "cc", "h", "hpp", "js", "mjs", "cjs",
    "ts", "tsx", "jsx", "html", "htm", "css", "scss", "less", "yml", "yaml", "toml",
    "sql", "gradle", "go", "rs", "rb", "php", "swift", "scala", "lua", "r", "dart",
    "vue", "sass", "pl", "pm", "sh", "fish", "Makefile", "cmake", "graphql", "proto"
).map { it.lowercase() }.toSet()

/** PDF */
private val PDF_EXTS = setOf("pdf")

/** 音频 */
private val AUDIO_EXTS = setOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "amr", "mid", "midi")

/** 视频 */
private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "ts", "m4v", "wmv", "flv")

/** 压缩包（系统库可解压的格式：zip 家族） */
private val ARCHIVE_EXTS = setOf("zip", "jar", "apk")

fun isImageFile(extension: String): Boolean = extension.lowercase() in IMAGE_EXTS

fun isTextFile(extension: String): Boolean = extension.lowercase() in TEXT_EXTS

fun isPdfFile(extension: String): Boolean = extension.lowercase() in PDF_EXTS

fun isAudioFile(extension: String): Boolean = extension.lowercase() in AUDIO_EXTS

fun isVideoFile(extension: String): Boolean = extension.lowercase() in VIDEO_EXTS

fun isArchiveFile(extension: String): Boolean = extension.lowercase() in ARCHIVE_EXTS

/** 是否支持在 App 内直接预览 */
fun isPreviewableFile(extension: String): Boolean =
    isImageFile(extension) || isTextFile(extension) ||
        isPdfFile(extension) || isAudioFile(extension) || isVideoFile(extension) ||
        isArchiveFile(extension)

/**
 * 文本文件对应的代码高亮语言；返回 null 表示纯文本（不高亮）。
 * 注意：区分大小写的扩展名（Dockerfile/Makefile）在调用前已统一 lowercase。
 */
fun codeLanguageFor(extension: String): String? {
    return when (extension.lowercase()) {
        "json" -> "json"
        "md", "markdown" -> "markdown"
        "py" -> "python"
        "kt", "kts", "gradle" -> "kotlin"
        "java" -> "java"
        "js", "mjs", "cjs", "jsx", "ts", "tsx", "vue" -> "javascript"
        "c", "cpp", "cc", "h", "hpp" -> "c"
        "go" -> "go"
        "rs" -> "rust"
        "rb", "pl", "pm" -> "ruby"
        "sh", "bash", "zsh", "fish" -> "shell"
        "sql" -> "sql"
        "php" -> "php"
        "swift" -> "swift"
        "scala" -> "scala"
        "lua" -> "lua"
        "dart" -> "dart"
        "html", "htm", "xml", "svg", "proto" -> "markup"
        "css", "scss", "less", "sass" -> "css"
        "yml", "yaml", "toml", "ini", "conf", "cfg", "properties", "env" -> "config"
        "log", "txt", "csv", "bat", "ps1", "r", "graphql", "dockerfile", "makefile" -> null
        else -> null
    }
}
