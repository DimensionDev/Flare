@file:OptIn(ExperimentalJsExport::class, ExperimentalWasmJsInterop::class)

package dev.dimension.flare.web.presenter.export

import androidx.paging.LoadState
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSourceRef
import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.network.rss.DocumentData
import dev.dimension.flare.data.platform.BlueskySocialPlatformPlugin
import dev.dimension.flare.data.platform.MastodonSocialPlatformPlugin
import dev.dimension.flare.data.platform.MisskeySocialPlatformPlugin
import dev.dimension.flare.data.platform.VvoSocialPlatformPlugin
import dev.dimension.flare.data.platform.XqtSocialPlatformPlugin
import dev.dimension.flare.di.KoinHelper
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.SocialPlatformRegistry
import dev.dimension.flare.model.SocialPlatformSpec
import dev.dimension.flare.ui.model.UiDraft
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.HomeTabsPresenter
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.compose.ComposePresenter
import dev.dimension.flare.ui.presenter.compose.ComposeState
import dev.dimension.flare.ui.presenter.compose.DraftBoxPresenter
import dev.dimension.flare.ui.presenter.compose.DraftBoxState
import dev.dimension.flare.ui.presenter.home.SearchPresenter
import dev.dimension.flare.ui.presenter.home.SearchState
import dev.dimension.flare.ui.presenter.home.TimelineState
import dev.dimension.flare.ui.presenter.home.rss.RssDetailPresenter
import dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter
import dev.dimension.flare.ui.presenter.home.rss.createAllRssTimeline
import dev.dimension.flare.ui.presenter.home.rss.createRssTimeline
import dev.dimension.flare.ui.presenter.login.BlueskyLoginState
import dev.dimension.flare.ui.presenter.login.BlueskyOAuthLoginPresenter
import dev.dimension.flare.ui.presenter.login.MastodonLoginState
import dev.dimension.flare.ui.presenter.login.MisskeyLoginState
import dev.dimension.flare.ui.presenter.login.NostrLoginState
import dev.dimension.flare.ui.presenter.login.ServiceSelectPresenter
import dev.dimension.flare.ui.presenter.login.ServiceSelectState
import dev.dimension.flare.ui.presenter.login.VVOLoginPresenter
import dev.dimension.flare.ui.presenter.login.VVOLoginState
import dev.dimension.flare.ui.presenter.login.XQTLoginPresenter
import dev.dimension.flare.ui.presenter.login.XQTLoginState
import dev.dimension.flare.ui.presenter.profile.ProfilePresenter
import dev.dimension.flare.ui.presenter.profile.ProfileState
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.presenter.status.StatusState
import dev.dimension.flare.ui.render.UiDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString

private val bridgeJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
private val bridgeScope = CoroutineScope(SupervisorJob() + PlatformDispatchers.IO)
private var nextHandleId = 1
private var nextSubscriptionId = 1
private var initialized = false
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
    val id: String,
    val type: String,
    val argsJson: String,
    val args: JsonElement,
    val presenter: PresenterBase<Any>,
    val subscribers: MutableMap<String, (String) -> Unit> = mutableMapOf(),
    val events: MutableList<JsonObject> = mutableListOf(),
    var collectionJob: Job? = null,
    var currentState: Any? = null,
    var dispatchCount: Int = 0,
    var lastActionJson: String? = null,
    var lastAction: JsonElement? = null,
    var lastDispatchResult: JsonObject? = null,
)

@JsExport
@JsName("initFlare")
public fun initFlare(configJson: String): Promise<JsAny?> {
    initializedConfig = parseBridgeJson(configJson, "configJson")
    initializedConfigJson = configJson
    ensureFlareInitialized()
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
    ensureFlareInitialized()
    val args = parseBridgeJson(argsJson, "argsJson")
    val handleId = "presenter-${nextHandleId++}"
    val presenter =
        createPresenterInstance(
            handleId = handleId,
            type = type,
            args = args.objectOrEmpty(),
        ).asAnyPresenter()
    val handle =
        PresenterHandle(
            id = handleId,
            type = type,
            argsJson = argsJson,
            args = args,
            presenter = presenter,
        )
    presenterHandles[handleId] = handle
    handle.startCollecting()
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
    val actionObject = action.objectOrEmpty()
    handle.dispatchCount += 1
    handle.lastActionJson = actionJson
    handle.lastAction = action
    handle.lastDispatchResult = null
    val fallbackResult = handle.dispatch(actionObject)
    val result = handle.lastDispatchResult ?: fallbackResult
    handle.lastDispatchResult = result
    handle.notifySubscribers()
    return Promise.resolve(result.toString().toJsString())
}

@JsExport
@JsName("dispose")
public fun dispose(handleId: String) {
    presenterHandles.remove(handleId)?.close()
}

private fun ensureFlareInitialized() {
    if (initialized) {
        return
    }
    if (!isKoinStarted()) {
        startKoin {
            modules(KoinHelper.modules())
        }
    }
    initialized = true
}

private fun isKoinStarted(): Boolean =
    runCatching {
        GlobalContext.get()
    }.isSuccess

