package dev.dimension.flare.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.model.appearance.AppearancePatch
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.appearance.withPatch
import dev.dimension.flare.data.model.tab.TimelineFilterConfig
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.model.tab.isSystemHomeMixedTimeline
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.platform.LocalWindowSizeClass
import dev.dimension.flare.ui.component.platform.WindowSizeClass
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun EditTabDialog(
    tabItem: UiTimelineTabItem,
    onDismissRequest: () -> Unit,
    onConfirm: (UiTimelineTabItem) -> Unit,
    titleAndIconOnly: Boolean = false,
) {
    val appearance = LocalTimelineAppearance.current
    val context = LocalContext.current
    val state by producePresenter(key = "EditTabSheet_$tabItem") {
        presenter(tabItem = tabItem, appearance = appearance, context = context)
    }
    CompositionLocalProvider(
        LocalWindowSizeClass provides WindowSizeClass.Compact,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(
                    enabled = state.canConfirm,
                    onClick = {
                        onConfirm(
                            tabItem.withPresentationOverrides(
                                title = state.text.text.toString(),
                                icon = state.icon,
                                appearancePatch = state.appearancePatch,
                                enabled = state.enabled,
                                filterConfig = state.filterConfig,
                            ),
                        )
                    },
                ) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
            text = {
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 560.dp)
                            .verticalScroll(rememberScrollState()),
                )
            },
            title = {
                Text(text = stringResource(id = R.string.edit_tab_title))
            },
        )
    }
}

@Composable
private fun presenter(
    tabItem: UiTimelineTabItem,
    appearance: TimelineAppearance,
    context: Context,
) = run {
    val text = rememberTextFieldState()
    val state =
        remember(tabItem, context) {
            EditTabPresenter(tabItem) { string ->
                context.getString(string.androidStringRes)
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

private val UiStrings.androidStringRes: Int
    get() =
        when (this) {
            UiStrings.Default -> R.string.tab_settings_default
            UiStrings.Home -> R.string.home_tab_home_title
            UiStrings.Notifications -> R.string.home_tab_notifications_title
            UiStrings.Discover -> R.string.home_tab_discover_title
            UiStrings.Me -> R.string.home_tab_me_title
            UiStrings.Settings -> R.string.settings_title
            UiStrings.MastodonLocal -> R.string.mastodon_tab_local_title
            UiStrings.MastodonPublic -> R.string.mastodon_tab_public_title
            UiStrings.Featured -> R.string.home_tab_featured_title
            UiStrings.Bookmark -> R.string.home_tab_bookmarks_title
            UiStrings.Favourite -> R.string.home_tab_favorite_title
            UiStrings.List -> R.string.home_tab_list_title
            UiStrings.Feeds -> R.string.home_tab_feeds_title
            UiStrings.DirectMessage -> R.string.dm_list_title
            UiStrings.Rss -> R.string.rss_title
            UiStrings.Social -> R.string.social_title
            UiStrings.Antenna -> R.string.antenna_title
            UiStrings.MixedTimeline -> R.string.home_tab_mixed_timeline_title
            UiStrings.Liked -> R.string.liked_title
            UiStrings.AllRssFeeds -> R.string.all_rss_feeds_title
            UiStrings.Posts -> R.string.posts_title
            UiStrings.PostsWithReplies -> R.string.profile_tab_timeline_with_reply
            UiStrings.Media -> R.string.profile_tab_media
            UiStrings.Channel -> R.string.channel_title
            UiStrings.Login -> R.string.login_button
            UiStrings.Verify -> R.string.login_button
            UiStrings.Cancel -> R.string.navigate_back
            UiStrings.Next -> R.string.service_select_next_button
            UiStrings.Username -> R.string.bluesky_login_username_hint
            UiStrings.Password -> R.string.bluesky_login_password_hint
            UiStrings.Otp -> R.string.bluesky_login_auth_factor_token_hint
            UiStrings.OAuthLogin -> R.string.bluesky_login_oauth_button
            UiStrings.PasswordLogin -> R.string.bluesky_login_use_password_button
            UiStrings.QrConnect -> R.string.login_button
            UiStrings.CredentialImport -> R.string.login_button
            UiStrings.ExternalSigner -> R.string.login_button
            UiStrings.WebCookieLogin -> R.string.login_button
            UiStrings.NostrLoginAccount -> R.string.login_button
            UiStrings.Following -> R.string.following_title
            UiStrings.PixivRankingWeek -> R.string.pixiv_ranking_week_title
            UiStrings.PixivRankingMonth -> R.string.pixiv_ranking_month_title
            UiStrings.PixivRankingDayMale -> R.string.pixiv_ranking_day_male_title
            UiStrings.PixivRankingDayFemale -> R.string.pixiv_ranking_day_female_title
            UiStrings.PixivRankingWeekOriginal -> R.string.pixiv_ranking_week_original_title
            UiStrings.PixivRankingWeekRookie -> R.string.pixiv_ranking_week_rookie_title
            UiStrings.PixivRankingDayManga -> R.string.pixiv_ranking_day_manga_title
            UiStrings.Illustrations -> R.string.illustrations_title
            UiStrings.Manga -> R.string.manga_title
            UiStrings.FanboxSupported -> R.string.fanbox_supported_title
            UiStrings.FanboxRecommendedCreators -> R.string.fanbox_recommended_creators_title
            UiStrings.PixivPrivateFollowing -> R.string.pixiv_private_following_title
            UiStrings.PixivPrivateBookmarks -> R.string.pixiv_private_bookmarks_title
        }
