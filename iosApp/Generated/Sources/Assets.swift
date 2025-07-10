// swiftlint:disable all
// Generated using SwiftGen — https://github.com/SwiftGen/SwiftGen

#if os(macOS)
    import AppKit
#elseif os(iOS)
    import UIKit
#elseif os(tvOS) || os(watchOS)
    import UIKit
#endif
#if canImport(SwiftUI)
    import SwiftUI
#endif

// Deprecated typealiases
// @available(*, deprecated, renamed: "ColorAsset.Color", message: "This typealias will be removed in SwiftGen 7.0")
public typealias AssetColorTypeAlias = ColorAsset.Color
@available(*, deprecated, renamed: "ImageAsset.Image", message: "This typealias will be removed in SwiftGen 7.0")
public typealias AssetImageTypeAlias = ImageAsset.Image

// swiftlint:disable superfluous_disable_command file_length implicit_return

// - Asset Catalogs

// swiftlint:disable identifier_name line_length nesting type_body_length type_name
public enum Asset {
    public static let accentColor = ColorAsset(name: "AccentColor")
    public enum Tab {
        public static let background = ColorAsset(name: "Tab/background")
        public static let compose = ImageAsset(name: "Tab/compose")
        public static let discoverActive = ImageAsset(name: "Tab/discover.active")
        public static let discoverInactive = ImageAsset(name: "Tab/discover.inactive")
        public static let feedActivie = ImageAsset(name: "Tab/feed.activie")
        public static let feedInactive = ImageAsset(name: "Tab/feed.inactive")
        public static let homeActive = ImageAsset(name: "Tab/home.active")
        public static let homeInactive = ImageAsset(name: "Tab/home.inactive")
        public static let profileActive = ImageAsset(name: "Tab/profile.active")
        public static let profileInactive = ImageAsset(name: "Tab/profile.inactive")
        public static let searchActive = ImageAsset(name: "Tab/search.active")
        public static let searchInactive = ImageAsset(name: "Tab/search.inactive")
        public static let trendingActive = ImageAsset(name: "Tab/trending.active")
        public static let trendingInactive = ImageAsset(name: "Tab/trending.inactive")
    }

    public enum Image {
        public enum Notification {
            public static let bellIcon = ImageAsset(name: "Notification/bell.icon")
            public static let birdIcon = ImageAsset(name: "Notification/bird.icon")
            public static let followWhite = ImageAsset(name: "Notification/follow.white")
            public static let heartIcon = ImageAsset(name: "Notification/heart.icon")
            public static let heartWhite = ImageAsset(name: "Notification/heart.white")
            public static let listIcon = ImageAsset(name: "Notification/list.icon")
            public static let messagesWhite = ImageAsset(name: "Notification/messages.white")
            public static let milestonIcon = ImageAsset(name: "Notification/mileston.icon")
            public static let more = ImageAsset(name: "Notification/more")
            public static let personIcon = ImageAsset(name: "Notification/person.icon")
            public static let playIcon = ImageAsset(name: "Notification/play.icon")
            public static let recommendationIcon = ImageAsset(name: "Notification/recommendation.icon")
            public static let reportIcon = ImageAsset(name: "Notification/report.icon")
            public static let retweetIcon = ImageAsset(name: "Notification/retweet.icon")
            public static let retweetWhite = ImageAsset(name: "Notification/retweet.white")
            public static let securityAlertIcon = ImageAsset(name: "Notification/security.alert.icon")
            public static let spacesIcon = ImageAsset(name: "Notification/spaces.icon")
        }

        public enum Status {
            public enum AudioSpace {
                public static let bell = ImageAsset(name: "Status/AudioSpace/bell")
                public static let calendar = ImageAsset(name: "Status/AudioSpace/calendar")
                public static let microphone = ImageAsset(name: "Status/AudioSpace/microphone")
                public static let playerPlay = ImageAsset(name: "Status/AudioSpace/player.play")
            }

            public enum Dashboard {
                public static let chatBubbleOutlineSmall = ImageAsset(name: "Status/Dashboard/chat.bubble.outline.small")
                public static let favoriteBorderSmall = ImageAsset(name: "Status/Dashboard/favorite.border.small")
                public static let quoteClosingSmall = ImageAsset(name: "Status/Dashboard/quote.closing.small")
                public static let repeatSmall = ImageAsset(name: "Status/Dashboard/repeat.small")
            }

            public enum RankingMode {
                public static let arrowDown = ImageAsset(name: "Status/RankingMode/arrow.down")
            }

