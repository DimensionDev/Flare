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

                incrementalItems = convertItemsInRange(
                    from: startIndex,
                    to: maxConvertibleIndex,
                    successState: successState
                )

                FlareLog.debug("[PagingStateConverter] 正常增量转换完成: 新增 \(incrementalItems.count) 个items")

            } else {
                // 异常情况：数据倒退，需要去重处理
                FlareLog.debug("[PagingStateConverter] 数据倒退情况 - 当前缓存: \(startIndex), KMP总数: \(maxConvertibleIndex)")

                // 获取KMP返回的所有数据
                let kmpItems = convertItemsInRange(
                    from: 0,
                    to: maxConvertibleIndex,
                    successState: successState
                )

                FlareLog.debug("[PagingStateConverter] 获取KMP数据完成: \(kmpItems.count) 个items")

                // 去重处理：只保留不在现有数组中的数据
                let existingKeys = Set(convertedItems.map(\.id))
                incrementalItems = kmpItems.filter { !existingKeys.contains($0.id) }

                FlareLog.debug("[PagingStateConverter] 去重处理完成 - 原始: \(kmpItems.count), 去重后: \(incrementalItems.count)")

                // 额外日志：记录被过滤的重复数据
                let duplicateCount = kmpItems.count - incrementalItems.count
                if duplicateCount > 0 {
                    FlareLog.debug("[PagingStateConverter] 过滤了 \(duplicateCount) 个重复数据")
                }

                // 记录新增数据的详细信息（仅前3个）
                for (index, item) in incrementalItems.prefix(3).enumerated() {
                    FlareLog.debug("[PagingStateConverter] 新增数据[\(index)]: \(item.id)")
                }
            }

            // 统一处理：将增量数据追加到现有数组
            if !incrementalItems.isEmpty {
                FlareLog.debug("[PagingStateConverter] 开始追加增量数据 - 追加前总数: \(convertedItems.count)")

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
            if let item = successState.peek(index: Int32(i)) {
                ids.append(item.itemKey)
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
            FlareLog.debug("[PagingStateConverter] 尝试获取index=\(index)的数据")

            let uiTimeline: UiTimeline?

            // 首先尝试peek（不触发加载）
            if let peekedItem = successState.peek(index: Int32(index)) {
                FlareLog.debug("[PagingStateConverter] peek成功获取index=\(index)的数据")
                uiTimeline = peekedItem
            } else {
                continue
                // break
//                FlareLog.debug("[PagingStateConverter] peek失败，尝试get获取index=\(index)的数据")
                // 如果peek失败，使用get（可能触发加载）
//                uiTimeline = successState.get(index: Int32(index))
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
}
