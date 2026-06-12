package dev.dimension.flare.feature.agent.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal fun String.cleanAgentVisibleText(): String =
    trim()
        .removeOuterMarkdownFence()
        .removeAgentToolCallJsonObjects()
        .trimVisibleText()

private fun String.removeOuterMarkdownFence(): String {
    val value = trim()
    if (!value.startsWith("```") || !value.endsWith("```")) {
        return value
    }
    val withoutFence = value.removePrefix("```").removeSuffix("```")
    return withoutFence
        .substringAfter('\n', missingDelimiterValue = withoutFence)
        .trim()
}

private fun String.removeAgentToolCallJsonObjects(): String {
    val output = StringBuilder(length)
    var index = 0
    while (index < length) {
        if (this[index] == '{') {
            val end = findJsonObjectEnd(index)
            if (end >= index) {
                val candidate = substring(index, end + 1)
                if (candidate.isAgentToolCallJsonObject()) {
                    index = end + 1
                    continue
                }
            }
        }
        output.append(this[index])
        index += 1
    }
    return output.toString()
}

private fun String.findJsonObjectEnd(startIndex: Int): Int {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in startIndex until length) {
        when (val char = this[index]) {
            '"' -> {
                if (!escaped) {
                    inString = !inString
                }
                escaped = false
            }

            '\\' -> {
                escaped = inString && !escaped
            }

            '{' -> {
                if (!inString) {
                    depth += 1
                }
                escaped = false
            }

            '}' -> {
                if (!inString) {
                    depth -= 1
                    if (depth == 0) {
                        return index
                    }
                }
                escaped = false
            }

            else -> {
                escaped = false
            }
        }
    }
    return -1
}

private fun String.isAgentToolCallJsonObject(): Boolean =
    runCatching {
        agentVisibleTextJson.parseToJsonElement(this) as? JsonObject
    }.getOrNull()
        ?.let { jsonObject ->
            val keys = jsonObject.keys
            "tool_call_id" in keys &&
                (
                    "tool_name" in keys ||
                        "toolName" in keys ||
                        "tool_args" in keys ||
                        "toolArgs" in keys
                )
        } ?: false

private fun String.trimVisibleText(): String =
    lineSequence()
        .map { it.trimEnd() }
        .joinToString("\n")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

private val agentVisibleTextJson =
    Json {
        ignoreUnknownKeys = true
    }
