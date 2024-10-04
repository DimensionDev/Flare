import SwiftUI
import shared
import Awesome

struct HomeContent: View {
    // horizontal window size
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @AppStorage("homeSidebarCustomizations") var tabViewCustomization: TabViewCustomization
    @State private var selectedTab: HomeTabs = .timeline
    let accountType: AccountType
    @State var showSettings = false
    @State var showLogin = false
    @State var showCompose = false
    @State var listPresenter: AllListPresenter
    init(accountType: AccountType) {
        self.accountType = accountType
        listPresenter = .init(accountType: accountType)
    }
    var body: some View {
        Observing(listPresenter.models) { listState in
            FlareTheme {
                TabView(selection: $selectedTab) {
                    Tab(value: .timeline) {
                        TabItem { router in
                            HomeTimelineScreen(
                                accountType: accountType
                            )
                            .toolbar {
                                if !(accountType is AccountTypeGuest) {
                                    ToolbarItem(placement: .primaryAction) {
                                        Button {
                                            router.navigate(to: AppleRoute.ComposeNew(accountType: accountType))
                                        } label: {
                                            Awesome.Classic.Regular.penToSquare.image
                                                .foregroundColor(.init(.accentColor))
                                        }
                                    }
                                    ToolbarItem(placement: .navigation) {
                                        Button {
                                            showSettings = true
                                        } label: {
                                            Awesome.Classic.Solid.gear.image
                                                .foregroundColor(.init(.accentColor))
                                        }
                                    }
                                } else {
                                    ToolbarItem(placement: .primaryAction) {
                                        Button {
                                            showLogin = true
                                        } label: {
                                            Text("Login")
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
                    .customizationID(HomeTabs.timeline.customizationID)
                    if !(accountType is AccountTypeGuest) {
                        Tab(value: .notification) {
                            TabItem { _ in
                                NotificationScreen(accountType: accountType)
                            }
                        } label: {
                            Label {
                                Text("home_notification_title")
                            } icon: {
                                Awesome.Classic.Solid.bell.image
                            }
                        }
                        .customizationID(HomeTabs.notification.customizationID)
                    }
                    Tab(value: .discover, role: .search) {
                        TabItem { router in
                            DiscoverScreen(
                                accountType: accountType,
                                onUserClicked: { user in
                                    router.navigate(to: AppleRoute.Profile(accountType: accountType, userKey: user.key))
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
                    .customizationID(HomeTabs.discover.customizationID)
                    if !(accountType is AccountTypeGuest) {
                        Tab(value: .profile) {
                            TabItem { router in
                                ProfileScreen(
                                    accountType: accountType,
                                    userKey: nil,
                                    toProfileMedia: { key in
                                        router.navigate(to: AppleRoute.Profile(accountType: accountType, userKey: key))
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
                        .customizationID(HomeTabs.profile.customizationID)
                    }
                    
                    // if horizontalSizeClass > .compact
                    //                    if horizontalSizeClass == .regular {
                    
                    //                        if case .success(let data) = onEnum(of: listState.items) {
                    //                            ForEach(0..<data.itemCount, id: \.self) { index in
                    //                                Tab {
                    //                                    Text("")
                    //                                } label: {
                    //                                    Text("")
                    //                                }
                    //                            }
                    //                        }
                    //                    }
                    
                }
                .tabViewStyle(.sidebarAdaptable)
                .tabViewCustomization($tabViewCustomization)
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
}

enum HomeTabs: Equatable, Hashable, Identifiable {
    var id: Self { self }
    case timeline
    case notification
    case discover
    case profile
    case list(String)
}

extension HomeTabs {
    var customizationID: String {
        switch self {
        case .timeline: return "home_timeline"
        case .notification: return "home_notification"
        case .discover: return "home_discover"
        case .profile: return "home_profile"
        case .list(let id): return "home_list_\(id)"
        }
    }
}
