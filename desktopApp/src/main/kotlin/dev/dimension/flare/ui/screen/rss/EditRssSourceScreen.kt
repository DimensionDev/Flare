package dev.dimension.flare.ui.screen.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleCheck
import compose.icons.fontawesomeicons.solid.CircleChevronDown
import compose.icons.fontawesomeicons.solid.CircleXmark
import dev.dimension.flare.Res
import dev.dimension.flare.add_rss_source
import dev.dimension.flare.cancel
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.edit_rss_source
import dev.dimension.flare.ok
import dev.dimension.flare.rss_sources_discovered_rss_sources
import dev.dimension.flare.rss_sources_pinned_in_tabs
import dev.dimension.flare.rss_sources_rss_hub_host_hint
import dev.dimension.flare.rss_sources_rss_hub_host_label
import dev.dimension.flare.rss_sources_title_label
import dev.dimension.flare.rss_sources_url_label
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.home.rss.CheckRssSourcePresenter
import dev.dimension.flare.ui.presenter.home.rss.EditRssSourcePresenter
import dev.dimension.flare.ui.presenter.invoke
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.CheckBox
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun EditRssSourceScreen(
    onDismissRequest: () -> Unit,
    id: Int?,
    initialUrl: String? = null,
) {
    val state by producePresenter("rss_source_edit_${id}_$initialUrl") { presenter(id, initialUrl) }

    ContentDialog(
        title =
            if (id == null) {
                stringResource(Res.string.add_rss_source)
            } else {
                stringResource(Res.string.edit_rss_source)
            },
        visible = true,
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    state.inputState.onSuccess { inputState ->
                        when (inputState) {
                            is EditRssSourcePresenter.State.RssInputState.RssFeed -> {
                                inputState.save(
                                    title = state.title.text.toString(),
                                )
                                state.save(
                                    sources =
                                        listOf(
                                            state.url.text.toString() to
                                                state.title.text.toString().ifEmpty {
                                                    null
                                                },
                                        ),
                                )
                                onDismissRequest.invoke()
                            }

                            is EditRssSourcePresenter.State.RssInputState.RssHub -> {
                                if (inputState.checkState.isSuccess &&
                                    inputState.checkState.takeSuccess() is CheckRssSourcePresenter.State.RssState.RssFeed
                                ) {
                                    inputState.save(
                                        title = state.title.text.toString(),
                                    )
                                    state.save(
                                        sources =
                                            listOf(
                                                inputState.actualUrl to
                                                    state.title.text.toString().ifEmpty {
                                                        null
                                                    },
                                            ),
                                    )
                                    onDismissRequest.invoke()
                                }
                            }

                            is EditRssSourcePresenter.State.RssInputState.RssSources -> {
                                if (state.selectedSource.isNotEmpty()) {
                                    inputState.save(
                                        sources = state.selectedSource,
                                    )
                                    state.save(
                                        sources =
                                            state.selectedSource.map { source ->
                                                source.url to source.title
                                            },
                                    )
                                    onDismissRequest.invoke()
                                }
                            }
                        }
                    }
                }

                ContentDialogButton.Secondary -> Unit
                ContentDialogButton.Close -> {
                    onDismissRequest.invoke()
                }
            }
        },
        content = {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            Column(
                verticalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(8.dp),
            ) {
                TextField(
                    state = state.url,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    header = { Text(text = stringResource(Res.string.rss_sources_url_label)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    trailing = {
                        state.checkState
                            .onSuccess {
                                when (it) {
                                    is CheckRssSourcePresenter.State.RssState.RssFeed ->
                                        FAIcon(
                                            FontAwesomeIcons.Solid.CircleCheck,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                        )

                                    CheckRssSourcePresenter.State.RssState.RssHub ->
                                        FAIcon(
                                            FontAwesomeIcons.Solid.CircleChevronDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                        )

                                    is CheckRssSourcePresenter.State.RssState.RssSources ->
                                        FAIcon(
                                            FontAwesomeIcons.Solid.CircleChevronDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                        )
                                }
                            }.onError {
                                FAIcon(
                                    FontAwesomeIcons.Solid.CircleXmark,
                                    contentDescription = null,
                                    tint = FluentTheme.colors.system.critical,
                                    modifier = Modifier.size(24.dp),
                                )
                            }.onLoading {
                                ProgressRing(
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                    },
                )

                state.checkState.onSuccess { rssState ->
                    when (rssState) {
                        is CheckRssSourcePresenter.State.RssState.RssFeed -> {
                            TextField(
                                state = state.title,
                                header = { Text(text = stringResource(Res.string.rss_sources_title_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                keyboardOptions =
                                    KeyboardOptions(
                                        imeAction = ImeAction.Done,
                                        autoCorrectEnabled = false,
                                    ),
                            )
                        }

                        CheckRssSourcePresenter.State.RssState.RssHub -> {
                            TextField(
                                state = state.title,
                                header = { Text(text = stringResource(Res.string.rss_sources_title_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                lineLimits = TextFieldLineLimits.SingleLine,
                            )
                            TextField(
                                state = state.rssHubHostText,
                                header = { Text(text = stringResource(Res.string.rss_sources_rss_hub_host_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                trailing = {
                                    state.inputState.onSuccess {
                                        if (it is EditRssSourcePresenter.State.RssInputState.RssHub) {
                                            it.checkState
                                                .onSuccess {
                                                    when (it) {
                                                        is CheckRssSourcePresenter.State.RssState.RssFeed ->
                                                            FAIcon(
                                                                FontAwesomeIcons.Solid.CircleCheck,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(24.dp),
                                                            )

                                                        CheckRssSourcePresenter.State.RssState.RssHub ->
                                                            FAIcon(
                                                                FontAwesomeIcons.Solid.CircleChevronDown,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(24.dp),
                                                            )

                                                        is CheckRssSourcePresenter.State.RssState.RssSources ->
                                                            FAIcon(
                                                                FontAwesomeIcons.Solid.CircleChevronDown,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(24.dp),
                                                            )
                                                    }
                                                }.onError {
                                                    FAIcon(
                                                        FontAwesomeIcons.Solid.CircleXmark,
                                                        contentDescription = null,
                                                        tint = FluentTheme.colors.system.critical,
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }.onLoading {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }
                                        } else {
                                            FAIcon(
                                                FontAwesomeIcons.Solid.CircleXmark,
                                                contentDescription = null,
                                                tint = FluentTheme.colors.system.critical,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                },
                                placeholder = {
                                    Text(text = stringResource(Res.string.rss_sources_rss_hub_host_hint))
                                },
                            )
                            publicRssHubServer.forEachIndexed { index, it ->
                                CardExpanderItem(
                                    heading = {
                                        Text(text = it)
                                    },
                                    onClick = {
                                        state.rssHubHostText.edit {
                                            delete(0, state.rssHubHostText.text.length)
                                            append(it)
                                        }
                                    },
                                )
                            }
                        }

                        is CheckRssSourcePresenter.State.RssState.RssSources -> {
                            Text(stringResource(Res.string.rss_sources_discovered_rss_sources))
                            rssState.sources.forEachIndexed { index, source ->
                                CardExpanderItem(
                                    icon = {
                                        NetworkImage(
                                            source.favIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    },
                                    heading = {
                                        Text(text = source.title.orEmpty())
                                    },
                                    caption = {
                                        Text(text = source.url)
                                    },
                                    trailing = {
                                        CheckBox(
                                            checked = state.selectedSource.contains(source),
                                            onCheckStateChange = {
                                                state.selectSource(source)
                                            },
                                        )
                                    },
                                    modifier =
                                        Modifier
                                            .listCard(
                                                index = index,
                                                totalCount = rssState.sources.size,
                                            ).clickable {
                                                state.selectSource(source)
                                            },
                                )
                            }
                        }
                    }
                }
                state.inputState.onSuccess { inputState ->
                    Row(
                        horizontalArrangement =
                            androidx.compose.foundation.layout.Arrangement
                                .spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    state.setPinnedInTabs(!state.pinnedInTabs)
                                },
                    ) {
                        CheckBox(
                            checked = state.pinnedInTabs,
                            onCheckStateChange = { state.setPinnedInTabs(it) },
                        )
                        Text(
                            text = stringResource(Res.string.rss_sources_pinned_in_tabs),
                        )
                    }

                    if (inputState is EditRssSourcePresenter.State.RssInputState.RssHub) {
                        LaunchedEffect(state.rssHubHostText.text) {
                            inputState.checkWithServer(state.rssHubHostText.text.toString())
                        }
                        inputState.checkState.onSuccess {
                            if (it is CheckRssSourcePresenter.State.RssState.RssFeed) {
                                DisposableEffect(it) {
                                    if (state.title.text.isEmpty()) {
                                        state.title.edit {
                                            append(it.title)
                                        }
                                    }
                                    onDispose {
                                        if (state.title.text == it) {
                                            state.title.edit {
                                                delete(0, it.title.length)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun presenter(
    id: Int?,
    initialUrl: String?,
    settingsRepository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val tabSettings by settingsRepository.tabSettings.collectAsUiState()
    val state = remember(id) { EditRssSourcePresenter(id) }.invoke()
    val titleText = rememberTextFieldState()
    val urlText = rememberTextFieldState(initialText = initialUrl.orEmpty())
    val rssHubHostText = rememberTextFieldState()
    val selectedSource = remember { mutableStateListOf<UiRssSource>() }
    var pinnedInTabs by remember { mutableStateOf(false) }
    state.data.onSuccess {
        LaunchedEffect(Unit) {
            titleText.edit {
                append(it.title)
            }
            urlText.edit {
                append(it.url)
            }
        }
    }

    val currentTabs =
        remember(state.data, tabSettings) {
            state.data.flatMap { rssSource ->
                tabSettings.map {
                    it.mainTabs
                        .filterIsInstance<RssTimelineTabItem>()
                        .filter { tab -> tab.feedUrl == rssSource.url }
                        .toImmutableList()
                }
            }
        }
    currentTabs.onSuccess {
        LaunchedEffect(it) {
            pinnedInTabs = it.isNotEmpty()
        }
    }
    state.checkState.onSuccess {
        if (it is CheckRssSourcePresenter.State.RssState.RssFeed) {
            DisposableEffect(it) {
                if (titleText.text.isEmpty()) {
                    titleText.edit {
                        append(it.title)
                    }
                }
                onDispose {
                    if (titleText.text == it) {
                        titleText.edit {
                            delete(0, it.title.length)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(urlText.text) {
        state.checkUrl(urlText.text.toString())
    }
    object : EditRssSourcePresenter.State by state {
        val pinnedInTabs = pinnedInTabs
        val selectedSource = selectedSource
        val rssHubHostText = rssHubHostText

        fun setPinnedInTabs(value: Boolean) {
            pinnedInTabs = value
        }

        fun selectSource(source: UiRssSource) {
            if (selectedSource.contains(source)) {
                selectedSource.remove(source)
            } else {
                selectedSource.add(source)
            }
        }

        fun save(sources: List<Pair<String, String?>>) {
            appScope.launch {
                settingsRepository.updateTabSettings {
                    if (pinnedInTabs) {
                        copy(
                            mainTabs =
                                mainTabs
                                    .filterNot { tab ->
                                        tab is RssTimelineTabItem && sources.any { it.first == tab.feedUrl }
                                    } +
                                    sources.map { source ->
                                        RssTimelineTabItem(
                                            feedUrl = source.first,
                                            title = source.second.orEmpty(),
                                        )
                                    },
                        )
                    } else {
                        copy(
                            mainTabs =
                                mainTabs
                                    .filterNot { tab ->
                                        tab is RssTimelineTabItem && sources.any { it.first == tab.feedUrl }
                                    },
                        )
                    }
                }
            }
        }

        val title = titleText
        val url = urlText
    }
}

private val publicRssHubServer =
    listOf(
        "https://rsshub.rssforever.com",
        "https://hub.slarker.me",
        "https://rsshub.pseudoyu.com",
    )
