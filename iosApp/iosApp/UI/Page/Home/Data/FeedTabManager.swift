import Foundation
import os
import shared
import SwiftUI

// Feed标签管理器
class FeedTabManager {
    private let logger = Logger(subsystem: "com.flare.app", category: "FeedTabManager")

    // 专门用于移动Feed标签的方法
    func moveFeedTab(
        from source: IndexSet,
        to destination: Int,
        availableAppBarTabsItems: [FLTabItem],
        moveTabHandler: (IndexSet, Int) -> Void
    ) {
        logger.debug("moveFeedTab调用: source=\(source), destination=\(destination)")

        // 获取所有Feed标签
        let feedTabs = availableAppBarTabsItems.filter { $0.key.starts(with: "feed_") }

        guard let sourceIndex = source.first, sourceIndex < feedTabs.count else {
            logger.debug("moveFeedTab: 无效的源索引")
            return
        }

        // 获取要移动的标签
        let movedTab = feedTabs[sourceIndex]
        logger.debug("moveFeedTab: 移动标签 \(movedTab.key)")

        // 找到这些标签在完整列表中的索引
        let fullFeedIndices = feedTabs.compactMap { tab in
            availableAppBarTabsItems.firstIndex(where: { $0.key == tab.key })
        }

        // 确保索引有效
        guard sourceIndex < fullFeedIndices.count else {
            logger.debug("moveFeedTab: 源索引超出范围")
            return
        }

        // 计算目标位置在完整列表中的索引
        let fullSourceIndex = fullFeedIndices[sourceIndex]

        // 计算目标索引
        var fullDestIndex: Int = if destination >= fullFeedIndices.count {
            // 如果目标位置超出了Feed标签的范围，放在最后一个Feed标签之后
            if let lastIndex = fullFeedIndices.last {
                lastIndex + 1
            } else {
                availableAppBarTabsItems.count
            }
        } else {
            fullFeedIndices[destination]
        }

        logger.debug("moveFeedTab: 完整列表中 从索引\(fullSourceIndex) 移动到 \(fullDestIndex)")

        // 创建适用于完整列表的IndexSet
        let fullSource = IndexSet([fullSourceIndex])

        // 调用原始moveTab方法
        moveTabHandler(fullSource, fullDestIndex)
    }

    // 创建Feed标签
    func createFeedTab(
        feedId: String,
        title: String,
        accountType: AccountType,
        iconUrl _: String? = nil
    ) -> FLBlueskyFeedTabItem {
        FLBlueskyFeedTabItem(
            metaData: FLTabMetaData(
                title: .text(title),
                icon: .material(.feeds)
            ),
            account: accountType,
            feedKey: feedId
        )
    }

    // 创建Feed配置
    func createFeedConfig(
        feedTab: FLBlueskyFeedTabItem,
        title: String,
        iconUrl: String? = nil
    ) -> AppBarItemConfig {
        var metadata: [String: String] = ["title": title]

        // 添加图标URL
        if let iconUrl, !iconUrl.isEmpty {
            metadata["iconUrl"] = iconUrl
        }

        return AppBarItemConfig(
            key: feedTab.key,
            type: .feed,
            addedTime: Date(),
            metadata: metadata
        )
    }
}
