import KotlinSharedUI
import SwiftUI
import FlareAppleCore
import FlareAppleUI

enum Route: Hashable, Identifiable {
    case empty
    case home
    case notification
    case discover
    case serviceSelect
    case timeline(UiTimelineTabItem)
    case statusDetail(AccountType, MicroBlogKey)
    case profileUser(AccountType, MicroBlogKey)
    case profileUserNameWithHost(AccountType, String, String)
    case userFollowing(AccountType, MicroBlogKey)
    case userFans(AccountType, MicroBlogKey)
    case deepLinkAccountPicker(String, [MicroBlogKey: Route])
    case externalLink(String)

    var id: Int {
        return self.hashValue
    }
    
    static func == (lhs: Route, rhs: Route) -> Bool {
        switch (lhs, rhs) {
        case (.timeline(let lhs), .timeline(let rhs)):
            return lhs.id == rhs.id
        default:
            return lhs.hashValue == rhs.hashValue
        }
    }

    func hash(into hasher: inout Hasher) {
        switch self {
        case .timeline(let item):
            hasher.combine("timeline")
            hasher.combine(item.id)
        default:
            hasher.combine(String(describing: self))
        }
    }

    @MainActor
    @ViewBuilder
    func view(
        onNavigate: @escaping (Route) -> Void,
        goBack: @escaping () -> Void
    ) -> some View {
        switch self {
        case .empty:
            EmptyView()
        case .home:
            HomeScreen()
        case .notification:
            PlaceholderPanel(destination: .notifications)
        case .discover:
            PlaceholderPanel(destination: .discover)
        case .serviceSelect:
            ServiceSelectionScreen(toHome: goBack)
        case .timeline(let item):
            TimelineScreen(tabItem: item, allowGalleryMode: true)
                .navigationTitle(item.title.text)
        case .statusDetail(let accountType, let statusKey):
            StatusDetailScreen(accountType: accountType, statusKey: statusKey)
        case .profileUser(let accountType, let userKey):
            ProfileScreen(
                accountType: accountType,
                userKey: userKey,
                onFollowingClick: { key in onNavigate(.userFollowing(accountType, key)) },
                onFansClick: { key in onNavigate(.userFans(accountType, key)) },
                goBack: goBack
            )
        case .profileUserNameWithHost(let accountType, let userName, let host):
            ProfileWithUserNameAndHostScreen(
                userName: userName,
                host: host,
                accountType: accountType,
                onFollowingClick: { key in onNavigate(.userFollowing(accountType, key)) },
                onFansClick: { key in onNavigate(.userFans(accountType, key)) },
                goBack: goBack
            )
        case .userFollowing(let accountType, let userKey):
            UserListScreen(accountType: accountType, userKey: userKey, isFollowing: true)
        case .userFans(let accountType, let userKey):
            UserListScreen(accountType: accountType, userKey: userKey, isFollowing: false)
        case .deepLinkAccountPicker(let originalUrl, let data):
            DeepLinkAccountPickerView(
                originalUrl: originalUrl,
                data: data,
                onNavigate: onNavigate
            )
        case .externalLink:
            EmptyView()
        }
    }
}

extension Route {
    static func fromDeepLinkRoute(deeplinkRoute: DeeplinkRoute) -> Route? {
        switch onEnum(of: deeplinkRoute) {
        case .login:
            .serviceSelect
        case .timeline(let data):
            fromTimeline(data)
        case .status(let status):
            fromStatus(status)
        case .profile(let profile):
            fromProfile(profile)
        case .search:
            .empty
        case .media:
            .empty
        case .rss:
            .empty
        case .twitterArticle:
            .empty
        case .compose:
            .empty
        case .deepLinkAccountPicker(let picker):
            fromAccountPicker(picker)
        case .openLinkDirectly(let data):
            .externalLink(data.url)
        case .directMessage:
            .empty
        case .editUserList:
            .empty
        case .blockUser:
            .empty
        case .unblockUser:
            .empty
        case .muteUser:
            .empty
        case .unmuteUser:
            .empty
        case .reportUser:
            .empty
        case .allDirectMessages:
            .empty
        case .allLists:
            .empty
        case .allFeeds:
            .empty
        case .allAntennas:
            .empty
        case .allChannels:
            .empty
        }
    }

    private static func fromTimeline(_ timeline: DeeplinkRoute.Timeline) -> Route? {
        switch onEnum(of: timeline) {
        case .xQTDeviceFollow(let data):
            if let tabItem = XQTUiTimelineTabItemHelpers.shared.xqtDeviceFollow(accountType: data.accountType) {
                .timeline(tabItem)
            } else {
                nil
            }
        }
    }

    private static func fromProfile(_ profile: DeeplinkRoute.Profile) -> Route? {
        switch onEnum(of: profile) {
        case .user(let data):
            .profileUser(data.accountType, data.userKey)
        case .userNameWithHost(let data):
            .profileUserNameWithHost(data.accountType, data.userName, data.host)
        }
    }

    private static func fromStatus(_ status: DeeplinkRoute.Status) -> Route? {
        switch onEnum(of: status) {
        case .detail(let data):
            .statusDetail(data.accountType, data.statusKey)
        default:
            .empty
        }
    }

    private static func fromAccountPicker(_ picker: DeeplinkRoute.DeepLinkAccountPicker) -> Route? {
        let routes = picker.data.mapValues { route in
            fromDeepLinkRoute(deeplinkRoute: route) ?? .empty
        }
        return .deepLinkAccountPicker(picker.originalUrl, routes)
    }
}
