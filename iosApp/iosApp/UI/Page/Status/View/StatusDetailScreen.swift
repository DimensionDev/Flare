import SwiftUI
import shared

struct StatusDetailScreen: View {
    @State private var presenter: StatusContextPresenter
    private let statusKey: MicroBlogKey

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
            //列表背景色
            .scrollContentBackground(.hidden)
            .background(Colors.Background.swiftUIPrimary)
            .refreshable {
                try? await state.refresh()
            }
        }
        //导航栏背景色
        // .navigationBarTitleDisplayMode(.inline)
        // .toolbarBackground(Colors.Background.swiftUIPrimary, for: .navigationBar)
        // .toolbarBackground(.visible, for: .navigationBar)
    }
}
