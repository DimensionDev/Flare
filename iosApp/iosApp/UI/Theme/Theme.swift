import Combine
import Observation
import SwiftUI

// 字体状态枚举，类似于IceCubesApp
public enum FontState: String, CaseIterable, Identifiable {
    case system
    case SFRounded
    case openDyslexic
    case hyperLegible
    case custom
    
    public var id: String { rawValue }
    
    public var title: String {
        switch self {
        case .system: return "系统默认"
        case .SFRounded: return "系统圆角"
        case .openDyslexic: return "OpenDyslexic"
        case .hyperLegible: return "Atkinson Hyperlegible"
        case .custom: return "自定义..."
        }
    }
}

@Observable
public final class Theme {
    public static var shared: Theme!

    // 对AppSettings的引用，通过初始化时注入
    private let appSettings: AppSettings

    // 字体缓存，避免重复创建
    private var _cachedChosenFont: UIFont?

    // MARK: - 初始化

    static func initialize(appSettings: AppSettings) {
        shared = Theme(appSettings: appSettings)
    }

    private init(appSettings: AppSettings) {
        self.appSettings = appSettings
    }

    // 清除内部字体缓存
    private func invalidateFontCache() {
        _cachedChosenFont = nil
    }

    // MARK: - 主题选择

    // 当前选择的预设主题
    public var selectedSet: ColorSetName {
        get {
            guard let nameRawValue = appSettings.appearanceSettings.selectedThemeSetName,
                  let name = ColorSetName(rawValue: nameRawValue)
            else {
                return .flareLight // 默认主题
            }
            return name
        }
        set {
            var newSettings = appSettings.appearanceSettings
            newSettings.selectedThemeSetName = newValue.rawValue
            appSettings.update(newValue: newSettings)
        }
    }

    // 应用预设主题
    public func applySet(_ set: ColorSetName) {
        var newSettings = appSettings.appearanceSettings
        newSettings.selectedThemeSetName = set.rawValue

        // 重要：应用预设主题时清除所有自定义颜色
        newSettings.customTintColorHex = nil
        newSettings.customPrimaryBackgroundColorHex = nil
        newSettings.customSecondaryBackgroundColorHex = nil
        newSettings.customLabelColorHex = nil

        appSettings.update(newValue: newSettings)
    }

    // MARK: - 颜色属性

    // 强调色
    public var tintColor: Color {
        get {
            if let hex = appSettings.appearanceSettings.customTintColorHex,
               let color = Color(hex: hex)
            {
                return color
            }
            return selectedSet.set.accent
        }
        set {
            var newSettings = appSettings.appearanceSettings
            newSettings.customTintColorHex = newValue.toHex()
            newSettings.selectedThemeSetName = nil // 标记为自定义
            appSettings.update(newValue: newSettings)
        }
    }

    // 主背景色
    public var primaryBackgroundColor: Color {
        get {
            if let hex = appSettings.appearanceSettings.customPrimaryBackgroundColorHex,
               let color = Color(hex: hex)
            {
                return color
            }
            return selectedSet.set.background
        }
        set {
            var newSettings = appSettings.appearanceSettings
            newSettings.customPrimaryBackgroundColorHex = newValue.toHex()
            newSettings.selectedThemeSetName = nil
            appSettings.update(newValue: newSettings)
        }
    }

    // 次背景色
    public var secondaryBackgroundColor: Color {
        get {
            if let hex = appSettings.appearanceSettings.customSecondaryBackgroundColorHex,
               let color = Color(hex: hex)
            {
                return color
            }
            return selectedSet.set.secondaryBackground
        }
        set {
            var newSettings = appSettings.appearanceSettings
            newSettings.customSecondaryBackgroundColorHex = newValue.toHex()
            newSettings.selectedThemeSetName = nil
            appSettings.update(newValue: newSettings)
        }
    }

    // 三级背景色
    public var tertiaryBackgroundColor: Color {
        selectedSet.set.tertiaryBackground
    }

    // 标签色
    public var labelColor: Color {
        get {
            if let hex = appSettings.appearanceSettings.customLabelColorHex,
               let color = Color(hex: hex)
            {
                return color
            }
            return selectedSet.set.label
        }
        set {
            var newSettings = appSettings.appearanceSettings
            newSettings.customLabelColorHex = newValue.toHex()
            newSettings.selectedThemeSetName = nil
            appSettings.update(newValue: newSettings)
        }
    }

