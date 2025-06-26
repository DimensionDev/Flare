import Foundation
import shared

 
class PagingStateConverter {
 
    /// 转换统计
    private var stats = ConversionStats()

    /// 上次转换的状态缓存
    private var lastConvertedState: FlareTimelineState?

    /// 转换队列
    private let conversionQueue = DispatchQueue(label: "timeline.state.converter", qos: .userInitiated)

 
    /// 上次转换的item数量
    private var lastConvertedItemCount: Int = 0

    /// 已转换的items缓存
    private var convertedItems: [TimelineItem] = []

    /// 上次转换的状态签名（用于检测真正的状态变化）
    private var lastStateSignature: String?

 
    init() {
        print("🏗️ [PagingStateConverter] Initialized")
    }

    /// 将KMP的PagingState转换为Swift的FlareTimelineState
    /// - Parameter pagingState: KMP的PagingState
    /// - Returns: 转换后的FlareTimelineState
    func convert(_ pagingState: PagingState<UiTimeline>) -> FlareTimelineState {
        let startTime = CFAbsoluteTimeGetCurrent()
        stats.totalConversions += 1

        let result: FlareTimelineState

        switch pagingState {
        case is PagingStateLoading<UiTimeline>:
            result = .loading
            stats.loadingStates += 1

        case let errorState as PagingStateError<UiTimeline>:
            let flareError = FlareError.from(errorState.error)
            result = .error(flareError)
            stats.errorStates += 1

        case is PagingStateEmpty<UiTimeline>:
            result = .empty
            stats.emptyStates += 1

        case let successState as PagingStateSuccess<UiTimeline>:
            result = convertSuccessState(successState)
            stats.successStates += 1

        default:
            print("⚠️ [PagingStateConverter] Unknown PagingState type: \(type(of: pagingState))")
            result = .loading
            stats.unknownStates += 1
        }

        // 更新统计
        let duration = CFAbsoluteTimeGetCurrent() - startTime
        stats.totalDuration += duration
        stats.averageDuration = stats.totalDuration / Double(stats.totalConversions)

        // 缓存结果
        lastConvertedState = result

        // 记录状态变化
        if let lastState = lastConvertedState, lastState != result {
            print("📊 [PagingStateConverter] State changed: \(result.changesSummary(from: lastState))")
        }

        return result
    }

     var statistics: ConversionStats {
        stats
    }

    /// 重置统计信息
    func resetStatistics() {
        stats = ConversionStats()
        print("📊 [PagingStateConverter] Statistics reset")
    }

    /// 重置转换器状态（用于刷新或切换数据源）
    func reset() {
        resetIncrementalState()
        lastConvertedState = nil
        print("🔄 [PagingStateConverter] Converter reset")
    }

    // MARK: - Private Methods

    /// 转换成功状态 - 使用修复的增量转换策略
    /// - Parameter successState: KMP的成功状态
    /// - Returns: 转换后的FlareTimelineState
    private func convertSuccessState(_ successState: PagingStateSuccess<UiTimeline>) -> FlareTimelineState {
        let kmpItemCount = Int(successState.itemCount)
        let isRefreshing = successState.isRefreshing
        let hasMore = checkHasMoreData(successState)

        // 如果没有项目，重置状态并返回空
        guard kmpItemCount > 0 else {
            resetIncrementalState()
            return .empty
        }

        // 🔥 关键修复：确定实际可转换的数量
        let actualAvailableCount = determineActualAvailableCount(successState, maxCount: kmpItemCount)

        // 生成状态签名 - 使用实际可用数量
        let currentStateSignature = "\(actualAvailableCount)_\(isRefreshing)_\(successState.appendState)"

        print("🔄 [PagingStateConverter] KMP reports: \(kmpItemCount), Actually available: \(actualAvailableCount)")

        // 检查是否需要重置（刷新或数据减少）
        if isRefreshing || actualAvailableCount < lastConvertedItemCount {
            print("🔄 [PagingStateConverter] Resetting incremental state - refreshing: \(isRefreshing), available: \(actualAvailableCount) < converted: \(lastConvertedItemCount)")
            resetIncrementalState()
        }

        // 智能重复检测 - 允许数量增加的情况
        if !shouldSkipConversion(currentStateSignature) {
            // 增量转换新数据
            if actualAvailableCount > lastConvertedItemCount {
                print("🔄 [PagingStateConverter] Converting new items: \(lastConvertedItemCount) -> \(actualAvailableCount)")

                let newItems = convertItemsInRange(
                    from: lastConvertedItemCount,
                    to: actualAvailableCount,
                    successState: successState
                )

                // 追加新items到已有列表
                convertedItems.append(contentsOf: newItems)
                lastConvertedItemCount = actualAvailableCount

                // 更新统计
                stats.totalItemsConverted += newItems.count
                stats.averageItemsPerConversion = Double(stats.totalItemsConverted) / Double(stats.successStates)

                print("🔄 [PagingStateConverter] Added \(newItems.count) new items, total: \(convertedItems.count)")
            } else if actualAvailableCount == lastConvertedItemCount, convertedItems.isEmpty {
                // 首次转换或状态重置后的完整转换
                print("🔄 [PagingStateConverter] Initial full conversion for \(actualAvailableCount) items")

                convertedItems = convertItemsInRange(
                    from: 0,
                    to: actualAvailableCount,
                    successState: successState
                )
                lastConvertedItemCount = actualAvailableCount

                // 更新统计
                stats.totalItemsConverted += convertedItems.count
                stats.averageItemsPerConversion = Double(stats.totalItemsConverted) / Double(stats.successStates)

                print("🔄 [PagingStateConverter] Initial conversion completed: \(convertedItems.count) items")
            }
        } else {
            print("🚫 [PagingStateConverter] Skipping duplicate conversion - signature: \(currentStateSignature)")
        }

        // 更新状态签名
        lastStateSignature = currentStateSignature

        if convertedItems.isEmpty {
            return .empty
        }

        return .loaded(items: convertedItems, hasMore: hasMore, isRefreshing: isRefreshing)
    }

