import SwiftUI
@preconcurrency import KotlinSharedUI

struct StatusDetailScreen : View {
    @Environment(\.openURL) private var openURL
    @State private var presenter: KotlinPresenter<TimelineState>
    private let statusKey: MicroBlogKey
    
    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.statusKey = statusKey
        self._presenter = .init(initialValue: .init(presenter: StatusContextPresenter(accountType: accountType, statusKey: statusKey)))
    }
    
    var body: some View {
        ScrollView {
            LazyVStack(
                spacing: 2,
            ) {
                PagingView(data: presenter.state.listState, detailStatusKey: statusKey)
            }
            .padding(.horizontal)
        }
        .background(Color(.systemGroupedBackground))
        .refreshable {
            try? await presenter.state.refresh()
        }
        .navigationTitle("Detail")
    }
}
