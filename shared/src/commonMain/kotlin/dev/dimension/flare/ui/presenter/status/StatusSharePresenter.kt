package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.TranslationDisplayState
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.model.contentPostOrNull
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

public class StatusSharePresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusSharePresenter.State>() {
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): State {
        val status = remember { StatusPresenter(accountType, statusKey) }.body().status
        val scope = rememberCoroutineScope()
        val session =
            remember {
                var source: dev.dimension.flare.data.datasource.microblog.MicroblogDataSource? = null
                StatusShareSession(rootKey = statusKey, childLoader = { source?.shareReplyChildren(it) }) {
                    accountServiceFlow(accountType, accountRepository).first().also { source = it }.shareReplies(statusKey)
                }
            }
        return object : State {
            override val status = status.map { it.forScreenshot() }
            override val replies = session.replies
            override val selectedReplies = session.selectedReplies
            override val expandableReplies = session.expandableReplies
            override val selecting = session.draftKeys != null
            override val selectedCount = session.draftKeys?.size ?: selectedReplies.size
            override val loading = session.loading
            override val error = session.error
            override val hasMore = session.hasMore
            private val keys = session.draftKeys.orEmpty()

            override fun isSelected(key: MicroBlogKey): Boolean = key in keys

            override fun selectReplies() {
                session.beginSelection()
                if (!session.started) loadMore()
            }

            override fun toggleReply(key: MicroBlogKey) = session.toggle(key)

            override fun clearSelection() = session.clear()

            override fun cancelSelection() = session.cancel()

            override fun confirmSelection() = session.confirm()

            override fun loadMore() {
                scope.launch { session.loadMore() }
            }

            override fun loadReplyChildren(key: MicroBlogKey) {
                scope.launch { session.loadChildren(key) }
            }
        }
    }

    @Immutable
    public interface State : StatusState {
        public val replies: ImmutableList<UiTimelineV2>
        public val selectedReplies: ImmutableList<UiTimelineV2>
        public val expandableReplies: ImmutableList<MicroBlogKey>
        public val selecting: Boolean
        public val selectedCount: Int
        public val loading: Boolean
        public val error: Throwable?
        public val hasMore: Boolean

        public fun isSelected(key: MicroBlogKey): Boolean

        public fun selectReplies()

        public fun toggleReply(key: MicroBlogKey)

        public fun clearSelection()

        public fun cancelSelection()

        public fun confirmSelection()

        public fun loadMore()

        public fun loadReplyChildren(key: MicroBlogKey)
    }
}

