package com.openlist.client.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/** 语法高亮配色（按深浅色主题各一套） */
data class HighlightPalette(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val jsonKey: Color,
    val tag: Color,
    val attr: Color,
    val section: Color,
    val codeBackground: Color
)

val LIGHT_HIGHLIGHT = HighlightPalette(
    keyword = Color(0xFF7C4DFF),
    string = Color(0xFF2E7D32),
    number = Color(0xFFE65100),
    comment = Color(0xFF8A8A8A),
    jsonKey = Color(0xFF1565C0),
    tag = Color(0xFFC62828),
    attr = Color(0xFF8D6E63),
    section = Color(0xFF00695C),
    codeBackground = Color(0xFFF0F0F0)
)

val DARK_HIGHLIGHT = HighlightPalette(
    keyword = Color(0xFFC792EA),
    string = Color(0xFFC3E88D),
    number = Color(0xFFF78C6C),
    comment = Color(0xFF6E7391),
    jsonKey = Color(0xFF82AAFF),
    tag = Color(0xFFF07178),
    attr = Color(0xFFFFCB6B),
    section = Color(0xFF80CBC4),
    codeBackground = Color(0xFF2A2A2E)
)

// ===== 各语言关键字 =====
private val KEYWORDS: Map<String, Set<String>> = mapOf(
    "python" to setOf(
        "def", "class", "import", "from", "return", "if", "elif", "else", "for", "while",
        "in", "not", "and", "or", "is", "None", "True", "False", "print", "lambda", "try",
        "except", "finally", "with", "as", "pass", "break", "continue", "global", "yield",
        "async", "await", "raise", "assert", "del", "self", "nonlocal", "match", "case"
    ),
    "kotlin" to setOf(
        "fun", "val", "var", "class", "object", "interface", "package", "import", "when",
        "data", "override", "private", "public", "internal", "protected", "return", "if",
        "else", "for", "while", "do", "try", "catch", "finally", "throw", "null", "true",
        "false", "this", "super", "suspend", "companion", "enum", "sealed", "abstract",
        "open", "inline", "lateinit", "by", "is", "in", "as", "out", "typealias", "init",
        "constructor", "get", "set", "field", "it", "Unit", "String", "Int", "Long",
        "Double", "Float", "Boolean", "List", "Map", "Set", "arrayOf", "listOf", "mapOf"
    ),
    "java" to setOf(
        "public", "private", "protected", "class", "interface", "enum", "extends",
        "implements", "import", "package", "static", "final", "void", "new", "return",
        "if", "else", "for", "while", "do", "try", "catch", "finally", "throw", "throws",
        "null", "true", "false", "this", "super", "int", "long", "double", "float",
        "boolean", "char", "byte", "short", "var", "record", "sealed", "permits",
        "instanceof", "switch", "case", "break", "continue", "default", "abstract",
        "synchronized", "native", "transient", "volatile", "strictfp", "assert", "yield",
        "String", "Integer", "Long", "Double", "Float", "Boolean", "Object", "List",
        "Map", "Set", "ArrayList", "HashMap", "System", "Math"
    ),
    "javascript" to setOf(
        "function", "const", "let", "var", "class", "import", "export", "from", "return",
        "if", "else", "for", "while", "do", "try", "catch", "finally", "throw", "new",
        "null", "true", "false", "this", "async", "await", "of", "in", "typeof",
        "instanceof", "undefined", "switch", "case", "break", "continue", "default",
        "yield", "static", "get", "set", "extends", "super", "delete", "void", "number",
        "string", "boolean", "object", "symbol", "bigint", "require", "module", "exports"
    ),
    "c" to setOf(
        "int", "char", "float", "double", "void", "struct", "union", "enum", "typedef",
        "const", "static", "extern", "return", "if", "else", "for", "while", "do",
        "switch", "case", "break", "continue", "goto", "new", "delete", "class",
        "public", "private", "protected", "namespace", "using", "include", "define",
        "true", "false", "null", "nullptr", "unsigned", "signed", "long", "short",
        "auto", "register", "volatile", "sizeof", "template", "typename", "virtual",
        "friend", "operator", "this", "try", "catch", "throw", "override", "final",
        "default", "inline", "string", "vector", "map", "cout", "cin", "endl"
    ),
    "go" to setOf(
        "package", "import", "func", "type", "struct", "interface", "map", "chan", "go",
        "defer", "return", "if", "else", "for", "range", "switch", "case", "break",
        "continue", "default", "var", "const", "true", "false", "nil", "len", "cap",
        "append", "make", "new", "error", "select", "fallthrough", "goto", "string",
        "int", "float", "bool", "byte", "rune", "uint", "complex", "panic", "recover"
    ),
    "rust" to setOf(
        "fn", "let", "mut", "const", "static", "struct", "enum", "impl", "trait", "pub",
        "use", "mod", "crate", "return", "if", "else", "for", "while", "loop", "match",
        "move", "ref", "self", "Self", "true", "false", "async", "await", "dyn", "where",
        "type", "unsafe", "extern", "in", "as", "super", "break", "continue", "yield"
    ),
    "ruby" to setOf(
        "def", "end", "class", "module", "require", "require_relative", "puts", "print",
        "p", "if", "else", "elsif", "unless", "while", "do", "return", "nil", "true",
        "false", "and", "or", "not", "case", "when", "then", "begin", "rescue",
        "ensure", "yield", "self", "super", "attr_reader", "attr_writer",
        "attr_accessor", "new", "each", "map", "select", "reject"
    ),
    "shell" to setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done",
        "case", "esac", "function", "return", "exit", "echo", "export", "local", "read",
        "cd", "source", "alias", "set", "shift", "trap", "printf", "test", "eval",
        "exec", "let", "select", "time", "wait", "unset", "declare", "typeset", "sudo",
        "apt", "yum", "pip", "curl", "wget", "grep", "sed", "awk", "ls", "mkdir", "rm",
        "cp", "mv", "cat", "touch", "chmod", "chown", "git"
    ),
    "sql" to setOf(
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
        "create", "table", "drop", "alter", "add", "column", "join", "left", "right",
        "inner", "outer", "full", "on", "as", "order", "by", "group", "asc", "desc",
        "limit", "offset", "distinct", "count", "sum", "avg", "max", "min", "and", "or",
        "not", "null", "primary", "key", "foreign", "references", "index", "view",
        "trigger", "procedure", "function", "begin", "commit", "rollback", "case",
        "when", "then", "end", "between", "like", "in", "exists", "union", "all",
        "having", "using", "database", "schema", "if", "exists", "default", "unique",
        "check", "constraint", "varchar", "int", "bigint", "tinyint", "text", "date",
        "datetime", "timestamp", "boolean", "decimal", "float", "double", "char",
        "blob", "is", "null", "not null"
    ),
    "php" to setOf(
        "function", "class", "interface", "trait", "namespace", "use", "return", "if",
        "else", "elseif", "for", "foreach", "while", "do", "switch", "case", "break",
        "continue", "default", "try", "catch", "finally", "throw", "new", "null",
        "true", "false", "echo", "print", "require", "require_once", "include",
        "include_once", "public", "private", "protected", "static", "final",
        "abstract", "extends", "implements", "this", "self", "parent", "const", "var",
        "global", "isset", "empty", "unset", "array", "list", "as", "instanceof",
        "and", "or", "xor", "not"
    ),
    "swift" to setOf(
        "func", "var", "let", "class", "struct", "enum", "protocol", "extension",
        "import", "return", "if", "else", "guard", "for", "while", "switch", "case",
        "break", "continue", "throws", "throw", "nil", "true", "false", "self", "init",
        "deinit", "override", "open", "public", "internal", "fileprivate", "private",
        "static", "final", "lazy", "inout", "where", "typealias", "associatedtype",
        "subscript", "defer", "do", "catch", "repeat", "fallthrough", "in", "as", "is",
        "try", "await", "async", "actor", "mutating", "nonmutating", "required",
        "convenience", "indirect", "dynamic"
    ),
    "scala" to setOf(
        "def", "val", "var", "class", "object", "trait", "package", "import",
        "extends", "with", "return", "if", "else", "for", "while", "do", "try",
        "catch", "finally", "throw", "new", "null", "true", "false", "this", "super",
        "private", "protected", "abstract", "final", "sealed", "implicit", "lazy",
        "override", "match", "case", "yield", "type", "enum", "given", "using",
        "extension"
    ),
    "lua" to setOf(
        "function", "end", "local", "if", "then", "else", "elseif", "for", "while",
        "do", "repeat", "until", "return", "nil", "true", "false", "and", "or", "not",
        "require", "print", "pairs", "ipairs", "type", "self", "error", "assert",
        "pcall", "xpcall", "select", "rawget", "rawset", "setmetatable",
        "getmetatable"
    ),
    "dart" to setOf(
        "void", "var", "final", "const", "class", "extends", "implements", "with",
        "mixin", "abstract", "override", "factory", "get", "set", "new", "return",
        "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
        "default", "try", "catch", "finally", "throw", "rethrow", "null", "true",
        "false", "this", "super", "static", "late", "required", "enum", "typedef",
        "import", "export", "part", "library", "as", "is", "in", "sync", "async",
        "await", "yield", "dynamic", "covariant", "external", "operator"
    ),
    "json" to setOf("true", "false", "null"),
    "css" to setOf(
        "important", "inherit", "initial", "auto", "none", "block", "inline", "flex",
        "grid", "absolute", "relative", "fixed", "sticky", "static", "solid", "dashed",
        "dotted", "hidden", "visible", "center", "left", "right", "top", "bottom",
        "bold", "normal", "italic", "pointer", "hover", "active", "focus"
    )
)

