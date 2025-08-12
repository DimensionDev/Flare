import Foundation
import os
import shared

// Timeline加载状态管理类
class TimelineLoadingState {
    // 当前加载中的行
    private(set) var loadingRows: Set<Int> = []
    private let preloadDistance: Int = 10
 
    // 清空加载队列
    func clearLoadingRows() {
        loadingRows.removeAll()
    }

  
    // 检查并触发预加载
    func checkAndTriggerPreload(currentRow: Int, data: PagingState<UiTimeline>) {
        guard case let .success(successData) = onEnum(of: data) else { return }

        // 计算需要预加载的范围
        let startRow = currentRow
        let endRow = min(currentRow + preloadDistance, Int(successData.itemCount) - 1)

        // 确保索引有效
        guard startRow >= 0, endRow >= startRow, endRow < Int(successData.itemCount) else {
            return
        }

        // 触发预加载
        for row in startRow ... endRow where !loadingRows.contains(row) {
            if successData.peek(index: Int32(row)) == nil {
                loadingRows.insert(row)
                os_log("[📔][TimelineLoadingState] 预加载触发get: row = %{public}d", log: .default, type: .debug, row)
                _ = successData.get(index: Int32(row))
            }
        }
    }

    // 处理数据加载
    func handleDataLoading(at row: Int, data: PagingState<UiTimeline>) -> UiTimeline? {
        guard case let .success(successData) = onEnum(of: data) else { return nil }

        do {
            // 先尝试使用peek获取数据
            if let item = successData.peek(index: Int32(row)) {
                loadingRows.remove(row)
                return item
            }
        } catch {
            // 如果peek返回nil，使用get触发加载
            if !loadingRows.contains(row) {
                loadingRows.insert(row)
                os_log("[📔][TimelineLoadingState] cell显示触发get: row = %{public}d", log: .default, type: .debug, row)
                if let item = successData.get(index: Int32(row)) {
                    loadingRows.remove(row)
                    return item
                }
            }
        }

        return nil
    }
}
