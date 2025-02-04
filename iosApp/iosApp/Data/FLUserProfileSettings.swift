import Foundation
import shared
import SwiftUI

// 类型别名
public typealias AccountType = shared.AccountType
public typealias MicroBlogKey = shared.MicroBlogKey
public typealias TimelinePresenter = shared.TimelinePresenter
public typealias UiUserV2 = shared.UiUserV2
public typealias ProfileTab = shared.ProfileTab
public typealias AccountTypeSpecific = shared.AccountTypeSpecific

//  - Profile Tab Settings
public extension FLTabSettings {
    // Profile Tab 类型
    enum FLProfileTabType {
        case timeline(TimelineType)
        case media

        public enum TimelineType {
            case status
            case statusWithReplies
            case likes
        }
    }

    // 获取 Profile 的默认三个 tab
    static func defaultThree(user: UiUserV2, userKey: MicroBlogKey?) -> [FLTabItem] {
        let actualUserKey = userKey ?? user.key

        switch user.platformType {
        case .mastodon:
            return [
                FLProfileMastodonTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimeline),
                        icon: .mixed(.profile, userKey: user.key)
                    ),
                    account: AccountTypeSpecific(accountKey: user.key),
                    userKey: actualUserKey,
                    type: .status
                ),
                FLProfileMastodonTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimelineWithReply),
                        icon: .mixed(.profile, userKey: user.key)
                    ),
                    account: AccountTypeSpecific(accountKey: user.key),
                    userKey: actualUserKey,
                    type: .statusWithReplies
                ),
            ]
        case .misskey:
            return [
                FLProfileMisskeyTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimeline),
                        icon: .mixed(.profile, userKey: user.key)
                    ),
                    account: AccountTypeSpecific(accountKey: user.key),
                    userKey: actualUserKey,
                    type: .status
                ),
                FLProfileMisskeyTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimelineWithReply),
                        icon: .mixed(.profile, userKey: user.key)
                    ),
                    account: AccountTypeSpecific(accountKey: user.key),
                    userKey: actualUserKey,
                    type: .statusWithReplies
                ),
            ]
        case .bluesky:
            return [
                FLProfileBlueskyTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimeline),
                        icon: .mixed(.profile, userKey: user.key)
                    ),
                    account: AccountTypeSpecific(accountKey: user.key),
                    userKey: actualUserKey,
                    type: .status
                ),
                FLProfileBlueskyTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimelineWithReply),
                        icon: .mixed(.profile, userKey: user.key)
                    ),
                    account: AccountTypeSpecific(accountKey: user.key),
                    userKey: actualUserKey,
                    type: .statusWithReplies
                ),
                FLProfileBlueskyTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileLikes),
                        icon: .mixed(.heart, userKey: user.key)
                    ),
                    account: AccountTypeSpecific(accountKey: user.key),
                    userKey: actualUserKey,
                    type: .likes
                ),
            ]
        case .xQt:
            return [
                FLProfileXQTTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimeline),
                        icon: .mixed(.profile, userKey: user.key)
                    ),
                    account: AccountTypeSpecific(accountKey: user.key),
                    userKey: actualUserKey,
                    type: .status
                ),
                FLProfileXQTTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimelineWithReply),
                        icon: .mixed(.profile, userKey: user.key)
                    ),
                    account: AccountTypeSpecific(accountKey: user.key),
                    userKey: actualUserKey,
                    type: .statusWithReplies
                ),
                FLProfileXQTTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileLikes),
                        icon: .mixed(.heart, userKey: user.key)
                    ),
                    account: AccountTypeSpecific(accountKey: user.key),
                    userKey: actualUserKey,
                    type: .likes
                ),
            ]
        case .vvo:
            return [
                // FLProfileTimelineTabItem(
                //     metaData: FLTabMetaData(
                //         title: .localized(.profileTimeline),
                //         icon: .mixed(.profile, userKey: accountKey)
                //     ),
                //     account: AccountTypeSpecific(accountKey: accountKey),
                //     userKey: user.key,
                //     type: .status
                // ),
                // FLProfileTimelineTabItem(
                //     metaData: FLTabMetaData(
                //         title: .localized(.profileTimelineWithReply),
                //         icon: .mixed(.profile, userKey: accountKey)
                //     ),
                //     account: AccountTypeSpecific(accountKey: accountKey),
                //     userKey: user.key,
                //     type: .statusWithReplies
                // )
            ]
        default:
            return []
        }
    }
}

//  - Platform Specific Profile Tab Items
// Bluesky
public struct FLProfileBlueskyTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public let userKey: MicroBlogKey?
    public let type: FLTabSettings.FLProfileTabType.TimelineType
    public var key: String { "profile_timeline_bluesky_\(account)_\(type)" }

    public func createPresenter() -> TimelinePresenter {
        BlueskyUserTimelinePresenter(accountType: account, userKey: userKey, type: type.toProfileTabType())
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLProfileBlueskyTimelineTabItem(metaData: metaData, account: account, userKey: userKey, type: type)
    }
}

// Mastodon
public struct FLProfileMastodonTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public let userKey: MicroBlogKey?
    public let type: FLTabSettings.FLProfileTabType.TimelineType
    public var key: String { "profile_timeline_mastodon_\(account)_\(type)" }

    public func createPresenter() -> TimelinePresenter {
        MastodonUserTimelinePresenter(accountType: account, userKey: userKey, type: type.toProfileTabType())
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLProfileMastodonTimelineTabItem(metaData: metaData, account: account, userKey: userKey, type: type)
    }
}


// Misskey
public struct FLProfileMisskeyTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public let userKey: MicroBlogKey?
    public let type: FLTabSettings.FLProfileTabType.TimelineType
    public var key: String { "profile_timeline_misskey_\(account)_\(type)" }

    public func createPresenter() -> TimelinePresenter {
        MisskeyUserTimelinePresenter(accountType: account, userKey: userKey, type: type.toProfileTabType())
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLProfileMisskeyTimelineTabItem(metaData: metaData, account: account, userKey: userKey, type: type)
    }
}


// XQT
public struct FLProfileXQTTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public let userKey: MicroBlogKey?
    public let type: FLTabSettings.FLProfileTabType.TimelineType
    public var key: String { "profile_timeline_xqt_\(account)_\(type)" }

    public func createPresenter() -> TimelinePresenter {
        XQTUserTimelinePresenter(accountType: account, userKey: userKey, type: type.toProfileTabType())
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLProfileXQTTimelineTabItem(metaData: metaData, account: account, userKey: userKey, type: type)
    }
}


//  - Profile Tab Items
public struct FLProfileMediaTabItem: FLTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public let userKey: MicroBlogKey?
    public var key: String { "profile_media_\(account)" }

    public init(metaData: FLTabMetaData, account: AccountTypeSpecific, userKey: MicroBlogKey?) {
        self.metaData = metaData
        self.account = account
        self.userKey = userKey
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLProfileMediaTabItem(metaData: metaData, account: account as! AccountTypeSpecific, userKey: userKey)
    }
}

//  - Helper Extensions
extension FLTabSettings.FLProfileTabType.TimelineType {
    func toProfileTabType() -> ProfileTabTimeline.Type_ {
        switch self {
        case .status:
            .status
        case .statusWithReplies:
            .statusWithReplies
        case .likes:
            .likes
        }
    }
}
