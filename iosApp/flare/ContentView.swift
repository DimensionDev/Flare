import SwiftUI
import KotlinSharedUI
internal import Combine

struct ContentView: View {
//    @StateObject private var bridge = ScrollBridge()
    let tabItem = HomeTimelineTabItem(accountType: AccountTypeGuest())
//    @State var presenter = TimelineItemPresenter(timelineTabItem: HomeTimelineTabItem(accountType: AccountTypeGuest()))
    @State var isTopShowing = true
    var body: some View {
//        Observing(presenter.models) { state in
        TabView {
            Tab {
                ZStack(alignment: .top) {
                    TimelineItemView(data: tabItem, onExpanded: { withAnimation {
                        isTopShowing = true
                    } }, onCollapsed: { withAnimation {
                        isTopShowing = false
                    } }, topPadding: 36)
                                    .ignoresSafeArea()
                    if isTopShowing {
                        Text("test")
                            .frame(maxWidth: .infinity)
                            .transition(.move(edge: .top))
                            .background(Color(.systemGroupedBackground))
                    }
                }
                .background(Color(.systemGroupedBackground))
            } label: {
                Label {
                    Text("Home")
                } icon: {
                    Image(systemName: "house.fill")
                }

            }
            Tab {
                List {
                    ForEach(0..<1000) { index in
                        Text("index")
                            .padding()
                    }
                }
            } label: {
                Label {
                    Text("Notification")
                } icon: {
                    Image(systemName: "bell")
                }

            }
            Tab {
                Text("Discover")
            } label: {
                Label {
                    Text("Discover")
                } icon: {
                    Image(systemName: "magnifyingglass")
                }

            }
            Tab(role: .search) {
                Text("sarch")
            } label: {
                Label {
                    Text("Search")
                } icon: {
                    Image(systemName: "plus")
                }

            }
        }
    }
}

struct StatusItemView : UIViewControllerRepresentable {
    let data: UiTimeline?
    let detailStatusKey: MicroBlogKey?
    @Binding var measuredHeight: CGFloat
    func makeUIViewController(context: Context) -> some UIViewController {
        return KotlinSharedUI.StatusController(
            data: data,
            detailStatusKey: detailStatusKey,
            heightChanged: { height in
                let scale = UIScreen.main.scale
                measuredHeight = CGFloat(height)/scale
            }
        )
    }
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
    }
}

struct TimelineView : UIViewControllerRepresentable {
    @State var state: TimelineItemPresenterState
    func makeUIViewController(context: Context) -> some UIViewController {
        return KotlinSharedUI.TimelineController(state: state)
    }
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
        
    }
}

struct TimelineItemView : UIViewControllerRepresentable {
    let data: TimelineTabItem
    let onExpanded: () -> Void
    let onCollapsed: () -> Void
    let topPadding: Int
    func makeUIViewController(context: Context) -> some UIViewController {
        return KotlinSharedUI.TimelineTabItemController(data: data, topPadding: Int32(topPadding), onExpanded: onExpanded, onCollapsed: onCollapsed)
    }
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
        
    }
}
