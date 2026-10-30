package dev.dimension.flare.feature.plugin.runtime

import dev.dimension.flare.feature.plugin.abi.DISABLE_DYNAMIC_CODE_PRELUDE
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.installer.PluginRegistrationInspector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal expect suspend fun createTestPluginJavaScriptEngine(hostCall: PluginHostCall): PluginJavaScriptEngine

class PluginJavaScriptEngineTest {
    @Test
    fun classicScriptsAndPromisesPreserveUnicode() =
        withEngine { engine ->
            val text = "中文 e\u0301 👋🏽 👩‍💻\u0000end"
            assertEquals(text, engine.evaluate(JsonPrimitive(text).toString(), "unicode.js"))
            assertEquals(text, engine.evaluate("Promise.resolve(${JsonPrimitive(text)})", "promise.js", true))
            val error = assertFails { engine.evaluate("throw new Error(${JsonPrimitive(text)})", "error.js") }
            assertTrue(error.message.orEmpty().contains("中文"), error.message)
            assertEquals("42", engine.evaluate("String(21 * 2)", "next.js"))
        }

    @Test
    fun hostCallsCorrelateConcurrentRepliesAndPreserveJson() =
        withEngine(
            hostCall = { id, operation, arguments ->
                assertEquals("invocation", id)
                assertEquals("echo", operation)
                delay(if (arguments.contains("first")) 50 else 5)
                arguments
            },
        ) { engine ->
            val first = JsonPrimitive("first 中文 👩‍💻\u0000tail").toString()
            val second = JsonPrimitive("second").toString()
            assertEquals(
                "[$first,$second]",
                engine.evaluate(
                    "Promise.all([__flareHostCallV1('invocation','echo',JSON.stringify($first))," +
                        "__flareHostCallV1('invocation','echo',JSON.stringify($second))]).then(values => '[' + values.join(',') + ']')",
                    "host.js",
                    true,
                ),
            )
        }

    @Test
    fun largeScriptsAndEscapedHostMessagesCrossTheBridge() =
        withEngine(
            hostCall = { _, _, _ -> "\u0001".repeat(4 * 1024 * 1024) },
        ) { engine ->
            val source = "'" + "x".repeat(2 * 1024 * 1024) + "'; 'loaded'"
            assertEquals("loaded", engine.evaluate(source, "large.js"))
            assertEquals(
                (4 * 1024 * 1024).toString(),
                engine.evaluate("__flareHostCallV1('id','op','{}').then(value => String(value.length))", "large-host.js", true),
            )
        }

    @Test
    fun detachedRejectionsDoNotFailTheReturnedChain() =
        withEngine { engine ->
            register(engine, "Promise.reject(new Error('detached')); (async () => { throw null; })(); return 'success';")
            assertEquals("success", invoke(engine).getValue("value").jsonPrimitive.content)
        }

    @Test
    fun returnedRejectionsIncludingNullStillFail() =
        withEngine { engine ->
            register(engine, "return Promise.reject(request.reason);")
            for (reason in listOf("null", "'returned error'")) {
                val result = invoke(engine, "{ reason: $reason }")
                assertEquals("false", result.getValue("ok").jsonPrimitive.content)
                assertTrue(result.containsKey("fatal"))
            }
            assertTrue(invoke(engine).containsKey("fatal")) // undefined rejection
        }

    @Test
    fun caughtHostFailuresRemainCatchable() =
        withEngine(hostCall = { _, _, _ -> error("Host denied") }) { engine ->
            register(engine, "try { await flare.locale.current(); } catch (_) { return 'caught'; } return 'wrong';")
            assertEquals("caught", invoke(engine).getValue("value").jsonPrimitive.content)
        }