            public enum Toolbar {
                public static let block = ImageAsset(name: "Status/Toolbar/block")
                public static let bookmarkFilled = ImageAsset(name: "Status/Toolbar/bookmark.filled")
                public static let bookmarkFilledLarge = ImageAsset(name: "Status/Toolbar/bookmark.filled.large")
                public static let bookmark = ImageAsset(name: "Status/Toolbar/bookmark")
                public static let bookmarkLarge = ImageAsset(name: "Status/Toolbar/bookmark.large")
                public static let chatBubbleOutline = ImageAsset(name: "Status/Toolbar/chat.bubble.outline")
                public static let chatBubbleOutlineLarge = ImageAsset(name: "Status/Toolbar/chat.bubble.outline.large")
                public static let collectBorder = ImageAsset(name: "Status/Toolbar/collect.border")
                public static let collect = ImageAsset(name: "Status/Toolbar/collect")
                public static let delete = ImageAsset(name: "Status/Toolbar/delete")
                public static let favoriteBorder = ImageAsset(name: "Status/Toolbar/favorite.border")
                public static let favoriteBorderLarge = ImageAsset(name: "Status/Toolbar/favorite.border.large")
                public static let favorite = ImageAsset(name: "Status/Toolbar/favorite")
                public static let favoriteLarge = ImageAsset(name: "Status/Toolbar/favorite.large")
                public static let flag = ImageAsset(name: "Status/Toolbar/flag")
                public static let follow = ImageAsset(name: "Status/Toolbar/follow")
                public static let link = ImageAsset(name: "Status/Toolbar/link")
                public static let mute = ImageAsset(name: "Status/Toolbar/mute")
                public static let playerPlayFilled = ImageAsset(name: "Status/Toolbar/player.play.filled")
                public static let quote = ImageAsset(name: "Status/Toolbar/quote")
                public static let `repeat` = ImageAsset(name: "Status/Toolbar/repeat")
                public static let repeatLarge = ImageAsset(name: "Status/Toolbar/repeat.large")
                public static let squareAndArrowUp = ImageAsset(name: "Status/Toolbar/square.and.arrow.up")
                public static let squareAndArrowUpLarge = ImageAsset(name: "Status/Toolbar/square.and.arrow.up.large")
                public static let unblock = ImageAsset(name: "Status/Toolbar/unblock")
                public static let unfollow = ImageAsset(name: "Status/Toolbar/unfollow")
                public static let unmute = ImageAsset(name: "Status/Toolbar/unmute")
                public static let userAdd = ImageAsset(name: "Status/Toolbar/user.add")
            }

            public static let media = ImageAsset(name: "Status/media")
            public static let more = ImageAsset(name: "Status/more")
            public static let pin = ImageAsset(name: "Status/pin")
            public static let place = ImageAsset(name: "Status/place")
            public static let verifedBadge = ImageAsset(name: "Status/verifed.badge")
        }

        public static let arrowSquareDown = ImageAsset(name: "arrow.square.down")
        public static let arrowSquareUp = ImageAsset(name: "arrow.square.up")
        public enum Attributes {
            public static let calendar = ImageAsset(name: "attributes/calendar")
            public static let globe = ImageAsset(name: "attributes/globe")
            public static let location = ImageAsset(name: "attributes/location")
        }

        public static let chevronDown = ImageAsset(name: "chevron.down")
        public static let chevronRight = ImageAsset(name: "chevron.right")
        public static let chevronUp = ImageAsset(name: "chevron.up")
        public static let collection = ImageAsset(name: "collection")
        public static let downArrow = ImageAsset(name: "downArrow")
        public static let export = ImageAsset(name: "export")
        public static let flag = ImageAsset(name: "flag")
        public static let gear = ImageAsset(name: "gear")
        public static let menu = ImageAsset(name: "menu")
        public static let more = ImageAsset(name: "more")
        public static let moreBtn = ImageAsset(name: "moreBtn")
        public static let selected = ImageAsset(name: "selected")
    }

    public static let label = ColorAsset(name: "label")
    public static let logo = ImageAsset(name: "logo")
}

// swiftlint:enable identifier_name line_length nesting type_body_length type_name

// - Implementation Details

public final class ColorAsset {
    public fileprivate(set) var name: String

    #if os(macOS)
        public typealias Color = NSColor
    #elseif os(iOS) || os(tvOS) || os(watchOS)
        public typealias Color = UIColor
    #endif

    @available(iOS 11.0, tvOS 11.0, watchOS 4.0, macOS 10.13, *)
    public private(set) lazy var color: Color = {
        guard let color = Color(asset: self) else {
            fatalError("Unable to load color asset named \(name).")
        }
        return color
    }()