    // 次要标签色
    public var secondaryLabelColor: Color {
        selectedSet.set.secondaryLabel
    }

    // MARK: - 显示模式

    // 应用显示模式（亮色/暗色/自动）
    var appDisplayMode: AppDisplayMode {
        get {
            let theme = appSettings.appearanceSettings.theme
            switch theme {
            case "light": return .light
            case "dark": return .dark
            default: return .auto
            }
        }
        set {
            var newSettings = appSettings.appearanceSettings
            switch newValue {
            case .light:
                newSettings.theme = "light"
            case .dark:
                newSettings.theme = "dark"
            case .auto:
                newSettings.theme = "system"
            }
            appSettings.update(newValue: newSettings)
        }
    }

    // 当前实际颜色方案
    public var colorScheme: ColorScheme? {
        switch appDisplayMode {
        case .light: .light
        case .dark: .dark
        case .auto: nil
        }
    }

    // MARK: - 字体属性

    // 选择的字体
    public var chosenFont: UIFont? {
        get {
            // 检查缓存字体是否仍有效
            if let cachedFont = _cachedChosenFont,
               cachedFont.fontName == appSettings.appearanceSettings.chosenFontName,
               appSettings.appearanceSettings.chosenFontPointSize == nil ||
               cachedFont.pointSize == appSettings.appearanceSettings.chosenFontPointSize
            {
                return cachedFont
            }

            // 缓存无效或为空，尝试创建/重新创建
            invalidateFontCache()

            guard let fontName = appSettings.appearanceSettings.chosenFontName else {
                return nil // 无自定义字体名称，将使用系统默认
            }

            let pointSize = appSettings.appearanceSettings.chosenFontPointSize ?? defaultBaseFontSize

            if let font = UIFont(name: fontName, size: pointSize) {
                _cachedChosenFont = font // 缓存新创建的字体
                return font
            } else {
                // 设置中的字体名称无效，记录错误并从设置中清除
                print("错误：为名称'\(fontName)'创建UIFont失败。从设置中清除。")
                var newSettings = appSettings.appearanceSettings
                newSettings.chosenFontName = nil
                newSettings.chosenFontPointSize = nil
                appSettings.update(newValue: newSettings) // 这次写入将在需要时触发UI刷新
                return nil
            }
        }
        set {
            var newSettings = appSettings.appearanceSettings
            if let newFont = newValue {
                newSettings.chosenFontName = newFont.fontName
                newSettings.chosenFontPointSize = newFont.pointSize // 存储基本大小
            } else {
                newSettings.chosenFontName = nil
                newSettings.chosenFontPointSize = nil
            }
            appSettings.update(newValue: newSettings)
            invalidateFontCache() // 关键：显式设置时使缓存失效
        }
    }

    // 用于创建UIFont的基本字体大小（在应用fontSizeScale之前）
    public var currentBaseFontSize: CGFloat {
        appSettings.appearanceSettings.chosenFontPointSize ?? defaultBaseFontSize
    }

    private var defaultBaseFontSize: CGFloat { 17.0 } // 默认正文文本大小

    // 字体大小缩放
    public var fontSizeScale: Double {
        get {
            appSettings.appearanceSettings.fontSizeScale
        }
        set {
            var newSettings = appSettings.appearanceSettings
            newSettings.fontSizeScale = newValue
            appSettings.update(newValue: newSettings)
        }
    }

    // 行间距
    public var lineSpacing: Double {
        get {
            appSettings.appearanceSettings.lineSpacing
        }
        set {
            var newSettings = appSettings.appearanceSettings
            newSettings.lineSpacing = newValue
            appSettings.update(newValue: newSettings)
        }
    }

    // 重置字体设置
    public func resetFont() {
        var newSettings = appSettings.appearanceSettings
        newSettings.chosenFontName = nil
        newSettings.chosenFontPointSize = nil
        newSettings.fontSizeScale = 1.0
        newSettings.lineSpacing = 1.2
        appSettings.update(newValue: newSettings)
        invalidateFontCache()
    }

