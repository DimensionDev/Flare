import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI
import FlareAppleUI

struct TimelineScreen: View {
    @Environment(\.openWindow) private var openWindow
    let tabItem: UiTimelineTabItem
    let allowGalleryMode: Bool
    @StateObject private var presenter: KotlinPresenter<TimelineItemPresenterState>
    @Environment(\.timelineAppearance) private var timelineAppearance
    @StateObject private var canComposePresenter = KotlinPresenter(presenter: CanComposePresenter())

    init(tabItem: UiTimelineTabItem, allowGalleryMode: Bool = false) {
        self.tabItem = tabItem
        self.allowGalleryMode = allowGalleryMode
        _presenter = .init(wrappedValue: .init(presenter: TimelineItemPresenter(timelineTabItem: tabItem)))
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
}

struct ListTimelineScreen: View {
    let tabItem: UiTimelineTabItem

    var body: some View {
        TimelineScreen(tabItem: tabItem)
    }
}