    /// 检查是否有更多数据可加载
    /// - Parameter successState: KMP的成功状态
    /// - Returns: 是否有更多数据
    private func checkHasMoreData(_ successState: PagingStateSuccess<UiTimeline>) -> Bool {
        // 基于appendState判断是否有更多数据
        let appendState = successState.appendState

        // 简化实现：基于appendState的字符串描述判断
        let appendStateDescription = String(describing: appendState)

        // 如果包含"NotLoading"且"endOfPaginationReached=true"，则没有更多数据
        if appendStateDescription.contains("NotLoading") && appendStateDescription.contains("endOfPaginationReached=true") {
            return false
        }

        // 如果是Loading或Error状态，保守地认为可能有更多数据
        if appendStateDescription.contains("Loading") || appendStateDescription.contains("Error") {
            return true
        }

        // 默认情况：有数据时假设可能有更多
        return successState.itemCount > 0
    }

    /// 重置增量转换状态
    private func resetIncrementalState() {
        lastConvertedItemCount = 0
        convertedItems.removeAll()
        lastStateSignature = nil
        print("🔄 [PagingStateConverter] Incremental state reset")
    }

    /// 确定实际可转换的数量
    /// - Parameters:
    ///   - successState: KMP的成功状态
    ///   - maxCount: KMP报告的最大数量
    /// - Returns: 实际可转换的数量
    private func determineActualAvailableCount(_ successState: PagingStateSuccess<UiTimeline>, maxCount: Int) -> Int {
        // 使用批量转换策略，避免一次性转换过多数据
        let batchSize = 20
        let maxBatchesToCheck = 5 // 最多检查5个批次

        var actualCount = lastConvertedItemCount

        // 从当前已转换位置开始，按批次探测可用数据
        for batchIndex in 0 ..< maxBatchesToCheck {
            let batchStart = lastConvertedItemCount + (batchIndex * batchSize)
            let batchEnd = min(batchStart + batchSize, maxCount)

            if batchStart >= maxCount {
                break
            }

            // 检查这个批次的第一个和最后一个item
            let firstAvailable = successState.peek(index: Int32(batchStart)) != nil
            let lastAvailable = successState.peek(index: Int32(batchEnd - 1)) != nil

            if firstAvailable, lastAvailable {
                actualCount = batchEnd
            } else if firstAvailable {
                // 如果第一个可用但最后一个不可用，逐个检查
                for index in batchStart ..< batchEnd {
                    if successState.peek(index: Int32(index)) != nil {
                        actualCount = index + 1
                    } else {
                        break
                    }
                }
                break
            } else {
                // 如果第一个都不可用，停止检查
                break
            }
        }

        print("🔍 [PagingStateConverter] Determined actual count: \(actualCount) (KMP reported: \(maxCount))")
        return actualCount
    }

    /// 智能重复检测 - 允许数量增加的情况
    /// - Parameter currentSignature: 当前状态签名
    /// - Returns: 是否应该跳过转换
    private func shouldSkipConversion(_ currentSignature: String) -> Bool {
        guard let lastSignature = lastStateSignature else { return false }

        // 如果签名完全相同，跳过
        if lastSignature == currentSignature {
            return true
        }

        // 解析签名组件
        let lastComponents = lastSignature.split(separator: "_")
        let currentComponents = currentSignature.split(separator: "_")

        if lastComponents.count >= 1, currentComponents.count >= 1 {
            let lastCount = Int(lastComponents[0]) ?? 0
            let currentCount = Int(currentComponents[0]) ?? 0

            // 如果数量增加，说明有新数据，不跳过
            if currentCount > lastCount {
                print("🔄 [PagingStateConverter] Count increased: \(lastCount) -> \(currentCount), allowing conversion")
                return false
            }
        }

        return false
    }

