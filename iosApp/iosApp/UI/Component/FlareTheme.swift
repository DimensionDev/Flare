import SwiftUI

struct FlareTheme<Child>: View where Child: View {
    @Environment(\.appSettings) private var appSettings
    let content: () -> Child
    var body: some View {
        let schema: ColorScheme? = switch appSettings.appearanceSettings.theme {
        case .auto: .none
        case .dark: .dark
        case .light: .light
        }
        content()
            .preferredColorScheme(schema)
    }
}
