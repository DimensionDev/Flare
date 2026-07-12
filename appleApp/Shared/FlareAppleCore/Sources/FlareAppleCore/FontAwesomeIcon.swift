// Subset of FontAwesome icons used by Flare.

import Foundation

#if canImport(SwiftUI)
import SwiftUI
#endif

#if canImport(UIKit)
import UIKit
#endif

#if canImport(AppKit) && !targetEnvironment(macCatalyst)
import AppKit
#endif

public enum FontAwesomeIcon: String, CaseIterable, Hashable, Identifiable, Sendable {
    case arrowsRotate = "arrows-rotate"
    case at = "at"
    case bell = "bell"
    case bluesky = "bluesky"
    case bookmark = "bookmark"
    case bookmarkFill = "bookmark.fill"
    case cat = "cat"
    case check = "check"
    case chevronDown = "chevron-down"
    case circleCheck = "circle-check"
    case circleDown = "circle-down"
    case circleChevronDown = "circle-chevron-down"
    case circleExclamation = "circle-exclamation"
    case circleInfo = "circle-info"
    case circlePlay = "circle-play"
    case circleUser = "circle-user"
    case clockRotateLeft = "clock-rotate-left"
    case commentDots = "comment-dots"
    case database = "database"
    case deleteLeft = "delete-left"
    case discord = "discord"
    case download = "download"
    case ellipsis = "ellipsis"
    case ellipsisVertical = "ellipsis-vertical"
    case envelope = "envelope"
    case eye = "eye"
    case eyeSlash = "eye-slash"
    case faceSmile = "face-smile"
    case fileExport = "file-export"
    case fileImport = "file-import"
    case filter = "filter"
    case floppyDisk = "floppy-disk"
    case gear = "gear"
    case github = "github"
    case globe = "globe"
    case heart = "heart"
    case heartCircleMinus = "heart-circle-minus"
    case heartCirclePlus = "heart-circle-plus"
    case heartFill = "heart.fill"
    case house = "house"
    case image = "image"
    case inbox = "inbox"
    case language = "language"
    case list = "list"
    case locationDot = "location-dot"
    case lock = "lock"
    case lockOpen = "lock-open"
    case magnifyingGlass = "magnifying-glass"
    case mastodon = "mastodon"
    case message = "message"
    case minus = "minus"
    case misskey = "misskey"
    case newspaper = "newspaper"
    case nostr = "nostr"
    case palette = "palette"
    case pause = "pause"
    case pen = "pen"
    case penToSquare = "pen-to-square"
    case photoFilm = "photo-film"
    case pixiv = "pixiv"
    case play = "play"
    case plus = "plus"
    case rectangleList = "rectangle-list"
    case reply = "reply"
    case retweet = "retweet"
    case robot = "robot"
    case shareNodes = "share-nodes"
    case sliders = "sliders"
    case squarePollHorizontal = "square-poll-horizontal"
    case squareRss = "square-rss"
    case star = "star"
    case starFill = "star.fill"
    case tableList = "table-list"
    case telegram = "telegram"
    case thumbtack = "thumbtack"
    case trash = "trash"
    case tv = "tv"
    case twitter = "twitter"
    case userPlus = "user-plus"
    case userSlash = "user-slash"
    case users = "users"
    case volumeXmark = "volume-xmark"
    case weibo = "weibo"
    case xTwitter = "x-twitter"
    case xmark = "xmark"

    public var id: String {
        rawValue
    }

    public var assetName: String {
        rawValue
    }
}

private extension Bundle {
    static var flareFontAwesomeResources: Bundle {
        FlareAppleResource.bundle
    }
}

#if canImport(SwiftUI)
public extension Image {
    init(fontAwesome icon: FontAwesomeIcon) {
        self.init(icon.assetName, bundle: .flareFontAwesomeResources)
    }
}
#endif

#if canImport(UIKit)
public extension UIImage {
    convenience init?(
        fontAwesome icon: FontAwesomeIcon,
        compatibleWith traitCollection: UITraitCollection? = nil
    ) {
        self.init(
            named: icon.assetName,
            in: .flareFontAwesomeResources,
            compatibleWith: traitCollection
        )
    }
}
#endif

#if canImport(AppKit) && !targetEnvironment(macCatalyst)
public extension NSImage {
    static func fontAwesome(_ icon: FontAwesomeIcon) -> NSImage? {
        Bundle.flareFontAwesomeResources.image(forResource: icon.assetName)
    }
}
#endif
