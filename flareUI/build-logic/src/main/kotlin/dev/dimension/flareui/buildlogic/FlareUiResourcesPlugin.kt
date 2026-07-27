package dev.dimension.flareui.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.android.ide.common.vectordrawable.Svg2Vector
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory

abstract class FlareUiResourcesExtension {
    abstract val namespace: Property<String>
    abstract val packageName: Property<String>
    abstract val accessorName: Property<String>
    abstract val sourceLanguage: Property<String>
}

class FlareUiResourcesPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension =
            target.extensions.create<FlareUiResourcesExtension>("flareUiResources").apply {
                namespace.convention(defaultResourceNamespace(target.path))
                accessorName.convention("${target.name.toUpperCamelCase()}Resources")
                sourceLanguage.convention("en")
            }
        val generateResources =
            target.tasks.register<GenerateFlareUiResourcesTask>("generateFlareUiResources") {
                group = "flare ui"
                description = "Generates typed Flare UI accessors and native resources."
                inputDirectory.set(
                    target.layout.projectDirectory.dir("src/commonMain/flareResources"),
                )
                resourceNamespace.set(extension.namespace)
                packageName.set(extension.packageName)
                accessorName.set(extension.accessorName)
                sourceLanguage.set(extension.sourceLanguage)
                commonKotlinDirectory.set(
                    target.layout.buildDirectory.dir(
                        "generated/flareui/resources/commonMain/kotlin",
                    ),
                )
                androidKotlinDirectory.set(
                    target.layout.buildDirectory.dir(
                        "generated/flareui/resources/androidMain/kotlin",
                    ),
                )
                androidResourcesDirectory.set(
                    target.layout.buildDirectory.dir(
                        "generated/flareui/resources/androidMain/res",
                    ),
                )
                appleResourcesDirectory.set(
                    target.layout.buildDirectory.dir(
                        "generated/flareui/resources/apple",
                    ),
                )
            }

        target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val kotlin = target.extensions.getByType<KotlinMultiplatformExtension>()
            val androidNamespace =
                target.providers.provider {
                    kotlin.targets
                        .getByName("android")
                        .let { it as KotlinMultiplatformAndroidLibraryTarget }
                        .namespace
                }
            extension.packageName.convention(
                androidNamespace.map { namespace -> "$namespace.resources" },
            )
            generateResources.configure {
                this.androidNamespace.set(androidNamespace)
            }

            kotlin.sourceSets.matching { sourceSet -> sourceSet.name == "commonMain" }
                .configureEach {
                    this.kotlin.srcDir(
                        generateResources.flatMap { task -> task.commonKotlinDirectory },
                    )
                }
            kotlin.sourceSets.matching { sourceSet -> sourceSet.name == "androidMain" }
                .configureEach {
                    this.kotlin.srcDir(
                        generateResources.flatMap { task -> task.androidKotlinDirectory },
                    )
                }
            kotlin.targets
                .withType(KotlinMultiplatformAndroidLibraryTarget::class.java)
                .configureEach {
                    androidResources.enable = true
                }
        }

        target.pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            val androidComponents =
                target.extensions.getByType<KotlinMultiplatformAndroidComponentsExtension>()
            androidComponents.onVariants(androidComponents.selector().all()) { variant ->
                variant.sources.res?.addGeneratedSourceDirectory(generateResources) { task ->
                    task.androidResourcesDirectory
                }
            }
        }
    }
}

@CacheableTask
abstract class GenerateFlareUiResourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty

    @get:Input
    abstract val resourceNamespace: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val accessorName: Property<String>

    @get:Input
    abstract val sourceLanguage: Property<String>

    @get:Input
    abstract val androidNamespace: Property<String>

    @get:OutputDirectory
    abstract val commonKotlinDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val androidKotlinDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val androidResourcesDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val appleResourcesDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val sourceRoot = inputDirectory.get().asFile
        val namespace = resourceNamespace.get()
        val packageName = packageName.get()
        val accessorName = accessorName.get()
        val sourceLanguage = sourceLanguage.get()
        val localeStrings = readLocaleStrings(sourceRoot, sourceLanguage)
        val defaultStrings =
            localeStrings.firstOrNull { locale -> locale.qualifier == null }
                ?: error("$sourceRoot must contain values/strings.xml")
        val images =
            sourceRoot.resolve("images")
                .listFiles { file -> file.isFile && file.extension == "svg" }
                .orEmpty()
                .sortedBy { file -> file.name }

        validateResources(defaultStrings.strings, localeStrings, images)

        val commonOutput = commonKotlinDirectory.clean()
        val androidKotlinOutput = androidKotlinDirectory.clean()
        val androidResourcesOutput = androidResourcesDirectory.clean()
        val appleOutput = appleResourcesDirectory.clean()

        writeCommonAccessors(
            outputRoot = commonOutput,
            packageName = packageName,
            accessorName = accessorName,
            namespace = namespace,
            stringNames = defaultStrings.strings.keys.sorted(),
            imageNames = images.map { image -> image.nameWithoutExtension },
        )
        writeAndroidResources(
            kotlinOutputRoot = androidKotlinOutput,
            resourcesOutputRoot = androidResourcesOutput,
            packageName = packageName,
            androidNamespace = androidNamespace.get(),
            accessorName = accessorName,
            namespace = namespace,
            localeStrings = localeStrings,
            images = images,
        )
        writeAppleResources(
            outputRoot = appleOutput,
            accessorName = accessorName,
            namespace = namespace,
            sourceLanguage = sourceLanguage,
            localeStrings = localeStrings,
            images = images,
        )
    }
}

