import SwiftUI

struct HomeScreen: View {
    var body: some View {
        TabView {
            TimelineScreen()
                .tabItem {
                    Image(systemName: "house")
                    Text("Home")
                }
            ContentView()
                .tabItem {
                    Image(systemName: "bell")
                    Text("Notification")
                }
            ContentView()
                .tabItem {
                    Image(systemName: "person.circle")
                    Text("Me")
                }
        }.navigationTitle("Flare")
            .navigationBarBackButtonHidden(true)
    }
}

#Preview {
    HomeScreen()
}
