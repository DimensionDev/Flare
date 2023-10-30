package dev.dimension.flare.common

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

actual class FileItem(

) {
    actual val name: String? = TODO()
    actual suspend fun readBytes(): ByteArray = TODO()
}
