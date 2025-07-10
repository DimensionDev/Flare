import Foundation
import Kingfisher
import shared
import SwiftUI

class TimelineImagePrefetcher {
    static let shared = TimelineImagePrefetcher()

    private let prefetchRadius = 7

    private let prefetchQueue = DispatchQueue(label: "timeline.prefetch", qos: .utility)

    private var prefetchedRanges: Set<String> = []
    private let lock = NSLock()

    private init() {}

    func smartPrefetch(currentIndex: Int, timelineItems: [TimelineItem]) {
        FlareLog.debug("TimelineImagePrefetcher === smartPrefetch 开始 ===")
        FlareLog.debug("TimelineImagePrefetcher 传入参数 - currentIndex: \(currentIndex), timelineItems数量: \(timelineItems.count)")

        // 🔥 打印传入的timeline用户名信息
        let userNames = timelineItems.prefix(5).compactMap { item in
            item.user?.name
        }
        FlareLog.debug("TimelineImagePrefetcher 传入timeline前5个用户名: \(userNames)")

        // 🔥 打印timeline items的ID范围
        if !timelineItems.isEmpty {
            let firstId = timelineItems.first?.id ?? "nil"
            let lastId = timelineItems.last?.id ?? "nil"
            FlareLog.debug("TimelineImagePrefetcher timeline items ID范围: \(firstId) ~ \(lastId)")
        }

        prefetchQueue.async { [weak self] in
            guard let self else { return }

            // 🔥 改进边界检查日志
            guard currentIndex >= 0, currentIndex < timelineItems.count else {
                FlareLog.warning("TimelineImagePrefetcher currentIndex 越界: \(currentIndex)/\(timelineItems.count)")
                return
            }

            // 🔥 修复预取策略：预取所有传入的新数据，而不是只预取currentIndex周围的几个
            // 根据用户需求："每次获得到的新数据，比如有10个，10个传递进去缓存"
            let startIndex = 0 // 从传入数据的开头开始
            let endIndex = timelineItems.count - 1 // 到传入数据的结尾
            let rangeKey = "range_\(startIndex)_\(endIndex)_count_\(timelineItems.count)"

            FlareLog.debug("TimelineImagePrefetcher === 预取策略分析 ===")
            FlareLog.debug("TimelineImagePrefetcher 策略：预取所有传入的新数据")
            FlareLog.debug("TimelineImagePrefetcher currentIndex: \(currentIndex) (在传入数据中的起始位置)")
            FlareLog.debug("TimelineImagePrefetcher 预取范围: 0 到 \(endIndex) (全部\(timelineItems.count)个items)")
            FlareLog.debug("TimelineImagePrefetcher rangeKey: \(rangeKey)")

            guard startIndex <= endIndex, endIndex < timelineItems.count else {
                FlareLog.warning("TimelineImagePrefetcher 范围计算错误: \(startIndex)...\(endIndex), timelineItems.count: \(timelineItems.count)")
                return
            }

            // 🔥 改进重复预取检查日志
            lock.lock()
            let alreadyPrefetched = prefetchedRanges.contains(rangeKey)
            if !alreadyPrefetched {
                prefetchedRanges.insert(rangeKey)
                FlareLog.debug("TimelineImagePrefetcher 新增预取范围记录: \(rangeKey)")
            } else {
                FlareLog.debug("TimelineImagePrefetcher 范围已预取过，跳过: \(rangeKey)")
            }
            lock.unlock()

            guard !alreadyPrefetched else {
                return
            }

            // 🔥 详细的预取items分析
            let itemsToPreload = Array(timelineItems[startIndex ... endIndex])
            FlareLog.debug("TimelineImagePrefetcher 提取预取items: \(itemsToPreload.count)个 (索引\(startIndex)到\(endIndex))")

            // 🔥 打印预取items的用户信息
            let preloadUserNames = itemsToPreload.compactMap { $0.user?.name }
            FlareLog.debug("TimelineImagePrefetcher 预取items用户名: \(preloadUserNames)")

            let imageUrls = TimelineImagePrefetcher.extractImageUrls(from: itemsToPreload, limit: Int.max)

            guard !imageUrls.isEmpty else {
                FlareLog.debug("TimelineImagePrefetcher 范围 \(rangeKey) 无图片，跳过预取")
                return
            }

            FlareLog.debug("TimelineImagePrefetcher === 开始预取 ===")
            FlareLog.debug("TimelineImagePrefetcher 预取范围: \(rangeKey)")
            FlareLog.debug("TimelineImagePrefetcher 图片数量: \(imageUrls.count)")
            FlareLog.debug("TimelineImagePrefetcher 前3个图片URL: \(imageUrls.prefix(3).map(\.absoluteString))")

            // 🔥 创建Kingfisher预取器
            let prefetcher = ImagePrefetcher(
                urls: imageUrls,
                options: [
                    .alsoPrefetchToMemory,
                    .backgroundDecode,
                    .downloadPriority(0.3),
                ],
                progressBlock: nil,
                completionHandler: { _, _, completed in
                    let successCount = completed.count
                    let failedCount = imageUrls.count - successCount
                    FlareLog.debug("TimelineImagePrefetcher === 预取完成 ===")
                    FlareLog.debug("TimelineImagePrefetcher 范围: \(rangeKey)")
                    FlareLog.debug("TimelineImagePrefetcher 成功: \(successCount), 失败: \(failedCount), 总计: \(imageUrls.count)")
                    if failedCount > 0 {
                        FlareLog.warning("TimelineImagePrefetcher 预取失败数量: \(failedCount)")
                    }
                }
            )

            // 🔥 配置并启动预取
            prefetcher.maxConcurrentDownloads = 3
            FlareLog.debug("TimelineImagePrefetcher 启动Kingfisher预取器，最大并发: 3")

            prefetcher.start()
        }

        FlareLog.debug("TimelineImagePrefetcher === smartPrefetch 结束 ===")
    }

