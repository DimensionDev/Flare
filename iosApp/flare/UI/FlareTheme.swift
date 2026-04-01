import SwiftUI
import KotlinSharedUI
import Foundation
import Combine

struct FlareTheme<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    private let sizes: [DynamicTypeSize] =
      [.xSmall, .small, .medium, .large, .xLarge, .xxLarge, .xxxLarge]
    var body: some View {
        StateView(state: presenter.state.appSettings) { appSettings in
            StateView(state: presenter.state.appearance) { appearance in
                content()
                    .networkStatus()
                    .environment(\.aiConfig, appSettings.aiConfig)
                    .environment(\.translateConfig, appSettings.translateConfig)
                    .environment(\.appearanceSettings, appearance)
                    .preferredColorScheme(
                        appearance.theme == .system ? nil : (appearance.theme == .dark ? .dark : .light)
                    )
                    .dynamicTypeSize(sizes[min(max(Int(appearance.fontSizeDiff) + 2, 0), sizes.count - 1)])
            }
        }
    }
}

private struct AppearanceSettingsKey: EnvironmentKey {
    static let defaultValue = AppearanceSettings.companion.Default
}
private struct AiConfigKey: EnvironmentKey {
    static let defaultValue = AppSettings.AiConfig.companion.default
}
private struct TranslateConfigKey: EnvironmentKey {
    static let defaultValue = AppSettings.TranslateConfig()
}
extension EnvironmentValues {
    var appearanceSettings: AppearanceSettings {
        get { self[AppearanceSettingsKey.self] }
        set { self[AppearanceSettingsKey.self] = newValue }
    }
    var aiConfig: AppSettings.AiConfig {
        get { self[AiConfigKey.self] }
        set { self[AiConfigKey.self] = newValue }
    }
    var translateConfig: AppSettings.TranslateConfig {
        get { self[TranslateConfigKey.self] }
        set { self[TranslateConfigKey.self] = newValue }
    }
}
