@file:OptIn(ExperimentalJsExport::class, ExperimentalWasmJsInterop::class)

package dev.dimension.flare.web.shared

import dev.dimension.flare.web.shared.generated.GeneratedWebPresenterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal interface WebPresenterSpec {
    val name: String

    fun create(
        argsJson: String,
        callbacks: JsArray<JsAny>,
    ): WebPresenterBinding

    fun bind(presenter: Any): WebPresenterBinding
}

internal interface WebPresenterBinding {
    val models: StateFlow<Any>

    fun encode(model: Any): WebPresenterSnapshot

    fun dispatch(
        model: Any,
        actionJson: String,
        refs: JsArray<JsReference<Any>>,
    )

    fun call(
        model: Any,
        actionJson: String,
        refs: JsArray<JsReference<Any>>,
    ): WebPresenterSnapshot

    fun close()
}

internal data class WebPresenterSnapshot(
    val json: String,
    val refs: JsArray<JsReference<Any>>,
)

internal fun emptyWebPresenterRefs(): JsArray<JsReference<Any>> = emptyArray<JsReference<Any>>().toJsArray()

internal val WebPresenterJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

internal fun webPresenterActionPath(actionJson: String): String =
    WebPresenterJson
        .parseToJsonElement(actionJson)
        .jsonObject
        .getValue("path")
        .jsonPrimitive
        .content

private val presenterHandles = mutableMapOf<Int, WebPresenterHandle>()
private var nextPresenterHandle = 0

@JsExport
@JsName("webPresenterCreate")
public fun webPresenterCreate(
    name: String,
    argsJson: String,
    callbacks: JsArray<JsAny>,
): Int {
    WebSharedHelper.initialize()
    val spec =
        requireNotNull(GeneratedWebPresenterRegistry.specs[name]) {
            "Web presenter $name is not registered."
        }
    return registerPresenterHandle(spec.create(argsJson, callbacks))
}

@JsExport
@JsName("webPresenterBindRef")
public fun webPresenterBindRef(
    name: String,
    presenter: JsReference<Any>,
): Int {
    WebSharedHelper.initialize()
    val spec =
        requireNotNull(GeneratedWebPresenterRegistry.specs[name]) {
            "Web presenter $name is not registered."
        }
    return registerPresenterHandle(spec.bind(requireNotNull(presenter.get())))
}

@JsExport
@JsName("webPresenterSubscribe")
public fun webPresenterSubscribe(
    id: Int,
    listener: (
        String,
        JsArray<JsReference<Any>>,
    ) -> Unit,
): Int = presenterHandle(id).subscribe(listener)

@JsExport
@JsName("webPresenterDispatch")
public fun webPresenterDispatch(
    id: Int,
    actionJson: String,
    refs: JsArray<JsReference<Any>>,
) {
    presenterHandle(id).dispatch(actionJson, refs)
}

@JsExport
@JsName("webPresenterCall")
public fun webPresenterCall(
    id: Int,
    actionJson: String,
    refs: JsArray<JsReference<Any>>,
    listener: (
        String,
        JsArray<JsReference<Any>>,
    ) -> Unit,
) {
    val snapshot = presenterHandle(id).call(actionJson, refs)
    listener(snapshot.json, snapshot.refs)
}

@JsExport
@JsName("webPresenterUnsubscribe")
public fun webPresenterUnsubscribe(
    id: Int,
    subscriptionId: Int,
) {
    presenterHandle(id).unsubscribe(subscriptionId)
}

@JsExport
@JsName("webPresenterClose")
public fun webPresenterClose(id: Int) {
    presenterHandles.remove(id)?.close()
}

private fun presenterHandle(id: Int): WebPresenterHandle =
    requireNotNull(presenterHandles[id]) {
        "Web presenter $id is not active."
    }

private fun registerPresenterHandle(binding: WebPresenterBinding): Int {
    val id = nextPresenterHandle++
    presenterHandles[id] = WebPresenterHandle(binding)
    return id
}

private class WebPresenterHandle(
    private val binding: WebPresenterBinding,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val subscriptions = mutableMapOf<Int, Job>()
    private var nextSubscriptionId = 0
    private var closed = false

    fun subscribe(
        listener: (
            String,
            JsArray<JsReference<Any>>,
        ) -> Unit,
    ): Int {
        check(!closed) { "Web presenter is closed." }
        val id = nextSubscriptionId++
        var lastSnapshot = binding.encode(binding.models.value)
        listener(lastSnapshot.json, lastSnapshot.refs)
        subscriptions[id] =
            scope.launch {
                binding.models.collect { state ->
                    val snapshot = binding.encode(state)
                    if (snapshot.json != lastSnapshot.json) {
                        lastSnapshot = snapshot
                        listener(snapshot.json, snapshot.refs)
                    }
                }
            }
        return id
    }

    fun dispatch(
        actionJson: String,
        refs: JsArray<JsReference<Any>>,
    ) {
        check(!closed) { "Web presenter is closed." }
        binding.dispatch(binding.models.value, actionJson, refs)
    }

    fun call(
        actionJson: String,
        refs: JsArray<JsReference<Any>>,
    ): WebPresenterSnapshot {
        check(!closed) { "Web presenter is closed." }
        return binding.call(binding.models.value, actionJson, refs)
    }

    fun unsubscribe(id: Int) {
        subscriptions.remove(id)?.cancel()
    }

    fun close() {
        if (closed) return
        closed = true
        subscriptions.values.forEach { it.cancel() }
        subscriptions.clear()
        binding.close()
        scope.cancel()
    }
}
