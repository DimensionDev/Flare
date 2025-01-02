import SwiftUI
import shared
import os.log

struct ProfileTabBarView: View {
    let tabs: [ProfileStateTab]
    @Binding var selectedTab: Int
    let onTabSelected: (Int) -> Void
 
    // 获取排序后的 tabs
    static func sortedTabs(_ tabs: [ProfileStateTab]) -> [ProfileStateTab] {
        os_log("[📔][ProfileTabBarView]开始排序 tabs: count=%{public}d", log: .default, type: .debug, tabs.count)
        var result: [ProfileStateTab] = []
        // 先添加 timeline tabs
        for tab in tabs {
            if case .timeline = onEnum(of: tab) {
                result.append(tab)
            }
        }
        // 再添加 media tab
        for tab in tabs {
            if case .media = onEnum(of: tab) {
                result.append(tab)
            }
        }
        os_log("[📔][ProfileTabBarView]排序完成: resultCount=%{public}d", log: .default, type: .debug, result.count)
        return result
    }
    
    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 24) {
                    ForEach(Array(tabs.enumerated()), id: \.offset) { index, tab in
                        TabItemView(
                            tab: tab,
                            index: index,
                            selectedTab: selectedTab,
                            onTabSelected: { selectedIndex in
                                os_log("[📔][ProfileTabBarView]点击标签: index=%{public}d", log: .default, type: .debug, selectedIndex)
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
                os_log("[📔][ProfileTabBarView]视图出现: selectedTab=%{public}d, tabsCount=%{public}d", log: .default, type: .debug, selectedTab, tabs.count)
                // 如果有选中的标签，滚动到该标签
                withAnimation {
                    proxy.scrollTo(selectedTab, anchor: .center)
                }
            }
            .onDisappear {
                os_log("[📔][ProfileTabBarView]视图消失: selectedTab=%{public}d", log: .default, type: .debug, selectedTab)
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
        .onAppear {
            os_log("[📔][ProfileTabBarView][TabItem]标签项出现: index=%{public}d, title=%{public}@", log: .default, type: .debug, index, title)
        }
    }
} 
