package dev.dimension.flare.data.io

internal expect object AppleDataDirectories {
    fun dataStoreRootDirectory(): String

    fun databaseRootDirectory(isCache: Boolean): String
}
