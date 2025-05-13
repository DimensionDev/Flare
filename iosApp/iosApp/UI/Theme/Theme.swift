import SwiftUI
import Combine

@MainActor
public final class FlareThemeProvider: ObservableObject {
    // 主题持久化存储
    final class ThemeStorage {
        enum ThemeKey: String {
            case selectedTheme, isThemePreviouslySet
        }

        @AppStorage(ThemeKey.selectedTheme.rawValue) public var selectedTheme: String = FlareTheme.system.rawValue
        @AppStorage(ThemeKey.isThemePreviouslySet.rawValue) public var isThemePreviouslySet: Bool = false
    }

    private let themeStorage = ThemeStorage()
    
    // 发布的属性，用于触发UI更新
    @Published public var flareTheme: FlareTheme {
        didSet {
            themeStorage.selectedTheme = flareTheme.rawValue
            updateColorScheme()
            updateColorSet()
        }
    }
    @Published public var colorScheme: ColorScheme?
    @Published public var colorSet: ColorSet
    
    public var isThemePreviouslySet: Bool {
        get { themeStorage.isThemePreviouslySet }
        set { themeStorage.isThemePreviouslySet = newValue }
    }

    public var userInterfaceStyle: UIUserInterfaceStyle {
        flareTheme.userInterfaceStyle
    }
    
    public var isSystemBased: Bool {
        flareTheme.isSystemBased
    }
    
    // 单例实例
    public static let shared = FlareThemeProvider()
    
    private init() {
        // 先初始化主题，避免在初始化其他依赖属性时使用self访问未初始化的属性
        let theme: FlareTheme
        if let storedTheme = FlareTheme(rawValue: themeStorage.selectedTheme) {
            theme = storedTheme
        } else {
            theme = .system
        }
        self.flareTheme = theme
        
        // 然后初始化颜色集
        switch theme {
        case .system:
            self.colorSet = SystemColorSet()
        case .flare:
            self.colorSet = FlareColorSet()
        case .dim:
            self.colorSet = DimColorSet()
        case .classic:
            self.colorSet = ClassicColorSet()
        case .snowfall:
            self.colorSet = SnowfallColorSet()
        case .breezy:
            self.colorSet = BreezyColorSet()
        case .ember:
            self.colorSet = EmberColorSet()
        case .sunset:
            self.colorSet = SunsetColorSet()
        case .carbon:
            self.colorSet = CarbonColorSet()
        case .nord:
            self.colorSet = NordColorSet()
        case .dracula:
            self.colorSet = DraculaColorSet()
        case .minuit:
            self.colorSet = MinuitColorSet()
        case .noir:
            self.colorSet = NoirColorSet()
        }
        
        // 最后初始化颜色方案
        switch theme.userInterfaceStyle {
        case .dark:
            self.colorScheme = .dark
        case .light:
            self.colorScheme = .light
        default:
            self.colorScheme = nil
        }
        
        // 标记主题已设置
        if !themeStorage.isThemePreviouslySet {
            themeStorage.isThemePreviouslySet = true
        }
    }
    
    private func updateColorScheme() {
        switch flareTheme.userInterfaceStyle {
        case .dark:
            colorScheme = .dark
        case .light:
            colorScheme = .light
        default:
            colorScheme = nil
        }
    }
    
    private func updateColorSet() {
        self.colorSet = getColorSet()
    }
    
    private func getColorSet() -> ColorSet {
        switch flareTheme {
        case .system:
            return SystemColorSet()
        case .flare:
            return FlareColorSet()
        case .dim:
            return DimColorSet()
        case .classic:
            return ClassicColorSet()
        case .snowfall:
            return SnowfallColorSet()
        case .breezy:
            return BreezyColorSet()
        case .ember:
            return EmberColorSet()
        case .sunset:
            return SunsetColorSet()
        case .carbon:
            return CarbonColorSet()
        case .nord:
            return NordColorSet()
        case .dracula:
            return DraculaColorSet()
        case .minuit:
            return MinuitColorSet()
        case .noir:
            return NoirColorSet()
        }
    }
    
    // 应用全局UI元素的主题
    public func applyGlobalUIElements() {
        DispatchQueue.main.async {
            // 设置系统外观
            if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                windowScene.windows.forEach { window in
                    window.overrideUserInterfaceStyle = self.flareTheme.userInterfaceStyle
                }
            }
            
            let colorSet = self.colorSet
            
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
} 