package dev.dimension.flare.server.common

internal object Log {
    fun d(tag: String, message: String) {
        println("DEBUG: [$tag] $message")
    }

    fun e(tag: String, message: String) {
        println("ERROR: [$tag] $message")
    }

    fun i(tag: String, message: String) {
        println("INFO: [$tag] $message")
    }

    fun w(tag: String, message: String) {
        println("WARN: [$tag] $message")
    }
}