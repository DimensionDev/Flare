package dev.dimension.flare.data.io

import org.koin.core.annotation.Single

@Single(binds = [FileStorage::class])
internal class WebFileStorage : FileStorage by InMemoryFileStorage()
