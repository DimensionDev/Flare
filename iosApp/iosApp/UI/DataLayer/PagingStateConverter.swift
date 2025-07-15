import Foundation
import shared

extension Notification.Name {
    static let sensitiveContentSettingsChanged = Notification.Name("sensitiveContentSettingsChanged")
}

class PagingStateConverter {


    /// 转换队列
    private let conversionQueue = DispatchQueue(label: "timeline.state.converter", qos: .userInitiated)

    /// 缓存变量
    private var convertedItems: [TimelineItem] = []

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
            FlareLog.debug("[PagingStateConverter] 状态已重置")
        }
    }

    /// 转换成功状态 - 简化的转换策略
    /// - Parameter successState: KMP的成功状态
    /// - Returns: 转换后的FlareTimelineState
    private func convertSuccessState(_ successState: PagingStateSuccess<UiTimeline>) -> FlareTimelineState {
        let isRefreshing = successState.isRefreshing

        // 简化的转换策略：刷新时全量转换，否则增量转换
        if isRefreshing || convertedItems.isEmpty {
            return performConversion(successState, isFullConversion: true)
        } else {
            return performConversion(successState, isFullConversion: false)
        }
    }

    private func performConversion(_ successState: PagingStateSuccess<UiTimeline>, isFullConversion: Bool) -> FlareTimelineState {
        let kmpTotalCount = Int(successState.itemCount)

        // 简化数据获取：直接使用KMP报告的总数据量
        let maxConvertibleIndex = kmpTotalCount

        if isFullConversion {
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
                FlareLog.debug("[PagingStateConverter] 全量转换完成: 转换了 \(allItems.count) 个items")
            }
        } else {
            // 执行增量转换：从当前缓存大小开始转换新数据
            let startIndex = convertedItems.count
            guard maxConvertibleIndex > startIndex else {
                FlareLog.debug("[PagingStateConverter] 无新数据需要转换")
                return generateFilteredState(successState)
            }

            let newItems = convertItemsInRange(
                from: startIndex,
                to: maxConvertibleIndex,
                successState: successState
            )

            FlareLog.debug("[PagingStateConverter] 增量转换完成: 新增 \(newItems.count) 个items")

            // 更新缓存
            convertedItems.append(contentsOf: newItems)
        }

        // 触发图片预取
//        if !convertedItems.isEmpty {
//            DispatchQueue.global(qos: .utility).async {
//                TimelineImagePrefetcher.shared.smartPrefetch(
//                    currentIndex: 0,
//                    timelineItems: self.convertedItems
//                )
//            }
//        }

        // 应用过滤并生成最终状态
        return generateFilteredState(successState)
    }

    private func generateFilteredState(_ successState: PagingStateSuccess<UiTimeline>) -> FlareTimelineState {
        // 应用敏感内容过滤
        let filteredItems = applyContentFiltering(convertedItems)

        // 生成最终状态（过滤不影响hasMore判断，基于KMP原始数据）
        let appendState = successState.appendState
        let appendStateDescription = String(describing: appendState)
        let hasMore = !(appendStateDescription.contains("NotLoading") && appendStateDescription.contains("endOfPaginationReached=true"))
        let isRefreshing = successState.isRefreshing

        FlareLog.debug("[PagingStateConverter] hasMore判断: AppendState(\(hasMore))")

        if filteredItems.isEmpty {
            return .empty
        }

        return .loaded(items: filteredItems, hasMore: hasMore, isRefreshing: isRefreshing)
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
            //FlareLog.debug("[PagingStateConverter] 尝试获取index=\(index)的数据")

            let uiTimeline: UiTimeline?

            // 首先尝试peek（不触发加载）
            if let peekedItem = successState.peek(index: Int32(index)) {
                FlareLog.debug("[PagingStateConverter] peek成功获取index=\(index)的数据")
                uiTimeline = peekedItem
            }
            else {
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
