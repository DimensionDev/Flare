package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.bottomsheet.spec.DestinationStyleBottomSheet
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@Destination<RootGraph>(
    style = DestinationStyleBottomSheet::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.AltText.ROUTE,
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun AltTextSheetRoute(
    text: String,
    navigator: DestinationsNavigator,
) {
    AltTextSheet(
        text = text,
        onBack = navigator::navigateUp,
    )
}

@Composable
private fun AltTextSheet(
    text: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = screenHorizontalPadding)
                .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.media_alt_text),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = text,
        )
    }
}
