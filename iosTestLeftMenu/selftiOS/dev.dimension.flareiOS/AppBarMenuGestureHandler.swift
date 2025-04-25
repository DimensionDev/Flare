import SwiftUI

struct AppBarMenuGestureHandler: ViewModifier {
    @EnvironmentObject private var appState: AppState
    let menuWidth: CGFloat
    let isFirstTopTab: Bool // 由调用者传入

    func body(content: Content) -> some View {
        content
            .contentShape(Rectangle()) // 确保应用手势的视图区域可交互
            // --- 从 HomeView/ProfileView 复制过来的手势逻辑 ---
            .simultaneousGesture(
                DragGesture(minimumDistance: 3)
                    .onChanged { value in
                        // 只在第一个顶部 Tab 时处理拖拽
                        guard isFirstTopTab else {
                            if appState.menuDragOffset != 0 { appState.menuDragOffset = 0 }
                            return
                        }
                        // 只在根视图处理
                        guard appState.navigationDepth == 0 else { return }

                        let horizontalTranslation = value.translation.width

                        if appState.isMenuOpen {
                            let dragOffset = min(max(horizontalTranslation, -menuWidth), 0)
                            appState.menuDragOffset = dragOffset
                        } else {
                            let dragOffset = min(max(horizontalTranslation, 0), menuWidth)
                            appState.menuDragOffset = dragOffset
                        }
                         // print("[AppBarMenuGesture] onChanged - Offset: \(appState.menuDragOffset)")
                    }
                    .onEnded { value in
                        // 只在第一个顶部 Tab 时处理手势结束
                        guard isFirstTopTab else {
                            if appState.menuDragOffset != 0 {
                                withAnimation(.spring(duration: 0.1)) { appState.menuDragOffset = 0 }
                            }
                            return
                        }
                         // 只在根视图处理
                        guard appState.navigationDepth == 0 else {
                            if appState.menuDragOffset != 0 { appState.menuDragOffset = 0 }
                            return
                        }

                        let horizontalTranslation = value.translation.width
                        let predictedEndTranslation = value.predictedEndTranslation.width
                        let thresholdRatio: CGFloat = 0.3
                        let quickSwipeVelocity: CGFloat = 100
                        let currentDragOffset = appState.menuDragOffset

                        defer {
                            withAnimation(.spring(duration: 0.1)) { appState.menuDragOffset = 0 }
                            // print("[AppBarMenuGesture] onEnded - Offset Reset")
                        }

                        if appState.isMenuOpen { // 处理关闭
                            // print("[AppBarMenuGesture] onEnded - Trying to close.")
                            if currentDragOffset < -menuWidth * thresholdRatio || predictedEndTranslation < -quickSwipeVelocity * 2 {
                                withAnimation(.interactiveSpring()) { appState.isMenuOpen = false }
                            }
                        } else { // 处理打开
                             // print("[AppBarMenuGesture] onEnded - Trying to open.")
                            // 包含快速滑动判断
                            if isFirstTopTab && predictedEndTranslation > quickSwipeVelocity { // isFirstTopTab 在此肯定为 true
                                withAnimation(.interactiveSpring()) { appState.isMenuOpen = true }
                            } else if currentDragOffset > menuWidth * thresholdRatio {
                                withAnimation(.interactiveSpring()) { appState.isMenuOpen = true }
                            }
                        }
                    }
            )
    }
}

// 便于使用的扩展方法
extension View {
    func appBarMenuGestureHandler(
        isFirstTopTab: Bool,
        menuWidth: CGFloat = 250
    ) -> some View {
        self.modifier(AppBarMenuGestureHandler(
            menuWidth: menuWidth,
            isFirstTopTab: isFirstTopTab
        ))
    }
} 