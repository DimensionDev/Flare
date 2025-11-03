import SwiftUI
@preconcurrency import KotlinSharedUI

struct StatusDetailScreen: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<TimelineState>
    private let statusKey: MicroBlogKey

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.statusKey = statusKey
        self._presenter = .init(wrappedValue: .init(presenter: StatusContextPresenter(accountType: accountType, statusKey: statusKey)))
    }

    var body: some View {
        ZStack {
            List {
                TimelinePagingView(data: presenter.state.listState, detailStatusKey: statusKey)
                    .listRowSeparator(.hidden)
                    .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                    .padding(.horizontal)
                    .listRowBackground(Color.clear)
            }
            .detectScrolling()
            .scrollContentBackground(.hidden)
            .listRowSpacing(2)
            .listStyle(.plain)
            .refreshable {
                try? await presenter.state.refresh()
            }
            .frame(maxWidth: horizontalSizeClass == .compact ? .infinity : 600, alignment: .center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemGroupedBackground))
        .navigationTitle("status_detail_title")
    }
}
