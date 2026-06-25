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
        .overlay(alignment: .top) {
            if presenter.state.isRefreshing {
                ProgressView()
                    .progressViewStyle(.linear)
                    .padding(.horizontal)
            }
        }
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
                        Image(fontAwesome: .arrowsRotate)
                    }

                }
            }
            if case .success(let value) = onEnum(of: canComposePresenter.state.canCompose), value.data.boolValue {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        MacComposeWindowCoordinator.shared.openNew(openWindow: openWindow)
                    } label: {
                        Label {
                            Text("home_compose")
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
