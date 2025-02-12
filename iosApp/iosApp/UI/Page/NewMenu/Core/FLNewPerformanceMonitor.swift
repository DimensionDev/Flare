import Foundation
import QuartzCore

class FLNewPerformanceMonitor {
    static let shared = FLNewPerformanceMonitor()

    private var frameStartTime: CFTimeInterval = 0
    private var frameCount: Int = 0
    private var lastFPS: Double = 0

    private init() {}

    // - Public Methods
    func startMonitoring() {
        // 开始监控帧率
        CADisplayLink(target: self, selector: #selector(handleFrame)).add(to: .main, forMode: .common)
    }

    func logGesturePerformance(_ name: String, operation: () -> Void) {
        let start = CACurrentMediaTime()
        operation()
        let end = CACurrentMediaTime()

        let duration = (end - start) * 1000 // 转换为毫秒
        print("手势性能 - \(name): \(String(format: "%.2f", duration))ms")
    }

    func getCurrentFPS() -> Double {
        lastFPS
    }

    // - Private Methods
    @objc private func handleFrame(displayLink: CADisplayLink) {
        if frameStartTime == 0 {
            frameStartTime = displayLink.timestamp
            frameCount = 0
        }

        frameCount += 1

        let currentTime = displayLink.timestamp
        let elapsed = currentTime - frameStartTime

        if elapsed >= 1.0 {
            lastFPS = Double(frameCount) / elapsed
            frameCount = 0
            frameStartTime = currentTime

            // 如果帧率过低，打印警告
            if lastFPS < 45 {
                print("警告：帧率过低 - \(String(format: "%.2f", lastFPS)) FPS")
            }
        }
    }
}

// - Performance Tracking
extension FLNewPerformanceMonitor {
    func track<T>(_ operation: String, block: () -> T) -> T {
        let start = CACurrentMediaTime()
        let result = block()
        let end = CACurrentMediaTime()

        let duration = (end - start) * 1000 // 转换为毫秒
        print("性能追踪 - \(operation): \(String(format: "%.2f", duration))ms")

        return result
    }

    func trackAsync<T>(_ operation: String, block: (@escaping (T) -> Void) -> Void) {
        let start = CACurrentMediaTime()

        block { _ in
            let end = CACurrentMediaTime()
            let duration = (end - start) * 1000
            print("异步性能追踪 - \(operation): \(String(format: "%.2f", duration))ms")
        }
    }
}
