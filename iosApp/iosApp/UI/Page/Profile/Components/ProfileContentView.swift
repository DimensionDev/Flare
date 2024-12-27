import SwiftUI
import shared

struct ProfileContentView: View {
    let tabs: [ProfileStateTab]
    @Binding var selectedTab: Int
    let refresh: () async throws -> Void
    let presenter: ProfilePresenter
    let accountType: AccountType
    let userKey: MicroBlogKey?
    
    @ViewBuilder
    func tabContent(for tab: ProfileStateTab) -> some View {
        switch onEnum(of: tab) {
        case .timeline(let timeline):
            TimelineTabContent(
                timeline: timeline,
                refresh: refresh
            )
        case .media(let media):
            ProfileMediaListScreen(
                accountType: accountType,
                userKey: userKey
            )
        }
    }
    
    var body: some View {
        GeometryReader { geometry in
            TabView(selection: $selectedTab) {
                ForEach(Array(tabs.enumerated()), id: \.offset) { index, tab in
                    tabContent(for: tab)
                        .tag(index)
                }
            }
            .frame(height: geometry.size.height)
            .tabViewStyle(.page(indexDisplayMode: .never))
        }
    }
}

// 将 Timeline 内容提取到单独的视图中
private struct TimelineTabContent: View, Equatable {
    let timeline: ProfileStateTabTimeline
    let refresh: () async throws -> Void
    
    static func == (lhs: TimelineTabContent, rhs: TimelineTabContent) -> Bool {
        return lhs.timeline.data == rhs.timeline.data
    }
    
    var body: some View {
        List {
            StatusTimelineComponent(
                data: timeline.data,
                detailKey: nil
            )
            .listRowBackground(Colors.Background.swiftUIPrimary)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .refreshable {
            try? await refresh()
        }
    }
} 