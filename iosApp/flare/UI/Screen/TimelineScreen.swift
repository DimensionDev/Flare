import SwiftUI
import KotlinSharedUI

struct TimelineScreen: View {
    let tabItem: TimelineTabItem
    @Environment(\.openURL) private var openURL
    @State var presenter: KotlinPresenter<TimelineItemPresenterState>
    init(tabItem: TimelineTabItem) {
        self.tabItem = tabItem
        _presenter = .init(wrappedValue: .init(presenter: TimelineItemPresenter(timelineTabItem: tabItem)))
    }
    var body: some View {
        let state = presenter.state
        List {
            PagingView(data: state.listState) { item in
                TimelineView(data: item)
            }
        }
        .refreshable {
            try? await state.refreshSuspend()
        }
    }
}
