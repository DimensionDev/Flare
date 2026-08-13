package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.common.Locale
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.repository.RequireReLoginException
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.host.PluginAsset
import dev.dimension.flare.feature.plugin.host.PluginCallTimeoutV1
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.runtime.PluginCallException
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimeKeyV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.feature.plugin.wire.PageDirectionV1
import dev.dimension.flare.feature.plugin.wire.PageRequestV1
import dev.dimension.flare.feature.plugin.wire.PageV1
import dev.dimension.flare.feature.plugin.wire.PluginErrorCodeV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.KSerializer

internal class PluginAccountInvokerV1(
    private val plugin: RunningPluginV1,
    private val runtimePool: PluginRuntimePool,
    private val runtimeKey: PluginRuntimeKeyV1,
    private val accountKey: MicroBlogKey?,
    private val context: (locale: String, assets: Map<String, PluginAsset>) -> PluginInvocationContextV1,
) {
    suspend fun <Request, Response> invoke(
        capabilityId: String,
        operation: String,
        request: Request,
        requestSerializer: KSerializer<Request>,
        responseSerializer: KSerializer<Response>,
        timeout: PluginCallTimeoutV1 = PluginCallTimeoutV1.Normal,
        assets: Map<String, PluginAsset> = emptyMap(),
        validate: (Response) -> Unit = {},
    ): Response =
        try {
            runtimePool.invoke(
                plugin = plugin,
                key = runtimeKey,
                context = context(Locale.language, assets),
                method = requireNotNull(PluginAbiV1.capabilityMethod(capabilityId, operation)),
                request = request,
                requestSerializer = requestSerializer,
                responseSerializer = responseSerializer,
                timeout = timeout,
                validate = validate,
            )
        } catch (error: PluginCallException) {
            if (error.error.code == PluginErrorCodeV1.AuthenticationRequired && accountKey != null) {
                throw RequireReLoginException(accountKey, plugin.installed.manifest.platform.id)
            }
            throw error
        }
}

internal fun PagingRequest.toWire(
    pageSize: Int,
    parameters: Map<String, String> = emptyMap(),
): PageRequestV1 =
    PageRequestV1(
        direction =
            when (this) {
                PagingRequest.Refresh -> PageDirectionV1.Refresh
                is PagingRequest.Append -> PageDirectionV1.Older
                is PagingRequest.Prepend -> PageDirectionV1.Newer
            },
        limit = pageSize.coerceIn(1, dev.dimension.flare.feature.plugin.wire.WireLimitsV1.MAX_PAGE_SIZE),
        cursor =
            when (this) {
                PagingRequest.Refresh -> null
                is PagingRequest.Append -> nextKey
                is PagingRequest.Prepend -> previousKey
            },
        parameters = parameters,
    ).also { it.requireValid() }

internal fun <Wire : Any, Ui : Any> pluginRemoteLoader(
    directions: Set<PageDirectionV1>,
    load: suspend (pageSize: Int, request: PagingRequest) -> PageV1<Wire>,
    map: (Wire) -> Ui,
): RemoteLoader<Ui> =
    object : RemoteLoader<Ui> {
        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<Ui> {
            val direction =
                when (request) {
                    PagingRequest.Refresh -> PageDirectionV1.Refresh
                    is PagingRequest.Append -> PageDirectionV1.Older
                    is PagingRequest.Prepend -> PageDirectionV1.Newer
                }
            if (directions.isNotEmpty() && direction !in directions) return PagingResult(endOfPaginationReached = true)
            val page = load(pageSize, request).also { it.requireValid() }
            return PagingResult(
                data = page.items.map(map),
                nextKey = page.olderCursor.takeUnless { page.endReached },
                previousKey = page.newerCursor,
            )
        }
    }
