package dev.dimension.flare.data.model

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.io.FileStorage
import kotlinx.coroutines.withContext
import okio.Path

internal suspend fun legacySettingsFileExists(
    fileStorage: FileStorage,
    path: Path,
): Boolean =
    withContext(PlatformDispatchers.IO) {
        runCatching {
            fileStorage.exists(path)
        }.getOrDefault(false)
    }

internal suspend fun readLegacySettingsFile(
    fileStorage: FileStorage,
    path: Path,
): ByteArray? =
    withContext(PlatformDispatchers.IO) {
        runCatching {
            fileStorage.read(path)
        }.getOrNull()
    }

internal suspend fun deleteLegacySettingsFile(
    fileStorage: FileStorage,
    path: Path,
) {
    withContext(PlatformDispatchers.IO) {
        runCatching {
            if (fileStorage.exists(path)) {
                fileStorage.delete(path)
            }
        }
    }
}
