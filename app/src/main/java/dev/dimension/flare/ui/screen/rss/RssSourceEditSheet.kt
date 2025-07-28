package dev.dimension.flare.ui.screen.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleCheck
import compose.icons.fontawesomeicons.solid.CircleChevronDown
import compose.icons.fontawesomeicons.solid.CircleXmark
import dev.dimension.flare.R
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.FAIcon
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
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@Composable
internal fun RssSourceEditSheet(
    onDismissRequest: () -> Unit,
    id: Int?,
    initialUrl: String? = null,
) {
    val state by producePresenter("rss_source_edit_${id}_$initialUrl") { presenter(id, initialUrl) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = screenHorizontalPadding),
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(8.dp),
    ) {
        if (id == null) {
            Text(
                text = stringResource(id = R.string.add_rss_source),
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            Text(
                text = stringResource(id = R.string.edit_rss_source),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        OutlinedTextField(
            state = state.url,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            label = { Text(text = stringResource(id = R.string.rss_sources_url_label)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            trailingIcon = {
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
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }.onLoading {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                        )
                    }
            },
        )

        state.checkState.onSuccess { rssState ->
            when (rssState) {
                is CheckRssSourcePresenter.State.RssState.RssFeed -> {
                    OutlinedTextField(
                        state = state.title,
                        label = { Text(text = stringResource(id = R.string.rss_sources_title_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }

                CheckRssSourcePresenter.State.RssState.RssHub -> {
                    OutlinedTextField(
                        state = state.title,
                        label = { Text(text = stringResource(id = R.string.rss_sources_title_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    OutlinedTextField(
                        state = state.rssHubHostText,
                        label = { Text(text = stringResource(id = R.string.rss_sources_rss_hub_host_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        trailingIcon = {
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
                                                tint = MaterialTheme.colorScheme.error,
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
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        },
                        supportingText = {
                            Text(text = stringResource(id = R.string.rss_sources_rss_hub_host_hint))
                        },
                    )
                    publicRssHubServer.forEach {
                        ListItem(
                            headlineContent = {
                                Text(text = it)
                            },
                            modifier =
                                Modifier.clickable {
                                    state.rssHubHostText.edit {
                                        delete(0, state.rssHubHostText.text.length)
                                        append(it)
                                    }
                                },
                        )
                    }
                }

                is CheckRssSourcePresenter.State.RssState.RssSources -> {
                    Text(stringResource(R.string.rss_sources_discovered_rss_sources))
                    rssState.sources.forEach { source ->
                        ListItem(
                            headlineContent = {
                                Text(text = source.title.orEmpty())
                            },
                            supportingContent = {
                                Text(text = source.url)
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = state.selectedSource.contains(source),
                                    onCheckedChange = {
                                        state.selectSource(source)
                                    },
                                )
                            },
                            modifier =
                                Modifier.clickable {
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
                Checkbox(
                    checked = state.pinnedInTabs,
                    onCheckedChange = { state.setPinnedInTabs(it) },
                )
                Text(
                    text = stringResource(id = R.string.rss_sources_pinned_in_tabs),
                )
            }
            when (inputState) {
                is EditRssSourcePresenter.State.RssInputState.RssFeed -> {
                    Button(
                        onClick = {
                            inputState.save(
                                title = state.title.text.toString(),
                            )
                            state.save()
                            onDismissRequest.invoke()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(id = android.R.string.ok))
                    }
                }

                is EditRssSourcePresenter.State.RssInputState.RssHub -> {
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
                    Button(
                        enabled =
                            inputState.checkState.isSuccess &&
                                inputState.checkState.takeSuccess() is CheckRssSourcePresenter.State.RssState.RssFeed,
                        onClick = {
                            inputState.save(
                                title = state.title.text.toString(),
                            )
                            state.save()
                            onDismissRequest.invoke()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(id = android.R.string.ok))
                    }
                }

                is EditRssSourcePresenter.State.RssInputState.RssSources -> {
                    Button(
                        enabled = state.selectedSource.isNotEmpty(),
                        onClick = {
                            inputState.save(
                                sources = state.selectedSource,
                            )
                            state.save()
                            onDismissRequest.invoke()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(id = android.R.string.ok))
                    }
                }
            }
        }

        TextButton(
            onClick = onDismissRequest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(id = android.R.string.cancel),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
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

        fun save() {
            appScope.launch {
                settingsRepository.updateTabSettings {
                    if (pinnedInTabs) {
                        copy(
                            mainTabs =
                                mainTabs
                                    .filterNot { tab ->
                                        tab is RssTimelineTabItem && tab.feedUrl == urlText.text.toString()
                                    }.plus(
                                        RssTimelineTabItem(
                                            feedUrl = urlText.text.toString(),
                                            title = titleText.text.toString(),
                                        ),
                                    ).toImmutableList(),
                        )
                    } else {
                        copy(
                            mainTabs =
                                mainTabs
                                    .filterNot { tab ->
                                        tab is RssTimelineTabItem && tab.feedUrl == urlText.text.toString()
                                    }.toImmutableList(),
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