/** 各语言注释前缀 */
private val COMMENT_PREFIXES: Map<String, List<String>> = mapOf(
    "python" to listOf("#"),
    "ruby" to listOf("#"),
    "shell" to listOf("#"),
    "kotlin" to listOf("//"),
    "java" to listOf("//"),
    "javascript" to listOf("//"),
    "c" to listOf("//"),
    "go" to listOf("//"),
    "rust" to listOf("//"),
    "swift" to listOf("//"),
    "scala" to listOf("//"),
    "lua" to listOf("--"),
    "dart" to listOf("//"),
    "php" to listOf("//", "#"),
    "sql" to listOf("--", "#"),
    "css" to listOf("/*")
)

/** 以指定样式追加文本（start/end 由 Builder 自动计算） */
private fun AnnotatedString.Builder.styled(style: SpanStyle, text: String) {
    val start = length
    append(text)
    addStyle(style, start, length)
}

/**
 * 高亮一行代码（按语言关键字/字符串/数字/注释）。
 * markup（html/xml）与 config（ini/yaml 等）走专用分支。
 */
fun highlightLine(line: String, lang: String, palette: HighlightPalette): AnnotatedString {
    return when (lang) {
        "markup" -> highlightMarkupLine(line, palette)
        "config" -> highlightConfigLine(line, palette)
        "plain" -> buildAnnotatedString {
            styled(SpanStyle(background = palette.codeBackground), line)
        }
        else -> highlightGenericLine(line, lang, palette)
    }
}

