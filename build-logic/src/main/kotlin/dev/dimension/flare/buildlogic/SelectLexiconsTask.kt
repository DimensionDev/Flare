package dev.dimension.flare.buildlogic

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

@CacheableTask
abstract class SelectLexiconsTask : DefaultTask() {
    @get:Classpath
    abstract val schemaArchives: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rootsFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun selectSchemas() {
        val schemas = schemaArchives.files.flatMap { archive ->
            ZipFile(archive).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".json") }
                    .map { entry -> zip.getInputStream(entry).bufferedReader().use { it.readText() } }
                    .toList()
            }
        }
        val roots = rootsFile.get().asFile.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
        val selected = selectLexiconSchemas(schemas, roots)
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        selected.forEach { (id, schema) -> output.resolve("$id.json").writeText(schema) }
        logger.lifecycle("Selected ${selected.size} of ${schemas.size} Bluesky lexicons")
    }
}

internal fun selectLexiconSchemas(schemas: List<String>, roots: List<String>): Map<String, String> {
    val documents = schemas.associate { text ->
        val document = JsonSlurper().parseText(text) as Map<*, *>
        val id = document["id"] as String
        require(id.matches(Regex("[a-zA-Z0-9.-]+"))) { "Invalid lexicon ID: $id" }
        id to (document to text)
    }
    require(documents.size == schemas.size) { "Duplicate lexicon IDs" }
    require(roots.isNotEmpty()) { "At least one root lexicon is required" }
    val selected = linkedMapOf<String, String>()
    val pending = ArrayDeque(roots)
    while (pending.isNotEmpty()) {
        val id = pending.removeFirst()
        if (id in selected) continue
        val (document, text) = requireNotNull(documents[id]) { "Missing lexicon: $id" }
        selected[id] = text
        lexiconReferences(document).forEach { reference ->
            val targetId = reference.substringBefore('#').ifEmpty { id }
            val definition = reference.substringAfter('#', "main")
            val target = requireNotNull(documents[targetId]) { "Missing lexicon $targetId referenced by $id" }.first
            require((target["defs"] as Map<*, *>).containsKey(definition)) {
                "Missing definition $reference referenced by $id"
            }
            pending.add(targetId)
        }
    }
    return selected.toSortedMap()
}

private fun lexiconReferences(value: Any?): List<String> = when (value) {
    is Map<*, *> -> when (value["type"]) {
        "ref" -> listOf(value["ref"] as String)
        "union" -> (value["refs"] as List<*>).map { it as String }
        else -> value.values.flatMap(::lexiconReferences)
    }
    is List<*> -> value.flatMap(::lexiconReferences)
    else -> emptyList()
}
