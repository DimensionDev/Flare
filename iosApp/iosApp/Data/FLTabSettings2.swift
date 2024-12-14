//import Foundation
//import SwiftUI
//import shared
//
//// MARK: - Core Data Models
//
//// MARK: - External Dependencies
//
//// 这些类型来自shared项目，可以直接使用，不需要翻译
//// /shared/src/commonMain/kotlin/dev/dimension/flare/model/
//// - AccountType
//// - MicroBlogKey
//// - PlatformType
//// - UiUserV2
//// - TimelinePresenter
//// - HomeTimelinePresenter
//// - ListTimelinePresenter
//
//// 这些类型需要从其他Swift文件中导入
//// TODO: 等待翻译
///*
//*/
//
///// 主要的标签页设置数据模型
//struct FLTabSettings: Codable {
//    var items: [FLTabItem]
//    var secondaryItems: [FLTabItem]?
//    var homeTabs: [MicroBlogKey: [FLTimelineTabItem]]  // 直接使用shared中的MicroBlogKey
//    
//    init(items: [FLTabItem] = FLTimelineTabItem.default,
//         secondaryItems: [FLTabItem]? = nil,
//         homeTabs: [MicroBlogKey: [FLTimelineTabItem]] = [:]) {
//        self.items = items
//        self.secondaryItems = secondaryItems
//        self.homeTabs = homeTabs
//    }
//}
//
///// 标签页基础协议
//protocol FLTabItem: Codable {
//    var metaData: FLTabMetaData { get }
//    var account: AccountType { get }  // 直接使用shared中的AccountType
//    var key: String { get }
//    
//    func update(metaData: FLTabMetaData) -> FLTabItem
//}
//
///// 标签页元数据
//struct FLTabMetaData: Codable {
//    var title: FLTitleType
//    var icon: FLIconType
//}
//
///// 标题类型
//enum FLTitleType: Codable {
//    case text(content: String)
//    case localized(key: LocalizedKey)
//    
//    enum LocalizedKey: String, Codable {
//        case home
//        case notifications
//        case discover
//        case me
//        case settings
//        case mastodonLocal
//        case mastodonPublic
//        case featured
//        case bookmark
//        case favourite
//        case list
//        case feeds
//        case directMessage
//        
//        var localizedString: String {
//            switch self {
//            case .home: return NSLocalizedString("home_tab_home_title", comment: "")
//            case .notifications: return NSLocalizedString("home_tab_notifications_title", comment: "")
//            case .discover: return NSLocalizedString("home_tab_discover_title", comment: "")
//            case .me: return NSLocalizedString("home_tab_me_title", comment: "")
//            case .settings: return NSLocalizedString("settings_title", comment: "")
//            case .mastodonLocal: return NSLocalizedString("mastodon_tab_local_title", comment: "")
//            case .mastodonPublic: return NSLocalizedString("mastodon_tab_public_title", comment: "")
//            case .featured: return NSLocalizedString("home_tab_featured_title", comment: "")
//            case .bookmark: return NSLocalizedString("home_tab_bookmarks_title", comment: "")
//            case .favourite: return NSLocalizedString("home_tab_favorite_title", comment: "")
//            case .list: return NSLocalizedString("home_tab_list_title", comment: "")
//            case .feeds: return NSLocalizedString("home_tab_feeds_title", comment: "")
//            case .directMessage: return NSLocalizedString("dm_list_title", comment: "")
//            }
//        }
//    }
//}
//
///// 图标类型
//enum FLIconType: Codable {
//    case avatar(userKey: MicroBlogKey)  // 直接使用shared中的MicroBlogKey
//    case material(icon: MaterialIcon)
//    case mixed(icon: MaterialIcon, userKey: MicroBlogKey)  // 直接使用shared中的MicroBlogKey
//    
//    enum MaterialIcon: String, Codable {
//        case home
//        case notification
//        case search
//        case profile
//        case settings
//        case local
//        case world
//        case featured
//        case bookmark
//        case heart
//        case twitter
//        case mastodon
//        case misskey
//        case bluesky
//        case list
//        case feeds
//        case messages
//        
//        var icon: Image {
//            switch self {
//            case .home: return Image(systemName: "house")
//            case .notification: return Image(systemName: "bell")
//            case .search: return Image(systemName: "magnifyingglass")
//            case .profile: return Image(systemName: "person.circle")
//            case .settings: return Image(systemName: "gear")
//            case .local: return Image(systemName: "person.2")
//            case .world: return Image(systemName: "globe")
//            case .featured: return Image(systemName: "star")
//            case .bookmark: return Image(systemName: "bookmark")
//            case .heart: return Image(systemName: "heart")
//            case .twitter: return Image("twitter") // 自定义图标
//            case .mastodon: return Image("mastodon") // 自定义图标
//            case .misskey: return Image("misskey") // 自定义图标
//            case .bluesky: return Image("bluesky") // 自定义图标
//            case .list: return Image(systemName: "list.bullet")
//            case .feeds: return Image(systemName: "newspaper")
//            case .messages: return Image(systemName: "message")
//            }
//        }
//    }
//}
//
//// MARK: - Timeline Tab Items
//
///// 时间线标签页基础协议
//protocol FLTimelineTabItem: FLTabItem {
//    func createPresenter() -> TimelinePresenter // 返回shared中的TimelinePresenter
//    
//    // 添加静态工厂方法
//    static var `default`: [FLTabItem] { get }
//    static func defaultPrimary(user: UiUserV2) -> [FLTabItem]
//    static func defaultSecondary(user: UiUserV2) -> [FLTabItem]
//}
//
///// 主页时间线标签页
//struct FLHomeTimelineTabItem: FLTimelineTabItem, Codable {
//    var metaData: FLTabMetaData
//    var account: AccountType
//    var key: String { "home_\(account)" }
//    
//    func update(metaData: FLTabMetaData) -> FLTabItem {
//        FLHomeTimelineTabItem(metaData: metaData, account: account)
//    }
//    
//    func createPresenter() -> TimelinePresenter {
//        HomeTimelinePresenter(accountType: account)
//    }
//}
//
///// 列表时间线标签页
//struct FLListTimelineTabItem: FLTimelineTabItem, Codable {
//    var metaData: FLTabMetaData
//    var account: AccountType
//    var listId: String
//    var key: String { "list_\(account)" }
//    
//    func update(metaData: FLTabMetaData) -> FLTabItem {
//        FLListTimelineTabItem(metaData: metaData, account: account, listId: listId)
//    }
//    
//    func createPresenter() -> TimelinePresenter {
//        ListTimelinePresenter(accountType: account, listId: listId)
//    }
//}
//
///// 所有列表标签页
//struct FLAllListTabItem: FLTabItem, Codable {
//    var metaData: FLTabMetaData
//    var account: AccountType
//    var key: String { "all_list_\(account)" }
//    
//    func update(metaData: FLTabMetaData) -> FLTabItem {
//        FLAllListTabItem(metaData: metaData, account: account)
//    }
//}
//
///// 通知标签页
//struct FLNotificationTabItem: FLTabItem, Codable {
//    var metaData: FLTabMetaData
//    var account: AccountType
//    var key: String { "notification_\(account)" }
//    
//    func update(metaData: FLTabMetaData) -> FLTabItem {
//        FLNotificationTabItem(metaData: metaData, account: account)
//    }
//}
//
//// MARK: - Platform Specific Items
//
///// Mastodon 相关标签页
//enum FLMastodon {
//    struct LocalTimelineTabItem: FLTimelineTabItem, Codable {
//        var metaData: FLTabMetaData
//        var account: AccountType
//        var key: String { "mastodon_local_\(account)" }
//        
//        func update(metaData: FLTabMetaData) -> FLTabItem {
//            LocalTimelineTabItem(metaData: metaData, account: account)
//        }
//        
//        func createPresenter() -> TimelinePresenter {
//            MastodonLocalTimelinePresenter(accountType: account)
//        }
//    }
//    
//    struct PublicTimelineTabItem: FLTimelineTabItem, Codable {
//        var metaData: FLTabMetaData
//        var account: AccountType
//        var key: String { "mastodon_public_\(account)" }
//        
//        func update(metaData: FLTabMetaData) -> FLTabItem {
//            PublicTimelineTabItem(metaData: metaData, account: account)
//        }
//        
//        func createPresenter() -> TimelinePresenter {
//            MastodonPublicTimelinePresenter(accountType: account)
//        }
//    }
//    
//    struct BookmarkTimelineTabItem: FLTimelineTabItem, Codable {
//        var metaData: FLTabMetaData
//        var account: AccountType
//        var key: String { "mastodon_bookmark_\(account)" }
//        
//        func update(metaData: FLTabMetaData) -> FLTabItem {
//            BookmarkTimelineTabItem(metaData: metaData, account: account)
//        }
//        
//        func createPresenter() -> TimelinePresenter {
//            MastodonBookmarkTimelinePresenter(accountType: account)
//        }
//    }
//    
//    struct FavouriteTimelineTabItem: FLTimelineTabItem, Codable {
//        var metaData: FLTabMetaData
//        var account: AccountType
//        var key: String { "mastodon_favourite_\(account)" }
//        
//        func update(metaData: FLTabMetaData) -> FLTabItem {
//            FavouriteTimelineTabItem(metaData: metaData, account: account)
//        }
//        
//        func createPresenter() -> TimelinePresenter {
//            MastodonFavouriteTimelinePresenter(accountType: account)
//        }
//    }
//}
//
///// Misskey 相关标签页
//enum FLMisskey {
//    struct LocalTimelineTabItem: FLTimelineTabItem, Codable {
//        var metaData: FLTabMetaData
//        var account: AccountType
//        var key: String { "misskey_local_\(account)" }
//        
//        func update(metaData: FLTabMetaData) -> FLTabItem {
//            LocalTimelineTabItem(metaData: metaData, account: account)
//        }
//        
//        func createPresenter() -> TimelinePresenter {
//            MisskeyLocalTimelinePresenter(accountType: account)
//        }
//    }
//    
//    struct GlobalTimelineTabItem: FLTimelineTabItem, Codable {
//        var metaData: FLTabMetaData
//        var account: AccountType
//        var key: String { "misskey_global_\(account)" }
//        
//        func update(metaData: FLTabMetaData) -> FLTabItem {
//            GlobalTimelineTabItem(metaData: metaData, account: account)
//        }
//        
//        func createPresenter() -> TimelinePresenter {
//            MisskeyPublicTimelinePresenter(accountType: account)
//        }
//    }
//}
//
///// XQT (Twitter) 相关标签页
//enum FLXQT {
//    struct FeaturedTimelineTabItem: FLTimelineTabItem, Codable {
//        var metaData: FLTabMetaData
//        var account: AccountType
//        var key: String { "xqt_featured_\(account)" }
//        
//        func update(metaData: FLTabMetaData) -> FLTabItem {
//            FeaturedTimelineTabItem(metaData: metaData, account: account)
//        }
//        
//        func createPresenter() -> TimelinePresenter {
//            XQTFeaturedTimelinePresenter(accountType: account)
//        }
//    }
//    
//    struct BookmarkTimelineTabItem: FLTimelineTabItem, Codable {
//        var metaData: FLTabMetaData
//        var account: AccountType
//        var key: String { "xqt_bookmark_\(account)" }
//        
//        func update(metaData: FLTabMetaData) -> FLTabItem {
//            BookmarkTimelineTabItem(metaData: metaData, account: account)
//        }
//        
//        func createPresenter() -> TimelinePresenter {
//            XQTBookmarkTimelinePresenter(accountType: account)
//        }
//    }
//}
//
///// Bluesky 相关标签页
//enum FLBluesky {
//    struct FeedTabItem: FLTimelineTabItem, Codable {
//        var metaData: FLTabMetaData
//        var account: AccountType
//        var feedUri: String
//        var key: String { "bluesky_feed_\(account)" }
//        
//        func update(metaData: FLTabMetaData) -> FLTabItem {
//            FeedTabItem(metaData: metaData, account: account, feedUri: feedUri)
//        }
//        
//        func createPresenter() -> TimelinePresenter {
//            BlueskyFeedTimelinePresenter(accountType: account, uri: feedUri)
//        }
//    }
//}
//
///// 个人资料标签页
//struct FLProfileTabItem: FLTabItem, Codable {
//    var metaData: FLTabMetaData
//    var account: AccountType
//    var userKey: MicroBlogKey
//    var key: String { "profile_\(userKey)" }
//    
//    func update(metaData: FLTabMetaData) -> FLTabItem {
//        FLProfileTabItem(metaData: metaData, account: account, userKey: userKey)
//    }
//}
//
///// 发现标签页
//struct FLDiscoverTabItem: FLTabItem, Codable {
//    var metaData: FLTabMetaData
//    var account: AccountType
//    var key: String { "discover_\(account)" }
//    
//    func update(metaData: FLTabMetaData) -> FLTabItem {
//        FLDiscoverTabItem(metaData: metaData, account: account)
//    }
//}
//
///// 设置标签页
//struct FLSettingsTabItem: FLTabItem, Codable {
//    var metaData: FLTabMetaData {
//        FLTabMetaData(
//            title: .localized(key: .settings),
//            icon: .material(icon: .settings)
//        )
//    }
//    var account: AccountType { .active }
//    var key: String { "settings" }
//    
//    func update(metaData: FLTabMetaData) -> FLTabItem { self }
//}
//
///// 私信标签页
//struct FLDirectMessageTabItem: FLTabItem, Codable {
//    var metaData: FLTabMetaData
//    var account: AccountType
//    var key: String { "direct_message_\(account)" }
//    
//    func update(metaData: FLTabMetaData) -> FLTabItem {
//        FLDirectMessageTabItem(metaData: metaData, account: account)
//    }
//}
//
//// MARK: - Default Implementations
//extension FLTimelineTabItem {
//    static var `default`: [FLTabItem] {
//        [
//            FLHomeTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .home),
//                    icon: .material(icon: .home)
//                ),
//                account: .active
//            ),
//            FLNotificationTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .notifications),
//                    icon: .material(icon: .notification)
//                ),
//                account: .active
//            ),
//            FLDiscoverTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .discover),
//                    icon: .material(icon: .search)
//                ),
//                account: .active
//            ),
//            FLProfileTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .me),
//                    icon: .material(icon: .profile)
//                ),
//                account: .active,
//                userKey: .active
//            )
//        ]
//    }
//    
//    static var guest: [FLTabItem] {
//        [
//            FLHomeTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .home),
//                    icon: .material(icon: .home)
//                ),
//                account: .guest
//            ),
//            FLDiscoverTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .discover),
//                    icon: .material(icon: .search)
//                ),
//                account: .guest
//            ),
//            FLSettingsTabItem()
//        ]
//    }
//    
//    static func defaultPrimary(user: UiUserV2) -> [FLTabItem] {
//        switch user.platformType {
//        case .mastodon:
//            return mastodon(accountKey: user.key)
//        case .misskey:
//            return misskey(accountKey: user.key)
//        case .bluesky:
//            return bluesky(accountKey: user.key)
//        case .xQt:
//            return xqt(accountKey: user.key)
//        case .vVo:
//            return vvo(accountKey: user.key)
//        }
//    }
//    
//    static func defaultSecondary(user: UiUserV2) -> [FLTabItem] {
//        switch user.platformType {
//        case .mastodon:
//            return defaultMastodonSecondaryItems(accountKey: user.key)
//        case .misskey:
//            return defaultMisskeySecondaryItems(accountKey: user.key)
//        case .bluesky:
//            return defaultBlueskySecondaryItems(accountKey: user.key)
//        case .xQt:
//            return defaultXQTSecondaryItems(accountKey: user.key)
//        case .vVo:
//            return defaultVVOSecondaryItems(accountKey: user.key)
//        }
//    }
//    
//    private static func mastodon(accountKey: MicroBlogKey) -> [FLTabItem] {
//        [
//            FLHomeTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .home),
//                    icon: .mixed(icon: .home, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLNotificationTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .notifications),
//                    icon: .mixed(icon: .notification, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLDiscoverTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .discover),
//                    icon: .mixed(icon: .search, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLProfileTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .me),
//                    icon: .mixed(icon: .profile, userKey: accountKey)
//                ),
//                account: .specific(accountKey),
//                userKey: accountKey
//            )
//        ]
//    }
//    
//    private static func defaultMastodonSecondaryItems(accountKey: MicroBlogKey) -> [FLTabItem] {
//        [
//            FLMastodon.LocalTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .mastodonLocal),
//                    icon: .mixed(icon: .local, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLMastodon.PublicTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .mastodonPublic),
//                    icon: .mixed(icon: .world, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLMastodon.BookmarkTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .bookmark),
//                    icon: .mixed(icon: .bookmark, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLMastodon.FavouriteTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .favourite),
//                    icon: .mixed(icon: .heart, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            )
//        ]
//    }
//    
//    private static func misskey(accountKey: MicroBlogKey) -> [FLTabItem] {
//        [
//            FLHomeTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .home),
//                    icon: .mixed(icon: .home, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLNotificationTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .notifications),
//                    icon: .mixed(icon: .notification, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLDiscoverTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .discover),
//                    icon: .mixed(icon: .search, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLProfileTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .me),
//                    icon: .mixed(icon: .profile, userKey: accountKey)
//                ),
//                account: .specific(accountKey),
//                userKey: accountKey
//            )
//        ]
//    }
//    
//    private static func defaultMisskeySecondaryItems(accountKey: MicroBlogKey) -> [FLTabItem] {
//        [
//            FLMisskey.LocalTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .mastodonLocal),
//                    icon: .mixed(icon: .local, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLMisskey.GlobalTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .mastodonPublic),
//                    icon: .mixed(icon: .world, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            )
//        ]
//    }
//    
//    private static func bluesky(accountKey: MicroBlogKey) -> [FLTabItem] {
//        [
//            FLHomeTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .home),
//                    icon: .mixed(icon: .home, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLNotificationTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .notifications),
//                    icon: .mixed(icon: .notification, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLDiscoverTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .discover),
//                    icon: .mixed(icon: .search, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLProfileTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .me),
//                    icon: .mixed(icon: .profile, userKey: accountKey)
//                ),
//                account: .specific(accountKey),
//                userKey: accountKey
//            )
//        ]
//    }
//    
//    private static func defaultBlueskySecondaryItems(accountKey: MicroBlogKey) -> [FLTabItem] {
//        [
//            FLBluesky.FeedTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .feeds),
//                    icon: .mixed(icon: .feeds, userKey: accountKey)
//                ),
//                account: .specific(accountKey),
//                feedUri: ""
//            )
//        ]
//    }
//    
//    private static func xqt(accountKey: MicroBlogKey) -> [FLTabItem] {
//        [
//            FLHomeTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .home),
//                    icon: .mixed(icon: .home, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLNotificationTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .notifications),
//                    icon: .mixed(icon: .notification, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLDiscoverTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .discover),
//                    icon: .mixed(icon: .search, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLProfileTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .me),
//                    icon: .mixed(icon: .profile, userKey: accountKey)
//                ),
//                account: .specific(accountKey),
//                userKey: accountKey
//            )
//        ]
//    }
//    
//    private static func defaultXQTSecondaryItems(accountKey: MicroBlogKey) -> [FLTabItem] {
//        [
//            FLXQT.FeaturedTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .featured),
//                    icon: .mixed(icon: .featured, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLXQT.BookmarkTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .bookmark),
//                    icon: .mixed(icon: .bookmark, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            )
//        ]
//    }
//    
//    private static func vvo(accountKey: MicroBlogKey) -> [FLTabItem] {
//        [
//            FLHomeTimelineTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .home),
//                    icon: .mixed(icon: .home, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLNotificationTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .notifications),
//                    icon: .mixed(icon: .notification, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLDiscoverTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .discover),
//                    icon: .mixed(icon: .search, userKey: accountKey)
//                ),
//                account: .specific(accountKey)
//            ),
//            FLProfileTabItem(
//                metaData: FLTabMetaData(
//                    title: .localized(key: .me),
//                    icon: .mixed(icon: .profile, userKey: accountKey)
//                ),
//                account: .specific(accountKey),
//                userKey: accountKey
//            )
//        ]
//    }
//    
//    private static func defaultVVOSecondaryItems(accountKey: MicroBlogKey) -> [FLTabItem] {
//        [] // VVO平台暂无特殊二级标签页
//    }
//}
//
//// MARK: - Data Serialization
//
///// TabSettings序列化器
//class FLTabSettingsSerializer {
//    static let shared = FLTabSettingsSerializer()
//    
//    private init() {}
//    
//    func readFrom(_ data: Data) throws -> FLTabSettings {
//        let decoder = PropertyListDecoder()
//        return try decoder.decode(FLTabSettings.self, from: data)
//    }
//    
//    func writeTo(_ settings: FLTabSettings) throws -> Data {
//        let encoder = PropertyListEncoder()
//        return try encoder.encode(settings)
//    }
//    
//    var defaultValue: FLTabSettings {
//        FLTabSettings()
//    }
//}
//
//// MARK: - Data Storage
//
///// TabSettings的数据存储管理器，对应Android的DataStore实现
//class FLTabSettingsStore {
//    static let shared = FLTabSettingsStore()
//    
//    private let defaults = UserDefaults.standard
//    private let storageKey = "tab_settings.pb"
//    private let serializer = FLTabSettingsSerializer.shared
//    
//    private init() {}
//    
//    func save(_ settings: FLTabSettings) throws {
//        let data = try serializer.writeTo(settings)
//        defaults.set(data, forKey: storageKey)
//    }
//    
//    func load() throws -> FLTabSettings {
//        guard let data = defaults.data(forKey: storageKey) else {
//            return serializer.defaultValue
//        }
//        return try serializer.readFrom(data)
//    }
//}
//
//// MARK: - Extensions
//
//extension FLTabSettings {
//    static var `default`: FLTabSettings {
//        FLTabSettings(
//            items: FLTimelineTabItem.default,
//            secondaryItems: nil,
//            homeTabs: [:]
//        )
//    }
//}
