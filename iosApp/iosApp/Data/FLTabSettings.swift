import Foundation
import ObjectiveC
import shared
import SwiftUI

typealias FLMicroBlogKey = MicroBlogKey
public typealias AccountType = shared.AccountType
public typealias MicroBlogKey = shared.MicroBlogKey
public typealias TimelinePresenter = shared.TimelinePresenter
public typealias UiUserV2 = shared.UiUserV2
public typealias ProfileTab = shared.ProfileTab
public typealias AccountTypeSpecific = shared.AccountTypeSpecific

// - Tab Settings
public struct FLTabSettings {
    public let items: [FLTabItem]
    public let secondaryItems: [FLTabItem]?
    public let homeTabs: [String: [FLTabItem]]

    enum CodingKeys: String, CodingKey {
        case items
        case secondaryItems
        case homeTabs
    }

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

    // - Factory Methods
    public static var `default`: FLTabSettings {
        FLTabSettings(
            items: [
                FLHomeTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized("Home"),
                        icon: .material(FLMaterialIcon.home)
                    ), account: AccountTypeActive.shared
                ),
                FLNotificationTabItem(
                    metaData: FLTabMetaData(
                        title: .localized("Notifications"),
                        icon: .material(FLMaterialIcon.notification)
                    ), account: AccountTypeActive.shared
                ),
                FLDiscoverTabItem(
                    metaData: FLTabMetaData(
                        title: .localized("Discover"),
                        icon: .material(FLMaterialIcon.search)
                    ), account: AccountTypeActive.shared
                ),
                FLProfileTabItem(
                    metaData: FLTabMetaData(
                        title: .localized("Me"),
                        icon: .material(.profile)
                    ),
                    account: AccountTypeActive.shared,
                    userKey: AccountTypeActive.shared
                )
            ],
            secondaryItems: nil,
            homeTabs: [:]
        )
    }

    public static var guest: FLTabSettings {
        FLTabSettings(
            items: [
                FLHomeTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .localized("Home"),
                        icon: .material(.home)
                    ), account: AccountTypeGuest.shared
                ),
                FLDiscoverTabItem(
                    metaData: FLTabMetaData(
                        title: .localized("Discover"),
                        icon: .material(.search)
                    ), account: AccountTypeGuest.shared
                ),
                FLSettingsTabItem(
                    metaData: FLTabMetaData(
                        title: .localized("Settings"),
                        icon: .material(.settings)
                    )
                )
            ],
            secondaryItems: nil,
            homeTabs: [:]
        )
    }

    // - Platform Specific Factory Methods
    public static func defaultPrimary(user: UiUserV2) -> [FLTabItem] {
        switch user.platformType {
        case .mastodon:
            mastodon(accountKey: user.key)
        case .misskey:
            misskey(accountKey: user.key)
        case .bluesky:
            bluesky(accountKey: user.key)
        case .xQt:
            xqt(accountKey: user.key)
        case .vvo:
            vvo(accountKey: user.key)
        }
    }

    public static func defaultSecondary(user: UiUserV2) -> [FLTabItem] {
        switch user.platformType {
        case .mastodon:
            defaultMastodonSecondaryItems(accountKey: user.key)
        case .misskey:
            defaultMisskeySecondaryItems(accountKey: user.key)
        case .bluesky:
            defaultBlueskySecondaryItems(accountKey: user.key)
        case .xQt:
            defaultXqtSecondaryItems(accountKey: user.key)
        case .vvo:
            defaultVVOSecondaryItems(accountKey: user.key)
        }
    }

    // - Platform Specific Items
    private static func mastodon(accountKey: FLMicroBlogKey) -> [FLTabItem] {
        [
            FLHomeTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized("Home"),
                    icon: .mixed(.home, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLNotificationTabItem(
                metaData: FLTabMetaData(
                    title: .localized("Notifications"),
                    icon: .mixed(.notification, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLDiscoverTabItem(
                metaData: FLTabMetaData(
                    title: .localized("Discover"),
                    icon: .mixed(.search, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLProfileTabItem(
                metaData: FLTabMetaData(
                    title: .localized("Me"),
                    icon: .mixed(.profile, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey),
                userKey: AccountTypeSpecific(accountKey: accountKey)
            )
        ]
    }

    private static func defaultMastodonSecondaryItems(accountKey: FLMicroBlogKey) -> [FLTabItem] {
        [
            FLMastodonLocalTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized("Local"),
                    icon: .mixed(.local, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLMastodonPublicTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized("Public"),
                    icon: .mixed(.world, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLMastodonBookmarkTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized("Bookmark"),
                    icon: .mixed(.bookmark, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLMastodonFavouriteTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized("Favourite"),
                    icon: .mixed(.heart, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey)
            )
//            FLAllListTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized("List"),
//                    icon: .mixed(.list, userKey: accountKey)
//                ),
//                account: AccountTypeSpecific(accountKey: accountKey)
//            ),
        ]
    }

    // - Misskey Platform Items
    private static func misskey(accountKey: FLMicroBlogKey) -> [FLTabItem] {
        [
            FLHomeTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.home),
                    icon: .mixed(.home, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLNotificationTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.notifications),
                    icon: .mixed(.notification, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLDiscoverTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.discover),
                    icon: .mixed(.search, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLProfileTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.me),
                    icon: .mixed(.profile, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey),
                userKey: AccountTypeSpecific(accountKey: accountKey)
            )
        ]
    }

    private static func defaultMisskeySecondaryItems(accountKey: FLMicroBlogKey) -> [FLTabItem] {
        [
            FLMisskeyLocalTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.mastodonLocal),
                    icon: .mixed(.local, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLMisskeyGlobalTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.mastodonPublic),
                    icon: .mixed(.world, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            )
        ]
    }

    // - Bluesky Platform Items
    private static func bluesky(accountKey: FLMicroBlogKey) -> [FLTabItem] {
        [
            FLHomeTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.home),
                    icon: .mixed(.home, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLNotificationTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.notifications),
                    icon: .mixed(.notification, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLDiscoverTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.discover),
                    icon: .mixed(.search, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            )
        ]
    }

    private static func defaultBlueskySecondaryItems(accountKey _: FLMicroBlogKey) -> [FLTabItem] {
        [
            //            FLAllListTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(.list),
//                    icon: .mixed(.list, userKey: accountKey)
//                ), account: AccountTypeSpecific(accountKey: accountKey)
//            ),
//            FLBlueskyFeedsTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(.feeds),
//                    icon: .mixed(.feeds, userKey: accountKey)
//                ), account: AccountTypeActive.shared
//            ),
//            FLDirectMessageTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(.directMessage),
//                    icon: .mixed(.messages, userKey: accountKey)
//                ), account: AccountTypeSpecific(accountKey: accountKey)
//            ),
        ]
    }

    // - XQT (Twitter) Platform Items
    private static func xqt(accountKey: FLMicroBlogKey) -> [FLTabItem] {
        [
            FLHomeTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.forYou),
                    icon: .mixed(.home, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLNotificationTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.notifications),
                    icon: .mixed(.notification, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLDiscoverTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.discover),
                    icon: .mixed(.search, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLProfileTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.me),
                    icon: .mixed(.profile, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey),
                userKey: AccountTypeSpecific(accountKey: accountKey)
            )
        ]
    }

    private static func defaultXqtSecondaryItems(accountKey: FLMicroBlogKey) -> [FLTabItem] {
        [
            FLXQTFeaturedTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.following),
                    icon: .mixed(.featured, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLXQTBookmarkTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.bookmark),
                    icon: .mixed(.bookmark, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            )
        ]
    }

    // - VVO Platform Items
    private static func vvo(accountKey: FLMicroBlogKey) -> [FLTabItem] {
        [
            FLHomeTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.home),
                    icon: .mixed(.home, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLNotificationTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.notifications),
                    icon: .mixed(.notification, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLDiscoverTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.discover),
                    icon: .mixed(.search, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLProfileTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.me),
                    icon: .mixed(.profile, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey),
                userKey: AccountTypeSpecific(accountKey: accountKey)
            )
        ]
    }

    private static func defaultVVOSecondaryItems(accountKey _: FLMicroBlogKey) -> [FLTabItem] {
        [] // VVO 没有次要标签项
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
                )
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
                )
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
                )
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
                )
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

    // Bluesky
    public struct FLProfileBlueskyTimelineTabItem: FLTimelineTabItem {
        public let metaData: FLTabMetaData
        public let account: AccountType
        public let userKey: MicroBlogKey?
        let type: FLTabSettings.FLProfileTabType.TimelineType
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
        let type: FLTabSettings.FLProfileTabType.TimelineType
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
        let type: FLProfileTabType.TimelineType
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
        let type: FLTabSettings.FLProfileTabType.TimelineType
        public var key: String { "profile_timeline_xqt_\(account)_\(type)" }

        public func createPresenter() -> TimelinePresenter {
            XQTUserTimelinePresenter(accountType: account, userKey: userKey, type: type.toProfileTabType())
        }

        public func update(metaData: FLTabMetaData) -> FLTabItem {
            FLProfileXQTTimelineTabItem(metaData: metaData, account: account, userKey: userKey, type: type)
        }
    }
}

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

