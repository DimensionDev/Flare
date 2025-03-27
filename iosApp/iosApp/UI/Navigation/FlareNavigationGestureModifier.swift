import os.log
import SwiftUI

/// 统一的导航手势修饰符

struct FlareNavigationGestureModifier: ViewModifier {
    @ObservedObject var router: FlareRouter

    @Environment(\.dismiss) private var dismiss

    @GestureState private var dragOffset: CGFloat = 0
    @State private var isTransitioning: Bool = false

    // 屏幕宽度阈值和速度阈值
    private let screenWidthThreshold: CGFloat = UIScreen.main.bounds.width * 0.25
    private let velocityThreshold: CGFloat = 800

    // 动画参数
    private let completionDuration: Double = 0.18
    private let cancelDuration: Double = 0.25

    func body(content: Content) -> some View {
        let _ = os_log("[FlareNavigationGestureModifier] Applied to view with router: %{public}@, depth: %{public}d",
                       log: .default, type: .debug,
                       String(describing: ObjectIdentifier(router)),
                       router.navigationDepth)

        // 创建ZStack包装内容，为前景和背景提供不同的变换效果
        ZStack {
            // 这是"下一层"视图的模拟（前一页面）
            if router.navigationDepth > 0, dragOffset > 0 || isTransitioning {
                // 如果有前一页面的截图，显示真实内容
                if let previousSnapshot = router.previousPageSnapshot {
                    Image(uiImage: previousSnapshot)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .scaleEffect(1.0 - 0.05) // 稍微缩小
                        .opacity(0.95) // 保持较高的不透明度
                        .offset(x: -UIScreen.main.bounds.width * 0.3 + (dragOffset * 0.3)) // 从左侧稍微偏移
                        .shadow(color: .black.opacity(0.2), radius: 5, x: 3, y: 0) // 添加侧边阴影增强层次感
                        .zIndex(0) // 确保在下层
                } else {
                    // 如果没有截图，使用模拟的页面效果
                    ZStack {
                        // 模拟前一页面的背景
                        Rectangle()
                            .fill(Color(UIColor.secondarySystemBackground))
                            .frame(maxWidth: .infinity, maxHeight: .infinity)

                        // 模拟前一页面的内容布局
                        VStack(alignment: .leading) {
                            // 模拟导航栏
                            Rectangle()
                                .fill(Color(UIColor.systemBackground))
                                .frame(height: 44)
                                .shadow(color: .black.opacity(0.1), radius: 1, x: 0, y: 1)

                            Spacer()
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .scaleEffect(1.0 - 0.05) // 稍微缩小
                    .opacity(0.9) // 保持较高的不透明度
                    .offset(x: -UIScreen.main.bounds.width * 0.3 + (dragOffset * 0.3)) // 从左侧稍微偏移
                    .shadow(color: .black.opacity(0.2), radius: 5, x: 3, y: 0) // 添加侧边阴影增强层次感
                    .zIndex(0) // 确保在下层
                }
            }

            // 实际内容（当前页面）
            content
                .offset(x: dragOffset * offsetMultiplier)
                // 添加阴影效果，增强深度感
                .shadow(color: .black.opacity(shadowOpacity), radius: 4, x: -2, y: 0)
                // 添加轻微的3D旋转效果
//                .rotation3DEffect(
//                    Angle(degrees: router.navigationDepth > 0 ? min(dragOffset * -0.015, 0) : 0),
//                    axis: (x: 0, y: 1, z: 0),
//                    anchor: .leading
//                )
                // 添加轻微的缩放效果
//                .scaleEffect(
//                    router.navigationDepth > 0
//                        ? 1.0 - min(dragOffset * 0.0003, 0.03)
//                        : 1.0,
//                    anchor: .leading
//                )
                // 添加轻微的不透明度变化
                .opacity(1.0 - (dragPercentage * 0.1))
                .zIndex(1) // 确保在上层
        }
        .gesture(
            DragGesture()
                .updating($dragOffset) { value, state, _ in
                    // 只处理从左向右的手势，并根据导航深度决定行为
                    if value.translation.width > 0, value.startLocation.x < 50 {
                        state = value.translation.width
                    }
                }
                .onChanged { value in
                    // 设置转换状态
                    if !isTransitioning, value.translation.width > 10 {
                        isTransitioning = true
                    }
                }
                .onEnded { value in
                    // 手势处理逻辑
                    let velocity = value.predictedEndLocation.x - value.location.x
                    let progress = value.translation.width / UIScreen.main.bounds.width

                    // 判断是否应该完成过渡（基于进度或速度）
                    let shouldComplete = progress > 0.5 || velocity > velocityThreshold

                    os_log("[FlareNavigationGestureModifier] Gesture ended: progress=%{public}f, velocity=%{public}f, shouldComplete=%{public}@",
                           log: .default, type: .debug,
                           progress, velocity, shouldComplete ? "true" : "false")

                    if value.translation.width > screenWidthThreshold || shouldComplete {
                        withAnimation(.easeOut(duration: completionDuration)) {
                            handleGestureEnd(value)
                        }
                    } else {
                        // 未达到完成条件，重置转换状态
                        withAnimation(.easeOut(duration: cancelDuration)) {
                            isTransitioning = false
                        }
                    }
                }
        )
        .animation(dragAnimation, value: dragOffset)
    }

    /// 拖动百分比，用于计算各种效果
    private var dragPercentage: CGFloat {
        let maxDrag = UIScreen.main.bounds.width / 2 // 使用屏幕宽度的一半作为最大拖动参考
        return min(dragOffset / maxDrag, 1.0)
    }

    /// 根据导航深度和转换状态决定动画曲线
    private var dragAnimation: Animation {
        if isTransitioning {
            .interpolatingSpring(
                mass: 1.0,
                stiffness: 100,
                damping: 13,
                initialVelocity: 5
            )
        } else {
            .interactiveSpring(
                response: 0.35,
                dampingFraction: 0.86,
                blendDuration: 0.25
            )
        }
    }

    /// 根据导航深度决定偏移乘数
    private var offsetMultiplier: CGFloat {
        router.navigationDepth > 0 ? 0.5 : 0.3
    }

    /// 阴影不透明度，根据拖动距离和导航深度动态计算
    private var shadowOpacity: Double {
        if router.navigationDepth > 0 {
            // 当有导航深度时，随着拖动增加阴影
            min(dragOffset / 400, 0.35)
        } else {
            // 在首页时不显示阴影
            0
        }
    }

    /// 处理手势结束
    private func handleGestureEnd(_ value: DragGesture.Value) {
        os_log("[FlareNavigationGestureModifier] Handling gesture end, router: %{public}@, depth: %{public}d",
               log: .default, type: .debug,
               String(describing: ObjectIdentifier(router)),
               router.navigationDepth)

        if router.navigationDepth > 0 {
            // 在二级或更深页面，触发返回
            // 使用router.goBack(fromGesture: true)方法，指明这是由手势触发的
            os_log("[FlareNavigationGestureModifier] Triggering goBack for depth > 0", log: .default, type: .debug)
            router.goBack()

            // 添加一个延迟，确保导航动画完成后再发送通知
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                os_log("[FlareNavigationGestureModifier] 手势导航完成，发送通知", log: .default, type: .debug)
                // NotificationCenter.default.post(
                //     name: NSNotification.Name("NavigationDidComplete"),
                //     object: nil
                // )

                // 重置转换状态
                isTransitioning = false
            }
        } else if value.startLocation.x < 50, !router.appState.isMenuOpen {
            // 在首级页面且从边缘滑动，打开菜单
            os_log("[FlareNavigationGestureModifier] Opening menu for depth = 0", log: .default, type: .debug)
            withAnimation(.spring()) {
                router.appState.isMenuOpen = true

                // 重置转换状态
                isTransitioning = false
            }
        }
    }
}

extension View {
    func flareNavigationGesture(router: FlareRouter) -> some View {
        modifier(FlareNavigationGestureModifier(router: router))
    }
}
