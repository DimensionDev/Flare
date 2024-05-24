package dev.dimension.flare.ui.screen.status

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.VVOStatusDetailPresenter

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Destination<RootGraph>(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
internal fun AnimatedVisibilityScope.VVOStatusRoute(
    statusKey: MicroBlogKey,
    navigator: DestinationsNavigator,
    accountType: AccountType,
    sharedTransitionScope: SharedTransitionScope,
) = with(sharedTransitionScope) {
    VVOStatusScreen(
        statusKey,
        onBack = navigator::navigateUp,
        accountType = accountType,
    )
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VVOStatusScreen(
    statusKey: MicroBlogKey,
    onBack: () -> Unit,
    accountType: AccountType,
) {
    val state by producePresenter(key = "status_detail_${statusKey}_$accountType") {
        presenter(
            statusKey = statusKey,
            accountType = accountType,
        )
    }
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.status_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) {
    }
}

@Composable
private fun presenter(
    statusKey: MicroBlogKey,
    accountType: AccountType,
) = run {
    remember {
        VVOStatusDetailPresenter(
            accountType = accountType,
            statusKey = statusKey,
        )
    }.invoke()
}
