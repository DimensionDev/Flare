package dev.dimension.flare.data.datasource.microblog.paging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Single

@Single
internal class HomeTimelineRefreshSessions {
    private val sessions = MutableStateFlow<Map<Pair<String, String>, Session>>(emptyMap())

    fun forTimeline(
        tabId: String,
        pagingKey: String,
    ): Session {
        val key = tabId to pagingKey
        return sessions.updateAndGet { if (key in it) it else it + (key to Session()) }.getValue(key)
    }

    class Session {
        val mutex = Mutex()
        val refreshedSources = mutableSetOf<String>()
    }
}
