package dev.dimension.flare.feature.plugin.installer

import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.manifest.PluginCatalogBundleV1
import dev.dimension.flare.feature.plugin.manifest.PluginManifestV1
import dev.dimension.flare.feature.plugin.manifest.PluginTextV1
import dev.dimension.flare.feature.plugin.manifest.isValidLocaleTag
import dev.dimension.flare.feature.plugin.manifest.validate
import kotlinx.serialization.decodeFromString
import okio.FileSystem
import okio.Path

internal data class ValidatedPluginPackage(
    val manifest: PluginManifestV1,
    val catalogs: Map<String, Map<String, String>>,
    val icon: ByteArray,
    val warnings: List<String>,
)

internal class PluginPackageValidator(
    fileSystem: FileSystem,
    private val registrationInspector: PluginRegistrationInspector = PluginRegistrationInspector(),
) {
    private val archiveReader = FppArchiveReader(fileSystem)

    suspend fun validate(path: Path): ValidatedPluginPackage {
        val archive = archiveReader.read(path)
        val manifest = decodeManifest(archive.files.getValue(FppPaths.MANIFEST))
        val source = decodeUtf8(archive.files.getValue(FppPaths.ENTRY), FppPaths.ENTRY)
        require('\u0000' !in source) { "plugin.js contains a null character" }
        val methodTable = registrationInspector.inspect(source)
        val manifestValidation = manifest.validate(methodTable)
        manifestValidation.requireValid()

        val catalogs = decodeCatalogs(archive)
        validatePluginCatalogs(manifest, catalogs)
        val icon = archive.files.getValue(FppPaths.ICON)
        validatePng(icon)
        return ValidatedPluginPackage(
            manifest = manifest,
            catalogs = catalogs,
            icon = icon,
            warnings = manifestValidation.warnings.map { "${it.path}: ${it.message}" },
        )
    }

    private fun decodeManifest(bytes: ByteArray): PluginManifestV1 = PluginJsonV1.decodeFromString(decodeUtf8(bytes, FppPaths.MANIFEST))

    private fun decodeCatalogs(archive: FppArchive): Map<String, Map<String, String>> =
        archive.files
            .filterKeys { it.startsWith(FppPaths.LOCALE_PREFIX) }
            .map { (path, bytes) ->
                val name = path.removePrefix(FppPaths.LOCALE_PREFIX)
                require('/' !in name && name.endsWith(".json")) { "Invalid locale catalog path: $path" }
                val locale = name.removeSuffix(".json")
                require(isValidLocaleTag(locale)) { "Invalid locale tag: $locale" }
                val values = PluginJsonV1.decodeFromString<Map<String, String>>(decodeUtf8(bytes, path))
                require(values.size <= MAX_CATALOG_KEYS) { "Too many strings in $path" }
                values.forEach { (key, value) ->
                    require(CATALOG_KEY.matches(key)) { "Invalid localization key in $path: $key" }
                    require(value.isNotBlank() && value.length <= MAX_CATALOG_VALUE_LENGTH) {
                        "Invalid localized value in $path: $key"
                    }
                }
                locale to values
            }.toMap()
}

internal fun validatePluginCatalogs(
    manifest: PluginManifestV1,
    catalogs: Map<String, Map<String, String>>,
) {
    require(catalogs.size <= FppLimits.MAX_CATALOG_COUNT) { "Too many locale catalogs" }
    require(catalogs.keys.all(::isValidLocaleTag)) { "Invalid locale catalog" }
    require(
        catalogs.keys
            .map(String::lowercase)
            .distinct()
            .size == catalogs.size,
    ) { "Duplicate locale catalog" }
    catalogs.forEach { (locale, values) ->
        require(values.size <= MAX_CATALOG_KEYS) { "Too many strings in $locale" }
        values.forEach { (key, value) ->
            require(CATALOG_KEY.matches(key)) { "Invalid localization key in $locale: $key" }
            require(value.isNotBlank() && value.length <= MAX_CATALOG_VALUE_LENGTH) {
                "Invalid localized value in $locale: $key"
            }
        }
    }
    val requiredKeys = manifest.localizedKeys()
    if (requiredKeys.isNotEmpty()) {
        val defaultCatalog =
            catalogs.entries.firstOrNull { it.key.equals(manifest.defaultLocale, ignoreCase = true) }?.value
                ?: error("Missing default locale catalog: ${manifest.defaultLocale}")
        val missing = requiredKeys - defaultCatalog.keys
        require(missing.isEmpty()) { "Default locale is missing keys: ${missing.sorted().joinToString()}" }
    }

    // Constructing the bundle here also guarantees the exact persisted shape remains decodable.
    PluginCatalogBundleV1(manifest.id, manifest.defaultLocale, catalogs)
}