private data class LocaleStrings(
    val qualifier: String?,
    val appleLocale: String,
    val strings: Map<String, String>,
)

private fun readLocaleStrings(
    sourceRoot: File,
    sourceLanguage: String,
): List<LocaleStrings> =
    sourceRoot
        .listFiles { file -> file.isDirectory && file.name.startsWith("values") }
        .orEmpty()
        .sortedBy { file -> file.name }
        .mapNotNull { directory ->
            val stringsFile = directory.resolve("strings.xml")
            if (!stringsFile.isFile) return@mapNotNull null

            val qualifier = directory.name.removePrefix("values").removePrefix("-").ifEmpty { null }
            LocaleStrings(
                qualifier = qualifier,
                appleLocale = qualifier?.toAppleLocale() ?: sourceLanguage,
                strings = readStrings(stringsFile),
            )
        }

private fun readStrings(file: File): Map<String, String> {
    val root = parseXml(file).documentElement
    check(root.tagName == "resources") {
        "$file must have a <resources> root."
    }
    val result = linkedMapOf<String, String>()
    for (index in 0 until root.childNodes.length) {
        val element = root.childNodes.item(index) as? Element ?: continue
        check(element.tagName == "string") {
            "$file only supports <string> entries, found <${element.tagName}>."
        }
        val name = element.getAttribute("name")
        check(name.matches(RESOURCE_NAME)) {
            "Invalid resource name '$name' in $file."
        }
        check(result.put(name, element.textContent) == null) {
            "Duplicate string '$name' in $file."
        }
    }
    return result
}

private fun validateResources(
    defaultStrings: Map<String, String>,
    localeStrings: List<LocaleStrings>,
    images: List<File>,
) {
    check(defaultStrings.isNotEmpty()) {
        "The default strings.xml must contain at least one string."
    }
    localeStrings.forEach { locale ->
        val unknown = locale.strings.keys - defaultStrings.keys
        check(unknown.isEmpty()) {
            "${locale.qualifier ?: "default"} defines unknown strings: ${unknown.sorted()}."
        }
    }
    val duplicateImages =
        images
            .groupBy { image -> image.nameWithoutExtension }
            .filterValues { files -> files.size > 1 }
            .keys
    check(duplicateImages.isEmpty()) {
        "Duplicate images: ${duplicateImages.sorted()}."
    }
    images.forEach { image ->
        check(image.nameWithoutExtension.matches(RESOURCE_NAME)) {
            "Invalid image resource name '${image.nameWithoutExtension}'."
        }
    }
}

private fun writeCommonAccessors(
    outputRoot: File,
    packageName: String,
    accessorName: String,
    namespace: String,
    stringNames: List<String>,
    imageNames: List<String>,
) {
    val output = outputRoot.packageDirectory(packageName).resolve("$accessorName.generated.kt")
    output.writeText(
        buildString {
            appendLine("// Generated by generateFlareUiResources. Do not edit.")
            appendLine()
            appendLine("package $packageName")
            appendLine()
            appendLine("import dev.dimension.flare.flareui.FlareImageResource")
            appendLine("import dev.dimension.flare.flareui.FlareResourceKey")
            appendLine("import dev.dimension.flare.flareui.FlareStringResource")
            appendLine()
            appendLine("public object $accessorName {")
            appendLine("    public object Strings {")
            stringNames.forEach { name ->
                appendLine("        public val ${name.toKotlinIdentifier()}: FlareStringResource =")
                appendLine("            FlareStringResource(")
                appendLine("                FlareResourceKey(")
                appendLine("                    resourceNamespace = ${namespace.asKotlinString()},")
                appendLine("                    name = ${name.asKotlinString()},")
                appendLine("                ),")
                appendLine("            )")
            }
            appendLine("    }")
            appendLine()
            appendLine("    public object Images {")
            imageNames.forEach { name ->
                appendLine("        public val ${name.toKotlinIdentifier()}: FlareImageResource =")
                appendLine("            FlareImageResource(")
                appendLine("                FlareResourceKey(")
                appendLine("                    resourceNamespace = ${namespace.asKotlinString()},")
                appendLine("                    name = ${name.asKotlinString()},")
                appendLine("                ),")
                appendLine("            )")
            }
            appendLine("    }")
            appendLine("}")
        },
    )
}

