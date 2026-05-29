package dev.dimension.flare.data.io

import android.content.Context
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.SYSTEM
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single(binds = [FileStorage::class])
internal class AndroidFileStorage(
    @Provided private val context: Context,
) : OkioFileStorage(
        fileSystem = FileSystem.SYSTEM,
        root =
            context
                .filesDir
                .toOkioPath()
                .resolve("datastore"),
    )
