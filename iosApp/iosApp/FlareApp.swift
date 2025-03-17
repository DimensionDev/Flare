import shared
import SwiftUI

@main
struct FlareApp: SwiftUI.App {
    #if os(macOS)
        @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate
    #else
        @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    #endif
    init() {
        KoinHelper.shared.start(inAppNotification: SwitUIInAppNotification())
        
        // 初始化UserManager
        UserManager.shared.initialize()
        
        // 初始化AppBarTabSettingStore（使用游客模式）
        // UserManager初始化完成后会自动更新为正确的账号
        AppBarTabSettingStore.shared.initialize(with: AccountTypeGuest(), user: nil)
    }

    var body: some Scene {
        WindowGroup {
            #if os(macOS)
                ProvideWindowSizeClass {
                    RouterView()
//                    .enableInjection()
                        .preferredColorScheme(.light) // 强制使用浅色模式
                }
                .handlesExternalEvents(preferring: ["flare"], allowing: ["flare"])
            #else
                RouterView()
//                .enableInjection()
                // .preferredColorScheme(.light)  // 强制使用浅色模式
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
