import Foundation
import os
import shared
import SwiftUI

// 列表标签管理器
class ListTabManager {
    private let logger = Logger(subsystem: "com.flare.app", category: "ListTabManager")

    // 专门用于移动列表标签的方法
    func moveListTab(
        from source: IndexSet,
        to destination: Int,
        availableAppBarTabsItems: [FLTabItem],
        moveTabHandler: (IndexSet, Int) -> Void
    ) {
        logger.debug("moveListTab调用: source=\(source), destination=\(destination)")

        // 获取所有列表标签
        let listTabs = availableAppBarTabsItems.filter { $0.key.starts(with: "list_") }

        guard let sourceIndex = source.first, sourceIndex < listTabs.count else {
            logger.debug("moveListTab: 无效的源索引")
            return
        }

        // 获取要移动的标签
        let movedTab = listTabs[sourceIndex]
        logger.debug("moveListTab: 移动标签 \(movedTab.key)")

        // 找到这些标签在完整列表中的索引
        let fullListIndices = listTabs.compactMap { tab in
            availableAppBarTabsItems.firstIndex(where: { $0.key == tab.key })
        }

        // 确保索引有效
        guard sourceIndex < fullListIndices.count else {
            logger.debug("moveListTab: 源索引超出范围")
            return
        }

        // 计算目标位置在完整列表中的索引
        let fullSourceIndex = fullListIndices[sourceIndex]

        // 计算目标索引
        var fullDestIndex: Int = if destination >= fullListIndices.count {
            // 如果目标位置超出了列表标签的范围，放在最后一个列表标签之后
            if let lastIndex = fullListIndices.last {
                lastIndex + 1
            } else {
                availableAppBarTabsItems.count
            }
        } else {
            fullListIndices[destination]
        }

        logger.debug("moveListTab: 完整列表中 从索引\(fullSourceIndex) 移动到 \(fullDestIndex)")

        // 创建适用于完整列表的IndexSet
        let fullSource = IndexSet([fullSourceIndex])

        // 调用原始moveTab方法
        moveTabHandler(fullSource, fullDestIndex)
    }

    // 创建列表标签
    func createListTab(
        listId: String,
        title: String,
        accountType: AccountType,
        iconUrl _: String? = nil
    ) -> FLListTimelineTabItem {
        FLListTimelineTabItem(
            metaData: FLTabMetaData(
                title: .text(title),
                icon: .material(.list)
            ),
            account: accountType,
            listKey: listId
        )
    }

    // 创建列表配置
    func createListConfig(
        listTab: FLListTimelineTabItem,
        title: String,
        iconUrl: String? = nil
    ) -> AppBarItemConfig {
        var metadata: [String: String] = ["title": title]

        // 添加图标URL
        if let iconUrl, !iconUrl.isEmpty {
            metadata["iconUrl"] = iconUrl
        }

        return AppBarItemConfig(
            key: listTab.key,
            type: .list,
            addedTime: Date(),
            metadata: metadata
        )
    }
}
