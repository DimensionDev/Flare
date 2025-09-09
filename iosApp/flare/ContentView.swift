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
            Text("Discover")
                .tabItem {
                    Label {
                        Text("Discover")
                    } icon: {
                        Image(systemName: "magnifyingglass")
                    }
                }
            Text("Search")
                .tabItem {
                    Label {
                        Text("Discover")
                    } icon: {
                        Image(systemName: "magnifyingglass")
                    }
                }
            
        }
//        TabView {
//            Tab {
//                Router {
//                    HomeTimelineScreen(accountType: AccountType.Guest())
//                }
//            } label: {
//                Label {
//                    Text("Home")
//                } icon: {
//                    Image(systemName: "house.fill")
//                }
//            }
//            Tab {
//                List {
//                    ForEach(0..<1000) { index in
//                        Text("index")
//                            .padding()
//                    }
//                }
//            } label: {
//                Label {
//                    Text("Notification")
//                } icon: {
//                    Image(systemName: "bell")
//                }
//
//            }
//            Tab {
//                Text("Discover")
//            } label: {
//                Label {
//                    Text("Discover")
//                } icon: {
//                    Image(systemName: "magnifyingglass")
//                }
//
//            }
//            Tab(role: .search) {
//                Text("sarch")
//            } label: {
//                Label {
//                    Text("Search")
//                } icon: {
//                    Image(systemName: "plus")
//                }
//
//            }
//        }
    }
}
