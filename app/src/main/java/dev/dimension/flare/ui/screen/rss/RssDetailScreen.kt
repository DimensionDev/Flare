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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
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
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Chrome
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.ShareNodes
import dev.dimension.flare.R
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.network.rss.DocumentData
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FavIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.RssRichText
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccessOr
import dev.dimension.flare.ui.presenter.home.rss.RssDetailPresenter
import dev.dimension.flare.ui.presenter.home.rss.RssDetailTranslatePresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.server.AiTLDRPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.ktor.http.Url
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.map
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RssDetailScreen(
    url: String,
    descriptionHtml: String? = null,
    descriptionTitle: String? = null,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter(url) { presenter(url, descriptionHtml, descriptionTitle) }
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
                    IconButton(
                        onClick = {
                            uriHandler.openUri(url)
                        },
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Brands.Chrome,
                            contentDescription = stringResource(R.string.rss_detail_open_in_browser),
                        )
                    }
                    IconButton(
                        onClick = {
                            val title =
                                when (val data = state.data) {
                                    is UiState.Success -> data.data.title.takeIf { it.isNotBlank() }
                                    else -> null
                                }
                                    ?: descriptionTitle?.takeIf { it.isNotBlank() }
                            val sendIntent =
                                Intent().apply {
                                    action = Intent.ACTION_SEND
                                    title?.let {
                                        putExtra(Intent.EXTRA_TITLE, it)
                                        putExtra(Intent.EXTRA_SUBJECT, it)
                                    }
                                    putExtra(Intent.EXTRA_TEXT, url)
                                    type = "text/plain"
                                }
                            val shareIntent = Intent.createChooser(sendIntent, title)
                            context.startActivity(shareIntent)
                        },
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.ShareNodes,
                            contentDescription = stringResource(R.string.rss_detail_share),
                        )
                    }
                    AnimatedVisibility(state.data.isSuccess && !state.isAutoTranslate && !state.enableTranslate) {
                        IconButton(
                            onClick = {
                                state.setEnableTranslate(true)
                            },
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.Language,
                                contentDescription = stringResource(R.string.rss_detail_translate),
                            )
                        }
                    }
                    AnimatedVisibility(state.data.isSuccess && state.enableTldr.takeSuccessOr(false)) {
                        TextButton(
                            onClick = {
                                state.setShowTldr(true)
                            },
                        ) {
                            Text(stringResource(R.string.rss_detail_tldr))
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 600.dp)
                        .padding(contentPadding)
                        .padding(horizontal = screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                state.data
                    .onSuccess { documentData ->
                        // Show translated title if available, otherwise original
                        val displayTitle =
                            state.translateState?.translatedTitle?.let { titleState ->
                                when (titleState) {
                                    is UiState.Success -> titleState.data
                                    else -> documentData.title
                                }
                            } ?: documentData.title
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            modifier =
                            Modifier,
                        )
                        if (documentData.siteName != null || documentData.byline != null || documentData.publishDateTime != null) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                documentData.siteName?.let {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        FavIcon(
                                            host =
                                                remember {
                                                    Url(url).host
                                                },
                                            size = 16.dp,
                                        )
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    documentData.byline?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    documentData.publishDateTime?.let {
                                        DateTimeText(
                                            data = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            fullTime = true,
                                        )
                                    }
                                }
                            }
                        }
                    }.onLoading {
                        Text(
                            "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                            modifier =
                                Modifier
                                    .placeholder(true),
                        )
                    }
                HorizontalDivider()
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
                        // Show translation loading indicator
                        state.translateState?.translatedHtml?.onLoading {
                            LinearWavyProgressIndicator(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                            )
                        }
                        SelectionContainer(
                            modifier =
                            Modifier,
                        ) {
                            // Use translated content if available by creating a new DocumentData
                            // whose lazy .element will re-parse the translated HTML
                            val displayData =
                                state.translateState?.translatedHtml?.let { htmlState ->
                                    when (htmlState) {
                                        is UiState.Success -> data.copy(content = htmlState.data)
                                        else -> data
                                    }
                                } ?: data
                            RssRichText(
                                element = displayData.element,
                                imageHeader = state.headers,
                            )
                        }
                        // Show translation error if any
                        state.translateState?.translatedHtml?.onError {
                            Text(
                                text = stringResource(R.string.rss_detail_translate_error),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier =
                                    Modifier
                                        .clickable { state.refreshTranslate() },
                            )
                        }
                    }.onLoading {
                        Text(
                            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nam elementum eget dui a bibendum. Fusce eget porttitor est, et rhoncus massa. Etiam cursus urna at odio vulputate semper. Interdum et malesuada fames ac ante ipsum primis in faucibus. Quisque tincidunt rhoncus massa sed volutpat. Nulla porta orci et finibus accumsan. Duis maximus diam quis congue suscipit. Suspendisse velit enim, mollis non tellus eu, auctor vulputate diam. Sed ut purus eleifend, tempor lectus ac, imperdiet tellus. Proin eleifend lorem ut risus gravida, id bibendum metus posuere. Cras pretium tortor mi. Quisque ac congue urna. Morbi posuere ac orci vestibulum euismod. Maecenas venenatis, justo at aliquet venenatis, arcu mauris sodales ligula, a iaculis nulla eros at turpis. Quisque varius lobortis porttitor.",
                            modifier =
                                Modifier
                                    .placeholder(true),
                        )
                    }
            }
        }
    }
}

@Composable
private fun presenter(
    url: String,
    descriptionHtml: String? = null,
    descriptionTitle: String? = null,
    settingsRepository: SettingsRepository = koinInject(),
) = run {
    val state =
        remember(url, descriptionHtml) {
            RssDetailPresenter(url, descriptionHtml, descriptionTitle)
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
    val preTranslate by remember {
        settingsRepository.appSettings.map { it.translateConfig.preTranslate }
    }.collectAsUiState()
    var showTldr by remember { mutableStateOf(false) }
    var tldrRefreshKey by remember { mutableIntStateOf(0) }
    var enableTranslate by remember { mutableStateOf(false) }
    var translateRefreshKey by remember { mutableIntStateOf(0) }
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
    // Determine if translation should be active:
    // auto-translate (preTranslate=true) or manually triggered
    val isAutoTranslate = preTranslate is UiState.Success && (preTranslate as UiState.Success<Boolean>).data
    val shouldTranslate = isAutoTranslate || enableTranslate
    val translateState: RssDetailTranslatePresenter.State? =
        if (shouldTranslate) {
            when (val dataState = state.data) {
                is UiState.Success -> {
                    key(translateRefreshKey) {
                        remember(dataState.data.content, dataState.data.title) {
                            RssDetailTranslatePresenter(
                                htmlContent = dataState.data.content,
                                title = dataState.data.title,
                                targetLanguage = Locale.current.toLanguageTag(),
                            )
                        }.invoke()
                    }
                }

                else -> {
                    null
                }
            }
        } else {
            null
        }
    object : RssDetailPresenter.State by state {
        val headers = headers
        val enableTldr = enableTldr
        val preTranslate = preTranslate
        val isAutoTranslate = isAutoTranslate
        val tldrState = tldrState
        val showTldr = showTldr
        val translateState = translateState
        val enableTranslate = enableTranslate

        fun setShowTldr(value: Boolean) {
            showTldr = value
        }

        fun refreshTldr() {
            tldrRefreshKey++
        }

        fun setEnableTranslate(value: Boolean) {
            enableTranslate = value
        }

        fun refreshTranslate() {
            translateRefreshKey++
        }
    }
}
