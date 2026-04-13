package dev.dimension.flare.ui.screen.rss

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleCheck
import compose.icons.fontawesomeicons.solid.CircleChevronDown
import compose.icons.fontawesomeicons.solid.CircleXmark
import dev.dimension.flare.R
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.model.SubscriptionTimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkImage
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
import dev.dimension.flare.ui.theme.segmentedShapes2
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@Composable
internal fun RssSourceEditSheet(
    onDismissRequest: () -> Unit,
    onImportOPML: (String) -> Unit,
    id: Int?,
    initialUrl: String? = null,
) {
    val state by producePresenter("rss_source_edit_${id}_$initialUrl") { presenter(id, initialUrl) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let {
                onDismissRequest.invoke()
                onImportOPML.invoke(it.toString())
            }
        }
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            supportingText = {
                Text(text = stringResource(id = R.string.subscription_url_hint))
            },
            trailingIcon = {
                state.checkState
                    .onSuccess {
                        when (it) {
                            is CheckRssSourcePresenter.State.RssState.RssFeed -> {
                                FAIcon(
                                    FontAwesomeIcons.Solid.CircleCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            CheckRssSourcePresenter.State.RssState.RssHub -> {
                                FAIcon(
                                    FontAwesomeIcons.Solid.CircleChevronDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            is CheckRssSourcePresenter.State.RssState.RssSources -> {
                                FAIcon(
                                    FontAwesomeIcons.Solid.CircleChevronDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            is CheckRssSourcePresenter.State.RssState.MastodonInstance -> {
                                FAIcon(
                                    FontAwesomeIcons.Solid.CircleCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
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

        AnimatedVisibility(state.url.text.isEmpty() && initialUrl == null) {
            TextButton(
                onClick = {
                    launcher.launch(arrayOf("*/*"))
                },
                modifier =
                    Modifier
                        .fillMaxWidth(),
            ) {
                Text(stringResource(R.string.opml_import))
            }
        }

        state.checkState.onSuccess { rssState ->
            when (rssState) {
                is CheckRssSourcePresenter.State.RssState.RssFeed -> {
                    OutlinedTextField(
                        state = state.title,
                        label = { Text(text = stringResource(id = R.string.rss_sources_title_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Uri,
                            ),
                        leadingIcon =
                            rssState.icon?.let {
                                {
                                    NetworkImage(
                                        it,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            },
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
                                                is CheckRssSourcePresenter.State.RssState.RssFeed -> {
                                                    FAIcon(
                                                        FontAwesomeIcons.Solid.CircleCheck,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }

                                                CheckRssSourcePresenter.State.RssState.RssHub -> {
                                                    FAIcon(
                                                        FontAwesomeIcons.Solid.CircleChevronDown,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }

                                                is CheckRssSourcePresenter.State.RssState.RssSources -> {
                                                    FAIcon(
                                                        FontAwesomeIcons.Solid.CircleChevronDown,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }

                                                is CheckRssSourcePresenter.State.RssState.MastodonInstance -> {
                                                    FAIcon(
                                                        FontAwesomeIcons.Solid.CircleCheck,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        publicRssHubServer.forEachIndexed { index, it ->
                            SegmentedListItem(
                                selected = false,
                                onClick = {
                                    state.rssHubHostText.edit {
                                        delete(0, state.rssHubHostText.text.length)
                                        append(it)
                                    }
                                },
                                shapes =
                                    ListItemDefaults.segmentedShapes2(
                                        index,
                                        publicRssHubServer.size,
                                    ),
                                content = {
                                    Text(text = it)
                                },
                            )
                        }
                    }
                }

                is CheckRssSourcePresenter.State.RssState.RssSources -> {
                    Text(stringResource(R.string.rss_sources_discovered_rss_sources))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        rssState.sources.forEachIndexed { index, source ->
                            SegmentedListItem(
                                selected = state.selectedSource.contains(source),
                                onClick = {
                                    state.selectSource(source)
                                },
                                shapes =
                                    ListItemDefaults.segmentedShapes2(
                                        index,
                                        rssState.sources.size,
                                    ),
                                leadingContent = {
                                    NetworkImage(
                                        source.favIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                content = {
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
                            )
                        }
                    }
                }

                is CheckRssSourcePresenter.State.RssState.MastodonInstance -> {
                    Text(
                        text = stringResource(id = R.string.mastodon_available_timelines),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        rssState.availableTimelines.forEachIndexed { index, type ->
                            val label =
                                when (type) {
                                    SubscriptionType.MASTODON_TRENDS -> stringResource(id = R.string.mastodon_trending_statuses)
                                    SubscriptionType.MASTODON_PUBLIC -> stringResource(id = R.string.mastodon_federated_timeline)
                                    SubscriptionType.MASTODON_LOCAL -> stringResource(id = R.string.mastodon_local_timeline)
                                    else -> type.name
                                }
                            SegmentedListItem(
                                selected = state.selectedMastodonTypes.contains(type),
                                onClick = {
                                    state.toggleMastodonType(type)
                                },
                                shapes =
                                    ListItemDefaults.segmentedShapes2(
                                        index,
                                        rssState.availableTimelines.size,
                                    ),
                                leadingContent =
                                    rssState.icon?.let {
                                        {
                                            NetworkImage(
                                                it,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    },
                                content = {
                                    Text(text = label)
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = state.selectedMastodonTypes.contains(type),
                                        onCheckedChange = {
                                            state.toggleMastodonType(type)
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        state.inputState.onSuccess { inputState ->
            if (inputState !is EditRssSourcePresenter.State.RssInputState.MastodonInstance) {
                val openInBrowserString = stringResource(id = R.string.rss_sources_open_in_browser)
                val openInAppString = stringResource(id = R.string.rss_sources_open_in_app)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.openInBrowser,
                        onClick = { state.setOpenInBrowser(true) },
                        label = {
                            Text(openInBrowserString)
                        },
                    )
                    FilterChip(
                        selected = !state.openInBrowser,
                        onClick = { state.setOpenInBrowser(false) },
                        label = {
                            Text(openInAppString)
                        },
                    )
                }
            }
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
                            val data =
                                inputState.save(
                                    title = state.title.text.toString(),
                                    openInBrowser = state.openInBrowser,
                                )
                            state.save(
                                sources = listOf(data),
                            )
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
                            val data =
                                inputState.save(
                                    title = state.title.text.toString(),
                                    openInBrowser = state.openInBrowser,
                                )
                            state.save(
                                sources =
                                    listOf(data),
                            )
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
                                openInBrowser = state.openInBrowser,
                            )
                            state.save(
                                sources = state.selectedSource,
                            )
                            onDismissRequest.invoke()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(id = android.R.string.ok))
                    }
                }

                is EditRssSourcePresenter.State.RssInputState.MastodonInstance -> {
                    val typeNames =
                        mapOf(
                            SubscriptionType.MASTODON_TRENDS to stringResource(id = R.string.mastodon_trending_statuses),
                            SubscriptionType.MASTODON_PUBLIC to stringResource(id = R.string.mastodon_federated_timeline),
                            SubscriptionType.MASTODON_LOCAL to stringResource(id = R.string.mastodon_local_timeline),
                        )
                    Button(
                        enabled = state.selectedMastodonTypes.isNotEmpty(),
                        onClick = {
                            val savedSources = inputState.save(state.selectedMastodonTypes, typeNames)
                            state.save(sources = savedSources)
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

@OptIn(FlowPreview::class)
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
    val selectedMastodonTypes = remember { mutableStateListOf<SubscriptionType>() }
    var pinnedInTabs by remember { mutableStateOf(false) }
    var openInBrowser by remember { mutableStateOf(false) }
    state.data.onSuccess {
        LaunchedEffect(Unit) {
            titleText.edit {
                append(it.title)
            }
            urlText.edit {
                append(it.url)
            }
            openInBrowser = it.openInBrowser
        }
    }

    val currentTabs =
        remember(state.data, tabSettings) {
            state.data.flatMap { rssSource ->
                tabSettings.map {
                    val rssMatches =
                        it.mainTabs
                            .filterIsInstance<RssTimelineTabItem>()
                            .filter { tab -> tab.feedUrl == rssSource.url }
                    val subscriptionMatches =
                        it.mainTabs
                            .filterIsInstance<SubscriptionTimelineTabItem>()
                            .filter { tab ->
                                tab.subscriptionUrl == rssSource.url &&
                                    tab.subscriptionType == rssSource.type
                            }
                    (rssMatches + subscriptionMatches).toImmutableList()
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
    LaunchedEffect(Unit) {
        snapshotFlow { urlText.text }
            .debounce(666)
            .collect {
                state.checkUrl(it.toString())
            }
    }
    object : EditRssSourcePresenter.State by state {
        val pinnedInTabs = pinnedInTabs
        val selectedSource = selectedSource
        val selectedMastodonTypes: List<SubscriptionType> = selectedMastodonTypes
        val rssHubHostText = rssHubHostText
        val openInBrowser = openInBrowser

        fun setOpenInBrowser(value: Boolean) {
            openInBrowser = value
        }

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

        fun toggleMastodonType(type: SubscriptionType) {
            if (selectedMastodonTypes.contains(type)) {
                selectedMastodonTypes.remove(type)
            } else {
                selectedMastodonTypes.add(type)
            }
        }

        fun save(sources: List<UiRssSource>) {
            appScope.launch {
                settingsRepository.updateTabSettings {
                    if (pinnedInTabs) {
                        copy(
                            mainTabs =
                                mainTabs
                                    .filterNot { tab ->
                                        (tab is RssTimelineTabItem && sources.any { it.url == tab.feedUrl }) ||
                                            (
                                                tab is SubscriptionTimelineTabItem &&
                                                    sources.any {
                                                        it.url == tab.subscriptionUrl && it.type == tab.subscriptionType
                                                    }
                                            )
                                    } +
                                    sources.map { source ->
                                        if (source.type == SubscriptionType.RSS) {
                                            RssTimelineTabItem(source)
                                        } else {
                                            SubscriptionTimelineTabItem(source)
                                        }
                                    },
                        )
                    } else {
                        copy(
                            mainTabs =
                                mainTabs
                                    .filterNot { tab ->
                                        (tab is RssTimelineTabItem && sources.any { it.url == tab.feedUrl }) ||
                                            (
                                                tab is SubscriptionTimelineTabItem &&
                                                    sources.any {
                                                        it.url == tab.subscriptionUrl && it.type == tab.subscriptionType
                                                    }
                                            )
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
