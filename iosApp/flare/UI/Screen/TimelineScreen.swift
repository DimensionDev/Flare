import SwiftUI
@preconcurrency import KotlinSharedUI

struct TimelineScreen: View {
    let tabItem: UiTimelineTabItem
    let allowGalleryMode: Bool
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject var presenter: KotlinPresenter<TimelineItemPresenterState>
    init(tabItem: UiTimelineTabItem, allowGalleryMode: Bool = false) {
        self.tabItem = tabItem
        self.allowGalleryMode = allowGalleryMode
        self._presenter = .init(wrappedValue: .init(presenter: TimelineItemPresenter(timelineTabItem: tabItem)))
    }
    var body: some View {
        TimelinePagingContent(data: presenter.state.listState, detailStatusKey: nil, key: presenter.key, allowGalleryMode: allowGalleryMode)
            .refreshable {
                try? await presenter.state.refreshSuspend()
            }
    }
}

struct ListTimelineScreen:  View {
    let tabItem: UiTimelineTabItem
    var body: some View {
        TimelineScreen(tabItem: tabItem)
    }
}
