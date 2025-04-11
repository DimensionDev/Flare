import Foundation
import SwiftUI

enum StorageFormatter {
    static func formatFileSize(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        formatter.includesUnit = true
        formatter.allowsNonnumericFormatting = false

        if bytes < 1024 {
            formatter.allowedUnits = .useBytes
        } else if bytes < 1024 * 1024 {
            formatter.allowedUnits = .useKB
        } else {
            formatter.allowedUnits = .useMB
        }

        return formatter.string(fromByteCount: bytes)
    }

    static func formatDownloadInfo(fileCount: Int, totalSize: Int64) -> String {
        if fileCount == 0 {
            return "None"
        }
        return "\(fileCount) files， \(formatFileSize(totalSize))"
    }
}
