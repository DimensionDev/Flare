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
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.data.repository.cache.misskeyUserDataPresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.flatMap
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.toUi

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MisskeyProfileHeader(
    user: UiUser.Misskey,
    relationState: UiState<UiRelation>,
    modifier: Modifier = Modifier
) {
    CommonProfileHeader(
        bannerUrl = user.bannerUrl,
        avatarUrl = user.avatarUrl,
        displayName = user.nameElement,
        handle = user.handle,
        handleTrailing = {
//            if (user.locked) {
//                Icon(
//                    imageVector = Icons.Default.Lock,
//                    contentDescription = null,
//                    modifier = Modifier
//                        .size(12.dp)
//                        .alpha(MediumAlpha)
//                )
//            }
        },
        headerTrailing = {
            when (relationState) {
                is UiState.Error -> Unit
                is UiState.Loading -> {
                    FilledTonalButton(
                        onClick = { /*TODO*/ },
                        modifier = Modifier.placeholder(
                            true,
                            shape = ButtonDefaults.filledTonalShape
                        )
                    ) {
                        Text(text = stringResource(R.string.profile_header_button_follow))
                    }
                }

                is UiState.Success -> {
                    if (relationState.data is UiRelation.Mastodon) {
                        FilledTonalButton(
                            onClick = { /*TODO*/ }
                        ) {
                            Text(
                                text = stringResource(
                                    when {
                                        relationState.data.following -> R.string.profile_header_button_following
                                        relationState.data.requested -> R.string.profile_header_button_requested
                                        else -> R.string.profile_header_button_follow
                                    }
                                )
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                user.descriptionElement?.let {
                    HtmlText(
                        element = it,
                        layoutDirection = user.descriptionDirection ?: LocalLayoutDirection.current
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.profile_misskey_header_status_count,
                            user.matrices.statusesCountHumanized
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(
                            R.string.profile_header_following_count,
                            user.matrices.followsCountHumanized
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(
                            R.string.profile_header_fans_count,
                            user.matrices.fansCountHumanized
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        modifier = modifier
    )
}

@Composable
internal fun misskeyUserRelationPresenter(
    account: UiAccount.Misskey,
    userKey: MicroBlogKey
): UiState<UiRelation> {
    if (account.accountKey == userKey) {
        return UiState.Error(IllegalStateException("Cannot show relation of self"))
    }
    return misskeyUserDataPresenter(account, userKey.id).toUi().flatMap {
        if (it is UiUser.Misskey) {
            UiState.Success(it.relation)
        } else {
            UiState.Error(IllegalStateException("User is not a Misskey user"))
        }
    }
}

