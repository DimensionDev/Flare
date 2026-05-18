package dev.dimension.flare.data.repository

import okio.FileSystem
import okio.SYSTEM

internal actual fun defaultDraftMediaStorage(): DraftMediaStorage = FileSystemDraftMediaStorage(FileSystem.SYSTEM)
