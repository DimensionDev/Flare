import SwiftUI

// 基本复制自 HomeView，但代表 Profile 页面
struct ProfileView: View {
    @EnvironmentObject private var appState: AppState
    @State private var selectedTopTab: Int = 0
    // 修改为 Profile 页面的子 Tab
    let topTabs = ["Tweets", "Replies", "Media", "Likes"]
    // private let menuWidth: CGFloat = 250 // 不再需要局部常量

    // 这个计算属性在 ProfileView 内部仍然有效
    private var isFirstTopTab: Bool { selectedTopTab == 0 }

    var body: some View {
        VStack(spacing: 0) {
            // AppBarView 可能需要根据 Profile 页定制，但暂时复用
            AppBarView(
                selectedTopTab: $selectedTopTab,
                topTabs: topTabs,
                onAvatarTap: {
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
        .contentShape(Rectangle())
        
        .appBarMenuGestureHandler(isFirstTopTab: isFirstTopTab)  
        .onAppear {
        
            // appState.isHomeFirstTabActive = false // 不再需要
            appState.navigationDepth = 0
            appState.menuDragOffset = 0
            print("ProfileView appeared, depth: \(appState.navigationDepth)")
        }
     }
}

// Preview
#Preview {
    NavigationStack { // 可能需要，如果内部用了 NavLink
        ProfileView()
    }
    .environmentObject(AppState())
} 