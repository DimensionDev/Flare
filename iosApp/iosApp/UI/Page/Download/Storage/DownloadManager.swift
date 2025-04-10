import Foundation
import Tiercel

typealias TiercelDownloadTask = Tiercel.DownloadTask
typealias TiercelSessionManager = Tiercel.SessionManager

final class DownloadManager {
    static let shared = DownloadManager()

    private let sessionManager: TiercelSessionManager
    private var downloadItems: [String: DownloadItem] = [:]

    private init() {
        var config = SessionConfiguration()
        config.allowsCellularAccess = true
        config.maxConcurrentTasksLimit = 3

        sessionManager = TiercelSessionManager("FlareDownloader", configuration: config)
    }

    private func convertStatus(_ status: Status) -> DownloadStatus {
        switch status {
        case .waiting:
            return .initial
        case .running:
            return .downloading
        case .suspended:
            return .paused
        case .succeeded:
            return .downloaded
        case .failed:
            return .failed
        case .removed:
            return .removed
        @unknown default:
            return .failed
        }
    }

    @discardableResult
    func startDownload(item: DownloadItem) -> TiercelDownloadTask? {
        downloadItems[item.url] = item

        let task = sessionManager.download(
            item.url,
            headers: [:], // 空 header, cookies
            fileName: item.fileName
        )

        task?.progress { [weak self] task in
            guard let self, var currentItem = downloadItems[item.url] else { return }

            currentItem.progress = task.progress.fractionCompleted

            if task.progress.totalUnitCount > 0, currentItem.totalSize <= 0 {
                currentItem.totalSize = Int(task.progress.totalUnitCount)
            }

            if task.progress.completedUnitCount >= 0 {
                currentItem.downloadedSize = Int(task.progress.completedUnitCount)
            }

            downloadItems[item.url] = currentItem

            NotificationCenter.default.post(
                name: .downloadProgressUpdated,
                object: nil,
                userInfo: [
                    "url": item.url,
                    "progress": currentItem.progress,
                    "totalSize": currentItem.totalSize,
                    "downloadedSize": currentItem.downloadedSize,
                    "downloadItem": currentItem,
                ]
            )
        }

        task?.completion { [weak self] task in
            guard let self else { return }

            let finalStatus = convertStatus(task.status)

            var updatedItem = downloadItems[item.url]
            updatedItem?.status = finalStatus
            if let itemToUpdate = updatedItem {
                downloadItems[item.url] = itemToUpdate
            }

            if finalStatus == .downloaded {
                print("Tiercel Download Task Succeeded:")
                print("  - Original URL: \(task.url.absoluteString)")
                print("  - Final File Path: \(task.filePath)")

                do {
                    let documentsURL = try FileManager.default
                        .url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                        .appendingPathComponent("Downloads", isDirectory: true)

                    try FileManager.default.createDirectory(at: documentsURL, withIntermediateDirectories: true)

                    let destinationURL = documentsURL.appendingPathComponent(item.fileName)

                    if FileManager.default.fileExists(atPath: destinationURL.path) {
                        try FileManager.default.removeItem(at: destinationURL)
                    }

                    try FileManager.default.moveItem(atPath: task.filePath, toPath: destinationURL.path)
                    print("  - Moved to: \(destinationURL.path)")

                    if let attributes = try? FileManager.default.attributesOfItem(atPath: destinationURL.path),
                       let fileSize = attributes[.size] as? Int
                    {
                        updatedItem?.totalSize = fileSize
                        updatedItem?.downloadedSize = fileSize
                        if let itemToUpdate = updatedItem {
                            downloadItems[item.url] = itemToUpdate
                        }
                    }
                } catch {
                    print("Failed to move downloaded file: \(error)")

                    updatedItem?.status = .failed
                    if let itemToUpdate = updatedItem {
                        downloadItems[item.url] = itemToUpdate
                    }
                }
            } else if finalStatus == .failed {
                print("Tiercel Download Task Failed:")
                print("  - Original URL: \(task.url.absoluteString)")
                print("  - Error: \(task.error?.localizedDescription ?? "Unknown error")")
            }

            NotificationCenter.default.post(
                name: .downloadCompleted,
                object: nil,
                userInfo: [
                    "url": item.url,
                    "tiercelStatus": task.status,
                    "finalStatus": finalStatus,
                    "downloadItem": updatedItem ?? item, // 发送更新后的item，如果更新失败则发送原始item
                ]
            )

            if finalStatus == .downloaded, let succeededItem = updatedItem {
                NotificationCenter.default.post(
                    name: .downloadTaskDidSucceed,
                    object: nil,
                    userInfo: [
                        "url": item.url,
                        "downloadItem": succeededItem,
                    ]
                )
            }
        }

        return task
    }

    func pauseDownload(url: String) {
        sessionManager.suspend(url)
    }

    func resumeDownload(url: String) {
//        if let task = sessionManager.fetchTask(url) {
            sessionManager.start(url) // 重新创建下载任务
//        }
    }

    func cancelDownload(url: String) {
        sessionManager.cancel(url)
    }

    func removeDownload(url: String) {
        sessionManager.remove(url)
    }

    func removeAllDownloads() {
        let urls = sessionManager.tasks.compactMap(\.url.absoluteString)

        for url in urls {
            sessionManager.remove(url)
        }
    }

    func getTask(for url: String) -> TiercelDownloadTask? {
        sessionManager.fetchTask(url)
    }

    var allTasks: [TiercelDownloadTask] {
        sessionManager.tasks
    }

    func getDownloadItem(for url: String) -> DownloadItem? {
        downloadItems[url]
    }

    var allDownloadItems: [DownloadItem] {
        Array(downloadItems.values)
    }

    func updateDownloadItem(_ item: DownloadItem) {
        downloadItems[item.url] = item
    }

    func removeDownloadItem(_ item: DownloadItem) {
        downloadItems.removeValue(forKey: item.url)
    }
}