// - Material Icon Type
public enum FLMaterialIcon: String {
    case home = "Home"
    case notification = "Notification"
    case search = "Search"
    case profile = "Profile"
    case settings = "Settings"
    case local = "Local"
    case world = "World"
    case featured = "Featured"
    case bookmark = "Bookmark"
    case heart = "Heart"
    case twitter = "Twitter"
    case mastodon = "Mastodon"
    case misskey = "Misskey"
    case bluesky = "Bluesky"
    case list = "List"
    case feeds = "Feeds"
    case messages = "Messages"
    case media = "Media"

    // TODO: 实现 SwiftUI Image 转换
    public var icon: Image {
        switch self {
        case .home:
            Image(systemName: "house.fill")
        case .notification:
            Image(systemName: "bell.fill")
        case .search:
            Image(systemName: "magnifyingglass")
        case .profile:
            Image(systemName: "person.circle.fill")
        case .settings:
            Image(systemName: "gear")
        case .local:
            Image(systemName: "person.2.fill")
        case .world:
            Image(systemName: "globe")
        case .featured:
            Image(systemName: "star.fill")
        case .bookmark:
            Image(systemName: "bookmark.fill")
        case .heart:
            Image(systemName: "heart.fill")
        case .twitter:
            Image(systemName: "bird.fill")
        case .mastodon:
            Image("mastodon") // 需要添加自定义图标
        case .misskey:
            Image("misskey") // 需要添加自定义图标
        case .bluesky:
            Image("bluesky") // 需要添加自定义图标
        case .list:
            Image(systemName: "list.bullet")
        case .feeds:
            Image(systemName: "dot.radiowaves.left.and.right")
        case .messages:
            Image(systemName: "message.fill")
        case .media:
            Image(systemName: "photo.on.rectangle")
        }
    }
}

