import Foundation
import os.log
import shared
import SwiftUI

struct StatusDetailScreen: View {
    @State private var presenter: StatusContextPresenter
    private let statusKey: MicroBlogKey

    @ObservedObject var router: FlareRouter
    @EnvironmentObject private var menuState: FlareAppState

    init(accountType: AccountType, statusKey: MicroBlogKey, router: FlareRouter) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
        self.statusKey = statusKey
        self.router = router
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                StatusTimelineComponent(
                    data: state.listState,
                    detailKey: statusKey
                )
                // 列表项背景色：
                .listRowBackground(Colors.Background.swiftUIPrimary)
            }
            .listStyle(.plain)
            // 列表背景色
            .scrollContentBackground(.hidden)
            .background(Colors.Background.swiftUIPrimary)
            .refreshable {
                try? await state.refresh()
            }
            .onAppear {
                os_log("[StatusDetailScreen] onAppear - Router: %{public}@, current depth: %{public}d",
                       log: .default, type: .debug,
                       String(describing: ObjectIdentifier(router)),
                       router.navigationDepth)
            }
            .onDisappear {
                os_log("[StatusDetailScreen] onDisappear - Router: %{public}@, current depth: %{public}d",
                       log: .default, type: .debug,
                       String(describing: ObjectIdentifier(router)),
                       router.navigationDepth)
            }
        }
        // 确保同时传递router和menuState
        .environmentObject(router)
        .environmentObject(menuState)
        // 使用新的导航手势修饰符
        // .navigationBarTitleDisplayMode(.inline)
        // .toolbarBackground(Colors.Background.swiftUIPrimary, for: .navigationBar)
        // .toolbarBackground(.visible, for: .navigationBar)
    }
}
