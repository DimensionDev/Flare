import SwiftUI
@preconcurrency import KotlinSharedUI

struct TimelineScreen: View {
    let tabItem: TimelineTabItem
    @Environment(\.openURL) private var openURL
    @StateObject var presenter: KotlinPresenter<TimelineItemPresenterState>
    init(tabItem: TimelineTabItem) {
        self.tabItem = tabItem
        self._presenter = .init(wrappedValue: .init(presenter: TimelineItemPresenter(timelineTabItem: tabItem)))
    }
    var body: some View {
//        ComposeTimelineView(
//            key: presenter.key,
//            data: presenter.state.listState,
//            detailStatusKey: nil,
//            topPadding: 0,
//            onOpenLink: { url in openURL.callAsFunction(.init(string: url)!) },
//            onExpand: {},
//            onCollapse: {}
//        )
//        .ignoresSafeArea()
        List {
            TimelinePagingView(data: presenter.state.listState)
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
            try? await presenter.state.refreshSuspend()
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle(tabItem.metaData.title.text)
    }
}
