package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.database.app.model.DraftTargetStatus
import dev.dimension.flare.data.model.tab.TimelinePresenterFactory
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.repository.DraftGroup
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.DraftTargetKey
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiDraftMedia
import dev.dimension.flare.ui.model.UiDraftMediaType
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiOutboxPost
import dev.dimension.flare.ui.model.UiOutboxStatus
import dev.dimension.flare.ui.model.UiOutboxTarget
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.compose.ComposeUseCase
import dev.dimension.flare.ui.presenter.compose.toComposeData
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Instant

public class TimelineItemPresenter(
    private val timelineTabItem: UiTimelineTabItem,
    private val isHomeTimeline: Boolean = false,
) : PresenterBase<TimelineItemPresenter.State>() {
    private val timelinePresenterFactory by koinInject<TimelinePresenterFactory>()
    private val timelineResolver by koinInject<TimelineResolver>()
    private val draftRepository by koinInject<DraftRepository>()
    private val composeUseCase by koinInject<ComposeUseCase>()

    private val outboxAccountKeys by lazy {
        if (isHomeTimeline) {
            timelineResolver.resolveAccountKeys(timelineTabItem)
        } else {
            emptySet()
        }
    }

    public interface State {
        public val listState: PagingState<UiTimelineV2>
        public val outboxItems: ImmutableList<UiOutboxPost>

        public fun refreshSync()

        public suspend fun refreshSuspend()

        public val isRefreshing: Boolean

        public fun retryOutbox(groupId: String)

        public fun deleteOutbox(groupId: String)
    }

    private val timelinePresenter by lazy {
        timelinePresenterFactory.create(timelineTabItem, isHomeTimeline)
    }

    @Composable
    override fun body(): State {
        val state = timelinePresenter.body()
        val scope = rememberCoroutineScope()
        val outboxItems =
            if (outboxAccountKeys.isEmpty()) {
                persistentListOf()
            } else {
                val drafts by draftRepository.outboxDrafts.collectAsState(emptyList())
                val accountsState = remember { AccountsPresenter() }.body()
                remember(drafts, accountsState.accounts, outboxAccountKeys) {
                    val accountItems = accountsState.accounts.takeSuccess().orEmpty()
                    val accountMap = accountItems.associate { it.account.accountKey to it.account }
                    val avatarMap =
                        accountItems.associate { item ->
                            item.account.accountKey to item.profile.takeSuccess()?.avatar
                        }
                    drafts.toUiOutboxPosts(
                        accountKeys = outboxAccountKeys,
                        accountMap = accountMap,
                        avatarMap = avatarMap,
                    )
                }
            }

        suspend fun refreshWithOutboxCleanup() {
            val success = state.listState as? PagingState.Success<UiTimelineV2>
            val sentTargets = outboxItems.sentTargetKeys()
            state.refresh()
            if (success != null && success.refreshError == null) {
                draftRepository.deleteTargets(sentTargets)
            }
        }

        val loadedRemoteKeys =
            (state.listState as? PagingState.Success<UiTimelineV2>)
                ?.let { success ->
                    buildSet {
                        repeat(success.itemCount) { index ->
                            success.peek(index)?.statusKey?.let(::add)
                        }
                    }
                }.orEmpty()
        val matchedTargets = outboxItems.sentTargetKeys(remoteKeys = loadedRemoteKeys)
        LaunchedEffect(matchedTargets) {
            if (matchedTargets.isNotEmpty()) {
                draftRepository.deleteTargets(matchedTargets)
            }
        }

        val completedTargets =
            outboxItems
                .filter { it.status != UiOutboxStatus.SENDING }
                .sentTargetKeys()
        LaunchedEffect(completedTargets) {
            if (completedTargets.isNotEmpty()) {
                delay(3_000)
                refreshWithOutboxCleanup()
            }
        }

        return object : State {
            override val listState = state.listState
            override val outboxItems = outboxItems
            override val isRefreshing = listState.isRefreshing

            override fun refreshSync() {
                scope.launch {
                    refreshWithOutboxCleanup()
                }
            }

            override suspend fun refreshSuspend() {
                refreshWithOutboxCleanup()
            }

            override fun retryOutbox(groupId: String) {
                composeUseCase.sendDraft(groupId)
            }

            override fun deleteOutbox(groupId: String) {
                scope.launch {
                    draftRepository.deleteGroup(groupId)
                }
            }
        }
    }
}

