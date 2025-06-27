import SwiftUI

//  - Visible Range Data Structure

/// 可见范围数据结构，用于跟踪 Timeline 中可见项目的范围
/// Visible range data structure for tracking visible items range in Timeline
struct VisibleRange: Equatable {
    let startIndex: Int
    let endIndex: Int
    let bufferSize: Int

    /// 创建可见范围
    /// Create visible range
    init(startIndex: Int, endIndex: Int, bufferSize: Int = 5) {
        self.startIndex = max(0, startIndex)
        self.endIndex = max(startIndex, endIndex)
        self.bufferSize = bufferSize
    }

    /// 获取包含缓冲区的扩展范围
    /// Get extended range including buffer
    var extendedRange: ClosedRange<Int> {
        let bufferedStart = max(0, startIndex - bufferSize)
        let bufferedEnd = endIndex + bufferSize
        return bufferedStart ... bufferedEnd
    }

    /// 检查索引是否在可见范围内（包含缓冲区）
    /// Check if index is within visible range (including buffer)
    func contains(_ index: Int) -> Bool {
        extendedRange.contains(index)
    }

    /// 检查索引是否在核心可见范围内（不包含缓冲区）
    /// Check if index is within core visible range (excluding buffer)
    func coreContains(_ index: Int) -> Bool {
        (startIndex ... endIndex).contains(index)
    }
}

//  - Visible Range Preference Key

/// SwiftUI PreferenceKey 用于收集可见范围数据
/// SwiftUI PreferenceKey for collecting visible range data
struct VisibleRangePreferenceKey: PreferenceKey {
    static var defaultValue: VisibleRange = .init(startIndex: 0, endIndex: 0)

    static func reduce(value: inout VisibleRange, nextValue: () -> VisibleRange) {
        let next = nextValue()
        // 合并范围，取最小的开始索引和最大的结束索引
        // Merge ranges, take minimum start index and maximum end index
        value = VisibleRange(
            startIndex: min(value.startIndex, next.startIndex),
            endIndex: max(value.endIndex, next.endIndex),
            bufferSize: value.bufferSize
        )
    }
}

//  - Visible Range Detector View

/// 可见范围检测器视图，用于检测项目是否在可见范围内
/// Visible range detector view for detecting if items are within visible range
struct VisibleRangeDetector: View {
    let index: Int
    let totalItems: Int

    var body: some View {
        GeometryReader { geometry in
            Color.clear
                .preference(
                    key: VisibleRangePreferenceKey.self,
                    value: calculateVisibleRange(geometry: geometry)
                )
        }
        .frame(height: 0) // 不占用空间 / Take no space
    }

    /// 计算可见范围
    /// Calculate visible range
    private func calculateVisibleRange(geometry: GeometryProxy) -> VisibleRange {
        let frame = geometry.frame(in: .named("scroll"))
        let screenHeight = UIScreen.main.bounds.height

        // 获取 ScrollView 的坐标空间信息
        // Get ScrollView coordinate space information
        let scrollFrame = geometry.frame(in: .global)

        // 检查当前项目是否在屏幕可见区域内
        // Check if current item is within screen visible area
        let isVisible = scrollFrame.minY < screenHeight && scrollFrame.maxY > 0

        if isVisible {
            // 计算可见范围的估算
            // Calculate estimated visible range
            let itemHeight: CGFloat = 120 // 估算的项目高度 / Estimated item height
            let visibleItemsCount = Int(screenHeight / itemHeight) + 2 // 额外的缓冲 / Extra buffer

            let startIndex = max(0, index - visibleItemsCount / 2)
            let endIndex = min(totalItems - 1, index + visibleItemsCount / 2)

            return VisibleRange(
                startIndex: startIndex,
                endIndex: endIndex,
                bufferSize: 5
            )
        } else {
            // 如果不可见，返回默认范围
            // If not visible, return default range
            return VisibleRange(startIndex: 0, endIndex: 0)
        }
    }
}

//  - Visible Range Manager

/// 可见范围管理器，用于管理和优化可见范围检测
/// Visible range manager for managing and optimizing visible range detection
@Observable
class VisibleRangeManager {
    private(set) var currentRange: VisibleRange = .init(startIndex: 0, endIndex: 0)
    private(set) var lastUpdateTime: Date = .init()

    // 性能优化：限制更新频率 / Performance optimization: limit update frequency
    private let updateThrottleInterval: TimeInterval = 0.016 // ~60fps

    /// 更新可见范围
    /// Update visible range
    func updateRange(_ newRange: VisibleRange) {
        let now = Date()

        // 节流更新，避免过于频繁的状态变化
        // Throttle updates to avoid too frequent state changes
        guard now.timeIntervalSince(lastUpdateTime) >= updateThrottleInterval else {
            return
        }

        // 只有在范围真正变化时才更新
        // Only update when range actually changes
        guard newRange != currentRange else {
            return
        }

        currentRange = newRange
        lastUpdateTime = now

        // 调试日志 / Debug logging
        if PerformanceConfig.isVerboseLoggingEnabled {
            FlareLog.performance("VisibleRangeManager Range updated: \(newRange.startIndex)-\(newRange.endIndex), extended: \(newRange.extendedRange)")
        }
    }

    /// 检查索引是否应该被渲染
    /// Check if index should be rendered
    func shouldRender(_ index: Int) -> Bool {
        currentRange.contains(index)
    }

    /// 检查索引是否在核心可见区域
    /// Check if index is in core visible area
    func isInCoreVisibleArea(_ index: Int) -> Bool {
        currentRange.coreContains(index)
    }

    /// 获取当前可见范围的统计信息
    /// Get statistics of current visible range
    var rangeStats: (visible: Int, buffered: Int) {
        let visibleCount = currentRange.endIndex - currentRange.startIndex + 1
        let bufferedCount = currentRange.extendedRange.count
        return (visible: visibleCount, buffered: bufferedCount)
    }
}

//  - View Extensions

extension View {
    /// 添加可见范围检测
    /// Add visible range detection
    func visibleRangeDetector(index: Int, totalItems: Int) -> some View {
        background(
            VisibleRangeDetector(index: index, totalItems: totalItems)
        )
    }
}
