import SwiftUI
import UIKit
import FlareAppleUI
import FlareAppleCore
import KotlinSharedUI

struct IOSTimelineListEnvironment: ViewModifier {
    @State private var accounts = KotlinPresenter(presenter: AccountsPresenter())
    @State private var resolvedAccountKey = "pending"

    private var accountKey: String? {
        if case .success(let account) = onEnum(of: accounts.state.activeAccount) {
            return String(describing: account.data.accountKey)
        }
        if case .success(let values) = onEnum(of: accounts.state.accounts), values.data.count == 0 {
            return "guest"
        }
        return nil
    }

    func body(content: Content) -> some View {
        content
            .environment(\.timelineAccountID, resolvedAccountKey)
            .onChange(of: accountKey, initial: true) { _, key in
                guard let key, key != resolvedAccountKey else { return }
                resolvedAccountKey = key
            }
            .environment(\.timelineListRenderer, TimelineListRenderer {
                IOSTimelineList(request: $0).id("\(resolvedAccountKey):\($0.key)")
            })
    }
}

extension EnvironmentValues {
    @Entry var timelineAccountID = ""
}

private struct IOSTimelineList: View {
    let request: TimelineListRequest
    @Environment(\.self) private var environment
    @State private var headers = TimelineHeaderViews()

    var body: some View {
        GeometryReader { geometry in
            UITimelineCollectionView(
                data: posts,
                detailStatusKey: nil,
                userData: users,
                columnCount: TimelineColumnPolicy.adaptive.columnCount(for: geometry.size.width),
                accessoryItems: headers.update(request.headers, environment: environment)
            )
            .ignoresSafeArea(edges: .vertical)
        }
        .modifier(TimelineListBackground(columnPolicy: .adaptive))
    }

    private var posts: PagingState<UiTimelineV2>? {
        if case .posts(let data) = request.content { return data }
        return nil
    }

    private var users: PagingState<UiProfile>? {
        if case .users(let data) = request.content { return data }
        return nil
    }
}

private final class TimelineHeaderViews {
    private var views: [String: TimelineHostedAccessoryView] = [:]
    private var pinnedViews: [String: TimelineHostedAccessoryView] = [:]

    func update(_ headers: [TimelineListHeader], environment: EnvironmentValues) -> [UITimelineCollectionViewAccessoryItem] {
        let ids = Set(headers.map(\.id))
        views = views.filter { ids.contains($0.key) }
        pinnedViews = pinnedViews.filter { ids.contains($0.key) }
        return headers.map { header in
            let view = views[header.id] ?? TimelineHostedAccessoryView()
            views[header.id] = view
            view.update(AnyView(header.view.environment(\.self, environment)))
            var pinned: TimelineHostedAccessoryView?
            if header.isPinned {
                pinned = pinnedViews[header.id] ?? TimelineHostedAccessoryView()
                pinnedViews[header.id] = pinned
                pinned?.update(AnyView(header.view.environment(\.self, environment)))
            }
            return UITimelineCollectionViewAccessoryItem(id: header.id, view: view, pinnedView: pinned)
        }
    }
}
