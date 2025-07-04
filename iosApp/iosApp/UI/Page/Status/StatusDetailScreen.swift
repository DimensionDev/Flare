import Foundation
import os.log
import shared
import SwiftUI

struct StatusDetailScreen: View {
    @State private var presenter: StatusContextPresenter
    private let statusKey: MicroBlogKey

    @Environment(FlareRouter.self) private var router
    @Environment(FlareAppState.self) private var menuState
    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
        self.statusKey = statusKey
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                StatusTimelineComponent(
                    data: state.listState,
                    detailKey: statusKey
                )
                // .padding(.horizontal, 16)
            }
            .listStyle(.plain)
            // 列表背景色
            .scrollContentBackground(.hidden)
            .background(theme.primaryBackgroundColor)
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
        .environment(router)
        .environment(menuState)
        // 使用新的导航手势修饰符
        // .navigationBarTitleDisplayMode(.inline)
        // .toolbarBackground(Colors.Background.swiftUIPrimary, for: .navigationBar)
        // .toolbarBackground(.visible, for: .navigationBar)
    }
}
