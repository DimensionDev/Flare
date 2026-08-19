package dev.dimension.flare.feature.plugin.installer

/** Finds executable dynamic imports without treating comments or string contents as code. */
internal fun containsDynamicImport(source: String): Boolean = scanCode(source, start = 0, stopAtClosingBrace = false).found

private data class ScanResult(
    val found: Boolean,
    val next: Int,
)

private fun scanCode(
    source: String,
    start: Int,
    stopAtClosingBrace: Boolean,
): ScanResult {
    var index = start
    var braceDepth = 0
    var canStartRegex = true
    while (index < source.length) {
        val current = source[index]
        when {
            current.isWhitespace() -> index++
            current == '/' && source.getOrNull(index + 1) == '/' -> index = skipLineComment(source, index + 2)
            current == '/' && source.getOrNull(index + 1) == '*' -> index = skipBlockComment(source, index + 2)
            current == '/' && canStartRegex -> {
                index = skipRegexLiteral(source, index + 1)
                canStartRegex = false
            }

            current == '\'' || current == '"' -> {
                index = skipQuoted(source, index + 1, current)
                canStartRegex = false
            }

            current == '`' -> {
                val result = scanTemplate(source, index + 1)
                if (result.found) return result
                index = result.next
                canStartRegex = false
            }

            current.isJavaScriptIdentifierStart() -> {
                val end = skipIdentifier(source, index + 1)
                val identifier = source.substring(index, end)
                if (identifier == "import") {
                    val next = skipTrivia(source, end)
                    if (source.getOrNull(next) == '(') return ScanResult(found = true, next = next)
                }
                canStartRegex = identifier in REGEX_PREFIX_KEYWORDS
                index = end
            }

            current == '{' -> {
                braceDepth++
                canStartRegex = true
                index++
            }

            current == '}' && stopAtClosingBrace -> {
                if (braceDepth == 0) return ScanResult(found = false, next = index + 1)
                braceDepth--
                canStartRegex = false
                index++
            }

            current == ')' || current == ']' -> {
                canStartRegex = false
                index++
            }

            current.isDigit() -> {
                index = skipNumber(source, index + 1)
                canStartRegex = false
            }

            current == '.' -> {
                canStartRegex = false
                index++
            }

            else -> {
                canStartRegex = true
                index++
            }
        }
    }
    return ScanResult(found = false, next = source.length)
}

private fun scanTemplate(
    source: String,
    start: Int,
): ScanResult {
    var index = start
    while (index < source.length) {
        when {
            source[index] == '\\' -> index = (index + 2).coerceAtMost(source.length)
            source[index] == '`' -> return ScanResult(found = false, next = index + 1)
            source[index] == '$' && source.getOrNull(index + 1) == '{' -> {
                val result = scanCode(source, index + 2, stopAtClosingBrace = true)
                if (result.found) return result
                index = result.next
            }

            else -> index++
        }
    }
    return ScanResult(found = false, next = source.length)
}

private fun skipTrivia(
    source: String,
    start: Int,
): Int {
    var index = start
    while (index < source.length) {
        index =
            when {
                source[index].isWhitespace() -> index + 1
                source[index] == '/' && source.getOrNull(index + 1) == '/' -> skipLineComment(source, index + 2)
                source[index] == '/' && source.getOrNull(index + 1) == '*' -> skipBlockComment(source, index + 2)
                else -> return index
            }
    }
    return index
}

private fun skipLineComment(
    source: String,
    start: Int,
): Int {
    val end = source.indexOf('\n', start)
    return if (end < 0) source.length else end + 1
}

private fun skipBlockComment(
    source: String,
    start: Int,
): Int {
    val end = source.indexOf("*/", start)
    return if (end < 0) source.length else end + 2
}

private fun skipQuoted(
    source: String,
    start: Int,
    quote: Char,
): Int {
    var index = start
    while (index < source.length) {
        when (source[index]) {
            '\\' -> index = (index + 2).coerceAtMost(source.length)
            quote -> return index + 1
            else -> index++
        }
    }
    return source.length
}

private fun skipRegexLiteral(
    source: String,
    start: Int,
): Int {
    var index = start
    var inCharacterClass = false
    while (index < source.length) {
        when (source[index]) {
            '\\' -> index = (index + 2).coerceAtMost(source.length)
            '[' -> {
                inCharacterClass = true
                index++
            }

            ']' -> {
                inCharacterClass = false
                index++
            }

            '/' ->
                if (inCharacterClass) {
                    index++
                } else {
                    index++
                    while (source.getOrNull(index)?.isJavaScriptIdentifierPart() == true) index++
                    return index
                }

            '\n', '\r' -> return index
            else -> index++
        }
    }
    return source.length
}

private fun skipIdentifier(
    source: String,
    start: Int,
): Int {
    var index = start
    while (source.getOrNull(index)?.isJavaScriptIdentifierPart() == true) index++
    return index
}

private fun skipNumber(
    source: String,
    start: Int,
): Int {
    var index = start
    while (source.getOrNull(index)?.let { it.isLetterOrDigit() || it == '.' || it == '_' } == true) index++
    return index
}

private fun Char.isJavaScriptIdentifierStart(): Boolean = isLetter() || this == '_' || this == '$' || code >= 0x80

private fun Char.isJavaScriptIdentifierPart(): Boolean = isJavaScriptIdentifierStart() || isDigit()

private val REGEX_PREFIX_KEYWORDS =
    setOf(
        "await",
        "case",
        "delete",
        "do",
        "else",
        "in",
        "instanceof",
        "new",
        "of",
        "return",
        "throw",
        "typeof",
        "void",
        "yield",
    )
