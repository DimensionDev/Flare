package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.At
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.CircleInfo
import compose.icons.fontawesomeicons.solid.Heart
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.QuoteLeft
import compose.icons.fontawesomeicons.solid.Reply
import compose.icons.fontawesomeicons.solid.Retweet
import compose.icons.fontawesomeicons.solid.SquarePollHorizontal
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.UserPlus
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.bluesky_notification_item_starterpack_joined
import dev.dimension.flare.compose.ui.mastodon_item_pinned
import dev.dimension.flare.compose.ui.mastodon_notification_item_favourited_your_status
import dev.dimension.flare.compose.ui.mastodon_notification_item_followed_you
import dev.dimension.flare.compose.ui.mastodon_notification_item_mentioned_you
import dev.dimension.flare.compose.ui.mastodon_notification_item_poll_ended
import dev.dimension.flare.compose.ui.mastodon_notification_item_posted_status
import dev.dimension.flare.compose.ui.mastodon_notification_item_reblogged_your_status
import dev.dimension.flare.compose.ui.mastodon_notification_item_requested_follow
import dev.dimension.flare.compose.ui.mastodon_notification_item_updated_status
import dev.dimension.flare.compose.ui.misskey_achievement_brain_diver_description
import dev.dimension.flare.compose.ui.misskey_achievement_brain_diver_title
import dev.dimension.flare.compose.ui.misskey_achievement_bubble_game_double_exploding_head_description
import dev.dimension.flare.compose.ui.misskey_achievement_bubble_game_double_exploding_head_title
import dev.dimension.flare.compose.ui.misskey_achievement_bubble_game_exploding_head_description
import dev.dimension.flare.compose.ui.misskey_achievement_bubble_game_exploding_head_title
import dev.dimension.flare.compose.ui.misskey_achievement_clicked_click_here_description
import dev.dimension.flare.compose.ui.misskey_achievement_clicked_click_here_title
import dev.dimension.flare.compose.ui.misskey_achievement_client30min_description
import dev.dimension.flare.compose.ui.misskey_achievement_client30min_title
import dev.dimension.flare.compose.ui.misskey_achievement_client60min_description
import dev.dimension.flare.compose.ui.misskey_achievement_client60min_title
import dev.dimension.flare.compose.ui.misskey_achievement_collect_achievements30_description
import dev.dimension.flare.compose.ui.misskey_achievement_collect_achievements30_title
import dev.dimension.flare.compose.ui.misskey_achievement_cookie_clicked_description
import dev.dimension.flare.compose.ui.misskey_achievement_cookie_clicked_title
import dev.dimension.flare.compose.ui.misskey_achievement_drive_folder_circular_reference_description
import dev.dimension.flare.compose.ui.misskey_achievement_drive_folder_circular_reference_title
import dev.dimension.flare.compose.ui.misskey_achievement_followers1000_description
import dev.dimension.flare.compose.ui.misskey_achievement_followers1000_title
import dev.dimension.flare.compose.ui.misskey_achievement_followers100_description
import dev.dimension.flare.compose.ui.misskey_achievement_followers100_title
import dev.dimension.flare.compose.ui.misskey_achievement_followers10_description
import dev.dimension.flare.compose.ui.misskey_achievement_followers10_title
import dev.dimension.flare.compose.ui.misskey_achievement_followers1_description
import dev.dimension.flare.compose.ui.misskey_achievement_followers1_title
import dev.dimension.flare.compose.ui.misskey_achievement_followers300_description
import dev.dimension.flare.compose.ui.misskey_achievement_followers300_title
import dev.dimension.flare.compose.ui.misskey_achievement_followers500_description
import dev.dimension.flare.compose.ui.misskey_achievement_followers500_title
import dev.dimension.flare.compose.ui.misskey_achievement_followers50_description
import dev.dimension.flare.compose.ui.misskey_achievement_followers50_title
import dev.dimension.flare.compose.ui.misskey_achievement_following100_description
import dev.dimension.flare.compose.ui.misskey_achievement_following100_title
import dev.dimension.flare.compose.ui.misskey_achievement_following10_description
import dev.dimension.flare.compose.ui.misskey_achievement_following10_title
import dev.dimension.flare.compose.ui.misskey_achievement_following1_description
import dev.dimension.flare.compose.ui.misskey_achievement_following1_title
import dev.dimension.flare.compose.ui.misskey_achievement_following300_description
import dev.dimension.flare.compose.ui.misskey_achievement_following300_title
import dev.dimension.flare.compose.ui.misskey_achievement_following50_description
import dev.dimension.flare.compose.ui.misskey_achievement_following50_title
import dev.dimension.flare.compose.ui.misskey_achievement_found_treasure_description
import dev.dimension.flare.compose.ui.misskey_achievement_found_treasure_title
import dev.dimension.flare.compose.ui.misskey_achievement_htl20npm_description
import dev.dimension.flare.compose.ui.misskey_achievement_htl20npm_title
import dev.dimension.flare.compose.ui.misskey_achievement_i_love_misskey_description
import dev.dimension.flare.compose.ui.misskey_achievement_i_love_misskey_title
import dev.dimension.flare.compose.ui.misskey_achievement_just_plain_lucky_description
import dev.dimension.flare.compose.ui.misskey_achievement_just_plain_lucky_title
import dev.dimension.flare.compose.ui.misskey_achievement_logged_in_on_birthday_description
import dev.dimension.flare.compose.ui.misskey_achievement_logged_in_on_birthday_title
import dev.dimension.flare.compose.ui.misskey_achievement_logged_in_on_new_years_day_description
import dev.dimension.flare.compose.ui.misskey_achievement_logged_in_on_new_years_day_title
import dev.dimension.flare.compose.ui.misskey_achievement_login1000_description
import dev.dimension.flare.compose.ui.misskey_achievement_login1000_title
import dev.dimension.flare.compose.ui.misskey_achievement_login100_description
import dev.dimension.flare.compose.ui.misskey_achievement_login100_title
import dev.dimension.flare.compose.ui.misskey_achievement_login15_description
import dev.dimension.flare.compose.ui.misskey_achievement_login15_title
import dev.dimension.flare.compose.ui.misskey_achievement_login200_description
import dev.dimension.flare.compose.ui.misskey_achievement_login200_title
import dev.dimension.flare.compose.ui.misskey_achievement_login300_description
import dev.dimension.flare.compose.ui.misskey_achievement_login300_title
import dev.dimension.flare.compose.ui.misskey_achievement_login30_description
import dev.dimension.flare.compose.ui.misskey_achievement_login30_title
import dev.dimension.flare.compose.ui.misskey_achievement_login3_description
import dev.dimension.flare.compose.ui.misskey_achievement_login3_title
import dev.dimension.flare.compose.ui.misskey_achievement_login400_description
import dev.dimension.flare.compose.ui.misskey_achievement_login400_title
import dev.dimension.flare.compose.ui.misskey_achievement_login500_description
import dev.dimension.flare.compose.ui.misskey_achievement_login500_title
import dev.dimension.flare.compose.ui.misskey_achievement_login600_description
import dev.dimension.flare.compose.ui.misskey_achievement_login600_title
import dev.dimension.flare.compose.ui.misskey_achievement_login60_description
import dev.dimension.flare.compose.ui.misskey_achievement_login60_title
import dev.dimension.flare.compose.ui.misskey_achievement_login700_description
import dev.dimension.flare.compose.ui.misskey_achievement_login700_title
import dev.dimension.flare.compose.ui.misskey_achievement_login7_description
import dev.dimension.flare.compose.ui.misskey_achievement_login7_title
import dev.dimension.flare.compose.ui.misskey_achievement_login800_description
import dev.dimension.flare.compose.ui.misskey_achievement_login800_title
import dev.dimension.flare.compose.ui.misskey_achievement_login900_description
import dev.dimension.flare.compose.ui.misskey_achievement_login900_title
import dev.dimension.flare.compose.ui.misskey_achievement_marked_as_cat_description
import dev.dimension.flare.compose.ui.misskey_achievement_marked_as_cat_title
import dev.dimension.flare.compose.ui.misskey_achievement_my_note_favorited1_description
import dev.dimension.flare.compose.ui.misskey_achievement_my_note_favorited1_title
import dev.dimension.flare.compose.ui.misskey_achievement_note_clipped1_description
import dev.dimension.flare.compose.ui.misskey_achievement_note_clipped1_title
import dev.dimension.flare.compose.ui.misskey_achievement_note_deleted_within1min_description
import dev.dimension.flare.compose.ui.misskey_achievement_note_deleted_within1min_title
import dev.dimension.flare.compose.ui.misskey_achievement_note_favorited1_description
import dev.dimension.flare.compose.ui.misskey_achievement_note_favorited1_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes100000_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes100000_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes10000_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes10000_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes1000_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes1000_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes100_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes100_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes10_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes10_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes1_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes1_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes20000_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes20000_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes30000_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes30000_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes40000_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes40000_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes50000_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes50000_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes5000_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes5000_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes500_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes500_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes60000_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes60000_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes70000_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes70000_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes80000_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes80000_title
import dev.dimension.flare.compose.ui.misskey_achievement_notes90000_description
import dev.dimension.flare.compose.ui.misskey_achievement_notes90000_title
import dev.dimension.flare.compose.ui.misskey_achievement_open3windows_description
import dev.dimension.flare.compose.ui.misskey_achievement_open3windows_title
import dev.dimension.flare.compose.ui.misskey_achievement_output_hello_world_on_scratchpad_description
import dev.dimension.flare.compose.ui.misskey_achievement_output_hello_world_on_scratchpad_title
import dev.dimension.flare.compose.ui.misskey_achievement_passed_since_account_created1_description
import dev.dimension.flare.compose.ui.misskey_achievement_passed_since_account_created1_title
import dev.dimension.flare.compose.ui.misskey_achievement_passed_since_account_created2_description
import dev.dimension.flare.compose.ui.misskey_achievement_passed_since_account_created2_title
import dev.dimension.flare.compose.ui.misskey_achievement_passed_since_account_created3_description
import dev.dimension.flare.compose.ui.misskey_achievement_passed_since_account_created3_title
import dev.dimension.flare.compose.ui.misskey_achievement_posted_at_0min0sec_description
import dev.dimension.flare.compose.ui.misskey_achievement_posted_at_0min0sec_title
import dev.dimension.flare.compose.ui.misskey_achievement_posted_at_late_night_description
import dev.dimension.flare.compose.ui.misskey_achievement_posted_at_late_night_title
import dev.dimension.flare.compose.ui.misskey_achievement_profile_filled_description
import dev.dimension.flare.compose.ui.misskey_achievement_profile_filled_title
import dev.dimension.flare.compose.ui.misskey_achievement_react_without_read_description
import dev.dimension.flare.compose.ui.misskey_achievement_react_without_read_title
import dev.dimension.flare.compose.ui.misskey_achievement_self_quote_description
import dev.dimension.flare.compose.ui.misskey_achievement_self_quote_title
import dev.dimension.flare.compose.ui.misskey_achievement_set_name_to_syuilo_description
import dev.dimension.flare.compose.ui.misskey_achievement_set_name_to_syuilo_title
import dev.dimension.flare.compose.ui.misskey_achievement_smash_test_notification_button_description
import dev.dimension.flare.compose.ui.misskey_achievement_smash_test_notification_button_title
import dev.dimension.flare.compose.ui.misskey_achievement_tutorial_completed_description
import dev.dimension.flare.compose.ui.misskey_achievement_tutorial_completed_title
import dev.dimension.flare.compose.ui.misskey_achievement_view_achievements3min_description
import dev.dimension.flare.compose.ui.misskey_achievement_view_achievements3min_title
import dev.dimension.flare.compose.ui.misskey_achievement_view_instance_chart_description
import dev.dimension.flare.compose.ui.misskey_achievement_view_instance_chart_title
import dev.dimension.flare.compose.ui.misskey_notification_item_achievement_earned
import dev.dimension.flare.compose.ui.misskey_notification_item_app
import dev.dimension.flare.compose.ui.misskey_notification_item_follow_request_accepted
import dev.dimension.flare.compose.ui.misskey_notification_item_reacted_to_your_status
import dev.dimension.flare.compose.ui.notification_item_accept_follow_request
import dev.dimension.flare.compose.ui.notification_item_reject_follow_request
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDividerDefaults
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.VerticalDivider
import dev.dimension.flare.ui.component.platform.PlatformButton
import dev.dimension.flare.ui.component.platform.PlatformCard
import dev.dimension.flare.ui.component.platform.PlatformFilledTonalButton
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.MisskeyAchievement
import dev.dimension.flare.ui.theme.PlatformContentColor
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// @Composable
// internal fun UiTimelineComponent(
//    item: UiTimelineV2,
//    modifier: Modifier = Modifier,
//    detailStatusKey: MicroBlogKey? = null,
//    horizontalPadding: Dp = screenHorizontalPadding,
// ) {
//    val bigScreen = isBigScreen()
//    val appearance = LocalComponentAppearance.current
//    Column(
//        modifier = modifier,
//    ) {
//        val message =
//            when (item) {
//                is UiTimelineV2.Post -> item.message
//                is UiTimelineV2.User -> item.message
//                is UiTimelineV2.UserList -> item.message
//                is UiTimelineV2.Message -> item
//                is UiTimelineV2.Feed -> null
//            }
//        val hasContent = item !is UiTimelineV2.Message
//        message?.let {
//            TopMessageComponent(
//                data = it,
//                topMessageOnly = !hasContent,
//                modifier =
//                    Modifier
//                        .padding(horizontal = horizontalPadding)
//                        .let {
//                            if (!hasContent) {
//                                it.padding(vertical = 8.dp)
//                            } else {
//                                if (!appearance.fullWidthPost) {
//                                    it.padding(
//                                        top = 8.dp,
//                                        start = AvatarComponentDefaults.size - PlatformTheme.typography.caption.fontSize.value.dp,
//                                    )
//                                } else {
//                                    it.padding(top = 8.dp)
//                                }
//                            }
//                        }.fillMaxWidth(),
//            )
//        }
//        if (hasContent) {
//            val padding =
//                if (message == null) {
//                    PaddingValues(
//                        start = horizontalPadding,
//                        end = horizontalPadding,
//                        bottom = 8.dp,
//                        top = if (bigScreen) 16.dp else 8.dp,
//                    )
//                } else {
//                    PaddingValues(
//                        start = horizontalPadding,
//                        end = horizontalPadding,
//                        bottom = 8.dp,
//                        top = 8.dp,
//                    )
//                }
//            ItemContentComponent(
//                item = item,
//                detailStatusKey = detailStatusKey,
//                paddingValues = padding,
//            )
//        }
//    }
// }

