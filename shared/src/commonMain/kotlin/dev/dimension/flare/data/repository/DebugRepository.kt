package dev.dimension.flare.data.repository

import dev.dimension.flare.common.PlatformDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@PublishedApi
internal object DebugRepository {
    private const val MAX_MESSAGES = 25
    private const val DEBUG_MAX_MESSAGES = 1000
    private val _messages = MutableStateFlow<List<String>>(emptyList())
    private val _enabled = MutableStateFlow(false)
    private val scope = CoroutineScope(PlatformDispatchers.IO)

    val enabled get() = _enabled.asSharedFlow()
    val messages get() = _messages.asSharedFlow()

    internal fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) {
            _messages.value = _messages.value.takeLast(MAX_MESSAGES)
        }
    }

    private val messageLimit: Int
        get() = if (_enabled.value) DEBUG_MAX_MESSAGES else MAX_MESSAGES

    internal fun log(message: String) {
        if (_enabled.value) {
            scope.launch {
                _messages.value = (_messages.value + LogSanitizer.sanitize(message)).takeLast(messageLimit)
            }
        }
    }

    public fun error(exception: Throwable) {
        if (exception is CancellationException) {
            // Ignore cancellation exceptions
            return
        }
        scope.launch {
//            exception.printStackTrace()
            val message =
                buildString {
                    appendLine("Error: ${exception.message}")
                    appendLine("Stacktrace:")
                    append(exception.stackTraceToString())
                }
            _messages.value = (_messages.value + LogSanitizer.sanitize(message)).takeLast(messageLimit)
        }
    }

    internal fun clear() {
        scope.launch {
            _messages.value = emptyList()
        }
    }

    internal fun printToString(): String = LogSanitizer.sanitize(_messages.value.joinToString(separator = "\n"))
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
public inline fun <R> tryRun(block: () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (e: Throwable) {
        DebugRepository.error(e)
        Result.failure(e)
    }
