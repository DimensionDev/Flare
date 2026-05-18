package dev.dimension.flare.ui.presenter.home.xqt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTwitterArticle
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
    private val accountRepository: AccountRepository by inject()

    @Immutable
    public interface State {
        public val data: UiState<UiTwitterArticle>
    }

    @Composable
    override fun body(): State {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val data by produceState<UiState<UiTwitterArticle>>(
            initialValue = UiState.Loading(),
            serviceState,
            tweetId,
            articleId,
        ) {
            value =
                when (serviceState) {
                    is UiState.Loading -> {
                        UiState.Loading()
                    }

                    is UiState.Error -> {
                        UiState.Error(serviceState.throwable)
                    }

                    is UiState.Success -> {
                        val service = serviceState.data as? XQTDataSource
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
