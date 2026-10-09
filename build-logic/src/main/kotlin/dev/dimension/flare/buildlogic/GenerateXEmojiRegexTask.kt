package dev.dimension.flare.buildlogic

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

@CacheableTask
abstract class GenerateXEmojiRegexTask : DefaultTask() {
    @get:Input
    val sourceUrl = "https://registry.npmjs.org/twemoji-parser/-/twemoji-parser-11.0.2.tgz"

    @get:Internal
    val archiveCache = project.gradle.gradleUserHomeDir.resolve("caches/flare-codegen")

    @get:Internal
    val offline = project.gradle.startParameter.isOffline

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @TaskAction
    fun generate() {
        val archive = archiveOperations.tarTree(downloadArchive(sourceUrl, archiveCache, offline))

        fun read(name: String): String = archive.matching { include("package/$name") }.singleFile.readText()
        val metadata = JsonSlurper().parseText(read("package.json")) as Map<*, *>
        check(metadata["name"] == "twemoji-parser" && metadata["version"] == "11.0.2")
        val source = read("dist/lib/regex.js")
        val pattern = portableXEmojiPattern(requireNotNull(Regex("exports.default = /(.*)/g;").find(source)).groupValues[1])
        check("[👨👩]" in pattern && "[🏻🏼🏽🏾🏿]" in pattern)
        val chunks = mutableListOf("")
        Regex("""\\u[0-9a-fA-F]{4}|.""").findAll(pattern).forEach { match ->
            val token = match.value
            if (chunks.last().codePointCount(0, chunks.last().length) + token.codePointCount(0, token.length) > 100) chunks.add("")
            chunks[chunks.lastIndex] += token
        }
        check(chunks.joinToString("") == pattern)
        val generated =
            buildString {
                append("package dev.dimension.flare.data.datasource.xqt\n\n/*\n" + read("LICENSE.md").trim() + "\n*/\n\n")
                append("// Generated from twemoji-parser 11.0.2 by generateXEmojiRegex.\n")
                append("// Surrogate pairs/classes become code points; ranges are expanded for Native/Wasm Regex.\n")
                append("internal val xEmojiRegex =\n    Regex(\n")
                append(chunks.joinToString(" +\n") { "        \"\"\"$it\"\"\"" })
                append("\n    )\n")
            }
        val output = outputDirectory.file("dev/dimension/flare/data/datasource/xqt/XEmojiRegexData.kt").get().asFile
        output.parentFile.mkdirs()
        output.writeText(generated)
    }
}

internal fun portableXEmojiPattern(source: String): String {
    fun codepoint(
        high: String,
        low: String,
    ): String =
        String(
            Character.toChars(0x10000 + (high.toInt(16) - 0xD800) * 0x400 + low.toInt(16) - 0xDC00),
        )
    val classes = Regex("""\\u([dD][89abAB][0-9a-fA-F]{2})\[([^]]+)]""")
    val pattern =
        classes.replace(source) { match ->
            val high = match.groupValues[1]
            val body = match.groupValues[2]
            val tokens = Regex("""\\u([dD][c-fC-F][0-9a-fA-F]{2})(?:-\\u([dD][c-fC-F][0-9a-fA-F]{2}))?""")
            check(Regex("(?:${tokens.pattern})+").matches(body))
            // Native/Wasm Regex cannot match supplementary character ranges.
            "[" +
                tokens.findAll(body).joinToString("") { token ->
                    val start = token.groupValues[1].toInt(16)
                    val end = token.groupValues[2].ifEmpty { token.groupValues[1] }.toInt(16)
                    (start..end).joinToString("") { codepoint(high, it.toString(16)) }
                } + "]"
        }
    val pairs = Regex("""\\u([dD][89abAB][0-9a-fA-F]{2})\\u([dD][c-fC-F][0-9a-fA-F]{2})""")
    return pairs.replace(pattern) { codepoint(it.groupValues[1], it.groupValues[2]) }.also {
        check(!Regex("""\\u[dD][89a-fA-F][0-9a-fA-F]{2}""").containsMatchIn(it))
    }
}
