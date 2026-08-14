package dev.dimension.flare.feature.plugin.login

import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.plugin.adapter.addPluginAccount
import dev.dimension.flare.feature.plugin.wire.LoginSuccessV1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns OAuth callback completion so a flow can finish with or without its original login UI. */
public class PluginOAuthCallbackCoordinatorV1(
    private val oauth: PluginOAuthLoginCoordinatorV1,
    private val accountService: AccountService,
    private val onUnattendedFailure: (Throwable) -> Unit = {},
) {
    private val mutex = Mutex()
    private val listeners = mutableMapOf<String, suspend (LoginSuccessV1) -> Unit>()
    private val completed = linkedSetOf<String>()

    public fun canHandle(url: String): Boolean = runCatching { parsePluginOAuthCallback(url) }.isSuccess

    public suspend fun handle(
        url: String,
        locale: String,
    ): Boolean {
        val callback = runCatching { parsePluginOAuthCallback(url) }.getOrNull() ?: return false
        return mutex.withLock {
            if (callback.flowId in completed) return@withLock true
            val listener = listeners[callback.flowId]
            try {
                val result = oauth.resumeResult(url, locale)
                if (listener != null) {
                    listener(result.success)
                    listeners.remove(callback.flowId)
                } else {
                    accountService.addPluginAccount(result.plugin, result.success)
                }
                completed += callback.flowId
                while (completed.size > MAX_COMPLETED_FLOWS) completed.remove(completed.first())
                true
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (listener == null) runCatching { onUnattendedFailure(error) }
                throw error
            }
        }
    }

    internal suspend fun register(
        flowId: String,
        listener: suspend (LoginSuccessV1) -> Unit,
    ) {
        pluginOAuthRedirectUri(flowId)
        mutex.withLock {
            require(flowId !in completed) { "OAuth flow already completed" }
            listeners[flowId] = listener
        }
    }

    internal suspend fun unregister(flowId: String) {
        mutex.withLock { listeners.remove(flowId) }
    }
}

private const val MAX_COMPLETED_FLOWS = 32