@Composable
internal fun UiTimelineComponent(
    item: UiTimelineV2,
    modifier: Modifier = Modifier,
    detailStatusKey: MicroBlogKey? = null,
    horizontalPadding: Dp = screenHorizontalPadding,
) {
    val uriHandler = LocalUriHandler.current
    when (item) {
        is UiTimelineV2.Post ->
            StatusContent(
                data = item,
                detailStatusKey = detailStatusKey,
                paddingValues =
                    PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        bottom = 8.dp,
                        top = 8.dp,
                    ),
                modifier = modifier,
            )

        is UiTimelineV2.User -> {
            Column(
                modifier =
                    modifier
                        .padding(
                            PaddingValues(
                                start = horizontalPadding,
                                end = horizontalPadding,
                                bottom = 8.dp,
                                top = 8.dp,
                            ),
                        ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item.message?.let { message ->
                    TopMessageComponent(
                        data = message,
                        topMessageOnly = false,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                    )
                }
                CommonStatusHeaderComponent(
                    data = item.value,
                    onUserClick = {
                        item.value.onClicked.invoke(
                            ClickContext(
                                launcher = {
                                    uriHandler.openUri(it)
                                },
                            ),
                        )
                    },
                )
                if (item.button.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item.button.fastForEach { button ->
                            when ((button.text as? ActionMenu.Item.Text.Localized)?.type) {
                                ActionMenu.Item.Text.Localized.Type.AcceptFollowRequest ->
                                    PlatformFilledTonalButton(
                                        onClick = {
                                            button.onClicked.invoke(
                                                ClickContext(
                                                    launcher = {
                                                        uriHandler.openUri(it)
                                                    },
                                                ),
                                            )
                                        },
                                    ) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.Check,
                                            contentDescription =
                                                stringResource(
                                                    Res.string.notification_item_accept_follow_request,
                                                ),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        PlatformText(
                                            text =
                                                stringResource(
                                                    Res.string.notification_item_accept_follow_request,
                                                ),
                                        )
                                    }
                                ActionMenu.Item.Text.Localized.Type.RejectFollowRequest -> {
                                    PlatformButton(
                                        onClick = {
                                            button.onClicked.invoke(
                                                ClickContext(
                                                    launcher = {
                                                        uriHandler.openUri(it)
                                                    },
                                                ),
                                            )
                                        },
                                        content = {
                                            FAIcon(
                                                FontAwesomeIcons.Solid.Xmark,
                                                contentDescription =
                                                    stringResource(
                                                        Res.string.notification_item_reject_follow_request,
                                                    ),
                                                tint = PlatformTheme.colorScheme.error,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            PlatformText(
                                                text =
                                                    stringResource(
                                                        Res.string.notification_item_reject_follow_request,
                                                    ),
                                                color = PlatformTheme.colorScheme.error,
                                            )
                                        },
                                    )
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }
        }

        is UiTimelineV2.UserList ->
            UserListContent(
                data = item,
                modifier =
                    modifier
                        .padding(
                            PaddingValues(
                                start = horizontalPadding,
                                end = horizontalPadding,
                                bottom = 8.dp,
                                top = 8.dp,
                            ),
                        ),
            )

        is UiTimelineV2.Feed -> {
            FeedComponent(
                data = item,
                modifier = modifier,
            )
        }

        is UiTimelineV2.Message ->
            TopMessageComponent(
                data = item,
                topMessageOnly = true,
                modifier =
                    Modifier
                        .padding(horizontal = horizontalPadding)
                        .padding(vertical = 8.dp)
                        .fillMaxWidth(),
            )
    }
}

@Composable
private fun UserListContent(
    data: UiTimelineV2.UserList,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        data.message?.let { message ->
            TopMessageComponent(
                data = message,
                topMessageOnly = false,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(data.users.size) {
                val user = data.users[it]
                PlatformCard(
                    modifier = Modifier.width(256.dp),
                ) {
                    CommonStatusHeaderComponent(
                        data = user,
                        onUserClick = {
                            user.onClicked.invoke(
                                ClickContext(
                                    launcher = {
                                        uriHandler.openUri(it)
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
        val status = data.post
        if (status != null) {
            CompositionLocalProvider(
                LocalComponentAppearance provides
                    LocalComponentAppearance.current.copy(
                        postActionStyle = PostActionStyle.Hidden,
                    ),
            ) {
                CommonStatusComponent(
                    item = status,
                    modifier =
                        Modifier
                            .border(
                                FlareDividerDefaults.thickness,
                                color = FlareDividerDefaults.color,
                                shape = PlatformTheme.shapes.medium,
                            ).clip(
                                shape = PlatformTheme.shapes.medium,
                            ).padding(8.dp),
                    isQuote = true,
                )
            }
        }
    }
}

@Composable
private fun StatusContent(
    data: UiTimelineV2.Post,
    detailStatusKey: MicroBlogKey?,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (data.parents.any()) {
            Layout(
                content = {
                    CompositionLocalProvider(
                        LocalComponentAppearance provides LocalComponentAppearance.current.copy(fullWidthPost = false),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            data.parents.fastForEach {
                                CommonStatusComponent(
                                    item = it,
                                    isDetail = false,
                                    modifier = Modifier.padding(paddingValues),
                                )
                            }
                        }
                    }
                    VerticalDivider(
                        modifier =
                            Modifier
                                .zIndex(-1f)
                                .padding(
                                    start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                                ).padding(start = AvatarComponentDefaults.size / 2)
                                .offset(y = AvatarComponentDefaults.size / 2),
                    )
                },
                measurePolicy = { measurables, constraints ->
                    val divider = measurables.last()
                    val content = measurables.first()
                    val placeables = content.measure(constraints)
                    val dividerPlaceable =
                        divider.measure(
                            Constraints(
                                maxWidth = constraints.maxWidth,
                                minWidth = constraints.minWidth,
                                maxHeight = placeables.measuredHeight,
                                minHeight = placeables.measuredHeight,
                            ),
                        )
                    layout(
                        width = placeables.width,
                        height = placeables.height,
                    ) {
                        dividerPlaceable.placeRelative(
                            x = 0,
                            y = 0,
                        )
                        placeables.placeRelative(0, 0)
                    }
                },
            )
        }
        Column {
            data.message?.let { message ->
                TopMessageComponent(
                    data = message,
                    topMessageOnly = false,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                )
            }
            CommonStatusComponent(
                item = data,
                isDetail = detailStatusKey?.toString()?.let(data.itemKey::contains) == true,
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}

@Composable
private fun TopMessageComponent(
    data: UiTimelineV2.Message,
    topMessageOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalComponentAppearance.current
    val uriHandler = LocalUriHandler.current
    val icon = data.icon.toTopMessageIcon()
    val text: String? =
        when (val type = data.type) {
            is UiTimelineV2.Message.Type.Raw -> type.content
            is UiTimelineV2.Message.Type.Unknown -> type.rawType.ifBlank { null }
            is UiTimelineV2.Message.Type.Localized ->
                when (type.data) {
                    UiTimelineV2.Message.Type.Localized.MessageId.Mention ->
                        stringResource(resource = Res.string.mastodon_notification_item_mentioned_you)

                    UiTimelineV2.Message.Type.Localized.MessageId.NewPost ->
                        stringResource(resource = Res.string.mastodon_notification_item_posted_status)

                    UiTimelineV2.Message.Type.Localized.MessageId.Repost ->
                        stringResource(resource = Res.string.mastodon_notification_item_reblogged_your_status)

                    UiTimelineV2.Message.Type.Localized.MessageId.Follow ->
                        stringResource(resource = Res.string.mastodon_notification_item_followed_you)

                    UiTimelineV2.Message.Type.Localized.MessageId.FollowRequest ->
                        stringResource(resource = Res.string.mastodon_notification_item_requested_follow)

                    UiTimelineV2.Message.Type.Localized.MessageId.Favourite ->
                        stringResource(resource = Res.string.mastodon_notification_item_favourited_your_status)

                    UiTimelineV2.Message.Type.Localized.MessageId.PollEnded ->
                        stringResource(resource = Res.string.mastodon_notification_item_poll_ended)

                    UiTimelineV2.Message.Type.Localized.MessageId.PostUpdated ->
                        stringResource(resource = Res.string.mastodon_notification_item_updated_status)

                    UiTimelineV2.Message.Type.Localized.MessageId.Reply ->
                        stringResource(resource = Res.string.mastodon_notification_item_mentioned_you)

                    UiTimelineV2.Message.Type.Localized.MessageId.Quote ->
                        stringResource(resource = Res.string.mastodon_notification_item_reblogged_your_status)

                    UiTimelineV2.Message.Type.Localized.MessageId.Reaction ->
                        stringResource(resource = Res.string.misskey_notification_item_reacted_to_your_status)

                    UiTimelineV2.Message.Type.Localized.MessageId.FollowRequestAccepted ->
                        stringResource(resource = Res.string.misskey_notification_item_follow_request_accepted)

                    UiTimelineV2.Message.Type.Localized.MessageId.AchievementEarned -> {
                        runCatching {
                            MisskeyAchievement.valueOf(type.args.getOrNull(0).orEmpty())
                        }.getOrNull()?.let { achievement ->
                            stringResource(
                                resource = Res.string.misskey_notification_item_achievement_earned,
                                achievement.titleResId,
                                achievement.descriptionResId,
                            )
                        }
                            ?: stringResource(
                                resource = Res.string.misskey_notification_item_achievement_earned,
                                type.args.getOrNull(0).orEmpty(),
                                "",
                            )
                    }

                    UiTimelineV2.Message.Type.Localized.MessageId.App ->
                        stringResource(resource = Res.string.misskey_notification_item_app)

                    UiTimelineV2.Message.Type.Localized.MessageId.StarterpackJoined ->
                        stringResource(resource = Res.string.bluesky_notification_item_starterpack_joined)

                    UiTimelineV2.Message.Type.Localized.MessageId.Pinned ->
                        stringResource(resource = Res.string.mastodon_item_pinned)
                }
        }

    if (text != null) {
        StatusRetweetHeaderComponent(
            icon = icon,
            user = data.user,
            text = text,
            color =
                if (topMessageOnly) {
                    PlatformContentColor.current
                } else {
                    PlatformTheme.colorScheme.caption
                },
            textStyle =
                if (topMessageOnly) {
                    PlatformTextStyle.current
                } else {
                    PlatformTheme.typography.caption
                },
            modifier =
                Modifier
                    .clickable {
                        data.onClicked.invoke(
                            ClickContext(
                                launcher = {
                                    uriHandler.openUri(it)
                                },
                            ),
                        )
                    }.let {
                        if (!appearance.fullWidthPost && !topMessageOnly) {
                            it.padding(
                                start = AvatarComponentDefaults.size - PlatformTheme.typography.caption.fontSize.value.dp,
                            )
                        } else {
                            it
                        }
                    }.fillMaxWidth()
                    .then(modifier),
        )
    }
}

private fun UiIcon.toTopMessageIcon() =
    when (this) {
        UiIcon.Retweet -> FontAwesomeIcons.Solid.Retweet
        UiIcon.Follow -> FontAwesomeIcons.Solid.UserPlus
        UiIcon.Favourite -> FontAwesomeIcons.Solid.Heart
        UiIcon.Mention -> FontAwesomeIcons.Solid.At
        UiIcon.Poll -> FontAwesomeIcons.Solid.SquarePollHorizontal
        UiIcon.Edit -> FontAwesomeIcons.Solid.Pen
        UiIcon.Info -> FontAwesomeIcons.Solid.CircleInfo
        UiIcon.Reply -> FontAwesomeIcons.Solid.Reply
        UiIcon.Quote -> FontAwesomeIcons.Solid.QuoteLeft
        UiIcon.Pin -> FontAwesomeIcons.Solid.Thumbtack
        else -> FontAwesomeIcons.Solid.CircleInfo
    }

private val MisskeyAchievement.titleResId: StringResource
    get() =
        when (this) {
            MisskeyAchievement.NOTES1 -> Res.string.misskey_achievement_notes1_title
            MisskeyAchievement.NOTES10 -> Res.string.misskey_achievement_notes10_title
            MisskeyAchievement.NOTES100 -> Res.string.misskey_achievement_notes100_title
            MisskeyAchievement.NOTES500 -> Res.string.misskey_achievement_notes500_title
            MisskeyAchievement.NOTES1000 -> Res.string.misskey_achievement_notes1000_title
            MisskeyAchievement.NOTES5000 -> Res.string.misskey_achievement_notes5000_title
            MisskeyAchievement.NOTES10000 -> Res.string.misskey_achievement_notes10000_title
            MisskeyAchievement.NOTES20000 -> Res.string.misskey_achievement_notes20000_title
            MisskeyAchievement.NOTES30000 -> Res.string.misskey_achievement_notes30000_title
            MisskeyAchievement.NOTES40000 -> Res.string.misskey_achievement_notes40000_title
            MisskeyAchievement.NOTES50000 -> Res.string.misskey_achievement_notes50000_title
            MisskeyAchievement.NOTES60000 -> Res.string.misskey_achievement_notes60000_title
            MisskeyAchievement.NOTES70000 -> Res.string.misskey_achievement_notes70000_title
            MisskeyAchievement.NOTES80000 -> Res.string.misskey_achievement_notes80000_title
            MisskeyAchievement.NOTES90000 -> Res.string.misskey_achievement_notes90000_title
            MisskeyAchievement.NOTES100000 -> Res.string.misskey_achievement_notes100000_title
            MisskeyAchievement.LOGIN3 -> Res.string.misskey_achievement_login3_title
            MisskeyAchievement.LOGIN7 -> Res.string.misskey_achievement_login7_title
            MisskeyAchievement.LOGIN15 -> Res.string.misskey_achievement_login15_title
            MisskeyAchievement.LOGIN30 -> Res.string.misskey_achievement_login30_title
            MisskeyAchievement.LOGIN60 -> Res.string.misskey_achievement_login60_title
            MisskeyAchievement.LOGIN100 -> Res.string.misskey_achievement_login100_title
            MisskeyAchievement.LOGIN200 -> Res.string.misskey_achievement_login200_title
            MisskeyAchievement.LOGIN300 -> Res.string.misskey_achievement_login300_title
            MisskeyAchievement.LOGIN400 -> Res.string.misskey_achievement_login400_title
            MisskeyAchievement.LOGIN500 -> Res.string.misskey_achievement_login500_title
            MisskeyAchievement.LOGIN600 -> Res.string.misskey_achievement_login600_title
            MisskeyAchievement.LOGIN700 -> Res.string.misskey_achievement_login700_title
            MisskeyAchievement.LOGIN800 -> Res.string.misskey_achievement_login800_title
            MisskeyAchievement.LOGIN900 -> Res.string.misskey_achievement_login900_title
            MisskeyAchievement.LOGIN1000 -> Res.string.misskey_achievement_login1000_title
            MisskeyAchievement.NOTE_CLIPPED1 -> Res.string.misskey_achievement_note_clipped1_title
            MisskeyAchievement.NOTE_FAVORITED1 -> Res.string.misskey_achievement_note_favorited1_title
            MisskeyAchievement.MY_NOTE_FAVORITED1 -> Res.string.misskey_achievement_my_note_favorited1_title
            MisskeyAchievement.PROFILE_FILLED -> Res.string.misskey_achievement_profile_filled_title
            MisskeyAchievement.MARKED_AS_CAT -> Res.string.misskey_achievement_marked_as_cat_title
            MisskeyAchievement.FOLLOWING1 -> Res.string.misskey_achievement_following1_title
            MisskeyAchievement.FOLLOWING10 -> Res.string.misskey_achievement_following10_title
            MisskeyAchievement.FOLLOWING50 -> Res.string.misskey_achievement_following50_title
            MisskeyAchievement.FOLLOWING100 -> Res.string.misskey_achievement_following100_title
            MisskeyAchievement.FOLLOWING300 -> Res.string.misskey_achievement_following300_title
            MisskeyAchievement.FOLLOWERS1 -> Res.string.misskey_achievement_followers1_title
            MisskeyAchievement.FOLLOWERS10 -> Res.string.misskey_achievement_followers10_title
            MisskeyAchievement.FOLLOWERS50 -> Res.string.misskey_achievement_followers50_title
            MisskeyAchievement.FOLLOWERS100 -> Res.string.misskey_achievement_followers100_title
            MisskeyAchievement.FOLLOWERS300 -> Res.string.misskey_achievement_followers300_title
            MisskeyAchievement.FOLLOWERS500 -> Res.string.misskey_achievement_followers500_title
            MisskeyAchievement.FOLLOWERS1000 -> Res.string.misskey_achievement_followers1000_title
            MisskeyAchievement.COLLECT_ACHIEVEMENTS30 -> Res.string.misskey_achievement_collect_achievements30_title
            MisskeyAchievement.VIEW_ACHIEVEMENTS3MIN -> Res.string.misskey_achievement_view_achievements3min_title
            MisskeyAchievement.I_LOVE_MISSKEY -> Res.string.misskey_achievement_i_love_misskey_title
            MisskeyAchievement.FOUND_TREASURE -> Res.string.misskey_achievement_found_treasure_title
            MisskeyAchievement.CLIENT30MIN -> Res.string.misskey_achievement_client30min_title
            MisskeyAchievement.CLIENT60MIN -> Res.string.misskey_achievement_client60min_title
            MisskeyAchievement.NOTE_DELETED_WITHIN1MIN -> Res.string.misskey_achievement_note_deleted_within1min_title
            MisskeyAchievement.POSTED_AT_LATE_NIGHT -> Res.string.misskey_achievement_posted_at_late_night_title
            MisskeyAchievement.POSTED_AT_0MIN0SEC -> Res.string.misskey_achievement_posted_at_0min0sec_title
            MisskeyAchievement.SELF_QUOTE -> Res.string.misskey_achievement_self_quote_title
            MisskeyAchievement.HTL20NPM -> Res.string.misskey_achievement_htl20npm_title
            MisskeyAchievement.VIEW_INSTANCE_CHART -> Res.string.misskey_achievement_view_instance_chart_title
            MisskeyAchievement.OUTPUT_HELLO_WORLD_ON_SCRATCHPAD -> Res.string.misskey_achievement_output_hello_world_on_scratchpad_title
            MisskeyAchievement.OPEN3WINDOWS -> Res.string.misskey_achievement_open3windows_title
            MisskeyAchievement.DRIVE_FOLDER_CIRCULAR_REFERENCE -> Res.string.misskey_achievement_drive_folder_circular_reference_title
            MisskeyAchievement.REACT_WITHOUT_READ -> Res.string.misskey_achievement_react_without_read_title
            MisskeyAchievement.CLICKED_CLICK_HERE -> Res.string.misskey_achievement_clicked_click_here_title
            MisskeyAchievement.JUST_PLAIN_LUCKY -> Res.string.misskey_achievement_just_plain_lucky_title
            MisskeyAchievement.SET_NAME_TO_SYUILO -> Res.string.misskey_achievement_set_name_to_syuilo_title
            MisskeyAchievement.PASSED_SINCE_ACCOUNT_CREATED1 -> Res.string.misskey_achievement_passed_since_account_created1_title
            MisskeyAchievement.PASSED_SINCE_ACCOUNT_CREATED2 -> Res.string.misskey_achievement_passed_since_account_created2_title
            MisskeyAchievement.PASSED_SINCE_ACCOUNT_CREATED3 -> Res.string.misskey_achievement_passed_since_account_created3_title
            MisskeyAchievement.LOGGED_IN_ON_BIRTHDAY -> Res.string.misskey_achievement_logged_in_on_birthday_title
            MisskeyAchievement.LOGGED_IN_ON_NEW_YEARS_DAY -> Res.string.misskey_achievement_logged_in_on_new_years_day_title
            MisskeyAchievement.COOKIE_CLICKED -> Res.string.misskey_achievement_cookie_clicked_title
            MisskeyAchievement.BRAIN_DIVER -> Res.string.misskey_achievement_brain_diver_title
            MisskeyAchievement.SMASH_TEST_NOTIFICATION_BUTTON -> Res.string.misskey_achievement_smash_test_notification_button_title
            MisskeyAchievement.TUTORIAL_COMPLETED -> Res.string.misskey_achievement_tutorial_completed_title
            MisskeyAchievement.BUBBLE_GAME_EXPLODING_HEAD -> Res.string.misskey_achievement_bubble_game_exploding_head_title
            MisskeyAchievement.BUBBLE_GAME_DOUBLE_EXPLODING_HEAD -> Res.string.misskey_achievement_bubble_game_double_exploding_head_title
        }

private val MisskeyAchievement.descriptionResId: StringResource
    get() =
        when (this) {
            MisskeyAchievement.NOTES1 -> Res.string.misskey_achievement_notes1_description
            MisskeyAchievement.NOTES10 -> Res.string.misskey_achievement_notes10_description
            MisskeyAchievement.NOTES100 -> Res.string.misskey_achievement_notes100_description
            MisskeyAchievement.NOTES500 -> Res.string.misskey_achievement_notes500_description
            MisskeyAchievement.NOTES1000 -> Res.string.misskey_achievement_notes1000_description
            MisskeyAchievement.NOTES5000 -> Res.string.misskey_achievement_notes5000_description
            MisskeyAchievement.NOTES10000 -> Res.string.misskey_achievement_notes10000_description
            MisskeyAchievement.NOTES20000 -> Res.string.misskey_achievement_notes20000_description
            MisskeyAchievement.NOTES30000 -> Res.string.misskey_achievement_notes30000_description
            MisskeyAchievement.NOTES40000 -> Res.string.misskey_achievement_notes40000_description
            MisskeyAchievement.NOTES50000 -> Res.string.misskey_achievement_notes50000_description
            MisskeyAchievement.NOTES60000 -> Res.string.misskey_achievement_notes60000_description
            MisskeyAchievement.NOTES70000 -> Res.string.misskey_achievement_notes70000_description
            MisskeyAchievement.NOTES80000 -> Res.string.misskey_achievement_notes80000_description
            MisskeyAchievement.NOTES90000 -> Res.string.misskey_achievement_notes90000_description
            MisskeyAchievement.NOTES100000 -> Res.string.misskey_achievement_notes100000_description
            MisskeyAchievement.LOGIN3 -> Res.string.misskey_achievement_login3_description
            MisskeyAchievement.LOGIN7 -> Res.string.misskey_achievement_login7_description
            MisskeyAchievement.LOGIN15 -> Res.string.misskey_achievement_login15_description
            MisskeyAchievement.LOGIN30 -> Res.string.misskey_achievement_login30_description
            MisskeyAchievement.LOGIN60 -> Res.string.misskey_achievement_login60_description
            MisskeyAchievement.LOGIN100 -> Res.string.misskey_achievement_login100_description
            MisskeyAchievement.LOGIN200 -> Res.string.misskey_achievement_login200_description
            MisskeyAchievement.LOGIN300 -> Res.string.misskey_achievement_login300_description
            MisskeyAchievement.LOGIN400 -> Res.string.misskey_achievement_login400_description
            MisskeyAchievement.LOGIN500 -> Res.string.misskey_achievement_login500_description
            MisskeyAchievement.LOGIN600 -> Res.string.misskey_achievement_login600_description
            MisskeyAchievement.LOGIN700 -> Res.string.misskey_achievement_login700_description
            MisskeyAchievement.LOGIN800 -> Res.string.misskey_achievement_login800_description
            MisskeyAchievement.LOGIN900 -> Res.string.misskey_achievement_login900_description
            MisskeyAchievement.LOGIN1000 -> Res.string.misskey_achievement_login1000_description
            MisskeyAchievement.NOTE_CLIPPED1 -> Res.string.misskey_achievement_note_clipped1_description
            MisskeyAchievement.NOTE_FAVORITED1 -> Res.string.misskey_achievement_note_favorited1_description
            MisskeyAchievement.MY_NOTE_FAVORITED1 -> Res.string.misskey_achievement_my_note_favorited1_description
            MisskeyAchievement.PROFILE_FILLED -> Res.string.misskey_achievement_profile_filled_description
            MisskeyAchievement.MARKED_AS_CAT -> Res.string.misskey_achievement_marked_as_cat_description
            MisskeyAchievement.FOLLOWING1 -> Res.string.misskey_achievement_following1_description
            MisskeyAchievement.FOLLOWING10 -> Res.string.misskey_achievement_following10_description
            MisskeyAchievement.FOLLOWING50 -> Res.string.misskey_achievement_following50_description
            MisskeyAchievement.FOLLOWING100 -> Res.string.misskey_achievement_following100_description
            MisskeyAchievement.FOLLOWING300 -> Res.string.misskey_achievement_following300_description
            MisskeyAchievement.FOLLOWERS1 -> Res.string.misskey_achievement_followers1_description
            MisskeyAchievement.FOLLOWERS10 -> Res.string.misskey_achievement_followers10_description
            MisskeyAchievement.FOLLOWERS50 -> Res.string.misskey_achievement_followers50_description
            MisskeyAchievement.FOLLOWERS100 -> Res.string.misskey_achievement_followers100_description
            MisskeyAchievement.FOLLOWERS300 -> Res.string.misskey_achievement_followers300_description
            MisskeyAchievement.FOLLOWERS500 -> Res.string.misskey_achievement_followers500_description
            MisskeyAchievement.FOLLOWERS1000 -> Res.string.misskey_achievement_followers1000_description
            MisskeyAchievement.COLLECT_ACHIEVEMENTS30 -> Res.string.misskey_achievement_collect_achievements30_description
            MisskeyAchievement.VIEW_ACHIEVEMENTS3MIN -> Res.string.misskey_achievement_view_achievements3min_description
            MisskeyAchievement.I_LOVE_MISSKEY -> Res.string.misskey_achievement_i_love_misskey_description
            MisskeyAchievement.FOUND_TREASURE -> Res.string.misskey_achievement_found_treasure_description
            MisskeyAchievement.CLIENT30MIN -> Res.string.misskey_achievement_client30min_description
            MisskeyAchievement.CLIENT60MIN -> Res.string.misskey_achievement_client60min_description
            MisskeyAchievement.NOTE_DELETED_WITHIN1MIN -> Res.string.misskey_achievement_note_deleted_within1min_description
            MisskeyAchievement.POSTED_AT_LATE_NIGHT -> Res.string.misskey_achievement_posted_at_late_night_description
            MisskeyAchievement.POSTED_AT_0MIN0SEC -> Res.string.misskey_achievement_posted_at_0min0sec_description
            MisskeyAchievement.SELF_QUOTE -> Res.string.misskey_achievement_self_quote_description
            MisskeyAchievement.HTL20NPM -> Res.string.misskey_achievement_htl20npm_description
            MisskeyAchievement.VIEW_INSTANCE_CHART -> Res.string.misskey_achievement_view_instance_chart_description
            MisskeyAchievement.OUTPUT_HELLO_WORLD_ON_SCRATCHPAD ->
                Res.string.misskey_achievement_output_hello_world_on_scratchpad_description
            MisskeyAchievement.OPEN3WINDOWS -> Res.string.misskey_achievement_open3windows_description
            MisskeyAchievement.DRIVE_FOLDER_CIRCULAR_REFERENCE -> Res.string.misskey_achievement_drive_folder_circular_reference_description
            MisskeyAchievement.REACT_WITHOUT_READ -> Res.string.misskey_achievement_react_without_read_description
            MisskeyAchievement.CLICKED_CLICK_HERE -> Res.string.misskey_achievement_clicked_click_here_description
            MisskeyAchievement.JUST_PLAIN_LUCKY -> Res.string.misskey_achievement_just_plain_lucky_description
            MisskeyAchievement.SET_NAME_TO_SYUILO -> Res.string.misskey_achievement_set_name_to_syuilo_description
            MisskeyAchievement.PASSED_SINCE_ACCOUNT_CREATED1 -> Res.string.misskey_achievement_passed_since_account_created1_description
            MisskeyAchievement.PASSED_SINCE_ACCOUNT_CREATED2 -> Res.string.misskey_achievement_passed_since_account_created2_description
            MisskeyAchievement.PASSED_SINCE_ACCOUNT_CREATED3 -> Res.string.misskey_achievement_passed_since_account_created3_description
            MisskeyAchievement.LOGGED_IN_ON_BIRTHDAY -> Res.string.misskey_achievement_logged_in_on_birthday_description
            MisskeyAchievement.LOGGED_IN_ON_NEW_YEARS_DAY -> Res.string.misskey_achievement_logged_in_on_new_years_day_description
            MisskeyAchievement.COOKIE_CLICKED -> Res.string.misskey_achievement_cookie_clicked_description
            MisskeyAchievement.BRAIN_DIVER -> Res.string.misskey_achievement_brain_diver_description
            MisskeyAchievement.SMASH_TEST_NOTIFICATION_BUTTON -> Res.string.misskey_achievement_smash_test_notification_button_description
            MisskeyAchievement.TUTORIAL_COMPLETED -> Res.string.misskey_achievement_tutorial_completed_description
            MisskeyAchievement.BUBBLE_GAME_EXPLODING_HEAD -> Res.string.misskey_achievement_bubble_game_exploding_head_description
            MisskeyAchievement.BUBBLE_GAME_DOUBLE_EXPLODING_HEAD ->
                Res.string.misskey_achievement_bubble_game_double_exploding_head_description
        }
