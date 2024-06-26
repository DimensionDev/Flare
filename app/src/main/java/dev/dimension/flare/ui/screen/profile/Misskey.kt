package dev.dimension.flare.ui.screen.profile

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.material3.placeholder
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.component.MatricesDisplay
import dev.dimension.flare.ui.component.UserFields
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.descriptionDirection
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.persistentMapOf

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MisskeyProfileHeader(
    user: UiUser.Misskey,
    relationState: UiState<UiRelation>,
    onFollowClick: (UiRelation.Misskey) -> Unit,
    onAvatarClick: () -> Unit,
    onBannerClick: () -> Unit,
    isMe: UiState<Boolean>,
    menu: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    expandMatrices: Boolean = false,
) {
    CommonProfileHeader(
        userKey = user.userKey,
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
            isMe.onSuccess {
                if (!it) {
                    when (relationState) {
                        is UiState.Error -> Unit
                        is UiState.Loading -> {
                            FilledTonalButton(
                                onClick = {
                                    // No-op
                                },
                                modifier =
                                    Modifier.placeholder(
                                        true,
                                        shape = ButtonDefaults.filledTonalShape,
                                    ),
                            ) {
                                Text(text = stringResource(R.string.profile_header_button_follow))
                            }
                        }

                        is UiState.Success -> {
                            when (val data = relationState.data) {
                                is UiRelation.Misskey -> {
                                    FilledTonalButton(
                                        onClick = {
                                            onFollowClick.invoke(data)
                                        },
                                    ) {
                                        Text(
                                            text =
                                                stringResource(
                                                    when {
                                                        data.blocking -> R.string.profile_header_button_blocked
                                                        data.following -> R.string.profile_header_button_following
                                                        data.hasPendingFollowRequestFromYou -> R.string.profile_header_button_requested
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
                }
            }
            menu.invoke(this)
        },
        content = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                user.descriptionElement?.let {
                    HtmlText(
                        element = it,
                        layoutDirection = user.descriptionDirection,
                    )
                }
                UserFields(
                    fields = user.fieldsParsed,
                )
                MatricesDisplay(
                    matrices =
                        remember(user.matrices) {
                            persistentMapOf(
                                R.string.profile_misskey_header_status_count to user.matrices.statusesCountHumanized,
                                R.string.profile_header_following_count to user.matrices.followsCountHumanized,
                                R.string.profile_header_fans_count to user.matrices.fansCountHumanized,
                            )
                        },
                    expanded = expandMatrices,
                )
            }
        },
        modifier = modifier,
        onAvatarClick = onAvatarClick,
        onBannerClick = onBannerClick,
    )
}
