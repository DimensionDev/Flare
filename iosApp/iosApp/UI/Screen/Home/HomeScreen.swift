import SwiftUI
import shared

struct HomeScreen: View {
    @State var presenter = ActiveAccountPresenter()
    @State var showSettings = false
    @State var showLogin = false
    @State var showCompose = false
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
                                destination: TabItem { router in
                                    HomeTimelineScreen(
                                        accountType: actualAccountType,
                                        toCompose: {
                                            router.navigate(to: AppleRoute.ComposeNew(accountType: actualAccountType))
                                        }
                                    )
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
                                destination: TabItem { router in
                                    DiscoverScreen(
                                        accountType: actualAccountType,
                                        onUserClicked: { user in
                                            router.navigate(to: AppleRoute.Profile(accountType: actualAccountType, userKey: user.key))
                                        }
                                    )
                                }
                            ),
                            !(accountType is AccountTypeGuest) ? TabModel(
                                title: String(localized: "home_profile_title"),
                                image: "person.circle",
                                destination: TabItem { router in
                                    ProfileScreen(
                                        accountType: actualAccountType,
                                        userKey: nil,
                                        toProfileMedia: { key in
                                            router.navigate(to: AppleRoute.Profile(accountType: actualAccountType, userKey: key))
                                        }
                                    )
                                }
                            ) : nil,
                            accountType is AccountTypeGuest ? TabModel(
                                title: String(localized: "settings_title"),
                                image: "gear",
                                destination: TabItem { _ in
                                    SettingsScreen()
                                }
                            ) : nil
                        ].compactMap { $0 },
                        secondaryItems: [
                        ],
                        leading: !(accountType is AccountTypeGuest) ? VStack {
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
                            Button {
                                showCompose = true
                            } label: {
                                Label("compose_title", systemImage: "pencil")
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .frame(maxWidth: .infinity)
                            .buttonStyle(.borderedProminent)
                            .sheet(isPresented: $showCompose, content: {
                                NavigationStack {
                                    ComposeScreen(onBack: {showCompose = false}, accountType: actualAccountType, status: nil)
                                }
                            })
                        }
                            .listRowInsets(EdgeInsets()) : nil
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