private fun requireHandle(handleId: String): PresenterHandle =
    requireNotNull(presenterHandles[handleId]) {
        "Unknown presenter handle: $handleId"
    }

private fun createPresenterInstance(
    handleId: String,
    type: String,
    args: JsonObject,
): PresenterBase<out Any> =
    when (type) {
        "service-select" -> {
            ServiceSelectPresenter(toHome = { handleId.navigateHome() })
        }

        "login" -> {
            createLoginPresenter(handleId, args)
        }

        "home-tabs" -> {
            HomeTabsPresenter()
        }

        "timeline" -> {
            createTimelinePresenter(args)
        }

        "search" -> {
            SearchPresenter(
                accountType = args.accountType(),
                initialQuery = args.optionalString("query").orEmpty(),
            )
        }

        "profile" -> {
            ProfilePresenter(
                accountType = args.accountType(),
                userKey = args["userKey"]?.toMicroBlogKeyOrNull(),
            )
        }

        "status-detail" -> {
            StatusPresenter(
                accountType = args.accountType(),
                statusKey = args.requiredMicroBlogKey("statusKey"),
            )
        }

        "compose" -> {
            ComposePresenter(
                accountType = args["accountType"]?.toAccountTypeOrNull(),
                draftGroupId = args.optionalString("draftGroupId"),
            )
        }

        "draft" -> {
            DraftBoxPresenter()
        }

        "rss-sources", "rss-settings" -> {
            RssSourcesPresenter()
        }

        "rss-detail" -> {
            RssDetailPresenter(
                url = args.requiredString("url"),
                descriptionHtml = args.optionalString("descriptionHtml"),
                descriptionTitle = args.optionalString("descriptionTitle"),
            )
        }

        else -> {
            error("Unsupported presenter type: $type")
        }
    }

private fun createLoginPresenter(
    handleId: String,
    args: JsonObject,
): PresenterBase<out Any> =
    when (args.optionalString("platform")?.lowercase()) {
        null, "", "service-select", "mastodon", "misskey", "bluesky" -> ServiceSelectPresenter(toHome = { handleId.navigateHome() })
        "xqt" -> XQTLoginPresenter(toHome = { handleId.navigateHome() })
        "vvo" -> VVOLoginPresenter(toHome = { handleId.navigateHome() })
        "nostr" -> throw IllegalArgumentException("Nostr login is not exported for Web.")
        else -> throw IllegalArgumentException("Unsupported login platform: ${args.optionalString("platform")}")
    }

private fun createTimelinePresenter(args: JsonObject): PresenterBase<out Any> {
    args["source"]?.let { sourceElement ->
        val source = bridgeJson.decodeFromJsonElement<TimelineSourceRef>(sourceElement)
        val resolver =
            GlobalContext
                .get()
                .get<TimelineResolver>()
        return resolver.createPresenter(resolver.toTabItem(source))
    }
    args.optionalString("rssUrl")?.let {
        return createRssTimeline(it)
    }
    args.optionalString("feedUrl")?.let {
        return createRssTimeline(it)
    }
    return when (args.optionalString("kind")?.lowercase()) {
        null, "", "all-rss" -> createAllRssTimeline()
        "nostr" -> throw IllegalArgumentException("Nostr timeline is not exported for Web.")
        else -> throw IllegalArgumentException("Unsupported timeline args: $args")
    }
}

@Suppress("UNCHECKED_CAST")
private fun PresenterBase<out Any>.asAnyPresenter(): PresenterBase<Any> = this as PresenterBase<Any>

private fun PresenterHandle.startCollecting() {
    collectionJob =
        bridgeScope.launch {
            presenter.models.collect { state ->
                currentState = state
                notifySubscribers()
            }
        }
}

private fun PresenterHandle.close() {
    collectionJob?.cancel()
    presenter.close()
    subscribers.clear()
}

private fun String.navigateHome() {
    presenterHandles[this]?.pushEvent(
        JsonObject(
            mapOf(
                "type" to JsonPrimitive("navigation"),
                "route" to JsonPrimitive("home"),
            ),
        ),
    )
}

private fun PresenterHandle.pushEvent(event: JsonObject) {
    events += event
    notifySubscribers()
}

private fun PresenterHandle.dispatch(action: JsonObject): JsonObject {
    val actionType = action.actionType()
    val state = currentState ?: return dispatchResult(actionType, accepted = false, reason = "State is not ready.")
    val accepted =
        when (state) {
            is ServiceSelectState -> dispatchServiceSelect(state, actionType, action)
            is BlueskyLoginState -> dispatchBlueskyLogin(state, actionType, action)
            is BlueskyOAuthLoginPresenter.State -> dispatchBlueskyOAuthLogin(state, actionType, action)
            is MastodonLoginState -> dispatchMastodonLogin(state, actionType, action)
            is MisskeyLoginState -> dispatchMisskeyLogin(state, actionType, action)
            is NostrLoginState -> false
            is XQTLoginState -> dispatchXqtLogin(state, actionType, action)
            is VVOLoginState -> dispatchVvoLogin(state, actionType, action)
            is RssSourcesPresenter.State -> dispatchRssSources(state, actionType, action)
            is TimelineState -> dispatchTimeline(state, actionType)
            is SearchState -> dispatchSearch(state, actionType, action)
            is ProfileState -> dispatchProfile(state, actionType, action)
            is ComposeState -> dispatchCompose(state, actionType, action)
            is DraftBoxState -> dispatchDraftBox(state, actionType, action)
            else -> false
        }
    return dispatchResult(actionType, accepted = accepted)
}

