import SwiftUI
import shared
import Awesome

struct HomeScreen: View {
    @State private var presenter = ActiveAccountPresenter()
    @State var showSettings = false
    @State var showLogin = false
    @State var showCompose = false
    
    var body: some View {
        ObservePresenter(presenter: presenter) { userState in
            let accountType: AccountType? = switch onEnum(of: userState.user) {
            case .success(let data): AccountTypeSpecific(accountKey: data.data.key)
            case .loading:
#if os(macOS)
                AccountTypeGuest()
#else
                nil
#endif
            case .error: AccountTypeGuest()
            }
            if let actualAccountType = accountType {
                FlareTheme {
                    TabView {
                        Tab {
                            TabItem { router in
                                HomeTimelineScreen(
                                    accountType: actualAccountType
                                )
                                .toolbar {
                                    ToolbarItem(placement: actualAccountType is AccountTypeGuest ? .primaryAction : .navigation) {
                                        Button {
                                            if actualAccountType is AccountTypeGuest {
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
                                    if !(actualAccountType is AccountTypeGuest) {
                                        ToolbarItem(placement: .primaryAction) {
                                            Button {
                                                router.navigate(to: AppleRoute.ComposeNew(accountType: actualAccountType))
                                            } label: {
                                                Awesome.Classic.Regular.penToSquare.image
                                                    .foregroundColor(.init(.accentColor))
                                            }
                                        }
                                    }
                                }
                            }
                        } label: {
                            Label {
                                Text("home_timeline_title")
                            } icon: {
                                Awesome.Classic.Solid.house.image
                            }
                        }
                        if !(actualAccountType is AccountTypeGuest) {
                            Tab {
                                TabItem { _ in
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
                            } label: {
                                Label {
                                    Text("home_notification_title")
                                } icon: {
                                    Awesome.Classic.Solid.bell.image
                                }
                            }
                        }
                        Tab(role: .search) {
                            TabItem { router in
                                DiscoverScreen(
                                    accountType: actualAccountType,
                                    onUserClicked: { user in
                                        router.navigate(to: AppleRoute.Profile(accountType: actualAccountType, userKey: user.key))
                                    }
                                )
                            }
                        } label: {
                            Label {
                                Text("home_discover_title")
                            } icon: {
                                Awesome.Classic.Solid.magnifyingGlass.image
                            }
                        }
                        
                        if !(actualAccountType is AccountTypeGuest) {
                            Tab {
                                TabItem { router in
                                    ProfileScreen(
                                        accountType: actualAccountType,
                                        userKey: nil,
                                        toProfileMedia: { key in
                                            router.navigate(to: AppleRoute.Profile(accountType: actualAccountType, userKey: key))
                                        }
                                    )
                                }
                            } label: {
                                Label {
                                    Text("home_profile_title")
                                } icon: {
                                    Awesome.Classic.Solid.circleUser.image
                                }
                            }
                        }
                    }
                    .tabViewStyle(.sidebarAdaptable)
                }
            }
        }
        .sheet(isPresented: $showLogin, content: {
            ServiceSelectScreen(toHome: {
                showLogin = false
            })
#if os(macOS)
            .frame(minWidth: 600, minHeight: 400)
#endif
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
