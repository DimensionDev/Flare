package dev.dimension.flare.data.io

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.koin.core.annotation.Single

@Single(binds = [FileStorage::class])
internal class AppleFileStorage :
    OkioFileStorage(
        fileSystem = FileSystem.SYSTEM,
        root = AppleDataDirectories.dataStoreRootDirectory().toPath(),
    )
