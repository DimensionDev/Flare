import os
import SwiftUI
import UIKit

class FLNewGestureCoordinator: NSObject {
    private let gestureState: FLNewGestureState
    private let menuState: Binding<Bool>
    private weak var tabProvider: TabStateProvider?

    init(gestureState: FLNewGestureState,
         menuState: Binding<Bool>,
         tabProvider: TabStateProvider? = nil) {
        self.gestureState = gestureState
        self.menuState = menuState
        self.tabProvider = tabProvider

        super.init()

        os_log("[🖐️][GestureCoordinator] Initialized", log: .default, type: .debug)
    }

    // 处理手势
    @objc func handleGesture(_ gesture: UIPanGestureRecognizer) {
        switch gesture.state {
        case .began:
            handleGestureBegan(gesture)
        case .changed:
            handleGestureChanged(gesture)
        case .ended, .cancelled:
            handleGestureEnded(gesture)
        default:
            break
        }
    }

    private func handleGestureBegan(_: UIPanGestureRecognizer) {
        // 如果不在第一个tab，不处理菜单手势
        if let tabProvider,
           tabProvider.selectedIndex > 0 {
            os_log("[🖐️][GestureCoordinator] Gesture ignored - not on first tab", log: .default, type: .debug)
            return
        }

        gestureState.beginGesture()
    }

    private func handleGestureChanged(_ gesture: UIPanGestureRecognizer) {
        guard gestureState.isGestureActive else {
            os_log("[🖐️][GestureCoordinator] Gesture not active", log: .default, type: .debug)
            return
        }

        let velocity = gesture.velocity(in: gesture.view)
        let translation = gesture.translation(in: gesture.view)

        os_log("[🖐️][GestureCoordinator] Processing gesture - Translation: (%{public}f, %{public}f), Velocity: (%{public}f, %{public}f)",
               log: .default, type: .debug,
               translation.x, translation.y, velocity.x, velocity.y)

        if gestureState.shouldRecognizeGesture(
            velocity: velocity,
            translation: translation
        ) {
            withAnimation {
                menuState.wrappedValue = true
            }
        }
    }

    private func handleGestureEnded(_: UIPanGestureRecognizer) {
        os_log("[🖐️][GestureCoordinator] Gesture ended", log: .default, type: .debug)
        gestureState.endGesture()
    }

    // 创建手势识别器
    func createGestureRecognizer() -> UIGestureRecognizer {
        FLNewGestureRecognizer(
            gestureState: gestureState,
            target: self,
            action: #selector(handleGesture(_:))
        )
    }
}
