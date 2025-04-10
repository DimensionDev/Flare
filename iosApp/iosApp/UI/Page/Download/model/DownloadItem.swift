import Foundation

enum DownloadStatus {
    case initial
    case downloading
    case downloaded
    case removed
    case failed
    case paused
}

extension DownloadStatus {
    init?(rawValue: String) {
        switch rawValue {
        case "initial": self = .initial
        case "downloading": self = .downloading
        case "downloaded": self = .downloaded
        case "removed": self = .removed
        case "paused": self = .paused
        case "failed": self = .failed
        default: return nil
        }
    }
}

enum DownItemType: String {
    case image
    case gif
    case video
    case audio
    case unknown
}

struct DownloadItem: Identifiable {
    let id: String
    let url: String
    var totalSize: Int
    var downloadedSize: Int = 0
    let fileName: String // fileName
    let imageUrl: String? // 可以作为Video 预览图 URL
    let downItemType: DownItemType
    let durationSecond: Int?
    let createTime: Date?
    var status: DownloadStatus

    var progress: Double = 0
    var speed: String = ""
    var remainingTime: TimeInterval = 0
    var error: Error?

    var formattedTotalSize: String {
        guard totalSize > 0 else { return "0 B" }
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useBytes, .useKB, .useMB, .useGB]
        formatter.countStyle = .file
        formatter.includesUnit = true
        formatter.includesCount = true
        formatter.zeroPadsFractionDigits = true
        formatter.allowsNonnumericFormatting = false
        return formatter.string(fromByteCount: Int64(totalSize))
    }

    var formattedDownloadedSize: String {
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useBytes, .useKB, .useMB, .useGB]
        formatter.countStyle = .file
        formatter.includesUnit = true
        formatter.includesCount = true
        formatter.zeroPadsFractionDigits = true
        formatter.allowsNonnumericFormatting = false
        return formatter.string(fromByteCount: Int64(downloadedSize))
    }

    //  时长
    var formattedDuration: String? {
        guard let duration = durationSecond else { return nil }
        let minutes = duration / 60
        let seconds = duration % 60
        return String(format: "%d:%02d", minutes, seconds)
    }

    //  剩余时间
    var formattedRemainingTime: String {
        if remainingTime <= 0 { return "" }
        let minutes = Int(remainingTime) / 60
        let seconds = Int(remainingTime) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }

    var statusDescription: String {
        switch status {
        case .initial:
            "waiting"
        case .downloading:
            if speed.isEmpty {
                "downloading \(Int(progress * 100))%"
            } else {
                "\(speed)/s • \(formattedRemainingTime)"
            }
        case .downloaded:
            "downloaded"
        case .removed:
            "removed"
        case .failed:
            error?.localizedDescription ?? "download failed"
        case .paused:
            "paused"
        }
    }
}
