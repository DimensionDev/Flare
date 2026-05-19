package dev.dimension.flare.data.io

import okio.FileSystem
import okio.SYSTEM

public actual fun defaultFileStorage(): FileStorage = OkioFileStorage(FileSystem.SYSTEM)
