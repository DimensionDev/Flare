import SwiftUI
import Combine
import UIKit
@preconcurrency import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI

struct TimelineScreen: View {
    let tabItem: UiTimelineTabItem
    let allowGalleryMode: Bool
    let isHomeTimeline: Bool
    let visibilityBounds: CGRect?
    let accessoryItems: [UITimelineCollectionViewAccessoryItem]
    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.appSettings) private var appSettings
    @Environment(\.scenePhase) private var scenePhase
    @State var presenter: KotlinPresenter<TimelineItemPresenterState>
    @State private var isVisible = false
    @State private var isAtTop = true
    @State private var isTabRefreshInFlight = false
    init(
        tabItem: UiTimelineTabItem,
        allowGalleryMode: Bool = true,
        isHomeTimeline: Bool = false,
        visibilityBounds: CGRect? = nil,
        accessoryItems: [UITimelineCollectionViewAccessoryItem] = []
    ) {
        self.tabItem = tabItem
        self.allowGalleryMode = allowGalleryMode
        self.isHomeTimeline = isHomeTimeline
        self.visibilityBounds = visibilityBounds
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
            readingState: presenter.state.readingState,
            allowGalleryMode: allowGalleryMode,
            accessoryItems: accessoryItems,
            onIsAtTopChanged: { isAtTop = $0 }
        )
            .overlay(alignment: .top) {
                if let reading = presenter.state.readingState, reading.hasNewContent {
                    Button {
                        Task { try? await reading.showLatest() }
                    } label: {
                        Label("home_timeline_new_content", systemImage: "arrow.up")
                            .padding(.horizontal, 16).padding(.vertical, 8)
                    }
                    .buttonStyle(.plain)
                    .background(.regularMaterial, in: Capsule())
                    .accessibilityHint(Text("home_timeline_view_latest"))
                    .padding(.top, 8)
                }
            }
            .onChange(of: scenePhase) { _, phase in
                if phase != .active { presenter.state.readingState?.savePosition() }
            }
            .background {
                GeometryReader { proxy in
                    Color.clear.onChange(of: proxy.frame(in: .global), initial: true) { _, frame in
                        isVisible = frame.intersects(visibilityBounds ?? frame) && frame.width > 0 && frame.height > 0
                    }
                }
            }
            .onDisappear {
                isVisible = false
                presenter.state.readingState?.savePosition()
            }
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
            .task(id: "\(isHomeTimeline)-\(appSettings.homeTimelineAutoRefreshInterval.minutes)-\(scenePhase)-\(isVisible)") {
                try? await autoRefresh()
            }
    }

    private func autoRefresh() async throws {
        let minutes = appSettings.homeTimelineAutoRefreshInterval.minutes
        guard isHomeTimeline, isVisible, minutes > 0, scenePhase == .active else { return }
        while true {
            try await Task.sleep(for: .seconds(minutes * 60))
            if !presenter.state.isRefreshing {
                if let reading = presenter.state.readingState {
                    if reading.position == nil { try? await reading.refreshAutomatically() }
                } else {
                    try? await presenter.state.refreshSuspend()
                }
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
