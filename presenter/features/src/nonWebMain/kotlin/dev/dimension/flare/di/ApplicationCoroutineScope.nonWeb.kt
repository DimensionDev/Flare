package dev.dimension.flare.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual fun applicationCoroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.IO)
