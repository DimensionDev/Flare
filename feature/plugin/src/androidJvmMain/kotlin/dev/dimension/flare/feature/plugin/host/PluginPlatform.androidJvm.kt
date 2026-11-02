package dev.dimension.flare.feature.plugin.host

import java.security.SecureRandom
import java.util.UUID

private val secureRandom = SecureRandom()

internal actual fun platformUuid(): String = UUID.randomUUID().toString()

internal actual fun platformSecureRandom(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)
