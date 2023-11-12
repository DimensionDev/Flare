package dev.dimension.flare.common

object ResultHelper {
    fun <T> unwrapResult(result: Result<T>): T? {
        return result.getOrNull()
    }

    fun <T> unwrapResult(
        result: Result<T>,
        default: T,
    ): T {
        return result.getOrDefault(default)
    }

    fun <T> unwrapResult(
        result: Result<T>,
        default: (() -> T),
    ): T {
        return result.getOrElse { default() }
    }

    fun <T> unwrapException(result: Result<T>): Throwable? {
        return result.exceptionOrNull()
    }

    fun <T> isSuccess(result: Result<T>): Boolean {
        return result.isSuccess
    }

    fun <T> isFailure(result: Result<T>): Boolean {
        return result.isFailure
    }
}
