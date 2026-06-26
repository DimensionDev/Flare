package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.all_rss_feeds_title
import dev.dimension.flare.antenna_title
import dev.dimension.flare.bluesky_login_2fa
import dev.dimension.flare.bluesky_login_oauth_button
import dev.dimension.flare.bluesky_login_password
import dev.dimension.flare.bluesky_login_use_password_button
import dev.dimension.flare.bluesky_login_username
import dev.dimension.flare.cancel
import dev.dimension.flare.channel_title
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.appearance.withPatch
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.dm_list_title
import dev.dimension.flare.edit_tab_name
import dev.dimension.flare.edit_tab_name_placeholder
import dev.dimension.flare.edit_tab_title
import dev.dimension.flare.fanbox_recommended_creators_title
import dev.dimension.flare.fanbox_supported_title
import dev.dimension.flare.home_tab_bookmarks_title
import dev.dimension.flare.home_tab_discover_title
import dev.dimension.flare.home_tab_favorite_title
import dev.dimension.flare.home_tab_featured_title
import dev.dimension.flare.home_tab_feeds_title
import dev.dimension.flare.home_tab_home_title
import dev.dimension.flare.home_tab_list_title
import dev.dimension.flare.home_tab_me_title
import dev.dimension.flare.home_tab_notifications_title
import dev.dimension.flare.illustrations_title
import dev.dimension.flare.liked_title
import dev.dimension.flare.manga_title
import dev.dimension.flare.mastodon_tab_local_title
import dev.dimension.flare.mastodon_tab_public_title
import dev.dimension.flare.misskey_channel_tab_following
import dev.dimension.flare.mixed_timeline_title
import dev.dimension.flare.ok
import dev.dimension.flare.pixiv_private_bookmarks_title
import dev.dimension.flare.pixiv_private_following_title
import dev.dimension.flare.pixiv_ranking_day_female_title
import dev.dimension.flare.pixiv_ranking_day_male_title
import dev.dimension.flare.pixiv_ranking_day_manga_title
import dev.dimension.flare.pixiv_ranking_month_title
import dev.dimension.flare.pixiv_ranking_week_original_title
import dev.dimension.flare.pixiv_ranking_week_rookie_title
import dev.dimension.flare.pixiv_ranking_week_title
import dev.dimension.flare.posts_title
import dev.dimension.flare.profile_tab_media
import dev.dimension.flare.profile_tab_timeline_with_reply
import dev.dimension.flare.rss_title
import dev.dimension.flare.service_select_next_button
import dev.dimension.flare.settings_title
import dev.dimension.flare.social_title
import dev.dimension.flare.tab_settings_default
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.EditTabPresenter
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EditTabDialog(
    visible: Boolean,
    tabItem: UiTimelineTabItem,
    onDismissRequest: () -> Unit,
    onConfirm: (UiTimelineTabItem) -> Unit,
    titleAndIconOnly: Boolean = false,
) {
    val appearance = LocalTimelineAppearance.current
    val state by producePresenter(key = "EditTabSheet_$tabItem") {
        presenter(tabItem = tabItem, appearance = appearance)
    }
    ContentDialog(
        visible = visible,
        title = stringResource(Res.string.edit_tab_title),
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    if (state.canConfirm) {
                        onConfirm(
                            tabItem.withPresentationOverrides(
                                title = state.text.text.toString(),
                                icon = state.icon,
                                appearancePatch = state.appearancePatch,
                                enabled = state.enabled,
                                filterConfig = state.filterConfig,
                            ),
                        )
                    }
                }

                ContentDialogButton.Secondary -> {
                    Unit
                }

                ContentDialogButton.Close -> {
                    onDismissRequest()
                }
            }
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                TimelinePresentationEditor(
                    text = state.text,
                    icon = state.icon,
                    availableIcons = state.availableIcons,
                    showIconPicker = state.showIconPicker,
                    onShowIconPickerChange = state::setShowIconPicker,
                    withAvatar = state.withAvatar,
                    canUseAvatar = !titleAndIconOnly && state.canUseAvatar,
                    onWithAvatarChange = state::setWithAvatar,
                    enabled = state.enabled,
                    onEnabledChange = state::setEnabled,
                    filterConfig = state.filterConfig,
                    onFilterConfigChange = state::setFilterConfig,
                    timelineAppearance = state.timelineAppearance,
                    appearancePatch = state.appearancePatch,
                    onAppearancePatchChange = state::setAppearancePatch,
                    onIconChange = state::setIcon,
                    showEnabled = !titleAndIconOnly && !tabItem.isSystemHomeMixedTimeline,
                    showAppearanceOverrides = !titleAndIconOnly,
                    modifier = Modifier.fillMaxWidth(),
                    header = {
                        Text(text = stringResource(Res.string.edit_tab_name))
                    },
                    placeholder = {
                        Text(text = stringResource(Res.string.edit_tab_name_placeholder))
                    },
                )
            }
        },
    )
}

