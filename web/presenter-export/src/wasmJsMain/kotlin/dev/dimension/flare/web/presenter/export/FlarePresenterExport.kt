@file:OptIn(ExperimentalJsExport::class, ExperimentalWasmJsInterop::class)

package dev.dimension.flare.web.presenter.export

import dev.dimension.flare.data.platform.BlueskySocialPlatformPlugin
import dev.dimension.flare.data.platform.MastodonSocialPlatformPlugin
import dev.dimension.flare.data.platform.MisskeySocialPlatformPlugin
import dev.dimension.flare.data.platform.VvoSocialPlatformPlugin
import dev.dimension.flare.data.platform.XqtSocialPlatformPlugin
import dev.dimension.flare.model.SocialPlatformRegistry
import dev.dimension.flare.model.SocialPlatformSpec
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val bridgeJson = Json
private var nextHandleId = 1
private var nextSubscriptionId = 1
private var initializedConfigJson: String = "{}"
private var initializedConfig: JsonElement = JsonObject(emptyMap())
private val webSocialPlatformRegistry =
    SocialPlatformRegistry(
        listOf(
            MastodonSocialPlatformPlugin,
            MisskeySocialPlatformPlugin,
            BlueskySocialPlatformPlugin,
            XqtSocialPlatformPlugin,
            VvoSocialPlatformPlugin,
        ),
    )

private val presenterHandles = mutableMapOf<String, PresenterHandle>()
private val supportedPresenterTypeIds =
    setOf(
        "service-select",
        "login",
        "home-tabs",
        "timeline",
        "search",
        "profile",
        "status-detail",
        "compose",
        "draft",
        "rss-sources",
        "rss-detail",
        "rss-settings",
    )

private data class PresenterHandle(
    val type: String,
    val argsJson: String,
    val args: JsonElement,
    val subscribers: MutableMap<String, (String) -> Unit> = mutableMapOf(),
    var dispatchCount: Int = 0,
    var lastActionJson: String? = null,
    var lastAction: JsonElement? = null,
)

@JsExport
@JsName("initFlare")
public fun initFlare(configJson: String): Promise<JsAny?> {
    initializedConfig = parseBridgeJson(configJson, "configJson")
    initializedConfigJson = configJson
    return Promise.resolve(null)
}

@JsExport
@JsName("createPresenter")
public fun createPresenter(
    type: String,
    argsJson: String,
): String {
    require(type in supportedPresenterTypeIds) {
        "Unsupported presenter type: $type"
    }
    val args = parseBridgeJson(argsJson, "argsJson")
    val handleId = "presenter-${nextHandleId++}"
    presenterHandles[handleId] =
        PresenterHandle(
            type = type,
            argsJson = argsJson,
            args = args,
        )
    return handleId
}

@JsExport
@JsName("supportedPresenterTypes")
public fun supportedPresenterTypes(): String =
    JsonObject(
        supportedPresenterTypeIds.associateWith { JsonPrimitive(true) },
    ).toString()

@JsExport
@JsName("registeredSocialPlatforms")
public fun registeredSocialPlatforms(): String = registeredPlatformMetadata().toString()

@JsExport
@JsName("subscribe")
public fun subscribe(
    handleId: String,
    callback: (String) -> Unit,
): String {
    val handle = requireHandle(handleId)
    val subscriptionId = "subscription-${nextSubscriptionId++}"
    handle.subscribers[subscriptionId] = callback
    callback(handle.stateJson())
    return subscriptionId
}

@JsExport
@JsName("dispatch")
public fun dispatch(
    handleId: String,
    actionJson: String,
): Promise<JsString> {
    val handle = requireHandle(handleId)
    val action = parseBridgeJson(actionJson, "actionJson")
    handle.dispatchCount += 1
    handle.lastActionJson = actionJson
    handle.lastAction = action
    val resultJson =
        JsonObject(
            mapOf(
                "handleId" to JsonPrimitive(handleId),
                "type" to JsonPrimitive(handle.type),
                "actionJson" to JsonPrimitive(actionJson),
                "action" to action,
                "accepted" to JsonPrimitive(true),
                "dispatchCount" to JsonPrimitive(handle.dispatchCount),
            ),
        ).toString()
    handle.notifySubscribers()
    return Promise.resolve(resultJson.toJsString())
}

@JsExport
@JsName("dispose")
public fun dispose(handleId: String) {
    presenterHandles.remove(handleId)?.subscribers?.clear()
}

private fun requireHandle(handleId: String): PresenterHandle =
    requireNotNull(presenterHandles[handleId]) {
        "Unknown presenter handle: $handleId"
    }

private fun parseBridgeJson(
    json: String,
    fieldName: String,
): JsonElement =
    try {
        bridgeJson.parseToJsonElement(json)
    } catch (cause: SerializationException) {
        throw IllegalArgumentException("$fieldName must be valid JSON.", cause)
    }

private fun PresenterHandle.notifySubscribers() {
    val stateJson = stateJson()
    subscribers.values.forEach { callback ->
        callback(stateJson)
    }
}

private fun PresenterHandle.stateJson(): String =
    JsonObject(
        mapOf(
            "type" to JsonPrimitive(type),
            "argsJson" to JsonPrimitive(argsJson),
            "args" to args,
            "initializedConfigJson" to JsonPrimitive(initializedConfigJson),
            "config" to initializedConfig,
            "ready" to JsonPrimitive(true),
            "dispatchCount" to JsonPrimitive(dispatchCount),
            "lastActionJson" to (lastActionJson?.let(::JsonPrimitive) ?: JsonNull),
            "lastAction" to (lastAction ?: JsonNull),
            "supportedPresenterTypes" to
                JsonObject(
                    supportedPresenterTypeIds.associateWith { JsonPrimitive(true) },
                ),
            "registeredSocialPlatforms" to registeredPlatformMetadata(),
        ),
    ).toString()

private fun registeredPlatformMetadata(): JsonArray =
    JsonArray(
        webSocialPlatformRegistry.specs.map { spec ->
            spec.toMetadataJson()
        },
    )

private fun SocialPlatformSpec.toMetadataJson(): JsonObject =
    JsonObject(
        mapOf(
            "type" to JsonPrimitive(type.name),
            "displayName" to JsonPrimitive(metadata.displayName),
            "icon" to JsonPrimitive(metadata.icon.name),
        ),
    )
