import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI
import FlareAppleUI

struct TimelineScreen: View {
    @Environment(\.openWindow) private var openWindow
    let tabItem: UiTimelineTabItem
    let allowGalleryMode: Bool
    let isHomeTimeline: Bool
    @State private var presenter: KotlinPresenter<TimelineItemPresenterState>
    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.appSettings) private var appSettings
    @Environment(\.scenePhase) private var scenePhase
    @State private var canComposePresenter = KotlinPresenter(presenter: CanComposePresenter())

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
            readingState: presenter.state.readingState,
            allowGalleryMode: allowGalleryMode
        )
        .overlay(alignment: .top) {
            if let reading = presenter.state.readingState, reading.hasNewContent {
                Button {
                    Task { try? await reading.showLatest() }
                } label: {
                    Label("home_timeline_new_content", systemImage: "arrow.up")
                        .padding(.horizontal, 12).padding(.vertical, 6)
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
        .onDisappear { presenter.state.readingState?.savePosition() }
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
                if let reading = presenter.state.readingState {
                    if reading.position == nil { try? await reading.refreshAutomatically() }
                } else {
                    try? await presenter.state.refreshSuspend()
                }
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
