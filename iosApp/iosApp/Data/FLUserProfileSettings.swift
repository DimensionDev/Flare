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

// MARK: - Profile Tab Settings
public extension FLTabSettings {
    // Profile Tab 类型
    public enum FLProfileTabType {
        case timeline(TimelineType)
        case media
        
        public enum TimelineType {
            case status
            case statusWithReplies
            case likes
        }
    }
    
    // 获取 Profile 的默认三个 tab
    static func defaultThree(user: UiUserV2, accountKey: MicroBlogKey) -> [FLTabItem] {
        switch user.platformType {
        case .mastodon:
            return [
                FLProfileTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimeline),
                        icon: .mixed(.profile, userKey: accountKey)
                    ),
                    account: AccountTypeSpecific(accountKey: accountKey),
                    userKey: user.key,
                    type: .status
                ),
                FLProfileTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimelineWithReply),
                        icon: .mixed(.profile, userKey: accountKey)
                    ),
                    account: AccountTypeSpecific(accountKey: accountKey),
                    userKey: user.key,
                    type: .statusWithReplies
                ),
//                FLProfileMediaTabItem(
//                    metaData: FLTabMetaData(
//                        title: .localized(.profileMedia),
//                        icon: .mixed(.media, userKey: accountKey)
//                    ),
//                    account: AccountTypeSpecific(accountKey: accountKey),
//                    userKey: user.key
//                )
            ]
        case .misskey:
            return [
                FLProfileTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimeline),
                        icon: .mixed(.profile, userKey: accountKey)
                    ),
                    account: AccountTypeSpecific(accountKey: accountKey),
                    userKey: user.key,
                    type: .status
                ),
                FLProfileTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimelineWithReply),
                        icon: .mixed(.profile, userKey: accountKey)
                    ),
                    account: AccountTypeSpecific(accountKey: accountKey),
                    userKey: user.key,
                    type: .statusWithReplies
                ),
//                FLProfileMediaTabItem(
//                    metaData: FLTabMetaData(
//                        title: .localized(.profileMedia),
//                        icon: .mixed(.media, userKey: accountKey)
//                    ),
//                    account: AccountTypeSpecific(accountKey: accountKey),
//                    userKey: user.key
//                )
            ]
        case .bluesky:
            return [
                FLProfileTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimeline),
                        icon: .mixed(.profile, userKey: accountKey)
                    ),
                    account: AccountTypeSpecific(accountKey: accountKey),
                    userKey: user.key,
                    type: .status
                ),
                FLProfileTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimelineWithReply),
                        icon: .mixed(.profile, userKey: accountKey)
                    ),
                    account: AccountTypeSpecific(accountKey: accountKey),
                    userKey: user.key,
                    type: .statusWithReplies
                ),
//                FLProfileMediaTabItem(
//                    metaData: FLTabMetaData(
//                        title: .localized(.profileMedia),
//                        icon: .mixed(.media, userKey: accountKey)
//                    ),
//                    account: AccountTypeSpecific(accountKey: accountKey),
//                    userKey: user.key
//                ),
                FLProfileTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileLikes),
                        icon: .mixed(.heart, userKey: accountKey)
                    ),
                    account: AccountTypeSpecific(accountKey: accountKey),
                    userKey: user.key,
                    type: .likes
                )
            ]
        case .xQt:
            return [
                FLProfileTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimeline),
                        icon: .mixed(.profile, userKey: accountKey)
                    ),
                    account: AccountTypeSpecific(accountKey: accountKey),
                    userKey: user.key,
                    type: .status
                ),
                FLProfileTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimelineWithReply),
                        icon: .mixed(.profile, userKey: accountKey)
                    ),
                    account: AccountTypeSpecific(accountKey: accountKey),
                    userKey: user.key,
                    type: .statusWithReplies
                ),
//                FLProfileMediaTabItem(
//                    metaData: FLTabMetaData(
//                        title: .localized(.profileMedia),
//                        icon: .mixed(.media, userKey: accountKey)
//                    ),
//                    account: AccountTypeSpecific(accountKey: accountKey),
//                    userKey: user.key
//                ),
                FLProfileTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileLikes),
                        icon: .mixed(.heart, userKey: accountKey)
                    ),
                    account: AccountTypeSpecific(accountKey: accountKey),
                    userKey: user.key,
                    type: .likes
                )
            ]
        case .vvo:
            return [
                FLProfileTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimeline),
                        icon: .mixed(.profile, userKey: accountKey)
                    ),
                    account: AccountTypeSpecific(accountKey: accountKey),
                    userKey: user.key,
                    type: .status
                ),
                FLProfileTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized(.profileTimelineWithReply),
                        icon: .mixed(.profile, userKey: accountKey)
                    ),
                    account: AccountTypeSpecific(accountKey: accountKey),
                    userKey: user.key,
                    type: .statusWithReplies
                ),
