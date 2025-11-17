import SwiftUI
import SwiftUIBackports
@preconcurrency import KotlinSharedUI

struct NotificationScreen: View {
    @StateObject private var presenter: KotlinPresenter<AllNotificationPresenterState> = .init(presenter: AllNotificationPresenter())
    @Environment(\.openURL) private var openURL
    @State private var showTopBar = true

    var body: some View {
        TimelinePagingContent(data: presenter.state.timeline, detailStatusKey: nil)
            .refreshable {
                try? await presenter.state.refreshSuspend()
            }
            .detectScrolling()
            .safeAreaInset(edge: .top) {
                StateView(state: presenter.state.supportedNotificationFilters) { allTypes in
                    let allTypes = allTypes.cast(NotificationFilter.self)
                    if allTypes.count > 1 {
                        Picker("notification_type_title", selection: Binding(get: {
                            presenter.state.selectedFilter ?? allTypes.first ?? .all
                        }, set: { value in
                            presenter.state.setFilter(filter: value)
                        })) {
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
                        .padding(.horizontal)
                    }
                }
            }
            .toolbar {
                ToolbarItem {
                    StateView(state: presenter.state.notifications) { dic in
                        if let items = dic as? [UiProfile : Int32] {
                            ScrollView(.horizontal) {
                                HStack(
                                    spacing: 8,
                                ) {
                                    ForEach(Array(items.keys), id: \.handle) { key in
                                        let value = items[key]
                                        ZStack {
                                            if presenter.state.selectedAccount?.key == key.key {
                                                Label {
                                                    Text(key.handle)
                                                        .badge(value != nil && value! > 0 ? value! : 0)
                                                } icon: {
                                                    AvatarView(data: key.avatar)
                                                        .frame(width: 20, height: 20)
                                                }
                                            } else {
                                                AvatarView(data: key.avatar)
                                                    .frame(width: 20, height: 20)
                                                    .badge(value != nil && value! > 0 ? value! : 0)
                                            }
                                        }
                                        .onTapGesture {
                                            presenter.state.setAccount(profile: key)
                                        }
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 8)
                                        .foregroundStyle(presenter.state.selectedAccount?.key == key.key ? Color.white : .primary)
                                        .backport
                                        .glassEffect(presenter.state.selectedAccount?.key == key.key ? .tinted(.accentColor) : .regular, in: .capsule, fallbackBackground: presenter.state.selectedAccount?.key == key.key ? Color.accentColor : Color(.systemBackground))
                                    }
                                }
                                .padding(.vertical, 8)
                            }
                            .scrollIndicators(.hidden)
                        }
                    }
                }
            }
    }
}
