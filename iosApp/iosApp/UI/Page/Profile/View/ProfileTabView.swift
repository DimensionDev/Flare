import SwiftUI
import shared


// 主视图
struct ProfileTabView: View {
    let tabs: UiState<ImmutableListWrapper<ProfileStateTab>>
    let appSettings: AppSettings
    let listState: PagingState<UiTimeline>
    let refresh: () async throws -> Void
    let presenter: ProfilePresenter
    let accountType: AccountType
    let userKey: MicroBlogKey?
    @State private var selectedTab: Int = 0
    
    // 获取排序后的 tabs
    func sortedTabs(_ tabs: ImmutableListWrapper<ProfileStateTab>) -> [ProfileStateTab] {
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
            let sortedTabs = sortedTabs(tabs.data)
            VStack(spacing: 0) {
                // 顶部标签栏
                ProfileTabHeader(
                    tabs: sortedTabs,
                    selectedTab: $selectedTab,
                    onTabSelected: { index in
                        withAnimation {
                            selectedTab = index
                        }
                    }
                )
                
                Divider()
                
                // 内容区域
                ProfileTabContent(
                    tabs: sortedTabs,
                    selectedTab: $selectedTab,
                    refresh: refresh,
                    presenter: presenter,
                    accountType: accountType,
                    userKey: userKey
                )
            }
        } else {
            List {
                StatusTimelineComponent(
                    data: listState,
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
}

// 单个标签项组件
struct ProfileTabItem: View {
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

// 顶部标签栏组件
struct ProfileTabHeader: View {
    let tabs: [ProfileStateTab]
    @Binding var selectedTab: Int
    let onTabSelected: (Int) -> Void
    
    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 24) {
                    ForEach(Array(tabs.enumerated()), id: \.offset) { index, tab in
                        ProfileTabItem(
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
            .frame(maxWidth: UIScreen.main.bounds.width - 120)
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

// 内容区域组件
struct ProfileTabContent: View {
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