private fun PresenterHandle.dispatchResult(
    actionType: String,
    accepted: Boolean,
    reason: String? = null,
    data: JsonElement = JsonNull,
): JsonObject =
    JsonObject(
        buildMap {
            put("handleId", JsonPrimitive(id))
            put("type", JsonPrimitive(type))
            put("action", JsonPrimitive(actionType))
            put("accepted", JsonPrimitive(accepted))
            put("dispatchCount", JsonPrimitive(dispatchCount))
            put("data", data)
            reason?.let { put("reason", JsonPrimitive(it)) }
        },
    )

private fun PresenterHandle.dispatchServiceSelect(
    state: ServiceSelectState,
    actionType: String,
    action: JsonObject,
): Boolean =
    when (actionType) {
        "setFilter" -> {
            state.setFilter(action.requiredString("value"))
            true
        }

        "mastodon.login" -> {
            state.mastodonLoginState.login(action.requiredString("host")) { url ->
                pushLaunchUrl("mastodon", url)
            }
            true
        }

        "mastodon.resume" -> {
            state.mastodonLoginState.resume(action.requiredString("url"))
            true
        }

        "misskey.login" -> {
            state.misskeyLoginState.login(action.requiredString("host")) { url ->
                pushLaunchUrl("misskey", url)
            }
            true
        }

        "misskey.resume" -> {
            state.misskeyLoginState.resume(action.requiredString("url"))
            true
        }

        "bluesky.login" -> {
            dispatchBlueskyLogin(state.blueskyLoginState, "login", action)
        }

        "bluesky.clear" -> {
            dispatchBlueskyLogin(state.blueskyLoginState, "clear", action)
        }

        "bluesky-oauth.login" -> {
            dispatchBlueskyOAuthLogin(state.blueskyOauthLoginState, "login", action)
        }

        "bluesky-oauth.resume" -> {
            dispatchBlueskyOAuthLogin(state.blueskyOauthLoginState, "resume", action)
        }

        "bluesky-oauth.clear" -> {
            dispatchBlueskyOAuthLogin(state.blueskyOauthLoginState, "clear", action)
        }

        "nostr.login", "nostr.connectAmber", "nostr.startQrLogin", "nostr.cancelQrLogin" -> {
            false
        }

        else -> {
            false
        }
    }

private fun PresenterHandle.dispatchBlueskyLogin(
    state: BlueskyLoginState,
    actionType: String,
    action: JsonObject,
): Boolean =
    when (actionType) {
        "login" -> {
            state.login(
                baseUrl = action.optionalString("baseUrl") ?: "bsky.social",
                username = action.requiredString("username"),
                password = action.requiredString("password"),
                authFactorToken = action.optionalString("authFactorToken"),
            )
            true
        }

        "clear" -> {
            state.clear()
            true
        }

        else -> {
            false
        }
    }

private fun PresenterHandle.dispatchBlueskyOAuthLogin(
    state: BlueskyOAuthLoginPresenter.State,
    actionType: String,
    action: JsonObject,
): Boolean =
    when (actionType) {
        "login" -> {
            state.login(
                baseUrl = action.optionalString("baseUrl") ?: "bsky.social",
                userName = action.requiredString("username"),
            ) { url ->
                pushLaunchUrl("bluesky", url)
            }
            true
        }

        "resume" -> {
            state.resume(action.requiredString("url"))
            true
        }

        "clear" -> {
            state.clear()
            true
        }

        else -> {
            false
        }
    }

private fun PresenterHandle.dispatchMastodonLogin(
    state: MastodonLoginState,
    actionType: String,
    action: JsonObject,
): Boolean =
    when (actionType) {
        "login" -> {
            state.login(action.requiredString("host")) { url ->
                pushLaunchUrl("mastodon", url)
            }
            true
        }

        "resume" -> {
            state.resume(action.requiredString("url"))
            true
        }

        else -> {
            false
        }
    }

private fun PresenterHandle.dispatchMisskeyLogin(
    state: MisskeyLoginState,
    actionType: String,
    action: JsonObject,
): Boolean =
    when (actionType) {
        "login" -> {
            state.login(action.requiredString("host")) { url ->
                pushLaunchUrl("misskey", url)
            }
            true
        }

        "resume" -> {
            state.resume(action.requiredString("url"))
            true
        }

        else -> {
            false
        }
    }

private fun PresenterHandle.dispatchXqtLogin(
    state: XQTLoginState,
    actionType: String,
    action: JsonObject,
): Boolean =
    when (actionType) {
        "login" -> {
            state.login(action.requiredString("chocolate"))
            true
        }

        "checkChocolate" -> {
            val result = state.checkChocolate(action.requiredString("chocolate"))
            lastDispatchResult =
                dispatchResult(
                    actionType = actionType,
                    accepted = true,
                    data = JsonObject(mapOf("valid" to JsonPrimitive(result))),
                )
            true
        }

        else -> {
            false
        }
    }

