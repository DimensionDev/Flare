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
            TimelinePagingContent(data: presenter.state.listState, detailStatusKey: statusKey, key: presenter.key)
                .frame(maxWidth: horizontalSizeClass == .compact ? .infinity : 600, alignment: .center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemGroupedBackground))
        .navigationTitle("status_detail_title")
    }
}
