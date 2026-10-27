package dev.dimension.flare.data.network

import dev.dimension.flare.data.model.ProxyConnection
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

@OptIn(InternalAPI::class)
internal class ProxyRoutingEngine(
    private val proxy: ProxyRef,
) : HttpClientEngineBase("flare-proxy") {
    override val config = HttpClientEngineConfig()
    override val supportedCapabilities get() = platformEngineCapabilities

    override fun install(client: io.ktor.client.HttpClient) {
        client.sendPipeline.intercept(io.ktor.client.request.HttpSendPipeline.Before) {
            // Redirects copy the builder; engine request data is already a separate copy.
            if (!context.attributes.contains(requestOrigin)) context.attributes.put(requestOrigin, context.url.build())
        }
        super.install(client)
    }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val origin = data.attributes.getOrNull(requestOrigin) ?: data.url.also { data.attributes.put(requestOrigin, it) }
        val sameOrigin = origin.protocol == data.url.protocol && origin.host == data.url.host && origin.port == data.url.port
        val request =
            if (sameOrigin) {
                data
            } else {
                HttpRequestData(
                    data.url,
                    data.method,
                    io.ktor.http.Headers.build {
                        data.headers.forEach { key, values ->
                            if (!key.equals("Authorization", true) && !key.equals("Cookie", true)) appendAll(key, values)
                        }
                    },
                    data.body,
                    data.executionContext,
                    data.attributes,
                )
            }
        return ProxyNetwork.execute(data.attributes.getOrNull(requestProxy) ?: proxy, request)
    }
}

public data class ProxySnapshot internal constructor(
    val connection: ProxyConnection,
    val key: String,
) {
    private val invalidation = Job()
    public val isValid: Boolean get() = invalidation.isActive

    // Completion also notifies late subscribers, closing the snapshot-to-request race.
    public fun onInvalidated(cancel: () -> Unit): () -> Unit {
        val handle = invalidation.invokeOnCompletion { cancel() }
        return { handle.dispose() }
    }

    internal fun invalidate() {
        invalidation.complete()
    }
}

@OptIn(InternalAPI::class)
public object ProxyNetwork {
    private val mutex = Mutex()
    private val routes = mutableMapOf<ProxyRef, Route>()
    private val revision = MutableStateFlow(0L)
    public val changes: StateFlow<Long> get() = revision

    private class Route(
        val snapshot: ProxySnapshot,
    ) {
        private val engineDelegate = lazy { createPlatformHttpClientEngine(snapshot.connection) }
        val engine by engineDelegate
        val calls = mutableSetOf<Job>()

        fun cancel() {
            snapshot.invalidate()
            calls.forEach { it.cancel(CancellationException("Proxy configuration changed")) }
            calls.clear()
            if (engineDelegate.isInitialized()) {
                engine.coroutineContext.cancel()
                engine.close()
            }
        }
    }

    private suspend fun route(
        ref: ProxyRef,
        call: Job? = null,
    ): Route =
        koinGet<ProxyRepository>().withSettings { settings ->
            mutex.withLock {
                val connection = settings.resolve(ref)
                val route =
                    routes[ref]?.takeIf { it.snapshot.connection == connection } ?: run {
                        routes.remove(ref)?.cancel()
                        Route(ProxySnapshot(connection, Uuid.random().toString())).also { routes[ref] = it }
                    }
                if (call != null) {
                    call.ensureActive()
                    // Initialize under the same lock as cancellation, so a retired route cannot start an engine.
                    route.engine
                    route.calls.removeAll { it.isCompleted }
                    route.calls.add(call)
                }
                route
            }
        }

    @Throws(Exception::class)
    public suspend fun snapshot(ref: ProxyRef): ProxySnapshot = route(ref).snapshot

    internal suspend fun execute(
        ref: ProxyRef,
        data: HttpRequestData,
    ): HttpResponseData {
        val selected = route(ref, callContext().job)
        currentCoroutineContext().ensureActive()
        return selected.engine.execute(data)
    }

    internal suspend fun settingsChanged(settings: ProxySettings) {
        mutex.withLock {
            val iterator = routes.iterator()
            var changed = false
            while (iterator.hasNext()) {
                val (ref, route) = iterator.next()
                if (runCatching { settings.resolve(ref) }.getOrNull() != route.snapshot.connection) {
                    route.cancel()
                    iterator.remove()
                    changed = true
                }
            }
            if (changed) revision.value++
        }
    }
}

internal expect fun createPlatformHttpClientEngine(connection: ProxyConnection): HttpClientEngine

internal expect val platformEngineCapabilities: Set<HttpClientEngineCapability<*>>

@kotlin.native.HiddenFromObjC
public fun mediaHttpClient(proxy: ProxyRef): io.ktor.client.HttpClient =
    io.ktor.client.HttpClient(ProxyRoutingEngine(proxy)) {
        useDefaultTransformers = false
        install(io.ktor.client.plugins.HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
    }

internal val requestProxy = io.ktor.util.AttributeKey<ProxyRef>("flare-request-proxy")

private val requestOrigin = io.ktor.util.AttributeKey<io.ktor.http.Url>("flare-request-origin")

@kotlin.native.HiddenFromObjC
public fun io.ktor.client.request.HttpRequestBuilder.applyMedia(media: dev.dimension.flare.ui.model.UiMedia) {
    attributes.put(requestProxy, media.proxy)
    media.customHeaders?.forEach { (key, value) -> headers.append(key, value) }
}