private fun writeAndroidResources(
    kotlinOutputRoot: File,
    resourcesOutputRoot: File,
    packageName: String,
    androidNamespace: String,
    accessorName: String,
    namespace: String,
    localeStrings: List<LocaleStrings>,
    images: List<File>,
) {
    localeStrings.forEach { locale ->
        val directoryName = locale.qualifier?.let { "values-$it" } ?: "values"
        val output = resourcesOutputRoot.resolve("$directoryName/strings.xml")
        output.parentFile.mkdirs()
        output.writeText(
            buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                appendLine("<resources>")
                locale.strings.toSortedMap().forEach { (name, value) ->
                    appendLine(
                        "    <string name=\"${androidResourceName(namespace, name)}\">" +
                            "${value.escapeAndroidXml()}</string>",
                    )
                }
                appendLine("</resources>")
            },
        )
    }

    val drawableDirectory = resourcesOutputRoot.resolve("drawable").apply(File::mkdirs)
    images.forEach { image ->
        drawableDirectory
            .resolve("${androidResourceName(namespace, image.nameWithoutExtension)}.xml")
            .writeText(convertSvgToAndroidVector(image))
    }

    val androidAccessorName =
        accessorName.removeSuffix("Resources") + "AndroidResources"
    val output =
        kotlinOutputRoot.packageDirectory(packageName)
            .resolve("$androidAccessorName.generated.kt")
    val defaultStrings =
        localeStrings.single { locale -> locale.qualifier == null }.strings.keys.sorted()
    output.writeText(
        buildString {
            appendLine("// Generated by generateFlareUiResources. Do not edit.")
            appendLine()
            appendLine("package $packageName")
            appendLine()
            appendLine("import dev.dimension.flare.flareui.AndroidFlareResourceResolver")
            appendLine("import dev.dimension.flare.flareui.FlareImageResource")
            appendLine("import dev.dimension.flare.flareui.FlareStringResource")
            appendLine("import $androidNamespace.R")
            appendLine()
            appendLine("public object $androidAccessorName : AndroidFlareResourceResolver {")
            appendLine(
                "    override fun stringId(resource: FlareStringResource): Int =",
            )
            appendLine("        when (resource) {")
            defaultStrings.forEach { name ->
                appendLine(
                    "            $accessorName.Strings.${name.toKotlinIdentifier()} -> " +
                        "R.string.${androidResourceName(namespace, name)}",
                )
            }
            appendLine(
                "            else -> error(\"Unknown string resource: \${resource.key.qualifiedName}\")",
            )
            appendLine("        }")
            appendLine()
            appendLine(
                "    override fun imageId(resource: FlareImageResource): Int =",
            )
            appendLine("        when (resource) {")
            images.forEach { image ->
                val name = image.nameWithoutExtension
                appendLine(
                    "            $accessorName.Images.${name.toKotlinIdentifier()} -> " +
                        "R.drawable.${androidResourceName(namespace, name)}",
                )
            }
            appendLine(
                "            else -> error(\"Unknown image resource: \${resource.key.qualifiedName}\")",
            )
            appendLine("        }")
            appendLine("}")
        },
    )
}

private fun writeAppleResources(
    outputRoot: File,
    accessorName: String,
    namespace: String,
    sourceLanguage: String,
    localeStrings: List<LocaleStrings>,
    images: List<File>,
) {
    val tableName = namespace.toPlatformIdentifier()
    outputRoot.resolve("$tableName.xcstrings").writeText(
        renderStringCatalog(
            sourceLanguage = sourceLanguage,
            localeStrings = localeStrings,
        ),
    )

    val catalog = outputRoot.resolve("$accessorName.xcassets").apply(File::mkdirs)
    catalog.resolve("Contents.json").writeText(
        """
        {
          "info" : {
            "author" : "xcode",
            "version" : 1
          }
        }
        """.trimIndent() + "\n",
    )
    images.forEach { image ->
        val assetName = androidResourceName(namespace, image.nameWithoutExtension)
        val imageSet = catalog.resolve("$assetName.imageset").apply(File::mkdirs)
        image.copyTo(imageSet.resolve(image.name), overwrite = true)
        imageSet.resolve("Contents.json").writeText(
            """
            {
              "images" : [
                {
                  "filename" : "${image.name}",
                  "idiom" : "universal"
                }
              ],
              "info" : {
                "author" : "xcode",
                "version" : 1
              },
              "properties" : {
                "preserves-vector-representation" : true,
                "template-rendering-intent" : "template"
              }
            }
            """.trimIndent() + "\n",
        )
    }
}

