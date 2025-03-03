import os
import SwiftUI

// 自定义环境key用于检测导航层级
struct NavigationLevelKey: EnvironmentKey {
    static let defaultValue: Int = 0
}

extension EnvironmentValues {
    var navigationLevel: Int {
        get { self[NavigationLevelKey.self] }
        set { self[NavigationLevelKey.self] = newValue }
    }
}

struct FLNewMenuGestureModifier: ViewModifier {
    @ObservedObject var appState: FLNewAppState
    @State private var currentAppBarIndex: Int = 0
    @Environment(\.navigationLevel) private var navigationLevel

    // 添加判断向右滑动的方法
    private func isValidRightSwipe(_ value: DragGesture.Value) -> Bool {
        let translation = value.translation
        let distance = sqrt(pow(translation.width, 2) + pow(translation.height, 2))
        guard distance > 0 else { return false }

        // 计算方向向量，判断是否向右滑动（允许一定角度的偏差）
        let directionVector = (
            x: translation.width / distance,
            y: translation.height / distance
        )
        return directionVector.x > 0.7 // cos 45° ≈ 0.7
    }

    init(appState: FLNewAppState) {
        self.appState = appState
    }

    func body(content: Content) -> some View {
        content.simultaneousGesture(
            DragGesture(minimumDistance: 10, coordinateSpace: .local)
                .onChanged { value in
                    // 如果在导航详情页或者不是第一个tab，不处理手势
                    if currentAppBarIndex > 0 || navigationLevel > 0 || appState.isInNavigationDetail {
                        os_log("[🖐️][GestureModifier] Drag ignored - not first tab, in navigation stack, or in detail page",
                               log: .default, type: .debug)
                        return
                    }

                    // 检查是否是向右滑动
                    guard isValidRightSwipe(value) else {
//                        os_log("[🖐️][GestureModifier] Drag ignored - not right direction",
//                               log: .default, type: .debug)
                        return
                    }

                    handleDragChange(value)
                }
                .onEnded { value in
                    // 如果在导航详情页或者不是第一个tab，不处理手势
                    if currentAppBarIndex > 0 || navigationLevel > 0 || appState.isInNavigationDetail {
                        return
                    }

                    // 检查是否是向右滑动
                    guard isValidRightSwipe(value) else {
//                        os_log("[🖐️][GestureModifier] Drag end ignored - not right direction",
//                               log: .default, type: .debug)
                        return
                    }

                    handleDragEnd(value)
                }
        )
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("AppBarIndexDidChange"))) { notification in
            if let index = notification.object as? Int {
                currentAppBarIndex = index
                os_log("[🖐️][GestureModifier] AppBar index updated: %{public}d",
                       log: .default, type: .debug, index)
            }
        }
    }

    private func handleDragChange(_ value: DragGesture.Value) {
        guard appState.gestureState.isGestureEnabled else {
            os_log("[🖐️][GestureModifier] Gesture not enabled", log: .default, type: .debug)
            return
        }
        
        // 在导航栈中不处理手势
        if navigationLevel > 0 {
            os_log("[🖐️][GestureModifier] Gesture ignored - in navigation stack", log: .default, type: .debug)
            return
        }

        let translation = value.translation
        let velocity = value.predictedEndTranslation.width - value.translation.width

        os_log("[🖐️][GestureModifier] Processing drag - Translation: %{public}f, Velocity: %{public}f",
               log: .default, type: .debug,
               translation.width, velocity)

        if translation.width > 0 {
            withAnimation(.spring()) {
                appState.isMenuOpen = true
            }
        }
    }

    private func handleDragEnd(_ value: DragGesture.Value) {
        // 在导航栈中不处理手势
        if navigationLevel > 0 {
            os_log("[🖐️][GestureModifier] Gesture end ignored - in navigation stack", log: .default, type: .debug)
            return
        }
        
        let translation = value.translation.width
        let velocity = value.predictedEndTranslation.width - value.translation.width

        os_log("[🖐️][GestureModifier] Processing drag end - Translation: %{public}f, Velocity: %{public}f",
               log: .default, type: .debug,
               translation, velocity)

        withAnimation(.spring()) {
            if translation > UIScreen.main.bounds.width * 0.3 || velocity > 300 {
                os_log("[🖐️][GestureModifier] Opening menu", log: .default, type: .debug)
                appState.isMenuOpen = true
            } else {
                os_log("[🖐️][GestureModifier] Closing menu", log: .default, type: .debug)
                appState.isMenuOpen = false
            }
        }
    }
}

// - View Extension
extension View {
    func newMenuGesture(appState: FLNewAppState) -> some View {
        modifier(FLNewMenuGestureModifier(appState: appState))
    }
}

// - GeometryProxy Extension
private extension GeometryProxy {
    var uiView: UIView? {
        let mirror = Mirror(reflecting: self)
        for child in mirror.children {
            if let view = child.value as? UIView {
                return view
            }
        }
        return nil
    }
}
