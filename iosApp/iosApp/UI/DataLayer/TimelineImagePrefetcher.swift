import Foundation
import Kingfisher
import shared

class TimelineImagePrefetcher {
    static let shared = TimelineImagePrefetcher()

    private let prefetchQueue = DispatchQueue(label: "timeline.prefetch", qos: .utility)

    private var lastPrefetchTime: Date = .distantPast
    private let minInterval: TimeInterval = 0.5

    private init() {}

    func smartPrefetchDiskImages(timelineItems: [TimelineItem]) {
        let now = Date()
        guard now.timeIntervalSince(lastPrefetchTime) >= minInterval else {
            FlareLog.debug("TimelineImagePrefetcher 调用过于频繁，跳过")
            return
        }
        lastPrefetchTime = now

        guard !timelineItems.isEmpty else {
            FlareLog.debug("TimelineImagePrefetcher timelineItems为空，跳过预取")
            return
        }

        FlareLog.debug("TimelineImagePrefetcher 开始预取磁盘图片，items数量: \(timelineItems.count)")

        prefetchQueue.async {
            let imageUrls = TimelineImagePrefetcher.extractImageUrls(from: timelineItems)

            guard !imageUrls.isEmpty else {
                FlareLog.debug("TimelineImagePrefetcher 无图片URL，跳过预取")
                return
            }

            FlareLog.debug("TimelineImagePrefetcher 提取到 \(imageUrls.count) 个图片URL")

            let prefetcher = ImagePrefetcher(
                urls: imageUrls,
                options: [
                    .backgroundDecode,
                    .downloadPriority(0.3),
                    .diskCacheExpiration(.days(7))
                    // .alsoPrefetchToMemory，
                ],
                progressBlock: nil,
                completionHandler: { skippedResources, failedResources, completedResources in
                    let skippedCount = skippedResources.count
                    let failedCount = failedResources.count
                    let successCount = completedResources.count
                    let totalCount = imageUrls.count

                    FlareLog.debug("TimelineImagePrefetcher === 磁盘预取完成 ===")
                    FlareLog.debug("TimelineImagePrefetcher 总计: \(totalCount), 成功: \(successCount), 跳过(已缓存): \(skippedCount), 失败: \(failedCount)")

                    if failedCount > 0 {
                        FlareLog.warning("TimelineImagePrefetcher 预取失败数量: \(failedCount)")
                    }
                }
            )

            prefetcher.start()
        }
    }

    private static func extractImageUrls(from timelineItems: [TimelineItem]) -> [URL] {
        var urls = Set<URL>()
        var avatarCount = 0
        var mediaCount = 0
        var quoteAvatarCount = 0
        var quoteMediaCount = 0

        FlareLog.debug("TimelineImagePrefetcher === extractImageUrls 开始 ===")
        FlareLog.debug("TimelineImagePrefetcher 处理 \(timelineItems.count) 个timeline items")

        for item in timelineItems {
            // 提取用户头像
            if let user = item.user,
               let avatarUrl = URL(string: user.avatar)
            {
                urls.insert(avatarUrl)
                avatarCount += 1
            }

            for media in item.images {
                guard let previewUrl = media.previewUrl, !previewUrl.isEmpty else {
                    continue
                }

                let cleanedImageUrl = cleanPreviewUrl(previewUrl)
                if let url = URL(string: cleanedImageUrl) {
                    urls.insert(url)
                    mediaCount += 1
                }
            }

            // 提取引用内容
            for quoteItem in item.quote {
                // 引用用户头像
                if let quoteUser = quoteItem.user,
                   let quoteAvatarUrl = URL(string: quoteUser.avatar)
                {
                    urls.insert(quoteAvatarUrl)
                    quoteAvatarCount += 1
                }

                for media in quoteItem.images {
                    guard let previewUrl = media.previewUrl, !previewUrl.isEmpty else {
                        continue
                    }

                    let cleanedImageUrl = cleanPreviewUrl(previewUrl)
                    if let url = URL(string: cleanedImageUrl) {
                        urls.insert(url)
                        quoteMediaCount += 1
                    }
                }
            }
        }

        let uniqueUrls = Array(urls)

        FlareLog.debug("TimelineImagePrefetcher === extractImageUrls 统计 ===")
        FlareLog.debug("TimelineImagePrefetcher 头像数量: \(avatarCount)")
        FlareLog.debug("TimelineImagePrefetcher 媒体数量: \(mediaCount)")
        FlareLog.debug("TimelineImagePrefetcher 引用头像数量: \(quoteAvatarCount)")
        FlareLog.debug("TimelineImagePrefetcher 引用媒体数量: \(quoteMediaCount)")
        FlareLog.debug("TimelineImagePrefetcher 总URL数量: \(urls.count)")
        FlareLog.debug("TimelineImagePrefetcher 去重后数量: \(uniqueUrls.count)")

        return uniqueUrls
    }

    private static func cleanPreviewUrl(_ url: String) -> String {
        if url.contains("?name=orig") {
            return url.replacingOccurrences(of: "?name=orig", with: "")
        }
        return url
    }
}