internal class StatusShareSession(
    private val rootKey: MicroBlogKey? = null,
    private val childLoader: (UiTimelineV2) -> RemoteLoader<UiTimelineV2>? = { null },
    private val createLoader: suspend () -> RemoteLoader<UiTimelineV2>,
) {
    var replies by mutableStateOf(emptyList<UiTimelineV2>().toImmutableList())
        private set
    var selectedReplies by mutableStateOf(emptyList<UiTimelineV2>().toImmutableList())
        private set
    var draftKeys by mutableStateOf<Set<MicroBlogKey>?>(null)
        private set
    var expandableReplies by mutableStateOf(emptyList<MicroBlogKey>().toImmutableList())
        private set

    private class ChildPage(
        val loader: RemoteLoader<UiTimelineV2>,
    ) {
        var started = false
        var nextKey: String? = null
        var items: List<UiTimelineV2> = emptyList()
    }

    private val children = mutableMapOf<MicroBlogKey, ChildPage>()
    private var failedChild: MicroBlogKey? = null
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    var hasMore by mutableStateOf(true)
        private set
    var started = false
        private set
    private var loader: RemoteLoader<UiTimelineV2>? = null
    private var nextKey: String? = null

    fun beginSelection() {
        draftKeys = selectedReplies.map { it.statusKey }.toSet()
    }

    fun toggle(key: MicroBlogKey) {
        val keys = draftKeys ?: return
        if (replies.none { it.statusKey == key }) return
        draftKeys = if (key in keys) keys - key else keys + key
    }

    fun clear() {
        if (draftKeys != null) draftKeys = emptySet()
    }

    fun cancel() {
        draftKeys = null
    }

    fun confirm() {
        val keys = draftKeys ?: return
        selectedReplies = replies.filter { it.statusKey in keys }.map { it.forScreenshot() }.toImmutableList()
        draftKeys = null
    }

    suspend fun loadMore() {
        failedChild?.let {
            loadChildren(it)
            return
        }
        if (loading || !hasMore) return
        loading = true
        error = null
        try {
            val source = loader ?: createLoader().also { loader = it }
            val initial = !started

            suspend fun loadPage() {
                val request = if (!started) PagingRequest.Refresh else PagingRequest.Append(requireNotNull(nextKey))
                val result = source.load(pageSize = 20, request = request)
                result.continuationFailure?.let { throw it }
                result.sourceFailures.firstOrNull()?.let { throw it }
                replies = (replies + result.data).distinctBy { it.statusKey }.toImmutableList()
                registerChildren(result.data)
                nextKey = result.nextKey
                hasMore = nextKey != null
                started = true
            }
            loadPage()
            // Detail sources fetch the focal post before their first reply page.
            if (initial && replies.isEmpty() && hasMore) loadPage()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            error = exception
        } finally {
            loading = false
        }
    }

    private fun registerChildren(items: List<UiTimelineV2>) {
        items.forEach { item ->
            if (item.statusKey !in children) {
                childLoader(item)?.let {
                    children[item.statusKey] = ChildPage(it)
                    expandableReplies = (expandableReplies + item.statusKey).toImmutableList()
                }
            }
        }
    }

    suspend fun loadChildren(key: MicroBlogKey) {
        val page = children[key] ?: return
        if (loading || (page.started && page.nextKey == null)) return
        loading = true
        error = null
        try {
            val request = if (page.started) PagingRequest.Append(requireNotNull(page.nextKey)) else PagingRequest.Refresh
            val result = page.loader.load(20, request)
            result.continuationFailure?.let { throw it }
            result.sourceFailures.firstOrNull()?.let { throw it }
            val parent = replies.first { it.statusKey == key }.contentPostOrNull()
            val newItems =
                result.data.filter { it.statusKey != rootKey && it.statusKey != key }.map { item ->
                    val post = item.asTimelinePostItem()
                    if (post == null || parent == null) {
                        item
                    } else {
                        post.copy(
                            post =
                                post.post.copy(
                                    replyToHandle = post.post.replyToHandle ?: parent.user?.handle?.display,
                                    references =
                                        (
                                            post.post.references +
                                                UiTimelineV2.Post.Reference(
                                                    key,
                                                    ReferenceType.Reply,
                                                )
                                        ).distinct().toImmutableList(),
                                ),
                        )
                    }
                }
            page.items = (page.items + newItems).distinctBy { it.statusKey }
            val keys = page.items.map { it.statusKey }.toSet()
            val remaining = replies.filter { it.statusKey !in keys }
            val index = remaining.indexOfFirst { it.statusKey == key } + 1
            replies = (remaining.take(index) + page.items + remaining.drop(index)).toImmutableList()
            page.started = true
            page.nextKey = result.nextKey
            failedChild = null
            if (page.nextKey == null) expandableReplies = expandableReplies.filter { it != key }.toImmutableList()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            failedChild = key
            error = exception
        } finally {
            loading = false
        }
    }
}

internal fun UiTimelineV2.forScreenshot(): UiTimelineV2 =
    when (this) {
        is UiTimelineV2.Post -> {
            copy(translationDisplayState = TranslationDisplayState.Hidden)
        }

        is UiTimelineV2.TimelinePostItem -> {
            copy(
                post = post.forScreenshot() as UiTimelineV2.Post,
                presentation =
                    presentation.copy(
                        inlineParents =
                            presentation.inlineParents
                                .map { it.forScreenshot() as UiTimelineV2.TimelinePostItem }
                                .toImmutableList(),
                        quotes = presentation.quotes.map { it.forScreenshot() as UiTimelineV2.Post }.toImmutableList(),
                        repost = presentation.repost?.forScreenshot() as? UiTimelineV2.Post,
                    ),
            )
        }

        is UiTimelineV2.Feed -> {
            copy(translationDisplayState = TranslationDisplayState.Hidden)
        }

        else -> {
            this
        }
    }