// - Tab Item Protocol

public protocol FLTabItem {
    var metaData: FLTabMetaData { get }
    var account: AccountType { get }
    var key: String { get }

    func update(metaData: FLTabMetaData) -> FLTabItem
}

// - Timeline Tab Item Protocol
public protocol FLTimelineTabItem: FLTabItem {
    func createPresenter() -> TimelinePresenter
}

// - Tab Meta Data
public struct FLTabMetaData {
    public let title: FLTitleType
    public let icon: FLIconType
}

// - Title Type
public enum FLTitleType {
    case text(String)
    case localized(String)
}

// - Icon Type
public enum FLIconType {
    case avatar(userKey: MicroBlogKey)
    case material(String)
    case mixed([String])
}

// - IconType Extensions
extension FLIconType {
    static func material(_ icon: FLMaterialIcon) -> FLIconType {
        .material(icon.rawValue)
    }

    static func mixed(_ icon: FLMaterialIcon, userKey _: FLMicroBlogKey) -> FLIconType {
        .mixed([icon.rawValue])
    }
}

// - Common Tab Items
public struct FLNotificationTabItem: FLTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "notification_\(account)" }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLNotificationTabItem(metaData: metaData, account: account)
    }
}

public struct FLHomeTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "home_\(account)" }

    public func createPresenter() -> TimelinePresenter {
        HomeTimelinePresenter(accountType: account)
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLHomeTimelineTabItem(metaData: metaData, account: account)
    }
}

public struct FLListTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public let listKey: String
    public var key: String { "list_\(account)_\(listKey)" }

    public func createPresenter() -> TimelinePresenter {
        ListTimelinePresenter(accountType: account, listId: listKey)
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLListTimelineTabItem(metaData: metaData, account: account, listKey: listKey)
    }
}

