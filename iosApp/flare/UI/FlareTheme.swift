import SwiftUI
import KotlinSharedUI
import Foundation
import Combine

struct FlareTheme<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @State private var themeSettings = ThemeSettings()
    var body: some View {
        content()
            .environment(\.themeSettings, themeSettings)
            .preferredColorScheme(
                themeSettings.appearanceSettings.theme == .system ? nil : (themeSettings.appearanceSettings.theme == .dark ? .dark : .light)
            )
    }
}

@Observable
class ThemeSettings {
    var subscribers = Set<AnyCancellable>()
    var presenter: SettingsPresenter

    init() {
        self.presenter = SettingsPresenter()
        self.appearanceSettings = AppearanceSettings.companion.Default
        self.presenter.models.toPublisher().receive(on: DispatchQueue.main).sink { [weak self] newState in
            if case .success(let appearanceSettings) = onEnum(of: newState.appearance) {
                self?.appearanceSettings = appearanceSettings.data
            }
        }.store(in: &subscribers)
    }
    var appearanceSettings: AppearanceSettings

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

