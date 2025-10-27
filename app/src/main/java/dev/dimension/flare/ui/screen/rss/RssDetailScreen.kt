package dev.dimension.flare.ui.screen.rss

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.ShareNodes
import dev.dimension.flare.R
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.network.rss.DocumentData
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.RssRichText
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.rss.RssDetailPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.server.AiTLDRPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.fornewid.placeholder.material3.placeholder
import io.ktor.http.Url
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.map
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RssDetailScreen(
    url: String,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter(url) { presenter(url) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    FlareScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FlareTopAppBar(
                title = {},
                navigationIcon = {
                    BackButton(onBack)
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    TextButton(
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = LocalContentColor.current,
                            ),
                        onClick = {
                            uriHandler.openUri(url)
                        },
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Globe,
                            contentDescription = stringResource(R.string.rss_detail_open_in_browser),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.rss_detail_open_in_browser))
                    }
                    IconButton(
                        onClick = {
                            val sendIntent =
                                Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, url)
                                    type = "text/plain"
                                }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        },
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.ShareNodes,
                            contentDescription = stringResource(R.string.rss_detail_share),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.data
                .onSuccess {
                    Text(
                        text = it.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier =
                            Modifier
                                .padding(horizontal = screenHorizontalPadding),
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier =
                            Modifier
                                .padding(horizontal = screenHorizontalPadding),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        state.enableTldr.onSuccess {
                            if (it) {
                                FilledTonalButton(
                                    onClick = {
                                        state.setShowTldr(true)
                                    },
                                ) {
                                    Text(stringResource(R.string.rss_detail_tldr))
                                }
                            }
                        }
                    }
                }.onLoading {
                    Text(
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                        modifier =
                            Modifier
                                .padding(horizontal = screenHorizontalPadding)
                                .placeholder(true),
                    )
                }
            state.data
                .onSuccess { data ->
                    AnimatedVisibility(state.showTldr) {
                        state.tldrState?.let { tldrState ->
                            Column(
                                modifier =
                                    Modifier
                                        .animateContentSize()
                                        .fillMaxWidth()
                                        .listCard()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(8.dp)
                                        .clickable {
                                            tldrState.onError {
                                                state.refreshTldr()
                                            }
                                        },
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AnimatedContent(tldrState) {
                                    it
                                        .onSuccess {
                                            Text(
                                                text = it,
                                                modifier =
                                                    Modifier
                                                        .padding(
                                                            horizontal = screenHorizontalPadding,
                                                            vertical = 8.dp,
                                                        ),
                                            )
                                        }.onLoading {
                                            LinearWavyProgressIndicator(
                                                modifier =
                                                    Modifier
                                                        .padding(
                                                            horizontal = screenHorizontalPadding,
                                                            vertical = 8.dp,
                                                        ),
                                            )
                                        }.onError {
                                            Text(
                                                text = stringResource(R.string.rss_detail_tldr_error),
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier =
                                                    Modifier
                                                        .padding(
                                                            horizontal = screenHorizontalPadding,
                                                            vertical = 8.dp,
                                                        ),
                                            )
                                        }
                                }
                            }
                        }
                    }
                    SelectionContainer(
                        modifier =
                            Modifier
                                .listCard()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                    ) {
                        RssRichText(
                            element = data.richText.data,
                            imageHeader = state.headers,
                        )
                    }
                }.onLoading {
                    Text(
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nam elementum eget dui a bibendum. Fusce eget porttitor est, et rhoncus massa. Etiam cursus urna at odio vulputate semper. Interdum et malesuada fames ac ante ipsum primis in faucibus. Quisque tincidunt rhoncus massa sed volutpat. Nulla porta orci et finibus accumsan. Duis maximus diam quis congue suscipit. Suspendisse velit enim, mollis non tellus eu, auctor vulputate diam. Sed ut purus eleifend, tempor lectus ac, imperdiet tellus. Proin eleifend lorem ut risus gravida, id bibendum metus posuere. Cras pretium tortor mi. Quisque ac congue urna. Morbi posuere ac orci vestibulum euismod. Maecenas venenatis, justo at aliquet venenatis, arcu mauris sodales ligula, a iaculis nulla eros at turpis. Quisque varius lobortis porttitor.",
                        modifier =
                            Modifier
                                .listCard()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = screenHorizontalPadding, vertical = 8.dp)
                                .placeholder(true),
                    )
                }
        }
    }
}

@Composable
private fun presenter(
    url: String,
    settingsRepository: SettingsRepository = koinInject(),
) = run {
    val state =
        remember(url) {
            RssDetailPresenter(url)
        }.invoke()
    val headers =
        remember(url) {
            persistentMapOf(
                "Referer" to "https://${Url(url).host}/",
                "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Ubuntu Chromium/70.0.3538.77 Chrome/70.0.3538.77 Safari/537.36",
            )
        }
    val enableTldr by remember {
        settingsRepository.appSettings.map { it.aiConfig.tldr }
    }.collectAsUiState()
    var showTldr by remember { mutableStateOf(false) }
    var tldrRefreshKey by remember { mutableIntStateOf(0) }
    val tldrState =
        if (showTldr) {
            key(tldrRefreshKey, state.data) {
                state.data.flatMap {
                    AiTLDRPresenter(
                        it.encodeJson(DocumentData.serializer()),
                        Locale.current.toLanguageTag(),
                    ).invoke()
                }
            }
        } else {
            null
        }
    object : RssDetailPresenter.State by state {
        val headers = headers
        val enableTldr = enableTldr
        val tldrState = tldrState
        val showTldr = showTldr

        fun setShowTldr(value: Boolean) {
            showTldr = value
        }

        fun refreshTldr() {
            tldrRefreshKey++
        }
    }
}
