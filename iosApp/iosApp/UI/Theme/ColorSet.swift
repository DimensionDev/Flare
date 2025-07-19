import SwiftUI

public let availableColorsSets: [ColorSetCouple] =
    [
        .init(light: ThemeLight(), dark: ThemeDark()),

        .init(light: ThemeFreshSkyLight(), dark: ThemeFreshSkyDark()),
        .init(light: ThemeDarkOceanLight(), dark: ThemeDarkOceanDark()),
        .init(light: ThemeMysticNightLight(), dark: ThemeMysticNightDark()),
        .init(light: ThemeSunsetGlowLight(), dark: ThemeSunsetGlowDark()),
        .init(light: ThemeEnchantedForestLight(), dark: ThemeEnchantedForestDark()),
        .init(light: ThemeAutumnWhisperLight(), dark: ThemeAutumnWhisperDark()),
        .init(light: ThemeArcticFrostLight(), dark: ThemeArcticFrostDark()),
        .init(light: ThemeCherryBlossomLight(), dark: ThemeCherryBlossomDark()),
        .init(light: ThemePristineEleganceLight(), dark: ThemePristineEleganceDark()),
        .init(light: ThemeNocturnalDepthsLight(), dark: ThemeNocturnalDepthsDark()),
        .init(light: ThemeObsidianGraceLight(), dark: ThemeObsidianGraceDark()),
        .init(light: ThemeJadeLagoonLight(), dark: ThemeJadeLagoonDark()),
        .init(light: ThemeTimberEssenceLight(), dark: ThemeTimberEssenceDark()),
        .init(light: ThemeRusticCanvasLight(), dark: ThemeRusticCanvasDark()),
        .init(light: ThemePlumBlossomLight(), dark: ThemePlumBlossomDark()),
        .init(light: ThemeNaturalLinenLight(), dark: ThemeNaturalLinenDark()),
        .init(light: ThemePristineSnowLight(), dark: ThemePristineSnowDark()),
        .init(light: ThemeEmeraldDepthsLight(), dark: ThemeEmeraldDepthsDark()),
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

    case freshSkyLight = "Fresh Sky - Light"
    case freshSkyDark = "Fresh Sky - Dark"
    case darkOceanLight = "Dark Ocean - Light"
    case darkOceanDark = "Dark Ocean - Dark"
    case mysticNightLight = "Mystic Night - Light"
    case mysticNightDark = "Mystic Night - Dark"
    case sunsetGlowLight = "Sunset Glow - Light"
    case sunsetGlowDark = "Sunset Glow - Dark"
    case enchantedForestLight = "Enchanted Forest - Light"
    case enchantedForestDark = "Enchanted Forest - Dark"
    case autumnWhisperLight = "Autumn Whisper - Light"
    case autumnWhisperDark = "Autumn Whisper - Dark"
    case arcticFrostLight = "Arctic Frost - Light"
    case arcticFrostDark = "Arctic Frost - Dark"
    case cherryBlossomLight = "Cherry Blossom - Light"
    case cherryBlossomDark = "Cherry Blossom - Dark"
    case pristineEleganceLight = "Pristine Elegance - Light"
    case pristineEleganceDark = "Pristine Elegance - Dark"
    case nocturnalDepthsLight = "Nocturnal Depths - Light"
    case nocturnalDepthsDark = "Nocturnal Depths - Dark"
    case obsidianGraceLight = "Obsidian Grace - Light"
    case obsidianGraceDark = "Obsidian Grace - Dark"
    case jadeLagoonLight = "Jade Lagoon - Light"
    case jadeLagoonDark = "Jade Lagoon - Dark"
    case timberEssenceLight = "Timber Essence - Light"
    case timberEssenceDark = "Timber Essence - Dark"
    case rusticCanvasLight = "Rustic Canvas - Light"
    case rusticCanvasDark = "Rustic Canvas - Dark"
    case plumBlossomLight = "Plum Blossom - Light"
    case plumBlossomDark = "Plum Blossom - Dark"
    case naturalLinenLight = "Natural Linen - Light"
    case naturalLinenDark = "Natural Linen - Dark"
    case pristineSnowLight = "Pristine Snow - Light"
    case pristineSnowDark = "Pristine Snow - Dark"
    case emeraldDepthsLight = "Emerald Depths - Light"
    case emeraldDepthsDark = "Emerald Depths - Dark"
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

public struct ThemeFreshSkyLight: ColorSet {
    public var name: ColorSetName = .freshSkyLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x216EEE)
    public var primaryBackgroundColor: Color = .init(hex: 0xFFFFFF)
    public var secondaryBackgroundColor: Color = .init(hex: 0xF8FAFF)
    public var labelColor: Color = .black

    public init() {}
}

