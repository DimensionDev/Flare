package dev.dimension.flare.ui.screen.xqt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.AccountItem
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.SubcomposeNetworkImage
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.xqt.TwitterArticlePresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun TwitterArticleScreen(
    accountType: AccountType,
    tweetId: String,
    articleId: String?,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val state by producePresenter(key = "$accountType-$tweetId-$articleId") {
        TwitterArticlePresenter(accountType, tweetId, articleId).invoke()
    }
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        FlareScrollBar(state = scrollState) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(scrollState)
                        .padding(horizontal = screenHorizontalPadding, vertical = 16.dp)
                        .padding(LocalWindowPadding.current),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.data
                    .onSuccess { article ->
                        article.image?.let { image ->
                            SubcomposeNetworkImage(
                                model = image,
                                contentDescription = article.title,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .listCard(),
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = article.title,
                                style = FluentTheme.typography.title,
                                modifier =
                                    Modifier
                                        .listCard(0, 3)
                                        .background(
                                            FluentTheme.colors.background.card.default,
                                        ).padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                            )
                            AccountItem(
                                userState = UiState.Success(article.profile),
                                onClick = {
                                    article.profile.onClicked(
                                        ClickContext(
                                            launcher = { uri -> uriHandler.openUri(uri) },
                                        ),
                                    )
                                },
                                toLogin = {},
                                modifier = Modifier.listCard(1, 3),
                            )
                            SelectionContainer(
                                modifier =
                                    Modifier
                                        .listCard(2, 3)
                                        .background(
                                            FluentTheme.colors.background.card.default,
                                        ).padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                            ) {
                                RichText(
                                    text = article.content,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }.onLoading {
                    }.onError {
                        Text(
                            text = it.message ?: "Failed to load article",
                        )
                    }
            }
        }
    }
}
