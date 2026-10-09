package dev.dimension.flare.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

@CacheableTask
abstract class GenerateGraphemePropertiesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val ucdArchive: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val properties = ByteArray(0x110000)
        val categories =
            listOf(
                "Other",
                "CR",
                "LF",
                "Control",
                "Extend",
                "ZWJ",
                "Regional_Indicator",
                "Prepend",
                "SpacingMark",
                "L",
                "V",
                "T",
                "LV",
                "LVT",
            )
        ZipFile(ucdArchive.singleFile).use { zip ->
            fun read(name: String): String = zip.getInputStream(requireNotNull(zip.getEntry(name))).bufferedReader().use { it.readText() }
            val grapheme = read("auxiliary/GraphemeBreakProperty.txt")
            val core = read("DerivedCoreProperties.txt")
            val emoji = read("emoji/emoji-data.txt")
            check(
                "GraphemeBreakProperty-16.0.0.txt" in grapheme && "DerivedCoreProperties-16.0.0.txt" in core &&
                    "Emoji Version 16.0" in emoji,
            )

            fun entries(
                text: String,
                apply: (IntRange, List<String>) -> Unit,
            ) {
                text.lineSequence().forEach { line ->
                    val fields = line.substringBefore('#').split(';').map(String::trim)
                    if (fields.size > 1) {
                        val bounds = fields.first().split("..")
                        apply(bounds.first().toInt(16)..bounds.last().toInt(16), fields.drop(1))
                    }
                }
            }
            entries(grapheme) { range, fields ->
                val category = categories.indexOf(fields.first())
                check(category >= 0)
                // Hangul LV/LVT are classified arithmetically at runtime.
                if (category !in 12..13) range.forEach { properties[it] = category.toByte() }
            }
            entries(core) { range, fields ->
                if (fields.first() == "InCB") {
                    val flag = mapOf("Consonant" to 0x10, "Extend" to 0x20, "Linker" to 0x30).getValue(fields[1])
                    range.forEach { properties[it] = (properties[it].toInt() or flag).toByte() }
                }
            }
            entries(emoji) { range, fields ->
                if (fields.first() == "Extended_Pictographic") range.forEach { properties[it] = (properties[it].toInt() or 0x40).toByte() }
            }
        }
        check(properties[0x1FAF1].toInt() == 0x40 && properties[0x1F3FB].toInt() and 0x0F == 4)
        val boundaries =
            properties.indices.filter { it == 0 || properties[it] != properties[it - 1] }.map {
                (it shl 7) or
                    properties[it].toInt()
            }
        val source =
            buildString {
                append("package dev.dimension.flare.common\n\n")
                append("// Generated from Unicode 16.0.0 UCD by generateGraphemeProperties.\n")
                append("// Copyright © 2024 Unicode, Inc. License: https://www.unicode.org/license.txt\n")
                append("internal val graphemePropertyStarts =\n    intArrayOf(\n")
                boundaries.chunked(10).forEach { row -> append("        " + row.joinToString(", ") { "0x%08X".format(it) } + ",\n") }
                append("    )\n")
            }
        val output = outputDirectory.file("dev/dimension/flare/common/GraphemePropertyData.kt").get().asFile
        output.parentFile.mkdirs()
        output.writeText(source)
    }
}
