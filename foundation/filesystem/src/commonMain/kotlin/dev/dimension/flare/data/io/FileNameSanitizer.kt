package dev.dimension.flare.data.io

private val invalidFileNameCharsRegex = Regex("[^A-Za-z0-9._-]")

public fun String.sanitizeFileName(): String = replace(invalidFileNameCharsRegex, "_")
