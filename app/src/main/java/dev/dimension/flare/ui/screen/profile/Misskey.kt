package dev.dimension.flare.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MisskeyProfileHeader(
    user: UiUser.Misskey,
    relationState: UiState<UiRelation>,
    modifier: Modifier = Modifier,
) {
    CommonProfileHeader(
        bannerUrl = user.bannerUrl,
        avatarUrl = user.avatarUrl,
        displayName = user.nameElement,
        handle = user.handle,
//        handleTrailing = {
//            if (user.isBot) {
//                Icon(
//                    imageVector = Icons.Default.Lock,
//                    contentDescription = null,
//                    modifier = Modifier
//                        .size(12.dp)
//                        .alpha(MediumAlpha)
//                )
//            }
//        },
        headerTrailing = {
            when (relationState) {
                is UiState.Error -> Unit
                is UiState.Loading -> {
                    FilledTonalButton(
                        onClick = { /*TODO*/ },
                        modifier = Modifier.placeholder(
                            true,
                            shape = ButtonDefaults.filledTonalShape,
                        ),
                    ) {
                        Text(text = stringResource(R.string.profile_header_button_follow))
                    }
                }

                is UiState.Success -> {
                    when (val data = relationState.data) {
                        is UiRelation.Mastodon -> {
                            FilledTonalButton(
                                onClick = { /*TODO*/ },
                            ) {
                                Text(
                                    text = stringResource(
                                        when {
                                            data.following -> R.string.profile_header_button_following
                                            data.requested -> R.string.profile_header_button_requested
                                            else -> R.string.profile_header_button_follow
                                        },
                                    ),
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                user.descriptionElement?.let {
                    HtmlText2(
                        element = it,
                        layoutDirection = user.descriptionDirection ?: LocalLayoutDirection.current,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.profile_misskey_header_status_count,
                            user.matrices.statusesCountHumanized,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.profile_header_following_count,
                            user.matrices.followsCountHumanized,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.profile_header_fans_count,
                            user.matrices.fansCountHumanized,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        modifier = modifier,
    )
}
