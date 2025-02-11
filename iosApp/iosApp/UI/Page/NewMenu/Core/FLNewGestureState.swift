import Foundation
import os
import SwiftUI

class FLNewGestureState: ObservableObject {
    @Published var isGestureEnabled: Bool = true
    @Published var isGestureActive: Bool = false
    private weak var tabProvider: TabStateProvider?

    private var lastGestureTime: TimeInterval = 0
    private let minimumGestureInterval: TimeInterval = 0.5

    // 手势配置
    struct Configuration {
        let minimumVelocity: CGFloat
        let minimumTranslation: CGFloat
        let velocityThreshold: CGFloat

        static let `default` = Configuration(
            minimumVelocity: 300,
            minimumTranslation: 50,
            velocityThreshold: 1000
        )
    }

    let configuration: Configuration

    init(configuration: Configuration = .default, tabProvider: TabStateProvider? = nil) {
        self.configuration = configuration
        self.tabProvider = tabProvider
    }

    // 检查手势是否满足条件
    func shouldRecognizeGesture(velocity: CGPoint, translation: CGPoint) -> Bool {
        // 首先检查是否在第一个tab
        if let tabProvider {
            let isFirstTab = tabProvider.selectedIndex == 0
            os_log("[🖐️][GestureState] Tab check - isFirstTab: %{public}@",
                   log: .default, type: .debug,
                   String(isFirstTab))

            if !isFirstTab {
                os_log("[🖐️][GestureState] Gesture rejected - not on first tab",
                       log: .default, type: .debug)
                return false
            }
        }

        guard isGestureEnabled else {
            os_log("[🖐️][GestureState] Gesture not enabled", log: .default, type: .debug)
            return false
        }

        let currentTime = Date().timeIntervalSince1970
        let timeSinceLastGesture = currentTime - lastGestureTime

        os_log("[🖐️][GestureState] Check gesture - Time since last: %{public}f, Velocity: (%{public}f, %{public}f), Translation: (%{public}f, %{public}f)",
               log: .default, type: .debug,
               timeSinceLastGesture, velocity.x, velocity.y, translation.x, translation.y)

        if timeSinceLastGesture < minimumGestureInterval {
            os_log("[🖐️][GestureState] Gesture rejected - too soon since last gesture",
                   log: .default, type: .debug)
            return false
        }

        return true
    }

    // 开始处理手势
    func beginGesture() {
        // 如果不在第一个tab，直接返回
        if let tabProvider, tabProvider.selectedIndex > 0 {
            os_log("[🖐️][GestureState] Begin gesture rejected - not on first tab",
                   log: .default, type: .debug)
            return
        }

        os_log("[🖐️][GestureState] Begin gesture - enabled: %{public}@, active: %{public}@",
               log: .default, type: .debug,
               String(isGestureEnabled), String(isGestureActive))

        isGestureActive = true
        lastGestureTime = Date().timeIntervalSince1970
    }

    // 结束处理手势
    func endGesture() {
        os_log("[🖐️][GestureState] End gesture - enabled: %{public}@, active: %{public}@",
               log: .default, type: .debug,
               String(isGestureEnabled), String(isGestureActive))

        isGestureActive = false
    }
}
