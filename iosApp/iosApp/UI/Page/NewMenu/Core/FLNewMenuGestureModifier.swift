import os
import SwiftUI

struct FLNewMenuGestureModifier: ViewModifier {
    @ObservedObject var appState: FLNewAppState
    @State private var currentAppBarIndex: Int = 0

    init(appState: FLNewAppState) {
        self.appState = appState
    }

    func body(content: Content) -> some View {
        content.simultaneousGesture(
            DragGesture(minimumDistance: 10, coordinateSpace: .local)
                .onChanged { value in
                    // 检查是否是从左边缘开始的手势
                    guard value.startLocation.x < 20 else {
                        os_log("[🖐️][GestureModifier] Drag ignored - not from left edge",
                               log: .default, type: .debug)
                        return
                    }

                    // 获取当前的 tabbar index 和 appbar item index
                    let isHomeTab = appState.tabStore?.selectedIndex == 0

                    // 在 Home tab 时，只有第一个 appbar item 才处理菜单手势
                    if isHomeTab && currentAppBarIndex > 0 {
                        os_log("[🖐️][GestureModifier] Drag ignored - not first appbar item in Home tab",
                               log: .default, type: .debug)
                        return
                    }

                    // 非 Home tab 时，允许菜单手势
                    if !isHomeTab {
                        os_log("[🖐️][GestureModifier] Processing drag - not in Home tab",
                               log: .default, type: .debug)
                    }

                    os_log("[🖐️][GestureModifier] Drag changed - Translation: (%{public}f, %{public}f), Predicted End: (%{public}f, %{public}f)",
                           log: .default, type: .debug,
                           value.translation.width, value.translation.height,
                           value.predictedEndTranslation.width, value.predictedEndTranslation.height)

                    handleDragChange(value)
                }
                .onEnded { value in
                    // 获取当前的 tabbar index 和 appbar item index
                    let isHomeTab = appState.tabStore?.selectedIndex == 0

                    // 在 Home tab 时，只有第一个 appbar item 才处理菜单手势
                    if isHomeTab && currentAppBarIndex > 0 {
                        return
                    }

                    os_log("[🖐️][GestureModifier] Drag ended - Translation: (%{public}f, %{public}f)",
                           log: .default, type: .debug,
                           value.translation.width, value.translation.height)

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

        let translation = value.translation.width
        let velocity = value.predictedEndTranslation.width - value.translation.width

        os_log("[🖐️][GestureModifier] Processing drag - Translation: %{public}f, Velocity: %{public}f",
               log: .default, type: .debug,
               translation, velocity)

        if translation > 0 {
            withAnimation(.spring()) {
                appState.isMenuOpen = true
            }
        }
    }

    private func handleDragEnd(_ value: DragGesture.Value) {
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