private fun List<DraftGroup>.toUiOutboxPosts(
    accountKeys: Set<MicroBlogKey>,
    accountMap: Map<MicroBlogKey, UiAccount>,
    avatarMap: Map<MicroBlogKey, UiMedia.Image?>,
): ImmutableList<UiOutboxPost> =
    mapNotNull { draft ->
        val targets =
            draft.targets
                .filter { it.accountKey in accountKeys }
                .mapNotNull { target ->
                    val status = target.status.toUiOutboxStatus() ?: return@mapNotNull null
                    val account = accountMap[target.accountKey] ?: return@mapNotNull null
                    UiOutboxTarget(
                        account = account,
                        avatar = avatarMap[target.accountKey],
                        status = status,
                        progressCurrent = target.progressCurrent.coerceIn(0, target.progressMax),
                        progressMax = target.progressMax.coerceAtLeast(1),
                        errorMessage = target.errorMessage,
                        remotePostKey = target.remotePostKey,
                    )
                }
        if (targets.isEmpty()) {
            return@mapNotNull null
        }
        val status =
            when {
                targets.any { it.status == UiOutboxStatus.SENDING } -> UiOutboxStatus.SENDING
                targets.any { it.status == UiOutboxStatus.FAILED } -> UiOutboxStatus.FAILED
                else -> UiOutboxStatus.SENT
            }
        UiOutboxPost(
            groupId = draft.groupId,
            status = status,
            updatedAt = Instant.fromEpochMilliseconds(draft.updatedAt).toUi(),
            targets = targets.toImmutableList(),
            data = draft.content.toComposeData(medias = emptyList()),
            medias =
                draft.medias
                    .map { media ->
                        UiDraftMedia(
                            cachePath = media.cachePath,
                            fileName = media.fileName,
                            type =
                                when (media.mediaType) {
                                    DraftMediaType.IMAGE -> UiDraftMediaType.IMAGE
                                    DraftMediaType.VIDEO -> UiDraftMediaType.VIDEO
                                    DraftMediaType.OTHER -> UiDraftMediaType.OTHER
                                },
                            altText = media.altText,
                        )
                    }.toImmutableList(),
            progressCurrent = targets.sumOf { it.progressCurrent },
            progressMax = targets.sumOf { it.progressMax },
        )
    }.toImmutableList()

private fun DraftTargetStatus.toUiOutboxStatus(): UiOutboxStatus? =
    when (this) {
        DraftTargetStatus.DRAFT -> null
        DraftTargetStatus.SENDING -> UiOutboxStatus.SENDING
        DraftTargetStatus.SENT -> UiOutboxStatus.SENT
        DraftTargetStatus.FAILED -> UiOutboxStatus.FAILED
    }

private fun List<UiOutboxPost>.sentTargetKeys(remoteKeys: Set<MicroBlogKey>? = null): List<DraftTargetKey> =
    flatMap { item ->
        item.targets
            .filter { target ->
                target.status == UiOutboxStatus.SENT &&
                    (remoteKeys == null || target.remotePostKey?.let(remoteKeys::contains) == true)
            }.map { target ->
                DraftTargetKey(groupId = item.groupId, accountKey = target.account.accountKey)
            }
    }

@WebPresenter("timelineItem")
public class WebTimelineItemPresenter(
    private val loaderKey: String,
) : PresenterBase<TimelineItemPresenter.State>() {
    private val timelineResolver by koinInject<TimelineResolver>()

    private val delegate by lazy {
        TimelineItemPresenter(timelineResolver.toTabItem(loaderKey))
    }

    @Composable
    override fun body(): TimelineItemPresenter.State = delegate.body()
}
