import Foundation
import shared

extension Notification.Name {
    static let sensitiveContentSettingsChanged = Notification.Name("sensitiveContentSettingsChanged")
}

class PagingStateConverter {
    /// 转换统计
    private var stats = ConversionStats()

    /// 转换队列
    private let conversionQueue = DispatchQueue(label: "timeline.state.converter", qos: .userInitiated)

    /// 核心追踪变量
    private var nextKmpIndex: Int = 0            // 下一个需要请求的KMP index
    private var lastKmpTotalCount: Int = 0       // 上次KMP报告的总数据量
    private var lastStateSignature: String?     // 上次的状态签名

    /// 缓存变量
    private var convertedItems: [TimelineItem] = []

    init() {
        setupNotificationObservers()
    }

    /// 获取下一个需要请求的KMP index
    func getNextKmpIndex() -> Int {
        return nextKmpIndex
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    /// 将KMP的PagingState转换为Swift的FlareTimelineState
    /// - Parameter pagingState: KMP的PagingState
    /// - Returns: 转换后的FlareTimelineState
    func convert(_ pagingState: PagingState<UiTimeline>) -> FlareTimelineState {
        // 线程安全：使用串行队列同步执行
        conversionQueue.sync {
            stats.totalConversions += 1
            FlareLog.debug("[PagingStateConverter] 开始转换PagingState，类型: \(type(of: pagingState))")

            let result: FlareTimelineState

            switch pagingState {
            case is PagingStateLoading<UiTimeline>:
                FlareLog.debug("[PagingStateConverter] 处理Loading状态")
                result = .loading

            case let errorState as PagingStateError<UiTimeline>:
                FlareLog.debug("[PagingStateConverter] 处理Error状态: \(errorState.error)")
                let flareError = FlareError.from(errorState.error)
                result = .error(flareError)
                stats.errorCount += 1

            case is PagingStateEmpty<UiTimeline>:
                FlareLog.debug("[PagingStateConverter] 处理Empty状态")
                result = .empty

            case let successState as PagingStateSuccess<UiTimeline>:
                FlareLog.debug("[PagingStateConverter] 处理Success状态，itemCount: \(successState.itemCount), isRefreshing: \(successState.isRefreshing)")
                result = convertSuccessState(successState)

            default:
                FlareLog.warning("PagingStateConverter Unknown PagingState type: \(type(of: pagingState))")
                result = .loading
                stats.errorCount += 1
            }

            FlareLog.debug("[PagingStateConverter] 转换完成，结果类型: \(type(of: result))")

            return result
        }
    }

    var statistics: ConversionStats {
        conversionQueue.sync { stats }
    }

    /// 重置转换器状态（用于刷新或切换数据源）
    func reset() {
        conversionQueue.sync {
            resetIncrementalState()
        }
    }

    // MARK: - Private Methods

    /// 转换成功状态 - 使用修复的增量转换策略
    /// - Parameter successState: KMP的成功状态
    /// - Returns: 转换后的FlareTimelineState
    private enum ConversionStrategy {
        case incremental
        case full
        case skip
    }

    private func convertSuccessState(_ successState: PagingStateSuccess<UiTimeline>) -> FlareTimelineState {
        let strategy = determineConversionStrategy(successState)

        switch strategy {
        case .incremental:
            return performIncrementalConversion(successState)
        case .full:
            return performFullConversion(successState)
        case .skip:
            // 跳过转换时，基于当前缓存生成状态
            return generateFilteredState(successState)
        }
    }

    private func determineConversionStrategy(_ successState: PagingStateSuccess<UiTimeline>) -> ConversionStrategy {
        let kmpTotalCount = Int(successState.itemCount)
        let isRefreshing = successState.isRefreshing

        FlareLog.debug("[PagingStateConverter] KMP状态: totalCount=\(kmpTotalCount), lastTotal=\(lastKmpTotalCount), nextKmpIndex=\(nextKmpIndex)")

        // 🚀 新增：Load More强制转换机制 - 检查是否有新的可用数据
        if nextKmpIndex < kmpTotalCount {
            if successState.peek(index: Int32(nextKmpIndex)) != nil {
                FlareLog.debug("[PagingStateConverter] 🎯 检测到新的可用数据，强制增量转换 (index: \(nextKmpIndex))")
                return .incremental
            }
        }

        // 生成基于KMP真实状态的签名（简化版本，避免appendState的复杂性）
        let appendStateType = String(describing: type(of: successState.appendState))
        let currentStateSignature = "\(kmpTotalCount)_\(isRefreshing)_\(appendStateType)"

        FlareLog.debug("[PagingStateConverter] 状态签名: \(currentStateSignature)")

        // 处理刷新场景
        if isRefreshing {
            FlareLog.debug("[PagingStateConverter] 检测到刷新，重置状态")
            resetIncrementalState()
            lastKmpTotalCount = kmpTotalCount
            lastStateSignature = currentStateSignature
            return .full
        }

        // 处理数据减少场景（切换账号等）
        if kmpTotalCount < lastKmpTotalCount {
            FlareLog.debug("[PagingStateConverter] 检测到数据减少，重置状态")
            resetIncrementalState()
            lastKmpTotalCount = kmpTotalCount
            lastStateSignature = currentStateSignature
            return .full
        }

        // 检查状态签名是否变化
        if let lastSignature = lastStateSignature, lastSignature == currentStateSignature {
            FlareLog.debug("[PagingStateConverter] 状态签名未变化: \(currentStateSignature)")
            FlareLog.debug("[PagingStateConverter] === 状态签名分析 ===")
            FlareLog.debug("[PagingStateConverter] 上次签名: \(lastSignature)")
            FlareLog.debug("[PagingStateConverter] 当前签名: \(currentStateSignature)")
            FlareLog.debug("[PagingStateConverter] KMP数据: totalCount=\(kmpTotalCount), nextKmpIndex=\(nextKmpIndex)")
            FlareLog.debug("[PagingStateConverter] 缓存状态: convertedItems=\(convertedItems.count)个")

            // 检查是否有未转换的KMP数据
            if nextKmpIndex < kmpTotalCount {
                FlareLog.warning("[PagingStateConverter] ⚠️ 检测到未转换的KMP数据: nextKmpIndex(\(nextKmpIndex)) < totalCount(\(kmpTotalCount))")
                FlareLog.warning("[PagingStateConverter] 可能需要强制转换，但状态签名阻止了转换")

                // 探测未转换数据的可用性
                FlareLog.debug("[PagingStateConverter] === 探测未转换数据可用性 ===")
                var availableCount = 0
                for i in nextKmpIndex..<min(nextKmpIndex + 5, kmpTotalCount) {
                    if successState.peek(index: Int32(i)) != nil {
                        availableCount += 1
                        FlareLog.debug("[PagingStateConverter] index \(i): 有数据")
                    } else {
                        FlareLog.debug("[PagingStateConverter] index \(i): 无数据")
                    }
                }
                FlareLog.debug("[PagingStateConverter] 未转换范围[\(nextKmpIndex), \(min(nextKmpIndex + 5, kmpTotalCount)))中有\(availableCount)个可用数据")
            }

            return .skip
        }

        // 更新状态签名
        lastStateSignature = currentStateSignature

        // 判断转换策略
        if kmpTotalCount > lastKmpTotalCount {
            FlareLog.debug("[PagingStateConverter] 检测到KMP数据增长: \(lastKmpTotalCount) → \(kmpTotalCount)")
            lastKmpTotalCount = kmpTotalCount

            if convertedItems.isEmpty {
                return .full
            } else {
                return .incremental
            }
        } else {
            FlareLog.debug("[PagingStateConverter] KMP数据量未变化，跳过转换")
            return .skip
        }
    }

    private func performIncrementalConversion(_ successState: PagingStateSuccess<UiTimeline>) -> FlareTimelineState {
        let kmpTotalCount = Int(successState.itemCount)

        // 确定实际可以转换的数据范围
        let actualAvailableCount = determineActualAvailableCount(successState, maxCount: kmpTotalCount)
        let maxConvertibleIndex = min(actualAvailableCount, kmpTotalCount)

        FlareLog.debug("[PagingStateConverter] 增量转换: 从KMP index \(nextKmpIndex) 到 \(maxConvertibleIndex)")

        // 确保有数据需要转换
        guard maxConvertibleIndex > nextKmpIndex else {
            FlareLog.debug("[PagingStateConverter] 无新数据需要转换")
            return generateFilteredState(successState)
        }

        // 执行转换
        let newItems = convertItemsInRange(
            from: nextKmpIndex,
            to: maxConvertibleIndex,
            successState: successState
        )

        FlareLog.debug("[PagingStateConverter] 转换完成: 新增 \(newItems.count) 个items")

        // 更新缓存和追踪变量
        convertedItems.append(contentsOf: newItems)
        nextKmpIndex = maxConvertibleIndex

        FlareLog.debug("[PagingStateConverter] 更新追踪: nextKmpIndex=\(nextKmpIndex), 总缓存=\(convertedItems.count)")

        if !newItems.isEmpty {
            DispatchQueue.global(qos: .utility).async {
                TimelineImagePrefetcher.shared.smartPrefetch(
                    currentIndex: 0,
                    timelineItems: newItems
                )
            }
        }

        // 应用过滤并生成最终状态
        return generateFilteredState(successState)
    }

    private func generateFilteredState(_ successState: PagingStateSuccess<UiTimeline>) -> FlareTimelineState {
        // 应用敏感内容过滤
        let filteredItems = applyContentFiltering(convertedItems)

        FlareLog.debug("[PagingStateConverter] 过滤结果: 原始\(convertedItems.count)个 → 过滤后\(filteredItems.count)个")

        // 生成最终状态（过滤不影响hasMore判断，基于KMP原始数据）
        let hasMore = checkHasMoreData(successState)
        let isRefreshing = successState.isRefreshing

        if filteredItems.isEmpty {
            FlareLog.debug("[PagingStateConverter] 过滤后为空，返回empty状态")
            return .empty
        }

        return .loaded(items: filteredItems, hasMore: hasMore, isRefreshing: isRefreshing)
    }

    private func performFullConversion(_ successState: PagingStateSuccess<UiTimeline>) -> FlareTimelineState {
        let kmpTotalCount = Int(successState.itemCount)
        let actualAvailableCount = determineActualAvailableCount(successState, maxCount: kmpTotalCount)
        let maxConvertibleIndex = min(actualAvailableCount, kmpTotalCount)

        FlareLog.debug("[PagingStateConverter] 全量转换: 转换 0 到 \(maxConvertibleIndex)")

        // 重置缓存
        convertedItems.removeAll()

        // 执行转换
        if maxConvertibleIndex > 0 {
            let allItems = convertItemsInRange(
                from: 0,
                to: maxConvertibleIndex,
                successState: successState
            )

            convertedItems = allItems
            nextKmpIndex = maxConvertibleIndex

            FlareLog.debug("[PagingStateConverter] 全量转换完成: 转换了 \(allItems.count) 个items")
        } else {
            nextKmpIndex = 0
        }

        if !convertedItems.isEmpty {
            DispatchQueue.global(qos: .utility).async {
                TimelineImagePrefetcher.shared.smartPrefetch(
                    currentIndex: 0,
                    timelineItems: self.convertedItems
                )
            }
        }

        // 应用过滤并生成最终状态
        return generateFilteredState(successState)
    }

    private func applyContentFiltering(_ items: [TimelineItem]) -> [TimelineItem] {
        guard shouldFilterSensitiveContent() else {
            return items
        }

        return items.filter { item in
            !shouldHideItem(item)
        }
    }

    private func shouldFilterSensitiveContent() -> Bool {
        guard let appSettings = getAppSettings() else {
            FlareLog.warning("PagingStateConverter: Unable to get AppSettings, skipping filter")
            return false
        }

        return appSettings.appearanceSettings.sensitiveContentSettings.hideInTimeline
    }

    private func shouldHideItem(_ item: TimelineItem) -> Bool {
        guard item.sensitive else {
            return false
        }

        guard let appSettings = getAppSettings() else {
            FlareLog.warning("PagingStateConverter: Unable to get AppSettings, not hiding sensitive content")
            return false
        }

        let settings = appSettings.appearanceSettings.sensitiveContentSettings

        if let timeRange = settings.timeRange, timeRange.isEnabled {
            return timeRange.isCurrentTimeInRange()
        } else {
            return true
        }
    }

    private func getAppSettings() -> AppSettings? {
        AppSettings()
    }

    private func checkHasMoreData(_ successState: PagingStateSuccess<UiTimeline>) -> Bool {
        let kmpTotalCount = Int(successState.itemCount)
        let appendState = successState.appendState
        let appendStateDescription = String(describing: appendState)

        // 基于KMP原始数据判断是否还有更多数据
        let hasMoreFromKmp = nextKmpIndex < kmpTotalCount

        // 基于appendState判断是否还有更多数据
        let hasMoreFromAppendState = !(appendStateDescription.contains("NotLoading") && appendStateDescription.contains("endOfPaginationReached=true"))

        let hasMore = hasMoreFromKmp || hasMoreFromAppendState

        FlareLog.debug("[PagingStateConverter] hasMore判断: KMP(\(hasMoreFromKmp)), AppendState(\(hasMoreFromAppendState)), 最终(\(hasMore))")

        return hasMore
    }

    /// 重置增量转换状态
    private func resetIncrementalState() {
        nextKmpIndex = 0
        lastKmpTotalCount = 0
        convertedItems.removeAll()
        lastStateSignature = nil

        FlareLog.debug("[PagingStateConverter] 状态已重置")
    }

    /// 确定实际可转换的数量
    /// - Parameters:
    ///   - successState: KMP的成功状态
    ///   - maxCount: KMP报告的最大数量
    /// - Returns: 实际可转换的数量
    private func determineActualAvailableCount(_ successState: PagingStateSuccess<UiTimeline>, maxCount: Int) -> Int {
        FlareLog.debug("[PagingStateConverter] === determineActualAvailableCount 开始 ===")
        FlareLog.debug("[PagingStateConverter] 输入参数: maxCount=\(maxCount), nextKmpIndex=\(nextKmpIndex)")

        // 使用批量转换策略，避免一次性转换过多数据
        let batchSize = 20
        let maxBatchesToCheck = 5 // 最多检查5个批次

        var actualCount = nextKmpIndex
        FlareLog.debug("[PagingStateConverter] 初始actualCount: \(actualCount)")

        // 从当前已转换位置开始，按批次探测可用数据
        for batchIndex in 0 ..< maxBatchesToCheck {
            let batchStart = nextKmpIndex + (batchIndex * batchSize)
            let batchEnd = min(batchStart + batchSize, maxCount)

            FlareLog.debug("[PagingStateConverter] 检查批次\(batchIndex): [\(batchStart), \(batchEnd))")

            if batchStart >= maxCount {
                FlareLog.debug("[PagingStateConverter] 批次起始位置超出范围，停止检查")
                break
            }

            // 检查这个批次的第一个和最后一个item
            let firstAvailable = successState.peek(index: Int32(batchStart)) != nil
            let lastAvailable = successState.peek(index: Int32(batchEnd - 1)) != nil

            FlareLog.debug("[PagingStateConverter] 批次\(batchIndex)检查结果: first(\(batchStart))=\(firstAvailable), last(\(batchEnd - 1))=\(lastAvailable)")

            if firstAvailable, lastAvailable {
                actualCount = batchEnd
                FlareLog.debug("[PagingStateConverter] 批次\(batchIndex)完全可用，更新actualCount: \(actualCount)")
            } else if firstAvailable {
                // 如果第一个可用但最后一个不可用，需要精确查找边界
                FlareLog.debug("[PagingStateConverter] 批次\(batchIndex)部分可用，精确查找边界")
                for i in batchStart..<batchEnd {
                    if successState.peek(index: Int32(i)) != nil {
                        actualCount = i + 1
                        FlareLog.debug("[PagingStateConverter] 精确边界: index \(i) 可用，actualCount=\(actualCount)")
                    } else {
                        FlareLog.debug("[PagingStateConverter] 精确边界: index \(i) 不可用，停止")
                        break
                    }
                }
                break
            } else {
                // 如果第一个都不可用，停止检查
                FlareLog.debug("[PagingStateConverter] 批次\(batchIndex)第一个数据不可用，停止检查")
                break
            }
        }

        FlareLog.debug("[PagingStateConverter] === determineActualAvailableCount 结果 ===")
        FlareLog.debug("[PagingStateConverter] 最终actualCount: \(actualCount)")
        FlareLog.debug("[PagingStateConverter] 相比nextKmpIndex(\(nextKmpIndex))增加了: \(actualCount - nextKmpIndex)个")

        return actualCount
    }

    /// 智能重复检测 - 允许数量增加的情况
    /// - Parameter currentSignature: 当前状态签名
    /// - Returns: 是否应该跳过转换
    // private func shouldSkipConversion(_ currentSignature: String) -> Bool {
    //     guard let lastSignature = lastStateSignature else { return false }

    //     // 如果签名完全相同，跳过
    //     if lastSignature == currentSignature {
    //         return true
    //     }

    //     // 解析签名组件
    //     let lastComponents = lastSignature.split(separator: "_")
    //     let currentComponents = currentSignature.split(separator: "_")

    //     if lastComponents.count >= 1, currentComponents.count >= 1 {
    //         let lastCount = Int(lastComponents[0]) ?? 0
    //         let currentCount = Int(currentComponents[0]) ?? 0

    //         // 如果数量增加，说明有新数据，不跳过
    //         if currentCount > lastCount {
    //             return false
    //         }
    //     }

    //     return false
    // }

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

        FlareLog.debug("[PagingStateConverter] 开始转换范围: [\(startIndex), \(endIndex))")

        for index in startIndex ..< endIndex {
            FlareLog.debug("[PagingStateConverter] 尝试获取index=\(index)的数据")

            let uiTimeline: UiTimeline?

            // 首先尝试peek（不触发加载）
            if let peekedItem = successState.peek(index: Int32(index)) {
                FlareLog.debug("[PagingStateConverter] peek成功获取index=\(index)的数据")
                uiTimeline = peekedItem
            } else {
                FlareLog.debug("[PagingStateConverter] peek失败，尝试get获取index=\(index)的数据")
                // 如果peek失败，使用get（可能触发加载）
                uiTimeline = successState.get(index: Int32(index))
            }

            if let timeline = uiTimeline {
                let timelineItem = TimelineItem.from(timeline)
                items.append(timelineItem)
                FlareLog.debug("[PagingStateConverter] 成功转换index=\(index)的数据，当前items数量: \(items.count)")
            } else {
                FlareLog.warning("PagingStateConverter Failed to get item at index \(index)")
                // 遇到nil时停止转换，避免空洞
                break
            }
        }

        FlareLog.debug("[PagingStateConverter] 范围转换完成，总共获得\(items.count)个items")
        return items
    }

    /// 智能状态比较，避免不必要的UI更新
    /// - Parameters:
    ///   - oldState: 旧状态
    ///   - newState: 新状态
    /// - Returns: 是否需要更新UI
    // private func shouldUpdateUI(from oldState: FlareTimelineState, to newState: FlareTimelineState) -> Bool {
    //     // 使用FlareTimelineState的内置比较方法
    //     newState.needsUIUpdate(from: oldState)
    // }
}

// MARK: - ConversionStats

/// 转换统计信息
struct ConversionStats {
    var totalConversions: Int = 0
    var errorCount: Int = 0
}

// MARK: - PagingStateConverter Extensions

extension PagingStateConverter {
    private func setupNotificationObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleSensitiveContentSettingsChanged),
            name: .sensitiveContentSettingsChanged,
            object: nil
        )

    }

    @objc private func handleSensitiveContentSettingsChanged() {
        reset()
        DispatchQueue.main.async {
            NotificationCenter.default.post(name: .timelineItemUpdated, object: nil)
        }
    }
}
