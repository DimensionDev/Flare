import SwiftUI

struct FlareThemeView<Child>: View where Child: View {
    @Environment(\.appSettings) private var appSettings
    let content: () -> Child
    var body: some View {
        // 将theme字符串转换为FlareTheme枚举实例
        let flareTheme = FlareTheme(rawValue: appSettings.appearanceSettings.theme) ?? .system
        let schema: ColorScheme? = switch flareTheme.userInterfaceStyle {
        case .unspecified: .none
        case .dark: .dark
        case .light: .light
        @unknown default: .none
        }
        ZStack {
            Colors.Background.swiftUIPrimary.ignoresSafeArea()
            content()
        }
        .preferredColorScheme(schema)
    }
}