    /// 转换指定范围的items
    /// - Parameters:
    ///   - from: 起始index（包含）
    ///   - to: 结束index（不包含）
    ///   - successState: KMP的成功状态
    /// - Returns: 转换后的TimelineItem数组
    private func convertItemsInRange(
        from startIndex: Int,
        to endIndex: Int,
        successState: PagingStateSuccess<UiTimeline>
    ) -> [TimelineItem] {
        var items: [TimelineItem] = []

        print("🔄 [PagingStateConverter] Converting range [\(startIndex), \(endIndex))")

        for index in startIndex ..< endIndex {
            var uiTimeline: UiTimeline?

                // 首先尝试peek（不触发加载）
                = if let peekedItem = successState.peek(index: Int32(index))
            {
                peekedItem
            } else {
                // 如果peek失败，使用get（可能触发加载）
                successState.get(index: Int32(index))
            }

            if let timeline = uiTimeline {
                let timelineItem = TimelineItem.from(timeline)
                items.append(timelineItem)
            } else {
                print("⚠️ [PagingStateConverter] Failed to get item at index \(index)")
                // 遇到nil时停止转换，避免空洞
                break
            }
        }

        print("🔄 [PagingStateConverter] Converted \(items.count) items in range [\(startIndex), \(endIndex))")
        return items
    }

    /// 智能状态比较，避免不必要的UI更新
    /// - Parameters:
    ///   - oldState: 旧状态
    ///   - newState: 新状态
    /// - Returns: 是否需要更新UI
    private func shouldUpdateUI(from oldState: FlareTimelineState, to newState: FlareTimelineState) -> Bool {
        // 使用FlareTimelineState的内置比较方法
        newState.needsUIUpdate(from: oldState)
    }
}

// MARK: - ConversionStats

/// 转换统计信息
struct ConversionStats {
    /// 总转换次数
    var totalConversions: Int = 0
    /// 加载状态次数
    var loadingStates: Int = 0
    /// 成功状态次数
    var successStates: Int = 0
    /// 错误状态次数
    var errorStates: Int = 0
    /// 空状态次数
    var emptyStates: Int = 0
    /// 未知状态次数
    var unknownStates: Int = 0
    /// 总转换耗时
    var totalDuration: TimeInterval = 0
    /// 平均转换耗时
    var averageDuration: TimeInterval = 0
    /// 总转换项目数
    var totalItemsConverted: Int = 0
    /// 平均每次转换的项目数
    var averageItemsPerConversion: Double = 0

    /// 格式化的统计信息
    var formattedDescription: String {
        """
        PagingStateConverter Statistics:
        - Total Conversions: \(totalConversions)
        - Loading States: \(loadingStates)
        - Success States: \(successStates)
        - Error States: \(errorStates)
        - Empty States: \(emptyStates)
        - Unknown States: \(unknownStates)
        - Average Duration: \(String(format: "%.4f", averageDuration))s
        - Total Items Converted: \(totalItemsConverted)
        - Average Items per Conversion: \(String(format: "%.1f", averageItemsPerConversion))
        """
    }

    /// 转换效率
    var efficiency: Double {
        guard totalConversions > 0 else { return 0 }

        // 基于转换速度和成功率计算效率
        let successRate = Double(successStates) / Double(totalConversions)
        let speedScore = averageDuration < 0.001 ? 1.0 : max(0, 1.0 - (averageDuration - 0.001) * 1000)

        return (successRate * 0.7) + (speedScore * 0.3)
    }
}

// MARK: - PagingStateConverter Extensions

extension PagingStateConverter {
    var isHealthy: Bool {
        guard stats.totalConversions > 10 else { return true } // 样本太少，认为健康

        // 检查错误率和性能
        let errorRate = Double(stats.errorStates + stats.unknownStates) / Double(stats.totalConversions)
        let isPerformanceGood = stats.averageDuration < 0.01 // 10ms以内

        return errorRate < 0.1 && isPerformanceGood
    }

    /// 获取状态分布
    var stateDistribution: [String: Double] {
        guard stats.totalConversions > 0 else { return [:] }

        let total = Double(stats.totalConversions)
        return [
            "loading": Double(stats.loadingStates) / total,
            "success": Double(stats.successStates) / total,
            "error": Double(stats.errorStates) / total,
            "empty": Double(stats.emptyStates) / total,
            "unknown": Double(stats.unknownStates) / total,
        ]
    }

    /// 预测下一个状态类型（基于历史模式）
    var predictedNextStateType: String {
        let distribution = stateDistribution
        let maxType = distribution.max { $0.value < $1.value }
        return maxType?.key ?? "unknown"
    }
}
