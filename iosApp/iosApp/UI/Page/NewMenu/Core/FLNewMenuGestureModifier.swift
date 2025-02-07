import os
import SwiftUI

struct FLNewMenuGestureModifier: ViewModifier {
    @ObservedObject var appState: FLNewAppState

    init(appState: FLNewAppState) {
        self.appState = appState
    }

    func body(content: Content) -> some View {
        content.simultaneousGesture(
            DragGesture(minimumDistance: 10, coordinateSpace: .local)
                .onChanged { value in
                    // 只在第一个tab时处理手势
                    guard appState.tabStore?.selectedIndex == 0 else {
                        os_log("[🖐️][GestureModifier] Drag ignored - not on first tab",
                               log: .default, type: .debug)
                        return
                    }

                    // 检查是否是从左边缘开始的手势
                    guard value.startLocation.x < 20 else {
                        os_log("[🖐️][GestureModifier] Drag ignored - not from left edge",
                               log: .default, type: .debug)
                        return
                    }

                    os_log("[🖐️][GestureModifier] Drag changed - Translation: (%{public}f, %{public}f), Predicted End: (%{public}f, %{public}f)",
                           log: .default, type: .debug,
                           value.translation.width, value.translation.height,
                           value.predictedEndTranslation.width, value.predictedEndTranslation.height)

                    handleDragChange(value)
                }
                .onEnded { value in
                    // 只在第一个tab时处理手势
                    guard appState.tabStore?.selectedIndex == 0 else { return }

                    os_log("[🖐️][GestureModifier] Drag ended - Translation: (%{public}f, %{public}f)",
                           log: .default, type: .debug,
                           value.translation.width, value.translation.height)

                    handleDragEnd(value)
                }
        )
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
