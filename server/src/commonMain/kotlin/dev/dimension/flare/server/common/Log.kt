package dev.dimension.flare.server.common

import io.ktor.util.logging.KtorSimpleLogger

internal object Log {
    internal val LOGGER = KtorSimpleLogger("dev.dimension.flare.server")

    fun trace(tag: String, message: String) {
        LOGGER.trace("$tag: $message")
    }

    fun d(tag: String, message: String) {
        LOGGER.debug("$tag: $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            LOGGER.error("$tag: $message", throwable)
        } else {
            LOGGER.error("$tag: $message")
        }
    }

    fun i(tag: String, message: String) {
        LOGGER.info("$tag: $message")
    }

    fun w(tag: String, message: String) {
        LOGGER.warn("$tag: $message")
    }
}