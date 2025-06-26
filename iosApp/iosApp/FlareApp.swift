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
    @StateObject private var router = FlareRouter()
    @StateObject private var podcastManager = IOSPodcastManager.shared
    @State var theme = FlareTheme.shared

    init() {
        // Register FontAwesome fonts
        FontAwesome.register()

        KoinHelper.shared.start(inAppNotification: SwitUIInAppNotification())

         FlareImageConfiguration.shared.configure()

        // 初始化UserManager
        UserManager.shared.initialize()

        // 初始化AppBarTabSettingStore（使用游客模式）
        // UserManager初始化完成后会自动更新为正确的账号
        AppBarTabSettingStore.shared.initialize(with: AccountTypeGuest(), user: nil)

        // DownloadManager初始化
        _ = DownloadManager.shared

        // 🚀 120fps优化配置
        FrameRateOptimizer.configureForApp()
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
                        .enableInjection()
                        // .preferredColorScheme(.light)
                        .onOpenURL { url in
                            router.handleDeepLink(url)
                        }
                        .withFlareTheme()
                        .environmentObject(router)
                        .floatingPerformanceWindow()

                #endif

                // --- Global Floating Player Overlay ---
                if podcastManager.currentPodcast != nil {
                    DraggablePlayerOverlay()
                        .animation(.spring(), value: podcastManager.currentPodcast?.id)
                        .environmentObject(router)
                }
            }.environment(theme).withFlareTheme().applyTheme(theme).environment(theme)
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
