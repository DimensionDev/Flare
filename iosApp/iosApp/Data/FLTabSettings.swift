import Foundation
import shared
import SwiftUI
import ObjectiveC

// 类型别名定义
//typealias FLUser = UiUserV2
typealias FLMicroBlogKey = MicroBlogKey

// - Tab Settings
// : Codable
public struct FLTabSettings {
    public let items: [FLTabItem]
    public let secondaryItems: [FLTabItem]?
    public let homeTabs: [String: [FLTabItem]]

    enum CodingKeys: String, CodingKey {
        case items
        case secondaryItems
        case homeTabs
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
                    ), account:AccountTypeGuest.shared
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
            return mastodon(accountKey: user.key)
        case .misskey:
            return misskey(accountKey: user.key)
        case .bluesky:
            return bluesky(accountKey: user.key)
        case .xQt:
            return xqt(accountKey: user.key)
        case .vvo:
            return vvo(accountKey: user.key)
        }
    }

    public static func defaultSecondary(user: UiUserV2) -> [FLTabItem] {
        switch user.platformType {
        case .mastodon:
            return defaultMastodonSecondaryItems(accountKey: user.key)
        case .misskey:
            return defaultMisskeySecondaryItems(accountKey: user.key)
        case .bluesky:
            return defaultBlueskySecondaryItems(accountKey: user.key)
        case .xQt:
            return defaultXqtSecondaryItems(accountKey: user.key)
        case .vvo:
            return defaultVVOSecondaryItems(accountKey: user.key)
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
            ),
            FLAllListTabItem(
                metaData: FLTabMetaData(
                    title: .localized("List"),
                    icon: .mixed(.list, userKey: accountKey)
                ),
                account: AccountTypeSpecific(accountKey: accountKey)
            )
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

    private static func defaultBlueskySecondaryItems(accountKey: FLMicroBlogKey) -> [FLTabItem] {
        [
            FLAllListTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.list),
                    icon: .mixed(.list, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            ),
            FLBlueskyFeedsTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.feeds),
                    icon: .mixed(.feeds, userKey: accountKey)
                ), account: AccountTypeActive.shared
            ),
            FLDirectMessageTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.directMessage),
                    icon: .mixed(.messages, userKey: accountKey)
                ), account: AccountTypeSpecific(accountKey: accountKey)
            )
        ]
    }

    // - XQT (Twitter) Platform Items
    private static func xqt(accountKey: FLMicroBlogKey) -> [FLTabItem] {
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

    private static func defaultXqtSecondaryItems(accountKey: FLMicroBlogKey) -> [FLTabItem] {
        [
            FLXQTFeaturedTimelineTabItem(
                metaData: FLTabMetaData(
                    title: .localized(.featured),
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

    private static func defaultVVOSecondaryItems(accountKey: FLMicroBlogKey) -> [FLTabItem] {
        [] // VVO 没有次要标签项
    }
}

// - Material Icon Type
public enum FLMaterialIcon: String, Codable {
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
            return Image(systemName: "house.fill")
        case .notification:
            return Image(systemName: "bell.fill")
        case .search:
            return Image(systemName: "magnifyingglass")
        case .profile:
            return Image(systemName: "person.circle.fill")
        case .settings:
            return Image(systemName: "gear")
        case .local:
            return Image(systemName: "person.2.fill")
        case .world:
            return Image(systemName: "globe")
        case .featured:
            return Image(systemName: "star.fill")
        case .bookmark:
            return Image(systemName: "bookmark.fill")
        case .heart:
            return Image(systemName: "heart.fill")
        case .twitter:
            return Image(systemName: "bird.fill")
        case .mastodon:
            return Image("mastodon") // 需要添加自定义图标
        case .misskey:
            return Image("misskey") // 需要添加自定义图标
        case .bluesky:
            return Image("bluesky") // 需要添加自定义图标
        case .list:
            return Image(systemName: "list.bullet")
        case .feeds:
            return Image(systemName: "dot.radiowaves.left.and.right")
        case .messages:
            return Image(systemName: "message.fill")
        case .media:
            return Image(systemName: "photo.on.rectangle")
        }
    }
}

// - Tab Item Protocol
// : Codable
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
public struct FLTabMetaData /* : Codable */ {
    public let title: FLTitleType
    public let icon: FLIconType

//    enum CodingKeys: String, CodingKey {
//        case title
//        case icon
//    }
}

// - Title Type
public enum FLTitleType /* : Codable */ {
    case text(String)
    case localized(String)

//    enum CodingKeys: String, CodingKey {
//        case text
//        case localized
//    }
//
//    public func encode(to encoder: Encoder) throws {
//        var container = encoder.container(keyedBy: CodingKeys.self)
//        switch self {
//        case .text(let value):
//            try container.encode(value, forKey: .text)
//        case .localized(let value):
//            try container.encode(value, forKey: .localized)
//        }
//    }
//
//    public init(from decoder: Decoder) throws {
//        let container = try decoder.container(keyedBy: CodingKeys.self)
//        if let value = try? container.decode(String.self, forKey: .text) {
//            self = .text(value)
//        } else if let value = try? container.decode(String.self, forKey: .localized) {
//            self = .localized(value)
//        } else {
//            throw DecodingError.dataCorruptedError(
//                forKey: .text,
//                in: container,
//                debugDescription: "Invalid title type"
//            )
//        }
//    }
}

// - Icon Type
public enum FLIconType /* : Codable */ {
    case avatar(userKey: MicroBlogKey)
    case material(String)
    case mixed([String])

//    enum CodingKeys: String, CodingKey {
//        case avatar
//        case material
//        case mixed
//    }
//
//    public func encode(to encoder: Encoder) throws {
//        var container = encoder.container(keyedBy: CodingKeys.self)
//        switch self {
//        case .avatar(let userKey):
//            try container.encode(true, forKey: .avatar)
//            try container.encode(userKey, forKey: .material)
//        case .material(let value):
//            try container.encode(value, forKey: .material)
//        case .mixed(let value):
//            try container.encode(value, forKey: .mixed)
//        }
//    }
//
//    public init(from decoder: Decoder) throws {
//        let container = try decoder.container(keyedBy: CodingKeys.self)
//        if let _ = try? container.decode(Bool.self, forKey: .avatar) {
//            let userKey = try container.decode(MicroBlogKey.self, forKey: .material)
//            self = .avatar(userKey: userKey)
//        } else if let value = try? container.decode(String.self, forKey: .material) {
//            self = .material(value)
//        } else if let value = try? container.decode([String].self, forKey: .mixed) {
//            self = .mixed(value)
//        } else {
//            throw DecodingError.dataCorruptedError(
//                forKey: .avatar,
//                in: container,
//                debugDescription: "Invalid icon type"
//            )
//        }
//    }
}

// - IconType Extensions
extension FLIconType {
    static func material(_ icon: FLMaterialIcon) -> FLIconType {
        .material(icon.rawValue)
    }

    static func mixed(_ icon: FLMaterialIcon, userKey: FLMicroBlogKey) -> FLIconType {
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

    public func update(metaData: FLTabMetaData) -> FLTabItem {
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

    public var localizedString: String {
        NSLocalizedString(self.rawValue, comment: "")
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
        case .serializationError(let message):
            return "序列化错误: \(message)"
        case .corruptionError(let message):
            return "数据损坏: \(message)"
        case .storageError(let message):
            return "存储错误: \(message)"
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
        let itemKeys = items.map { $0.key }
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
        return enabledKeys.compactMap { key in
            defaultItems.first { $0.key == key }
        }
    }

    // 重置为默认设置
    public func resetToDefault(for user: UiUserV2) {
        storage.clear(for: user.platformType, accountKey: user.key)
    }
}

// - Tab Settings Extensions
extension FLTabSettings {
    // - Validation
    public var isValid: Bool {
        !items.isEmpty && items.allSatisfy { $0.isValid }
    }
}

// - Tab Item Extensions
extension FLTabItem {
    public var isValid: Bool {
        !key.isEmpty
    }
}

// - Combine Support
//#if canImport(Combine)
//import Combine
//
//extension FLTabSettingsManager {
//    public func settingsPublisher() -> AnyPublisher<FLTabSettings, Never> {
//        NotificationCenter.default.publisher(for: UserDefaults.didChangeNotification)
//            .map { [weak self] _ in
//                self?.getSettings() ?? .default
//            }
//            .eraseToAnyPublisher()
//    }
//}
//#endif

// - SwiftUI Support
//#if canImport(SwiftUI)
//import SwiftUI
//
//extension FLTabSettings {
//    @propertyWrapper
//    public struct UserDefault<T: Codable> {
//        private let key: String
//        private let defaultValue: T
//        private let storage: UserDefaults
//
//        public init(key: String, defaultValue: T, storage: UserDefaults = .standard) {
//            self.key = key
//            self.defaultValue = defaultValue
//            self.storage = storage
//        }
//
//        public var wrappedValue: T {
//            get {
//                guard let data = storage.data(forKey: key) else {
//                    return defaultValue
//                }
//
//                do {
//                    return try JSONDecoder().decode(T.self, from: data)
//                } catch {
//                    return defaultValue
//                }
//            }
//            set {
//                do {
//                    let data = try JSONEncoder().encode(newValue)
//                    storage.set(data, forKey: key)
//                } catch {
//                    print("保存设置失败: \(error.localizedDescription)")
//                }
//            }
//        }
//    }
//}

//@available(iOS 13.0, *)
//extension FLTabSettingsManager: ObservableObject {
//    @Published public private(set) var currentSettings: FLTabSettings {
//        didSet {
//            updateSettings(currentSettings)
//        }
//    }
//
//    public convenience init() {
//        self.init(storage: FLTabSettingsStorage())
//        self.currentSettings = getSettings()
//
//        #if canImport(Combine)
//        settingsPublisher()
//            .assign(to: &$currentSettings)
//        #endif
//    }
//}
//#endif

// - Timeline Tab Item Extensions
extension FLTimelineTabItem {
    public static var `default`: [FLTabItem] {
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

    public static var guest: [FLTabItem] {
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
extension FLHomeTimelineTabItem {
    public init(accountType: AccountType) {
        self.init(
            metaData: FLTabMetaData(
                title: .localized(.home),
                icon: .material(.home)
            ), account: accountType
        )
    }
}

// - Tab Settings Serialization
//public protocol FLTabSettingsSerializer {
//    func serialize(_ settings: FLTabSettings) throws -> Data
//    func deserialize(_ data: Data) throws -> FLTabSettings
//    var defaultValue: FLTabSettings { get }
//}
//
//public class FLDefaultTabSettingsSerializer: FLTabSettingsSerializer {
//    public init() {}
//
//    public func serialize(_ settings: FLTabSettings) throws -> Data {
//        try JSONEncoder().encode(settings)
//    }
//
//    public func deserialize(_ data: Data) throws -> FLTabSettings {
//        do {
//            return try JSONDecoder().decode(FLTabSettings.self, from: data)
//        } catch {
//            throw FLTabSettingsError.corruptionError("无法解析数据: \(error.localizedDescription)")
//        }
//    }
//
//    public var defaultValue: FLTabSettings {
//        FLTabSettings(items: [], secondaryItems: nil, homeTabs: [:])
//    }
//}

// - Tab Settings Storage Extensions
//extension FLTabSettingsStorage {
//    private struct AssociatedKeys {
//        static var serializer = UnsafeRawPointer(bitPattern: "fl_tab_settings_serializer".hashValue)!
//    }
//
//    public convenience init(serializer: FLTabSettingsSerializer = FLDefaultTabSettingsSerializer()) {
//        self.init()
//        self.serializer = serializer
//    }
//
//    private var serializer: FLTabSettingsSerializer {
//        get {
//            guard let value = objc_getAssociatedObject(self, AssociatedKeys.serializer) as? FLTabSettingsSerializer else {
//                let defaultSerializer = FLDefaultTabSettingsSerializer()
//                self.serializer = defaultSerializer
//                return defaultSerializer
//            }
//            return value
//        }
//        set {
//            objc_setAssociatedObject(
//                self,
//                AssociatedKeys.serializer,
//                newValue,
//                .OBJC_ASSOCIATION_RETAIN_NONATOMIC
//            )
//        }
//    }
//}
