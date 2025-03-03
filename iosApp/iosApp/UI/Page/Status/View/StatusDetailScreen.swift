import shared
import SwiftUI
import Foundation
// 导入Navigation模块下的自定义组件
// import SwiftUI

struct StatusDetailScreen: View {
    @State private var presenter: StatusContextPresenter
    private let statusKey: MicroBlogKey
    
    // 获取全局的AppState
    @EnvironmentObject private var menuState: FLNewAppState
    
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
        // 使用封装的二级导航修饰符
        .secondNavigation()
        // 导航栏背景色（如需自定义可保留）
        // .navigationBarTitleDisplayMode(.inline)
        // .toolbarBackground(Colors.Background.swiftUIPrimary, for: .navigationBar)
        // .toolbarBackground(.visible, for: .navigationBar)
    }
}
