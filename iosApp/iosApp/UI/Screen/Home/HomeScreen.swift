import SwiftUI
import shared

struct HomeScreen: View {
    @State var viewModel = HomeViewModel()
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
                            Button(action: {}) {
                                Image(systemName: "square.and.pencil")
                            }
                        }
                        ToolbarItem(placement: .navigation) {
                            if case .success(let data) = onEnum(of: viewModel.model.user) {
                                UserAvatar(data: data.data.avatarUrl, size: 36)
                            } else {
                                UserAvatarPlaceholder(size: 36)
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
        }.activateViewModel(viewModel: viewModel)
    }
}

@Observable
class HomeViewModel : MoleculeViewModelBase<HomeState, HomePresenter> {
    
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
                case .search(_):
                    router.navigate(to: .settings)
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