public struct ThemeFreshSkyDark: ColorSet {
    public var name: ColorSetName = .freshSkyDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0x4B8BFD)
    public var primaryBackgroundColor: Color = .init(hex: 0x0F1419)
    public var secondaryBackgroundColor: Color = .init(hex: 0x1A2332)
    public var labelColor: Color = .white

    public init() {}
}

public struct ThemeDarkOceanLight: ColorSet {
    public var name: ColorSetName = .darkOceanLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x5E81AC)
    public var primaryBackgroundColor: Color = .init(hex: 0xECEFF4)
    public var secondaryBackgroundColor: Color = .init(hex: 0xE5E9F0)
    public var labelColor: Color = .init(hex: 0x2E3440)

    public init() {}
}

public struct ThemeDarkOceanDark: ColorSet {
    public var name: ColorSetName = .darkOceanDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0x88C0D0)
    public var primaryBackgroundColor: Color = .init(hex: 0x2E3440)
    public var secondaryBackgroundColor: Color = .init(hex: 0x3B4252)
    public var labelColor: Color = .init(hex: 0xECEFF4)

    public init() {}
}

public struct ThemeMysticNightLight: ColorSet {
    public var name: ColorSetName = .mysticNightLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x6F42C1)
    public var primaryBackgroundColor: Color = .init(hex: 0xF8F9FA)
    public var secondaryBackgroundColor: Color = .init(hex: 0xF3E8FF)
    public var labelColor: Color = .init(hex: 0x282A36)

    public init() {}
}

public struct ThemeMysticNightDark: ColorSet {
    public var name: ColorSetName = .mysticNightDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0xBD93F9)
    public var primaryBackgroundColor: Color = .init(hex: 0x282A36)
    public var secondaryBackgroundColor: Color = .init(hex: 0x44475A)
    public var labelColor: Color = .init(hex: 0xF8F8F2)

    public init() {}
}

public struct ThemeSunsetGlowLight: ColorSet {
    public var name: ColorSetName = .sunsetGlowLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0xFF6347)
    public var primaryBackgroundColor: Color = .init(hex: 0xFFF8F0)
    public var secondaryBackgroundColor: Color = .init(hex: 0xFFE4E1)
    public var labelColor: Color = .init(hex: 0x8B4513)

    public init() {}
}

public struct ThemeSunsetGlowDark: ColorSet {
    public var name: ColorSetName = .sunsetGlowDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0xFFD700)
    public var primaryBackgroundColor: Color = .init(hex: 0x2D1B1B)
    public var secondaryBackgroundColor: Color = .init(hex: 0x4A2C2A)
    public var labelColor: Color = .init(hex: 0xFFF8DC)

    public init() {}
}

public struct ThemeEnchantedForestLight: ColorSet {
    public var name: ColorSetName = .enchantedForestLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x228B22)
    public var primaryBackgroundColor: Color = .init(hex: 0xF0FFF0)
    public var secondaryBackgroundColor: Color = .init(hex: 0xE6F3E6)
    public var labelColor: Color = .init(hex: 0x2F4F2F)

    public init() {}
}

public struct ThemeEnchantedForestDark: ColorSet {
    public var name: ColorSetName = .enchantedForestDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0x00FF7F)
    public var primaryBackgroundColor: Color = .init(hex: 0x1C2E1C)
    public var secondaryBackgroundColor: Color = .init(hex: 0x2F4F2F)
    public var labelColor: Color = .init(hex: 0xF0FFF0)

    public init() {}
}

public struct ThemeAutumnWhisperLight: ColorSet {
    public var name: ColorSetName = .autumnWhisperLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0xD2691E)
    public var primaryBackgroundColor: Color = .init(hex: 0xFDF5E6)
    public var secondaryBackgroundColor: Color = .init(hex: 0xF5DEB3)
    public var labelColor: Color = .init(hex: 0x8B4513)

    public init() {}
}

public struct ThemeAutumnWhisperDark: ColorSet {
    public var name: ColorSetName = .autumnWhisperDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0xCD853F)
    public var primaryBackgroundColor: Color = .init(hex: 0x2F1B14)
    public var secondaryBackgroundColor: Color = .init(hex: 0x4A2C1A)
    public var labelColor: Color = .init(hex: 0xFDF5E6)

    public init() {}
}

public struct ThemeArcticFrostLight: ColorSet {
    public var name: ColorSetName = .arcticFrostLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x4682B4)
    public var primaryBackgroundColor: Color = .init(hex: 0xF0F8FF)
    public var secondaryBackgroundColor: Color = .init(hex: 0xE0FFFF)
    public var labelColor: Color = .init(hex: 0x2F4F4F)

    public init() {}
}

