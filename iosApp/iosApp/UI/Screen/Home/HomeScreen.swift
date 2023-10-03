import SwiftUI

struct HomeScreen: View {
    var body: some View {
        TabView {
            HomeTimelineScreen()
                .tabItem {
                    Image(systemName: "house")
                    Text("Home")
                }
            NotificationScreen()
                .tabItem {
                    Image(systemName: "bell")
                    Text("Notification")
                }
            ContentView()
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
