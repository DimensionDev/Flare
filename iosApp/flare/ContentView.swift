import SwiftUI
import KotlinSharedUI

struct ContentView: View {
    
    
    var body: some View {
        TabView {
            Router {
                HomeTimelineScreen(accountType: AccountType.Guest())
            }
            .tabItem {
                Label {
                    Text("Home")
                } icon: {
                    Image(systemName: "house.fill")
                }
            }
            .toolbar(.hidden, for: .tabBar)
            List {
                ForEach(0..<1000) { index in
                    Text("index")
                        .padding()
                }
            }
            .tabItem {
                Label {
                    Text("Notification")
                } icon: {
                    Image(systemName: "bell")
                }
            }
            .toolbar(.hidden, for: .tabBar)
            Text("Discover")
                .tabItem {
                    Label {
                        Text("Discover")
                    } icon: {
                        Image(systemName: "magnifyingglass")
                    }
                }
                .toolbar(.hidden, for: .tabBar)
            Text("Search")
                .tabItem {
                    Label {
                        Text("Discover")
                    } icon: {
                        Image(systemName: "magnifyingglass")
                    }
                }
                .toolbar(.hidden, for: .tabBar)
            
        }
    }
}
