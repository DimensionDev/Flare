import SwiftUI

struct StandardMenuGestureHandler: ViewModifier {
    @EnvironmentObject private var appState: AppState
    let menuWidth: CGFloat // 从调用者传入或使用默认值

    func body(content: Content) -> some View {
        content
            .contentShape(Rectangle()) // 确保应用手势的视图区域可交互
            // --- 从 SearchView/NotificationsView/MessagesView 复制过来的手势逻辑 ---
            .simultaneousGesture(
                DragGesture(minimumDistance: 3)
                    .onChanged { value in
                        // 只在根视图处理 (不需要 isFirstTopTab 判断)
                        guard appState.navigationDepth == 0 else { return }
                        let horizontalTranslation = value.translation.width
                        if appState.isMenuOpen {
                            let dragOffset = min(max(horizontalTranslation, -menuWidth), 0)
                            appState.menuDragOffset = dragOffset
                        } else {
                            let dragOffset = min(max(horizontalTranslation, 0), menuWidth)
                            appState.menuDragOffset = dragOffset
                        }
                        // 可以保留或移除日志
                        // print("[StandardMenuGesture] onChanged - Offset: \(appState.menuDragOffset)")
                    }
                    .onEnded { value in
                         // 只在根视图处理
                        guard appState.navigationDepth == 0 else {
                            if appState.menuDragOffset != 0 { appState.menuDragOffset = 0 }
                            return
                        }
                        let horizontalTranslation = value.translation.width
                        let predictedEndTranslation = value.predictedEndTranslation.width
                        let thresholdRatio: CGFloat = 0.3
                        let currentDragOffset = appState.menuDragOffset

                        defer {
                            withAnimation(.spring(duration: 0.1)) { appState.menuDragOffset = 0 }
                            // print("[StandardMenuGesture] onEnded - Offset Reset")
                        }

                        if appState.isMenuOpen { // 处理关闭
                             // print("[StandardMenuGesture] onEnded - Trying to close.")
                            if currentDragOffset < -menuWidth * thresholdRatio || predictedEndTranslation < -200 {
                                withAnimation(.interactiveSpring()) { appState.isMenuOpen = false }
                            }
                        } else { // 处理打开 (只检查拖拽阈值)
                             // print("[StandardMenuGesture] onEnded - Trying to open.")
                            if currentDragOffset > menuWidth * thresholdRatio {
                                withAnimation(.interactiveSpring()) { appState.isMenuOpen = true }
                            }
                        }
                    }
            )
    }
}

// 便于使用的扩展方法
extension View {
    func standardMenuGestureHandler(menuWidth: CGFloat = 250) -> some View {
        self.modifier(StandardMenuGestureHandler(menuWidth: menuWidth))
    }
} 