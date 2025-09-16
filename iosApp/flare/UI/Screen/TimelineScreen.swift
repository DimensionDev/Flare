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
        List {
            TimelinePagingView(data: presenter.state.listState)
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .padding(.horizontal)
        }
        .listRowSpacing(2)
        .listStyle(.plain)
        .background(Color(.systemGroupedBackground))
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
        .navigationTitle(tabItem.metaData.title.text)
    }
}
