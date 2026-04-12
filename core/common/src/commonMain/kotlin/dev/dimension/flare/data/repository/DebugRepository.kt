package dev.dimension.flare.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

public object DebugRepository {
    private const val MAX_MESSAGES = 25
    private const val DEBUG_MAX_MESSAGES = 1000
    private val _messages = MutableStateFlow<List<String>>(emptyList())
    private val _enabled = MutableStateFlow(false)
    private val scope = CoroutineScope(Dispatchers.Default)

    public val enabled: SharedFlow<Boolean> get() = _enabled.asSharedFlow()
    public val messages: SharedFlow<List<String>> get() = _messages.asSharedFlow()

    public fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) {
            _messages.value = _messages.value.takeLast(MAX_MESSAGES)
        }
    }

    private val messageLimit: Int
        get() = if (_enabled.value) DEBUG_MAX_MESSAGES else MAX_MESSAGES

    public fun log(message: String) {
        if (_enabled.value) {
            scope.launch {
                _messages.value = (_messages.value + message).takeLast(messageLimit)
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
            _messages.value = (_messages.value + message).takeLast(messageLimit)
        }
    }

    public fun clear() {
        scope.launch {
            _messages.value = emptyList()
        }
    }

    public fun printToString(): String = _messages.value.joinToString(separator = "\n")
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
    } catch (e: Exception) {
        DebugRepository.error(e)
        Result.failure(e)
    }
