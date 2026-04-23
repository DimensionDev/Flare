package dev.dimension.flare.ui.screen.rss

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Chrome
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.ShareNodes
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.copied_to_clipboard
import dev.dimension.flare.data.network.rss.DocumentData
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.rss_detail_open_in_browser
import dev.dimension.flare.rss_detail_tldr
import dev.dimension.flare.rss_detail_tldr_error
import dev.dimension.flare.rss_detail_translate
import dev.dimension.flare.status_share
import dev.dimension.flare.ui.component.ComposeInAppNotification
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FavIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.RssRichText
import dev.dimension.flare.ui.component.listCard
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
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.ListItemSeparator
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.ktor.http.Url
import kotlinx.coroutines.flow.map
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
internal fun RssDetailScreen(
    url: String,
    descriptionHtml: String? = null,
    descriptionTitle: String? = null,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val state by producePresenter(url) { presenter(url, descriptionHtml, descriptionTitle) }
    val inAppNotification: ComposeInAppNotification = koinInject()
    val scrollState = rememberScrollState()
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        FlareScrollBar(
            state = scrollState,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = 600.dp)
                            .padding(horizontal = screenHorizontalPadding, vertical = 16.dp)
                            .padding(LocalWindowPadding.current),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.data
                        .onSuccess { data ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SubtleButton(
                                    onClick = {
                                        uriHandler.openUri(url)
                                    },
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Brands.Chrome,
                                        contentDescription = stringResource(Res.string.rss_detail_open_in_browser),
                                    )
                                }
                                SubtleButton(
                                    onClick = {
                                        shareRssText(title = data.title, url = url)
                                        inAppNotification.message(Res.string.copied_to_clipboard)
                                    },
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.ShareNodes,
                                        contentDescription = stringResource(Res.string.status_share),
                                    )
                                }
                                AnimatedVisibility(state.data.isSuccess && !state.isAutoTranslate && !state.enableTranslate) {
                                    SubtleButton(
                                        onClick = {
                                            state.setEnableTranslate(true)
                                        },
                                    ) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.Language,
                                            contentDescription = stringResource(Res.string.rss_detail_translate),
                                        )
                                    }
                                }
                                AnimatedVisibility(
                                    state.data.isSuccess &&
                                        state.enableTldr.takeSuccessOr(
                                            false,
                                        ),
                                ) {
                                    SubtleButton(
                                        onClick = {
                                            state.setShowTldr(true)
                                        },
                                    ) {
                                        Text(stringResource(Res.string.rss_detail_tldr))
                                    }
                                }
                            }
                            val displayTitle =
                                state.translateState?.translatedTitle?.let { titleState ->
                                    when (titleState) {
                                        is UiState.Success -> titleState.data
                                        else -> data.title
                                    }
                                } ?: data.title
                            if (displayTitle.isNotEmpty()) {
                                SelectionContainer {
                                    Text(
                                        text = displayTitle,
                                        style = FluentTheme.typography.subtitle,
                                    )
                                }
                            }
                            if (data.siteName != null || data.byline != null || data.publishDateTime != null) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    data.siteName?.let {
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
                                                style = FluentTheme.typography.caption,
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        data.byline?.let {
                                            Text(
                                                text = it,
                                                style = FluentTheme.typography.caption,
                                            )
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        data.publishDateTime?.let {
                                            DateTimeText(
                                                data = it,
                                                style = FluentTheme.typography.caption,
                                                fullTime = true,
                                            )
                                        }
                                    }
                                }
                            }
                            ListItemSeparator(Modifier.fillMaxWidth())
                        }.onLoading {
                            ProgressRing(modifier = Modifier.align(Alignment.CenterHorizontally))
                        }.onError {
                            Text(
                                text = it.message ?: "Failed to load article",
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
                                                .background(FluentTheme.colors.background.card.default)
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
                                                    ProgressRing(
                                                        modifier =
                                                            Modifier
                                                                .padding(
                                                                    horizontal = screenHorizontalPadding,
                                                                    vertical = 8.dp,
                                                                ),
                                                    )
                                                }.onError {
                                                    Text(
                                                        text = stringResource(Res.string.rss_detail_tldr_error),
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
                            state.translateState?.translatedHtml?.onLoading {
                                ProgressBar(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = screenHorizontalPadding),
                                )
                            }
                            SelectionContainer {
                                val displayData =
                                    state.translateState?.translatedHtml?.let { htmlState ->
                                        when (htmlState) {
                                            is UiState.Success -> data.copy(content = htmlState.data)
                                            else -> data
                                        }
                                    } ?: data
                                RssRichText(
                                    element = displayData.element,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            state.translateState?.translatedHtml?.onError {
                                Text(
                                    text = it.message ?: "Translation failed",
                                    modifier =
                                        Modifier
                                            .padding(horizontal = screenHorizontalPadding)
                                            .clickable { state.refreshTranslate() },
                                )
                            }
                        }
                }
            }
        }
    }
}

private fun shareRssText(
    title: String,
    url: String,
) {
    val text =
        if (title.isBlank()) {
            url
        } else {
            "$title\n$url"
        }
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
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
    val isAutoTranslate =
        preTranslate is UiState.Success && (preTranslate as UiState.Success<Boolean>).data
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
        val enableTldr = enableTldr
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
