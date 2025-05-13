import SwiftUI

#if canImport(UIKit)
    import UIKit
#endif

public extension View {
    @MainActor func applyTheme() -> some View {
        modifier(ThemeApplier())
    }
}

@MainActor
struct ThemeApplier: ViewModifier {
    @Environment(\EnvironmentValues.colorScheme) var colorScheme

    // Theme.shared已在FlareApp.swift中初始化
    var theme: Theme { Theme.shared! }

    var actualColorScheme: SwiftUI.ColorScheme? {
        switch theme.appDisplayMode {
        case .light: .light
        case .dark: .dark
        case .auto: nil
        }
    }

    func body(content: Content) -> some View {
        content
            .tint(theme.tintColor)
            .preferredColorScheme(actualColorScheme)
        #if canImport(UIKit)
            .onAppear {
                setWindowTint(theme.tintColor)
                setWindowUserInterfaceStyle(from: theme.appDisplayMode)
                setBarsColor(theme.primaryBackgroundColor)
            }
            .onChange(of: theme.tintColor) { newValue in
                setWindowTint(newValue)
            }
            .onChange(of: theme.primaryBackgroundColor) { newValue in
                setBarsColor(newValue)
            }
            .onChange(of: theme.appDisplayMode) { newValue in
                setWindowUserInterfaceStyle(from: newValue)
            }
            .onChange(of: colorScheme) { _ in
                if theme.appDisplayMode == .auto {
                    // 系统颜色方案变化时，需要应用对应的主题
                    // 当前关联的亮/暗主题配对逻辑可在需要时添加
                }
            }
        #endif
    }

    #if canImport(UIKit)
        private func setWindowUserInterfaceStyle(from displayMode: AppDisplayMode) {
            let userInterfaceStyle: UIUserInterfaceStyle = switch displayMode {
            case .dark:
                .dark
            case .light:
                .light
            case .auto:
                .unspecified
            }

            for window in allWindows() {
                window.overrideUserInterfaceStyle = userInterfaceStyle
            }
        }

        private func setWindowTint(_ color: Color) {
            for window in allWindows() {
                window.tintColor = UIColor(color)
            }
        }

        private func setBarsColor(_ color: Color) {
            UINavigationBar.appearance().isTranslucent = true
            UINavigationBar.appearance().barTintColor = UIColor(color)
        }

        private func allWindows() -> [UIWindow] {
            UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows)
        }
    #endif
}