    // MARK: - 全局UI应用

    // 应用全局UI设置
    @MainActor func applyGlobalUIElements() {
        DispatchQueue.main.async {
            #if !os(macOS)
                if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                    let uiStyle: UIUserInterfaceStyle = switch self.appDisplayMode {
                    case .light: .light
                    case .dark: .dark
                    case .auto: .unspecified
                    }

                    for window in windowScene.windows {
                        window.overrideUserInterfaceStyle = uiStyle
                    }
                }
            #endif

            let colorSet = self.selectedSet.set

            // Navigation bar appearance
            let navBarAppearance = UINavigationBarAppearance()
            navBarAppearance.configureWithOpaqueBackground()
            navBarAppearance.backgroundColor = UIColor(colorSet.background)
            navBarAppearance.titleTextAttributes = [.foregroundColor: UIColor(colorSet.label)]
            navBarAppearance.largeTitleTextAttributes = [.foregroundColor: UIColor(colorSet.label)]

            UINavigationBar.appearance().standardAppearance = navBarAppearance
            UINavigationBar.appearance().compactAppearance = navBarAppearance
            UINavigationBar.appearance().scrollEdgeAppearance = navBarAppearance
            UINavigationBar.appearance().tintColor = UIColor(colorSet.accent)

            // Tab bar appearance
            let tabBarAppearance = UITabBarAppearance()
            tabBarAppearance.configureWithOpaqueBackground()
            tabBarAppearance.backgroundColor = UIColor(colorSet.background)

            UITabBar.appearance().standardAppearance = tabBarAppearance
            if #available(iOS 15.0, *) {
                UITabBar.appearance().scrollEdgeAppearance = tabBarAppearance
            }
            UITabBar.appearance().tintColor = UIColor(colorSet.accent)

            // Table view appearance
            UITableView.appearance().backgroundColor = UIColor(colorSet.background)
            UITableViewCell.appearance().backgroundColor = UIColor(colorSet.background)

            // Text view appearance
            UITextView.appearance().backgroundColor = UIColor(colorSet.background)
            UITextView.appearance().textColor = UIColor(colorSet.label)

            // Text field appearance
            UITextField.appearance().backgroundColor = UIColor(colorSet.background)
            UITextField.appearance().textColor = UIColor(colorSet.label)

            // Search bar appearance
            UISearchBar.appearance().backgroundColor = UIColor(colorSet.background)

            // Button appearance
            UIButton.appearance().tintColor = UIColor(colorSet.accent)

            // Switch appearance
            UISwitch.appearance().onTintColor = UIColor(colorSet.accent)

            // Segmented control appearance
            UISegmentedControl.appearance().selectedSegmentTintColor = UIColor(colorSet.accent)
            UISegmentedControl.appearance().setTitleTextAttributes([.foregroundColor: UIColor(colorSet.background)], for: .selected)
            UISegmentedControl.appearance().setTitleTextAttributes([.foregroundColor: UIColor(colorSet.label)], for: .normal)
        }
    }

    // 桥接到AppSettings (过渡期使用)
    func syncToAppSettings(_ appSettings: AppSettings) {
        var updatedSettings = appSettings.appearanceSettings

        // 从Theme映射到AppSettings
        switch appDisplayMode {
        case .light:
            updatedSettings.theme = "light"
        case .dark:
            updatedSettings.theme = "dark"
        case .auto:
            updatedSettings.theme = "system"
        }

        // 执行更新
        appSettings.update(newValue: updatedSettings)
    }

    // 从AppSettings同步（初始化时使用）
    func syncFromAppSettings(_ appSettings: AppSettings) {
        // 从AppSettings映射到Theme
        let currentTheme = appSettings.appearanceSettings.theme
        switch currentTheme {
        case "light":
            appDisplayMode = .light
        case "dark":
            appDisplayMode = .dark
        default:
            appDisplayMode = .auto
        }
    }
}

// 显示模式枚举
public enum DisplayMode: String, CaseIterable, Identifiable {
    case light
    case dark
    case auto

    public var id: String { rawValue }

    public var localizedName: String {
        switch self {
        case .light: "浅色"
        case .dark: "深色"
        case .auto: "自动"
        }
    }
}
 