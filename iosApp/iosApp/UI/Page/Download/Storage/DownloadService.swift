import Foundation
import UIKit

final class DownloadService {
    static let shared = DownloadService()

    private let downloadManager = DownloadManager.shared
    private let storage = DownloadStorage.shared
    private var completionObserver: NSObjectProtocol?

    private init() {
        setupNotifications()
    }

    deinit {
        removeNotifications()
    }

    private func setupNotifications() {
        completionObserver = NotificationCenter.default.addObserver(
            forName: .downloadCompleted,
            object: nil,
            queue: nil
        ) { [weak self] notification in
            self?.handleDownloadCompletion(notification)
        }
    }

    private func removeNotifications() {
        if let observer = completionObserver {
            NotificationCenter.default.removeObserver(observer)
        }
    }

    private func handleDownloadCompletion(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let finalStatus = userInfo["finalStatus"] as? DownloadStatus,
              var item = userInfo["downloadItem"] as? DownloadItem
        else {
            print("DownloadService: Invalid completion notification received.")
            return
        }

        Task {
            do {
                if finalStatus == .downloaded {
                    if let filePath = await storage.localFilePath(for: item) {
                        if let attributes = try? FileManager.default.attributesOfItem(atPath: filePath.path),
                           let fileSize = attributes[.size] as? Int
                        {
                            item.totalSize = fileSize
                            item.downloadedSize = fileSize
                            print("DownloadService: Updated file size for \(item.url): \(fileSize) bytes")
                        }
                    }
                }

                try await storage.save(item)
                try await storage.updateStatus(id: item.id, status: finalStatus)
                print("DownloadService: DB status updated for \(item.url) to \(finalStatus)")
            } catch {
                print("DownloadService: Failed to update DB status for \(item.url): \(error)")
            }
        }
    }

    func startDownload(url: String, previewImageUrl: String, itemType: DownItemType) {
        let uuidString = UUID().uuidString
        let ext = URL(string: url)?.pathExtension
        let fileExt = (ext == nil || ext?.isEmpty == true) ? "jpg" : ext!
        let downloadItem = DownloadItem(
            id: uuidString,
            url: url,
            totalSize: 0,
            fileName: "\(uuidString).\(fileExt)",
            imageUrl: previewImageUrl,
            downItemType: itemType,
            durationSecond: nil,
            createTime: Date(),
            status: .initial,
            progress: 0.0
        )

        Task {
            do {
                try await storage.save(downloadItem)

                if downloadManager.startDownload(item: downloadItem) == nil {
                    try await storage.updateStatus(id: downloadItem.id, status: .failed)
                }
            } catch {
                print("Failed to start download: \(error)")
            }
        }
    }

    func pauseDownload(item: DownloadItem) {
        downloadManager.pauseDownload(url: item.url)
        Task {
            try? await storage.updateStatus(id: item.id, status: .paused)
        }
    }

    func resumeDownload(item: DownloadItem) {
        downloadManager.resumeDownload(url: item.url)
        Task {
            try? await storage.updateStatus(id: item.id, status: .downloading)
        }
    }

    func cancelDownload(item: DownloadItem) {
        downloadManager.cancelDownload(url: item.url)
        Task {
            try? await storage.updateStatus(id: item.id, status: .failed)
        }
    }

    func removeDownload(item: DownloadItem) {
        downloadManager.removeDownload(url: item.url)
        downloadManager.removeDownloadItem(item)
        Task {
            try? await storage.updateStatus(id: item.id, status: .removed)
        }
    }
}
