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
@available(*, deprecated, renamed: "ColorAsset.Color", message: "This typealias will be removed in SwiftGen 7.0")
public typealias AssetColorTypeAlias = ColorAsset.Color
@available(*, deprecated, renamed: "ImageAsset.Image", message: "This typealias will be removed in SwiftGen 7.0")
public typealias AssetImageTypeAlias = ImageAsset.Image

// swiftlint:disable superfluous_disable_command file_length implicit_return

// MARK: - Asset Catalogs

// swiftlint:disable identifier_name line_length nesting type_body_length type_name
public enum Asset {
  public static let accentColor = ColorAsset(name: "AccentColor")
  public enum Color {
    public enum Background {
      public static let _01 = ColorAsset(name: "Color/Background/01")
      public static let _02 = ColorAsset(name: "Color/Background/02")
      public static let _02Blur85 = ColorAsset(name: "Color/Background/02_blur85")
      public static let _03 = ColorAsset(name: "Color/Background/03")
      public static let _04 = ColorAsset(name: "Color/Background/04")
      public static let black1 = ColorAsset(name: "Color/Background/black1")
      public static let black2 = ColorAsset(name: "Color/Background/black2")
      public static let blur = ColorAsset(name: "Color/Background/blur")
      public static let blur25 = ColorAsset(name: "Color/Background/blur25")
      public static let blur50 = ColorAsset(name: "Color/Background/blur50")
      public static let blur85 = ColorAsset(name: "Color/Background/blur85")
      public static let darkElevated = ColorAsset(name: "Color/Background/dark.elevated")
      public static let lens = ColorAsset(name: "Color/Background/lens")
      public static let lensForground = ColorAsset(name: "Color/Background/lens_forground")
      public static let lensSignup = ColorAsset(name: "Color/Background/lens_signup")
      public static let loginMiddle = ColorAsset(name: "Color/Background/login_middle")
      public static let navigationbarBackground = ColorAsset(name: "Color/Background/navigationbar_background")
      public static let placeholder = ColorAsset(name: "Color/Background/placeholder")
      public static let primary = ColorAsset(name: "Color/Background/primary")
      public static let pure01 = ColorAsset(name: "Color/Background/pure01")
      public static let redpacketLoading = ColorAsset(name: "Color/Background/redpacket.loading")
      public static let vitalikBg = ColorAsset(name: "Color/Background/vitalik_bg")
    }
    public enum Colorful {
      public static let _02 = ColorAsset(name: "Color/Colorful/02")
      public static let _03 = ColorAsset(name: "Color/Colorful/03")
      public static let _04 = ColorAsset(name: "Color/Colorful/04")
      public static let _05 = ColorAsset(name: "Color/Colorful/05")
      public static let _06 = ColorAsset(name: "Color/Colorful/06")
      public static let _07 = ColorAsset(name: "Color/Colorful/07")
      public static let _08 = ColorAsset(name: "Color/Colorful/08")
      public static let _09 = ColorAsset(name: "Color/Colorful/09")
      public static let blue = ColorAsset(name: "Color/Colorful/blue")
      public static let farcaster = ColorAsset(name: "Color/Colorful/farcaster")
      public static let green = ColorAsset(name: "Color/Colorful/green")
      public static let primary = ColorAsset(name: "Color/Colorful/primary")
      public static let purple = ColorAsset(name: "Color/Colorful/purple")
    }
    public enum Line {
      public static let _01 = ColorAsset(name: "Color/Line/01")
      public static let _02 = ColorAsset(name: "Color/Line/02")
    }
    public enum M3 {
      public enum ReadOnly {
        public enum InverseSurface {
          public static let opacity016 = ColorAsset(name: "Color/M3/ReadOnly/InverseSurface/opacity-0.16")
        }
        public enum Outline {
          public static let opacity016 = ColorAsset(name: "Color/M3/ReadOnly/Outline/opacity-0.16")
        }
        public static let onSurfaceVariant012 = ColorAsset(name: "Color/M3/ReadOnly/on.surface.variant-0.12")
        public static let surface1 = ColorAsset(name: "Color/M3/ReadOnly/surface1")
        public static let surface2 = ColorAsset(name: "Color/M3/ReadOnly/surface2")
        public static let white = ColorAsset(name: "Color/M3/ReadOnly/white")
      }
      public enum Ref {
        public enum Secondary {
          public static let secondary20 = ColorAsset(name: "Color/M3/Ref/Secondary/secondary20")
          public static let secondary80 = ColorAsset(name: "Color/M3/Ref/Secondary/secondary80")
        }
      }
      public enum Sys {
        public static let error = ColorAsset(name: "Color/M3/Sys/error")
        public static let onPrimary = ColorAsset(name: "Color/M3/Sys/on.primary")
        public static let onPrimaryContainer = ColorAsset(name: "Color/M3/Sys/on.primary.container")
        public static let onSecondary = ColorAsset(name: "Color/M3/Sys/on.secondary")
        public static let onSecondaryContainer = ColorAsset(name: "Color/M3/Sys/on.secondary.container")
        public static let onSurface = ColorAsset(name: "Color/M3/Sys/on.surface")
        public static let onSurfaceVariant = ColorAsset(name: "Color/M3/Sys/on.surface.variant")
        public static let onSurfaceVariantReverse = ColorAsset(name: "Color/M3/Sys/on.surface.variant.reverse")
        public static let onTertiary = ColorAsset(name: "Color/M3/Sys/on.tertiary")
        public static let onTertiaryContainer = ColorAsset(name: "Color/M3/Sys/on.tertiary.container")
        public static let outline = ColorAsset(name: "Color/M3/Sys/outline")
        public static let primary = ColorAsset(name: "Color/M3/Sys/primary")
        public static let primaryContainer = ColorAsset(name: "Color/M3/Sys/primary.container")
        public static let secondary = ColorAsset(name: "Color/M3/Sys/secondary")
        public static let secondaryContainer = ColorAsset(name: "Color/M3/Sys/secondary.container")
        public static let surface = ColorAsset(name: "Color/M3/Sys/surface")
        public static let surfaceVariant = ColorAsset(name: "Color/M3/Sys/surface.variant")
        public static let tertiaryContainer = ColorAsset(name: "Color/M3/Sys/tertiary-container")
        public static let tertiary = ColorAsset(name: "Color/M3/Sys/tertiary")
      }
    }
    public enum State {
      public static let active = ColorAsset(name: "Color/State/active")
      public static let deactive = ColorAsset(name: "Color/State/deactive")
      public static let deactiveDarkerA = ColorAsset(name: "Color/State/deactive.darker.a")
      public static let deactiveDarker = ColorAsset(name: "Color/State/deactive.darker")
      public static let labelA = ColorAsset(name: "Color/State/label.a")
      public static let labelB = ColorAsset(name: "Color/State/label.b")
      public static let label = ColorAsset(name: "Color/State/label")
      public static let secondActive = ColorAsset(name: "Color/State/second.active")
      public static let secondDeactive = ColorAsset(name: "Color/State/second.deactive")
    }
    public enum Sys {
      public static let primary = ColorAsset(name: "Color/Sys/primary")
      public static let secondary = ColorAsset(name: "Color/Sys/secondary")
      public static let surface = ColorAsset(name: "Color/Sys/surface")
    }
    public static let twitter = ColorAsset(name: "Color/Twitter")
    public static let black = ColorAsset(name: "Color/black")
    public static let blackPure = ColorAsset(name: "Color/black.pure")
    public static let coinbase = ColorAsset(name: "Color/coinbase")
    public static let deviceBackground = ColorAsset(name: "Color/device.background")
    public static let discord = ColorAsset(name: "Color/discord")
    public enum Gradient {
      public enum A {
        public static let a1 = ColorAsset(name: "Color/gradient/a/a1")
        public static let a2 = ColorAsset(name: "Color/gradient/a/a2")
        public static let a3 = ColorAsset(name: "Color/gradient/a/a3")
        public static let a4 = ColorAsset(name: "Color/gradient/a/a4")
      }
      public enum B {
        public static let a1 = ColorAsset(name: "Color/gradient/b/a1")
        public static let a2 = ColorAsset(name: "Color/gradient/b/a2")
      }
      public enum C {
        public static let a1 = ColorAsset(name: "Color/gradient/c/a1")
        public static let a2 = ColorAsset(name: "Color/gradient/c/a2")
      }
      public enum D {
        public static let a1 = ColorAsset(name: "Color/gradient/d/a1")
        public static let a2 = ColorAsset(name: "Color/gradient/d/a2")
      }
      public enum E {
        public static let a1 = ColorAsset(name: "Color/gradient/e/a1")
        public static let a2 = ColorAsset(name: "Color/gradient/e/a2")
      }
      public enum F {
        public static let a1 = ColorAsset(name: "Color/gradient/f/a1")
        public static let a2 = ColorAsset(name: "Color/gradient/f/a2")
      }
      public enum G {
        public static let a1 = ColorAsset(name: "Color/gradient/g/a1")
        public static let a2 = ColorAsset(name: "Color/gradient/g/a2")
        public static let a3 = ColorAsset(name: "Color/gradient/g/a3")
      }
      public enum H {
        public static let a1 = ColorAsset(name: "Color/gradient/h/a1")
        public static let a2 = ColorAsset(name: "Color/gradient/h/a2")
        public static let a3 = ColorAsset(name: "Color/gradient/h/a3")
        public static let a4 = ColorAsset(name: "Color/gradient/h/a4")
      }
      public enum I {
        public static let a1 = ColorAsset(name: "Color/gradient/i/a1")
        public static let a2 = ColorAsset(name: "Color/gradient/i/a2")
        public static let a3 = ColorAsset(name: "Color/gradient/i/a3")
        public static let a4 = ColorAsset(name: "Color/gradient/i/a4")
      }
      public enum J {
        public static let a1 = ColorAsset(name: "Color/gradient/j/a1")
        public static let a2 = ColorAsset(name: "Color/gradient/j/a2")
        public static let a3 = ColorAsset(name: "Color/gradient/j/a3")
        public static let a4 = ColorAsset(name: "Color/gradient/j/a4")
      }
      public enum K {
        public static let a1 = ColorAsset(name: "Color/gradient/k/a1")
        public static let a2 = ColorAsset(name: "Color/gradient/k/a2")
      }
      public static let surface1End = ColorAsset(name: "Color/gradient/surface1.end")
      public static let surface1Start = ColorAsset(name: "Color/gradient/surface1.start")
    }
    public static let hyperlink = ColorAsset(name: "Color/hyperlink")
    public static let luckyDropRequireBackground = ColorAsset(name: "Color/luckyDrop.requireBackground")
    public static let luckyDropCover = ColorAsset(name: "Color/lucky_drop_cover")
    public static let navbar = ColorAsset(name: "Color/navbar")
    public static let naviitemBackground = ColorAsset(name: "Color/naviitem.background")
    public static let orange = ColorAsset(name: "Color/orange")
    public static let pageIndicatorBackground = ColorAsset(name: "Color/pageIndicator.background")
    public static let phantom = ColorAsset(name: "Color/phantom")
    public enum Primary {
      public static let _2 = ColorAsset(name: "Color/primary/2")
      public static let blue = ColorAsset(name: "Color/primary/blue")
      public static let green = ColorAsset(name: "Color/primary/green")
      public static let main = ColorAsset(name: "Color/primary/main")
    }
    public static let primary = ColorAsset(name: "Color/primary")
    public enum Second {
      public static let subTitle = ColorAsset(name: "Color/second/subTitle")
    }
    public static let secondary = ColorAsset(name: "Color/secondary")
    public enum Surface {
      public static let _10 = ColorAsset(name: "Color/surface/10")
      public static let _11 = ColorAsset(name: "Color/surface/11")
      public static let _2 = ColorAsset(name: "Color/surface/2")
      public static let _3 = ColorAsset(name: "Color/surface/3")
      public static let _4 = ColorAsset(name: "Color/surface/4")
      public static let _7 = ColorAsset(name: "Color/surface/7")
      public static let background = ColorAsset(name: "Color/surface/background")
      public static let discoverFeedCard = ColorAsset(name: "Color/surface/discover_feed_card")
      public static let hotTwitterBanner = ColorAsset(name: "Color/surface/hot_twitter_banner")
      public static let iconBackground = ColorAsset(name: "Color/surface/icon_background")
      public static let innerBackground = ColorAsset(name: "Color/surface/inner_background")
      public static let onHotTwitterBanner = ColorAsset(name: "Color/surface/on_hot_twitter_banner")
      public static let topBackground = ColorAsset(name: "Color/surface/top_background")
    }
    public static let surfaceIcon = ColorAsset(name: "Color/surface_icon")
    public static let walletConnect = ColorAsset(name: "Color/wallet_connect")
    public static let white = ColorAsset(name: "Color/white")
    public static let whitePure = ColorAsset(name: "Color/white.pure")
    public static let zerion = ColorAsset(name: "Color/zerion")
  }
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
  public static let logo = ImageAsset(name: "logo")
}
// swiftlint:enable identifier_name line_length nesting type_body_length type_name