private fun renderStringCatalog(
    sourceLanguage: String,
    localeStrings: List<LocaleStrings>,
): String {
    val defaultStrings =
        localeStrings.single { locale -> locale.qualifier == null }.strings
    val entries =
        defaultStrings.keys.sorted().joinToString(",\n") { name ->
            val localizations =
                localeStrings
                    .mapNotNull { locale ->
                        locale.strings[name]?.let { value ->
                            """
                            "${locale.appleLocale.jsonEscaped()}" : {
                              "stringUnit" : {
                                "state" : "translated",
                                "value" : "${value.jsonEscaped()}"
                              }
                            }
                            """.trimIndent()
                        }
                    }.joinToString(",\n")
                    .prependIndent("        ")
            """
            "${name.jsonEscaped()}" : {
              "localizations" : {
$localizations
              }
            }
            """.trimIndent().prependIndent("    ")
        }
    return """
        {
          "sourceLanguage" : "${sourceLanguage.jsonEscaped()}",
          "strings" : {
$entries
          },
          "version" : "1.0"
        }
        """.trimIndent() + "\n"
}

internal fun convertSvgToAndroidVector(svg: File): String {
    val source = svg.readText()
    val converterInput =
        if (CURRENT_COLOR in source) {
            File.createTempFile("flareui-svg2vector-", ".svg").apply {
                writeText(source.replace(CURRENT_COLOR, TEMPLATE_COLOR))
            }
        } else {
            svg
        }

    return try {
        val output = ByteArrayOutputStream()
        val diagnostics =
            output.use { stream ->
                Svg2Vector.parseSvgToXml(converterInput.toPath(), stream)
            }
        check(diagnostics.isBlank()) {
            "Unable to convert $svg to Android VectorDrawable:\n${diagnostics.trim()}"
        }
        output.toByteArray().toString(StandardCharsets.UTF_8)
    } finally {
        if (converterInput != svg) {
            converterInput.delete()
        }
    }
}

private fun parseXml(file: File) =
    DocumentBuilderFactory.newInstance()
        .apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }.newDocumentBuilder()
        .parse(file)

private fun DirectoryProperty.clean(): File =
    get().asFile.apply {
        deleteRecursively()
        mkdirs()
    }

private fun File.packageDirectory(packageName: String): File =
    resolve(packageName.replace('.', '/')).apply(File::mkdirs)

private fun String.toAppleLocale(): String =
    if (startsWith("b+")) {
        removePrefix("b+").replace('+', '-')
    } else {
        replace("-r", "-")
    }

private fun defaultResourceNamespace(projectPath: String): String =
    projectPath
        .trim(':')
        .split(':')
        .joinToString(".") { segment -> segment.lowercase() }

private fun String.toPlatformIdentifier(): String =
    lowercase().map { character ->
        if (character.isLetterOrDigit()) character else '_'
    }.joinToString("")

private fun androidResourceName(
    namespace: String,
    name: String,
): String = "${namespace.toPlatformIdentifier()}__$name"

private fun String.toUpperCamelCase(): String =
    split(IDENTIFIER_SEPARATOR)
        .filter(String::isNotBlank)
        .joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }

private fun String.toKotlinIdentifier(): String {
    val identifier =
        split(IDENTIFIER_SEPARATOR)
            .filter(String::isNotBlank)
            .mapIndexed { index, part ->
                if (index == 0) {
                    part.lowercase()
                } else {
                    part.lowercase().replaceFirstChar(Char::uppercaseChar)
                }
            }.joinToString("")
    return if (identifier in KOTLIN_KEYWORDS) "`$identifier`" else identifier
}

private fun String.asKotlinString(): String = "\"${jsonEscaped()}\""

private fun String.escapeAndroidXml(): String =
    xmlEscaped()
        .replace("'", "\\'")
        .replace("\n", "\\n")

private fun String.xmlEscaped(): String =
    replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun String.jsonEscaped(): String =
    buildString {
        this@jsonEscaped.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else ->
                    if (character.code < 0x20) {
                        append("\\u%04x".format(character.code))
                    } else {
                        append(character)
                    }
            }
        }
    }

private val RESOURCE_NAME = Regex("[a-z][a-z0-9_]*")
private val IDENTIFIER_SEPARATOR = Regex("[^A-Za-z0-9]+|_+")
private const val CURRENT_COLOR = "currentColor"
private const val TEMPLATE_COLOR = "#000000"
private val KOTLIN_KEYWORDS =
    setOf(
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while",
    )
