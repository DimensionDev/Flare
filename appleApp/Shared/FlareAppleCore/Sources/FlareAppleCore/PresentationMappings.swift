import Foundation
import KotlinSharedUI
import SwiftUI

public func localizedPresentationString(
    _ key: String,
    fallback: String,
    arguments: [String] = []
) -> String {
    FlareAppleCoreLocalization.string(key, fallback: fallback, arguments: arguments)
}

public extension UiTimelineV2 {
    var timelineContentPost: UiTimelineV2.Post? {
        switch onEnum(of: self) {
        case .post(let post):
            return post
        case .timelinePostItem(let item):
            return item.presentation.repost ?? item.post
        default:
            return nil
        }
    }
}

public extension UiText {
    var text: String {
        switch onEnum(of: self) {
        case .localized(let localized):
            localized.string.text
        case .raw(let raw):
            raw.string
        }
    }
}

public extension UiStrings {
    var text: String {
        switch self {
        case .home:
            localizedPresentationString("home_tab_home_title", fallback: "Home")
        case .notifications:
            localizedPresentationString("home_tab_notifications_title", fallback: "Notifications")
        case .discover:
            localizedPresentationString("discover_title", fallback: "Discover")
        case .me:
            localizedPresentationString("home_tab_me_title", fallback: "Me")
        case .settings:
            localizedPresentationString("settings_title", fallback: "Settings")
        case .mastodonLocal:
            localizedPresentationString("mastodon_local_timeline", fallback: "Local")
        case .mastodonPublic:
            localizedPresentationString("mastodon_tab_public_title", fallback: "Public")
        case .featured:
            localizedPresentationString("home_tab_featured_title", fallback: "Featured")
        case .bookmark:
            localizedPresentationString("home_tab_bookmarks_title", fallback: "Bookmarks")
        case .favourite:
            localizedPresentationString("home_tab_favorite_title", fallback: "Favorites")
        case .list:
            localizedPresentationString("all_lists_title", fallback: "Lists")
        case .feeds:
            localizedPresentationString("bluesky_feeds_title", fallback: "Feeds")
        case .directMessage:
            localizedPresentationString("direct_messages_title", fallback: "Direct Messages")
        case .rss:
            localizedPresentationString("rss_title", fallback: "Subscriptions")
        case .antenna:
            localizedPresentationString("antenna_title", fallback: "Antennas")
        case .mixedTimeline:
            localizedPresentationString("mixed_timeline_title", fallback: "Mixed")
        case .social:
            localizedPresentationString("social_title", fallback: "Social")
        case .liked:
            localizedPresentationString("liked_tab_title", fallback: "Liked")
        case .allRssFeeds:
            localizedPresentationString("all_rss_feeds_title", fallback: "All Subscriptions")
        case .posts:
            localizedPresentationString("local_history_status", fallback: "Posts")
        case .channel:
            localizedPresentationString("channel_title", fallback: "Channel")
        case .default:
            localizedPresentationString("Default", fallback: "Default")
        case .login:
            localizedPresentationString("Login", fallback: "Log in")
        case .verify:
            localizedPresentationString("verify_button", fallback: "Verify")
        case .cancel:
            localizedPresentationString("Cancel", fallback: "Cancel")
        case .next:
            localizedPresentationString("service_select_next_button", fallback: "Next")
        case .username:
            localizedPresentationString("bluesky_login_username_hint", fallback: "Username")
        case .password:
            localizedPresentationString("bluesky_login_password_hint", fallback: "Password")
        case .otp:
            localizedPresentationString("bluesky_login_auth_factor_token_hint", fallback: "One-time Password")
        case .oauthLogin:
            localizedPresentationString("bluesky_login_oauth_button", fallback: "OAuth Login")
        case .blueskyFixDelegationScopes:
            localizedPresentationString(
                "bluesky_login_fix_delegation_scopes_button",
                fallback: "Fix Tranquil permissions"
            )
        case .passwordLogin:
            localizedPresentationString("bluesky_login_use_password_button", fallback: "Password Login")
        case .qrConnect:
            localizedPresentationString("nostr_login_qr_button", fallback: "QR Connect")
        case .credentialImport:
            localizedPresentationString("nostr_login_title", fallback: "Credential Import")
        case .externalSigner:
            localizedPresentationString("nostr_login_amber_button", fallback: "External Signer")
        case .webCookieLogin:
            localizedPresentationString("Login", fallback: "Log in")
        case .nostrLoginAccount:
            localizedPresentationString("nostr_login_account_hint", fallback: "Nostr Account")
        case .pixivRankingWeek:
            localizedPresentationString("pixiv_ranking_week_title", fallback: "Weekly Ranking")
        case .pixivRankingMonth:
            localizedPresentationString("pixiv_ranking_month_title", fallback: "Monthly Ranking")
        case .pixivRankingDayMale:
            localizedPresentationString("pixiv_ranking_day_male_title", fallback: "Male Ranking")
        case .pixivRankingDayFemale:
            localizedPresentationString("pixiv_ranking_day_female_title", fallback: "Female Ranking")
        case .pixivRankingWeekOriginal:
            localizedPresentationString("pixiv_ranking_week_original_title", fallback: "Original Ranking")
        case .pixivRankingWeekRookie:
            localizedPresentationString("pixiv_ranking_week_rookie_title", fallback: "Rookie Ranking")
        case .pixivRankingDayManga:
            localizedPresentationString("pixiv_ranking_day_manga_title", fallback: "Manga Ranking")
        case .pixivPrivateFollowing:
            localizedPresentationString("pixiv_private_following_title", fallback: "Private Following")
        case .pixivPrivateBookmarks, .pixivPrivateFavourites:
            localizedPresentationString("pixiv_private_favourites_title", fallback: "Private Favorites")
        case .illustrations:
            localizedPresentationString("illustrations_title", fallback: "Illustrations")
        case .manga:
            localizedPresentationString("manga_title", fallback: "Manga")
        case .fanboxSupported:
            localizedPresentationString("fanbox_supported_title", fallback: "Supporting")
        case .fanboxRecommendedCreators:
            localizedPresentationString("fanbox_recommended_creators_title", fallback: "Recommended creators")
        case .following:
            localizedPresentationString("matrix_following", fallback: "Following")
        case .postsWithReplies:
            localizedPresentationString("posts_with_replies_title", fallback: "Posts & Replies")
        case .highlights:
            localizedPresentationString("profile_tab_highlights", fallback: "Highlights")
        case .media:
            localizedPresentationString("appearance_media_group_title", fallback: "Media")
        }
    }
}