public struct FLAllListTabItem: FLTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "list_\(account)" }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLAllListTabItem(metaData: metaData, account: account)
    }
}

public struct FLProfileTabItem: FLTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public let userKey: AccountType
    public var key: String { "profile_\(account)_\(userKey)" }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLProfileTabItem(metaData: metaData, account: account, userKey: userKey)
    }
}

public struct FLDiscoverTabItem: FLTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "discover_\(account)" }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLDiscoverTabItem(metaData: metaData, account: account)
    }
}

public struct FLSettingsTabItem: FLTabItem {
    public let metaData: FLTabMetaData
    public var account: AccountType {
        // TODO: 需要实现 AccountTypeActive.shared
        fatalError("需要实现 AccountTypeActive.shared")
    }

    public var key: String { "settings" }

    public func update(metaData _: FLTabMetaData) -> FLTabItem {
        self // Settings tab item is immutable
    }
}

public struct FLDirectMessageTabItem: FLTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "dm_\(account)" }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLDirectMessageTabItem(metaData: metaData, account: account)
    }
}

// - Mastodon Specific Tab Items
public struct FLMastodonLocalTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "local_\(account)" }

    public func createPresenter() -> TimelinePresenter {
        MastodonLocalTimelinePresenter(accountType: account)
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLMastodonLocalTimelineTabItem(metaData: metaData, account: account)
    }
}

public struct FLMastodonPublicTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "public_\(account)" }

    public func createPresenter() -> TimelinePresenter {
        MastodonPublicTimelinePresenter(accountType: account)
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLMastodonPublicTimelineTabItem(metaData: metaData, account: account)
    }
}

public struct FLMastodonBookmarkTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "bookmark_\(account)" }

    public func createPresenter() -> TimelinePresenter {
        MastodonBookmarkTimelinePresenter(accountType: account)
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLMastodonBookmarkTimelineTabItem(metaData: metaData, account: account)
    }
}

public struct FLMastodonFavouriteTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "favourite_\(account)" }

    public func createPresenter() -> TimelinePresenter {
        MastodonFavouriteTimelinePresenter(accountType: account)
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLMastodonFavouriteTimelineTabItem(metaData: metaData, account: account)
    }
}

// - Misskey Specific Tab Items
public struct FLMisskeyLocalTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "local_\(account)" }

    public func createPresenter() -> TimelinePresenter {
        MissKeyLocalTimelinePresenter(accountType: account)
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLMisskeyLocalTimelineTabItem(metaData: metaData, account: account)
    }
}

public struct FLMisskeyGlobalTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "global_\(account)" }

    public func createPresenter() -> TimelinePresenter {
        MissKeyPublicTimelinePresenter(accountType: account)
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLMisskeyGlobalTimelineTabItem(metaData: metaData, account: account)
    }
}

// - XQT (Twitter) Specific Tab Items
public struct FLXQTFeaturedTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "featured_\(account)" }

    public func createPresenter() -> TimelinePresenter {
        XQTFeaturedTimelinePresenter(accountType: account)
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLXQTFeaturedTimelineTabItem(metaData: metaData, account: account)
    }
}

public struct FLXQTBookmarkTimelineTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "bookmark_\(account)" }

    public func createPresenter() -> TimelinePresenter {
        XQTBookmarkTimelinePresenter(accountType: account)
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLXQTBookmarkTimelineTabItem(metaData: metaData, account: account)
    }
}

// - Bluesky Specific Tab Items
public struct FLBlueskyFeedsTabItem: FLTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public var key: String { "feeds_\(account)" }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLBlueskyFeedsTabItem(metaData: metaData, account: account)
    }
}

public struct FLBlueskyFeedTabItem: FLTimelineTabItem {
    public let metaData: FLTabMetaData
    public let account: AccountType
    public let feedKey: String
    public var key: String { "feed_\(account)_\(feedKey)" }

    public func createPresenter() -> TimelinePresenter {
        BlueskyFeedTimelinePresenter(accountType: account, uri: feedKey)
    }

    public func update(metaData: FLTabMetaData) -> FLTabItem {
        FLBlueskyFeedTabItem(metaData: metaData, account: account, feedKey: feedKey)
    }
}

