package dev.dimension.flare.ui.screen.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssSourcesScreen(
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onClicked: (UiRssSource) -> Unit,
    onBack: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { presenter() }
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.rss_sources_title))
                },
                actions = {
                    IconButton(
                        onClick = {
                            onAdd.invoke()
                        },
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(R.string.add_rss_source),
                        )
                    }
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = contentPadding,
        ) {
            rssListWithTabs(
                state = state,
                onClicked = onClicked,
                onEdit = onEdit,
            )
        }
    }
}

@Composable
private fun presenter() =
    run {
        remember { RssListWithTabsPresenter() }.invoke()
    }
