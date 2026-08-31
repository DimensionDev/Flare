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
    let accessoryItems: [UITimelineCollectionViewAccessoryItem]
    let onEditDraft: (String) -> Void
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.appSettings) private var appSettings
    @Environment(\.scenePhase) private var scenePhase
    @StateObject var presenter: KotlinPresenter<TimelineItemPresenterState>
    @State private var isAtTop = true
    @State private var isTabRefreshInFlight = false
    @StateObject private var outboxAccessoryStore = OutboxAccessoryStore()
    init(
        tabItem: UiTimelineTabItem,
        allowGalleryMode: Bool = false,
        isHomeTimeline: Bool = false,
        accessoryItems: [UITimelineCollectionViewAccessoryItem] = [],
        onEditDraft: @escaping (String) -> Void = { _ in }
    ) {
        self.tabItem = tabItem
        self.allowGalleryMode = allowGalleryMode
        self.isHomeTimeline = isHomeTimeline
        self.accessoryItems = accessoryItems
        self.onEditDraft = onEditDraft
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
        let outboxAccessoryItems = outboxAccessoryStore.update(
            posts: Array(presenter.state.outboxItems),
            onRetry: { presenter.state.retryOutbox(groupId: $0) },
            onEdit: onEditDraft,
            onDelete: { presenter.state.deleteOutbox(groupId: $0) }
        )
        UITimelinePagingView(
            data: presenter.state.listState,
            detailStatusKey: nil,
            key: presenter.key,
            allowGalleryMode: allowGalleryMode,
            accessoryItems: outboxAccessoryItems + accessoryItems,
            onIsAtTopChanged: { isAtTop = $0 }
        )
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

private final class OutboxAccessoryStore: ObservableObject {
    private var hosts: [String: OutboxHostedAccessoryView] = [:]

    func update(
        posts: [UiOutboxPost],
        onRetry: @escaping (String) -> Void,
        onEdit: @escaping (String) -> Void,
        onDelete: @escaping (String) -> Void
    ) -> [UITimelineCollectionViewAccessoryItem] {
        let activeIDs = Set(posts.map(\.groupId))
        hosts = hosts.filter { activeIDs.contains($0.key) }
        return posts.map { post in
            let host = hosts[post.groupId] ?? OutboxHostedAccessoryView()
            hosts[post.groupId] = host
            host.update(
                AnyView(
                    OutboxPostView(
                        post: post,
                        onRetry: { onRetry(post.groupId) },
                        onEdit: { onEdit(post.groupId) },
                        onDelete: { onDelete(post.groupId) }
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 4)
                )
            )
            return UITimelineCollectionViewAccessoryItem(id: "outbox_\(post.groupId)", view: host)
        }
    }
}

private final class OutboxHostedAccessoryView: UIView {
    private let host = UIHostingController(rootView: AnyView(EmptyView()))

    override init(frame: CGRect) {
        super.init(frame: frame)
        commonInit()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        commonInit()
    }

    func update(_ rootView: AnyView) {
        host.rootView = rootView
        host.view.invalidateIntrinsicContentSize()
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            host.willMove(toParent: nil)
            host.removeFromParent()
        } else if let parent = findParentViewController(), host.parent !== parent {
            host.willMove(toParent: nil)
            host.removeFromParent()
            parent.addChild(host)
            host.didMove(toParent: parent)
        }
    }

    private func commonInit() {
        backgroundColor = .clear
        host.view.backgroundColor = .clear
        host.view.translatesAutoresizingMaskIntoConstraints = false
        if #available(iOS 16.0, *) {
            host.sizingOptions = [.intrinsicContentSize]
        }
        addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.topAnchor.constraint(equalTo: topAnchor),
            host.view.leadingAnchor.constraint(equalTo: leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: trailingAnchor),
            host.view.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    private func findParentViewController() -> UIViewController? {
        var responder: UIResponder? = self
        while let current = responder {
            if let viewController = current as? UIViewController {
                return viewController
            }
            responder = current.next
        }
        return nil
    }
}

struct ListTimelineScreen:  View {
    let tabItem: UiTimelineTabItem
    var body: some View {
        TimelineScreen(tabItem: tabItem)
    }
}
