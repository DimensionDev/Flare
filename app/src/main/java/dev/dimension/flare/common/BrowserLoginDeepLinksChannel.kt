package dev.dimension.flare.common

import kotlinx.coroutines.channels.Channel

object BrowserLoginDeepLinksChannel {
    private val channel: Channel<String> = Channel()

    fun send(uri: String) {
        channel.trySend(uri)
    }

    fun canHandle(uri: String): Boolean {
        return uri.startsWith(AppDeepLink.Callback.Twitter) || uri.startsWith(AppDeepLink.Callback.Mastodon)
    }

    suspend fun waitOne(): String {
        return channel.receive()
    }

    fun cancel() {
        channel.cancel()
    }
}