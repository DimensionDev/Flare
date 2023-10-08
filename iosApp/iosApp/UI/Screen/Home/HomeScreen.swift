import SwiftUI
import shared

struct HomeScreen: View {
    var body: some View {
        TabView {
            TabItem {
                HomeTimelineScreen()
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .principal) {
                            Text("Flare")
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
    }
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
