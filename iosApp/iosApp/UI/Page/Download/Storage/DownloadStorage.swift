import Foundation
import GRDB

struct DownloadRecord: Codable, FetchableRecord, PersistableRecord {
    let id: String
    let url: String
    let size: Int
    let title: String
    let downItemType: String
    let image: String?
    let durationSecond: Int?
    let createTime: Date?
    var status: String

    init(from item: DownloadItem) {
        id = item.id
        url = item.url
        size = item.totalSize
        title = item.fileName
        image = item.imageUrl
        downItemType = item.downItemType.rawValue
        durationSecond = item.durationSecond
        createTime = item.createTime
        status = String(describing: item.status)
    }

    func toDownloadItem() -> DownloadItem {
        DownloadItem(
            id: id,
            url: url,
            totalSize: size,
            fileName: title,
            imageUrl: image,
            downItemType: DownItemType(rawValue: downItemType) ?? .unknown,
            durationSecond: durationSecond,
            createTime: createTime,
            status: DownloadStatus(rawValue: status) ?? .initial
        )
    }
}

actor DownloadStorage {
    private var dbQueue: DatabaseQueue!
    private let fileManager = FileManager.default

    static let shared = DownloadStorage()

    private init() {
        setupDatabase()
    }

    private func setupDatabase() {
        do {
            let fileURL = try FileManager.default
                .url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                .appendingPathComponent("downloads.sqlite")

            dbQueue = try DatabaseQueue(path: fileURL.path)

            try dbQueue.write { db in
                try db.create(table: "downloadRecord", ifNotExists: true) { t in
                    t.column("id", .text).primaryKey()
                    t.column("url", .text)
                    t.column("size", .integer)
                    t.column("title", .text)
                    t.column("image", .text)
                    t.column("downItemType", .text)
                    t.column("durationSecond", .integer)
                    t.column("createTime", .datetime)
                    t.column("status", .text)
                }
            }
        } catch {
            print("Database setup failed: \(error)")
        }
    }

    func save(_ item: DownloadItem) async throws {
        let record = DownloadRecord(from: item)
        try await dbQueue.write { db in
            try record.save(db)
        }
    }

    func updateStatus(id: String, status: DownloadStatus) async throws {
        try await dbQueue.write { db in
            if var record = try DownloadRecord.fetchOne(db, key: id) {
                record.status = String(describing: status)
                try record.save(db)
            }
        }
    }

    func getAllItems() async throws -> [DownloadItem] {
        try await dbQueue.read { db in
            let records = try DownloadRecord.fetchAll(db)
            return records.map { $0.toDownloadItem() }
        }
    }

    func delete(id: String) async throws {
        try await dbQueue.write { db in
            _ = try DownloadRecord.deleteOne(db, key: id)
        }
    }

    func localFilePath(for item: DownloadItem) -> URL? {
        do {
            let documentsURL = try FileManager.default
                .url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                .appendingPathComponent("Downloads", isDirectory: true)

            try FileManager.default.createDirectory(at: documentsURL, withIntermediateDirectories: true)

            return documentsURL.appendingPathComponent(item.fileName)
        } catch {
            print("Failed to get local file path: \(error)")
            return nil
        }
    }

    func getDatabaseSize() -> Int64 {
        do {
            let fileURL = try FileManager.default
                .url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                .appendingPathComponent("downloads.sqlite")

            let attributes = try FileManager.default.attributesOfItem(atPath: fileURL.path)
            return attributes[.size] as? Int64 ?? 0
        } catch {
            print("Failed to get database size: \(error)")
            return 0
        }
    }

    func getDownloadsInfo() -> (fileCount: Int, totalSize: Int64) {
        do {
            let documentsURL = try FileManager.default
                .url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                .appendingPathComponent("Downloads", isDirectory: true)

            guard FileManager.default.fileExists(atPath: documentsURL.path) else {
                return (0, 0)
            }

            let files = try FileManager.default.contentsOfDirectory(at: documentsURL, includingPropertiesForKeys: [.fileSizeKey])

            var totalSize: Int64 = 0
            for file in files {
                let attributes = try file.resourceValues(forKeys: [.fileSizeKey])
                if let size = attributes.fileSize {
                    totalSize += Int64(size)
                }
            }

            return (files.count, totalSize)
        } catch {
            print("Failed to get downloads info: \(error)")
            return (0, 0)
        }
    }

    /// 清理数据库 (删除整个数据库文件)
    func clearDatabase() async throws {
        //    try await dbQueue.write { db in
        //     try db.execute(sql: "DELETE FROM downloadRecord")
        // 先关闭当前数据库连接 (如果可能/需要，GRDB会自动处理文件句柄，但明确关闭更好)
        // dbQueue = nil // 注意：这会使当前的 DownloadStorage 实例失效，需要重新初始化或重启应用

        // 获取数据库文件路径
        let fileURL = try FileManager.default
            .url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: false) // create: false, 因为我们要删除它
            .appendingPathComponent("downloads.sqlite")

        print("Attempting to delete database file at: \(fileURL.path)")

        // 检查文件是否存在并删除
        if FileManager.default.fileExists(atPath: fileURL.path) {
            try FileManager.default.removeItem(at: fileURL)
            print("Database file successfully deleted.")
            // 重置 dbQueue 为 nil，强制下次访问时重新初始化
            // 或者依赖于应用重启来重新执行 setupDatabase
            dbQueue = nil // 让下次访问 setupDatabase 重新创建
            setupDatabase() // 立即重新创建空的数据库文件和表结构
        } else {
            print("Database file not found, nothing to delete.")
        }
    }

    func clearDownloads() throws {
        let documentsURL = try FileManager.default
            .url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("Downloads", isDirectory: true)

        if !FileManager.default.fileExists(atPath: documentsURL.path) {
            try FileManager.default.createDirectory(at: documentsURL, withIntermediateDirectories: true)
            return
        }

        let fileURLs = try FileManager.default.contentsOfDirectory(
            at: documentsURL,
            includingPropertiesForKeys: nil,
            options: .skipsHiddenFiles
        )

        for fileURL in fileURLs {
            try FileManager.default.removeItem(at: fileURL)
        }

        print("Cleared all files in Downloads directory")
    }
}


