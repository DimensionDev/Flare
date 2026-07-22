import SwiftUI
import KotlinSharedUI
import Foundation
import Combine
import FlareAppleCore
import FlareAppleUI

struct FlareTheme<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @StateObject private var presenter = KotlinPresenter(presenter: EnvironmentSettingsPresenter())
    @State private var openedURL: URL? = nil
    @State private var appSettings = AppSettings.companion.default
    @State private var globalAppearance: GlobalAppearance = GlobalAppearance.companion.Default
    @State private var timelineAppearance: TimelineAppearance = TimelineAppearance.companion.Default
    private let sizes: [DynamicTypeSize] =
      [.xSmall, .small, .medium, .large, .xLarge, .xxLarge, .xxxLarge]
    var body: some View {
        content()
            .networkStatus()
            .environment(\.aiConfig, appSettings.aiConfig)
            .environment(\.translateConfig, appSettings.translateConfig)
            .environment(\.appSettings, appSettings)
            .environment(\.globalAppearance, globalAppearance)
            .environment(\.timelineAppearance, timelineAppearance)
            .preferredColorScheme(
                globalAppearance.theme == .system ? nil : (globalAppearance.theme == .dark ? .dark : .light)
            )
            .dynamicTypeSize(sizes[min(max(Int(globalAppearance.fontSizeDiff) + 2, 0), sizes.count - 1)])
            .environment(\.openURL, OpenURLAction { url in
                guard url.isWebURL else {
                    return .systemAction(url)
                }
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
                timelineAppearance = timelineAppearance.withAppSettings(newValue)
            }
            .onSuccessOf(of: presenter.state.globalAppearance) { newValue in
                globalAppearance = newValue
            }
            .onSuccessOf(of: presenter.state.timelineAppearance) { newValue in
                timelineAppearance = newValue.withAppSettings(appSettings)
            }
    }
}

private extension TimelineAppearance {
    func withAppSettings(_ appSettings: AppSettings) -> TimelineAppearance {
        doCopy(
            avatarShape: avatarShape,
            showMedia: showMedia,
            showSensitiveContent: showSensitiveContent,
            expandContentWarning: expandContentWarning,
            expandMediaSize: expandMediaSize,
            limitMediaGridToNine: limitMediaGridToNine,
            videoAutoplay: videoAutoplay,
            showLinkPreview: showLinkPreview,
            compatLinkPreview: compatLinkPreview,
            showNumbers: showNumbers,
            postActionStyle: postActionStyle,
            postActionLayout: postActionLayout,
            postActionFixedWidth: postActionFixedWidth,
            fullWidthPost: fullWidthPost,
            absoluteTimestamp: absoluteTimestamp,
            showPlatformLogo: showPlatformLogo,
            timelineDisplayMode: timelineDisplayMode,
            aiConfig: TimelineAppearance.AiConfig(
                translation: true,
                tldr: appSettings.aiConfig.tldr,
                agent: appSettings.aiConfig.agent && appSettings.aiConfig.type.openAIModel?.isEmpty == false,
                showOriginalWithTranslation: appSettings.translateConfig.showOriginalWithTranslation
            ),
            lineLimit: lineLimit,
            showTranslateButton: showTranslateButton
        )
    }
}

private extension AppSettingsAiConfigType {
    var openAIModel: String? {
        (self as? AppSettingsAiConfigTypeOpenAI)?.model
    }
}

private extension URL {
    var isWebURL: Bool {
        guard let scheme = scheme?.lowercased() else { return false }
        return scheme == "http" || scheme == "https"
    }
}
