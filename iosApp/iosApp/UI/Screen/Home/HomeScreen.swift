import SwiftUI
import shared

struct HomeScreen: View {
    @State var presenter = ActiveAccountPresenter()
    @State var showSettings = false
    @State var showLogin = false
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    var body: some View {
        Observing(presenter.models) { userState in
            let accountType: AccountType? = switch onEnum(of: userState.user) {
            case .success(let data): AccountTypeSpecific(accountKey: data.data.key)
            case .loading: nil
            case .error: AccountTypeGuest()
            }
            if let actualAccountType = accountType {
                FlareTheme {
                    AdativeTabView(
                        items: [
                            TabModel(
                                title: String(localized: "home_timeline_title"),
                                image: "house",
                                destination: TabItem { _ in
                                    HomeTimelineScreen(accountType: actualAccountType)
                                        .toolbar {
#if os(iOS)
                                            
                                            ToolbarItem(placement: accountType is AccountTypeGuest ? .primaryAction : .navigation) {
                                                Button {
                                                    if accountType is AccountTypeGuest {
                                                        showLogin = true
                                                    } else {
                                                        showSettings = true
                                                    }
                                                } label: {
                                                    switch onEnum(of: userState.user) {
                                                    case .success(let data):
                                                        UserAvatar(data: data.data.avatar, size: 36)
                                                    case .loading:
                                                        userAvatarPlaceholder(size: 36)
                                                    case .error:
                                                        Text("Login")
                                                    }
                                                }
                                            }
#endif
                                        }
                                }
                            ),
                            !(accountType is AccountTypeGuest) ? TabModel(
                                title: String(localized: "home_notification_title"),
                                image: "bell",
                                destination: TabItem { _ in
                                    NotificationScreen(accountType: actualAccountType)
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
                            ) : nil,
                            TabModel(
                                title: String(localized: "home_discover_title"),
                                image: "magnifyingglass",
                                destination: TabItem { _ in
                                    DiscoverScreen(
                                        accountType: actualAccountType,
                                        onUserClicked: { _ in
                                            //                                        router.navigate(to: .profileMedia(accountType: .active, userKey: user.key.description()))
                                        }
                                    )
                                }
                            ),
                            !(accountType is AccountTypeGuest) ? TabModel(
                                title: String(localized: "home_profile_title"),
                                image: "person.circle",
                                destination: TabItem { _ in
                                    ProfileScreen(
                                        accountType: actualAccountType,
                                        userKey: nil,
                                        toProfileMedia: { _ in
                                            //                                        router.navigate(to: .profileMedia(accountType: .active, userKey: userKey.description()))
                                        }
                                    )
                                }
                            ) : nil,
                            accountType is AccountTypeGuest ? TabModel(
                                title: String(localized: "home_settings_title"),
                                image: "gear",
                                destination: TabItem { _ in
                                    SettingsScreen()
                                }
                            ) : nil
                        ].compactMap { $0 },
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
        }
        .sheet(isPresented: $showLogin, content: {
            ServiceSelectScreen(toHome: {
                showLogin = false
            })
        })
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
