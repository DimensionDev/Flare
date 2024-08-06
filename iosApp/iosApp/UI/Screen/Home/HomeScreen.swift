import SwiftUI
import shared

struct HomeScreen: View {
    @State
    var presenter = ActiveAccountPresenter()
    @State var showSettings = false
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    var body: some View {
        Observing(presenter.models) { userState in
            FlareTheme {
                AdativeTabView(
                    items: [
                        TabModel(
                            title: String(localized: "home_timeline_title"),
                            image: "house",
                            destination: TabItem { _ in
                                HomeTimelineScreen(accountType: AccountTypeActive())
                                    .toolbar {
    #if os(iOS)
                                        ToolbarItem(placement: .navigation) {
                                            Button {
                                                showSettings = true
                                            } label: {
                                                if case .success(let data) = onEnum(of: userState.user) {
                                                    UserAvatar(data: data.data.avatar, size: 36)
                                                } else {
                                                    userAvatarPlaceholder(size: 36)
                                                }
                                            }
                                        }
    #endif
                                    }
                            }
                        ),
                        TabModel(
                            title: String(localized: "home_notification_title"),
                            image: "bell",
                            destination: TabItem { _ in
                                NotificationScreen(accountType: AccountTypeActive())
                                    .toolbar {
    #if os(iOS)
                                        ToolbarItem(placement: .navigation) {
                                            Button {
                                                showSettings = true
                                            } label: {
                                                if case .success(let data) = onEnum(of: userState.user) {
                                                    UserAvatar(data: data.data.avatar, size: 36)
                                                } else {
                                                    userAvatarPlaceholder(size: 36)
                                                }
                                            }
                                        }
    #endif
                                    }
                            }
                        ),
                        TabModel(
                            title: String(localized: "home_discover_title"),
                            image: "magnifyingglass",
                            destination: TabItem { _ in
                                DiscoverScreen(
                                    accountType: AccountTypeActive(),
                                    onUserClicked: { _ in
//                                        router.navigate(to: .profileMedia(accountType: .active, userKey: user.key.description()))
                                    }
                                )
                            }
                        ),
                        TabModel(
                            title: String(localized: "home_profile_title"),
                            image: "person.circle",
                            destination: TabItem { _ in
                                ProfileScreen(
                                    accountType: AccountTypeActive(),
                                    userKey: nil,
                                    toProfileMedia: { _ in
//                                        router.navigate(to: .profileMedia(accountType: .active, userKey: userKey.description()))
                                    }
                                )
                            }
                        )
                    ],
                    secondaryItems: [
                    ],
                    leading: VStack {
                        Button {
                            showSettings = true
                        } label: {
                            AccountItem(userState: userState.user)
                            Spacer()
                            Image(systemName: "gear")
                                .opacity(0.5)
                        }
    #if os(iOS)
                        .padding([.horizontal, .top])
    #endif
                        .buttonStyle(.plain)
                    }
                        .listRowInsets(EdgeInsets())
                )
            }
        }
        .sheet(isPresented: $showSettings, content: {
            SettingsScreen()
#if os(macOS)
                .frame(minWidth: 600, minHeight: 400)
#endif
        })
    }
}

struct MediaClickData {
    let statusKey: MicroBlogKey
    let index: Int
    let preview: String?
}
