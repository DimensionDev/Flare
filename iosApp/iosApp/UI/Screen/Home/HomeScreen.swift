import SwiftUI

struct HomeScreen: View {
    var body: some View {
        TabView {
            NavigationStack {
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
            NavigationStack {
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
            NavigationStack {
                ProfileScreen(userKey: nil)
            }
            .tabItem {
                Image(systemName: "person.circle")
                Text("Me")
            }
        }
    }
}

#Preview {
    HomeScreen()
}
