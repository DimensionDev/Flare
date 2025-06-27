import Foundation
import Tiercel

class DownloadManager {
    static let shared = DownloadManager()

    let sessionManager: SessionManager

    private init() {
        var config = SessionConfiguration()
        config.allowsCellularAccess = true
        config.maxConcurrentTasksLimit = 3

        var documentsURL: URL
        do {
            documentsURL = try FileManager.default
                .url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                .appendingPathComponent("Downloads", isDirectory: true)

            try FileManager.default.createDirectory(at: documentsURL, withIntermediateDirectories: true, attributes: nil)
        } catch {
            FlareLog.error("DownloadManager Error creating Downloads directory: \(error), using default cache path")
            documentsURL = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("Downloads")
            try? FileManager.default.createDirectory(at: documentsURL, withIntermediateDirectories: true)
        }

        let cache = Cache("FlareDownloader", downloadPath: documentsURL.path)

        sessionManager = SessionManager("FlareDownloader", configuration: config, cache: cache)
    }

    @discardableResult
    func download(url: String, fileName: String? = nil) -> DownloadTask? {
        sessionManager.download(url, fileName: fileName)
    }

    func pause(url: String) {
        sessionManager.suspend(url)
    }

    func resume(url: String) {
        sessionManager.start(url)
    }

    func cancel(url: String) {
        sessionManager.cancel(url)
    }

    func remove(url: String, completely: Bool = true) {
        DownloadHelper.shared.removePreviewImageUrl(for: url)

        sessionManager.remove(url, completely: completely)
    }

    var tasks: [DownloadTask] {
        sessionManager.tasks.filter { $0.status != .removed }
    }

    var allTasks: [DownloadTask] {
        sessionManager.tasks
    }

    func getTask(url: String) -> DownloadTask? {
        sessionManager.fetchTask(url)
    }

    func clearSucceededTasks(completely: Bool = false) {
        let succeededTasks = sessionManager.tasks.filter { $0.status == .succeeded }
        for task in succeededTasks {
            let url = task.url.absoluteString

            DownloadHelper.shared.removePreviewImageUrl(for: url)

            sessionManager.remove(url, completely: completely)
        }
    }

    func clearAllTasks(completely: Bool = false) {
        let urls = sessionManager.tasks.map(\.url.absoluteString)
        for url in urls {
            DownloadHelper.shared.removePreviewImageUrl(for: url)

            sessionManager.remove(url, completely: completely)
        }
    }

    func setCompletionHandler(_ handler: @escaping () -> Void) {
        sessionManager.completionHandler = handler
    }
}
