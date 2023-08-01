package dev.dimension.flare.common

import com.moriatsushi.koject.Provides
import com.moriatsushi.koject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Singleton
@Provides
internal fun provideIOCoroutineScope() = CoroutineScope(Dispatchers.IO)
