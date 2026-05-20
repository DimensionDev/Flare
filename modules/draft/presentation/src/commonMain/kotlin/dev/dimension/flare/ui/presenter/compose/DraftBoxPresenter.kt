package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.account.AccountProfileProvider
import dev.dimension.flare.data.database.app.model.DraftMediaType
import dev.dimension.flare.data.draft.ComposeProgressState
import dev.dimension.flare.data.draft.DraftRepository
import dev.dimension.flare.data.draft.SendDraftUseCase
import dev.dimension.flare.data.draft.toComposeData
import dev.dimension.flare.data.draft.toUiDraftStatus
import dev.dimension.flare.ui.model.UiDraft
import dev.dimension.flare.ui.model.UiDraftAccount
import dev.dimension.flare.ui.model.UiDraftMedia
import dev.dimension.flare.ui.model.UiDraftMediaType
import dev.dimension.flare.ui.model.UiDraftStatus
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Instant

public class DraftBoxPresenter :
    PresenterBase<DraftBoxState>(),
    KoinComponent {
    private val draftRepository: DraftRepository by inject()
    private val sendDraftUseCase: SendDraftUseCase by inject()
    private val inAppNotification: InAppNotification by inject()
    private val accountProfileProvider: AccountProfileProvider by inject()
    private val coroutineScope: CoroutineScope by inject()

    @Composable
    override fun body(): DraftBoxState {
        val visibleDrafts by draftRepository.visibleDrafts.collectAsState(emptyList())
        val sendingDrafts by draftRepository.sendingDrafts.collectAsState(emptyList())
        val accountProfiles by accountProfileProvider.accounts.collectAsState(emptyList())

        val items =
            remember(visibleDrafts, sendingDrafts, accountProfiles) {
                val accountMap =
                    accountProfiles.associate { it.account.accountKey to it.account }
                val avatarMap =
                    accountProfiles.associate { it.account.accountKey to it.avatar }
                (visibleDrafts + sendingDrafts)
                    .associateBy { it.groupId }
                    .values
                    .sortedWith(
                        compareBy<dev.dimension.flare.data.draft.DraftGroup>({ it.toUiDraftStatus().sortOrder })
                            .thenByDescending { it.updatedAt },
                    ).mapNotNull { draft ->
                        val accounts =
                            draft.targets.mapNotNull { target ->
                                accountMap[target.accountKey]?.let { account ->
                                    UiDraftAccount(
                                        account = account,
                                        avatar = avatarMap[target.accountKey],
                                    )
                                }
                            }
                        if (accounts.isEmpty()) {
                            return@mapNotNull null
                        }
                        UiDraft(
                            groupId = draft.groupId,
                            status = draft.toUiDraftStatus(),
                            updatedAt = Instant.fromEpochMilliseconds(draft.updatedAt).toUi(),
                            accounts = accounts.toImmutableList(),
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
                        )
                    }.toImmutableList()
            }

        return object : DraftBoxState {
            override val items: ImmutableList<UiDraft> = items

            override fun retry(groupId: String) {
                sendDraft(groupId)
            }

            override fun send(groupId: String) {
                sendDraft(groupId)
            }

            override fun delete(groupId: String) {
                coroutineScope.launch {
                    draftRepository.deleteGroup(groupId)
                }
            }

            private fun sendDraft(groupId: String) {
                coroutineScope.launch {
                    sendDraftUseCase(groupId) {
                        when (it) {
                            is ComposeProgressState.Error -> {
                                inAppNotification.onError(Message.Compose, it.throwable)
                            }

                            is ComposeProgressState.Progress -> {
                                if (it.max > 0) {
                                    inAppNotification.onProgress(
                                        Message.Compose,
                                        it.current,
                                        it.max,
                                    )
                                }
                            }

                            ComposeProgressState.Success -> {
                                inAppNotification.onSuccess(Message.Compose)
                            }
                        }
                    }
                }
            }
        }
    }
}

public interface DraftBoxState {
    public val items: ImmutableList<UiDraft>

    public fun retry(groupId: String)

    public fun send(groupId: String)

    public fun delete(groupId: String)
}

private val UiDraftStatus.sortOrder: Int
    get() =
        when (this) {
            UiDraftStatus.SENDING -> 0
            UiDraftStatus.FAILED -> 1
            UiDraftStatus.DRAFT -> 2
        }
