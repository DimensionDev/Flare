import SwiftUI
import Combine
import UIKit
@preconcurrency import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI

struct TimelineScreen: View {
    let tabItem: UiTimelineTabItem
    var readingState: TimelineReadingState? = nil
    let allowGalleryMode: Bool
    let isHomeTimeline: Bool
    let accessoryItems: [UITimelineCollectionViewAccessoryItem]
    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.appSettings) private var appSettings
    @Environment(\.scenePhase) private var scenePhase
    @State var presenter: KotlinPresenter<TimelineItemPresenterState>
    @State private var isAtTop = true
    @State private var isTabRefreshInFlight = false
    init(
        tabItem: UiTimelineTabItem,
        readingState: TimelineReadingState? = nil,
        allowGalleryMode: Bool = true,
        isHomeTimeline: Bool = false,
        accessoryItems: [UITimelineCollectionViewAccessoryItem] = []
    ) {
        self.tabItem = tabItem
        self.readingState = readingState
        self.allowGalleryMode = allowGalleryMode
        self.isHomeTimeline = isHomeTimeline
        self.accessoryItems = accessoryItems
        self._presenter = .init(
            wrappedValue: .init(
                presenter: TimelineItemPresenter(
                    timelineTabItem: tabItem,
                    isHomeTimeline: isHomeTimeline
                )
            )
        )
    }
    var body: some View {
        UITimelinePagingView(
            data: presenter.state.listState,
            detailStatusKey: nil,
            key: "timeline:\(tabItem.id):\(tabItem.loaderKey)",
            readingState: readingState,
            allowGalleryMode: allowGalleryMode,
            accessoryItems: accessoryItems,
            onIsAtTopChanged: { isAtTop = $0 }
        )
            .environment(\.timelineAppearance, tabItem.resolveTimelineAppearance(base: timelineAppearance))
            .refreshable {
                try? await presenter.state.refreshSuspend()
            }
            .onReceive(NotificationCenter.default.publisher(for: .tabDoubleTapped)) { notification in
                guard notification.object as? String == HomeTabsPresenterStateHomeTabs.home.name.lowercased(),
                      isHomeTimeline, isAtTop, !isTabRefreshInFlight,
                      !presenter.state.isRefreshing else { return }
                isTabRefreshInFlight = true
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                Task {
                    defer { isTabRefreshInFlight = false }
                    try? await presenter.state.refreshSuspend()
                }
            }
            .task(id: "\(isHomeTimeline)-\(appSettings.homeTimelineAutoRefreshInterval.minutes)-\(scenePhase)") {
                try? await autoRefresh()
            }
    }

    private func autoRefresh() async throws {
        let minutes = appSettings.homeTimelineAutoRefreshInterval.minutes
        guard isHomeTimeline, minutes > 0, scenePhase == .active else { return }
        while true {
            try await Task.sleep(for: .seconds(minutes * 60))
            if !presenter.state.isRefreshing {
                try? await presenter.state.refreshSuspend()
            }
        }
    }
}

struct ListTimelineScreen:  View {
    let tabItem: UiTimelineTabItem
    var body: some View {
        TimelineScreen(tabItem: tabItem)
    }
}