private fun PresenterHandle.dispatchVvoLogin(
    state: VVOLoginState,
    actionType: String,
    action: JsonObject,
): Boolean =
    when (actionType) {
        "login" -> {
            state.login(action.requiredString("chocolate"))
            true
        }

        "checkChocolate" -> {
            val result = state.checkChocolate(action.requiredString("chocolate"))
            lastDispatchResult =
                dispatchResult(
                    actionType = actionType,
                    accepted = true,
                    data = JsonObject(mapOf("valid" to JsonPrimitive(result))),
                )
            true
        }

        else -> {
            false
        }
    }

private fun dispatchRssSources(
    state: RssSourcesPresenter.State,
    actionType: String,
    action: JsonObject,
): Boolean =
    when (actionType) {
        "add" -> {
            state.add(
                url = action.requiredString("url"),
                title = action.optionalString("title").orEmpty(),
                iconUrl = action.optionalString("iconUrl"),
            )
            true
        }

        "delete" -> {
            state.delete(action.requiredInt("id"))
            true
        }

        else -> {
            false
        }
    }

private fun dispatchTimeline(
    state: TimelineState,
    actionType: String,
): Boolean =
    when (actionType) {
        "refresh" -> {
            bridgeScope.launch { state.refresh() }
            true
        }

        else -> {
            false
        }
    }

private fun dispatchSearch(
    state: SearchState,
    actionType: String,
    action: JsonObject,
): Boolean =
    when (actionType) {
        "search" -> {
            state.search(action.requiredString("query"))
            true
        }

        "refresh" -> {
            bridgeScope.launch { state.refreshSuspend() }
            true
        }

        else -> {
            false
        }
    }

private fun dispatchProfile(
    state: ProfileState,
    actionType: String,
    action: JsonObject,
): Boolean {
    val userKey = action["userKey"]?.toMicroBlogKeyOrNull() ?: return false
    return when (actionType) {
        "follow" -> {
            state.follow(userKey)
            true
        }

        "unfollow" -> {
            state.unfollow(userKey)
            true
        }

        "unblock" -> {
            state.unblock(userKey)
            true
        }

        "report" -> {
            state.report(userKey)
            true
        }

        else -> {
            false
        }
    }
}

private fun dispatchCompose(
    state: ComposeState,
    actionType: String,
    action: JsonObject,
): Boolean =
    when (actionType) {
        "setText" -> {
            state.setText(action.requiredString("value"))
            true
        }

        "setMediaSize" -> {
            state.setMediaSize(action.requiredInt("value"))
            true
        }

        "selectAccount" -> {
            state.selectAccount(action.requiredMicroBlogKey("accountKey"))
            true
        }

        "loadDraft" -> {
            state.loadDraft(action.requiredString("groupId"))
            true
        }

        "consumeLoadedDraft" -> {
            state.consumeLoadedDraft()
            true
        }

        else -> {
            false
        }
    }

private fun dispatchDraftBox(
    state: DraftBoxState,
    actionType: String,
    action: JsonObject,
): Boolean =
    when (actionType) {
        "retry" -> {
            state.retry(action.requiredString("groupId"))
            true
        }

        "send" -> {
            state.send(action.requiredString("groupId"))
            true
        }

        "delete" -> {
            state.delete(action.requiredString("groupId"))
            true
        }

        else -> {
            false
        }
    }

private fun PresenterHandle.pushLaunchUrl(
    platform: String,
    url: String,
) {
    pushEvent(
        JsonObject(
            mapOf(
                "type" to JsonPrimitive("launchUrl"),
                "platform" to JsonPrimitive(platform),
                "url" to JsonPrimitive(url),
            ),
        ),
    )
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
            "handleId" to JsonPrimitive(id),
            "type" to JsonPrimitive(type),
            "argsJson" to JsonPrimitive(argsJson),
            "args" to args,
            "initializedConfigJson" to JsonPrimitive(initializedConfigJson),
            "config" to initializedConfig,
            "ready" to JsonPrimitive(currentState != null),
            "dispatchCount" to JsonPrimitive(dispatchCount),
            "lastActionJson" to (lastActionJson?.let(::JsonPrimitive) ?: JsonNull),
            "lastAction" to (lastAction ?: JsonNull),
            "lastDispatchResult" to (lastDispatchResult ?: JsonNull),
            "events" to JsonArray(events),
            "state" to statePayloadJson(),
            "supportedPresenterTypes" to
                JsonObject(
                    supportedPresenterTypeIds.associateWith { JsonPrimitive(true) },
                ),
            "registeredSocialPlatforms" to registeredPlatformMetadata(),
        ),
    ).toString()

