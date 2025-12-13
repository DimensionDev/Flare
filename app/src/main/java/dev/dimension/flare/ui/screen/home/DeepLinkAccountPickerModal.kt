package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Globe
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.settings.AccountItem
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun DeepLinkAccountPickerModal(
    originalUrl: String,
    data: ImmutableMap<MicroBlogKey, Route>,
    onNavigate: (Route) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val accounts by producePresenter { presenter(data) }
    LazyColumn(
        contentPadding =
            PaddingValues(
                horizontal = screenHorizontalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        stickyHeader {
            Text(stringResource(R.string.deeplink_account_selection_message))
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        itemsIndexed(accounts) { index, (userState, route) ->
            AccountItem(
                userState.user,
                onClick = {
                    onDismissRequest()
                    onNavigate(route)
                },
                toLogin = {},
                modifier =
                    Modifier
                        .listCard(
                            index = index,
                            totalCount = accounts.size + 1,
                        ),
            )
        }
        item {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.deeplink_account_selection_browser))
                },
                leadingContent = {
                    FAIcon(
                        FontAwesomeIcons.Solid.Globe,
                        contentDescription = stringResource(R.string.deeplink_account_selection_browser),
                    )
                },
                modifier =
                    Modifier
                        .listCard(
                            index = accounts.size,
                            totalCount = accounts.size + 1,
                        ).clickable {
                            uriHandler.openUri(originalUrl)
                            onDismissRequest.invoke()
                        },
            )
        }
        item {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun presenter(data: ImmutableMap<MicroBlogKey, Route>) =
    run {
        remember(data) {
            data.map {
                UserPresenter(AccountType.Specific(it.key), null) to it.value
            }
        }.map {
            it.first.invoke() to it.second
        }.toImmutableList()
    }
