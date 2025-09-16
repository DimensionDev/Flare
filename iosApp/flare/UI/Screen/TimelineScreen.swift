import SwiftUI
@preconcurrency import KotlinSharedUI

struct TimelineScreen: View {
    let tabItem: TimelineTabItem
    @Environment(\.openURL) private var openURL
    @State var presenter: KotlinPresenter<TimelineItemPresenterState>
    init(tabItem: TimelineTabItem) {
        self.tabItem = tabItem
        _presenter = .init(wrappedValue: .init(presenter: TimelineItemPresenter(timelineTabItem: tabItem)))
    }
    var body: some View {
        ScrollView {
            LazyVStack(
                spacing: 2,
            ) {
                PagingView(data: presenter.state.listState)
            }
            .padding(.horizontal)
        }
        .background(Color(.systemGroupedBackground))
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
        .navigationTitle(tabItem.metaData.title.text)
    }
}
