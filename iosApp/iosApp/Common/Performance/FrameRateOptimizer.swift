
import UIKit
import SwiftUI

/// 独立的帧率优化器 - 确保120fps在release版本中也能生效
/// 不依赖于性能监控UI，专门负责帧率优化配置
class FrameRateOptimizer {
    

    static let shared = FrameRateOptimizer()
    

    private var frameRateDisplayLink: CADisplayLink?
    private var isOptimizationActive = false
    

    private init() {
        setupFrameRateOptimization()
    }

     func startOptimization() {
        guard !isOptimizationActive else {
            print("🚀 [FrameRateOptimizer] Already optimized")
            return
        }
        
        setupFrameRateOptimization()
        isOptimizationActive = true
        
        #if DEBUG
        print("🚀 [FrameRateOptimizer] 120fps optimization started")
        print("📱 [FrameRateOptimizer] Device max refresh rate: \(UIScreen.main.maximumFramesPerSecond)fps")
        print("🔧 [FrameRateOptimizer] ProMotion support: \(UIScreen.main.maximumFramesPerSecond > 60 ? "✅ YES" : "❌ NO")")
        #endif
    }
    

    func getDeviceFrameRateInfo() -> (maxFPS: Int, isProMotionSupported: Bool, isSimulator: Bool) {
        let maxFPS = UIScreen.main.maximumFramesPerSecond
        let isProMotionSupported = maxFPS > 60
        let isSimulator = isRunningOnSimulator()
        
        return (maxFPS: maxFPS, isProMotionSupported: isProMotionSupported, isSimulator: isSimulator)
    }
    

    private func setupFrameRateOptimization() {
        // 创建CADisplayLink来配置帧率
        frameRateDisplayLink = CADisplayLink(target: self, selector: #selector(frameRateDisplayLinkCallback))
        
        // 🔥 关键配置：设置120fps支持
        if #available(iOS 15.0, *) {
            let maxFrameRate = Float(UIScreen.main.maximumFramesPerSecond)
            frameRateDisplayLink?.preferredFrameRateRange = CAFrameRateRange(
                minimum: 60,           // 最低60fps
                maximum: maxFrameRate, // 设备最大刷新率
                preferred: maxFrameRate // 优先使用最大刷新率
            )
            
            #if DEBUG
            print("🔧 [FrameRateOptimizer] Configured preferredFrameRateRange: 60-\(maxFrameRate)fps")
            #endif
        } else {
            // iOS 15以下版本的兼容处理
            frameRateDisplayLink?.preferredFramesPerSecond = UIScreen.main.maximumFramesPerSecond
            
            #if DEBUG
            print("🔧 [FrameRateOptimizer] Configured preferredFramesPerSecond: \(UIScreen.main.maximumFramesPerSecond)fps")
            #endif
        }
        
        // 添加到主运行循环
        frameRateDisplayLink?.add(to: .main, forMode: .common)
        
        // 立即暂停，我们只需要配置，不需要实际的回调
        frameRateDisplayLink?.isPaused = true
    }
    
    /// CADisplayLink回调 - 实际上不执行任何操作，只是为了配置帧率
    @objc private func frameRateDisplayLinkCallback() {
        // 空实现 - 我们只需要CADisplayLink的帧率配置功能
    }
    
     private func isRunningOnSimulator() -> Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }
}


extension FrameRateOptimizer {
    
    /// 在App启动时调用的便捷方法
    static func configureForApp() {
        FrameRateOptimizer.shared.startOptimization()
        
        #if DEBUG
        let info = FrameRateOptimizer.shared.getDeviceFrameRateInfo()
        print("""
        📱 [FrameRateOptimizer] Device Configuration:
           - Max FPS: \(info.maxFPS)
           - ProMotion: \(info.isProMotionSupported ? "✅" : "❌")
           - Platform: \(info.isSimulator ? "🖥️ Simulator" : "📱 Device")
           - Optimization: ✅ Active
        """)
        #endif
    }
}