public extension ActionMenuItemText {
    var resolvedString: String {
        switch onEnum(of: self) {
        case .raw(let raw):
            raw.text
        case .localized(let localized):
            switch localized.type {
            case .like:
                localizedPresentationString("like", fallback: "like")
            case .unlike:
                localizedPresentationString("unlike", fallback: "Unlike")
            case .retweet:
                localizedPresentationString("Repost", fallback: "Retweet")
            case .unretweet:
                localizedPresentationString("retweet_remove", fallback: "Remove retweet")
            case .reply:
                localizedPresentationString("compose_title_reply", fallback: "compose_title_reply")
            case .comment:
                localizedPresentationString("comment", fallback: "comment")
            case .quote:
                localizedPresentationString("compose_title_quote", fallback: "compose_title_quote")
            case .bookmark:
                localizedPresentationString("bookmark_add", fallback: "Add bookmark")
            case .unbookmark:
                localizedPresentationString("bookmark_remove", fallback: "Remove bookmark")
            case .more:
                localizedPresentationString("more", fallback: "more")
            case .delete:
                localizedPresentationString("delete", fallback: "delete")
            case .report:
                localizedPresentationString("bluesky_report", fallback: "bluesky_report")
            case .react:
                localizedPresentationString("reaction_add", fallback: "Add reaction")
            case .share:
                localizedPresentationString("fx_share", fallback: "fx_share")
            case .fxShare:
                localizedPresentationString("fx_share", fallback: "FX Share")
            case .unReact:
                localizedPresentationString("reaction_remove", fallback: "Remove reaction")
            case .editUserList:
                localizedPresentationString("edit_user_in_list", fallback: "Edit user in list")
            case .sendMessage:
                localizedPresentationString("send_message", fallback: "Send message")
            case .mute:
                localizedPresentationString("mute", fallback: "Mute")
            case .unMute:
                localizedPresentationString("unmute", fallback: "Unmute")
            case .block:
                localizedPresentationString("block", fallback: "Block")
            case .unBlock:
                localizedPresentationString("unblock", fallback: "Unblock")
            case .blockWithHandleParameter:
                localizedPresentationString(
                    "block_user_with_handle %@",
                    fallback: "Block %@",
                    arguments: [localized.parameters.first ?? ""]
                )
            case .muteWithHandleParameter:
                localizedPresentationString(
                    "mute_user_with_handle %@",
                    fallback: "Mute %@",
                    arguments: [localized.parameters.first ?? ""]
                )
            case .acceptFollowRequest:
                localizedPresentationString("accept_follow_request", fallback: "Accept follow request")
            case .rejectFollowRequest:
                localizedPresentationString("reject_follow_request", fallback: "Reject follow request")
            case .retryTranslation:
                localizedPresentationString("Retry translation", fallback: "Retry translation")
            case .translate:
                localizedPresentationString("Translate", fallback: "Translate")
            case .showOriginal:
                localizedPresentationString("Show original", fallback: "Show original")
            case .favorite:
                localizedPresentationString("Favourite", fallback: "Favourite")
            case .unFavorite:
                localizedPresentationString("misskey_channel_unfavorite", fallback: "misskey_channel_unfavorite")
            }
        }
    }
}

