import SwiftUI
import KotlinSharedUI
import Foundation
import Combine

struct FlareTheme<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @State private var themeSettings = ThemeSettings()
    private let sizes: [DynamicTypeSize] =
      [.xSmall, .small, .medium, .large, .xLarge, .xxLarge, .xxxLarge]
    var body: some View {
        content()
            .networkStatus()
            .environment(\.themeSettings, themeSettings)
            .preferredColorScheme(
                themeSettings.appearanceSettings.theme == .system ? nil : (themeSettings.appearanceSettings.theme == .dark ? .dark : .light)
            )
            .dynamicTypeSize(sizes[min(max(Int(themeSettings.appearanceSettings.fontSizeDiff) + 2, 0), sizes.count - 1)])
    }
}

@Observable
class ThemeSettings {
    var subscribers = Set<AnyCancellable>()
    var presenter: SettingsPresenter

    init() {
        self.presenter = SettingsPresenter()
        self.appearanceSettings = AppearanceSettings.companion.Default
        self.aiConfig = .init(translation: false, tldr: true)
        self.presenter.models.toPublisher().receive(on: DispatchQueue.main).sink { [weak self] newState in
            if case .success(let appearanceSettings) = onEnum(of: newState.appearance) {
                self?.appearanceSettings = appearanceSettings.data
            }
            if case .success(let appSettings) = onEnum(of: newState.appSettings) {
                self?.aiConfig = appSettings.data.aiConfig
            }
        }.store(in: &subscribers)
    }
    var appearanceSettings: AppearanceSettings
    var aiConfig: AppSettings.AiConfig

    @MainActor
    deinit {
        subscribers.forEach { cancleable in
            cancleable.cancel()
        }
        presenter.close()
    }
}



private struct ThemeSettingsKey: EnvironmentKey {
    static let defaultValue = ThemeSettings()
}
extension EnvironmentValues {
    var themeSettings: ThemeSettings {
        get { self[ThemeSettingsKey.self] }
        set { self[ThemeSettingsKey.self] = newValue }
    }
}

