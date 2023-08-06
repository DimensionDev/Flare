package dev.dimension.flare.ui.screen.profile

import androidx.compose.runtime.Composable
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER


@Composable
@Destination(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER"
        )
    ]
)
fun MisskeyProfileRoute(
    userName: String,
    host: String?,
) {

}