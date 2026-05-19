package dev.dimension.flare.data.draft

import okio.FileSystem
import okio.SYSTEM

internal actual fun defaultDraftMediaStorage(): DraftMediaStorage = FileSystemDraftMediaStorage(FileSystem.SYSTEM)
