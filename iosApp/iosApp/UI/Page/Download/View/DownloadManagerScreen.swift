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
    @ObservedObject var router: FlareRouter
    @EnvironmentObject private var menuState: FlareAppState

    let accountType: AccountType
    @State private var downloadTasks: [DownloadTask] = []
    @State private var itemToShare: ShareableFile? = nil

    init(accountType: AccountType, router: FlareRouter) {
        self.accountType = accountType
        self.router = router
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
                )
                .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                .listRowBackground(Colors.Background.swiftUIPrimary)
            }
            .onDelete(perform: deleteTasks)
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

        // 立即从本地数组中移除这些任务
        var indexSet = IndexSet()
        for (index, task) in downloadTasks.enumerated() {
            if tasksToDelete.contains(where: { $0.url.absoluteString == task.url.absoluteString }) {
                indexSet.insert(index)
            }
        }
        if !indexSet.isEmpty {
            downloadTasks.remove(atOffsets: indexSet)
        }

        // 异步刷新列表，确保删除操作在UI上完全生效
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
            // 已删除，无操作
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
