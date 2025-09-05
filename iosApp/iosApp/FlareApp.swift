import FontAwesomeSwiftUI
import shared
import SwiftUI

@main
struct FlareApp: SwiftUI.App {
    #if os(macOS)
        @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate
    #else
        @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    #endif
    @State private var router = FlareRouter.shared
    @State private var podcastManager = IOSPodcastManager.shared
    @State var theme = FlareTheme.shared
    @State private var shouldShowVersionBanner: Bool = false

    init() {
        FontAwesome.register()

        KoinHelper.shared.start(inAppNotification: SwitUIInAppNotification())

        FlareImageConfiguration.shared.configure()

        UserManager.shared.initialize()

        AppBarTabSettingStore.shared.initialize(with: AccountTypeGuest(), user: nil)

        _ = DownloadManager.shared

        _shouldShowVersionBanner = State(initialValue: ReleaseLogManager.shared.shouldShowBanner())
    }

    var body: some Scene {
        WindowGroup {
            ZStack(alignment: .bottom) {
                #if os(macOS)
                    ProvideWindowSizeClass {
                        FlareRootView()
                            .withFlareTheme()
//                        .enableInjection()
                        // .preferredColorScheme(.light)
                    }
                    .handlesExternalEvents(preferring: ["flare"], allowing: ["flare"])
                    .onOpenURL { url in
                        router.handleDeepLink(url)
                    }
                #else
                    // AboutTestScreen().withFlareTheme().applyTheme(theme).environment(theme).applyRootTheme()

                    FlareRootView()
//                        .enableInjection()
                        // .preferredColorScheme(.light)
                        .onOpenURL { url in
                            router.handleDeepLink(url)
                        }
                        .withFlareTheme()
                        .environment(router)
                        .errorToast()

                #endif

                // --- Global Floating Player Overlay ---
                if podcastManager.currentPodcast != nil {
                    DraggablePlayerOverlay()
                        .animation(.spring(), value: podcastManager.currentPodcast?.id)
                        .environment(router)
                }
            }.environment(theme).withFlareTheme().applyTheme(theme).environment(theme)
                .environment(\.shouldShowVersionBanner, shouldShowVersionBanner)
                .onReceive(NotificationCenter.default.publisher(for: .versionBannerDismissed)) { _ in
                    shouldShowVersionBanner = false
                }
        }
        .environment(theme)

        #if os(macOS)
            WindowGroup(id: "image-view", for: String.self) { $url in
                ImageViewWindow(url: url)
            }
            .windowStyle(.hiddenTitleBar)
            WindowGroup(id: "video-view", for: String.self) { $url in
                VideoViewWindow(url: url)
            }
            .windowStyle(.hiddenTitleBar)
        #endif
    }
}
