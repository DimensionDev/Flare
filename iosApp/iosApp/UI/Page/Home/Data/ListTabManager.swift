import Foundation
import os
import shared
import SwiftUI

class ListTabManager {
    func moveListTab(
        from source: IndexSet,
        to destination: Int,
        availableAppBarTabsItems: [FLTabItem],
        moveTabHandler: (IndexSet, Int) -> Void
    ) {
        FlareLog.debug("moveListTab调用: source=\(source), destination=\(destination)")

        let listTabs = availableAppBarTabsItems.filter { $0.key.starts(with: "list_") }

        guard let sourceIndex = source.first, sourceIndex < listTabs.count else {
            FlareLog.debug("moveListTab: 无效的源索引")
            return
        }

        let movedTab = listTabs[sourceIndex]
        FlareLog.debug("moveListTab: 移动标签 \(movedTab.key)")

        let fullListIndices = listTabs.compactMap { tab in
            availableAppBarTabsItems.firstIndex(where: { $0.key == tab.key })
        }

        guard sourceIndex < fullListIndices.count else {
            FlareLog.debug("moveListTab: 源索引超出范围")
            return
        }

        let fullSourceIndex = fullListIndices[sourceIndex]

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

        FlareLog.debug("moveListTab: 完整列表中 从索引\(fullSourceIndex) 移动到 \(fullDestIndex)")

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
