import Foundation
import SwiftUI

struct AppearanceSettings: Codable, Equatable, Changeable {
    var theme: String = FlareTheme.system.rawValue
    var fontName: String = ".SFUI-Regular"
    var fontSize: Double = 14.0
    var forceUseSystemAsDarkMode: Bool = false
    var enableFullSwipePop: Bool = true
    var autoTranslate: Bool = false
    
    // 恢复原有的设置属性
    var avatarShape: AvatarShape = .circle
    var showActions: Bool = true
    var showNumbers: Bool = true
    var showLinkPreview: Bool = true
    var showMedia: Bool = true
    var showSensitiveContent: Bool = false
    
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
    }
    
    init() {
        self.theme = FlareTheme.system.rawValue
        self.fontName = ".SFUI-Regular" // 系统默认字体
        self.fontSize = 14.0
        self.forceUseSystemAsDarkMode = false
        self.enableFullSwipePop = true
        self.autoTranslate = false
        self.avatarShape = .circle
        self.showActions = true
        self.showNumbers = true
        self.showLinkPreview = true
        self.showMedia = true
        self.showSensitiveContent = false
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        theme = try container.decodeIfPresent(String.self, forKey: .theme) ?? FlareTheme.system.rawValue
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
    }
}

enum Theme: String, Codable, CaseIterable, Identifiable {
    case auto
    case light
    case dark
    
    var id: String { self.rawValue }
}

enum AvatarShape: String, Codable, CaseIterable, Identifiable {
    case circle
    case square
    
    var id: String { self.rawValue }
}

protocol Changeable {}

extension Changeable {
    func changing<T>(path: WritableKeyPath<Self, T>, to value: T) -> Self {
        var clone = self
        clone[keyPath: path] = value
        return clone
    }
}
