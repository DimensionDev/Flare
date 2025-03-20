import SwiftUI
import SwiftfulRouting
import shared
import Combine

struct FlareRouterView: View {
    // 应用状态
    @StateObject private var appState: FlareAppState
    
    // 用户状态管理
    @State private var presenter = ActiveAccountPresenter()
    @State private var appSettings = AppSettings()
    
    init() {
        let tabStore = AppBarTabSettingStore.shared
        _appState = StateObject(wrappedValue: FlareAppState(tabProvider: tabStore))
    }
    
    var body: some View {
        // 使用SwiftfulRouting的RouterView作为根视图
        SwiftfulRouting.RouterView { router in
            // 监听用户状态
            ObservePresenter<UserState, ActiveAccountPresenter, AnyView>(presenter: presenter) { userState in
                // 创建更新状态的视图（不使用显式return）
                AnyView(
                    ZStack {
                        // 内容视图
                        Group {
                            if let accountType = appState.accountType {
                                // 使用新的标签栏视图
                                FlareTabBarView(accountType: accountType)
                                    .environment(\.appSettings, appSettings)
                                    .environmentObject(appState)
                                    .environment(\.appRouter, router)
                                    
                                // 处理sheet展示
                                .sheet(item: $appState.sheetDestination) { destination in
                                    destination.view(with: router)
                                }
                                
                                // 处理全屏展示
                                .fullScreenCover(item: $appState.fullScreenDestination) { destination in
                                    destination.view(with: router)
                                }
                                
                                // 监听导航通知
                                .onReceive(NotificationCenter.default.publisher(for: .flShowNewMenu)) { _ in
                                    // 导航到左侧菜单（可以在以后实现）
                                    print("左侧菜单导航请求")
                                }
                            } else {
                                // 加载中状态
                                ProgressView()
                            }
                        }
                        // 隐藏的视图，用于调用onAppear
                        Color.clear
                            .frame(width: 0, height: 0)
                            .onAppear {
                                // 在视图出现时更新用户状态
                                appState.updateUserState(userState)
                            }
                    }
                )
            }
        }
        // 添加平台特定的修饰符
        .modifier(PlatformSpecificModifier())
    }
    
    // 从UserState获取用户数据
    private func getUserData(from state: UserState) -> UiUserV2? {
        switch onEnum(of: state.user) {
        case let .success(data):
            return data.data
        default:
            return nil
        }
    }
}

// MARK: - 环境值扩展
private struct AppRouterKey: EnvironmentKey {
    static let defaultValue: AnyRouter? = nil
}

extension EnvironmentValues {
    var appRouter: AnyRouter? {
        get { self[AppRouterKey.self] }
        set { self[AppRouterKey.self] = newValue }
    }
}

// MARK: - 平台特定修饰符
private struct PlatformSpecificModifier: ViewModifier {
    func body(content: Content) -> some View {
        #if os(macOS)
        content
            .preferredColorScheme(.light) // macOS使用浅色模式
            .handlesExternalEvents(preferring: ["flare"], allowing: ["flare"])
        #else
        content
            // iOS无特殊修饰
        #endif
    }
} 