package dev.dimension.flare.ui.presenter.home.xqt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTwitterArticle
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.mapper.renderArticle
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class TwitterArticlePresenter(
    private val accountType: AccountType,
    private val tweetId: String,
    private val articleId: String? = null,
) : PresenterBase<TwitterArticlePresenter.State>(),
    KoinComponent {
    private val accountService: AccountService by inject()
    private val serviceFlow by lazy {
        accountService.accountServiceFlow(accountType)
    }

    @Immutable
    public interface State {
        public val data: UiState<UiTwitterArticle>
    }

    @Composable
    override fun body(): State {
        val serviceState by serviceFlow.collectAsUiState()
        val data by produceState<UiState<UiTwitterArticle>>(
            initialValue = UiState.Loading(),
            serviceState,
            tweetId,
            articleId,
        ) {
            value =
                when (val state = serviceState) {
                    is UiState.Loading -> {
                        UiState.Loading()
                    }

                    is UiState.Error -> {
                        UiState.Error(state.throwable)
                    }

                    is UiState.Success -> {
                        val service = state.data as? XQTDataSource
                        if (service == null) {
                            UiState.Error(IllegalStateException("Twitter article requires an XQT account"))
                        } else {
                            runCatching {
                                service
                                    .getTweetResultByRestId(tweetId)
                                    ?.renderArticle(
                                        accountKey = service.accountKey,
                                        expectedArticleId = articleId,
                                    ) ?: error("Twitter article not found")
                            }.fold(
                                onSuccess = { UiState.Success(it) },
                                onFailure = { UiState.Error(it) },
                            )
                        }
                    }
                }
        }
        return object : State {
            override val data: UiState<UiTwitterArticle> = data
        }
    }
}
