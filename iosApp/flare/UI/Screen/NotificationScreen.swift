import SwiftUI
import SwiftUIBackports
@preconcurrency import KotlinSharedUI

struct NotificationScreen: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject private var presenter: KotlinPresenter<AllNotificationPresenterState> = .init(presenter: AllNotificationPresenter())
    @State private var showTopBar = true

    var body: some View {
        TimelinePagingContent(data: presenter.state.timeline, detailStatusKey: nil, key: presenter.key)
            .refreshable {
                try? await presenter.state.refreshSuspend()
            }
            .detectScrolling()
            .if(presenter.state.notifications.count >= 1 && horizontalSizeClass == .compact) { view in
                view
                    .safeAreaInset(edge: .top) {
                        StateView(state: presenter.state.supportedNotificationFilters) { allTypesAny in
                            let allTypes = allTypesAny.cast(NotificationFilter.self)
                            NotificationFilterSegments(
                                allTypes: allTypes,
                                selected: presenter.state.selectedFilter,
                                onSelect: { presenter.state.setFilter(filter: $0) }
                            )
                            .padding(.horizontal)
                        }
                    }
            }
            .toolbar {
                if presenter.state.notifications.count > 1 {
                    ToolbarItem {
                        NotificationAccountsMenu(
                            items: presenter.state.notifications,
                            selectedAccount: presenter.state.selectedAccount,
                            onSelect: { presenter.state.setAccount(profile: $0) }
                        )
                    }
                    if horizontalSizeClass == .regular {
                        if #available(iOS 26.0, *) {
                            ToolbarSpacer()
                        }
                        ToolbarItem {
                            StateView(state: presenter.state.supportedNotificationFilters) { allTypesAny in
                                let allTypes = allTypesAny.cast(NotificationFilter.self)
                                NotificationFilterSegments(
                                    allTypes: allTypes,
                                    selected: presenter.state.selectedFilter,
                                    onSelect: { presenter.state.setFilter(filter: $0) }
                                )
                                .padding(.horizontal)
                            }
                        }
                    }
                    
                } else {
                    ToolbarItem(placement: .title) {
                        StateView(state: presenter.state.supportedNotificationFilters) { allTypesAny in
                            let allTypes = allTypesAny.cast(NotificationFilter.self)
                            NotificationFilterSegments(
                                allTypes: allTypes,
                                selected: presenter.state.selectedFilter,
                                onSelect: { presenter.state.setFilter(filter: $0) }
                            )
                            .padding(.horizontal)
                        }
                    }
                }
            }
    }
}

struct NotificationFilterSegments: View {
    let allTypes: [NotificationFilter]
    let selected: NotificationFilter?
    let onSelect: (NotificationFilter) -> Void

    var body: some View {
        if allTypes.count > 1 {
            Picker("notification_type_title", selection: Binding<NotificationFilter>(
                get: { selected ?? allTypes.first ?? .all },
                set: { value in onSelect(value) }
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

struct NotificationAccountsBar: View {
    let items: [NotificationAccountItem]
    let selectedAccount: UiProfile?
    let onSelect: (UiProfile) -> Void

    var body: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                ForEach(items, id: \.stableKey) { item in
                    let key = item.profile
                    let value = item.badge
                    HStack {
                        ZStack(alignment: .bottomTrailing) {
                            AvatarView(data: key.avatar)
                            if value > 0 {
                                Text("\(value)")
                                    .font(.caption2)
                                    .padding(2)
                                    .background(
                                        Circle()
                                            .fill(Color.red)
                                    )
                                    .foregroundStyle(.white)
                                    .frame(width: 12, height: 12)
                            }
                        }
                        Text(key.handle.canonical)
                    }
                    .onTapGesture {
                        onSelect(key)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 8)
                    .foregroundStyle(selectedAccount?.key == key.key ? Color.white : .primary)
                    .backport
                    .glassEffect(selectedAccount?.key == key.key ? .tinted(.accentColor) : .regular, in: .capsule, fallbackBackground: selectedAccount?.key == key.key ? Color.accentColor : Color(.systemBackground))
                }
            }
            .padding(.vertical, 8)
        }
        .scrollIndicators(.hidden)
    }
}

struct NotificationAccountsMenu: View {
    let items: [NotificationAccountItem]
    let selectedAccount: UiProfile?
    let onSelect: (UiProfile) -> Void

    private var unreadItems: [NotificationAccountItem] {
        items.filter { $0.badge > 0 }
    }

    private var unreadSummary: String? {
        guard !unreadItems.isEmpty else {
            return nil
        }

        let visible = unreadItems.prefix(2).map { $0.profile.handle.canonical }
        let remaining = unreadItems.count - visible.count
        if remaining > 0 {
            return visible.joined(separator: " · ") + " +\(remaining)"
        } else {
            return visible.joined(separator: " · ")
        }
    }

    var body: some View {
        Menu {
            ForEach(items, id: \.stableKey) { item in
                Toggle(isOn: Binding(get: {
                    selectedAccount?.key == item.profile.key
                }, set: { value in
                    if value {
                        onSelect(item.profile)
                    }
                })) {
                    Label {
                        Text(item.profile.handle.canonical)
                    } icon: {
                        AvatarView(data: item.profile.avatar)
                            .overlay(alignment: .bottomTrailing) {
                            }
                    }
                    if item.badge > 0 {
                        Text("\(item.badge)")
                    }
                }
            }
        } label: {
            if let selectedAccount {
                HStack(spacing: 8) {
                    AvatarView(data: selectedAccount.avatar)
                        .frame(width: 24, height: 24)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(selectedAccount.handle.canonical)
                            .lineLimit(1)
                        if let unreadSummary {
                            Text("New from \(unreadSummary)")
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