private fun PresenterHandle.statePayloadJson(): JsonObject =
    when (val state = currentState) {
        null -> {
            JsonObject(mapOf("kind" to JsonPrimitive("initializing")))
        }

        is HomeTabsPresenter.State -> {
            homeTabsStateJson(state)
        }

        is ServiceSelectState -> {
            serviceSelectStateJson(state)
        }

        is BlueskyLoginState -> {
            blueskyLoginStateJson(state)
        }

        is BlueskyOAuthLoginPresenter.State -> {
            blueskyOAuthLoginStateJson(state)
        }

        is MastodonLoginState -> {
            mastodonLoginStateJson(state)
        }

        is MisskeyLoginState -> {
            misskeyLoginStateJson(state)
        }

        is NostrLoginState -> {
            nostrLoginStateJson(state)
        }

        is XQTLoginState -> {
            xqtLoginStateJson(state)
        }

        is VVOLoginState -> {
            vvoLoginStateJson(state)
        }

        is RssSourcesPresenter.State -> {
            rssSourcesStateJson(state)
        }

        is RssDetailPresenter.State -> {
            rssDetailStateJson(state)
        }

        is TimelineState -> {
            timelineStateJson(state)
        }

        is SearchState -> {
            searchStateJson(state)
        }

        is ProfileState -> {
            profileStateJson(state)
        }

        is StatusState -> {
            statusStateJson(state)
        }

        is ComposeState -> {
            composeStateJson(state)
        }

        is DraftBoxState -> {
            draftBoxStateJson(state)
        }

        else -> {
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("opaque"),
                    "description" to JsonPrimitive(state.toString()),
                ),
            )
        }
    }

private fun homeTabsStateJson(state: HomeTabsPresenter.State): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("home-tabs"),
            "tabs" to
                uiStateJson(state.tabs) { tabs ->
                    JsonArray(tabs.map { JsonPrimitive(it.name) })
                },
        ),
    )

private fun serviceSelectStateJson(state: ServiceSelectState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("service-select"),
            "loading" to JsonPrimitive(state.loading),
            "canNext" to JsonPrimitive(state.canNext),
            "instances" to pagingStateJson(state.instances, ::uiInstanceJson),
            "detectedPlatformType" to uiStateJson(state.detectedPlatformType, ::nodeDataJson),
            "nostr" to nostrLoginStateJson(state.nostrLoginState),
            "bluesky" to blueskyLoginStateJson(state.blueskyLoginState),
            "blueskyOAuth" to blueskyOAuthLoginStateJson(state.blueskyOauthLoginState),
            "mastodon" to mastodonLoginStateJson(state.mastodonLoginState),
            "misskey" to misskeyLoginStateJson(state.misskeyLoginState),
        ),
    )

private fun blueskyLoginStateJson(state: BlueskyLoginState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("bluesky-login"),
            "loading" to JsonPrimitive(state.loading),
            "require2FA" to JsonPrimitive(state.require2FA),
            "error" to throwableJson(state.error),
        ),
    )

private fun blueskyOAuthLoginStateJson(state: BlueskyOAuthLoginPresenter.State): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("bluesky-oauth-login"),
            "loading" to JsonPrimitive(state.loading),
            "error" to (state.error?.let(::JsonPrimitive) ?: JsonNull),
        ),
    )

private fun mastodonLoginStateJson(state: MastodonLoginState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("mastodon-login"),
            "loading" to JsonPrimitive(state.loading),
            "error" to (state.error?.let(::JsonPrimitive) ?: JsonNull),
            "resumedState" to unitUiStateJson(state.resumedState),
        ),
    )

private fun misskeyLoginStateJson(state: MisskeyLoginState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("misskey-login"),
            "loading" to JsonPrimitive(state.loading),
            "error" to (state.error?.let(::JsonPrimitive) ?: JsonNull),
            "resumedState" to unitUiStateJson(state.resumedState),
        ),
    )

private fun nostrLoginStateJson(state: NostrLoginState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("nostr-login"),
            "availableOnWeb" to JsonPrimitive(false),
            "loading" to JsonPrimitive(state.loading),
            "error" to throwableJson(state.error),
            "amberAvailable" to JsonPrimitive(false),
            "qrConnectUri" to JsonNull,
            "qrWaitingForApproval" to JsonPrimitive(false),
        ),
    )

private fun xqtLoginStateJson(state: XQTLoginState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("xqt-login"),
            "loading" to JsonPrimitive(state.loading),
            "error" to throwableJson(state.error),
        ),
    )

private fun vvoLoginStateJson(state: VVOLoginState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("vvo-login"),
            "loading" to JsonPrimitive(state.loading),
            "error" to throwableJson(state.error),
        ),
    )

private fun rssSourcesStateJson(state: RssSourcesPresenter.State): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("rss-sources"),
            "sources" to JsonArray(state.sources.map(::rssSourceJson)),
        ),
    )

private fun rssDetailStateJson(state: RssDetailPresenter.State): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("rss-detail"),
            "data" to uiStateJson(state.data, ::documentDataJson),
        ),
    )

private fun timelineStateJson(state: TimelineState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("timeline"),
            "listState" to pagingStateJson(state.listState, ::timelineItemJson),
        ),
    )

private fun searchStateJson(state: SearchState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("search"),
            "searching" to JsonPrimitive(state.searching),
            "accounts" to
                uiStateJson(state.accounts) { accounts ->
                    JsonArray(accounts.map(::profileJson))
                },
            "selectedAccount" to (state.selectedAccount?.let(::profileJson) ?: JsonNull),
            "users" to pagingStateJson(state.users, ::profileJson),
            "status" to pagingStateJson(state.status, ::timelineItemJson),
        ),
    )

