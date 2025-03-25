import SwiftUI

/// 统一的导航手势修饰符

struct FlareNavigationGestureModifier: ViewModifier {
    @ObservedObject var router: FlareRouter

    @Environment(\.dismiss) private var dismiss

    @GestureState private var dragOffset: CGFloat = 0

    private let screenWidthThreshold: CGFloat = UIScreen.main.bounds.width * 0.25

    func body(content: Content) -> some View {
        content
            .gesture(
                DragGesture()
                    .updating($dragOffset) { value, state, _ in
                        // 只处理从左向右的手势，并根据导航深度决定行为
                        if value.translation.width > 0, value.startLocation.x < 50 {
                            state = value.translation.width
                        }
                    }
                    .onEnded { value in
                        // 手势处理逻辑
                        if value.translation.width > screenWidthThreshold {
                            handleGestureEnd(value)
                        }
                    }
            )
            // 动态偏移量，创建更自然的滑动效果
            .offset(x: dragOffset * offsetMultiplier)
            .animation(.interactiveSpring(), value: dragOffset)
    }

    /// 根据导航深度决定偏移乘数
    private var offsetMultiplier: CGFloat {
        router.navigationDepth > 0 ? 0.5 : 0.3
    }

    /// 处理手势结束
    private func handleGestureEnd(_ value: DragGesture.Value) {
        if router.navigationDepth > 0 {
            // 在二级或更深页面，触发返回
            dismiss()
        } else if value.startLocation.x < 50, !router.appState.isMenuOpen {
            // 在首级页面且从边缘滑动，打开菜单
            withAnimation(.spring()) {
                router.appState.isMenuOpen = true
            }
        }
    }
}

extension View {
    func flareNavigationGesture(router: FlareRouter) -> some View {
        modifier(FlareNavigationGestureModifier(router: router))
    }
}
