import SwiftUI
import Combine
import UIKit
@preconcurrency import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI
import SwiftUIBackports

struct TimelineScreen: View {
    let tabItem: UiTimelineTabItem
    let allowGalleryMode: Bool
    let isHomeTimeline: Bool
    let accessoryItems: [UITimelineCollectionViewAccessoryItem]
    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.appSettings) private var appSettings
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State var presenter: KotlinPresenter<TimelineItemPresenterState>
    @State private var isAtTop = true
    @State private var isTabRefreshInFlight = false
    init(
        tabItem: UiTimelineTabItem,
        allowGalleryMode: Bool = true,
        isHomeTimeline: Bool = false,
        accessoryItems: [UITimelineCollectionViewAccessoryItem] = []
    ) {
        self.tabItem = tabItem
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
            allowGalleryMode: allowGalleryMode,
            accessoryItems: accessoryItems,
            onIsAtTopChanged: { isAtTop = $0 }
        )
            .environment(\.timelineAppearance, tabItem.resolveTimelineAppearance(base: timelineAppearance))
        .environment(\.timelineReading, presenter.state.reading)
        .overlay(alignment: .top) {
            VStack(spacing: 0) {
                if presenter.state.reading?.hasNewContent == true {
                    Button {
                        presenter.state.reading?.showLatest()
                    } label: {
                        Label("home_timeline_view_latest", systemImage: "chevron.up.2")
                            .font(.subheadline)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                    }
                    .buttonStyle(.plain)
                    .backport
                    .glassEffect(.regularInteractive, in: .capsule, fallbackBackground: .regularMaterial)
                    .accessibilityIdentifier("home-new-content")
                    .transition(reduceMotion ? .opacity : .move(edge: .top))
                }
            }
            .animation(
                reduceMotion ? .easeInOut(duration: 0.15) : .spring(response: 0.3, dampingFraction: 1),
                value: presenter.state.reading?.hasNewContent == true
            )
            .clipped()
            .padding(.top, 8)
        }
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
