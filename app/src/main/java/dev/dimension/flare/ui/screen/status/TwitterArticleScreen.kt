package dev.dimension.flare.ui.screen.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.SubcomposeNetworkImage
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.xqt.TwitterArticlePresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.AccountItem
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.single
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TwitterArticleScreen(
    accountType: AccountType,
    tweetId: String,
    articleId: String?,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter(key = "$accountType-$tweetId-$articleId") {
        TwitterArticlePresenter(accountType, tweetId, articleId).invoke()
    }
    FlareScaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    state.data
                        .onSuccess {
                            Text(
                                text = it.title,
                            )
                        }.onLoading {
                            Text(
                                text = "Loading...",
                                modifier = Modifier.placeholder(true),
                            )
                        }
                },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = screenHorizontalPadding)
                    .padding(contentPadding)
                    .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.data
                .onSuccess { data ->
                    data.image?.let { image ->
                        SubcomposeNetworkImage(
                            model = image,
                            contentDescription = data.title,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(
                                        ListItemDefaults.single().shape,
                                    ),
                        )
                    }
                    AccountItem(
                        userState = UiState.Success(data.profile),
                        onClick = {
                            data.profile.onClicked(
                                ClickContext(
                                    launcher = { uri -> uriHandler.openUri(uri) },
                                ),
                            )
                        },
                        toLogin = {},
                        shapes = ListItemDefaults.single(),
                    )
                    SelectionContainer {
                        RichText(
                            text = data.content,
                        )
                    }
                }.onLoading {
                    Text(
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nam elementum eget dui a bibendum. Fusce eget porttitor est, et rhoncus massa. Etiam cursus urna at odio vulputate semper. Interdum et malesuada fames ac ante ipsum primis in faucibus. Quisque tincidunt rhoncus massa sed volutpat. Nulla porta orci et finibus accumsan. Duis maximus diam quis congue suscipit. Suspendisse velit enim, mollis non tellus eu, auctor vulputate diam. Sed ut purus eleifend, tempor lectus ac, imperdiet tellus. Proin eleifend lorem ut risus gravida, id bibendum metus posuere. Cras pretium tortor mi. Quisque ac congue urna. Morbi posuere ac orci vestibulum euismod. Maecenas venenatis, justo at aliquet venenatis, arcu mauris sodales ligula, a iaculis nulla eros at turpis. Quisque varius lobortis porttitor.",
                    )
                }.onError {
                    Text(
                        text = it.message ?: "Failed to load article",
                    )
                }
        }
    }
}
