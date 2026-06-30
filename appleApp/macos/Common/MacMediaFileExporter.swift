import AppKit
import FlareAppleCore
import Foundation
import Kingfisher
import KotlinSharedUI

struct MacMediaShareContext: Hashable {
    let statusKey: String?
    let userHandle: String?
}

struct MacMediaExportSource: Identifiable, Hashable {
    enum Kind: String, Hashable {
        case image
        case gif
        case video
    }

    let kind: Kind
    let url: String
    let customHeaders: [String: String]?
    let shareContext: MacMediaShareContext?

    var id: String {
        [
            kind.rawValue,
            url,
            customHeaders?
                .sorted { $0.key < $1.key }
                .map { "\($0.key)=\($0.value)" }
                .joined(separator: "&"),
            shareContext?.statusKey,
            shareContext?.userHandle,
        ]
        .compactMap { $0 }
        .joined(separator: "|")
    }

    var supportsSharing: Bool {
        switch kind {
        case .image, .gif:
            true
        case .video:
            false
        }
    }
}

enum MacMediaFileExporter {
    static func makeShareFile(source: MacMediaExportSource) async throws -> URL {
        guard source.supportsSharing else {
            throw MacMediaExportError.unsupportedShare
        }
        return try await downloadFile(source: source)
    }

    @MainActor
    static func presentSharePicker(
        fileURL: URL,
        relativeTo anchorView: NSView?
    ) throws {
        guard let anchorView,
              anchorView.window != nil else {
            throw MacMediaExportError.missingWindow
        }

        let picker = NSSharingServicePicker(items: [fileURL])
        picker.show(relativeTo: anchorView.bounds, of: anchorView, preferredEdge: .minY)
    }

    static func save(
        source: MacMediaExportSource,
        existingFileURL: URL? = nil
    ) async throws -> Bool {
        let fileURL: URL
        if let existingFileURL {
            fileURL = existingFileURL
        } else {
            fileURL = try await downloadFile(source: source)
        }

        guard let destinationURL = await selectDestinationURL(defaultFileName: fileURL.lastPathComponent) else {
            return false
        }

        try copyReplacingItem(at: fileURL, to: destinationURL)
        return true
    }

    static func saveRemoteFile(
        url: String,
        fileName: String,
        customHeaders: [String: String]?
    ) async throws -> Bool {
        let notification = MacMediaExportNotification()

        do {
            let remoteURL = try makeRemoteURL(url)
            notification.progress()
            let (downloadedURL, response) = try await downloadRemoteFile(
                url: remoteURL,
                customHeaders: customHeaders
            )
            guard isSuccessfulHTTPResponse(response) else {
                throw MacMediaExportError.downloadFailed
            }

            let sourceName = fileName.trimmedNonEmpty
                ?? response.suggestedFilename?.trimmedNonEmpty
                ?? remoteURL.lastPathComponent.trimmedNonEmpty
                ?? "file"
            guard let destinationURL = await selectDestinationURL(
                defaultFileName: MediaFileNamePolicy.shared.safeDownloadFileName(
                    value: sourceName,
                    fallback: "file"
                )
            ) else {
                notification.finishProgress()
                return false
            }

            try copyReplacingItem(at: downloadedURL, to: destinationURL)
            notification.success()
            return true
        } catch {
            notification.error()
            throw error
        }
    }

    private static func downloadFile(source: MacMediaExportSource) async throws -> URL {
        let remoteURL = try makeRemoteURL(source.url)
        switch source.kind {
        case .image, .gif:
            let data = try await downloadOriginalImageData(
                url: remoteURL,
                customHeaders: source.customHeaders
            )
            let extensionName = AppleMediaFileExtension.image(
                url: remoteURL,
                data: data,
                fallback: source.kind == .gif ? "gif" : "jpg"
            )
            let fileURL = temporaryFileURL(
                url: remoteURL,
                shareContext: source.shareContext,
                extensionName: extensionName
            )
            try data.write(to: fileURL, options: .atomic)
            return fileURL

        case .video:
            guard remoteURL.pathExtension.lowercased() != "m3u8" else {
                throw MacMediaExportError.unsupportedStreamingVideo
            }
            let (downloadedURL, response) = try await downloadRemoteFile(
                url: remoteURL,
                customHeaders: source.customHeaders
            )
            guard isSuccessfulHTTPResponse(response) else {
                throw MacMediaExportError.downloadFailed
            }
            let extensionName = AppleMediaFileExtension.video(url: remoteURL, response: response)
            let fileURL = temporaryFileURL(
                url: remoteURL,
                shareContext: source.shareContext,
                extensionName: extensionName
            )
            try copyReplacingItem(at: downloadedURL, to: fileURL)
            return fileURL
        }
    }

    private static func makeRemoteURL(_ value: String) throws -> URL {
        guard let url = URL(string: value) else {
            throw MacMediaExportError.invalidURL
        }
        return url
    }

    private static func downloadOriginalImageData(
        url: URL,
        customHeaders: [String: String]?
    ) async throws -> Data {
        let result = try await KingfisherManager.shared.downloader.downloadImage(
            with: url,
            options: kingfisherOptions(customHeaders: customHeaders)
        )
        return result.originalData
    }