// - Localized Keys
public enum FLLocalizedKey: String {
    case home = "home_tab_home_title"
    case notifications = "home_tab_notifications_title"
    case discover = "home_tab_discover_title"
    case me = "home_tab_me_title"
    case settings = "settings_title"
    case mastodonLocal = "mastodon_tab_local_title"
    case mastodonPublic = "mastodon_tab_public_title"
    case featured = "home_tab_featured_title"
    case bookmark = "home_tab_bookmarks_title"
    case favourite = "home_tab_favorite_title"
    case list = "home_tab_list_title"
    case feeds = "home_tab_feeds_title"
    case directMessage = "dm_list_title"
    case profileTimeline = "profile_tab_timeline"
    case profileTimelineWithReply = "profile_tab_timeline_with_reply"
    case profileMedia = "profile_tab_media"
    case profileLikes = "profile_tab_likes"
    case forYou = "home_tab_for_you_title"
    case following = "home_tab_following_title"

    public var localizedString: String {
        NSLocalizedString(rawValue, comment: "")
    }
}

extension FLTitleType {
    static func localized(_ key: FLLocalizedKey) -> FLTitleType {
        .localized(key.rawValue)
    }
}

// - Tab Settings Error
public enum FLTabSettingsError: Error {
    case serializationError(String)
    case corruptionError(String)
    case storageError(String)

    var localizedDescription: String {
        switch self {
        case let .serializationError(message):
            "error: \(message)"
        case let .corruptionError(message):
            "data error: \(message)"
        case let .storageError(message):
            "save error: \(message)"
        }
    }
}

// - Tab Settings Storage
public class FLTabSettingsStorage {
    private static let storageKeyPrefix = "fl_tab_settings_"
    private let userDefaults: UserDefaults

    public init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }

    // 存储启用的tab items
    public func saveEnabledItems(_ items: [String], for platformType: PlatformType, accountKey: MicroBlogKey) {
        let key = Self.storageKey(for: platformType, accountKey: accountKey)
        userDefaults.set(items, forKey: key)
    }

    // 读取启用的tab items
    public func loadEnabledItems(for platformType: PlatformType, accountKey: MicroBlogKey) -> [String] {
        let key = Self.storageKey(for: platformType, accountKey: accountKey)
        return userDefaults.array(forKey: key) as? [String] ?? []
    }

    // 清除特定平台和账号的设置
    public func clear(for platformType: PlatformType, accountKey: MicroBlogKey) {
        let key = Self.storageKey(for: platformType, accountKey: accountKey)
        userDefaults.removeObject(forKey: key)
    }

    // 生成存储key
    private static func storageKey(for platformType: PlatformType, accountKey: MicroBlogKey) -> String {
        let platformString = platformType.name // 使用 PlatformType 的 name 属性
        let accountString = accountKey.id // 使用 MicroBlogKey 的 id 属性
        return "\(storageKeyPrefix)\(platformString)_\(accountString)"
    }
}

// - Tab Settings Manager
public class FLTabSettingsManager {
    private let storage: FLTabSettingsStorage

    public init(storage: FLTabSettingsStorage = FLTabSettingsStorage()) {
        self.storage = storage
    }

    // 保存启用的items
    public func saveEnabledItems(_ items: [FLTabItem], for user: UiUserV2) {
        let itemKeys = items.map(\.key)
        storage.saveEnabledItems(itemKeys, for: user.platformType, accountKey: user.key)
    }

    // 获取启用的items，如果没有存储则返回nil
    public func getEnabledItems(for user: UiUserV2) -> [FLTabItem]? {
        let enabledKeys = storage.loadEnabledItems(for: user.platformType, accountKey: user.key)
        if enabledKeys.isEmpty {
            return nil
        }

        // 从默认配置中找到对应的items
        let defaultItems = FLTabSettings.defaultSecondary(user: user)
        let mainItems = FLTabSettings.defaultPrimary(user: user)

        let allItems = defaultItems + mainItems

        return enabledKeys.compactMap { key in
            allItems.first { $0.key == key }
        }
    }

    // 重置为默认设置
    public func resetToDefault(for user: UiUserV2) {
        storage.clear(for: user.platformType, accountKey: user.key)
    }
}

// - Tab Settings Extensions
public extension FLTabSettings {
    // - Validation
    var isValid: Bool {
        !items.isEmpty && items.allSatisfy(\.isValid)
    }
}

