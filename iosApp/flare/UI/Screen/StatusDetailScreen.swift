import SwiftUI
@preconcurrency import KotlinSharedUI

struct StatusDetailScreen: View {
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<TimelineState>
    private let statusKey: MicroBlogKey

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.statusKey = statusKey
        self._presenter = .init(wrappedValue: .init(presenter: StatusContextPresenter(accountType: accountType, statusKey: statusKey)))
    }

    var body: some View {
        List {
            TimelinePagingView(data: presenter.state.listState, detailStatusKey: statusKey)
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .padding(.horizontal)
                .listRowBackground(Color.clear)
        }
        .scrollContentBackground(.hidden)
        .listRowSpacing(2)
        .listStyle(.plain)
        .background(Color(.systemGroupedBackground))
        .refreshable {
            try? await presenter.state.refresh()
        }
        .navigationTitle("status_detail_title")
    }
}
