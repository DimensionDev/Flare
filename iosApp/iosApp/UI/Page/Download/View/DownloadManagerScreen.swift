import Foundation
import shared
import SwiftUI
import Tiercel

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
    @Environment(FlareRouter.self) private var router
    @Environment(FlareAppState.self) private var menuState

    let accountType: AccountType
    @State private var downloadTasks: [DownloadTask] = []
    @State private var itemToShare: ShareableFile? = nil
    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType) {
        self.accountType = accountType
    }

    var body: some View {
        List {
            ForEach(downloadTasks, id: \.url.absoluteString) { task in
                DownloadTaskCell(
                    task: task,
                    onTapAction: {
                        handleTaskTap(task)
                    },
                    onShareAction: {
                        handleTaskShare(task)
                    }
                ).scrollContentBackground(.hidden).listRowBackground(theme.primaryBackgroundColor)
                    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
//                .listRowBackground(FColors.Background.swiftUIPrimary)
            }
            .onDelete(perform: deleteTasks)
        }.listRowBackground(theme.primaryBackgroundColor)
            // .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(theme.secondaryBackgroundColor)
            .navigationTitle("Download Manager")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    EditButton().foregroundColor(theme.tintColor)
                }
            }
//        .toolbarBackground(FColors.Background.swiftUIPrimary, for: .navigationBar)
//        .toolbarBackground(.visible, for: .navigationBar)
            .environment(router)
            .environment(menuState)
            .task {
                loadDownloadTasks()
            }
            .onReceive(NotificationCenter.default.publisher(for: .init("TR.DownloadTaskDidBecomeRunning"))) { _ in
                loadDownloadTasks()
            }
            .onReceive(NotificationCenter.default.publisher(for: .init("TR.DownloadTaskDidBecomeWaiting"))) { _ in
                loadDownloadTasks()
            }
            .onReceive(NotificationCenter.default.publisher(for: .init("TR.DownloadTaskDidBecomeSucceeded"))) { _ in
                loadDownloadTasks()
            }
            .onReceive(NotificationCenter.default.publisher(for: .init("TR.DownloadTaskDidBecomeSuspended"))) { _ in
                loadDownloadTasks()
            }
            .onReceive(NotificationCenter.default.publisher(for: .init("TR.DownloadTaskDidBecomeFailed"))) { _ in
                loadDownloadTasks()
            }
            .sheet(item: $itemToShare) { shareableFile in
                ActivityView(activityItems: [shareableFile.url])
                    .ignoresSafeArea()
            }
    }

    private func loadDownloadTasks() {
        downloadTasks = DownloadManager.shared.tasks
    }

    private func deleteTasks(at offsets: IndexSet) {
        let tasksToDelete = offsets.map { downloadTasks[$0] }

        for task in tasksToDelete {
            DownloadManager.shared.remove(url: task.url.absoluteString)
        }

        var indexSet = IndexSet()
        for (index, task) in downloadTasks.enumerated() {
            if tasksToDelete.contains(where: { $0.url.absoluteString == task.url.absoluteString }) {
                indexSet.insert(index)
            }
        }
        if !indexSet.isEmpty {
            downloadTasks.remove(atOffsets: indexSet)
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            loadDownloadTasks()
        }
    }

    private func handleTaskTap(_ task: DownloadTask) {
        switch task.status {
        case .waiting:
            DownloadManager.shared.pause(url: task.url.absoluteString)
        case .running:
            DownloadManager.shared.pause(url: task.url.absoluteString)
        case .suspended:
            DownloadManager.shared.resume(url: task.url.absoluteString)
        case .succeeded:
            let fileURL = URL(fileURLWithPath: task.filePath)
            itemToShare = ShareableFile(url: fileURL)
        case .failed:
            DownloadManager.shared.resume(url: task.url.absoluteString)
        case .removed:
            break
        @unknown default:
            break
        }
    }

    private func handleTaskShare(_ task: DownloadTask) {
        if task.status == .succeeded {
            let fileURL = URL(fileURLWithPath: task.filePath)
            itemToShare = ShareableFile(url: fileURL)
        }
    }
}
