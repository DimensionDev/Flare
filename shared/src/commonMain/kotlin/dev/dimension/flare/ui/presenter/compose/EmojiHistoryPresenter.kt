package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

public class EmojiHistoryPresenter(
    private val accountType: AccountType,
    private val emojis: ImmutableList<UiEmoji>,
) : PresenterBase<EmojiHistoryPresenter.State>(),
    KoinComponent {
    private val cacheDatabase by inject<CacheDatabase>()
    private val accountRepository by inject<AccountRepository>()
    private val scope by inject<CoroutineScope>()

    public interface State {
        public val history: UiState<ImmutableList<UiEmoji>>

        public fun addHistory(emoji: UiEmoji)
    }

    @Composable
    override fun body(): State {
        val accountState by accountProvider(accountType, accountRepository)
        val historyState =
            accountState.flatMap { account ->
                produceState(
                    initialValue = UiState.Loading(),
                    key1 = account,
                ) {
                    // do not use flow, call suspend function just once to avoid list flickering
                    value =
                        cacheDatabase
                            .emojiDao()
                            .getHistory(AccountType.Specific(account.accountKey))
                            .mapNotNull { history ->
                                emojis.firstOrNull { emoji -> emoji.shortcode == history.shortCode }
                            }.toImmutableList()
                            .let {
                                UiState.Success(it)
                            }
                }.value
            }
        return object : State {
            override val history = historyState

            override fun addHistory(emoji: UiEmoji) {
                accountState.onSuccess { account ->
                    scope.launch {
                        cacheDatabase.emojiDao().insertHistory(
                            dev.dimension.flare.data.database.cache.model.DbEmojiHistory(
                                accountType = AccountType.Specific(account.accountKey),
                                shortCode = emoji.shortcode,
                                lastUse = Clock.System.now().toEpochMilliseconds(),
                            ),
                        )
                    }
                }
            }
        }
    }
}