@Composable
private fun presenter(
    tabItem: UiTimelineTabItem,
    appearance: TimelineAppearance,
) = run {
    val text = rememberTextFieldState()
    val state =
        remember(tabItem) {
            EditTabPresenter(tabItem) { string ->
                getString(string.desktopStringResource)
            }
        }.invoke()
    var showIconPicker by remember { mutableStateOf(false) }
    var enabled by remember(tabItem) { mutableStateOf(tabItem.enabled) }
    var filterConfig by remember(tabItem) { mutableStateOf(tabItem.filterConfig) }
    var appearancePatch by remember(tabItem) { mutableStateOf(tabItem.appearancePatch ?: AppearancePatch.EMPTY) }
    val timelineAppearance by remember {
        derivedStateOf {
            appearance.withPatch(appearancePatch)
        }
    }
    state.initialText.onSuccess {
        LaunchedEffect(it) {
            text.edit {
                replace(0, length, it)
            }
        }
    }
    object : EditTabPresenter.State by state {
        val text = text
        val canConfirm = text.text.isNotEmpty()
        val showIconPicker = showIconPicker
        val enabled = enabled
        val filterConfig = filterConfig
        val appearancePatch = appearancePatch
        val timelineAppearance = timelineAppearance

        fun setShowIconPicker(value: Boolean) {
            showIconPicker = value
        }

        fun setEnabled(value: Boolean) {
            enabled = value
        }

        fun setFilterConfig(value: TimelineFilterConfig) {
            filterConfig = value
        }

        fun setAppearancePatch(value: AppearancePatch) {
            appearancePatch = value
        }
    }
}

private val UiStrings.desktopStringResource: StringResource
    get() =
        when (this) {
            UiStrings.Default -> Res.string.tab_settings_default
            UiStrings.Home -> Res.string.home_tab_home_title
            UiStrings.Notifications -> Res.string.home_tab_notifications_title
            UiStrings.Discover -> Res.string.home_tab_discover_title
            UiStrings.Me -> Res.string.home_tab_me_title
            UiStrings.Settings -> Res.string.settings_title
            UiStrings.MastodonLocal -> Res.string.mastodon_tab_local_title
            UiStrings.MastodonPublic -> Res.string.mastodon_tab_public_title
            UiStrings.Featured -> Res.string.home_tab_featured_title
            UiStrings.Bookmark -> Res.string.home_tab_bookmarks_title
            UiStrings.Favourite -> Res.string.home_tab_favorite_title
            UiStrings.List -> Res.string.home_tab_list_title
            UiStrings.Feeds -> Res.string.home_tab_feeds_title
            UiStrings.DirectMessage -> Res.string.dm_list_title
            UiStrings.Rss -> Res.string.rss_title
            UiStrings.Social -> Res.string.social_title
            UiStrings.Antenna -> Res.string.antenna_title
            UiStrings.MixedTimeline -> Res.string.mixed_timeline_title
            UiStrings.Liked -> Res.string.liked_title
            UiStrings.AllRssFeeds -> Res.string.all_rss_feeds_title
            UiStrings.Posts -> Res.string.posts_title
            UiStrings.PostsWithReplies -> Res.string.profile_tab_timeline_with_reply
            UiStrings.Media -> Res.string.profile_tab_media
            UiStrings.Channel -> Res.string.channel_title
            UiStrings.Login -> Res.string.ok
            UiStrings.Verify -> Res.string.ok
            UiStrings.Cancel -> Res.string.cancel
            UiStrings.Next -> Res.string.service_select_next_button
            UiStrings.Username -> Res.string.bluesky_login_username
            UiStrings.Password -> Res.string.bluesky_login_password
            UiStrings.Otp -> Res.string.bluesky_login_2fa
            UiStrings.OAuthLogin -> Res.string.bluesky_login_oauth_button
            UiStrings.PasswordLogin -> Res.string.bluesky_login_use_password_button
            UiStrings.QrConnect -> Res.string.ok
            UiStrings.CredentialImport -> Res.string.ok
            UiStrings.ExternalSigner -> Res.string.ok
            UiStrings.WebCookieLogin -> Res.string.ok
            UiStrings.NostrLoginAccount -> Res.string.ok
            UiStrings.Following -> Res.string.misskey_channel_tab_following
            UiStrings.PixivRankingWeek -> Res.string.pixiv_ranking_week_title
            UiStrings.PixivRankingMonth -> Res.string.pixiv_ranking_month_title
            UiStrings.PixivRankingDayMale -> Res.string.pixiv_ranking_day_male_title
            UiStrings.PixivRankingDayFemale -> Res.string.pixiv_ranking_day_female_title
            UiStrings.PixivRankingWeekOriginal -> Res.string.pixiv_ranking_week_original_title
            UiStrings.PixivRankingWeekRookie -> Res.string.pixiv_ranking_week_rookie_title
            UiStrings.PixivRankingDayManga -> Res.string.pixiv_ranking_day_manga_title
            UiStrings.Illustrations -> Res.string.illustrations_title
            UiStrings.Manga -> Res.string.manga_title
            UiStrings.FanboxSupported -> Res.string.fanbox_supported_title
            UiStrings.FanboxRecommendedCreators -> Res.string.fanbox_recommended_creators_title
            UiStrings.PixivPrivateFollowing -> Res.string.pixiv_private_following_title
            UiStrings.PixivPrivateBookmarks -> Res.string.pixiv_private_bookmarks_title
        }
