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

extension View {
    func confirmationDialog(
        title: String,
        message: String,
        isPresented: Binding<Bool>,
        action: @escaping () -> Void
    ) -> some View {
        confirmationDialog(
            title,
            isPresented: isPresented,
            titleVisibility: .visible,
            actions: {
                Button("OK", role: .destructive, action: action)
                Button("Cancel", role: .cancel) {}
            },
            message: {
                Text(message)
            }
        )
    }

    func successToast(_ message: String, isPresented: Binding<Bool>) -> some View {
        alert(
            "Tips",
            isPresented: isPresented,
            actions: {
                Button("OK", role: .cancel) {}
            },
            message: {
                Text(message)
            }
        )
    }
}
