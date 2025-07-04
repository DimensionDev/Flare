import Foundation
import shared

extension Notification.Name {
    static let sensitiveContentSettingsChanged = Notification.Name("sensitiveContentSettingsChanged")
}

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
        FlareLog.debug("PagingStateConverter Initialized")
        setupNotificationObservers()
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    /// 将KMP的PagingState转换为Swift的FlareTimelineState
    /// - Parameter pagingState: KMP的PagingState
    /// - Returns: 转换后的FlareTimelineState
    func convert(_ pagingState: PagingState<UiTimeline>) -> FlareTimelineState {
        // 🔥 线程安全修复：使用串行队列同步执行，确保所有共享状态访问都是线程安全的
        conversionQueue.sync {
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
                FlareLog.warning("PagingStateConverter Unknown PagingState type: \(type(of: pagingState))")
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
                FlareLog.debug("PagingStateConverter State changed: \(result.changesSummary(from: lastState))")
            }

            return result
        }
    }

    var statistics: ConversionStats {
        // 🔥 线程安全修复：读取统计信息也需要同步保护
        conversionQueue.sync {
            stats
        }
    }

    /// 重置统计信息
    func resetStatistics() {
        // 🔥 线程安全修复：重置操作需要同步保护
        conversionQueue.sync {
            stats = ConversionStats()
            FlareLog.debug("PagingStateConverter Statistics reset")
        }
    }

    /// 重置转换器状态（用于刷新或切换数据源）
    func reset() {
        // 🔥 线程安全修复：重置操作需要同步保护
        conversionQueue.sync {
            resetIncrementalState()
            lastConvertedState = nil
            FlareLog.debug("PagingStateConverter Converter reset")
        }
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

        // 确定实际可转换的数量
        let actualAvailableCount = determineActualAvailableCount(successState, maxCount: kmpItemCount)

        // 生成状态签名 - 使用实际可用数量
        let currentStateSignature = "\(actualAvailableCount)_\(isRefreshing)_\(successState.appendState)"

        // 🔥 调试日志：分析分页流的数据状态
        FlareLog.debug("PagingStateConverter === convertSuccessState 开始分析 ===")
        FlareLog.debug("PagingStateConverter KMP reports: \(kmpItemCount), Actually available: \(actualAvailableCount)")
        FlareLog.debug("PagingStateConverter isRefreshing: \(isRefreshing), hasMore: \(hasMore)")
        FlareLog.debug("PagingStateConverter 上次转换的items数量: \(lastConvertedItemCount)")
        FlareLog.debug("PagingStateConverter 当前已转换items数量: \(convertedItems.count)")

        // 检查是否需要重置（刷新或数据减少）
        if isRefreshing || actualAvailableCount < lastConvertedItemCount {
            FlareLog.debug("PagingStateConverter Resetting incremental state - refreshing: \(isRefreshing), available: \(actualAvailableCount) < converted: \(lastConvertedItemCount)")
            resetIncrementalState()
        }

        // 智能重复检测 - 允许数量增加的情况
        if !shouldSkipConversion(currentStateSignature) {
            // 增量转换新数据
            if actualAvailableCount > lastConvertedItemCount {
                let newItemsStartIndex = lastConvertedItemCount
                let newItemsCount = actualAvailableCount - lastConvertedItemCount

                FlareLog.debug("PagingStateConverter === 增量转换分析 ===")
                FlareLog.debug("PagingStateConverter Converting new items: \(lastConvertedItemCount) -> \(actualAvailableCount)")
                FlareLog.debug("PagingStateConverter 新增items起始索引: \(newItemsStartIndex)")
                FlareLog.debug("PagingStateConverter 新增items数量: \(newItemsCount)")

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

                FlareLog.debug("PagingStateConverter Added \(newItems.count) new items, total: \(convertedItems.count)")

                // 🔥 智能预取：只预取新增的数据
                if !newItems.isEmpty {
                    FlareLog.debug("PagingStateConverter === 预取策略分析（增量） ===")
                    FlareLog.debug("PagingStateConverter 预取新增items: \(newItems.count)个")
                    FlareLog.debug("PagingStateConverter 新增items ID范围: \(newItems.first?.id ?? "nil") ~ \(newItems.last?.id ?? "nil")")
                    FlareLog.debug("PagingStateConverter 预取currentIndex设为0（新数据中的起始位置）")

                    DispatchQueue.global(qos: .utility).async {
                        TimelineImagePrefetcher.shared.smartPrefetch(
                            currentIndex: 0, // 🔥 修复：在新数据中从0开始预取
                            timelineItems: newItems
                        )
                    }
                }
            } else if actualAvailableCount == lastConvertedItemCount, convertedItems.isEmpty {
                // 首次转换或状态重置后的完整转换
                FlareLog.debug("PagingStateConverter === 首次转换分析 ===")
                FlareLog.debug("PagingStateConverter Initial full conversion for \(actualAvailableCount) items")

                convertedItems = convertItemsInRange(
                    from: 0,
                    to: actualAvailableCount,
                    successState: successState
                )
                lastConvertedItemCount = actualAvailableCount

                // 更新统计
                stats.totalItemsConverted += convertedItems.count
                stats.averageItemsPerConversion = Double(stats.totalItemsConverted) / Double(stats.successStates)

                FlareLog.debug("PagingStateConverter Initial conversion completed: \(convertedItems.count) items")

                // 🔥 智能预取：首次加载从0开始预取
                if !convertedItems.isEmpty {
                    FlareLog.debug("PagingStateConverter === 预取策略分析（首次） ===")
                    FlareLog.debug("PagingStateConverter 预取首次加载items: \(convertedItems.count)个")
                    FlareLog.debug("PagingStateConverter 首次items ID范围: \(convertedItems.first?.id ?? "nil") ~ \(convertedItems.last?.id ?? "nil")")
                    FlareLog.debug("PagingStateConverter 预取currentIndex设为0（首次加载）")

                    DispatchQueue.global(qos: .utility).async {
                        TimelineImagePrefetcher.shared.smartPrefetch(
                            currentIndex: 0,
                            timelineItems: self.convertedItems
                        )
                    }
                }
            }
        } else {
            FlareLog.debug("PagingStateConverter Skipping duplicate conversion - signature: \(currentStateSignature)")
        }

        // 更新状态签名
        lastStateSignature = currentStateSignature

        if convertedItems.isEmpty {
            return .empty
        }

        //   sensitive
        let originalCount = convertedItems.count
        convertedItems = TimelineContentFilter.filterSensitiveContent(convertedItems)
        let filteredCount = originalCount - convertedItems.count

        if filteredCount > 0 {
            FlareLog.debug("PagingStateConverter 敏感内容过滤: 原始\(originalCount)项 -> 过滤\(filteredCount)项 -> 剩余\(convertedItems.count)项")
        }

        if convertedItems.isEmpty {
            return .empty
        }

        // 🔥 预取逻辑已移到增量转换和首次转换的具体分支中，避免重复预取
        FlareLog.debug("PagingStateConverter === convertSuccessState 分析结束 ===")

        return .loaded(items: convertedItems, hasMore: hasMore, isRefreshing: isRefreshing)
    }

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
        FlareLog.debug("PagingStateConverter Incremental state reset")
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

        FlareLog.debug("PagingStateConverter Determined actual count: \(actualCount) (KMP reported: \(maxCount))")
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
                FlareLog.debug("PagingStateConverter Count increased: \(lastCount) -> \(currentCount), allowing conversion")
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

        FlareLog.debug("PagingStateConverter Converting range [\(startIndex), \(endIndex))")

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
                FlareLog.warning("PagingStateConverter Failed to get item at index \(index)")
                // 遇到nil时停止转换，避免空洞
                break
            }
        }

        FlareLog.debug("PagingStateConverter Converted \(items.count) items in range [\(startIndex), \(endIndex))")
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

extension PagingStateConverter {
    private func setupNotificationObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleSensitiveContentSettingsChanged),
            name: .sensitiveContentSettingsChanged,
            object: nil
        )
        FlareLog.debug("PagingStateConverter 已设置敏感内容设置变更监听器")
    }

    @objc private func handleSensitiveContentSettingsChanged() {
        FlareLog.debug("PagingStateConverter 收到敏感内容设置变更通知，重置转换器状态")

        // 🔥 线程安全修复：reset()方法已经包含了同步保护，直接调用即可
        reset()

        DispatchQueue.main.async {
            NotificationCenter.default.post(name: .timelineItemUpdated, object: nil)
        }
    }
}
