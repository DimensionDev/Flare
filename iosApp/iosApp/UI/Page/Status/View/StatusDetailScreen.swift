import Foundation
import shared
import SwiftUI

// 导入Navigation模块下的自定义组件
// import SwiftUI

struct StatusDetailScreen: View {
    @State private var presenter: StatusContextPresenter
    private let statusKey: MicroBlogKey

    // 获取全局的AppState
    @EnvironmentObject private var menuState: FlareAppState

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
        }
        // 使用新的导航手势修饰符
        .environmentObject(menuState)
        // 导航栏背景色（如需自定义可保留）
        // .navigationBarTitleDisplayMode(.inline)
        // .toolbarBackground(Colors.Background.swiftUIPrimary, for: .navigationBar)
        // .toolbarBackground(.visible, for: .navigationBar)
    }
}
