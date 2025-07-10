import Foundation
import SwiftUI

/// 性能监控配置类
/// 统一管理所有性能监控相关的常量和配置
enum PerformanceConfig {
    // - Debug Configuration

    /// 是否启用调试模式（只在Debug构建中启用性能监控）
    static var isDebugModeEnabled: Bool {
        // 临时强制启用用于测试
        true
//        #if DEBUG
//        // return true
//        #else
//            // return false
//        #endif
    }

    /// 是否启用详细日志输出
    static let isVerboseLoggingEnabled = true

    // - Monitoring Configuration

    /// CPU和内存监控更新间隔（秒）
    /// Standardized to 0.5s for consistency with performance testing
    static let cpuMemoryUpdateInterval: TimeInterval = 0.5

    /// 帧率监控更新间隔（基于CADisplayLink）
    /// FPS is calculated every 0.5s for more responsive monitoring
    static let frameRateUpdateInterval: TimeInterval = 0.5

    /// 性能测试数据收集间隔（秒）
    /// Unified interval for all metrics during performance testing
    static let performanceTestDataInterval: TimeInterval = 0.5

    /// 历史数据保留的最大数量
    enum HistoryLimits {
        static let cpuMemoryHistory = 120 // 60秒历史（0.5s间隔）
        static let frameRateHistory = 120 // 60秒历史（0.5s间隔）
        static let scrollFrameRates = 120 // 滚动性能分析（0.5s间隔）
    }

    // - Performance Thresholds

    /// 性能阈值配置
    enum Thresholds {
        static let cpuUsage: Double = 0.3 // 30%
        static let memoryUsage: UInt64 = 200_000_000 // 200MB
        static let frameRate: Double = 55.0 // 55fps
        static let stutterFrameRate: Double = 45.0 // 45fps以下认为是卡顿

        /// 动态获取设备最大刷新率
        static var deviceMaxFrameRate: Double {
            if #available(iOS 15.0, *) {
                Double(UIScreen.main.maximumFramesPerSecond)
            } else {
                60.0
            }
        }

