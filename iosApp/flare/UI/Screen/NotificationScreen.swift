import SwiftUI
import SwiftUIBackports
@preconcurrency import KotlinSharedUI

struct NotificationScreen: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject private var presenter: KotlinPresenter<AllNotificationPresenterState> = .init(presenter: AllNotificationPresenter())
    @State private var selectedAccountStableKey: String?
    @State private var selectedFilter: NotificationFilter?
    @State private var filterSegmentsHeight: CGFloat = 0

    private var notificationItems: [NotificationAccountItem] {
        presenter.state.notifications
    }

    private var supportedFilters: [NotificationFilter] {
        switch onEnum(of: presenter.state.supportedNotificationFilters) {
        case .success(let data):
            data.data.cast(NotificationFilter.self)
        case .loading, .error:
            []
        }
    }

    private var presenterSelectedAccountStableKey: String? {
        let selectedStableKey =
            presenter.state.selectedAccount.flatMap { profile in
                let stableKey = "\(profile.key.host):\(profile.key.id)"
                return notificationItems.contains(where: { $0.stableKey == stableKey }) ? stableKey : nil
            }
        return selectedStableKey ?? notificationItems.first?.stableKey
    }

    private var presenterSelectedFilterStableKey: String? {
        presenter.state.selectedFilter?.stableKey
    }

    private var notificationItemsSignature: String {
        notificationItems.map(\.stableKey).joined(separator: "|")
    }

    private var supportedFiltersSignature: String {
        supportedFilters.map(\.stableKey).joined(separator: "|")
    }

    private var isSyncingAccountSelection: Bool {
        selectedAccountStableKey != presenterSelectedAccountStableKey
    }

    private var timelineKey: String {
        [
            presenter.key,
            presenterSelectedAccountStableKey ?? "none",
            presenterSelectedFilterStableKey ?? "none",
        ].joined(separator: "::")
    }

    private var filterSegments: some View {
        NotificationFilterSegments(
            allTypes: supportedFilters,
            selected: $selectedFilter
        )
        .padding(.horizontal)
        .background {
            GeometryReader { proxy in
                Color.clear
                    .onAppear {
                        filterSegmentsHeight = proxy.size.height
                    }
                    .onChange(of: proxy.size.height) { height in
                        filterSegmentsHeight = height
                    }
            }
        }
    }

    var body: some View {
        TimelinePagingContent(
            data: presenter.state.timeline,
            detailStatusKey: nil,
            key: timelineKey,
            topContentInset: horizontalSizeClass == .compact && !isSyncingAccountSelection ? filterSegmentsHeight + 8 : 0
        )
            .id(timelineKey)
            .refreshable {
                try? await presenter.state.refreshSuspend()
            }
            .detectScrolling()
            .if(!notificationItems.isEmpty && horizontalSizeClass == .compact && !isSyncingAccountSelection) { view in
                view
                    .safeAreaInset(edge: .top) {
                        filterSegments
                    }
            }
            .toolbar {
                if notificationItems.count > 1 {
                    ToolbarItem {
                        NotificationAccountsMenu(
                            items: notificationItems,
                            selectedStableKey: $selectedAccountStableKey
                        )
                    }
                    if horizontalSizeClass == .regular && !isSyncingAccountSelection {
                        if #available(iOS 26.0, *) {
                            ToolbarSpacer()
                        }
                        ToolbarItem {
                            filterSegments
                        }
                    }

                }
            }
            .onAppear {
                syncSelectedAccountFromPresenter()
                syncSelectedFilterFromPresenter()
            }
            .onChange(of: presenterSelectedAccountStableKey) { _ in
                syncSelectedAccountFromPresenter()
                syncSelectedFilterFromPresenter()
            }
            .onChange(of: notificationItemsSignature) { _ in
                syncSelectedAccountFromPresenter()
            }
            .onChange(of: selectedAccountStableKey) { _ in
                syncSelectedAccountToPresenter()
            }
            .onChange(of: presenterSelectedFilterStableKey) { _ in
                syncSelectedFilterFromPresenter()
            }
            .onChange(of: supportedFiltersSignature) { _ in
                syncSelectedFilterFromPresenter()
            }
            .onChange(of: selectedFilter?.stableKey) { _ in
                syncSelectedFilterToPresenter()
            }
    }

    private func syncSelectedAccountFromPresenter() {
        if selectedAccountStableKey != presenterSelectedAccountStableKey {
            selectedAccountStableKey = presenterSelectedAccountStableKey
        }
    }

    private func syncSelectedAccountToPresenter() {
        guard
            let selectedAccountStableKey,
            selectedAccountStableKey != presenterSelectedAccountStableKey,
            let profile = notificationItems.first(where: { $0.stableKey == selectedAccountStableKey })?.profile
        else {
            return
        }
        presenter.state.setAccount(profile: profile)
    }

    private func syncSelectedFilterFromPresenter() {
        let resolvedFilter: NotificationFilter?
        if supportedFilters.isEmpty {
            resolvedFilter = presenter.state.selectedFilter
        } else if let presenterFilter = presenter.state.selectedFilter {
            resolvedFilter =
                supportedFilters.first(where: { $0.stableKey == presenterFilter.stableKey }) ??
                supportedFilters.first
        } else {
            resolvedFilter = supportedFilters.first
        }

        if selectedFilter?.stableKey != resolvedFilter?.stableKey {
            selectedFilter = resolvedFilter
        }
    }

    private func syncSelectedFilterToPresenter() {
        guard
            let selectedFilter,
            selectedFilter.stableKey != presenterSelectedFilterStableKey
        else {
            return
        }
        presenter.state.setFilter(filter: selectedFilter)
    }
}

