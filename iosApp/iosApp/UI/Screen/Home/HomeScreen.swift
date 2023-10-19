import SwiftUI
import shared

struct HomeScreen: View {
    @State var viewModel = HomeViewModel()
    @State var showSettings = false
    @State var showCompose = false
    var body: some View {
        TabView {
            TabItem {
                HomeTimelineScreen()
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .principal) {
                            Text("Flare")
                        }
                        ToolbarItem(placement: .primaryAction) {
                            Button(action: {
                                showCompose = true
                            }) {
                                Image(systemName: "square.and.pencil")
                            }
                        }
                        ToolbarItem(placement: .navigation) {
                            Button {
                                showSettings = true
                            } label: {
                                if case .success(let data) = onEnum(of: viewModel.model.user) {
                                    UserAvatar(data: data.data.avatarUrl, size: 36)
                                } else {
                                    UserAvatarPlaceholder(size: 36)
                                }
                            }
                        }
                    }
            }
            .tabItem {
                Image(systemName: "house")
                Text("Home")
            }
            TabItem {
                NotificationScreen()
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .principal) {
                            Text("Notification")
                        }
                    }
            }
            .tabItem {
                Image(systemName: "bell")
                Text("Notification")
            }
            TabItem {
                ProfileScreen(userKey: nil)
            }
            .tabItem {
                Image(systemName: "person.circle")
                Text("Me")
            }
        }
        .sheet(isPresented: $showCompose, content: {
            NavigationStack {
                ComposeScreen()
            }
        })
        .sheet(isPresented: $showSettings, content: {
            HomeSheetContent()
        })
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class HomeViewModel : MoleculeViewModelBase<HomeState, HomePresenter> {
    
}

struct HomeSheetContent: View {
    @Bindable var sheetRouter = Router<SheetDestination>()
    var body: some View {
        NavigationStack(path: $sheetRouter.navPath) {
            SettingsScreen()
                .withSheetRouter {
                    sheetRouter.navigateBack(count: 3)
                } toMisskey: {
                    sheetRouter.navigate(to: .misskey)
                } toMastodon: {
                    sheetRouter.navigate(to: .mastodon)
                }
        }
        .onOpenURL { url in
            if (url.absoluteString.starts(with: AppDeepLink.Callback.shared.Mastodon)) {
                if let range = url.absoluteString.range(of: "code=") {
                    let code = url.absoluteString.suffix(from: range.upperBound)
                    sheetRouter.navigate(to: .mastodonCallback(code: String(code)))
                }
            } else if (url.absoluteString.starts(with: AppDeepLink.Callback.shared.Misskey)) {
                if let range = url.absoluteString.range(of: "session=") {
                    let session = url.absoluteString.suffix(from: range.upperBound)
                    sheetRouter.navigate(to: .misskeyCallback(session: String(session)))
                }
            }
        }
    }
}

#Preview {
    HomeSheetContent()
}

struct TabItem<Content: View>: View {
    @Bindable var router = Router<TabDestination>()
    let content: () -> Content
    
    var body: some View {
        NavigationStack(path: $router.navPath) {
            content()
                .withTabRouter()
        }.environment(\.openURL, OpenURLAction { url in
            if let event = AppDeepLink.shared.parse(url: url.absoluteString) {
                switch onEnum(of: event) {
                case .profile(let data):
                    router.navigate(to: .profile(userKey: data.userKey.description()))
                case .profileWithNameAndHost(let data):
                    router.navigate(to: .profileWithUserNameAndHost(userName: data.userName, host: data.host))
                case .search(let data):
                    router.navigate(to: .search(q: data.keyword))
                }
                return .handled
            } else {
                return .discarded
            }
        })
        
    }
}

#Preview {
    HomeScreen()
}