private fun profileStateJson(state: ProfileState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("profile"),
            "user" to uiStateJson(state.userState, ::profileJson),
            "isMe" to uiStateJson(state.isMe) { JsonPrimitive(it) },
            "myAccountKey" to uiStateJson(state.myAccountKey, ::microBlogKeyJson),
            "actionsCount" to JsonPrimitive(state.actions.size),
            "tabs" to
                uiStateJson(state.tabs) { tabs ->
                    JsonArray(
                        tabs.map {
                            JsonPrimitive(
                                when (it) {
                                    is ProfileState.Tab.Timeline -> "timeline:${it.type.name}"
                                    is ProfileState.Tab.Media -> "media"
                                },
                            )
                        },
                    )
                },
        ),
    )

private fun statusStateJson(state: StatusState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("status-detail"),
            "status" to uiStateJson(state.status, ::timelineItemJson),
        ),
    )

private fun composeStateJson(state: ComposeState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("compose"),
            "canSend" to JsonPrimitive(state.canSend),
            "showDraft" to JsonPrimitive(state.showDraft),
            "editingDraftGroupId" to (state.editingDraftGroupId?.let(::JsonPrimitive) ?: JsonNull),
            "loadedDraft" to (state.loadedDraftState?.let { uiStateJson(it, ::draftJson) } ?: JsonNull),
            "reply" to (state.replyState?.let { uiStateJson(it, ::timelineItemJson) } ?: JsonNull),
            "enableCrossPost" to uiStateJson(state.enableCrossPost) { JsonPrimitive(it) },
            "selectedUsers" to profileStateListJson(state.selectedUsers),
            "otherUsers" to profileStateListJson(state.otherUsers),
            "visibility" to
                uiStateJson(state.visibilityState) {
                    JsonObject(
                        mapOf(
                            "visibility" to JsonPrimitive(it.visibility.name),
                            "showVisibilityMenu" to JsonPrimitive(it.showVisibilityMenu),
                        ),
                    )
                },
        ),
    )

private fun draftBoxStateJson(state: DraftBoxState): JsonObject =
    JsonObject(
        mapOf(
            "kind" to JsonPrimitive("draft"),
            "items" to JsonArray(state.items.map(::draftJson)),
        ),
    )

private fun profileStateListJson(state: UiState<kotlinx.collections.immutable.ImmutableList<UiState<UiProfile>>>): JsonObject =
    uiStateJson(state) { list ->
        JsonArray(list.map { uiStateJson(it, ::profileJson) })
    }

private fun <T : Any> uiStateJson(
    state: UiState<T>,
    valueJson: (T) -> JsonElement,
): JsonObject =
    when (state) {
        is UiState.Loading -> {
            JsonObject(mapOf("status" to JsonPrimitive("loading")))
        }

        is UiState.Error -> {
            JsonObject(
                mapOf(
                    "status" to JsonPrimitive("error"),
                    "error" to throwableJson(state.throwable),
                ),
            )
        }

        is UiState.Success -> {
            JsonObject(
                mapOf(
                    "status" to JsonPrimitive("success"),
                    "data" to valueJson(state.data),
                ),
            )
        }
    }

private fun unitUiStateJson(state: UiState<Nothing>?): JsonObject =
    when (state) {
        null -> {
            JsonObject(mapOf("status" to JsonPrimitive("idle")))
        }

        is UiState.Loading -> {
            JsonObject(mapOf("status" to JsonPrimitive("loading")))
        }

        is UiState.Error -> {
            JsonObject(
                mapOf(
                    "status" to JsonPrimitive("error"),
                    "error" to throwableJson(state.throwable),
                ),
            )
        }

        is UiState.Success -> {
            JsonObject(mapOf("status" to JsonPrimitive("success")))
        }
    }

private fun <T : Any> pagingStateJson(
    state: PagingState<T>,
    itemJson: (T) -> JsonElement,
): JsonObject =
    when (state) {
        is PagingState.Loading -> {
            JsonObject(mapOf("status" to JsonPrimitive("loading")))
        }

        is PagingState.Error -> {
            JsonObject(
                mapOf(
                    "status" to JsonPrimitive("error"),
                    "error" to throwableJson(state.error),
                ),
            )
        }

        is PagingState.Empty -> {
            JsonObject(mapOf("status" to JsonPrimitive("empty")))
        }

        is PagingState.Success -> {
            JsonObject(
                mapOf(
                    "status" to JsonPrimitive("success"),
                    "itemCount" to JsonPrimitive(state.itemCount),
                    "isRefreshing" to JsonPrimitive(state.isRefreshing),
                    "appendState" to loadStateJson(state.appendState),
                    "items" to
                        JsonArray(
                            (0 until minOf(state.itemCount, 50)).mapNotNull { index ->
                                state.peek(index)?.let(itemJson)
                            },
                        ),
                ),
            )
        }
    }

