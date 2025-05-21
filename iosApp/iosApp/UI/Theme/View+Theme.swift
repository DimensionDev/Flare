import SwiftUI

//// Modifier to apply the theme's label color
// struct ThemeLabelColorModifier: ViewModifier {
//    @Environment(FlareTheme.self) private var theme
//
//    func body(content: Content) -> some View {
//        content.foregroundColor(theme.labelColor)
//    }
// }
//
//// Modifier to apply the theme's primary background color
// struct ThemePrimaryBackgroundModifier: ViewModifier {
//    @Environment(FlareTheme.self) private var theme
//
//    func body(content: Content) -> some View {
//        content.background(theme.primaryBackgroundColor)
//    }
// }
//
//// Modifier to apply the theme's secondary background color
// struct ThemeSecondaryBackgroundModifier: ViewModifier {
//    @Environment(FlareTheme.self) private var theme
//
//    func body(content: Content) -> some View {
//        content.background(theme.secondaryBackgroundColor)
//    }
// }

// Modifier specific for List styling
struct ThemeListModifier: ViewModifier {
    @Environment(FlareTheme.self) private var theme

    func body(content: Content) -> some View {
        content
            .background(theme.primaryBackgroundColor)
            .scrollContentBackground(.hidden) // For iOS 16+ to allow List background color
    }
}

//// Modifier for applying root theme settings
// struct RootThemeApplicator: ViewModifier {
//    @Environment(FlareTheme.self) private var theme
//
//    func body(content: Content) -> some View {
//        content
//            .background(theme.primaryBackgroundColor.ignoresSafeArea()) // 这个设置不了的，必须UIkit才能设置成功，
//            .foregroundColor(theme.labelColor)
//            // 添加对 NavigationStack 的特殊处理
//            .onAppear {
//                // 设置 UINavigationBar 的外观 有用，
//                UINavigationBar.appearance().backgroundColor = UIColor(theme.primaryBackgroundColor)
//            }
//    }
// }

// 扩展 List 类型
extension List {
    /// Applies standard theme styling for a List (primary background and hidden scroll content background).
    @MainActor // FlareTheme 是 @MainActor，这里也最好标记
    func themeListStyle() -> some View {
        modifier(ThemeListModifier()) // 内部仍然使用 ThemeListModifier
    }
}

// 保持对 View 的其他扩展
// extension View {
//    /// Applies the theme's label color to the view's foreground.
//    func themeLabelColor() -> some View {
//        modifier(ThemeLabelColorModifier())
//    }
//
//    /// Applies the theme's primary background color to the view.
//    func themePrimaryBackground() -> some View {
//        modifier(ThemePrimaryBackgroundModifier())
//    }
//
//    /// Applies the theme's secondary background color to the view.
//    func themeSecondaryBackground() -> some View {
//        modifier(ThemeSecondaryBackgroundModifier())
//    }

// themeListStyle() 从这里移除

/// Applies the root theme settings (primary background ignoring safe area, default label color).
/// This should typically be applied to the outermost view in a scene or major view component.
//    func applyRootTheme() -> some View {
//        modifier(RootThemeApplicator())
//    }
// }

//// 有效，但是有点猛，不能用，不能精细，屏蔽
// struct EnhancedThemeApplier: ViewModifier {
//    @Environment(FlareTheme.self) private var theme
//
//    func body(content: Content) -> some View {
//        content
//            .background(theme.primaryBackgroundColor) // SwiftUI 背景
//            .onAppear {
//                // 导航栏设置
//                UINavigationBar.appearance().backgroundColor = UIColor(theme.primaryBackgroundColor)
//                UINavigationBar.appearance().barTintColor = UIColor(theme.primaryBackgroundColor)
//                UINavigationBar.appearance().isTranslucent = false
//
//                // 内容区域设置
//                UIView.appearance().backgroundColor = UIColor(theme.primaryBackgroundColor)
//
//                // 滚动视图设置
//                UIScrollView.appearance().backgroundColor = UIColor(theme.primaryBackgroundColor)
//
//                // 表格视图设置
//                UITableView.appearance().backgroundColor = UIColor(theme.primaryBackgroundColor)
//
//                // 标签栏设置
//                UITabBar.appearance().backgroundColor = UIColor(theme.primaryBackgroundColor)
//                UITabBar.appearance().barTintColor = UIColor(theme.primaryBackgroundColor)
//
//                // 设置导航控制器的视图背景
//                if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
//                   let rootViewController = windowScene.windows.first?.rootViewController {
//                    setBackgroundRecursively(rootViewController, color: UIColor(theme.primaryBackgroundColor))
//                }
//            }
//    }
//
//    // 递归设置所有视图控制器的背景色
//    private func setBackgroundRecursively(_ viewController: UIViewController, color: UIColor) {
//        viewController.view.backgroundColor = color
//
//        for child in viewController.children {
//            setBackgroundRecursively(child, color: color)
//        }
//
//        if let navigationController = viewController as? UINavigationController {
//            navigationController.view.backgroundColor = color
//            for viewController in navigationController.viewControllers {
//                setBackgroundRecursively(viewController, color: color)
//            }
//        }
//
//        if let tabBarController = viewController as? UITabBarController {
//            tabBarController.view.backgroundColor = color
//            if let viewControllers = tabBarController.viewControllers {
//                for viewController in viewControllers {
//                    setBackgroundRecursively(viewController, color: color)
//                }
//            }
//        }
//    }
// }
//
// extension View {
//    func enhancedTheme() -> some View {
//        modifier(EnhancedThemeApplier())
//    }
// }
