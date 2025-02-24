package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.Message
import compose.icons.fontawesomeicons.solid.UserSlash
import compose.icons.fontawesomeicons.solid.VolumeXmark
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenu
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenuItem
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.profile.ProfileState
import dev.dimension.flare.ui.theme.PlatformTheme
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import kotlin.collections.component1
import kotlin.collections.component2


@Composable
public fun ProfileMenu(
    profileState: ProfileState,
    accountsState: UiState<Map<PlatformType, ImmutableList<UiState<UiProfile>>>>,
    setShowMoreMenus: (Boolean) -> Unit,
    showMoreMenus: Boolean,
    toEditAccountList: () -> Unit,
    toSearchUserUsingAccount: (String, MicroBlogKey) -> Unit,
    toStartMessage: (MicroBlogKey) -> Unit,
) {
    profileState.isMe.onSuccess { isMe ->
        if (!isMe) {
            profileState.userState.onSuccess { user ->
                PlatformIconButton(onClick = {
                    setShowMoreMenus(true)
                }) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                        contentDescription = stringResource(Res.string.more),
                    )
                }
                PlatformDropdownMenu(
                    expanded = showMoreMenus,
                    onDismissRequest = { setShowMoreMenus(false) },
                ) {
                    profileState.relationState.onSuccess { relation ->
                        if (!profileState.isGuestMode && relation.following) {
                            profileState.isListDataSource.onSuccess { isListDataSource ->
                                if (isListDataSource) {
                                    PlatformDropdownMenuItem(
                                        text = {
                                            PlatformText(
                                                text =
                                                stringResource(
                                                    Res.string.user_follow_edit_list,
                                                ),
                                            )
                                        },
                                        onClick = {
                                            setShowMoreMenus(false)
                                            toEditAccountList.invoke()
                                        },
                                        leadingIcon = {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.List,
                                                contentDescription =
                                                stringResource(
                                                    Res.string.user_follow_edit_list,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        profileState.canSendMessage.onSuccess {
                            if (it) {
                                PlatformDropdownMenuItem(
                                    text = {
                                        PlatformText(
                                            text = stringResource(Res.string.user_send_message),
                                        )
                                    },
                                    onClick = {
                                        setShowMoreMenus(false)
                                        toStartMessage.invoke(user.key)
                                    },
                                    leadingIcon = {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Message,
                                            contentDescription =
                                            stringResource(
                                                Res.string.user_send_message,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        accountsState.onSuccess { accounts ->
                            profileState.myAccountKey.onSuccess { myKey ->
                                if (accounts.size > 1) {
                                    accounts.forEach { (_, value) ->
                                        value.forEach { account ->
                                            account.onSuccess { accountData ->
                                                if (accountData.key != user.key &&
                                                    accountData.key != myKey &&
                                                    accountData.platformType != user.platformType
                                                ) {
                                                    PlatformDropdownMenuItem(
                                                        text = {
                                                            PlatformText(
                                                                text =
                                                                if (value.size == 1) {
                                                                    stringResource(
                                                                        Res.string.profile_search_user_using_account_compat,
                                                                        accountData.platformType.name,
                                                                    )
                                                                } else {
                                                                    stringResource(
                                                                        Res.string.profile_search_user_using_account,
                                                                        user.handleWithoutAtAndHost,
                                                                        accountData.platformType.name,
                                                                        accountData.handleWithoutAt,
                                                                    )
                                                                },
                                                            )
                                                        },
                                                        onClick = {
                                                            setShowMoreMenus(false)
                                                            val actualHandle =
                                                                if (accountData.platformType == PlatformType.Bluesky) {
                                                                    user.handleWithoutAtAndHost
                                                                        .replace("_", "")
                                                                } else {
                                                                    user.handleWithoutAtAndHost
                                                                }
                                                            toSearchUserUsingAccount(
                                                                actualHandle,
                                                                accountData.key,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                        profileState.actions.onSuccess { actions ->
                            for (i in 0..<actions.size) {
                                val action = actions[i]
                                PlatformDropdownMenuItem(
                                    leadingIcon = {
                                        FAIcon(
                                            imageVector =
                                            when (action) {
                                                is ProfileAction.Block ->
                                                    FontAwesomeIcons.Solid.UserSlash

                                                is ProfileAction.Mute ->
                                                    FontAwesomeIcons.Solid.VolumeXmark
                                            },
                                            contentDescription = null,
                                        )
                                    },
                                    text = {
                                        val text =
                                            when (action) {
                                                is ProfileAction.Block ->
                                                    if (action.relationState(relation)) {
                                                        stringResource(
                                                            Res.string.user_unblock,
                                                            user.handle,
                                                        )
                                                    } else {
                                                        stringResource(
                                                            Res.string.user_block,
                                                            user.handle,
                                                        )
                                                    }

                                                is ProfileAction.Mute ->
                                                    if (action.relationState(relation)) {
                                                        stringResource(
                                                            Res.string.user_unmute,
                                                            user.handle,
                                                        )
                                                    } else {
                                                        stringResource(
                                                            Res.string.user_mute,
                                                            user.handle,
                                                        )
                                                    }
                                            }
                                        PlatformText(text = text)
                                    },
                                    onClick = {
                                        setShowMoreMenus(false)
                                        profileState.onProfileActionClick(
                                            userKey = user.key,
                                            relation = relation,
                                            action = action,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    PlatformDropdownMenuItem(
                        text = {
                            PlatformText(
                                text =
                                stringResource(
                                    Res.string.user_report,
                                    user.handle,
                                ),
                                color = PlatformTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.CircleExclamation,
                                contentDescription =
                                stringResource(
                                    Res.string.user_report,
                                    user.handle,
                                ),
                                tint = PlatformTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            setShowMoreMenus(false)
                            profileState.report(user.key)
                        },
                    )
                }
            }
        }
    }
}