// MARK: - Implementation Details

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
    let bundle = Bundle(identifier: "dev.dimension.flare")
    guard let color = Color(named: name, in: bundle, compatibleWith: traitCollection) else {
      fatalError("Unable to load color asset named \(name).")
    }
    return color
  }
  #endif

  #if canImport(SwiftUI)
  @available(iOS 13.0, tvOS 13.0, watchOS 6.0, macOS 10.15, *)
  public private(set) lazy var swiftUIColor: SwiftUI.Color = {
    SwiftUI.Color(asset: self)
  }()
  #endif

  fileprivate init(name: String) {
    self.name = name
  }
}

public extension ColorAsset.Color {
  @available(iOS 11.0, tvOS 11.0, watchOS 4.0, macOS 10.13, *)
  convenience init?(asset: ColorAsset) {
    let bundle = Bundle(identifier: "dev.dimension.flare")
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
    let bundle = Bundle(identifier: "dev.dimension.flare")
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
    let bundle = Bundle(identifier: "dev.dimension.flare")
    #if os(iOS) || os(tvOS)
    let image = Image(named: name, in: bundle, compatibleWith: nil)
    #elseif os(macOS)
    let name = NSImage.Name(self.name)
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
    let bundle = Bundle(identifier: "dev.dimension.flare")
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
    let bundle = Bundle(identifier: "dev.dimension.flare")
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
    let bundle = Bundle(identifier: "dev.dimension.flare")
    self.init(asset.name, bundle: bundle)
  }

  init(asset: ImageAsset, label: Text) {
    let bundle = Bundle(identifier: "dev.dimension.flare")
    self.init(asset.name, bundle: bundle, label: label)
  }

  init(decorative asset: ImageAsset) {
    let bundle = Bundle(identifier: "dev.dimension.flare")
    self.init(decorative: asset.name, bundle: bundle)
  }
}
#endif
