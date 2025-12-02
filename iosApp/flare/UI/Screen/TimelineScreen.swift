import SwiftUI
@preconcurrency import KotlinSharedUI

struct TimelineScreen: View {
    let tabItem: TimelineTabItem
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
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
        TimelinePagingContent(data: presenter.state.listState, detailStatusKey: nil)
            .refreshable {
                try? await presenter.state.refreshSuspend()
            }
    }
}

struct ListTimelineScreen:  View {
    let tabItem: ListTimelineTabItem
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<ListInfoState>
    @State private var showEditListSheet = false
    init(tabItem: ListTimelineTabItem) {
        self.tabItem = tabItem
        self._presenter = .init(wrappedValue: .init(presenter: ListInfoPresenter(accountType: tabItem.account, listId: tabItem.listId)))
    }
    var body: some View {
        TimelineScreen(tabItem: tabItem)
            .sheet(isPresented: $showEditListSheet) {
                NavigationStack {
                    EditListScreen(accountType: tabItem.account, listId: tabItem.listId)
                }
            }
            .toolbar {
                if case .success(let success) = onEnum(of: presenter.state.listInfo), !success.data.readonly {
                    ToolbarItem(placement: .primaryAction) {
                        Button {
                            showEditListSheet = true
                        } label: {
                            Text("edit_list_title")
                        }
                    }
                }
            }
    }
}
