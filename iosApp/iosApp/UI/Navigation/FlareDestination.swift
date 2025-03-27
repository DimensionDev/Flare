import shared
import SwiftfulRouting
import SwiftUI

enum FlareDestination: Identifiable, Hashable {
    case profile(accountType: AccountType, userKey: MicroBlogKey)
    case profileWithNameAndHost(accountType: AccountType, userName: String, host: String)
    case profileMedia(accountType: AccountType, userKey: MicroBlogKey)
    case statusDetail(accountType: AccountType, statusKey: MicroBlogKey)
    case search(accountType: AccountType, keyword: String)
    case statusMedia(accountType: AccountType, statusKey: MicroBlogKey, index: Int)

    case compose(accountType: AccountType, status: FlareComposeStatus?)
    case addReaction(accountType: AccountType, statusKey: MicroBlogKey)

    case lists(accountType: AccountType)
    case feeds(accountType: AccountType)
    case feedDetail(accountType: AccountType, list: UiList, defaultUser: UiUserV2?)
    case listDetail(accountType: AccountType, list: UiList, defaultUser: UiUserV2?)

    case blueskyReportStatus(accountType: AccountType, statusKey: MicroBlogKey)
    case mastodonReportStatus(accountType: AccountType, statusKey: MicroBlogKey, userKey: MicroBlogKey)
    case misskeyReportStatus(accountType: AccountType, statusKey: MicroBlogKey, userKey: MicroBlogKey)

    case vvoStatusDetail(accountType: AccountType, statusKey: MicroBlogKey)
    case vvoCommentDetail(accountType: AccountType, statusKey: MicroBlogKey)
    case vvoReplyToComment(accountType: AccountType, replyTo: MicroBlogKey, rootId: String)

    case rawImage(url: String)
    case callback(type: CallbackType)
    case deleteStatus(accountType: AccountType, statusKey: MicroBlogKey)

    enum CallbackType: Hashable {
        case mastodon
        case misskey
    }

    var id: String {
        switch self {
        case let .profile(accountType, userKey):
            "profile_\(String(describing: accountType))_\(userKey.description)"
        case let .profileWithNameAndHost(accountType, userName, host):
            "profileWithName_\(String(describing: accountType))_\(userName)_\(host)"
        case let .profileMedia(accountType, userKey):
            "profileMedia_\(String(describing: accountType))_\(userKey.description)"
        case let .statusDetail(accountType, statusKey):
            "statusDetail_\(String(describing: accountType))_\(statusKey.description)"
        case let .search(accountType, keyword):
            "search_\(String(describing: accountType))_\(keyword)"
        case let .statusMedia(accountType, statusKey, index):
            "statusMedia_\(String(describing: accountType))_\(statusKey.description)_\(index)"
        case let .compose(accountType, _):
            "compose_\(String(describing: accountType))"
        case let .addReaction(accountType, statusKey):
            "addReaction_\(String(describing: accountType))_\(statusKey.description)"
        case let .blueskyReportStatus(accountType, statusKey):
            "blueskyReport_\(String(describing: accountType))_\(statusKey.description)"
        case let .mastodonReportStatus(accountType, statusKey, userKey):
            "mastodonReport_\(String(describing: accountType))_\(statusKey.description)_\(userKey.description)"
        case let .misskeyReportStatus(accountType, statusKey, userKey):
            "misskeyReport_\(String(describing: accountType))_\(statusKey.description)_\(userKey.description)"
        case let .vvoStatusDetail(accountType, statusKey):
            "vvoStatus_\(String(describing: accountType))_\(statusKey.description)"
        case let .vvoCommentDetail(accountType, statusKey):
            "vvoComment_\(String(describing: accountType))_\(statusKey.description)"
        case let .vvoReplyToComment(accountType, replyTo, rootId):
            "vvoReply_\(String(describing: accountType))_\(replyTo.description)_\(rootId)"
        case let .rawImage(url):
            "rawImage_\(url.hashValue)"
        case let .callback(type):
            "callback_\(type.hashValue)"
        case let .deleteStatus(accountType, statusKey):
            "deleteStatus_\(String(describing: accountType))_\(statusKey.description)"
        case let .lists(accountType):
            "lists_\(String(describing: accountType))"
        case let .feeds(accountType):
            "feeds_\(String(describing: accountType))"
        case let .feedDetail(accountType, list, _):
            "feedDetail_\(String(describing: accountType))_\(list.id)"
        case let .listDetail(accountType, list, _):
            "listDetail_\(String(describing: accountType))_\(list.id)"
        }
    }

    var navigationType: FlarePresentationType {
        switch self {
        case .compose, .addReaction, .callback:
            .sheet
        case .statusMedia, .rawImage:
            .fullScreen
        case .blueskyReportStatus, .mastodonReportStatus, .misskeyReportStatus, .deleteStatus:
            .dialog
        default:
            .push
        }
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: FlareDestination, rhs: FlareDestination) -> Bool {
        lhs.id == rhs.id
    }
}

enum FlarePresentationType {
    case push
    case sheet
    case fullScreen
    case dialog
}

enum FlareComposeStatus: Hashable {
    case reply(statusKey: MicroBlogKey)
    case quote(statusKey: MicroBlogKey)
    case vvoComment(statusKey: MicroBlogKey, rootId: String)
}
