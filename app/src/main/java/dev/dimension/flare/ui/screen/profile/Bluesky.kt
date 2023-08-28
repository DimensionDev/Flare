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
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlueskyProfileHeader(
    user: UiUser.Bluesky,
    relationState: UiState<UiRelation>,
    modifier: Modifier = Modifier,
) {
    CommonProfileHeader(
        bannerUrl = user.bannerUrl,
        avatarUrl = user.avatarUrl,
        displayName = user.nameElement,
        handle = user.handle,
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
                    if (relationState.data is UiRelation.Bluesky) {
                        FilledTonalButton(
                            onClick = { /*TODO*/ },
                        ) {
                            Text(
                                text = stringResource(
                                    when {
                                        relationState.data.isFollowing -> R.string.profile_header_button_following
                                        else -> R.string.profile_header_button_follow
                                    },
                                ),
                            )
                        }
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
                    HtmlText(
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
                            R.string.profile_header_toots_count,
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