struct NotificationFilterSegments: View {
    let allTypes: [NotificationFilter]
    @Binding var selected: NotificationFilter?

    private var resolvedSelection: NotificationFilter {
        if let selected = selected,
           let matchingFilter = allTypes.first(where: { $0.stableKey == selected.stableKey }) {
            return matchingFilter
        }
        return allTypes.first ?? .all
    }

    var body: some View {
        if allTypes.count > 1 {
            Picker("notification_type_title", selection: Binding<NotificationFilter>(
                get: { resolvedSelection },
                set: { value in selected = value }
            )) {
                ForEach(allTypes, id: \.self) { type in
                    switch type {
                    case .all:
                        Text("notification_type_all").tag(type)
                    case .comment:
                        Text("notification_type_comments").tag(type)
                    case .like:
                        Text("notification_type_likes").tag(type)
                    case .mention:
                        Text("notification_type_mentions").tag(type)
                    }
                }
            }
            .pickerStyle(.segmented)
        }
    }
}

struct NotificationAccountsMenu: View {
    let items: [NotificationAccountItem]
    @Binding var selectedStableKey: String?

    private var resolvedSelectedStableKey: String? {
        if let selectedStableKey,
           items.contains(where: { $0.stableKey == selectedStableKey }) {
            return selectedStableKey
        }
        return items.first?.stableKey
    }

    private var unreadItems: [NotificationAccountItem] {
        items.filter { $0.badge > 0 }
    }

    private var totalUnreadCount: Int {
        unreadItems.reduce(0) { partialResult, item in
            partialResult + Int(item.badge)
        }
    }

    private var selectedItem: NotificationAccountItem? {
        items.first(where: { $0.stableKey == resolvedSelectedStableKey }) ?? items.first
    }

    private func unreadText(for count: Int) -> String? {
        guard count > 0 else {
            return nil
        }
        return "\(count) unread"
    }

    var body: some View {
        if items.count > 1 {
            Menu {
                ForEach(items, id: \.stableKey) { item in
                    Toggle(isOn: Binding(
                        get: { resolvedSelectedStableKey == item.stableKey },
                        set: { isSelected in
                            if isSelected {
                                selectedStableKey = item.stableKey
                            }
                        }
                    )) {
                        Label {
                            Text(item.profile.handle.canonical)
                        } icon: {
                            AvatarView(data: item.profile.avatar)
                        }
                        if let unreadText = unreadText(for: Int(item.badge)) {
                            Text(unreadText)
                        }
                    }
                }
            } label: {
                if let selectedItem {
                    let selectedAccount = selectedItem.profile
                    HStack(spacing: 8) {
                        AvatarView(data: selectedAccount.avatar)
                            .frame(width: 24, height: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(selectedAccount.handle.canonical)
                                .lineLimit(1)
                            if let unreadText = unreadText(for: totalUnreadCount) {
                                Text(unreadText)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        Image("fa-chevron-down")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .scaledToFit()
                            .frame(width: 8, height: 8)
                            .padding(8)
                            .background(
                                Circle()
                                    .fill(Color.secondary.opacity(0.2))
                            )
                            .scaleEffect(0.66)
                    }
                }
            }
        }
    }
}

private extension NotificationFilter {
    var stableKey: String {
        switch self {
        case .all:
            "all"
        case .comment:
            "comment"
        case .like:
            "like"
        case .mention:
            "mention"
        }
    }
}