// - Tab Item Extensions
public extension FLTabItem {
    var isValid: Bool {
        !key.isEmpty
    }
}

// - Timeline Tab Item Extensions
public extension FLTimelineTabItem {
    static var `default`: [FLTabItem] {
        [
            FLHomeTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.home),
                    icon: .material(.home)
                ), account: AccountTypeActive.shared
            ),
            FLNotificationTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.notifications),
                    icon: .material(.notification)
                ), account: AccountTypeActive.shared
            ),
            FLDiscoverTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.discover),
                    icon: .material(.search)
                ), account: AccountTypeActive.shared
            ),
            FLProfileTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.me),
                    icon: .material(.profile)
                ),
                account: AccountTypeActive.shared,
                userKey: AccountTypeActive.shared
            )
        ]
    }

    static var guest: [FLTabItem] {
        [
            FLHomeTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.home),
                    icon: .material(.home)
                ), account: AccountTypeGuest.shared
            ),
            FLDiscoverTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.discover),
                    icon: .material(.search)
                ), account: AccountTypeGuest.shared
            ),
            FLSettingsTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.settings),
                    icon: .material(.settings)
                )
            )
        ]
    }
}

// - Home Timeline Tab Item Extensions
public extension FLHomeTimelineTabItem {
    init(accountType: AccountType) {
        self.init(
            metaData: FLTabMetaData(
                title: .localized(.home),
                icon: .material(.home)
            ), account: accountType
        )
    }
}

//  - Profile  Media Tab Items
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

// - AppBar统一存储数据结构
// AppBar项目配置结构
public struct AppBarItemConfig: Codable {
    public let key: String // 项目唯一标识
    public let type: AppBarItemType // 项目类型
    public let addedTime: Date // 添加时间
    public var metadata: [String: String] // 元数据

    public init(key: String, type: AppBarItemType, addedTime: Date = Date(), metadata: [String: String] = [:]) {
        self.key = key
        self.type = type
        self.addedTime = addedTime
        self.metadata = metadata
    }
}

// 项目类型枚举
public enum AppBarItemType: String, Codable {
    case main // 主标签
    case secondary // 次要标签
    case list // 固定列表
    case feed // 固定Feed
}

// 平台配置结构
public struct PlatformAppBarConfig: Codable {
    public var enabledItems: [AppBarItemConfig]
    public let version: Int = 1 // 添加版本字段便于未来扩展

    public init(enabledItems: [AppBarItemConfig] = []) {
        self.enabledItems = enabledItems
    }
}

// 扩展FLTabSettingsStorage添加AppBar配置相关方法
public extension FLTabSettingsStorage {
    // 存储键名前缀
    private static let appBarConfigKeyPrefix = "appbar_config_4_"

    // 获取AppBar配置存储键
    private func appBarConfigKey(for platformType: PlatformType, accountKey: MicroBlogKey) -> String {
        let platformString = platformType.name
        let accountString = accountKey.id
        return "\(Self.appBarConfigKeyPrefix)\(platformString)_\(accountString)"
    }

    // 保存AppBar配置
    func saveAppBarConfig(_ config: PlatformAppBarConfig, for platformType: PlatformType, accountKey: MicroBlogKey) {
        let key = appBarConfigKey(for: platformType, accountKey: accountKey)
        do {
            let data = try JSONEncoder().encode(config)
            userDefaults.set(data, forKey: key)
        } catch {
            FlareLog.error("保存AppBar配置失败: \(error.localizedDescription)")
        }
    }

    // 读取AppBar配置
    func loadAppBarConfig(for platformType: PlatformType, accountKey: MicroBlogKey) -> PlatformAppBarConfig? {
        let key = appBarConfigKey(for: platformType, accountKey: accountKey)
        guard let data = userDefaults.data(forKey: key) else { return nil }

        do {
            return try JSONDecoder().decode(PlatformAppBarConfig.self, from: data)
        } catch {
            FlareLog.error("加载AppBar配置失败: \(error.localizedDescription)")
            return nil
        }
    }

    // 清除AppBar配置
    func clearAppBarConfig(for platformType: PlatformType, accountKey: MicroBlogKey) {
        let key = appBarConfigKey(for: platformType, accountKey: accountKey)
        userDefaults.removeObject(forKey: key)
    }
}

