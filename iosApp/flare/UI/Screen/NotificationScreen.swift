import SwiftUI
import SwiftUIBackports
@preconcurrency import KotlinSharedUI

struct NotificationScreen: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject private var presenter: KotlinPresenter<AllNotificationPresenterState> = .init(presenter: AllNotificationPresenter())
    @State private var showTopBar = true

    var body: some View {
        TimelinePagingContent(data: presenter.state.timeline, detailStatusKey: nil)
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
                        NotificationAccountsBar(
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
    let items: [UiProfile : KotlinInt]
    let selectedAccount: UiProfile?
    let onSelect: (UiProfile) -> Void

    var body: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                ForEach(Array(items.keys), id: \.handle) { key in
                    let value = items[key]?.intValue
                    HStack {
                        if selectedAccount?.key == key.key {
                            Label {
                                Text(key.handle)
                            } icon: {
                                AvatarView(data: key.avatar)
                                    .frame(width: 20, height: 20)
                            }
                        } else {
                            AvatarView(data: key.avatar)
                                .frame(width: 20, height: 20)
                        }
                        if let badge = value, badge > 0 {
                            Text("\(badge)")
                                .font(.caption2)
                                .padding(4)
                                .background(
                                    Circle()
                                        .fill(Color.accentColor)
                                )
                                .foregroundStyle(.white)
                        }
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
