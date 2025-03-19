package dev.dimension.flare.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

internal object DebugRepository {
    private val _messages = MutableStateFlow<List<String>>(emptyList())
    private val _enabled = MutableStateFlow(false)
    val enabled get() = _enabled.asSharedFlow()
    val messages get() = _messages.asSharedFlow()

    fun setEnabled(enabled: Boolean) {
        this._enabled.value = enabled
    }

    fun log(message: String) {
        if (_enabled.value) {
            _messages.value = _messages.value + message
        }
    }

    fun error(exception: Throwable) {
        val message =
            buildString {
                appendLine("Error: ${exception.message}")
                appendLine("Stacktrace:")
                append(exception.stackTraceToString())
            }
        _messages.value = _messages.value + message
    }

    fun clear() {
        _messages.value = emptyList()
    }

    fun printToString(): String = _messages.value.joinToString(separator = "\n")
}

/**
 * Executes the given [block] function and returns its encapsulated result as [Result].
 *
 * If the [block] executes successfully, the result is wrapped in [Result.success].
 * If an exception is thrown during execution:
 * - The exception is logged to [DebugRepository] with its message and stack trace
 * - The exception is wrapped in [Result.failure] and returned
 *
 * @param R The return type of the block function
 * @param block The function to execute
 * @return A [Result] object containing either the successful result or the failure exception
 */
internal inline fun <R> tryRun(block: () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (e: Throwable) {
        DebugRepository.error(e)
        Result.failure(e)
    }
