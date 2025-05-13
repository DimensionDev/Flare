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
    @State private var appSettings = AppSettings()

    init() {
        // Register FontAwesome fonts
        FontAwesome.register()

        KoinHelper.shared.start(inAppNotification: SwitUIInAppNotification())

        // 初始化UserManager
        UserManager.shared.initialize()

        // 初始化AppBarTabSettingStore（使用游客模式）
        // UserManager初始化完成后会自动更新为正确的账号
        AppBarTabSettingStore.shared.initialize(with: AccountTypeGuest(), user: nil)

        // DownloadManager初始化
        _ = DownloadManager.shared

        // 初始化Theme（新的统一主题系统）
        Theme.initialize(appSettings: appSettings)
        Theme.shared.applyGlobalUIElements()
    }

    var body: some Scene {
        WindowGroup {
            ZStack(alignment: .bottom) {
                #if os(macOS)
                    ProvideWindowSizeClass {
                        FlareRootView()
//                        .enableInjection()
                            .preferredColorScheme(.light)
                    }
                    .handlesExternalEvents(preferring: ["flare"], allowing: ["flare"])
                    .onOpenURL { url in
                        router.handleDeepLink(url)
                    }
                #else
                    FlareRootView()
//                    .enableInjection()
                        .onOpenURL { url in
                            router.handleDeepLink(url)
                        }
                        .applyTheme() // 应用新的主题系统
                        .environmentObject(router)
                        .environment(\.appSettings, appSettings)
                #endif

                // --- Global Floating Player Overlay ---
                if podcastManager.currentPodcast != nil {
                    DraggablePlayerOverlay().animation(.spring(), value: podcastManager.currentPodcast?.id)
                }
            }
            .onAppear {
                // 应用全局UI主题
                Theme.shared.applyGlobalUIElements()
            }
            .onChange(of: Theme.shared.appDisplayMode) { _, _ in
                // 当主题变化时同步更新UI
                Theme.shared.applyGlobalUIElements()
            }
        }
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