    @Test
    fun waitsForUnawaitedHostTasksAndTheirImmediateHostFollowups() =
        runBlocking<Unit> {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val secondFinished = CompletableDeferred<Unit>()
            val engine =
                createTestPluginJavaScriptEngine { _, operation, _ ->
                    if (operation == "locale.current") {
                        started.complete(Unit)
                        release.await()
                    } else {
                        delay(20)
                        secondFinished.complete(Unit)
                    }
                    "{\"ok\":true,\"value\":\"done\"}"
                }
            try {
                withTimeout(15_000) {
                    register(engine, "flare.locale.current().then(() => flare.crypto.uuid()); return 'result';")
                    val result = async { invoke(engine) }
                    started.await()
                    delay(30)
                    assertFalse(result.isCompleted)
                    release.complete(Unit)
                    assertEquals(
                        "result",
                        result
                            .await()
                            .getValue("value")
                            .jsonPrimitive.content,
                    )
                    assertTrue(secondFinished.isCompleted, "Follow-up Host task was not drained")
                }
            } finally {
                release.complete(Unit)
                engine.close()
            }
        }

    @Test
    fun waitsForHostTasksBeforeReportingReturnedErrors() =
        runBlocking<Unit> {
            val finished = CompletableDeferred<Unit>()
            val engine =
                createTestPluginJavaScriptEngine { _, _, _ ->
                    delay(50)
                    finished.complete(Unit)
                    "{\"ok\":true,\"value\":true}"
                }
            try {
                withTimeout(15_000) {
                    register(engine, "flare.locale.current(); throw new Error('returned');")
                    assertTrue(invoke(engine).containsKey("fatal"))
                    assertTrue(finished.isCompleted)
                }
            } finally {
                engine.close()
            }
        }

    @Test
    fun detachedHostRejectionDoesNotFailSuccessfulResult() =
        withEngine(
            hostCall = { _, _, _ ->
                delay(20)
                "{\"ok\":false,\"error\":{\"code\":\"denied\",\"message\":\"Denied\"}}"
            },
        ) { engine ->
            register(engine, "flare.locale.current(); return 'success';")
            assertEquals("success", invoke(engine).getValue("value").jsonPrimitive.content)
        }

    @Test
    fun hostBindingRejectsMalformedArguments() =
        withEngine(hostCall = { _, _, _ -> error("must not call Host") }) { engine ->
            for (arguments in listOf("", "'id','op'", "'id','op','{}','extra'", "'id',42,'{}'", "'id','op',{}")) {
                assertEquals(
                    "rejected",
                    engine.evaluate(
                        "__flareHostCallV1($arguments).then(() => 'wrong', () => 'rejected')",
                        "invalid-host.js",
                        true,
                    ),
                )
            }
        }

    @Test
    fun policyDisablesDownloadedJavaScriptAndWasm() =
        withEngine { engine ->
            engine.evaluate(DISABLE_DYNAMIC_CODE_PRELUDE + "''", "policy.js")
            assertEquals(
                "undefined,undefined,undefined,undefined,undefined",
                engine.evaluate(
                    "[typeof eval, typeof Function, typeof (() => {}).constructor, " +
                        "typeof (async () => {}).constructor, typeof WebAssembly].join(',')",
                    "policy-check.js",
                ),
            )
        }

    @Test
    fun contextsDoNotShareGlobals() =
        runBlocking<Unit> {
            val engines = List(2) { createTestPluginJavaScriptEngine { _, _, _ -> "{}" } }
            try {
                withTimeout(15_000) {
                    engines[0].evaluate("globalThis.privateValue = 'first'", "first.js")
                    assertEquals("undefined", engines[1].evaluate("typeof privateValue", "second.js"))
                    assertEquals(
                        listOf("first", "second"),
                        coroutineScope {
                            engines
                                .mapIndexed { index, engine ->
                                    async {
                                        engine.evaluate(
                                            "Promise.resolve('${if (index == 0) "first" else "second"}')",
                                            "concurrent.js",
                                            true,
                                        )
                                    }
                                }.awaitAll()
                        },
                    )
                }
            } finally {
                engines.forEach { it.close() }
            }
        }