public struct ThemeArcticFrostDark: ColorSet {
    public var name: ColorSetName = .arcticFrostDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0x87CEEB)
    public var primaryBackgroundColor: Color = .init(hex: 0x1C2833)
    public var secondaryBackgroundColor: Color = .init(hex: 0x2F4F4F)
    public var labelColor: Color = .init(hex: 0xF0F8FF)

    public init() {}
}

public struct ThemeCherryBlossomLight: ColorSet {
    public var name: ColorSetName = .cherryBlossomLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0xFF69B4)
    public var primaryBackgroundColor: Color = .init(hex: 0xFFF0F5)
    public var secondaryBackgroundColor: Color = .init(hex: 0xFFE4E1)
    public var labelColor: Color = .init(hex: 0x8B008B)

    public init() {}
}

public struct ThemeCherryBlossomDark: ColorSet {
    public var name: ColorSetName = .cherryBlossomDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0xFFC0CB)
    public var primaryBackgroundColor: Color = .init(hex: 0x2D1B2D)
    public var secondaryBackgroundColor: Color = .init(hex: 0x4A2C4A)
    public var labelColor: Color = .init(hex: 0xFFF0F5)

    public init() {}
}

public struct ThemePristineEleganceLight: ColorSet {
    public var name: ColorSetName = .pristineEleganceLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x4169E1)
    public var primaryBackgroundColor: Color = .init(hex: 0xF8F8FF)
    public var secondaryBackgroundColor: Color = .init(hex: 0xF0F0F0)
    public var labelColor: Color = .init(hex: 0x2F2F2F)

    public init() {}
}

public struct ThemePristineEleganceDark: ColorSet {
    public var name: ColorSetName = .pristineEleganceDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0x1E90FF)
    public var primaryBackgroundColor: Color = .init(hex: 0x1C1C1E)
    public var secondaryBackgroundColor: Color = .init(hex: 0x2C2C2E)
    public var labelColor: Color = .init(hex: 0xF8F8FF)

    public init() {}
}

public struct ThemeNocturnalDepthsLight: ColorSet {
    public var name: ColorSetName = .nocturnalDepthsLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x708090)
    public var primaryBackgroundColor: Color = .init(hex: 0xF8F8FF)
    public var secondaryBackgroundColor: Color = .init(hex: 0xE6E6FA)
    public var labelColor: Color = .init(hex: 0x2F4F4F)

    public init() {}
}

public struct ThemeNocturnalDepthsDark: ColorSet {
    public var name: ColorSetName = .nocturnalDepthsDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0xC0C0C0)
    public var primaryBackgroundColor: Color = .init(hex: 0x2F4F4F)
    public var secondaryBackgroundColor: Color = .init(hex: 0x3C5A5A)
    public var labelColor: Color = .init(hex: 0xF8F8FF)

    public init() {}
}

public struct ThemeObsidianGraceLight: ColorSet {
    public var name: ColorSetName = .obsidianGraceLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x4A4A4A)
    public var primaryBackgroundColor: Color = .init(hex: 0xFAFAFA)
    public var secondaryBackgroundColor: Color = .init(hex: 0xF0F0F0)
    public var labelColor: Color = .init(hex: 0x2A2A2A)

    public init() {}
}

public struct ThemeObsidianGraceDark: ColorSet {
    public var name: ColorSetName = .obsidianGraceDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0xD3D3D3)
    public var primaryBackgroundColor: Color = .init(hex: 0x000000)
    public var secondaryBackgroundColor: Color = .init(hex: 0x1A1A1A)
    public var labelColor: Color = .init(hex: 0xF5F5F5)

    public init() {}
}

public struct ThemeJadeLagoonLight: ColorSet {
    public var name: ColorSetName = .jadeLagoonLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x20B2AA)
    public var primaryBackgroundColor: Color = .init(hex: 0xE0FFFF)
    public var secondaryBackgroundColor: Color = .init(hex: 0xAFEEEE)
    public var labelColor: Color = .init(hex: 0x2F4F4F)

    public init() {}
}

public struct ThemeJadeLagoonDark: ColorSet {
    public var name: ColorSetName = .jadeLagoonDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0x48D1CC)
    public var primaryBackgroundColor: Color = .init(hex: 0x1C3333)
    public var secondaryBackgroundColor: Color = .init(hex: 0x2F4F4F)
    public var labelColor: Color = .init(hex: 0xE0FFFF)

    public init() {}
}

public struct ThemeTimberEssenceLight: ColorSet {
    public var name: ColorSetName = .timberEssenceLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x8B4513)
    public var primaryBackgroundColor: Color = .init(hex: 0xFDF5E6)
    public var secondaryBackgroundColor: Color = .init(hex: 0xF5DEB3)
    public var labelColor: Color = .init(hex: 0x654321)

    public init() {}
}

