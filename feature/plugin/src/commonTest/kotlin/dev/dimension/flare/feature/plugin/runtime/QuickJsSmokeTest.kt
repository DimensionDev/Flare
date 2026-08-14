package dev.dimension.flare.feature.plugin.runtime

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import dev.dimension.flare.feature.plugin.abi.DISABLE_DYNAMIC_CODE_PRELUDE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuickJsSmokeTest {
    @Test
    fun evaluatesJavaScript() =
        withQuickJs { quickJs ->
            assertEquals(42, quickJs.evaluate<Int>("21 * 2"))
        }

    @Test
    fun awaitsPromise() =
        withQuickJs { quickJs ->
            assertEquals("Flare", quickJs.evaluate<String>("await Promise.resolve('Flare')"))
        }

    @Test
    fun pluginPolicyDisablesStringCompilation() =
        withQuickJs { quickJs ->
            quickJs.evaluate<Unit>(DISABLE_DYNAMIC_CODE_PRELUDE)

            assertEquals(
                "undefined,undefined,undefined,undefined",
                quickJs.evaluate<String>(
                    "[typeof eval, typeof Function, typeof (() => {}).constructor, " +
                        "typeof (async () => {}).constructor].join(',')",
                ),
            )
        }

    @Test
    fun interruptsRunawayEvaluation() =
        withQuickJs { quickJs ->
            quickJs.evaluationTimeoutMillis = 100
            assertFailsWith<QuickJsInterruptedException> {
                quickJs.evaluate<Unit>("while (true) {}")
            }
        }

    @Test
    fun rejectsEvaluationAfterClose() {
        runBlocking {
            val quickJs = QuickJs.create(Dispatchers.Default)
            quickJs.close()

            assertFailsWith<QuickJsException> {
                quickJs.evaluate<Unit>("undefined")
            }
        }
    }

    private fun withQuickJs(block: suspend (QuickJs) -> Unit) =
        runBlocking {
            val quickJs = QuickJs.create(Dispatchers.Default)
            try {
                block(quickJs)
            } finally {
                quickJs.close()
            }
        }
}
