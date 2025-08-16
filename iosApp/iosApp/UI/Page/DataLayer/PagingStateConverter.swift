import Foundation
import shared

extension Notification.Name {
    static let sensitiveContentSettingsChanged = Notification.Name("sensitiveContentSettingsChanged")
}

class PagingStateConverter {
    /// 转换队列
    private let conversionQueue = DispatchQueue(label: "timeline.state.converter", qos: .userInitiated)

    private var convertedItems: [TimelineItem] = []

    private var topSignature: String = ""

    init() {
        setupNotificationObservers()
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

            case is PagingStateEmpty<UiTimeline>:
                FlareLog.debug("[PagingStateConverter] 处理Empty状态")
                result = .empty

            case let successState as PagingStateSuccess<UiTimeline>:
                FlareLog.debug("[PagingStateConverter] 处理Success状态，itemCount: \(successState.itemCount), isRefreshing: \(successState.isRefreshing)")
                result = convertSuccessState(successState)

            default:
                FlareLog.warning("PagingStateConverter Unknown PagingState type: \(type(of: pagingState))")
                result = .loading
            }

            FlareLog.debug("[PagingStateConverter] 转换完成，结果类型: \(type(of: result))")

            return result
        }
    }

    /// 重置转换器状态（用于刷新或切换数据源）
    func reset() {
        conversionQueue.sync {
            convertedItems.removeAll()
            // 保留topSignature用于后续比较，不重置
            FlareLog.debug("[PagingStateConverter] 状态已重置，保留topSignature: \(topSignature)")
        }
    }

    /// 转换成功状态 - 极简转换策略
    /// - Parameter successState: KMP的成功状态
    /// - Returns: 转换后的FlareTimelineState
    private func convertSuccessState(_ successState: PagingStateSuccess<UiTimeline>) -> FlareTimelineState {
        //  极简转换策略：只基于convertedItems状态判断
        FlareLog.debug("[PagingStateConverter] 转换策略判断 - convertedItems.count: \(convertedItems.count), KMP itemCount: \(successState.itemCount)")

        if convertedItems.isEmpty {
            // 首次加载或刷新后 → 全量转换
            FlareLog.debug("[PagingStateConverter] 选择全量转换策略 - convertedItems为空")
            return performConversion(successState, isFullConversion: true)
        } else {
            // 已有数据 → 增量转换
            FlareLog.debug("[PagingStateConverter] 选择增量转换策略 - convertedItems非空")
            return performConversion(successState, isFullConversion: false)
        }
    }

    private func performConversion(_ successState: PagingStateSuccess<UiTimeline>, isFullConversion: Bool) -> FlareTimelineState {
        let kmpTotalCount = Int(successState.itemCount)

        // 简化数据获取：直接使用KMP报告的总数据量
        let maxConvertibleIndex = kmpTotalCount

        var newItems: [TimelineItem] = []

        if isFullConversion {
            // 新增：topSignature检查逻辑
            let newSignature = calculateTopSignature(successState, count: 10)

            if newSignature == topSignature {
                // 新增：检测到缓存数据，使用临时数据显示
                FlareLog.debug("[PagingStateConverter] 检测到缓存数据，保持显示")

                let tempItems = convertItemsInRange(
                    from: 0,
                    to: maxConvertibleIndex,
                    successState: successState
                )

                return generateFilteredState(successState, items: tempItems)
            } else {
                FlareLog.debug("[PagingStateConverter] 检测到新数据，执行全量转换")
                FlareLog.debug("[PagingStateConverter] 旧signature: \(topSignature)")
                FlareLog.debug("[PagingStateConverter] 新signature: \(newSignature)")
            }

            // 新增：检测到新数据，执行全量转换
            FlareLog.debug("[PagingStateConverter] 检测到新数据，执行全量转换")

            // 添加日志：全量转换开始
            FlareLog.debug("[PagingStateConverter] 开始全量转换 - 清空缓存，当前缓存数量: \(convertedItems.count)")
            FlareLog.debug("[PagingStateConverter] 全量转换: 转换 0 到 \(maxConvertibleIndex)")

            // 重置缓存
            convertedItems.removeAll()

            // 执行全量转换
            if maxConvertibleIndex > 0 {
                let allItems = convertItemsInRange(
                    from: 0,
                    to: maxConvertibleIndex,
                    successState: successState
                )

                convertedItems = allItems

                if !allItems.isEmpty {
                    // 修复：返回所有items，不要截断
                    newItems = allItems
                    FlareLog.debug("[PagingStateConverter] 全量转换完成 - 返回所有items: \(allItems.count)")
                }
            }

            // 新增：更新topSignature
            topSignature = newSignature
        } else {
            // 执行增量转换：从当前缓存大小开始转换新数据
            let startIndex = convertedItems.count
            var incrementalItems: [TimelineItem] = []

            if maxConvertibleIndex > startIndex {
                // 正常增量转换：有新数据需要加载
                FlareLog.debug("[PagingStateConverter] 正常增量转换 - startIndex: \(startIndex), maxIndex: \(maxConvertibleIndex)")

                let updatedItems = findUpdatedItems(successState: successState)

                incrementalItems = convertItemsInRange(
                    from: startIndex,
                    to: maxConvertibleIndex,
                    successState: successState
                )

                updateExistingItems(with: updatedItems)

                FlareLog.debug("[PagingStateConverter] 正常增量转换完成: 更新 \(updatedItems.count) 个items, 新增 \(incrementalItems.count) 个items")

            } else {
                // 异常情况：数据倒退，需要智能去重处理
                FlareLog.debug("[PagingStateConverter] 数据倒退情况 - 当前缓存: \(startIndex), KMP总数: \(maxConvertibleIndex)")

                // 获取KMP返回的所有数据
                let kmpItems = convertItemsInRange(
                    from: 0,
                    to: maxConvertibleIndex,
                    successState: successState
                )

                FlareLog.debug("[PagingStateConverter] 获取KMP数据完成: \(kmpItems.count) 个items")

                let (updatedItems, newItemsToAdd) = processIntelligentDeduplication(
                    kmpItems: kmpItems,
                    existingItems: convertedItems
                )

                //   更新
                updateExistingItems(with: updatedItems)

                //  新增
                incrementalItems = newItemsToAdd

                FlareLog.debug("[PagingStateConverter] 智能去重处理完成 - 原始: \(kmpItems.count), 更新: \(updatedItems.count), 新增: \(newItemsToAdd.count)")

                let duplicateCount = kmpItems.count - updatedItems.count - newItemsToAdd.count
                if duplicateCount > 0 {
                    FlareLog.debug("[PagingStateConverter] 过滤了 \(duplicateCount) 个无变化的数据")
                }
            }

            // 统一处理：将增量数据追加到现有数组
            if !incrementalItems.isEmpty {
                convertedItems.append(contentsOf: incrementalItems)
                newItems = incrementalItems
                FlareLog.debug("[PagingStateConverter] 增量转换完成 - 新增: \(incrementalItems.count), 缓存总数: \(convertedItems.count)")
            } else {
                // 没有新数据的情况
                if maxConvertibleIndex <= startIndex {
                    FlareLog.debug("[PagingStateConverter] 数据倒退但无新数据，当前缓存: \(startIndex), KMP总数: \(maxConvertibleIndex)")
                } else {
                    FlareLog.debug("[PagingStateConverter] 正常情况但无新数据，可能存在转换问题")
                }

                // 确保newItems为空数组
                newItems = []
            }
        }

        if !newItems.isEmpty {
            DispatchQueue.global(qos: .utility).async {
                FlareLog.debug("[PagingStateConverter] 触发图片预取，新数据数量: \(newItems.count)")
                TimelineImagePrefetcher.shared.smartPrefetchDiskImages(timelineItems: newItems)
            }
        }

        // 应用过滤并生成最终状态
        return generateFilteredState(successState)
    }

    // 修改：统一的generateFilteredState方法，增加可选items参数
    private func generateFilteredState(_ successState: PagingStateSuccess<UiTimeline>, items: [TimelineItem]? = nil) -> FlareTimelineState {
        // 添加日志：最终状态生成
        let sourceItems = items ?? convertedItems
        FlareLog.debug("[PagingStateConverter] generateFilteredState开始 - 源items: \(sourceItems.count)")

        // 应用敏感内容过滤
        let filteredItems = applyContentFiltering(sourceItems)
        FlareLog.debug("[PagingStateConverter] 内容过滤完成 - 过滤前: \(sourceItems.count), 过滤后: \(filteredItems.count)")

        // 生成最终状态（过滤不影响hasMore判断，基于KMP原始数据）
        let appendState = successState.appendState
        let appendStateDescription = String(describing: appendState)
        let hasMore = !(appendStateDescription.contains("NotLoading") && appendStateDescription.contains("endOfPaginationReached=true"))

        //  添加日志：hasMore判断详情
        FlareLog.debug("[PagingStateConverter] hasMore判断详情 - appendState: \(appendStateDescription)")
        FlareLog.debug("[PagingStateConverter] hasMore判断: AppendState(\(hasMore))")
        FlareLog.debug("[PagingStateConverter] 最终状态 - hasMore: \(hasMore)")

        if filteredItems.isEmpty {
            FlareLog.debug("[PagingStateConverter] 返回空状态")
            return .empty
        }

        FlareLog.debug("[PagingStateConverter] 返回loaded状态 - items: \(filteredItems.count), hasMore: \(hasMore)")
        return .loaded(items: filteredItems, hasMore: hasMore)
    }

    // 新增：计算topSignature方法
    private func calculateTopSignature(_ successState: PagingStateSuccess<UiTimeline>, count: Int) -> String {
        var ids: [String] = []
        let maxCount = min(count, Int(successState.itemCount))

        for i in 0 ..< maxCount {
            if let item = safePeek(successState, index: Int32(i)) {
                ids.append(item.itemKey)
            } else {
                FlareLog.warning("PagingStateConverter: safePeek returned nil at index \(i) in calculateTopSignature")
            }
        }

        return ids.joined(separator: "|")
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
            // FlareLog.debug("[PagingStateConverter] 尝试获取index=\(index)的数据")

            let uiTimeline: UiTimeline?

            if let peekedItem = safePeek(successState, index: Int32(index)) {
                uiTimeline = peekedItem
            } else {
                FlareLog.warning("PagingStateConverter: safePeek returned nil at index \(index) in convertItemsInRange")
                continue
                // break
//                FlareLog.debug("[PagingStateConverter] peek失败，尝试get获取index=\(index)的数据")
                // 如果peek失败，使用get（可能触发加载）
//                uiTimeline = successState.get(index: Int32(index))
            }

            if let timeline = uiTimeline {
                let timelineItem = TimelineItem.from(timeline)
                items.append(timelineItem)
                // FlareLog.debug("[PagingStateConverter] 成功转换index=\(index)的数据，当前items数量: \(items.count)")
            } else {
                FlareLog.warning("PagingStateConverter Failed to get item at index \(index)")
                // 遇到nil时停止转换，避免空洞
                break
            }
        }

        FlareLog.debug("[PagingStateConverter] 范围转换完成，总共获得\(items.count)个items")
        return items
    }

    private func safePeek(_ successState: PagingStateSuccess<UiTimeline>, index: Int32) -> UiTimeline? {
        guard index >= 0, index < successState.itemCount else {
            FlareLog.warning("PagingStateConverter: Index \(index) out of bounds (itemCount: \(successState.itemCount))")
            return nil
        }

        let result = successState.peek(index: index)

        if result == nil {
            FlareLog.debug("PagingStateConverter: peek returned nil at index \(index)")
        }

        return result
    }

    private func processIntelligentDeduplication(
        kmpItems: [TimelineItem],
        existingItems: [TimelineItem]
    ) -> ([TimelineItem], [TimelineItem]) {
        FlareLog.debug("[PagingStateConverter] 开始智能去重 - KMP数据: \(kmpItems.count), 缓存数据: \(existingItems.count)")

//        let existingItemsMap = Dictionary(uniqueKeysWithValues: existingItems.map { ($0.id, $0) })

        // crash
        var existingItemsMap: [String: TimelineItem] = [:]
        for item in existingItems {
            if existingItemsMap[item.id] != nil {
                FlareLog.warning("[PagingStateConverter] 发现重复ID   后面的覆盖前面的 existingItems: \(item.id)")
            }
            existingItemsMap[item.id] = item
        }

        var updatedItems: [TimelineItem] = []
        var newItems: [TimelineItem] = []

        for kmpItem in kmpItems {
            if let existingItem = existingItemsMap[kmpItem.id] {
                if hasOperationStateChanged(existing: existingItem, new: kmpItem) {
                    FlareLog.debug("[PagingStateConverter] 检测到状态变化 - ID: \(kmpItem.id)")
                    logStateChanges(existing: existingItem, new: kmpItem)
                    updatedItems.append(kmpItem) // 状态有变化，用于更新
                } else {
                    FlareLog.debug("[PagingStateConverter] 检测到状态无变化 - ID: \(kmpItem.id)")
                }
            } else {
                FlareLog.debug("[PagingStateConverter] 发现新item - ID: \(kmpItem.id)")
                newItems.append(kmpItem)
            }
        }

        FlareLog.debug("[PagingStateConverter] 智能去重完成 - 更新items: \(updatedItems.count), 新增items: \(newItems.count)")
        return (updatedItems, newItems)
    }

    private func findUpdatedItems(
        successState: PagingStateSuccess<UiTimeline>
    ) -> [TimelineItem] {
        var existingItemsMap: [String: TimelineItem] = [:]
        for item in convertedItems {
            if existingItemsMap[item.id] != nil {
                FlareLog.warning("[PagingStateConverter] findUpdatedItems - 发现重复ID: \(item.id)")
            }
            existingItemsMap[item.id] = item
        }

        FlareLog.debug("[PagingStateConverter] findUpdatedItems - 开始遍历KMP数据，总数: \(successState.itemCount)")

        var updatedItems: [TimelineItem] = []
        for index in 0 ..< successState.itemCount {
            if let uiTimeline = safePeek(successState, index: Int32(index)) {
                let kmpItem = TimelineItem.from(uiTimeline)

                if let existingItem = existingItemsMap[kmpItem.id] {
                    if hasOperationStateChanged(existing: existingItem, new: kmpItem) {
                        updatedItems.append(kmpItem)
                        FlareLog.debug("[PagingStateConverter] findUpdatedItems - 发现需要更新的item: \(kmpItem.id)")
                    }
                }
            }
        }

        FlareLog.debug("[PagingStateConverter] findUpdatedItems - 完成，找到 \(updatedItems.count) 个需要更新的items")
        return updatedItems
    }

    private func hasOperationStateChanged(existing: TimelineItem, new: TimelineItem) -> Bool {
        existing.likeCount != new.likeCount ||
            existing.isLiked != new.isLiked ||
            existing.retweetCount != new.retweetCount ||
            existing.isRetweeted != new.isRetweeted ||
            existing.bookmarkCount != new.bookmarkCount ||
            existing.isBookmarked != new.isBookmarked
    }

    private func logStateChanges(existing: TimelineItem, new: TimelineItem) {
        if existing.likeCount != new.likeCount || existing.isLiked != new.isLiked {
            FlareLog.debug("[PagingStateConverter] Like状态变化: \(existing.likeCount)/\(existing.isLiked) → \(new.likeCount)/\(new.isLiked)")
        }
        if existing.retweetCount != new.retweetCount || existing.isRetweeted != new.isRetweeted {
            FlareLog.debug("[PagingStateConverter] Retweet状态变化: \(existing.retweetCount)/\(existing.isRetweeted) → \(new.retweetCount)/\(new.isRetweeted)")
        }
        if existing.bookmarkCount != new.bookmarkCount || existing.isBookmarked != new.isBookmarked {
            FlareLog.debug("[PagingStateConverter] Bookmark状态变化: \(existing.bookmarkCount)/\(existing.isBookmarked) → \(new.bookmarkCount)/\(new.isBookmarked)")
        }
    }

    private func updateExistingItems(with updatedItems: [TimelineItem]) {
        for updatedItem in updatedItems {
            if let existingIndex = convertedItems.firstIndex(where: { $0.id == updatedItem.id }) {
                convertedItems[existingIndex] = updatedItem
                FlareLog.debug("[PagingStateConverter] 更新convertedItems中的item: \(updatedItem.id)")
            }
        }
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
    }

    @objc private func handleSensitiveContentSettingsChanged() {
        reset()
        DispatchQueue.main.async {
            NotificationCenter.default.post(name: .timelineItemUpdated, object: nil)
        }
    }

    func syncUpdateItem(itemId: String, updatedItem: TimelineItem) {
        FlareLog.debug("🔄 [PagingStateConverter] 开始同步更新convertedItems - itemId: \(itemId)")

        conversionQueue.async { [weak self] in
            guard let self else {
                FlareLog.warning("⚠️ [PagingStateConverter] self已释放，取消同步更新")
                return
            }

            if let index = convertedItems.firstIndex(where: { $0.id == itemId }) {
                let oldItem = convertedItems[index]
                convertedItems[index] = updatedItem

                FlareLog.debug("✅ [PagingStateConverter] 异步同步更新convertedItems成功 - itemId: \(itemId)")
                FlareLog.debug("📊 [PagingStateConverter] 状态对比 - 旧: isLiked=\(oldItem.isLiked), likeCount=\(oldItem.likeCount)")
                FlareLog.debug("📊 [PagingStateConverter] 状态对比 - 新: isLiked=\(updatedItem.isLiked), likeCount=\(updatedItem.likeCount)")
            } else {
                FlareLog.warning("⚠️ [PagingStateConverter] 异步同步更新失败 - 未找到itemId: \(itemId), convertedItems.count: \(convertedItems.count)")
            }
        }
    }
}