    #if os(iOS) || os(tvOS)
        @available(iOS 11.0, tvOS 11.0, *)
        public func color(compatibleWith traitCollection: UITraitCollection) -> Color {
            let bundle = Bundle(identifier: "dev.dimension.flare")!
            guard let color = Color(named: name, in: bundle, compatibleWith: traitCollection) else {
                fatalError("Unable to load color asset named \(name).")
            }
            return color
        }
    #endif

    #if canImport(SwiftUI)
        @available(iOS 13.0, tvOS 13.0, watchOS 6.0, macOS 10.15, *)
        public private(set) lazy var swiftUIColor: SwiftUI.Color = .init(asset: self)
    #endif

    fileprivate init(name: String) {
        self.name = name
    }
}

public extension ColorAsset.Color {
    @available(iOS 11.0, tvOS 11.0, watchOS 4.0, macOS 10.13, *)
    convenience init?(asset: ColorAsset) {
        let bundle = Bundle(identifier: "dev.dimension.flare")!
        #if os(iOS) || os(tvOS)
            self.init(named: asset.name, in: bundle, compatibleWith: nil)
        #elseif os(macOS)
            self.init(named: NSColor.Name(asset.name), bundle: bundle)
        #elseif os(watchOS)
            self.init(named: asset.name)
        #endif
    }
}

#if canImport(SwiftUI)
    @available(iOS 13.0, tvOS 13.0, watchOS 6.0, macOS 10.15, *)
    public extension SwiftUI.Color {
        init(asset: ColorAsset) {
            let bundle = Bundle(identifier: "dev.dimension.flare")!
            self.init(asset.name, bundle: bundle)
        }
    }
#endif

public struct ImageAsset {
    public fileprivate(set) var name: String

    #if os(macOS)
        public typealias Image = NSImage
    #elseif os(iOS) || os(tvOS) || os(watchOS)
        public typealias Image = UIImage
    #endif

    @available(iOS 8.0, tvOS 9.0, watchOS 2.0, macOS 10.7, *)
    public var image: Image {
        let bundle = Bundle(identifier: "dev.dimension.flare")!
        #if os(iOS) || os(tvOS)
            let image = Image(named: name, in: bundle, compatibleWith: nil)
        #elseif os(macOS)
            let name = NSImage.Name(name)
            let image = (bundle == .main) ? NSImage(named: name) : bundle.image(forResource: name)
        #elseif os(watchOS)
            let image = Image(named: name)
        #endif
        guard let result = image else {
            fatalError("Unable to load image asset named \(name).")
        }
        return result
    }

    #if os(iOS) || os(tvOS)
        @available(iOS 8.0, tvOS 9.0, *)
        public func image(compatibleWith traitCollection: UITraitCollection) -> Image {
            let bundle = Bundle(identifier: "dev.dimension.flare")!
            guard let result = Image(named: name, in: bundle, compatibleWith: traitCollection) else {
                fatalError("Unable to load image asset named \(name).")
            }
            return result
        }
    #endif

    #if canImport(SwiftUI)
        @available(iOS 13.0, tvOS 13.0, watchOS 6.0, macOS 10.15, *)
        public var swiftUIImage: SwiftUI.Image {
            SwiftUI.Image(asset: self)
        }
    #endif
}

public extension ImageAsset.Image {
    @available(iOS 8.0, tvOS 9.0, watchOS 2.0, *)
    @available(macOS, deprecated,
               message: "This initializer is unsafe on macOS, please use the ImageAsset.image property")
    convenience init?(asset: ImageAsset) {
        #if os(iOS) || os(tvOS)
            let bundle = Bundle(identifier: "dev.dimension.flare")!
            self.init(named: asset.name, in: bundle, compatibleWith: nil)
        #elseif os(macOS)
            self.init(named: NSImage.Name(asset.name))
        #elseif os(watchOS)
            self.init(named: asset.name)
        #endif
    }
}

#if canImport(SwiftUI)
    @available(iOS 13.0, tvOS 13.0, watchOS 6.0, macOS 10.15, *)
    public extension SwiftUI.Image {
        init(asset: ImageAsset) {
            let bundle = Bundle(identifier: "dev.dimension.flare")!
            self.init(asset.name, bundle: bundle)
        }

        init(asset: ImageAsset, label: Text) {
            let bundle = Bundle(identifier: "dev.dimension.flare")!
            self.init(asset.name, bundle: bundle, label: label)
        }

        init(decorative asset: ImageAsset) {
            let bundle = Bundle(identifier: "dev.dimension.flare")!
            self.init(decorative: asset.name, bundle: bundle)
        }
    }
#endif
