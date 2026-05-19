package dev.dimension.flare.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

internal actual fun applicationCoroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.Default)