        /// 根据设备刷新率调整的高性能阈值
        static var highPerformanceFrameRate: Double {
            let maxFPS = deviceMaxFrameRate
            if maxFPS >= 120 {
                return 100.0 // 120Hz设备的高性能阈值
            } else {
                return 55.0 // 60Hz设备的高性能阈值
            }
        }
    }

    // - Floating Window Configuration

    /// 浮动窗口配置
    enum FloatingWindow {
        /// 窗口尺寸
        enum Size {
            static let minimized = CGSize(width: 120, height: 60)
            static let expanded = CGSize(width: UIScreen.main.bounds.width, height: 280) // 进一步减少高度以适应简化布局
        }

        /// 边距和吸附配置
        enum Layout {
            static let edgeMargin: CGFloat = 20
            static let snapDistance: CGFloat = 30
        }

        /// 透明度配置
        enum Opacity {
            static let normal: Double = 0.9
            static let dragging: Double = 0.6
        }

        /// UserDefaults键
        enum UserDefaultsKeys {
            static let positionX = "FloatingWindow.Position.X"
            static let positionY = "FloatingWindow.Position.Y"
            static let state = "FloatingWindow.State"
            static let selectedChart = "FloatingWindow.SelectedChart"
        }
    }

    // - Chart Configuration

    /// 图表配置
    enum Charts {
        /// 图表数据点数量
        static let maxDataPoints = 20

        /// 图表高度
        static let chartHeight: CGFloat = 120

        /// 迷你图表高度
        static let miniChartHeight: CGFloat = 80
    }

    // - Color Configuration

    /// 性能状态颜色配置
    enum Colors {
        /// CPU使用率颜色
        static func cpuColor(for usage: Double) -> Color {
            if usage > 0.5 {
                .red
            } else if usage > 0.3 {
                .orange
            } else {
                .green
            }
        }

        /// 内存使用颜色
        static func memoryColor(for memoryMB: Double) -> Color {
            if memoryMB > 200 {
                .red
            } else if memoryMB > 150 {
                .orange
            } else {
                .blue
            }
        }

        /// 帧率颜色 - 动态适配120Hz设备
        static func frameRateColor(for fps: Double) -> Color {
            let maxFPS = Thresholds.deviceMaxFrameRate

            if maxFPS >= 120 {
                // 120Hz设备的阈值
                if fps < 60 {
                    return .red // 低于60fps为红色
                } else if fps < 100 {
                    return .orange // 60-100fps为橙色
                } else {
                    return .green // 100fps以上为绿色
                }
            } else {
                // 60Hz设备的阈值
                if fps < 45 {
                    return .red // 低于45fps为红色
                } else if fps < 55 {
                    return .orange // 45-55fps为橙色
                } else {
                    return .green // 55fps以上为绿色
                }
            }
        }

        /// 卡顿率颜色
        static func stutterColor(for rate: Double) -> Color {
            if rate > 20 {
                .red
            } else if rate > 10 {
                .orange
            } else {
                .green
            }
        }
    }

    // - Formatting Configuration

    /// 数值格式化配置
    enum Formatting {
        /// CPU使用率格式化
        static func formatCPU(_ value: Double) -> String {
            String(format: "%.1f", value * 100)
        }

        /// 内存使用格式化（MB）
        static func formatMemoryMB(_ bytes: UInt64) -> String {
            String(format: "%.0f", Double(bytes) / 1_000_000)
        }

        /// 帧率格式化
        static func formatFrameRate(_ fps: Double) -> String {
            String(format: "%.0f", fps)
        }

        /// 百分比格式化
        static func formatPercentage(_ value: Double) -> String {
            String(format: "%.1f", value)
        }

        /// 详细数值格式化
        static func formatDetailedValue(_ value: Double) -> String {
            String(format: "%.2f", value)
        }
    }

    // - Performance Grades

    /// 性能等级评估 - 动态适配120Hz设备
    enum PerformanceGrades {
        static func frameRateGrade(for fps: Double) -> String {
            let maxFPS = Thresholds.deviceMaxFrameRate

            if maxFPS >= 120 {
                // 120Hz设备的等级标准
                if fps >= 100 {
                    return "Excellent"
                } else if fps >= 80 {
                    return "Good"
                } else if fps >= 60 {
                    return "Fair"
                } else {
                    return "Poor"
                }
            } else {
                // 60Hz设备的等级标准
                if fps >= 55 {
                    return "Excellent"
                } else if fps >= 45 {
                    return "Good"
                } else if fps >= 30 {
                    return "Fair"
                } else {
                    return "Poor"
                }
            }
        }

        static func scrollPerformanceGrade(averageFPS: Double, stutterCount: Int) -> String {
            let maxFPS = Thresholds.deviceMaxFrameRate

            if maxFPS >= 120 {
                // 120Hz设备的滚动性能标准
                if averageFPS >= 100, stutterCount <= 2 {
                    return "Excellent"
                } else if averageFPS >= 80, stutterCount <= 5 {
                    return "Good"
                } else if averageFPS >= 60, stutterCount <= 10 {
                    return "Fair"
                } else {
                    return "Needs Optimization"
                }
            } else {
                // 60Hz设备的滚动性能标准
                if averageFPS >= 55, stutterCount <= 2 {
                    return "Excellent"
                } else if averageFPS >= 45, stutterCount <= 5 {
                    return "Good"
                } else if averageFPS >= 30, stutterCount <= 10 {
                    return "Fair"
                } else {
                    return "Needs Optimization"
                }
            }
        }
    }

    // - Application Lifecycle

    /// 应用生命周期配置
    enum Lifecycle {
        /// 应用进入后台时是否停止监控
        static let stopMonitoringOnBackground = true

        /// 应用返回前台时是否自动恢复监控
        static let resumeMonitoringOnForeground = true

        /// 内存警告时是否清理历史数据
        static let clearHistoryOnMemoryWarning = true
    }
}

// - Performance Theme

/// 性能监控主题配置
enum PerformanceTheme {
    // - Card Styles

    static let cardCornerRadius: CGFloat = 16
    static let cardShadowRadius: CGFloat = 8
    static let cardShadowOffset = CGSize(width: 0, height: 4)
    static let cardShadowOpacity: Double = 0.2

    // - Floating Window Styles

    static let floatingWindowCornerRadius: CGFloat = 16
    static let floatingWindowShadowRadius: CGFloat = 12
    static let floatingWindowShadowOffset = CGSize(width: 0, height: 6)
    static let floatingWindowShadowOpacity: Double = 0.3

    // - Animation Durations

    static let stateTransitionDuration: Double = 0.3
    static let dragFeedbackDuration: Double = 0.2
    static let snapAnimationDuration: Double = 0.5

    // - Fonts

    static let titleFont = Font.headline
    static let subtitleFont = Font.subheadline
    static let captionFont = Font.caption
    static let monospaceFont = Font.system(.caption, design: .monospaced)

    // - Spacing

    static let defaultSpacing: CGFloat = 16
    static let compactSpacing: CGFloat = 8
    static let tightSpacing: CGFloat = 4
}
