import os
import UIKit

class FLNewGestureRecognizer: UIPanGestureRecognizer {
    private let gestureState: FLNewGestureState
    private var startLocation: CGPoint = .zero
    private var isTracking: Bool = false
    private var velocityThreshold: CGFloat = 500
    private var translationThreshold: CGFloat = 20
    private var directionThreshold: CGFloat = 0.7 // cos(45°)

    init(gestureState: FLNewGestureState, target: Any?, action: Selector?) {
        self.gestureState = gestureState
        super.init(target: target, action: action)

        // 设置代理
        delegate = self

        // 设置手势属性
        maximumNumberOfTouches = 1
        minimumNumberOfTouches = 1

        os_log("[🖐️][Gesture] Initialized with thresholds - velocity: %{public}f, translation: %{public}f, direction: %{public}f",
               log: .default, type: .debug,
               velocityThreshold, translationThreshold, directionThreshold)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)

        guard let touch = touches.first else { return }
        startLocation = touch.location(in: view)
        isTracking = true
        gestureState.beginGesture()

        os_log("[🖐️][Gesture] Touches began at: (%{public}f, %{public}f)",
               log: .default, type: .debug,
               startLocation.x, startLocation.y)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches, with: event)

        guard isTracking,
              let touch = touches.first else { return }

        let location = touch.location(in: view)
        let translation = CGPoint(
            x: location.x - startLocation.x,
            y: location.y - startLocation.y
        )

        let velocity = velocity(in: view)

        os_log("[🖐️][Gesture] Touch moved - Translation: (%{public}f, %{public}f), Velocity: (%{public}f, %{public}f)",
               log: .default, type: .debug,
               translation.x, translation.y, velocity.x, velocity.y)

        // 检查手势方向
        let totalTranslation = sqrt(translation.x * translation.x + translation.y * translation.y)
        if totalTranslation > 0 {
            let directionX = translation.x / totalTranslation

            os_log("[🖐️][Gesture] Direction X: %{public}f, Total Translation: %{public}f",
                   log: .default, type: .debug,
                   directionX, totalTranslation)

            // 检查是否主要是水平方向
            if abs(directionX) > directionThreshold {
                // 检查速度和位移是否满足条件
                if velocity.x > velocityThreshold, translation.x > translationThreshold {
                    if gestureState.shouldRecognizeGesture(velocity: velocity, translation: translation) {
                        os_log("[🖐️][Gesture] Gesture recognized and began",
                               log: .default, type: .debug)
                        state = .began
                    }
                }
            } else {
                os_log("[🖐️][Gesture] Gesture failed - direction threshold not met",
                       log: .default, type: .debug)
                state = .failed
            }
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesEnded(touches, with: event)
        os_log("[🖐️][Gesture] Touches ended", log: .default, type: .debug)
        endGesture()
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesCancelled(touches, with: event)
        os_log("[🖐️][Gesture] Touches cancelled", log: .default, type: .debug)
        endGesture()
    }

    private func endGesture() {
        isTracking = false
        gestureState.endGesture()
    }

    // 重置手势状态
    override func reset() {
        super.reset()
        isTracking = false
        startLocation = .zero
        os_log("[🖐️][Gesture] Reset", log: .default, type: .debug)
    }
}

// - UIGestureRecognizerDelegate
extension FLNewGestureRecognizer: UIGestureRecognizerDelegate {
    func gestureRecognizer(
        _: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        // 不允许与滚动视图手势或导航手势同时识别
        if otherGestureRecognizer is UIPanGestureRecognizer ||
            otherGestureRecognizer.view is UIScrollView ||
            otherGestureRecognizer is UIScreenEdgePanGestureRecognizer {
            return false
        }
        return true
    }

    func gestureRecognizer(
        _: UIGestureRecognizer,
        shouldRequireFailureOf otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        // 系统边缘手势优先
        if otherGestureRecognizer is UIScreenEdgePanGestureRecognizer {
            return true
        }

        // 判断是否是导航返回手势
        if let view = otherGestureRecognizer.view {
            // 检查手势是否属于导航控制器
            var current: UIResponder? = view
            while let responder = current {
                if responder is UINavigationController {
                    os_log("[🖐️][Gesture] Navigation controller found in responder chain, giving priority to system gesture",
                           log: .default, type: .debug)
                    return true
                }
                current = responder.next
            }

            // 检查手势类型
            if String(describing: type(of: otherGestureRecognizer)).contains("NavigationTransition") ||
                String(describing: type(of: otherGestureRecognizer)).contains("BackGesture") {
                os_log("[🖐️][Gesture] Navigation gesture detected, giving it priority",
                       log: .default, type: .debug)
                return true
            }
        }

        return false
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        // 获取AppState中的状态
        if !gestureState.isGestureEnabled {
            os_log("[🖐️][Gesture] Gesture disabled in AppState, rejecting",
                   log: .default, type: .debug)
            return false
        }

        // 额外的开始条件检查
        guard let panGesture = gestureRecognizer as? UIPanGestureRecognizer else {
            return true
        }

        let velocity = panGesture.velocity(in: panGesture.view)
        let translation = panGesture.translation(in: panGesture.view)

        // 检查是否是水平滑动
        let angle = atan2(velocity.y, velocity.x)
        return abs(angle) < .pi / 4 && velocity.x > 0 && translation.x > 0
    }
}
