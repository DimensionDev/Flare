import AppKit
import FlareAppleCore
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import SwiftUI

struct StatusDetailScreen: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode

    @StateObject private var presenter: KotlinPresenter<StatusContextPresenterState>
    private let statusKey: MicroBlogKey

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.statusKey = statusKey
        _presenter = .init(
            wrappedValue: .init(
                presenter: StatusContextPresenter(accountType: accountType, statusKey: statusKey)
            )
        )
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                TimelinePagingContent(
                    data: presenter.state.listState,
                    detailStatusKey: statusKey
                )
            }
        }
        .detectScrolling()
        .id(presenter.key)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(detailBackground)
        .navigationTitle("status_detail_title")
    }

    private var detailBackground: Color {
        timelineDisplayMode == .plain
            ? .clear
            : Color(nsColor: .windowBackgroundColor)
    }
}