public extension UiIcon {
    var image: Image {
        Image(fontAwesome: fontAwesomeIcon)
    }

    var fontAwesomeIcon: FontAwesomeIcon {
        switch self {
        case .home:
            .house
        case .notification:
            .bell
        case .search:
            .magnifyingGlass
        case .profile:
            .circleUser
        case .settings:
            .gear
        case .local:
            .users
        case .world:
            .globe
        case .featured:
            .rectangleList
        case .bookmark:
            .bookmark
        case .unbookmark:
            .bookmarkFill
        case .heart, .like:
            .heart
        case .unlike:
            .heartFill
        case .twitter:
            .twitter
        case .x:
            .xTwitter
        case .mastodon:
            .mastodon
        case .misskey:
            .misskey
        case .bluesky:
            .bluesky
        case .nostr:
            .nostr
        case .weibo:
            .weibo
        case .pixiv:
            .pixiv
        case .fanbox:
            .pixiv
        case .list:
            .list
        case .feeds, .rss:
            .squareRss
        case .messages, .chatMessage:
            .message
        case .channel:
            .tv
        case .retweet, .unretweet:
            .retweet
        case .reply, .quote:
            .reply
        case .comment:
            .commentDots
        case .more:
            .ellipsis
        case .moreVerticel:
            .ellipsisVertical
        case .delete:
            .trash
        case .report, .info:
            .circleInfo
        case .eye:
            .eye
        case .react:
            .plus
        case .unReact:
            .minus
        case .share:
            .shareNodes
        case .mute, .unMute:
            .volumeXmark
        case .block, .unBlock:
            .userSlash
        case .follow:
            .userPlus
        case .favourite:
            .starFill
        case .unFavourite:
            .star
        case .mention:
            .at
        case .poll:
            .squarePollHorizontal
        case .edit:
            .pen
        case .pin:
            .thumbtack
        case .check:
            .check
        case .translate:
            .language
        }
    }
}