private fun loadStateJson(state: LoadState): JsonObject =
    when (state) {
        is LoadState.Loading -> {
            JsonObject(mapOf("status" to JsonPrimitive("loading")))
        }

        is LoadState.NotLoading -> {
            JsonObject(
                mapOf(
                    "status" to JsonPrimitive("not-loading"),
                    "endOfPaginationReached" to JsonPrimitive(state.endOfPaginationReached),
                ),
            )
        }

        is LoadState.Error -> {
            JsonObject(
                mapOf(
                    "status" to JsonPrimitive("error"),
                    "error" to throwableJson(state.error),
                ),
            )
        }
    }

private fun nodeDataJson(data: NodeData): JsonObject =
    JsonObject(
        mapOf(
            "host" to JsonPrimitive(data.host),
            "platformType" to JsonPrimitive(data.platformType.name),
            "software" to JsonPrimitive(data.software),
            "compatibleMode" to JsonPrimitive(data.compatibleMode),
        ),
    )

private fun uiInstanceJson(instance: UiInstance): JsonObject =
    JsonObject(
        mapOf(
            "name" to JsonPrimitive(instance.name),
            "description" to (instance.description?.let(::JsonPrimitive) ?: JsonNull),
            "iconUrl" to (instance.iconUrl?.let(::JsonPrimitive) ?: JsonNull),
            "domain" to JsonPrimitive(instance.domain),
            "type" to JsonPrimitive(instance.type.name),
            "bannerUrl" to (instance.bannerUrl?.let(::JsonPrimitive) ?: JsonNull),
            "usersCount" to JsonPrimitive(instance.usersCount),
        ),
    )

private fun profileJson(profile: UiProfile): JsonObject =
    JsonObject(
        mapOf(
            "key" to microBlogKeyJson(profile.key),
            "handle" to JsonPrimitive(profile.handle.raw),
            "displayName" to JsonPrimitive(profile.name.raw),
            "avatar" to JsonPrimitive(profile.avatar),
            "platformType" to JsonPrimitive(profile.platformType.name),
            "banner" to (profile.banner?.let(::JsonPrimitive) ?: JsonNull),
            "description" to (profile.description?.raw?.let(::JsonPrimitive) ?: JsonNull),
            "fansCount" to JsonPrimitive(profile.matrices.fansCount),
            "followsCount" to JsonPrimitive(profile.matrices.followsCount),
            "statusesCount" to JsonPrimitive(profile.matrices.statusesCount),
        ),
    )

private fun timelineItemJson(item: UiTimelineV2): JsonObject {
    val common =
        mapOf(
            "type" to JsonPrimitive(item.itemType),
            "id" to (item.id?.let(::JsonPrimitive) ?: JsonNull),
            "statusKey" to microBlogKeyJson(item.statusKey),
            "createdAt" to dateTimeJson(item.createdAt),
            "accountType" to accountTypeJson(item.accountType),
            "renderHash" to JsonPrimitive(item.renderHash),
        )
    val detail =
        when (item) {
            is UiTimelineV2.Feed -> {
                mapOf(
                    "title" to (item.title?.let(::JsonPrimitive) ?: JsonNull),
                    "description" to (item.description?.let(::JsonPrimitive) ?: JsonNull),
                    "url" to JsonPrimitive(item.url),
                    "sourceName" to JsonPrimitive(item.source.name),
                    "sourceIcon" to (item.source.icon?.let(::JsonPrimitive) ?: JsonNull),
                )
            }

            is UiTimelineV2.Post -> {
                mapOf(
                    "platformType" to JsonPrimitive(item.platformType.name),
                    "content" to JsonPrimitive(item.content.raw),
                    "contentWarning" to (item.contentWarning?.raw?.let(::JsonPrimitive) ?: JsonNull),
                    "user" to (item.user?.let(::profileJson) ?: JsonNull),
                    "sensitive" to JsonPrimitive(item.sensitive),
                    "mediaCount" to JsonPrimitive(item.images.size),
                    "quoteCount" to JsonPrimitive(item.quote.size),
                )
            }

            is UiTimelineV2.User -> {
                mapOf(
                    "user" to profileJson(item.value),
                )
            }

            is UiTimelineV2.UserList -> {
                mapOf(
                    "users" to JsonArray(item.users.map(::profileJson)),
                )
            }

            is UiTimelineV2.Message -> {
                mapOf(
                    "messageType" to JsonPrimitive(item.type.toString()),
                    "user" to (item.user?.let(::profileJson) ?: JsonNull),
                )
            }
        }
    return JsonObject(common + detail)
}

private fun rssSourceJson(source: UiRssSource): JsonObject =
    JsonObject(
        mapOf(
            "id" to JsonPrimitive(source.id),
            "url" to JsonPrimitive(source.url),
            "title" to (source.title?.let(::JsonPrimitive) ?: JsonNull),
            "lastUpdate" to dateTimeJson(source.lastUpdate),
            "favIcon" to (source.favIcon?.let(::JsonPrimitive) ?: JsonNull),
            "displayMode" to JsonPrimitive(source.displayMode.name),
            "type" to JsonPrimitive(source.type.name),
            "host" to JsonPrimitive(source.host),
        ),
    )

