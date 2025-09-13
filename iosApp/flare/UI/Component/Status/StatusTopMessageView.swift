import SwiftUI
import KotlinSharedUI
import Awesome
import MarkdownUI

struct StatusTopMessageView: View {
    let topMessage: UiTimeline.TopMessage
    var body: some View {
        HStack {
            topMessage.icon.awesomeImage.image
                .foregroundColor(.label)
            if let user = topMessage.user {
                RichText(text: user.name)
                    .lineLimit(1)
                    .markdownTextStyle(\.text) {
                        FontSize(12)
                    }
            }
            if let text = topMessage.type.localizedText {
                Text(text)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .font(.caption)
    }
}

extension UiTimeline.TopMessageIcon {
    var awesomeImage: some Amazing {
        switch self {
        case .retweet:
            return Awesome.Classic.Solid.retweet
        case .follow:
            return Awesome.Classic.Solid.userPlus
        case .favourite:
            return Awesome.Classic.Solid.heart
        case .mention:
            return Awesome.Classic.Solid.at
        case .poll:
            return Awesome.Classic.Solid.squarePollHorizontal
        case .edit:
            return Awesome.Classic.Solid.pen
        case .info:
            return Awesome.Classic.Solid.circleInfo
        case .reply:
            return Awesome.Classic.Solid.reply
        case .quote:
            return Awesome.Classic.Solid.quoteLeft
        case .pin:
            return Awesome.Classic.Solid.thumbtack
        }
    }
}


extension UiTimeline.TopMessageMessageType {
    var localizedText: String? {
        switch onEnum(of: self) {
        case .bluesky(let data):
            switch onEnum(of: data) {
            case .follow: String(localized: "bluesky_notification_follow")
            case .like: String(localized: "bluesky_notification_like")
            case .mention: String(localized: "bluesky_notification_mention")
            case .quote: String(localized: "bluesky_notification_quote")
            case .reply: String(localized: "bluesky_notification_reply")
            case .repost: String(localized: "bluesky_notification_repost")
            case .unKnown: String(localized: "bluesky_notification_unKnown")
            case .starterpackJoined: String(localized: "bluesky_notification_starterpackJoined")
            case .pinned: String(localized: "bluesky_notification_item_pin")
            }
        case .mastodon(let data):
            switch onEnum(of: data) {
            case .favourite: String(localized: "mastodon_notification_favourite")
            case .follow: String(localized: "mastodon_notification_follow")
            case .followRequest: String(localized: "mastodon_notification_follow_request")
            case .mention: String(localized: "mastodon_notification_mention")
            case .poll: String(localized: "mastodon_notification_poll")
            case .reblogged: String(localized: "mastodon_notification_reblog")
            case .status: String(localized: "mastodon_notification_status")
            case .update: String(localized: "mastodon_notification_update")
            case .pinned: String(localized: "mastodon_item_pinned")
            case .unKnown: nil
            }
        case .misskey(let data):
            switch onEnum(of: data) {
            case .achievementEarned(let achive):
                String(format: NSLocalizedString("misskey_notification_achievement_earned", comment: ""), String(localized: achive.achievement?.titleKey ?? ""), String(localized: achive.achievement?.descriptionKey ?? ""))
            case .app: String(localized: "misskey_notification_app")
            case .follow: String(localized: "misskey_notification_follow")
            case .followRequestAccepted: String(localized: "misskey_notification_follow_request_accepted")
            case .mention: String(localized: "misskey_notification_mention")
            case .pollEnded: String(localized: "misskey_notification_poll_ended")
            case .quote: String(localized: "misskey_notification_quote")
            case .reaction: String(localized: "misskey_notification_reaction")
            case .receiveFollowRequest: String(localized: "misskey_notification_receive_follow_request")
            case .renote: String(localized: "misskey_notification_renote")
            case .reply: String(localized: "misskey_notification_reply")
            case .pinned: String(localized: "mastodon_item_pinned")
            case .unKnown(let type): String(format: NSLocalizedString("misskey_notification_unknwon", comment: ""), type.type)
            }
        case .vVO(let data):
            switch onEnum(of: data) {
            case .custom(let message): message.message
            case .like: String(localized: "vvo_notification_like")
            }
        case .xQT(let data):
            switch onEnum(of: data) {
            case .custom(let message): message.message
            case .mention: String(localized: "xqt_notification_mention")
            case .retweet: String(localized: "xqt_notification_retweet")
            }
        }
    }
}


extension MisskeyAchievement {
    var titleKey: LocalizedStringResource {
        switch self {
        case .notes1:      return "misskey_achievement_notes1_title"
        case .notes10:     return "misskey_achievement_notes10_title"
        case .notes100:    return "misskey_achievement_notes100_title"
        case .notes500:    return "misskey_achievement_notes500_title"
        case .notes1000:   return "misskey_achievement_notes1000_title"
        case .notes5000:   return "misskey_achievement_notes5000_title"
        case .notes10000:  return "misskey_achievement_notes10000_title"
        case .notes20000:  return "misskey_achievement_notes20000_title"
        case .notes30000:  return "misskey_achievement_notes30000_title"
        case .notes40000:  return "misskey_achievement_notes40000_title"
        case .notes50000:  return "misskey_achievement_notes50000_title"
        case .notes60000:  return "misskey_achievement_notes60000_title"
        case .notes70000:  return "misskey_achievement_notes70000_title"
        case .notes80000:  return "misskey_achievement_notes80000_title"
        case .notes90000:  return "misskey_achievement_notes90000_title"
        case .notes100000: return "misskey_achievement_notes100000_title"

        case .login3:    return "misskey_achievement_login3_title"
        case .login7:    return "misskey_achievement_login7_title"
        case .login15:   return "misskey_achievement_login15_title"
        case .login30:   return "misskey_achievement_login30_title"
        case .login60:   return "misskey_achievement_login60_title"
        case .login100:  return "misskey_achievement_login100_title"
        case .login200:  return "misskey_achievement_login200_title"
        case .login300:  return "misskey_achievement_login300_title"
        case .login400:  return "misskey_achievement_login400_title"
        case .login500:  return "misskey_achievement_login500_title"
        case .login600:  return "misskey_achievement_login600_title"
        case .login700:  return "misskey_achievement_login700_title"
        case .login800:  return "misskey_achievement_login800_title"
        case .login900:  return "misskey_achievement_login900_title"
        case .login1000: return "misskey_achievement_login1000_title"

        case .noteClipped1:          return "misskey_achievement_note_clipped1_title"
        case .noteFavorited1:        return "misskey_achievement_note_favorited1_title"
        case .myNoteFavorited1:      return "misskey_achievement_my_note_favorited1_title"
        case .profileFilled:         return "misskey_achievement_profile_filled_title"
        case .markedAsCat:           return "misskey_achievement_marked_as_cat_title"
        case .following1:            return "misskey_achievement_following1_title"
        case .following10:           return "misskey_achievement_following10_title"
        case .following50:           return "misskey_achievement_following50_title"
        case .following100:          return "misskey_achievement_following100_title"
        case .following300:          return "misskey_achievement_following300_title"
        case .followers1:            return "misskey_achievement_followers1_title"
        case .followers10:           return "misskey_achievement_followers10_title"
        case .followers50:           return "misskey_achievement_followers50_title"
        case .followers100:          return "misskey_achievement_followers100_title"
        case .followers300:          return "misskey_achievement_followers300_title"
        case .followers500:          return "misskey_achievement_followers500_title"
        case .followers1000:         return "misskey_achievement_followers1000_title"
        case .collectAchievements30: return "misskey_achievement_collect_achievements30_title"
        case .viewAchievements3Min:  return "misskey_achievement_view_achievements3min_title"
        case .iLoveMisskey:          return "misskey_achievement_i_love_misskey_title"
        case .foundTreasure:         return "misskey_achievement_found_treasure_title"
        case .client30Min:           return "misskey_achievement_client30min_title"
        case .client60Min:           return "misskey_achievement_client60min_title"
        case .noteDeletedWithin1Min: return "misskey_achievement_note_deleted_within1min_title"
        case .postedAtLateNight:     return "misskey_achievement_posted_at_late_night_title"
        case .postedAt0Min0Sec:      return "misskey_achievement_posted_at_0min0sec_title"
        case .selfQuote:             return "misskey_achievement_self_quote_title"
        case .htl20Npm:              return "misskey_achievement_htl20npm_title"
        case .viewInstanceChart:     return "misskey_achievement_view_instance_chart_title"
        case .outputHelloWorldOnScratchpad:
            return "misskey_achievement_output_hello_world_on_scratchpad_title"
        case .open3Windows:                 return "misskey_achievement_open3windows_title"
        case .driveFolderCircularReference: return "misskey_achievement_drive_folder_circular_reference_title"
        case .reactWithoutRead:             return "misskey_achievement_react_without_read_title"
        case .clickedClickHere:             return "misskey_achievement_clicked_click_here_title"
        case .justPlainLucky:               return "misskey_achievement_just_plain_lucky_title"
        case .setNameToSyuilo:              return "misskey_achievement_set_name_to_syuilo_title"
        case .passedSinceAccountCreated1:   return "misskey_achievement_passed_since_account_created1_title"
        case .passedSinceAccountCreated2:   return "misskey_achievement_passed_since_account_created2_title"
        case .passedSinceAccountCreated3:   return "misskey_achievement_passed_since_account_created3_title"
        case .loggedInOnBirthday:           return "misskey_achievement_logged_in_on_birthday_title"
        case .loggedInOnNewYearsDay:        return "misskey_achievement_logged_in_on_new_years_day_title"
        case .cookieClicked:                return "misskey_achievement_cookie_clicked_title"
        case .brainDiver:                   return "misskey_achievement_brain_diver_title"
        case .smashTestNotificationButton:  return "misskey_achievement_smash_test_notification_button_title"
        case .tutorialCompleted:            return "misskey_achievement_tutorial_completed_title"
        case .bubbleGameExplodingHead:      return "misskey_achievement_bubble_game_exploding_head_title"
        case .bubbleGameDoubleExplodingHead:return "misskey_achievement_bubble_game_double_exploding_head_title"
        }
    }

    var descriptionKey: LocalizedStringResource {
        switch self {
        case .notes1:      return "misskey_achievement_notes1_description"
        case .notes10:     return "misskey_achievement_notes10_description"
        case .notes100:    return "misskey_achievement_notes100_description"
        case .notes500:    return "misskey_achievement_notes500_description"
        case .notes1000:   return "misskey_achievement_notes1000_description"
        case .notes5000:   return "misskey_achievement_notes5000_description"
        case .notes10000:  return "misskey_achievement_notes10000_description"
        case .notes20000:  return "misskey_achievement_notes20000_description"
        case .notes30000:  return "misskey_achievement_notes30000_description"
        case .notes40000:  return "misskey_achievement_notes40000_description"
        case .notes50000:  return "misskey_achievement_notes50000_description"
        case .notes60000:  return "misskey_achievement_notes60000_description"
        case .notes70000:  return "misskey_achievement_notes70000_description"
        case .notes80000:  return "misskey_achievement_notes80000_description"
        case .notes90000:  return "misskey_achievement_notes90000_description"
        case .notes100000: return "misskey_achievement_notes100000_description"

        case .login3:    return "misskey_achievement_login3_description"
        case .login7:    return "misskey_achievement_login7_description"
        case .login15:   return "misskey_achievement_login15_description"
        case .login30:   return "misskey_achievement_login30_description"
        case .login60:   return "misskey_achievement_login60_description"
        case .login100:  return "misskey_achievement_login100_description"
        case .login200:  return "misskey_achievement_login200_description"
        case .login300:  return "misskey_achievement_login300_description"
        case .login400:  return "misskey_achievement_login400_description"
        case .login500:  return "misskey_achievement_login500_description"
        case .login600:  return "misskey_achievement_login600_description"
        case .login700:  return "misskey_achievement_login700_description"
        case .login800:  return "misskey_achievement_login800_description"
        case .login900:  return "misskey_achievement_login900_description"
        case .login1000: return "misskey_achievement_login1000_description"

        case .noteClipped1:          return "misskey_achievement_note_clipped1_description"
        case .noteFavorited1:        return "misskey_achievement_note_favorited1_description"
        case .myNoteFavorited1:      return "misskey_achievement_my_note_favorited1_description"
        case .profileFilled:         return "misskey_achievement_profile_filled_description"
        case .markedAsCat:           return "misskey_achievement_marked_as_cat_description"
        case .following1:            return "misskey_achievement_following1_description"
        case .following10:           return "misskey_achievement_following10_description"
        case .following50:           return "misskey_achievement_following50_description"
        case .following100:          return "misskey_achievement_following100_description"
        case .following300:          return "misskey_achievement_following300_description"
        case .followers1:            return "misskey_achievement_followers1_description"
        case .followers10:           return "misskey_achievement_followers10_description"
        case .followers50:           return "misskey_achievement_followers50_description"
        case .followers100:          return "misskey_achievement_followers100_description"
        case .followers300:          return "misskey_achievement_followers300_description"
        case .followers500:          return "misskey_achievement_followers500_description"
        case .followers1000:         return "misskey_achievement_followers1000_description"
        case .collectAchievements30: return "misskey_achievement_collect_achievements30_description"
        case .viewAchievements3Min:  return "misskey_achievement_view_achievements3min_description"
        case .iLoveMisskey:          return "misskey_achievement_i_love_misskey_description"
        case .foundTreasure:         return "misskey_achievement_found_treasure_description"
        case .client30Min:           return "misskey_achievement_client30min_description"
        case .client60Min:           return "misskey_achievement_client60min_description"
        case .noteDeletedWithin1Min: return "misskey_achievement_note_deleted_within1min_description"
        case .postedAtLateNight:     return "misskey_achievement_posted_at_late_night_description"
        case .postedAt0Min0Sec:      return "misskey_achievement_posted_at_0min0sec_description"
        case .selfQuote:             return "misskey_achievement_self_quote_description"
        case .htl20Npm:              return "misskey_achievement_htl20npm_description"
        case .viewInstanceChart:     return "misskey_achievement_view_instance_chart_description"
        case .outputHelloWorldOnScratchpad:
            return "misskey_achievement_output_hello_world_on_scratchpad_description"
        case .open3Windows:                 return "misskey_achievement_open3windows_description"
        case .driveFolderCircularReference: return "misskey_achievement_drive_folder_circular_reference_description"
        case .reactWithoutRead:             return "misskey_achievement_react_without_read_description"
        case .clickedClickHere:             return "misskey_achievement_clicked_click_here_description"
        case .justPlainLucky:               return "misskey_achievement_just_plain_lucky_description"
        case .setNameToSyuilo:              return "misskey_achievement_set_name_to_syuilo_description"
        case .passedSinceAccountCreated1:   return "misskey_achievement_passed_since_account_created1_description"
        case .passedSinceAccountCreated2:   return "misskey_achievement_passed_since_account_created2_description"
        case .passedSinceAccountCreated3:   return "misskey_achievement_passed_since_account_created3_description"
        case .loggedInOnBirthday:           return "misskey_achievement_logged_in_on_birthday_description"
        case .loggedInOnNewYearsDay:        return "misskey_achievement_logged_in_on_new_years_day_description"
        case .cookieClicked:                return "misskey_achievement_cookie_clicked_description"
        case .brainDiver:                   return "misskey_achievement_brain_diver_description"
        case .smashTestNotificationButton:  return "misskey_achievement_smash_test_notification_button_description"
        case .tutorialCompleted:            return "misskey_achievement_tutorial_completed_description"
        case .bubbleGameExplodingHead:      return "misskey_achievement_bubble_game_exploding_head_description"
        case .bubbleGameDoubleExplodingHead:return "misskey_achievement_bubble_game_double_exploding_head_description"
        }
    }
}
