package dev.dimension.flare.ui.screen.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.add_rss_source
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RssListScreen(
    toItem: (UiRssSource) -> Unit,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter { presenter() }

    LazyColumn(
        modifier =
            modifier
                .padding(horizontal = screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = LocalWindowPadding.current,
    ) {
        item {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.fillParentMaxWidth(),
            ) {
                AccentButton(
                    onClick = {
                        onAdd.invoke()
                    },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Plus,
                        contentDescription = stringResource(Res.string.add_rss_source),
                    )
                    Text(
                        text = stringResource(Res.string.add_rss_source),
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(6.dp))
        }

        rssListWithTabs(
            state = state,
            onClicked = toItem,
            onEdit = onEdit,
        )
    }
}

@Composable
private fun presenter() =
    run {
        remember { RssListWithTabsPresenter() }.invoke()
    }
