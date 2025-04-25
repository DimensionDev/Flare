import SwiftUI

struct HomeView: View {
    // 从环境中获取 AppState，用于读取 isMenuOpen (如果UI需要根据菜单状态调整) 和更新状态
    @EnvironmentObject private var appState: AppState
    // Home 内部状态，控制顶部 Tab
    @State private var selectedTopTab: Int = 0
    let topTabs = ["For You", "Following"]
 
    // 计算属性，判断当前是否为第一个顶部Tab
    private var isFirstTopTab: Bool {
        selectedTopTab == 0
    }

    var body: some View {
        VStack(spacing: 0) {
            AppBarView(
                selectedTopTab: $selectedTopTab,
                topTabs: topTabs,
                onAvatarTap: {
                    // 点击头像时，切换菜单状态
                    withAnimation(.interactiveSpring()) {
                        appState.isMenuOpen.toggle()
                    }
                }
            )

            Divider()

            TabContentView(
                selectedTopTab: $selectedTopTab,
                topTabs: topTabs
            )
        }
        .contentShape(Rectangle()) // 确保 VStack 区域可接收手势
       
        // --- 应用新的 Modifier ---
        .appBarMenuGestureHandler(isFirstTopTab: isFirstTopTab) // 使用计算属性传递条件
        .onAppear {
            // 当 HomeView 出现时，更新 AppState
            // appState.isHomeFirstTabActive = isFirstTopTab // 不再需要 
            appState.navigationDepth = 0 // 标记为根视图层级
            // 出现时重置可能的残留拖拽状态
            appState.menuDragOffset = 0
            print("HomeView appeared, isFirstTopTab: \(isFirstTopTab), depth: \(appState.navigationDepth)") // 直接用本地 isFirstTopTab
        }
     
    }
}

 
 