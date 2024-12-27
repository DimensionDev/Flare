import SwiftUI
import shared

struct ProfileTabBarView: View {
    let tabs: UiState<ImmutableListWrapper<ProfileStateTab>>
    @Binding var selectedTab: Int
    let onTabSelected: (Int) -> Void
    
    // 获取排序后的 tabs
    static func sortedTabs(_ tabs: ImmutableListWrapper<ProfileStateTab>) -> [ProfileStateTab] {
        var result: [ProfileStateTab] = []
        // 先添加 timeline tabs
        for i in 0..<tabs.size {
            let tab = tabs.get(index: i)
            if case .timeline = onEnum(of: tab) {
                result.append(tab)
            }
        }
        // 再添加 media tab
        for i in 0..<tabs.size {
            let tab = tabs.get(index: i)
            if case .media = onEnum(of: tab) {
                result.append(tab)
            }
        }
        return result
    }
    
    var body: some View {
        if case .success(let tabs) = onEnum(of: tabs) {
            let sortedTabs = Self.sortedTabs(tabs.data)
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 24) {
                        ForEach(Array(sortedTabs.enumerated()), id: \.offset) { index, tab in
                            TabItemView(
                                tab: tab,
                                index: index,
                                selectedTab: selectedTab,
                                onTabSelected: { selectedIndex in
                                    onTabSelected(selectedIndex)
                                    // 滚动到选中的标签
                                    withAnimation {
                                        proxy.scrollTo(selectedIndex, anchor: .center)
                                    }
                                }
                            )
                            .id(index)
                        }
                    }
                    .padding(.horizontal)
                }
                .frame(height: 44)
                .background(Colors.Background.swiftUIPrimary)
                .onAppear {
                    // 如果有选中的标签，滚动到该标签
                    withAnimation {
                        proxy.scrollTo(selectedTab, anchor: .center)
                    }
                }
            }
        }
    }
}

// 单个标签项组件
private struct TabItemView: View {
    let tab: ProfileStateTab
    let index: Int
    let selectedTab: Int
    let onTabSelected: (Int) -> Void
    
    var body: some View {
        let title: String = switch onEnum(of: tab) {
        case .timeline(let timeline):
            switch timeline.type {
            case .status: String(localized: "profile_tab_timeline")
            case .statusWithReplies: String(localized: "profile_tab_timeline_with_reply")
            case .likes: String(localized: "profile_tab_likes")
            default: ""
            }
        case .media:
            String(localized: "profile_tab_media")
        }
        
        VStack(spacing: 4) {
            Text(title)
                .font(.system(size: 16))
                .foregroundColor(selectedTab == index ? .primary : .gray)
                .fontWeight(selectedTab == index ? .semibold : .regular)
            
            // 下划线
            Rectangle()
                .fill(selectedTab == index ? Color.accentColor : .clear)
                .frame(height: 2)
                .frame(width: 24)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            withAnimation {
                onTabSelected(index)
            }
        }
    }
} 