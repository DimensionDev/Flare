import SwiftUI
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import FlareAppleCore

struct StatusDetailScreen: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<StatusContextPresenterState>
    private let statusKey: MicroBlogKey

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.statusKey = statusKey
        self._presenter = .init(wrappedValue: .init(presenter: StatusContextPresenter(accountType: accountType, statusKey: statusKey)))
    }

    var body: some View {
        ZStack {
            UITimelinePagingView(
                data: presenter.state.listState,
                detailStatusKey: statusKey,
                key: presenter.key,
                suppressInitialRefreshIndicator: true
            )
                .frame(maxWidth: horizontalSizeClass == .compact ? .infinity : 600, alignment: .center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(timelineDisplayMode == .plain ? .clear : .systemGroupedBackground))
        .navigationTitle("status_detail_title")
    }
}
