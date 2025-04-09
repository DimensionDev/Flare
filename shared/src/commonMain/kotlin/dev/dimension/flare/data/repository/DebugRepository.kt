package dev.dimension.flare.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal object DebugRepository {
    private const val MAX_MESSAGES = 25
    private val _messages = MutableStateFlow<List<String>>(emptyList())
    private val _enabled = MutableStateFlow(false)
    private val scope = CoroutineScope(Dispatchers.IO)

    val enabled get() = _enabled.asSharedFlow()
    val messages get() = _messages.asSharedFlow()

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
    }

    fun log(message: String) {
        if (_enabled.value) {
            scope.launch {
                _messages.value = (_messages.value + message).takeLast(MAX_MESSAGES)
            }
        }
    }

    fun error(exception: Throwable) {
        if (exception is CancellationException) {
            // Ignore cancellation exceptions
            return
        }
        scope.launch {
            val message =
                buildString {
                    appendLine("Error: ${exception.message}")
                    appendLine("Stacktrace:")
                    append(exception.stackTraceToString())
                }
            _messages.value = (_messages.value + message).takeLast(MAX_MESSAGES)
        }
    }

    fun clear() {
        scope.launch {
            _messages.value = emptyList()
        }
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
        e.printStackTrace()
        DebugRepository.error(e)
        Result.failure(e)
    }
