package dev.dimension.flare.common

object ResultHelper {
    fun <T> unwrapResult(result: Result<T>): T? = result.getOrNull()

    fun <T> unwrapResult(
        result: Result<T>,
        default: T,
    ): T = result.getOrDefault(default)

    fun <T> unwrapResult(
        result: Result<T>,
        default: (() -> T),
    ): T = result.getOrElse { default() }

    fun <T> unwrapException(result: Result<T>): Throwable? = result.exceptionOrNull()

    fun <T> isSuccess(result: Result<T>): Boolean = result.isSuccess

    fun <T> isFailure(result: Result<T>): Boolean = result.isFailure
}