private fun highlightGenericLine(
    line: String,
    lang: String,
    palette: HighlightPalette
): AnnotatedString {
    return buildAnnotatedString {
        val keywords = KEYWORDS[lang].orEmpty()
        val commentPrefixes = COMMENT_PREFIXES[lang].orEmpty()
        var i = 0
        val n = line.length
        while (i < n) {
            val c = line[i]
            // 注释（前缀出现在行内任意位置，且不在字符串中——字符串在前面已整体跳过）
            val matchedPrefix = if (commentPrefixes.isEmpty()) null
            else commentPrefixes.firstOrNull { line.startsWith(it, i) }
            if (matchedPrefix != null) {
                val rest = line.substring(i)
                styled(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), rest)
                i = n
                continue
            }
            // 字符串
            if (c == '"' || c == '\'' || c == '`') {
                var j = i + 1
                while (j < n) {
                    if (line[j] == '\\' && j + 1 < n) {
                        j += 2
                    } else if (line[j] == c) {
                        j++
                        break
                    } else {
                        j++
                    }
                }
                val token = line.substring(i, j)
                // JSON 键名："xxx":（引号后紧跟冒号）
                if (lang == "json" && c == '"') {
                    var k = j
                    while (k < n && line[k].isWhitespace()) k++
                    if (k < n && line[k] == ':') {
                        styled(SpanStyle(color = palette.jsonKey, fontWeight = FontWeight.Bold), token)
                        i = j
                        continue
                    }
                }
                styled(SpanStyle(color = palette.string), token)
                i = j
                continue
            }
            // 数字
            if (c.isDigit() ||
                (c == '-' && i + 1 < n && line[i + 1].isDigit() && (i == 0 || line[i - 1].isWhitespace()))
            ) {
                var j = i + 1
                while (j < n && (line[j].isLetterOrDigit() || line[j] == '.' || line[j] == '_')) j++
                val token = line.substring(i, j)
                styled(SpanStyle(color = palette.number), token)
                i = j
                continue
            }
            // 标识符 / 关键字
            if (c.isLetter() || c == '_') {
                var j = i + 1
                while (j < n && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                val token = line.substring(i, j)
                if (token in keywords) {
                    styled(SpanStyle(color = palette.keyword, fontWeight = FontWeight.Bold), token)
                } else {
                    append(token)
                }
                i = j
                continue
            }
            append(c)
            i++
        }
    }
}

/** HTML/XML：标签 / 属性 / 引号值 / 注释 */
private val MARKUP_REGEX = Regex(
    """(<!--[\s\S]*?-->)|(<\/?)([a-zA-Z][\w-]*)|([a-zA-Z-]+=)|("[^"]*"|'[^']*')"""
)

private fun highlightMarkupLine(line: String, palette: HighlightPalette): AnnotatedString {
    return buildAnnotatedString {
        var last = 0
        for (m in MARKUP_REGEX.findAll(line)) {
            if (m.range.first > last) append(line.substring(last, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> styled(
                    SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), g[1]
                )
                g[3].isNotEmpty() -> {
                    if (g[2].isNotEmpty()) append(g[2]) // </ 符号
                    styled(SpanStyle(color = palette.tag), g[3])
                }
                g[4].isNotEmpty() -> styled(SpanStyle(color = palette.attr), g[4])
                g[5].isNotEmpty() -> styled(SpanStyle(color = palette.string), g[5])
            }
            last = m.range.last + 1
        }
        if (last < line.length) append(line.substring(last))
    }
}