    /// 清理预览URL，去掉?name=orig后缀
    private static func cleanPreviewUrl(_ url: String?, for type: TimelineMediaType) -> String? {
        guard let url else { return nil }

        switch type {
        case .image, .video, .gif:
            if url.hasSuffix("?name=orig") {
                return String(url.dropLast("?name=orig".count))
            }
            return url
        case .audio:
            return url
        }
    }

    private static func extractImageUrls(from timelineItems: [TimelineItem], limit _: Int) -> [URL] {
        var urls = Set<URL>()
        var avatarCount = 0
        var mediaCount = 0
        var quoteAvatarCount = 0
        var quoteMediaCount = 0

        FlareLog.debug("TimelineImagePrefetcher === extractImageUrls 开始 ===")
        FlareLog.debug("TimelineImagePrefetcher 处理 \(timelineItems.count) 个timeline items")

        for (index, item) in timelineItems.enumerated() {
            // 🔥 打印每个item的基本信息
            let userName = item.user?.name.raw ?? "未知用户"
            let itemId = item.id
            FlareLog.debug("TimelineImagePrefetcher 处理item[\(index)]: \(userName) (ID: \(itemId))")

            // 提取用户头像
            if let user = item.user,
               let avatarUrl = URL(string: user.avatar)
            {
                urls.insert(avatarUrl)
                avatarCount += 1
                // FlareLog.debug("TimelineImagePrefetcher   - 添加头像: \(user.name.raw) -> \(user.avatar)")
            }

            // 提取媒体图片
            for (mediaIndex, media) in item.images.enumerated() {
                let rawImageUrl = media.previewUrl ?? media.url
                let cleanedImageUrl = cleanPreviewUrl(rawImageUrl, for: .image) ?? rawImageUrl
                if let url = URL(string: cleanedImageUrl) {
                    urls.insert(url)
                    mediaCount += 1
                    // FlareLog.debug("TimelineImagePrefetcher   - 添加媒体[\(mediaIndex)]: \(cleanedImageUrl)")
                }
            }

            // 提取引用内容
            for (quoteIndex, quoteItem) in item.quote.enumerated() {
                let quoteUserName = quoteItem.user?.name.raw ?? "未知引用用户"
                FlareLog.debug("TimelineImagePrefetcher   - 处理引用[\(quoteIndex)]: \(quoteUserName)")

                // 引用用户头像
                if let quoteUser = quoteItem.user,
                   let quoteAvatarUrl = URL(string: quoteUser.avatar)
                {
                    urls.insert(quoteAvatarUrl)
                    quoteAvatarCount += 1
                    FlareLog.debug("TimelineImagePrefetcher     - 添加引用头像: \(quoteUser.name.raw) -> \(quoteUser.avatar)")
                }

                // 引用媒体图片
                for (quoteMediaIndex, media) in quoteItem.images.enumerated() {
                    let rawImageUrl = media.previewUrl ?? media.url
                    let cleanedImageUrl = cleanPreviewUrl(rawImageUrl, for: .image) ?? rawImageUrl
                    if let url = URL(string: cleanedImageUrl) {
                        urls.insert(url)
                        quoteMediaCount += 1
                        FlareLog.debug("TimelineImagePrefetcher     - 添加引用媒体[\(quoteMediaIndex)]: \(cleanedImageUrl)")
                    }
                }
            }
        }


        let uniqueUrls = Array(urls)
        let duplicateCount = urls.count - uniqueUrls.count

        FlareLog.debug("TimelineImagePrefetcher === extractImageUrls 统计 ===")
        FlareLog.debug("TimelineImagePrefetcher 头像数量: \(avatarCount)")
        FlareLog.debug("TimelineImagePrefetcher 媒体数量: \(mediaCount)")
        FlareLog.debug("TimelineImagePrefetcher 引用头像数量: \(quoteAvatarCount)")
        FlareLog.debug("TimelineImagePrefetcher 引用媒体数量: \(quoteMediaCount)")
        FlareLog.debug("TimelineImagePrefetcher 总URL数量: \(urls.count)")
        FlareLog.debug("TimelineImagePrefetcher 去重后数量: \(uniqueUrls.count)")
        FlareLog.debug("TimelineImagePrefetcher 重复URL数量: \(duplicateCount)")

        return uniqueUrls
    }
}
