package dev.dimension.flare.data.io

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.koin.core.annotation.Single

@Single(binds = [FileStorage::class])
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
internal class AppleFileStorage :
    OkioFileStorage(
        fileSystem = FileSystem.SYSTEM,
        cacheRoot =
            (
                platform.Foundation.NSSearchPathForDirectoriesInDomains(
                    platform.Foundation.NSCachesDirectory,
                    platform.Foundation.NSUserDomainMask,
                    true,
                )[0] as String
            ).toPath().resolve(platform.Foundation.NSBundle.mainBundle.bundleIdentifier ?: "dev.dimension.flare"),
        root = AppleDataDirectories.dataStoreRootDirectory().toPath(),
    )
