package dev.dimension.flare.feature.plugin.runtime

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuickJsAndroidSmokeTest {
    @Test
    fun evaluatesPromiseAndCloses() {
        runBlocking {
            val quickJs = QuickJs.create(Dispatchers.Default)
            try {
                assertEquals(42, quickJs.evaluate<Int>("await Promise.resolve(21 * 2)"))
                quickJs.evaluationTimeoutMillis = 100
                assertFailsWith<QuickJsInterruptedException> {
                    quickJs.evaluate<Unit>("while (true) {}")
                }
            } finally {
                quickJs.close()
            }

            assertFailsWith<QuickJsException> {
                quickJs.evaluate<Unit>("undefined")
            }
        }
    }
}
