package dev.dimension.flare.common

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class JvmOnDeviceAI: OnDeviceAI {
    override suspend fun isAvailable(): Boolean =
        withContext(Dispatchers.IO) {
            FoundationModelsBridge.isAvailable()
        }

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        withContext(Dispatchers.IO) {
            FoundationModelsBridge.generate(prompt)
        }

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        withContext(Dispatchers.IO) {
            FoundationModelsBridge.generate(prompt)
        }
}

private object FoundationModelsBridge {
    private const val LIBRARY_NAME = "flare_foundation_models_bridge"

    private val nativeApi: FoundationModelsBridgeLibrary? by lazy {
        if (!Platform.isMac()) {
            return@lazy null
        }

        runCatching {
            Native.load(LIBRARY_NAME, FoundationModelsBridgeLibrary::class.java)
        }.getOrNull()
    }

    fun isAvailable(): Boolean {
        val api = nativeApi ?: return false
        if (api.flare_foundation_models_is_supported() == 0) {
            return false
        }
        return runCatching {
            callWithErrorHandling(api) {
                api.flare_foundation_models_is_available(it.errorCode, it.errorMessage) != 0
            } ?: false
        }.getOrDefault(false)
    }

    fun generate(prompt: String): String? {
        val api = nativeApi ?: return null
        if (api.flare_foundation_models_is_supported() == 0) {
            return null
        }
        return runCatching {
            callWithErrorHandling(api) { refs ->
                val responsePointer =
                    api.flare_foundation_models_generate(
                        prompt,
                        refs.errorCode,
                        refs.errorMessage,
                    ) ?: return@callWithErrorHandling null
                try {
                    responsePointer.getString(0, Charsets.UTF_8.name())
                } finally {
                    api.flare_foundation_models_free_string(responsePointer)
                }
            }
        }.getOrNull()
    }

    private fun <T> callWithErrorHandling(
        api: FoundationModelsBridgeLibrary,
        block: (NativeErrorRefs) -> T?,
    ): T? {
        val refs = NativeErrorRefs()
        return try {
            val result = block(refs)
            if (refs.errorCode.value == 0) {
                result
            } else {
                null
            }
        } finally {
            refs.free(api)
        }
    }
}

private class NativeErrorRefs {
    val errorCode = IntByReference(0)
    val errorMessage = PointerByReference()

    fun free(api: FoundationModelsBridgeLibrary) {
        errorMessage.value?.let {
            api.flare_foundation_models_free_string(it)
            errorMessage.value = null
        }
    }
}

@Suppress("FunctionName")
private interface FoundationModelsBridgeLibrary : Library {
    fun flare_foundation_models_is_supported(): Int

    fun flare_foundation_models_is_available(
        errorCode: IntByReference?,
        errorMessage: PointerByReference?,
    ): Int

    fun flare_foundation_models_generate(
        prompt: String?,
        errorCode: IntByReference?,
        errorMessage: PointerByReference?,
    ): Pointer?

    fun flare_foundation_models_free_string(value: Pointer?)
}