//                FLProfileMediaTabItem(
//                    metaData: FLTabMetaData(
//                        title: .localized(.profileMedia),
//                        icon: .mixed(.media, userKey: accountKey)
//                    ),
//                    account: AccountTypeSpecific(accountKey: accountKey),
//                    userKey: user.key
//                )
            ]
        default:
            return []
        }
    }
}

// MARK: - Profile Tab Items
public struct FLProfileTimelineTabItem: FLTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public let userKey: MicroBlogKey
    public let type: FLTabSettings.FLProfileTabType.TimelineType
    public var key: String { "profile_timeline_\(account)_\(type)" }
    
    public init(metaData: FLTabMetaData, account: AccountTypeSpecific, userKey: MicroBlogKey, type: FLTabSettings.FLProfileTabType.TimelineType) {
        self.metaData = metaData
        self.account = account
        self.userKey = userKey
        self.type = type
    }
    
    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLProfileTimelineTabItem(metaData: metaData, account: account as! AccountTypeSpecific, userKey: userKey, type: type)
    }
}

//public struct FLProfileMediaTabItem: FLTabItem {
//    public let metaData: FLTabMetaData
//    public let account: AccountType
//    public let userKey: MicroBlogKey
//    public var key: String { "profile_media_\(account)" }
//    
//    public init(metaData: FLTabMetaData, account: AccountTypeSpecific, userKey: MicroBlogKey) {
//        self.metaData = metaData
//        self.account = account
//        self.userKey = userKey
//    }
//    
//    public func update(metaData: FLTabMetaData) -> FLTabItem {
//        FLProfileMediaTabItem(metaData: metaData, account: account as! AccountTypeSpecific, userKey: userKey)
//    }
//}

// MARK: - Platform Specific Profile Tab Items
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

//public struct FLProfileBlueskyMediaTabItem: FLTimelineTabItem {
//    public let metaData: FLTabMetaData
//    public let account: AccountType
//    public let userKey: MicroBlogKey?
//    public var key: String { "profile_media_bluesky_\(account)" }
//    
//    public func createPresenter() -> TimelinePresenter {
//        BlueskyUserMediaPresenter(accountType: account, userKey: userKey)
//    }
//    
//    public func update(metaData: FLTabMetaData) -> FLTabItem {
//        FLProfileBlueskyMediaTabItem(metaData: metaData, account: account, userKey: userKey)
//    }
//}

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

//public struct FLProfileMastodonMediaTabItem: FLTimelineTabItem {
//    public let metaData: FLTabMetaData
//    public let account: AccountType
//    public let userKey: MicroBlogKey?
//    public var key: String { "profile_media_mastodon_\(account)" }
//    
//    public func createPresenter() -> TimelinePresenter {
//        MastodonUserMediaPresenter(accountType: account, userKey: userKey)
//    }
//    
//    public func update(metaData: FLTabMetaData) -> FLTabItem {
//        FLProfileMastodonMediaTabItem(metaData: metaData, account: account, userKey: userKey)
//    }
//}

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

//public struct FLProfileMisskeyMediaTabItem: FLTimelineTabItem {
//    public let metaData: FLTabMetaData
//    public let account: AccountType
//    public let userKey: MicroBlogKey?
//    public var key: String { "profile_media_misskey_\(account)" }
//    
//    public func createPresenter() -> TimelinePresenter {
//        MisskeyUserMediaPresenter(accountType: account, userKey: userKey)
//    }
//    
//    public func update(metaData: FLTabMetaData) -> FLTabItem {
//        FLProfileMisskeyMediaTabItem(metaData: metaData, account: account, userKey: userKey)
//    }
//}

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

//public struct FLProfileXQTMediaTabItem: FLTimelineTabItem {
//    public let metaData: FLTabMetaData
//    public let account: AccountType
//    public let userKey: MicroBlogKey?
//    public var key: String { "profile_media_xqt_\(account)" }
//    
//    public func createPresenter() -> TimelinePresenter {
//        XQTUserMediaPresenter(accountType: account, userKey: userKey)
//    }
//    
//    public func update(metaData: FLTabMetaData) -> FLTabItem {
//        FLProfileXQTMediaTabItem(metaData: metaData, account: account, userKey: userKey)
//    }
//}

// MARK: - Helper Extensions
extension FLTabSettings.FLProfileTabType.TimelineType {
    func toProfileTabType() -> ProfileTabTimeline.Type_ {
        switch self {
        case .status:
            return .status
        case .statusWithReplies:
            return .statusWithReplies
        case .likes:
            return .likes
        }
    }
}