/** ini / yaml / toml / properties：[section]、key=value、注释 */
private fun highlightConfigLine(line: String, palette: HighlightPalette): AnnotatedString {
    return buildAnnotatedString {
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") || trimmed.startsWith(";") -> styled(
                SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), line
            )
            trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                append(line.substring(0, line.length - trimmed.length))
                styled(SpanStyle(color = palette.section, fontWeight = FontWeight.Bold), trimmed)
            }
            else -> {
                val idx = line.indexOfFirst { it == '=' || it == ':' }
                if (idx > 0 && line.substring(0, idx).isNotBlank()) {
                    styled(SpanStyle(color = palette.jsonKey), line.substring(0, idx))
                    append(line.substring(idx))
                } else {
                    append(line)
                }
            }
        }
    }
}

/** Markdown 整行渲染：标题 / 引用 / 列表 / 分隔线 / 行内样式 */
fun renderMarkdownLine(line: String, palette: HighlightPalette): AnnotatedString {
    return buildAnnotatedString {
        val leadingLen = line.length - line.trimStart().length
        val leading = line.substring(0, leadingLen)
        val content = line.substring(leadingLen)
        append(leading)

        val heading = Regex("^(#{1,6})\\s+(.*)$").matchEntire(content)
        if (heading != null) {
            val mark = heading.groupValues[1]
            val text = heading.groupValues[2]
            styled(SpanStyle(color = palette.section), mark)
            append(" ")
            styled(SpanStyle(color = palette.section, fontWeight = FontWeight.Bold), text)
            return@buildAnnotatedString
        }
        when {
            content.startsWith("```") || content.startsWith("~~~") -> styled(
                SpanStyle(color = palette.section, fontWeight = FontWeight.Bold), content
            )
            content.startsWith(">") -> styled(
                SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), content
            )
            content.startsWith("---") || content.startsWith("***") || content.startsWith("___") -> styled(
                SpanStyle(color = palette.comment), content
            )
            Regex("^([-*+]|\\d+\\.)\\s").containsMatchIn(content) -> {
                val m = Regex("^([-*+]|\\d+\\.)").find(content)!!
                styled(SpanStyle(color = palette.number, fontWeight = FontWeight.Bold), m.value)
                append(content.substring(m.range.last + 1))
            }
            else -> {
                // 普通段落：处理行内 **加粗**、*斜体*、`行内代码`、[链接](url)
                inlineMarkdown(this, content, palette)
            }
        }
    }
}

/** 行内 Markdown 样式 */
private val INLINE_MARKDOWN_REGEX = Regex(
    """(`[^`]+`)|(\*\*[^*]+\*\*)|(\*[^*]+\*)|(\[[^\]\n]+\]\([^)\n]+\))"""
)

private fun inlineMarkdown(b: androidx.compose.ui.text.AnnotatedString.Builder, text: String, palette: HighlightPalette) {
    var last = 0
    for (m in INLINE_MARKDOWN_REGEX.findAll(text)) {
        if (m.range.first > last) b.append(text.substring(last, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() -> b.styled(
                SpanStyle(color = palette.string, background = palette.codeBackground), g[1]
            )
            g[2].isNotEmpty() -> b.styled(SpanStyle(fontWeight = FontWeight.Bold), g[2])
            g[3].isNotEmpty() -> b.styled(SpanStyle(fontStyle = FontStyle.Italic), g[3])
            g[4].isNotEmpty() -> {
                val inner = g[4]
                val sep = inner.indexOf("](")
                b.styled(SpanStyle(color = palette.jsonKey), inner.substring(0, sep + 1))
                b.styled(SpanStyle(color = palette.string), inner.substring(sep + 1))
            }
        }
        last = m.range.last + 1
    }
    if (last < text.length) b.append(text.substring(last))
}

/**
 * Markdown 全文行渲染（维护代码块开闭状态）。
 * 代码块内按 plain 渲染（等宽 + 底纹）。
 */
fun renderMarkdownLines(
    lines: List<String>,
    palette: HighlightPalette
): List<AnnotatedString> {
    var inBlock = false
    return lines.map { line ->
        val t = line.trimStart()
        when {
            t.startsWith("```") || t.startsWith("~~~") -> {
                inBlock = !inBlock
                renderMarkdownLine(line, palette)
            }
            inBlock -> highlightLine(line, "plain", palette)
            else -> renderMarkdownLine(line, palette)
        }
    }
}
