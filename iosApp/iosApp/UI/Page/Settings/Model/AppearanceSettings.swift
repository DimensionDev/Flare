import Foundation
import SwiftUI

struct AppearanceSettings: Codable, Equatable, Changeable {
    var theme: String = "system" // 使用字符串常量替换FlareTheme.system.rawValue
    var fontName: String = ".SFUI-Regular"
    var fontSize: Double = 14.0
    var forceUseSystemAsDarkMode: Bool = false
    var enableFullSwipePop: Bool = true
    var autoTranslate: Bool = false

    // 头像和内容相关设置
    var avatarShape: AvatarShape = .circle
    var showActions: Bool = true
    var showNumbers: Bool = true
    var showLinkPreview: Bool = true
    var showMedia: Bool = true
    var showSensitiveContent: Bool = false

    // 主题系统的新增字段
    var selectedThemeSetName: String? = nil
    var customTintColorHex: String? = nil
    var customPrimaryBackgroundColorHex: String? = nil
    var customSecondaryBackgroundColorHex: String? = nil
    var customLabelColorHex: String? = nil
    var chosenFontName: String? = nil
    var chosenFontPointSize: CGFloat? = nil
    var fontSizeScale: Double = 1.0
    var lineSpacing: Double = 1.2

    enum CodingKeys: String, CodingKey {
        case theme
        case fontName
        case fontSize
        case forceUseSystemAsDarkMode
        case enableFullSwipePop
        case autoTranslate
        case avatarShape
        case showActions
        case showNumbers
        case showLinkPreview
        case showMedia
        case showSensitiveContent

        // 主题系统新增字段的键
        case selectedThemeSetName
        case customTintColorHex
        case customPrimaryBackgroundColorHex
        case customSecondaryBackgroundColorHex
        case customLabelColorHex
        case chosenFontName
        case chosenFontPointSize
        case fontSizeScale
        case lineSpacing
    }

    init() {
        theme = "system" // 使用字符串常量替换FlareTheme.system.rawValue
        fontName = ".SFUI-Regular" // 系统默认字体
        fontSize = 14.0
        forceUseSystemAsDarkMode = false
        enableFullSwipePop = true
        autoTranslate = false
        avatarShape = .circle
        showActions = true
        showNumbers = true
        showLinkPreview = true
        showMedia = true
        showSensitiveContent = false

        // 主题系统的默认值
        selectedThemeSetName = "flareLight"
        customTintColorHex = nil
        customPrimaryBackgroundColorHex = nil
        customSecondaryBackgroundColorHex = nil
        customLabelColorHex = nil
        chosenFontName = nil
        chosenFontPointSize = nil
        fontSizeScale = 1.0
        lineSpacing = 1.2
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        theme = try container.decodeIfPresent(String.self, forKey: .theme) ?? "system" // 使用字符串常量替换FlareTheme.system.rawValue
        fontName = try container.decodeIfPresent(String.self, forKey: .fontName) ?? ".SFUI-Regular"
        fontSize = try container.decodeIfPresent(Double.self, forKey: .fontSize) ?? 14.0
        forceUseSystemAsDarkMode = try container.decodeIfPresent(Bool.self, forKey: .forceUseSystemAsDarkMode) ?? false
        enableFullSwipePop = try container.decodeIfPresent(Bool.self, forKey: .enableFullSwipePop) ?? true
        autoTranslate = try container.decodeIfPresent(Bool.self, forKey: .autoTranslate) ?? false

        avatarShape = try container.decodeIfPresent(AvatarShape.self, forKey: .avatarShape) ?? .circle
        showActions = try container.decodeIfPresent(Bool.self, forKey: .showActions) ?? true
        showNumbers = try container.decodeIfPresent(Bool.self, forKey: .showNumbers) ?? true
        showLinkPreview = try container.decodeIfPresent(Bool.self, forKey: .showLinkPreview) ?? true
        showMedia = try container.decodeIfPresent(Bool.self, forKey: .showMedia) ?? true
        showSensitiveContent = try container.decodeIfPresent(Bool.self, forKey: .showSensitiveContent) ?? false

        // 解码主题系统的新增字段
        selectedThemeSetName = try container.decodeIfPresent(String.self, forKey: .selectedThemeSetName)
        customTintColorHex = try container.decodeIfPresent(String.self, forKey: .customTintColorHex)
        customPrimaryBackgroundColorHex = try container.decodeIfPresent(String.self, forKey: .customPrimaryBackgroundColorHex)
        customSecondaryBackgroundColorHex = try container.decodeIfPresent(String.self, forKey: .customSecondaryBackgroundColorHex)
        customLabelColorHex = try container.decodeIfPresent(String.self, forKey: .customLabelColorHex)
        chosenFontName = try container.decodeIfPresent(String.self, forKey: .chosenFontName)
        chosenFontPointSize = try container.decodeIfPresent(CGFloat.self, forKey: .chosenFontPointSize)
        fontSizeScale = try container.decodeIfPresent(Double.self, forKey: .fontSizeScale) ?? 1.0
        lineSpacing = try container.decodeIfPresent(Double.self, forKey: .lineSpacing) ?? 1.2
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)

        try container.encode(theme, forKey: .theme)
        try container.encode(fontName, forKey: .fontName)
        try container.encode(fontSize, forKey: .fontSize)
        try container.encode(forceUseSystemAsDarkMode, forKey: .forceUseSystemAsDarkMode)
        try container.encode(enableFullSwipePop, forKey: .enableFullSwipePop)
        try container.encode(autoTranslate, forKey: .autoTranslate)

        try container.encode(avatarShape, forKey: .avatarShape)
        try container.encode(showActions, forKey: .showActions)
        try container.encode(showNumbers, forKey: .showNumbers)
        try container.encode(showLinkPreview, forKey: .showLinkPreview)
        try container.encode(showMedia, forKey: .showMedia)
        try container.encode(showSensitiveContent, forKey: .showSensitiveContent)

        // 编码主题系统的新增字段
        try container.encodeIfPresent(selectedThemeSetName, forKey: .selectedThemeSetName)
        try container.encodeIfPresent(customTintColorHex, forKey: .customTintColorHex)
        try container.encodeIfPresent(customPrimaryBackgroundColorHex, forKey: .customPrimaryBackgroundColorHex)
        try container.encodeIfPresent(customSecondaryBackgroundColorHex, forKey: .customSecondaryBackgroundColorHex)
        try container.encodeIfPresent(customLabelColorHex, forKey: .customLabelColorHex)
        try container.encodeIfPresent(chosenFontName, forKey: .chosenFontName)
        try container.encodeIfPresent(chosenFontPointSize, forKey: .chosenFontPointSize)
        try container.encode(fontSizeScale, forKey: .fontSizeScale)
        try container.encode(lineSpacing, forKey: .lineSpacing)
    }
}

enum AppDisplayMode: String, Codable, CaseIterable, Identifiable {
    case auto
    case light
    case dark

    var id: String { rawValue }

    var localizedName: String {
        switch self {
        case .auto: "自动"
        case .light: "浅色"
        case .dark: "深色"
        }
    }
}

enum AvatarShape: String, Codable, CaseIterable, Identifiable {
    case circle
    case square

    var id: String { rawValue }
}

protocol Changeable {}

extension Changeable {
    func changing<T>(path: WritableKeyPath<Self, T>, to value: T) -> Self {
        var clone = self
        clone[keyPath: path] = value
        return clone
    }
}
