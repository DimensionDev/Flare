package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import compose.icons.fontawesomeicons.solid.UserPlus
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Res
import dev.dimension.flare.ui.component.bluesky_notification_item_favourited_your_status
import dev.dimension.flare.ui.component.bluesky_notification_item_followed_you
import dev.dimension.flare.ui.component.bluesky_notification_item_mentioned_you
import dev.dimension.flare.ui.component.bluesky_notification_item_quoted_your_status
import dev.dimension.flare.ui.component.bluesky_notification_item_reblogged_your_status
import dev.dimension.flare.ui.component.bluesky_notification_item_replied_to_you
import dev.dimension.flare.ui.component.bluesky_notification_item_starterpack_joined
import dev.dimension.flare.ui.component.bluesky_notification_item_unKnown
import dev.dimension.flare.ui.component.mastodon_notification_item_favourited_your_status
import dev.dimension.flare.ui.component.mastodon_notification_item_followed_you
import dev.dimension.flare.ui.component.mastodon_notification_item_mentioned_you
import dev.dimension.flare.ui.component.mastodon_notification_item_poll_ended
import dev.dimension.flare.ui.component.mastodon_notification_item_posted_status
import dev.dimension.flare.ui.component.mastodon_notification_item_reblogged_your_status
import dev.dimension.flare.ui.component.mastodon_notification_item_requested_follow
import dev.dimension.flare.ui.component.mastodon_notification_item_updated_status
import dev.dimension.flare.ui.component.misskey_achievement_brain_diver_description
import dev.dimension.flare.ui.component.misskey_achievement_brain_diver_title
import dev.dimension.flare.ui.component.misskey_achievement_bubble_game_double_exploding_head_description
import dev.dimension.flare.ui.component.misskey_achievement_bubble_game_double_exploding_head_title
import dev.dimension.flare.ui.component.misskey_achievement_bubble_game_exploding_head_description
import dev.dimension.flare.ui.component.misskey_achievement_bubble_game_exploding_head_title
import dev.dimension.flare.ui.component.misskey_achievement_clicked_click_here_description
import dev.dimension.flare.ui.component.misskey_achievement_clicked_click_here_title
import dev.dimension.flare.ui.component.misskey_achievement_client30min_description
import dev.dimension.flare.ui.component.misskey_achievement_client30min_title
import dev.dimension.flare.ui.component.misskey_achievement_client60min_description
import dev.dimension.flare.ui.component.misskey_achievement_client60min_title
import dev.dimension.flare.ui.component.misskey_achievement_collect_achievements30_description
import dev.dimension.flare.ui.component.misskey_achievement_collect_achievements30_title
import dev.dimension.flare.ui.component.misskey_achievement_cookie_clicked_description
import dev.dimension.flare.ui.component.misskey_achievement_cookie_clicked_title
import dev.dimension.flare.ui.component.misskey_achievement_drive_folder_circular_reference_description
import dev.dimension.flare.ui.component.misskey_achievement_drive_folder_circular_reference_title
import dev.dimension.flare.ui.component.misskey_achievement_followers1000_description
import dev.dimension.flare.ui.component.misskey_achievement_followers1000_title
import dev.dimension.flare.ui.component.misskey_achievement_followers100_description
import dev.dimension.flare.ui.component.misskey_achievement_followers100_title
import dev.dimension.flare.ui.component.misskey_achievement_followers10_description
import dev.dimension.flare.ui.component.misskey_achievement_followers10_title
import dev.dimension.flare.ui.component.misskey_achievement_followers1_description
import dev.dimension.flare.ui.component.misskey_achievement_followers1_title
import dev.dimension.flare.ui.component.misskey_achievement_followers300_description
import dev.dimension.flare.ui.component.misskey_achievement_followers300_title
import dev.dimension.flare.ui.component.misskey_achievement_followers500_description
import dev.dimension.flare.ui.component.misskey_achievement_followers500_title
import dev.dimension.flare.ui.component.misskey_achievement_followers50_description
import dev.dimension.flare.ui.component.misskey_achievement_followers50_title
import dev.dimension.flare.ui.component.misskey_achievement_following100_description
import dev.dimension.flare.ui.component.misskey_achievement_following100_title
import dev.dimension.flare.ui.component.misskey_achievement_following10_description
import dev.dimension.flare.ui.component.misskey_achievement_following10_title
import dev.dimension.flare.ui.component.misskey_achievement_following1_description
import dev.dimension.flare.ui.component.misskey_achievement_following1_title
import dev.dimension.flare.ui.component.misskey_achievement_following300_description
import dev.dimension.flare.ui.component.misskey_achievement_following300_title
import dev.dimension.flare.ui.component.misskey_achievement_following50_description
import dev.dimension.flare.ui.component.misskey_achievement_following50_title
import dev.dimension.flare.ui.component.misskey_achievement_found_treasure_description
import dev.dimension.flare.ui.component.misskey_achievement_found_treasure_title
import dev.dimension.flare.ui.component.misskey_achievement_htl20npm_description
import dev.dimension.flare.ui.component.misskey_achievement_htl20npm_title
import dev.dimension.flare.ui.component.misskey_achievement_i_love_misskey_description
import dev.dimension.flare.ui.component.misskey_achievement_i_love_misskey_title
import dev.dimension.flare.ui.component.misskey_achievement_just_plain_lucky_description
import dev.dimension.flare.ui.component.misskey_achievement_just_plain_lucky_title
import dev.dimension.flare.ui.component.misskey_achievement_logged_in_on_birthday_description
import dev.dimension.flare.ui.component.misskey_achievement_logged_in_on_birthday_title
import dev.dimension.flare.ui.component.misskey_achievement_logged_in_on_new_years_day_description
import dev.dimension.flare.ui.component.misskey_achievement_logged_in_on_new_years_day_title
import dev.dimension.flare.ui.component.misskey_achievement_login1000_description
import dev.dimension.flare.ui.component.misskey_achievement_login1000_title
import dev.dimension.flare.ui.component.misskey_achievement_login100_description
import dev.dimension.flare.ui.component.misskey_achievement_login100_title
import dev.dimension.flare.ui.component.misskey_achievement_login15_description
import dev.dimension.flare.ui.component.misskey_achievement_login15_title
import dev.dimension.flare.ui.component.misskey_achievement_login200_description
import dev.dimension.flare.ui.component.misskey_achievement_login200_title
import dev.dimension.flare.ui.component.misskey_achievement_login300_description
import dev.dimension.flare.ui.component.misskey_achievement_login300_title
import dev.dimension.flare.ui.component.misskey_achievement_login30_description
import dev.dimension.flare.ui.component.misskey_achievement_login30_title
import dev.dimension.flare.ui.component.misskey_achievement_login3_description
import dev.dimension.flare.ui.component.misskey_achievement_login3_title
import dev.dimension.flare.ui.component.misskey_achievement_login400_description
import dev.dimension.flare.ui.component.misskey_achievement_login400_title
import dev.dimension.flare.ui.component.misskey_achievement_login500_description
import dev.dimension.flare.ui.component.misskey_achievement_login500_title
import dev.dimension.flare.ui.component.misskey_achievement_login600_description
import dev.dimension.flare.ui.component.misskey_achievement_login600_title
import dev.dimension.flare.ui.component.misskey_achievement_login60_description
import dev.dimension.flare.ui.component.misskey_achievement_login60_title
import dev.dimension.flare.ui.component.misskey_achievement_login700_description
import dev.dimension.flare.ui.component.misskey_achievement_login700_title
import dev.dimension.flare.ui.component.misskey_achievement_login7_description
import dev.dimension.flare.ui.component.misskey_achievement_login7_title
import dev.dimension.flare.ui.component.misskey_achievement_login800_description
import dev.dimension.flare.ui.component.misskey_achievement_login800_title
import dev.dimension.flare.ui.component.misskey_achievement_login900_description
import dev.dimension.flare.ui.component.misskey_achievement_login900_title
import dev.dimension.flare.ui.component.misskey_achievement_marked_as_cat_description
import dev.dimension.flare.ui.component.misskey_achievement_marked_as_cat_title
import dev.dimension.flare.ui.component.misskey_achievement_my_note_favorited1_description
import dev.dimension.flare.ui.component.misskey_achievement_my_note_favorited1_title
import dev.dimension.flare.ui.component.misskey_achievement_note_clipped1_description
import dev.dimension.flare.ui.component.misskey_achievement_note_clipped1_title
import dev.dimension.flare.ui.component.misskey_achievement_note_deleted_within1min_description
import dev.dimension.flare.ui.component.misskey_achievement_note_deleted_within1min_title
import dev.dimension.flare.ui.component.misskey_achievement_note_favorited1_description
import dev.dimension.flare.ui.component.misskey_achievement_note_favorited1_title
import dev.dimension.flare.ui.component.misskey_achievement_notes100000_description
import dev.dimension.flare.ui.component.misskey_achievement_notes100000_title
import dev.dimension.flare.ui.component.misskey_achievement_notes10000_description
import dev.dimension.flare.ui.component.misskey_achievement_notes10000_title
import dev.dimension.flare.ui.component.misskey_achievement_notes1000_description
import dev.dimension.flare.ui.component.misskey_achievement_notes1000_title
import dev.dimension.flare.ui.component.misskey_achievement_notes100_description
import dev.dimension.flare.ui.component.misskey_achievement_notes100_title
import dev.dimension.flare.ui.component.misskey_achievement_notes10_description
import dev.dimension.flare.ui.component.misskey_achievement_notes10_title
import dev.dimension.flare.ui.component.misskey_achievement_notes1_description
import dev.dimension.flare.ui.component.misskey_achievement_notes1_title
import dev.dimension.flare.ui.component.misskey_achievement_notes20000_description
import dev.dimension.flare.ui.component.misskey_achievement_notes20000_title
import dev.dimension.flare.ui.component.misskey_achievement_notes30000_description
import dev.dimension.flare.ui.component.misskey_achievement_notes30000_title
import dev.dimension.flare.ui.component.misskey_achievement_notes40000_description
import dev.dimension.flare.ui.component.misskey_achievement_notes40000_title
import dev.dimension.flare.ui.component.misskey_achievement_notes50000_description
import dev.dimension.flare.ui.component.misskey_achievement_notes50000_title
import dev.dimension.flare.ui.component.misskey_achievement_notes5000_description
import dev.dimension.flare.ui.component.misskey_achievement_notes5000_title
import dev.dimension.flare.ui.component.misskey_achievement_notes500_description
import dev.dimension.flare.ui.component.misskey_achievement_notes500_title
import dev.dimension.flare.ui.component.misskey_achievement_notes60000_description
import dev.dimension.flare.ui.component.misskey_achievement_notes60000_title
import dev.dimension.flare.ui.component.misskey_achievement_notes70000_description
import dev.dimension.flare.ui.component.misskey_achievement_notes70000_title
import dev.dimension.flare.ui.component.misskey_achievement_notes80000_description
import dev.dimension.flare.ui.component.misskey_achievement_notes80000_title
import dev.dimension.flare.ui.component.misskey_achievement_notes90000_description
import dev.dimension.flare.ui.component.misskey_achievement_notes90000_title
import dev.dimension.flare.ui.component.misskey_achievement_open3windows_description
import dev.dimension.flare.ui.component.misskey_achievement_open3windows_title
import dev.dimension.flare.ui.component.misskey_achievement_output_hello_world_on_scratchpad_description
import dev.dimension.flare.ui.component.misskey_achievement_output_hello_world_on_scratchpad_title
import dev.dimension.flare.ui.component.misskey_achievement_passed_since_account_created1_description
import dev.dimension.flare.ui.component.misskey_achievement_passed_since_account_created1_title
import dev.dimension.flare.ui.component.misskey_achievement_passed_since_account_created2_description
import dev.dimension.flare.ui.component.misskey_achievement_passed_since_account_created2_title
import dev.dimension.flare.ui.component.misskey_achievement_passed_since_account_created3_description
import dev.dimension.flare.ui.component.misskey_achievement_passed_since_account_created3_title
import dev.dimension.flare.ui.component.misskey_achievement_posted_at_0min0sec_description
import dev.dimension.flare.ui.component.misskey_achievement_posted_at_0min0sec_title
import dev.dimension.flare.ui.component.misskey_achievement_posted_at_late_night_description
import dev.dimension.flare.ui.component.misskey_achievement_posted_at_late_night_title
import dev.dimension.flare.ui.component.misskey_achievement_profile_filled_description
import dev.dimension.flare.ui.component.misskey_achievement_profile_filled_title
import dev.dimension.flare.ui.component.misskey_achievement_react_without_read_description
import dev.dimension.flare.ui.component.misskey_achievement_react_without_read_title
import dev.dimension.flare.ui.component.misskey_achievement_self_quote_description
import dev.dimension.flare.ui.component.misskey_achievement_self_quote_title
import dev.dimension.flare.ui.component.misskey_achievement_set_name_to_syuilo_description
import dev.dimension.flare.ui.component.misskey_achievement_set_name_to_syuilo_title
import dev.dimension.flare.ui.component.misskey_achievement_smash_test_notification_button_description
import dev.dimension.flare.ui.component.misskey_achievement_smash_test_notification_button_title
import dev.dimension.flare.ui.component.misskey_achievement_tutorial_completed_description
import dev.dimension.flare.ui.component.misskey_achievement_tutorial_completed_title
import dev.dimension.flare.ui.component.misskey_achievement_view_achievements3min_description
import dev.dimension.flare.ui.component.misskey_achievement_view_achievements3min_title
import dev.dimension.flare.ui.component.misskey_achievement_view_instance_chart_description
import dev.dimension.flare.ui.component.misskey_achievement_view_instance_chart_title
import dev.dimension.flare.ui.component.misskey_notification_item_achievement_earned
import dev.dimension.flare.ui.component.misskey_notification_item_app
import dev.dimension.flare.ui.component.misskey_notification_item_follow_request_accepted
import dev.dimension.flare.ui.component.misskey_notification_item_followed_you
import dev.dimension.flare.ui.component.misskey_notification_item_mentioned_you
import dev.dimension.flare.ui.component.misskey_notification_item_poll_ended
import dev.dimension.flare.ui.component.misskey_notification_item_quoted_your_status
import dev.dimension.flare.ui.component.misskey_notification_item_reacted_to_your_status
import dev.dimension.flare.ui.component.misskey_notification_item_replied_to_you
import dev.dimension.flare.ui.component.misskey_notification_item_reposted_your_status
import dev.dimension.flare.ui.component.misskey_notification_item_requested_follow
import dev.dimension.flare.ui.component.misskey_notification_unknwon
import dev.dimension.flare.ui.component.notification_item_accept_follow_request
import dev.dimension.flare.ui.component.notification_item_reject_follow_request
import dev.dimension.flare.ui.component.platform.PlatformButton
import dev.dimension.flare.ui.component.platform.PlatformCard
import dev.dimension.flare.ui.component.platform.PlatformFilledTonalButton
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.vvo_notification_like
import dev.dimension.flare.ui.component.xqt_item_mention_status
import dev.dimension.flare.ui.component.xqt_item_reblogged_status
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.mapper.MisskeyAchievement
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun UiTimelineComponent(
    item: UiTimeline,
    modifier: Modifier = Modifier,
    detailStatusKey: MicroBlogKey? = null,
    horizontalPadding: Dp = screenHorizontalPadding,
) {
    val bigScreen = isBigScreen()
    Column(
        modifier = modifier,
    ) {
        item.topMessage?.let {
            TopMessageComponent(
                data = it,
                topMessageOnly = item.content == null,
                modifier =
                    Modifier
                        .padding(horizontal = horizontalPadding)
                        .let {
                            if (item.content == null) {
                                it.padding(vertical = 8.dp)
                            } else {
                                it.padding(top = 8.dp)
                            }
                        }.fillMaxWidth(),
            )
        }
        item.content?.let {
            val padding =
                if (item.topMessage == null) {
                    PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        bottom = 8.dp,
                        top = if (bigScreen) 16.dp else 8.dp,
                    )
                } else {
                    PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        bottom = 8.dp,
                        top = 8.dp,
                    )
                }
            ItemContentComponent(
                item = it,
                detailStatusKey = detailStatusKey,
                paddingValues = padding,
            )
        }
    }
}