private fun documentDataJson(data: DocumentData): JsonObject =
    JsonObject(
        mapOf(
            "title" to JsonPrimitive(data.title),
            "content" to JsonPrimitive(data.content),
            "textContent" to JsonPrimitive(data.textContent),
            "length" to (data.length?.let(::JsonPrimitive) ?: JsonNull),
            "excerpt" to (data.excerpt?.let(::JsonPrimitive) ?: JsonNull),
            "byline" to (data.byline?.let(::JsonPrimitive) ?: JsonNull),
            "dir" to (data.dir?.let(::JsonPrimitive) ?: JsonNull),
            "siteName" to (data.siteName?.let(::JsonPrimitive) ?: JsonNull),
            "lang" to (data.lang?.let(::JsonPrimitive) ?: JsonNull),
            "publishedTime" to (data.publishedTime?.let(::JsonPrimitive) ?: JsonNull),
        ),
    )

private fun draftJson(draft: UiDraft): JsonObject =
    JsonObject(
        mapOf(
            "groupId" to JsonPrimitive(draft.groupId),
            "status" to JsonPrimitive(draft.status.name),
            "updatedAt" to dateTimeJson(draft.updatedAt),
            "accounts" to
                JsonArray(
                    draft.accounts.map {
                        JsonObject(
                            mapOf(
                                "accountKey" to microBlogKeyJson(it.account.accountKey),
                                "avatar" to (it.avatar?.let(::JsonPrimitive) ?: JsonNull),
                                "platformType" to JsonPrimitive(it.account.platformType.name),
                            ),
                        )
                    },
                ),
            "mediaCount" to JsonPrimitive(draft.medias.size),
        ),
    )

private fun microBlogKeyJson(key: MicroBlogKey): JsonObject =
    JsonObject(
        mapOf(
            "id" to JsonPrimitive(key.id),
            "host" to JsonPrimitive(key.host),
            "value" to JsonPrimitive(key.toString()),
        ),
    )

private fun accountTypeJson(accountType: AccountType): JsonObject =
    when (accountType) {
        AccountType.Guest -> {
            JsonObject(mapOf("type" to JsonPrimitive("guest")))
        }

        is AccountType.GuestHost -> {
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("guest-host"),
                    "host" to JsonPrimitive(accountType.host),
                ),
            )
        }

        is AccountType.Specific -> {
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("specific"),
                    "accountKey" to microBlogKeyJson(accountType.accountKey),
                ),
            )
        }
    }

private fun dateTimeJson(value: UiDateTime): JsonObject =
    JsonObject(
        mapOf(
            "epochMillis" to JsonPrimitive(value.value.toEpochMilliseconds()),
            "relative" to JsonPrimitive(value.relative),
            "full" to JsonPrimitive(value.full),
            "absolute" to JsonPrimitive(value.absolute),
        ),
    )

private fun throwableJson(throwable: Throwable?): JsonElement =
    throwable?.let {
        JsonObject(
            mapOf(
                "message" to (it.message?.let(::JsonPrimitive) ?: JsonNull),
                "description" to JsonPrimitive(it.toString()),
            ),
        )
    } ?: JsonNull

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

private fun JsonElement.objectOrEmpty(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

private fun JsonObject.actionType(): String = optionalString("type") ?: optionalString("action") ?: "unknown"

private fun JsonObject.optionalString(name: String): String? =
    (this[name] as? JsonPrimitive)
        ?.takeUnless { it is JsonNull }
        ?.content

private fun JsonObject.requiredString(name: String): String =
    requireNotNull(optionalString(name)) {
        "$name is required."
    }

private fun JsonObject.requiredInt(name: String): Int =
    requireNotNull((this[name] as? JsonPrimitive)?.content?.toIntOrNull()) {
        "$name must be an integer."
    }

private fun JsonObject.requiredMicroBlogKey(name: String): MicroBlogKey =
    requireNotNull(this[name]?.toMicroBlogKeyOrNull()) {
        "$name must be a MicroBlogKey."
    }

private fun JsonObject.accountType(): AccountType = this["accountType"]?.toAccountTypeOrNull() ?: AccountType.Guest

private fun JsonElement.toAccountTypeOrNull(): AccountType? =
    when (this) {
        is JsonObject -> {
            when (optionalString("type")?.lowercase()) {
                "guest" -> AccountType.Guest
                "guest-host", "guesthost" -> optionalString("host")?.let(AccountType::GuestHost)
                "specific" -> this["accountKey"]?.toMicroBlogKeyOrNull()?.let(AccountType::Specific)
                else -> this["accountKey"]?.toMicroBlogKeyOrNull()?.let(AccountType::Specific)
            }
        }

        is JsonPrimitive -> {
            when (content.lowercase()) {
                "guest", "" -> AccountType.Guest
                else -> MicroBlogKey.valueOf(content).let(AccountType::Specific)
            }
        }

        else -> {
            null
        }
    }

private fun JsonElement.toMicroBlogKeyOrNull(): MicroBlogKey? =
    when (this) {
        is JsonObject -> {
            val id = optionalString("id") ?: return null
            val host = optionalString("host").orEmpty()
            MicroBlogKey(id = id, host = host)
        }

        is JsonPrimitive -> {
            runCatching { MicroBlogKey.valueOf(content) }.getOrNull()
        }

        else -> {
            null
        }
    }
