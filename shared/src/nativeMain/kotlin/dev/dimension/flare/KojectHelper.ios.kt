package dev.dimension.flare

import co.touchlab.kermit.Logger
import co.touchlab.kermit.XcodeSeverityWriter
import co.touchlab.kermit.platformLogWriter
import com.moriatsushi.koject.Koject
import com.moriatsushi.koject.start

object KojectHelper {
    fun start() {
        Koject.start()
    }
}