@Composable
private fun ItemContentComponent(
    item: UiTimeline.ItemContent,
    detailStatusKey: MicroBlogKey?,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    when (item) {
        is UiTimeline.ItemContent.Status ->
            StatusContent(
                data = item,
                detailStatusKey = detailStatusKey,
                modifier =
                    modifier
                        .padding(paddingValues),
            )

        is UiTimeline.ItemContent.User -> {
            Column(
                modifier =
                    modifier
                        .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                        item.button.forEach { button ->
                            when (button) {
                                is UiTimeline.ItemContent.User.Button.AcceptFollowRequest ->
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
                                is UiTimeline.ItemContent.User.Button.RejectFollowRequest -> {
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
                                    ) {
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
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        is UiTimeline.ItemContent.UserList ->
            UserListContent(
                data = item,
                modifier =
                    modifier
                        .padding(paddingValues),
            )

        is UiTimeline.ItemContent.Feed -> {
            FeedComponent(
                data = item,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun UserListContent(
    data: UiTimeline.ItemContent.UserList,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
        val status = data.status
        if (status != null) {
            StatusContent(
                data = status,
                detailStatusKey = null,
            )
        }
    }
}

@Composable
private fun StatusContent(
    data: UiTimeline.ItemContent.Status,
    detailStatusKey: MicroBlogKey?,
    modifier: Modifier = Modifier,
) {
    CommonStatusComponent(
        item = data,
        isDetail = detailStatusKey == data.statusKey,
        modifier = modifier,
    )
}

@Composable
private fun TopMessageComponent(
    data: UiTimeline.TopMessage,
    topMessageOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val icon =
        when (data.icon) {
            UiTimeline.TopMessage.Icon.Retweet -> FontAwesomeIcons.Solid.Retweet
            UiTimeline.TopMessage.Icon.Follow -> FontAwesomeIcons.Solid.UserPlus
            UiTimeline.TopMessage.Icon.Favourite -> FontAwesomeIcons.Solid.Heart
            UiTimeline.TopMessage.Icon.Mention -> FontAwesomeIcons.Solid.At
            UiTimeline.TopMessage.Icon.Poll -> FontAwesomeIcons.Solid.SquarePollHorizontal
            UiTimeline.TopMessage.Icon.Edit -> FontAwesomeIcons.Solid.Pen
            UiTimeline.TopMessage.Icon.Info -> FontAwesomeIcons.Solid.CircleInfo
            UiTimeline.TopMessage.Icon.Reply -> FontAwesomeIcons.Solid.Reply
            UiTimeline.TopMessage.Icon.Quote -> FontAwesomeIcons.Solid.QuoteLeft
        }
    val text: String? =
        when (val type = data.type) {
            is UiTimeline.TopMessage.MessageType.Bluesky ->
                when (type) {
                    UiTimeline.TopMessage.MessageType.Bluesky.Follow ->
                        stringResource(resource = Res.string.bluesky_notification_item_followed_you)

                    UiTimeline.TopMessage.MessageType.Bluesky.Like ->
                        stringResource(
                            resource = Res.string.bluesky_notification_item_favourited_your_status,
                        )

                    UiTimeline.TopMessage.MessageType.Bluesky.Mention ->
                        stringResource(
                            resource = Res.string.bluesky_notification_item_mentioned_you,
                        )

                    UiTimeline.TopMessage.MessageType.Bluesky.Quote ->
                        stringResource(
                            resource = Res.string.bluesky_notification_item_quoted_your_status,
                        )

                    UiTimeline.TopMessage.MessageType.Bluesky.Reply ->
                        stringResource(
                            resource = Res.string.bluesky_notification_item_replied_to_you,
                        )

                    UiTimeline.TopMessage.MessageType.Bluesky.Repost ->
                        stringResource(
                            resource = Res.string.bluesky_notification_item_reblogged_your_status,
                        )

                    UiTimeline.TopMessage.MessageType.Bluesky.StarterpackJoined ->
                        stringResource(
                            resource = Res.string.bluesky_notification_item_starterpack_joined,
                        )
                    UiTimeline.TopMessage.MessageType.Bluesky.UnKnown ->
                        stringResource(
                            resource = Res.string.bluesky_notification_item_unKnown,
                        )
                }

            is UiTimeline.TopMessage.MessageType.Mastodon ->
                when (type) {
                    is UiTimeline.TopMessage.MessageType.Mastodon.Favourite ->
                        stringResource(
                            resource = Res.string.mastodon_notification_item_favourited_your_status,
                        )

                    is UiTimeline.TopMessage.MessageType.Mastodon.Follow ->
                        stringResource(
                            resource = Res.string.mastodon_notification_item_followed_you,
                        )

                    is UiTimeline.TopMessage.MessageType.Mastodon.FollowRequest ->
                        stringResource(
                            resource = Res.string.mastodon_notification_item_requested_follow,
                        )

                    is UiTimeline.TopMessage.MessageType.Mastodon.Mention ->
                        stringResource(
                            resource = Res.string.mastodon_notification_item_mentioned_you,
                        )

                    is UiTimeline.TopMessage.MessageType.Mastodon.Poll ->
                        stringResource(resource = Res.string.mastodon_notification_item_poll_ended)

                    is UiTimeline.TopMessage.MessageType.Mastodon.Reblogged ->
                        stringResource(
                            resource = Res.string.mastodon_notification_item_reblogged_your_status,
                        )

                    is UiTimeline.TopMessage.MessageType.Mastodon.Status ->
                        stringResource(
                            resource = Res.string.mastodon_notification_item_posted_status,
                        )

                    is UiTimeline.TopMessage.MessageType.Mastodon.Update ->
                        stringResource(
                            resource = Res.string.mastodon_notification_item_updated_status,
                        )

                    is UiTimeline.TopMessage.MessageType.Mastodon.UnKnown -> null
                }

            is UiTimeline.TopMessage.MessageType.Misskey ->
                when (type) {
                    is UiTimeline.TopMessage.MessageType.Misskey.AchievementEarned ->
                        stringResource(
                            resource = Res.string.misskey_notification_item_achievement_earned,
                            type.achievement?.titleResId?.let { stringResource(it) } ?: "",
                            type.achievement?.descriptionResId?.let { stringResource(it) } ?: "",
                        )

                    is UiTimeline.TopMessage.MessageType.Misskey.App ->
                        stringResource(resource = Res.string.misskey_notification_item_app)

                    is UiTimeline.TopMessage.MessageType.Misskey.Follow ->
                        stringResource(resource = Res.string.misskey_notification_item_followed_you)

                    is UiTimeline.TopMessage.MessageType.Misskey.FollowRequestAccepted ->
                        stringResource(
                            resource = Res.string.misskey_notification_item_follow_request_accepted,
                        )

                    is UiTimeline.TopMessage.MessageType.Misskey.Mention ->
                        stringResource(
                            resource = Res.string.misskey_notification_item_mentioned_you,
                        )

                    is UiTimeline.TopMessage.MessageType.Misskey.PollEnded ->
                        stringResource(
                            resource = Res.string.misskey_notification_item_poll_ended,
                        )

                    is UiTimeline.TopMessage.MessageType.Misskey.Quote ->
                        stringResource(
                            resource = Res.string.misskey_notification_item_quoted_your_status,
                        )

                    is UiTimeline.TopMessage.MessageType.Misskey.Reaction ->
                        stringResource(
                            resource = Res.string.misskey_notification_item_reacted_to_your_status,
                        )

                    is UiTimeline.TopMessage.MessageType.Misskey.ReceiveFollowRequest ->
                        stringResource(
                            resource = Res.string.misskey_notification_item_requested_follow,
                        )

                    is UiTimeline.TopMessage.MessageType.Misskey.Renote ->
                        stringResource(
                            resource = Res.string.misskey_notification_item_reposted_your_status,
                        )

                    is UiTimeline.TopMessage.MessageType.Misskey.Reply ->
                        stringResource(
                            resource = Res.string.misskey_notification_item_replied_to_you,
                        )

                    is UiTimeline.TopMessage.MessageType.Misskey.UnKnown ->
                        stringResource(
                            resource = Res.string.misskey_notification_unknwon,
                            type.type,
                        )
                }

            is UiTimeline.TopMessage.MessageType.VVO ->
                when (type) {
                    is UiTimeline.TopMessage.MessageType.VVO.Custom -> type.message
                    UiTimeline.TopMessage.MessageType.VVO.Like ->
                        stringResource(resource = Res.string.vvo_notification_like)
                }

            is UiTimeline.TopMessage.MessageType.XQT ->
                when (type) {
                    is UiTimeline.TopMessage.MessageType.XQT.Custom -> type.message
                    UiTimeline.TopMessage.MessageType.XQT.Mention ->
                        stringResource(resource = Res.string.xqt_item_mention_status)

                    UiTimeline.TopMessage.MessageType.XQT.Retweet ->
                        stringResource(resource = Res.string.xqt_item_reblogged_status)
                }
        }

    if (text != null) {
        StatusRetweetHeaderComponent(
            icon = icon,
            user = data.user,
            text = text,
            textStyle =
                if (topMessageOnly) {
                    PlatformTextStyle.current
                } else {
                    PlatformTheme.typography.caption
                },
            modifier =
                Modifier
                    .let {
                        if (topMessageOnly) {
                            it
                        } else {
                            it.alpha(MediumAlpha)
                        }
                    }.clickable {
                        data.onClicked.invoke(
                            ClickContext(
                                launcher = {
                                    uriHandler.openUri(it)
                                },
                            ),
                        )
                    }.then(modifier),
        )
    }
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
