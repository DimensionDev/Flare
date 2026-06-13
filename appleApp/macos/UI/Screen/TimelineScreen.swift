import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI

struct TimelineScreen: View {
    let tabItem: UiTimelineTabItem
    let allowGalleryMode: Bool
    @StateObject private var presenter: KotlinPresenter<TimelineItemPresenterState>

    init(tabItem: UiTimelineTabItem, allowGalleryMode: Bool = false) {
        self.tabItem = tabItem
        self.allowGalleryMode = allowGalleryMode
        _presenter = .init(wrappedValue: .init(presenter: TimelineItemPresenter(timelineTabItem: tabItem)))
    }

    var body: some View {
        TimelinePagingContent(
            data: presenter.state.listState,
            detailStatusKey: nil,
            key: presenter.key,
            allowGalleryMode: allowGalleryMode
        )
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
    }
}

struct ListTimelineScreen: View {
    let tabItem: UiTimelineTabItem

    var body: some View {
        TimelineScreen(tabItem: tabItem)
    }
}
