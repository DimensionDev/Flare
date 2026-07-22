import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI
import FlareAppleUI

struct TimelineScreen: View {
    @Environment(\.openWindow) private var openWindow
    let tabItem: UiTimelineTabItem
    let allowGalleryMode: Bool
    let isHomeTimeline: Bool
    @StateObject private var presenter: KotlinPresenter<TimelineItemPresenterState>
    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.appSettings) private var appSettings
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var canComposePresenter = KotlinPresenter(presenter: CanComposePresenter())

    init(tabItem: UiTimelineTabItem, allowGalleryMode: Bool = false, isHomeTimeline: Bool = false) {
        self.tabItem = tabItem
        self.allowGalleryMode = allowGalleryMode
        self.isHomeTimeline = isHomeTimeline
        _presenter = .init(
            wrappedValue: .init(
                presenter: TimelineItemPresenter(
                    timelineTabItem: tabItem,
                    isHomeTimeline: isHomeTimeline
                )
            )
        )
    }

    var body: some View {
        TimelinePagingView(
            data: presenter.state.listState,
            detailStatusKey: nil,
            key: presenter.key,
            allowGalleryMode: allowGalleryMode
        )
        .environment(\.timelineAppearance, tabItem.resolveTimelineAppearance(base: timelineAppearance))
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
        .task(id: "\(isHomeTimeline)-\(appSettings.homeTimelineAutoRefreshInterval.minutes)-\(scenePhase)") {
            try? await autoRefresh()
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    presenter.state.refreshSync()
                } label: {
                    Label {
                        Text("Refresh")
                    } icon: {
                        if presenter.state.isRefreshing {
                            ProgressView()
                                .progressViewStyle(.circular)
                                .scaleEffect(0.5)
                                .frame(width: 12, height: 12)
                        } else {
                            Image(fontAwesome: .arrowsRotate)
                        }
                    }
                }
                .disabled(presenter.state.isRefreshing)
            }
            if case .success(let value) = onEnum(of: canComposePresenter.state.canCompose), value.data.boolValue {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        MacComposeWindowCoordinator.shared.openNew(openWindow: openWindow)
                    } label: {
                        Label {
                            Text("compose_title_new")
                        } icon: {
                            Image(fontAwesome: .penToSquare)
                        }
                    }
                }
            }
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

struct ListTimelineScreen: View {
    let tabItem: UiTimelineTabItem

    var body: some View {
        TimelineScreen(tabItem: tabItem)
    }
}