    @Test
    fun closeRejectsPendingEvaluationAndNewCalls() =
        runBlocking<Unit> {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val engine =
                createTestPluginJavaScriptEngine { _, _, _ ->
                    started.complete(Unit)
                    try {
                        CompletableDeferred<Unit>().await()
                        "unreachable"
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            try {
                withTimeout(15_000) {
                    val result = async { runCatching { engine.evaluate("__flareHostCallV1('id','op','{}')", "pending.js", true) } }
                    started.await()
                    engine.close()
                    assertTrue(result.await().isFailure)
                    cancelled.await()
                    assertFails { engine.evaluate("'closed'", "closed.js") }
                }
            } finally {
                engine.close()
            }
        }

    @Test
    fun closeDetachesCallerWithoutWaitingForBusyJavaScript() =
        runBlocking<Unit> {
            val started = CompletableDeferred<Unit>()
            val engine =
                createTestPluginJavaScriptEngine { _, _, _ ->
                    started.complete(Unit)
                    "{}"
                }
            try {
                withTimeout(15_000) {
                    val result =
                        async {
                            runCatching {
                                engine.evaluate(
                                    "(() => { __flareHostCallV1('id','op','{}'); const until = Date.now() + 2000; " +
                                        "while (Date.now() < until) {} return 'late'; })()",
                                    "bounded-busy.js",
                                )
                            }
                        }
                    started.await()
                    withTimeout(1_000) {
                        engine.close()
                        assertTrue(result.await().isFailure)
                    }
                }
            } finally {
                engine.close()
            }
        }

    @Test
    fun socialRegistrationUsesExplicitEntryPoint() =
        withEngine { engine ->
            engine.evaluate(RUNTIME_PRELUDE + "''", "runtime.js")
            assertEquals(
                "function,undefined",
                engine.evaluate("[typeof defineFlareSocialPlugin, typeof definePlugin].join(',')", "globals.js"),
            )
            val missing = assertFails { engine.evaluate("__flareValidateRegistrationV1()", "missing.js") }
            assertTrue(missing.message.orEmpty().contains("plugin.js did not call defineFlareSocialPlugin"), missing.message)
            assertEquals(
                "true",
                engine.evaluate("defineFlareSocialPlugin({}); String(__flareValidateRegistrationV1())", "register.js"),
            )
            val duplicate = assertFails { engine.evaluate("defineFlareSocialPlugin({})", "duplicate.js") }
            assertTrue(duplicate.message.orEmpty().contains("defineFlareSocialPlugin may only be called once"), duplicate.message)
        }

    @Test
    fun registrationInspectorUsesPlatformEngine() =
        runBlocking<Unit> {
            val inspector = PluginRegistrationInspector(engineFactory = { host -> createTestPluginJavaScriptEngine(host) })
            val methods = inspector.inspect("defineFlareSocialPlugin({ detector: { detect() {} } });")
            assertEquals(setOf("detector.detect"), methods.methods)
            val duplicate = assertFails { inspector.inspect("defineFlareSocialPlugin({}); defineFlareSocialPlugin({});") }
            assertTrue(duplicate.message.orEmpty().contains("defineFlareSocialPlugin may only be called once"), duplicate.message)
            val missing = assertFails { inspector.inspect("({});") }
            assertTrue(missing.message.orEmpty().contains("plugin.js did not call defineFlareSocialPlugin"), missing.message)
            assertFails { inspector.inspect("definePlugin({});") }
            assertFails { inspector.inspect("eval('defineFlareSocialPlugin({})')") }
        }

    private fun withEngine(
        hostCall: PluginHostCall = { _, _, _ -> error("Unexpected Host call") },
        block: suspend (PluginJavaScriptEngine) -> Unit,
    ) = runBlocking<Unit> {
        val engine = createTestPluginJavaScriptEngine(hostCall)
        try {
            withTimeout(15_000) { block(engine) }
        } finally {
            engine.close()
        }
    }

    private suspend fun register(
        engine: PluginJavaScriptEngine,
        body: String,
    ) {
        engine.evaluate(
            RUNTIME_PRELUDE + DISABLE_DYNAMIC_CODE_PRELUDE + "defineFlareSocialPlugin({ test: { async run(request) { $body } } }); ''",
            "plugin.js",
        )
    }

    private suspend fun invoke(
        engine: PluginJavaScriptEngine,
        request: String = "{}",
    ) = PluginJsonV1
        .parseToJsonElement(
            engine.evaluate("__flareInvokeV1('invocation','test.run',$request,{})", "invoke.js", true),
        ).jsonObject
}
