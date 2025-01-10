package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.bottomsheet.spec.DestinationStyleBottomSheet
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.MisskeyReactionPresenter
import moe.tlaster.precompose.molecule.producePresenter

@Destination<RootGraph>(
    style = DestinationStyleBottomSheet::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.Misskey.AddReaction.ROUTE,
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun MisskeyReactionRoute(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey,
    navigator: DestinationsNavigator,
) {
    MisskeyReactionSheet(
        statusKey = statusKey,
        onBack = navigator::navigateUp,
        accountType = AccountType.Specific(accountKey),
    )
}

@Composable
private fun MisskeyReactionSheet(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter("MisskeyReactionSheet_${accountType}_$statusKey") {
        misskeyReactionPresenter(
            statusKey = statusKey,
            accountType = accountType,
        )
    }

    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(48.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        state.emojis.onSuccess {
            items(it.size) { index ->
                val emoji = it[index]
                NetworkImage(
                    model = emoji.url,
                    contentDescription = emoji.shortcode,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clickable {
                                state.select(emoji)
                                onBack.invoke()
                            },
                )
            }
        }
    }
}

@Composable
private fun misskeyReactionPresenter(
    statusKey: MicroBlogKey,
    accountType: AccountType,
) = run {
    remember(statusKey, accountType) {
        MisskeyReactionPresenter(
            accountType = accountType,
            statusKey = statusKey,
        )
    }.invoke()
}
