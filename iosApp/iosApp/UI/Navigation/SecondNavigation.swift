import shared
import SwiftUI

/// 二级导航页面修饰符
/// 用于处理二级页面的导航手势和菜单手势冲突
struct SecondNavigation: ViewModifier {
    @EnvironmentObject private var menuState: FLNewAppState
    @Environment(\.dismiss) private var dismiss
    @GestureState private var dragOffset: CGFloat = 0

    func body(content: Content) -> some View {
        content
            // 设置导航层级
            .environment(\.navigationLevel, 1)
            // 处理页面生命周期
            .onAppear {
                // 进入详情页时禁用左滑菜单手势
                menuState.enterNavigationDetail()
            }
            .onDisappear {
                // 离开详情页时恢复左滑菜单手势
                menuState.leaveNavigationDetail()
            }
            // 添加自定义的返回手势
            .gesture(
                DragGesture()
                    .updating($dragOffset) { value, state, _ in
                        // 只处理向右的拖动
                        if value.translation.width > 0 {
                            state = value.translation.width
                        }
                    }
                    .onEnded { value in
                        // 如果拖动足够远，触发返回操作
                        if value.translation.width > UIScreen.main.bounds.width * 0.25 {
                            dismiss()
                        }
                    }
            )
            // 使用偏移量制造滑动效果
            .offset(x: max(0, dragOffset))
            .animation(.interactiveSpring(), value: dragOffset)
    }
}

// MARK: - 视图扩展

extension View {
    /// 将视图标记为二级导航页面
    /// 自动处理手势冲突和返回手势
    func secondNavigation() -> some View {
        modifier(SecondNavigation())
    }
}