    private static func downloadRemoteFile(
        url: URL,
        customHeaders: [String: String]?
    ) async throws -> (URL, URLResponse) {
        var request = URLRequest(url: url)
        customHeaders?.forEach { key, value in
            request.setValue(value, forHTTPHeaderField: key)
        }
        return try await URLSession.shared.download(for: request)
    }

    private static func kingfisherOptions(customHeaders: [String: String]?) -> KingfisherOptionsInfo {
        guard let customHeaders, !customHeaders.isEmpty else {
            return []
        }
        return [
            .requestModifier(AnyModifier { request in
                var request = request
                for (key, value) in customHeaders {
                    request.setValue(value, forHTTPHeaderField: key)
                }
                return request
            }),
        ]
    }

    private static func temporaryFileURL(
        url: URL,
        shareContext: MacMediaShareContext?,
        extensionName: String
    ) -> URL {
        let directoryURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("flare-media-\(UUID().uuidString)", isDirectory: true)
        try? FileManager.default.createDirectory(at: directoryURL, withIntermediateDirectories: true)
        return directoryURL.appendingPathComponent(
            fileName(url: url, shareContext: shareContext, extensionName: extensionName)
        )
    }

    private static func fileName(
        url: URL,
        shareContext: MacMediaShareContext?,
        extensionName: String
    ) -> String {
        if let statusKey = shareContext?.statusKey,
           let userHandle = shareContext?.userHandle {
            let safeStatusKey = MediaFileNamePolicy.shared.sanitizeFileName(
                value: statusKey,
                fallback: "flare-media"
            )
            let safeUserHandle = MediaFileNamePolicy.shared.sanitizeFileName(
                value: userHandle,
                fallback: "flare-media"
            )
            return "\(safeStatusKey)_\(safeUserHandle).\(extensionName)"
        }

        let originalName = url.deletingPathExtension().lastPathComponent
        let baseName = originalName.isEmpty ? "flare-media-\(UUID().uuidString)" : originalName
        let safeBaseName = MediaFileNamePolicy.shared.sanitizeFileName(
            value: baseName,
            fallback: "flare-media"
        )
        return "\(safeBaseName).\(extensionName)"
    }

    private static func isSuccessfulHTTPResponse(_ response: URLResponse?) -> Bool {
        guard let response = response as? HTTPURLResponse else {
            return true
        }
        return (200..<300).contains(response.statusCode)
    }

    private static func copyReplacingItem(at sourceURL: URL, to destinationURL: URL) throws {
        let didAccess = destinationURL.startAccessingSecurityScopedResource()
        defer {
            if didAccess {
                destinationURL.stopAccessingSecurityScopedResource()
            }
        }

        let fileManager = FileManager.default
        if fileManager.fileExists(atPath: destinationURL.path) {
            try fileManager.removeItem(at: destinationURL)
        }
        try fileManager.copyItem(at: sourceURL, to: destinationURL)
    }

    private static func selectDestinationURL(defaultFileName: String) async -> URL? {
        await withCheckedContinuation { continuation in
            let panel = NSSavePanel()
            panel.canCreateDirectories = true
            panel.isExtensionHidden = false
            panel.nameFieldStringValue = defaultFileName
            panel.begin { response in
                continuation.resume(returning: response == .OK ? panel.url : nil)
            }
        }
    }

}

private enum MacMediaExportError: LocalizedError {
    case invalidURL
    case unsupportedShare
    case unsupportedStreamingVideo
    case downloadFailed
    case missingWindow

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            String(localized: "Invalid media URL")
        case .unsupportedShare:
            String(localized: "This media type cannot be shared yet")
        case .unsupportedStreamingVideo:
            String(localized: "Streaming videos cannot be saved directly")
        case .downloadFailed:
            String(localized: "Failed to download media")
        case .missingWindow:
            String(localized: "Unable to present the share sheet")
        }
    }
}

private struct MacMediaExportNotification {
    let identifier = "media-export-\(UUID().uuidString)"

    func progress() {
        SwiftInAppNotification.shared.notifyProgress(
            identifier: identifier,
            title: progressTitle
        )
    }

    func success() {
        SwiftInAppNotification.shared.notifySuccess(
            identifier: identifier,
            title: successTitle
        )
    }

    func error() {
        SwiftInAppNotification.shared.notifyError(
            identifier: identifier,
            title: errorTitle
        )
    }

    func finishProgress() {
        SwiftInAppNotification.shared.finishProgress(identifier: identifier)
    }

    private var progressTitle: String {
        String(localized: "notification_download_file_started", defaultValue: "Download started", bundle: .main)
    }

    private var successTitle: String {
        String(localized: "notification_save_file_success", defaultValue: "Saved to Files", bundle: .main)
    }

    private var errorTitle: String {
        String(localized: "notification_save_file_error", defaultValue: "Failed to save file", bundle: .main)
    }
}

private extension String {
    var trimmedNonEmpty: String? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}