public struct ThemeTimberEssenceDark: ColorSet {
    public var name: ColorSetName = .timberEssenceDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0xD2691E)
    public var primaryBackgroundColor: Color = .init(hex: 0x2F1B14)
    public var secondaryBackgroundColor: Color = .init(hex: 0x4A2C1A)
    public var labelColor: Color = .init(hex: 0xFDF5E6)

    public init() {}
}

public struct ThemeRusticCanvasLight: ColorSet {
    public var name: ColorSetName = .rusticCanvasLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0xCD853F)
    public var primaryBackgroundColor: Color = .init(hex: 0xFFF8DC)
    public var secondaryBackgroundColor: Color = .init(hex: 0xF5DEB3)
    public var labelColor: Color = .init(hex: 0x8B4513)

    public init() {}
}

public struct ThemeRusticCanvasDark: ColorSet {
    public var name: ColorSetName = .rusticCanvasDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0xF4A460)
    public var primaryBackgroundColor: Color = .init(hex: 0x2F1F14)
    public var secondaryBackgroundColor: Color = .init(hex: 0x4A3220)
    public var labelColor: Color = .init(hex: 0xFFF8DC)

    public init() {}
}

public struct ThemePlumBlossomLight: ColorSet {
    public var name: ColorSetName = .plumBlossomLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x8B008B)
    public var primaryBackgroundColor: Color = .init(hex: 0xFFF0F5)
    public var secondaryBackgroundColor: Color = .init(hex: 0xFFE4E1)
    public var labelColor: Color = .init(hex: 0x4B0082)

    public init() {}
}

public struct ThemePlumBlossomDark: ColorSet {
    public var name: ColorSetName = .plumBlossomDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0xFF1493)
    public var primaryBackgroundColor: Color = .init(hex: 0x2D1B2D)
    public var secondaryBackgroundColor: Color = .init(hex: 0x4A2C4A)
    public var labelColor: Color = .init(hex: 0xFFF0F5)

    public init() {}
}

public struct ThemeNaturalLinenLight: ColorSet {
    public var name: ColorSetName = .naturalLinenLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x8B4513)
    public var primaryBackgroundColor: Color = .init(hex: 0xFAF0E6)
    public var secondaryBackgroundColor: Color = .init(hex: 0xF5DEB3)
    public var labelColor: Color = .init(hex: 0x654321)

    public init() {}
}

public struct ThemeNaturalLinenDark: ColorSet {
    public var name: ColorSetName = .naturalLinenDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0xD2691E)
    public var primaryBackgroundColor: Color = .init(hex: 0x2F1F14)
    public var secondaryBackgroundColor: Color = .init(hex: 0x4A3220)
    public var labelColor: Color = .init(hex: 0xFAF0E6)

    public init() {}
}

public struct ThemePristineSnowLight: ColorSet {
    public var name: ColorSetName = .pristineSnowLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x4169E1)
    public var primaryBackgroundColor: Color = .init(hex: 0xFFFFFF)
    public var secondaryBackgroundColor: Color = .init(hex: 0xF8F8FF)
    public var labelColor: Color = .init(hex: 0x2F2F2F)

    public init() {}
}

public struct ThemePristineSnowDark: ColorSet {
    public var name: ColorSetName = .pristineSnowDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0x808080)
    public var primaryBackgroundColor: Color = .init(hex: 0x1C1C1E)
    public var secondaryBackgroundColor: Color = .init(hex: 0x2C2C2E)
    public var labelColor: Color = .init(hex: 0xFFFFFF)

    public init() {}
}

public struct ThemeEmeraldDepthsLight: ColorSet {
    public var name: ColorSetName = .emeraldDepthsLight
    public var scheme: ColorScheme = .light
    public var tintColor: Color = .init(hex: 0x228B22)
    public var primaryBackgroundColor: Color = .init(hex: 0xF0FFF0)
    public var secondaryBackgroundColor: Color = .init(hex: 0xE6F3E6)
    public var labelColor: Color = .init(hex: 0x006400)

    public init() {}
}

public struct ThemeEmeraldDepthsDark: ColorSet {
    public var name: ColorSetName = .emeraldDepthsDark
    public var scheme: ColorScheme = .dark
    public var tintColor: Color = .init(hex: 0x00FF00)
    public var primaryBackgroundColor: Color = .init(hex: 0x006400)
    public var secondaryBackgroundColor: Color = .init(hex: 0x228B22)
    public var labelColor: Color = .init(hex: 0xF0FFF0)

    public init() {}
}
