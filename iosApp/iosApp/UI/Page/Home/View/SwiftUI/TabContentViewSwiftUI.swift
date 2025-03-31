import shared
import SwiftUI

struct TabContentViewSwiftUI: View {
    @ObservedObject var tabStore: AppBarTabSettingStore
    @Binding var selectedTab: String
    @State private var scrollPositions: [String: String] = [:]
    @EnvironmentObject private var appState: FlareAppState
    @EnvironmentObject private var router: FlareRouter

    // 是否在第一个标签页
    private var isFirstTab: Bool {
        selectedTab == tabStore.availableAppBarTabsItems.first?.key
    }

    // 左侧滑动边缘检测手势
    private var edgeSwipeGesture: some Gesture {
        DragGesture(minimumDistance: 15)
            .onChanged { value in
                // 只有在第一个标签页且从左侧边缘滑动时才处理
                if isFirstTab, value.startLocation.x < 20, value.translation.width > 0 {
                    // 计算菜单打开进度
                    let maxDragForFullMenu: CGFloat = UIScreen.main.bounds.width * 0.6
                    let progress = min(value.translation.width / maxDragForFullMenu, 1.0)

                    // 更新菜单进度，使用较短的动画时间降低渲染负担
                    withAnimation(.linear(duration: 0.08)) {
                        appState.menuProgress = progress
                    }

                    // 如果滑动足够远，设置菜单为打开状态
                    if progress > 0.5, value.translation.width > UIScreen.main.bounds.width * 0.25 {
                        withAnimation(.spring(response: 0.25, dampingFraction: 0.7)) {
                            appState.isMenuOpen = true
                            appState.menuProgress = 1.0
                        }
                    }
                }
            }
            .onEnded { value in
                // 只有在第一个标签页处理
                if isFirstTab, value.startLocation.x < 20 {
                    let maxDragForFullMenu: CGFloat = UIScreen.main.bounds.width * 0.6
                    let progress = min(value.translation.width / maxDragForFullMenu, 1.0)
                    let velocity = value.predictedEndLocation.x - value.location.x

                    // 判断是否应该完成菜单打开
                    let shouldCompleteOpen = progress > 0.3 || velocity > 500

                    withAnimation(.spring(response: 0.25, dampingFraction: 0.7)) {
                        if shouldCompleteOpen {
                            appState.isMenuOpen = true
                            appState.menuProgress = 1.0
                        } else {
                            appState.isMenuOpen = false
                            appState.menuProgress = 0.0
                        }
                    }
                }
            }
    }

    var body: some View {
        ZStack {
            TabView(selection: $selectedTab) {
                ForEach(tabStore.availableAppBarTabsItems, id: \.key) { tab in
                    TimelineViewSwiftUI(
                        tab: tab,
                        store: tabStore,
                        scrollPositionID: Binding(
                            get: { scrollPositions[tab.key] },
                            set: { newValue in
                                if let newValue {
                                    scrollPositions[tab.key] = newValue
                                }
                            }
                        )
                    )
                    .tag(tab.key)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            // 只在第一个标签页时启用 左侧边缘20 透明覆盖层，用于捕获左侧边缘滑动
            if isFirstTab {
                HStack {
                    Rectangle()
                        .fill(Color.clear)
                        .frame(width: 20)
                        .contentShape(Rectangle())

                    Spacer()
                }
                .frame(maxHeight: .infinity)
                .gesture(edgeSwipeGesture)
                .allowsHitTesting(true)
            }
        }
    }
}
