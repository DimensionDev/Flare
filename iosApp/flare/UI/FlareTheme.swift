import SwiftUI
import KotlinSharedUI
import Foundation
import Combine

struct FlareTheme<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @StateObject private var presenter = KotlinPresenter(presenter: EnvironmentSettingsPresenter())
    @State private var openedURL: URL? = nil
    @State private var appSettings: AppSettings = AppSettings(version: "0")
    @State private var globalAppearance: GlobalAppearance = GlobalAppearance.companion.Default
    @State private var timelineAppearance: TimelineAppearance = TimelineAppearance.companion.Default
    private let sizes: [DynamicTypeSize] =
      [.xSmall, .small, .medium, .large, .xLarge, .xxLarge, .xxxLarge]
    var body: some View {
        content()
            .networkStatus()
            .environment(\.aiConfig, appSettings.aiConfig)
            .environment(\.translateConfig, appSettings.translateConfig)
            .environment(\.globalAppearance, globalAppearance)
            .environment(\.timelineAppearance, timelineAppearance)
            .preferredColorScheme(
                globalAppearance.theme == .system ? nil : (globalAppearance.theme == .dark ? .dark : .light)
            )
            .dynamicTypeSize(sizes[min(max(Int(globalAppearance.fontSizeDiff) + 2, 0), sizes.count - 1)])
            .environment(\.openURL, OpenURLAction { url in
                if #available(iOS 26.0, *) {
                    return .systemAction(url, prefersInApp: globalAppearance.inAppBrowser)
                } else if globalAppearance.inAppBrowser {
                    openedURL = url
                    return .handled
                } else {
                    return .systemAction(url)
                }
            })
            .fullScreenCover(item: $openedURL) { url in
                SafariView(url: url, onClose: {
                    openedURL = nil
                })
                .ignoresSafeArea()
            }
            .onSuccessOf(of: presenter.state.appSettings) { newValue in
                appSettings = newValue
            }
            .onSuccessOf(of: presenter.state.globalAppearance) { newValue in
                globalAppearance = newValue
            }
            .onSuccessOf(of: presenter.state.timelineAppearance) { newValue in
                timelineAppearance = newValue
            }
    }
}

private struct GlobalAppearanceKey: EnvironmentKey {
    static let defaultValue = GlobalAppearance.companion.Default
}
private struct TimelineAppearanceKey: EnvironmentKey {
    static let defaultValue = TimelineAppearance.companion.Default
}
private struct AiConfigKey: EnvironmentKey {
    static let defaultValue = AppSettings.AiConfig.companion.default
}
private struct TranslateConfigKey: EnvironmentKey {
    static let defaultValue = AppSettings.TranslateConfig()
}
extension EnvironmentValues {
    var globalAppearance: GlobalAppearance {
        get { self[GlobalAppearanceKey.self] }
        set { self[GlobalAppearanceKey.self] = newValue }
    }
    var timelineAppearance: TimelineAppearance {
        get { self[TimelineAppearanceKey.self] }
        set { self[TimelineAppearanceKey.self] = newValue }
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
