import SwiftUI
import UIKit

 
public extension View {
    @MainActor func withFlareTheme() -> some View {
        modifier(FlareThemeApplier(theme: FlareThemeProvider.shared))
    }
}

 
@MainActor
public struct FlareThemeApplier: ViewModifier {
    @ObservedObject var theme: FlareThemeProvider
    
    public func body(content: Content) -> some View {
        let colorSet = theme.colorSet
        
        content
            // 应用背景色
            .background(colorSet.background)
            // 应用文本色
            .foregroundColor(colorSet.label)
            // 应用强调色
            .tint(colorSet.accent)
            // 设置首选配色方案
            .preferredColorScheme(theme.colorScheme)
            // 设置环境值
            .environment(\.colorScheme, theme.colorScheme ?? .light)
            .onAppear {
                // 确保应用全局UI元素
                theme.applyGlobalUIElements()
            }
    }
}

// 旧的ThemeApplier用于兼容
// 这个类处理全局应用主题的工作
@available(*, deprecated, message: "使用FlareThemeProvider.shared.applyGlobalUIElements()代替")
public class ThemeApplier {
    // 应用主题到整个应用程序
    @MainActor
    public static func applyTheme(theme: FlareTheme) {
        // 使用新的主题系统
        Task { @MainActor in
            FlareThemeProvider.shared.flareTheme = theme
            FlareThemeProvider.shared.applyGlobalUIElements()
        }
    }
}

// 为了兼容旧代码保留的结构体
@available(*, deprecated, message: "使用FlareThemeApplier代替")
public struct FlareThemeModifier: ViewModifier {
    @ObservedObject private var themeProvider = FlareThemeProvider.shared
    
    public func body(content: Content) -> some View {
        content
            .background(themeProvider.colorSet.background)
            .foregroundColor(themeProvider.colorSet.label)
            .tint(themeProvider.colorSet.accent)
            .preferredColorScheme(themeProvider.colorScheme)
            .environment(\.colorScheme, themeProvider.colorScheme ?? .light)
            .onAppear {
                themeProvider.applyGlobalUIElements()
            }
    }
} 
