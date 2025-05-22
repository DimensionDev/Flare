import SwiftUI

// copy from Ice Cube
public let availableColorsSets: [ColorSetCouple] =
    [
        .init(light: ThemeLight(), dark: ThemeDark()),
        .init(light: ThemeNeonLight(), dark: ThemeNeonDark()),
        .init(light: ThemeDesertLight(), dark: ThemeDesertDark()),
        .init(light: ThemeNemesisLight(), dark: ThemeNemesisDark()),
        .init(light: ThemeMediumLight(), dark: ThemeMediumDark()),
        .init(light: ThemeConstellationLight(), dark: ThemeConstellationDark()),
        .init(light: ThemeThreadsLight(), dark: ThemeThreadsDark()),
    ]

public protocol ColorSet: Sendable {
    var name: ColorSetName { get }
    var scheme: ColorScheme { get }
    var tintColor: Color { get set }
    var primaryBackgroundColor: Color { get set }
    var secondaryBackgroundColor: Color { get set }
    var labelColor: Color { get set }
}

public enum ColorScheme: String, Sendable {
    case dark, light
}

public enum ColorSetName: String, Sendable {
    case themeDark = "Dark"
    case themeLight = "Light"
    case themeNeonDark = "Neon - Dark"
    case themeNeonLight = "Neon - Light"
    case themeDesertDark = "Desert - Dark"
    case themeDesertLight = "Desert - Light"
    case themeNemesisDark = "Nemesis - Dark"
    case themeNemesisLight = "Nemesis - Light"
    case themeMediumLight = "Medium - Light"
    case themeMediumDark = "Medium - Dark"
    case themeConstellationLight = "Constellation - Light"
    case themeConstellationDark = "Constellation - Dark"
    case threadsLight = "Threads - Light"
    case threadsDark = "Threads - Dark"
}

public struct ColorSetCouple: Identifiable, Sendable {
    public var id: String {
        dark.name.rawValue + light.name.rawValue
    }

    public let light: ColorSet
    public let dark: ColorSet
}

public struct ThemeDark: ColorSet {
    public var name: ColorSetName = .themeDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(red: 187 / 255, green: 59 / 255, blue: 226 / 255)
    public var primaryBackgroundColor: Color = .init(red: 16 / 255, green: 21 / 255, blue: 35 / 255)
    public var secondaryBackgroundColor: Color = .init(red: 30 / 255, green: 35 / 255, blue: 62 / 255)
    public var labelColor: Color = .white

    public init() {}
}

public struct ThemeLight: ColorSet {
    public var name: ColorSetName = .themeLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(red: 187 / 255, green: 59 / 255, blue: 226 / 255)
    public var primaryBackgroundColor: Color = .white
    public var secondaryBackgroundColor: Color = .init(hex: 0xF0F1F2)
    public var labelColor: Color = .black

    public init() {}
}

public struct ThemeNeonDark: ColorSet {
    public var name: ColorSetName = .themeNeonDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(red: 213 / 255, green: 46 / 255, blue: 245 / 255)
    public var primaryBackgroundColor: Color = .black
    public var secondaryBackgroundColor: Color = .init(red: 19 / 255, green: 0 / 255, blue: 32 / 255)
    public var labelColor: Color = .white

    public init() {}
}

public struct ThemeNeonLight: ColorSet {
    public var name: ColorSetName = .themeNeonLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(red: 213 / 255, green: 46 / 255, blue: 245 / 255) // 图标
    public var primaryBackgroundColor: Color = .white // 主背景色
    public var secondaryBackgroundColor: Color = .init(hex: 0xF0F1F2) //  次背景色
    public var labelColor: Color = .black // 文字

    public init() {}
}

public struct ThemeDesertDark: ColorSet {
    public var name: ColorSetName = .themeDesertDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0xDF915E)
    public var primaryBackgroundColor: Color = .init(hex: 0x433744)
    public var secondaryBackgroundColor: Color = .init(hex: 0x654868)
    public var labelColor: Color = .white

    public init() {}
}

public struct ThemeDesertLight: ColorSet {
    public var name: ColorSetName = .themeDesertLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0xDF915E)
    public var primaryBackgroundColor: Color = .init(hex: 0xFCF2EB)
    public var secondaryBackgroundColor: Color = .init(hex: 0xEEEDE7)
    public var labelColor: Color = .black

    public init() {}
}

public struct ThemeNemesisDark: ColorSet {
    public var name: ColorSetName = .themeNemesisDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0x17A2F2)
    public var primaryBackgroundColor: Color = .init(hex: 0x000000)
    public var secondaryBackgroundColor: Color = .init(hex: 0x151E2B)
    public var labelColor: Color = .white

    public init() {}
}

public struct ThemeNemesisLight: ColorSet {
    public var name: ColorSetName = .themeNemesisLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x17A2F2)
    public var primaryBackgroundColor: Color = .init(hex: 0xFFFFFF)
    public var secondaryBackgroundColor: Color = .init(hex: 0xE8ECEF)
    public var labelColor: Color = .black

    public init() {}
}

public struct ThemeMediumDark: ColorSet {
    public var name: ColorSetName = .themeMediumDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0x1A8917)
    public var primaryBackgroundColor: Color = .init(hex: 0x121212)
    public var secondaryBackgroundColor: Color = .init(hex: 0x191919)
    public var labelColor: Color = .white

    public init() {}
}

public struct ThemeMediumLight: ColorSet {
    public var name: ColorSetName = .themeMediumLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x1A8917)
    public var primaryBackgroundColor: Color = .init(hex: 0xFFFFFF)
    public var secondaryBackgroundColor: Color = .init(hex: 0xFAFAFA)
    public var labelColor: Color = .black

    public init() {}
}

public struct ThemeConstellationDark: ColorSet {
    public var name: ColorSetName = .themeConstellationDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0xFFD966)
    public var primaryBackgroundColor: Color = .init(hex: 0x09192C)
    public var secondaryBackgroundColor: Color = .init(hex: 0x304C7A)
    public var labelColor: Color = .init(hex: 0xE2E4E2)

    public init() {}
}

public struct ThemeConstellationLight: ColorSet {
    public var name: ColorSetName = .themeConstellationLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0xC82238)
    public var primaryBackgroundColor: Color = .init(hex: 0xF4F5F7)
    public var secondaryBackgroundColor: Color = .init(hex: 0xACC7E5)
    public var labelColor: Color = .black

    public init() {}
}

public struct ThemeThreadsDark: ColorSet {
    public var name: ColorSetName = .threadsDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0x0095F6)
    public var primaryBackgroundColor: Color = .init(hex: 0x101010)
    public var secondaryBackgroundColor: Color = .init(hex: 0x181818)
    public var labelColor: Color = .init(hex: 0xE2E4E2)

    public init() {}
}

public struct ThemeThreadsLight: ColorSet {
    public var name: ColorSetName = .threadsLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x0095F6)
    public var primaryBackgroundColor: Color = .init(hex: 0xFFFFFF)
    public var secondaryBackgroundColor: Color = .init(hex: 0xFFFFFF)
    public var labelColor: Color = .black

    public init() {}
}
