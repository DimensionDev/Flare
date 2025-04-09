import Kingfisher
import shared
import SwiftUI

struct StorageScreen: View {
    @State private var presenter = StoragePresenter()
    @State private var imageCacheSize: String = "Calculating..."
    @State private var isCleaningCache = false

    // 下载相关状态
    @State private var downloadDBSize: String = "Calculating..."
    @State private var downloadFilesInfo: String = "Calculating..."
    @State private var isCleaningDownloadDB = false
    @State private var isCleaningDownloadFiles = false

    // 对话框状态
    @State private var showClearDBConfirm = false
    @State private var showClearFilesConfirm = false
    @State private var showSuccessAlert = false
    @State private var successMessage = ""

    private func calculateImageCacheSize() {
        ImageCache.default.calculateDiskStorageSize { result in
            switch result {
            case let .success(size):
                imageCacheSize = StorageFormatter.formatFileSize(Int64(size))
            case .failure:
                imageCacheSize = "calculate failed"
            }
        }
    }

    private func updateDownloadSizes() {
        Task {
            // 获取数据库大小
            let dbSize = await DownloadStorage.shared.getDatabaseSize()
            downloadDBSize = StorageFormatter.formatFileSize(dbSize)

            // 获取下载文件信息
            let (fileCount, totalSize) = await DownloadStorage.shared.getDownloadsInfo()
            downloadFilesInfo = StorageFormatter.formatDownloadInfo(fileCount: fileCount, totalSize: totalSize)
        }
    }

    private func clearImageCache() {
        isCleaningCache = true
        ImageCache.default.clearDiskCache {
            ImageCache.default.clearMemoryCache()
            calculateImageCacheSize()
            isCleaningCache = false
            showSuccessAlert(message: "image cache cleaned")
        }
    }

    private func clearDownloadDB() {
        Task {
            isCleaningDownloadDB = true
            do {
                try await DownloadStorage.shared.clearDatabase()
                updateDownloadSizes()
                showSuccessAlert(message: "download record cleaned")
            } catch {
                print("Failed to clear download database: \(error)")
            }
            isCleaningDownloadDB = false
        }
    }

    private func clearDownloadFiles() {
        Task {
            isCleaningDownloadFiles = true
            do {
                try await DownloadStorage.shared.clearDownloads()
                updateDownloadSizes()
                showSuccessAlert(message: "download files cleaned")
            } catch {
                print("Failed to clear download files: \(error)")
            }
            isCleaningDownloadFiles = false
        }
    }

    private func showSuccessAlert(message: String) {
        successMessage = message
        showSuccessAlert = true
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                // Database Cache Section
                Section("settings_storage_clear_database") {
                    Button(role: .destructive) {
                        state.clearCache()
                    } label: {
                        HStack(alignment: .center) {
                            Image(systemName: "trash")
                                .font(.title)
                            Spacer()
                                .frame(width: 16)
                            VStack(alignment: .leading) {
                                Text(String(format: NSLocalizedString("settings_storage_clear_database_description", comment: ""), String(state.userCount), String(state.statusCount)))
                            }
                        }
                    }
                    .buttonStyle(.borderless)
                }

                // Image Cache Section
                Section("settings_storage_clear_image_cache") {
                    Button(role: .destructive) {
                        clearImageCache()
                    } label: {
                        HStack {
                            Image(systemName: "photo.circle")
                                .font(.title)
                            Spacer()
                                .frame(width: 16)
                            VStack(alignment: .leading) {
                                Text(String(format: NSLocalizedString("settings_storage_clear_image_cache_description", comment: ""), String(imageCacheSize)))
                            }
                            if isCleaningCache {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .buttonStyle(.borderless)
                    .disabled(isCleaningCache)
                }

                // Download Cache Section
                Section("Download Cache") {
                    // 清理下载数据库
                    Button(role: .destructive) {
                        showClearDBConfirm = true
                    } label: {
                        HStack {
                            Image(systemName: "database")
                                .font(.title)
                            Spacer()
                                .frame(width: 16)
                            VStack(alignment: .leading) {
                                Text("Clean Download Record")
                                Text("Database Size: \(downloadDBSize)")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            if isCleaningDownloadDB {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .buttonStyle(.borderless)
                    .disabled(isCleaningDownloadDB)

                    // 清理下载文件
                    Button(role: .destructive) {
                        showClearFilesConfirm = true
                    } label: {
                        HStack {
                            Image(systemName: "folder")
                                .font(.title)
                            Spacer()
                                .frame(width: 16)
                            VStack(alignment: .leading) {
                                Text("Clean Download Files")
                                Text("\(downloadFilesInfo)")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            if isCleaningDownloadFiles {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    .buttonStyle(.borderless)
                    .disabled(isCleaningDownloadFiles)
                }
            }
            .navigationTitle("settings_storage_clear_database")
            .onAppear {
                calculateImageCacheSize()
                updateDownloadSizes()
            }
            // 确认对话框
            .confirmationDialog(
                title: "Clean Download Record",
                message: "Are you sure you want to clear all download records? This action cannot be undone.",
                isPresented: $showClearDBConfirm,
                action: clearDownloadDB
            )
            .confirmationDialog(
                title: "Clean Download Files",
                message: "Are you sure you want to delete all downloaded files? This action cannot be undone.",
                isPresented: $showClearFilesConfirm,
                action: clearDownloadFiles
            )
            // 成功提示
            .successToast(successMessage, isPresented: $showSuccessAlert)
        }
    }
}