private fun decodeUtf8(
    bytes: ByteArray,
    path: String,
): String =
    runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }
        .getOrElse { error("$path is not valid UTF-8") }

private fun validatePng(bytes: ByteArray) {
    require(bytes.size >= PNG_MIN_BYTES) { "Platform icon is not a PNG" }
    require(PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }) { "Platform icon is not a PNG" }
    var offset = PNG_SIGNATURE.size
    var chunkIndex = 0
    var hasImageData = false
    var hasEnd = false
    while (offset < bytes.size) {
        require(bytes.size - offset >= PNG_CHUNK_OVERHEAD) { "Platform icon has a truncated chunk" }
        val length = bytes.uint32BigEndian(offset)
        require(length <= Int.MAX_VALUE.toLong()) { "Platform icon chunk is too large" }
        val chunkEndLong = offset.toLong() + PNG_CHUNK_OVERHEAD + length
        require(chunkEndLong <= bytes.size) { "Platform icon has a truncated chunk" }
        val chunkEnd = chunkEndLong.toInt()
        val type = bytes.copyOfRange(offset + 4, offset + 8).decodeToString()
        if (chunkIndex == 0) {
            require(type == "IHDR" && length == PNG_IHDR_LENGTH) { "Platform icon has an invalid IHDR" }
            val width = bytes.uint32BigEndian(offset + 8)
            val height = bytes.uint32BigEndian(offset + 12)
            require(width in 1..MAX_ICON_DIMENSION && height in 1..MAX_ICON_DIMENSION) {
                "Platform icon dimensions are invalid"
            }
        } else {
            require(type != "IHDR") { "Platform icon contains multiple IHDR chunks" }
        }
        if (type == "IDAT") hasImageData = true
        if (type == "IEND") {
            require(length == 0L && chunkEnd == bytes.size) { "Platform icon has an invalid IEND" }
            hasEnd = true
        }
        offset = chunkEnd
        chunkIndex++
        if (hasEnd) break
    }
    require(hasImageData && hasEnd) { "Platform icon is missing image data" }
}

private fun ByteArray.uint32BigEndian(offset: Int): Long =
    ((this[offset].toLong() and 0xff) shl 24) or
        ((this[offset + 1].toLong() and 0xff) shl 16) or
        ((this[offset + 2].toLong() and 0xff) shl 8) or
        (this[offset + 3].toLong() and 0xff)

private fun PluginManifestV1.localizedKeys(): Set<String> =
    buildSet {
        fun addText(text: PluginTextV1?) {
            if (text is PluginTextV1.Localized) add(text.key)
        }
        addText(name)
        addText(description)
        addText(platform.name)
        addText(platform.description)
        platform.timelines.forEach { addText(it.title) }
        platform.profileTabs.forEach { addText(it.title) }
        platform.loginMethods.forEach { method ->
            addText(method.title)
            addText(method.description)
            method.fields.forEach { field ->
                addText(field.label)
                addText(field.placeholder)
            }
        }
    }

private const val MAX_CATALOG_KEYS = 2_048
private const val MAX_CATALOG_VALUE_LENGTH = 4_096
private const val PNG_MIN_BYTES = 45
private const val PNG_CHUNK_OVERHEAD = 12
private const val PNG_IHDR_LENGTH = 13L
private const val MAX_ICON_DIMENSION = 1_024L
private val CATALOG_KEY = Regex("[A-Za-z][A-Za-z0-9_.-]{0,127}")
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
