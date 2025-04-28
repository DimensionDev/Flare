import shared
import SwiftUI
import FontAwesomeSwiftUI

@main
struct FlareApp: SwiftUI.App {
    #if os(macOS)
        @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate
    #else
        @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    #endif
    @StateObject private var router = FlareRouter()

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
    }

    var body: some Scene {
        WindowGroup {
            #if os(macOS)
                ProvideWindowSizeClass {
                    FlareRootView()
//                    .enableInjection()
                        .preferredColorScheme(.light) // 强制使用浅色模式
                }
                .handlesExternalEvents(preferring: ["flare"], allowing: ["flare"])
                .onOpenURL { url in
                    router.handleDeepLink(url)
                }
            #else
                FlareRootView()
//                .enableInjection()
                    // .preferredColorScheme(.light)  // 强制使用浅色模式
                    .onOpenURL { url in
                        router.handleDeepLink(url)
                    }
                    .environmentObject(router)
            #endif
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
