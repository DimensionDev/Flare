import Foundation
import GRDB
import shared
import SwiftUI

extension Notification.Name {
    static let downloadProgressUpdated = Notification.Name("downloadProgressUpdated")
    static let downloadCompleted = Notification.Name("downloadCompleted")
    static let downloadTaskDidSucceed = Notification.Name("downloadTaskDidSucceed")
    static let downloadFailed = Notification.Name("downloadFailed")
}

struct ShareableFile: Identifiable {
    let id = UUID()
    let url: URL
}

@MainActor
struct DownloadManagerScreen: View {
    @ObservedObject var router: FlareRouter
    @EnvironmentObject private var menuState: FlareAppState

    let accountType: AccountType
    @State private var downloadItems: [DownloadItem] = []
    @State private var itemToShare: ShareableFile? = nil // New state for .sheet(item:)

    init(accountType: AccountType, router: FlareRouter) {
        self.accountType = accountType
        self.router = router
    }

    var body: some View {
        List {
            ForEach(downloadItems) { item in
                DownloadItemCell(
                    item: item,
                    onTapAction: {
                        handleItemTap(item)
                    },
                    onShareAction: { shareItem in
                        handleItemShare(shareItem)
                    }
                )
                .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                .listRowBackground(Colors.Background.swiftUIPrimary)
            }
            .onDelete(perform: deleteItems)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Colors.Background.swiftUIPrimary)
        .navigationTitle("Download Manager")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                EditButton()
            }
        }
        .toolbarBackground(Colors.Background.swiftUIPrimary, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .environmentObject(router)
        .environmentObject(menuState)
        .task {
            await loadDownloadItems()
        }
        .onAppear {
            setupNotifications()
        }
        .onDisappear {
            removeNotifications()
        }
        .sheet(item: $itemToShare) { shareableFile in
            ActivityView(activityItems: [shareableFile.url])
                .ignoresSafeArea()
        }
    }

    private func loadDownloadItems() async {
        do {
            downloadItems = try await DownloadStorage.shared.getAllItems()
        } catch {
            print("Failed to load download items: \(error)")
        }
    }

    private func deleteItems(at offsets: IndexSet) {
        let itemsToDelete = offsets.map { downloadItems[$0] }
        var successfullyDeletedIndices = IndexSet()

        let originalIndices = offsets.sorted()

        Task {
            for i in 0 ..< itemsToDelete.count {
                let item = itemsToDelete[i]
                let originalIndex = originalIndices[i]

                do {
                    // 1. delete download
                    DownloadService.shared.removeDownload(item: item)

                    // 2. delete file
                    if let filePath = await DownloadStorage.shared.localFilePath(for: item) {
                        do {
                            try FileManager.default.removeItem(at: filePath)
                            print("Successfully deleted file at: \(filePath.path)")
                        } catch let error as NSError where error.domain == NSCocoaErrorDomain && error.code == NSFileNoSuchFileError {
                            print("File already removed or not found at: \(filePath.path), skipping.")
                        } catch {
                            print("Failed to delete file for item \(item.id) at \(filePath.path): \(error)")
                        }
                    }

                    // 3. delete db record
                    try await DownloadStorage.shared.delete(id: item.id)
                    print("Successfully deleted DB record for item \(item.id)")

                    successfullyDeletedIndices.insert(originalIndex)

                } catch {
                    print("Failed overall deletion process for item \(item.id): \(error)")
                }
            }

            // 4. delete item form ui
            if !successfullyDeletedIndices.isEmpty {
                await MainActor.run {
                    downloadItems.remove(atOffsets: successfullyDeletedIndices)
                }
            }
        }
    }

    private func handleItemTap(_ item: DownloadItem) {
        Task {
            do {
                switch item.status {
                case .initial:
                    DownloadService.shared.startDownload(url: item.url, previewImageUrl: item.url, itemType: item.downItemType)
                case .downloading:
                    DownloadService.shared.pauseDownload(item: item)
                case .downloaded:
                    print("Tapped downloaded item: \(item.fileName)")
                case .removed:
                    print("Tapped removed item: \(item.fileName)")
                case .failed:
                    DownloadService.shared.startDownload(url: item.url, previewImageUrl: item.url, itemType: item.downItemType)
                case .paused:
                    // Update UI state immediately
                    if let index = downloadItems.firstIndex(where: { $0.id == item.id }) {
                        downloadItems[index].status = .downloading
                    }
                    // Call the service to handle the actual resume
                    DownloadService.shared.resumeDownload(item: item)
                }
            } catch {
                print("Failed to handle tap action: \(error)")
            }
        }
    }

    private func handleItemShare(_ item: DownloadItem) {
        Task {
            do {
                switch item.status {
                case .initial:
                    DownloadService.shared.startDownload(url: item.url, previewImageUrl: item.url, itemType: item.downItemType)
                case .downloading:
                    DownloadService.shared.pauseDownload(item: item)
                case .downloaded:
                    if let path = await DownloadStorage.shared.localFilePath(for: item) {
                        itemToShare = ShareableFile(url: path)
                    } else {
                        print("Could not get file path for sharing item: \(item.fileName ?? "")")
                    }
                case .removed:
                    print("Tapped removed item: \(item.fileName ?? "")")
                case .failed:
                    DownloadService.shared.startDownload(url: item.url, previewImageUrl: item.url, itemType: item.downItemType) // Retry
                case .paused:
                    if let index = downloadItems.firstIndex(where: { $0.id == item.id }) {
                        downloadItems[index].status = .downloading
                    }
                    DownloadService.shared.resumeDownload(item: item)
                }
            } catch {
                print("Failed to handle tap action: \(error)")
            }
        }
    }

    private func setupNotifications() {
        NotificationCenter.default.addObserver(
            forName: .downloadProgressUpdated,
            object: nil,
            queue: .main
        ) { notification in
            handleProgressUpdate(notification)
        }

        NotificationCenter.default.addObserver(
            forName: .downloadCompleted,
            object: nil,
            queue: .main
        ) { notification in
            handleDownloadCompletion(notification)
        }

        NotificationCenter.default.addObserver(
            forName: .downloadFailed,
            object: nil,
            queue: .main
        ) { notification in
            handleDownloadError(notification)
        }
    }

    private func removeNotifications() {
        NotificationCenter.default.removeObserver(self)
    }

    private func handleProgressUpdate(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let url = userInfo["url"] as? String,
              let progress = userInfo["progress"] as? Double,
              let totalSize = userInfo["totalSize"] as? Int,
              let downloadedSize = userInfo["downloadedSize"] as? Int
        else {
            print("Error: Invalid progress notification received or size missing.")
            return
        }

        if let index = downloadItems.firstIndex(where: { $0.url == url }) {
            // Update progress
            downloadItems[index].progress = progress

            if totalSize > 0 {
                downloadItems[index].totalSize = totalSize
            }
            if downloadedSize >= 0 {
                downloadItems[index].downloadedSize = downloadedSize
            }

            if downloadItems[index].status != .downloading, downloadItems[index].status != .paused {
                downloadItems[index].status = .downloading
            }
        }
    }

    private func handleDownloadCompletion(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let url = userInfo["url"] as? String,
              let finalStatus = userInfo["finalStatus"] as? DownloadStatus
        else {
            return
        }

        if let index = downloadItems.firstIndex(where: { $0.url == url }) {
            downloadItems[index].status = finalStatus
            if finalStatus == .downloaded {
                downloadItems[index].progress = 1.0
            }
        }
    }

    private func handleDownloadError(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let url = userInfo["url"] as? String
        else {
            return
        }

        if let index = downloadItems.firstIndex(where: { $0.url == url }) {
            downloadItems[index].status = .failed
        }
    }
}