// 扩展FLTabSettingsManager添加AppBar配置相关方法
public extension FLTabSettingsManager {
    // 保存AppBar配置
    func saveAppBarConfig(_ config: PlatformAppBarConfig, for user: UiUserV2) {
        storage.saveAppBarConfig(config, for: user.platformType, accountKey: user.key)
    }

    // 读取AppBar配置
    func getAppBarConfig(for user: UiUserV2) -> PlatformAppBarConfig {
        storage.loadAppBarConfig(for: user.platformType, accountKey: user.key) ?? PlatformAppBarConfig()
    }

    // 将FLTabItem转换为AppBarItemConfig
    func convertTabToConfig(_ tab: FLTabItem, type: AppBarItemType, addedTime: Date = Date()) -> AppBarItemConfig {
        var metadata: [String: String] = [:]

        // 添加标题元数据
        switch tab.metaData.title {
        case let .text(text):
            metadata["title"] = text
        case let .localized(key):
            metadata["title"] = NSLocalizedString(key, comment: "")
        }

        return AppBarItemConfig(
            key: tab.key,
            type: type,
            addedTime: addedTime,
            metadata: metadata
        )
    }

    // 将AppBarItemConfig转换回FLTabItem数组
    func convertConfigToTabs(_ config: PlatformAppBarConfig, for user: UiUserV2, accountType: AccountType) -> [FLTabItem] {
        // 获取默认标签以便查找
        let defaultItems = FLTabSettings.defaultSecondary(user: user)
        let mainItems = FLTabSettings.defaultPrimary(user: user)
        let allDefaultItems = mainItems + defaultItems

        // 按添加时间排序
        let sortedItems = config.enabledItems.sorted { $0.addedTime < $1.addedTime }

        // 转换为FLTabItem
        var result: [FLTabItem] = []

        for item in sortedItems {
            // 首先尝试从默认项中查找
            if let tab = allDefaultItems.first(where: { $0.key == item.key }) {
                result.append(tab)
                continue
            }

            // 特殊处理列表和Feed标签
            if item.type == .list, let components = item.key.split(separator: "_").last {
                let listId = String(components)
                let title = item.metadata["title"] ?? "List"

                let listTab = FLListTimelineTabItem(
                    metaData: FLTabMetaData(
                        title: .text(title),
                        icon: .material(.list)
                    ),
                    account: accountType, // 使用传入的accountType
                    listKey: listId
                )
                result.append(listTab)
            } else if item.type == .feed, let components = item.key.split(separator: "_").last {
                // 只有Bluesky平台才处理Feed
                if user.platformType == .bluesky {
                    let feedId = String(components)
                    let title = item.metadata["title"] ?? "Feed"

                    let feedTab = FLBlueskyFeedTabItem(
                        metaData: FLTabMetaData(
                            title: .text(title),
                            icon: .material(.feeds)
                        ),
                        account: accountType, // 使用传入的accountType
                        feedKey: feedId
                    )
                    result.append(feedTab)
                }
            }
        }

        // 确保至少有主页标签
        if result.isEmpty, let homeItem = mainItems.first {
            result.append(homeItem)
        }

        return result
    }

    // 创建默认配置
    func createDefaultAppBarConfig(for user: UiUserV2) -> PlatformAppBarConfig {
        var items: [AppBarItemConfig] = []

        // 添加主标签，使用1970年确保始终排在最前
        if let homeItem = FLTabSettings.defaultPrimary(user: user).first {
            items.append(convertTabToConfig(
                homeItem,
                type: .main,
                addedTime: Date(timeIntervalSince1970: 0)
            ))
        }

        // 添加默认次要标签
        let secondaryItems = FLTabSettings.defaultSecondary(user: user).prefix(3)
        for (index, item) in secondaryItems.enumerated() {
            items.append(convertTabToConfig(
                item,
                type: .secondary,
                addedTime: Date().addingTimeInterval(Double(index) * 60)
            ))
        }

        return PlatformAppBarConfig(enabledItems: items)
    }

    // 重置为默认AppBar配置
    func resetToDefaultAppBarConfig(for user: UiUserV2) {
        let defaultConfig = createDefaultAppBarConfig(for: user)
        saveAppBarConfig(defaultConfig, for: user)
    }
}
