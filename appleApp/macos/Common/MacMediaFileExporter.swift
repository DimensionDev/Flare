import AppKit
import Foundation
import Kingfisher

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
        let remoteURL = try makeRemoteURL(url)
        let (downloadedURL, response) = try await downloadRemoteFile(
            url: remoteURL,
            customHeaders: customHeaders
        )
        guard isSuccessfulHTTPResponse(response) else {
            throw MacMediaExportError.downloadFailed
        }

        guard let destinationURL = await selectDestinationURL(
            defaultFileName: safeFileName(fileName, fallbackURL: remoteURL, response: response)
        ) else {
            return false
        }

        try copyReplacingItem(at: downloadedURL, to: destinationURL)
        return true
    }

    private static func downloadFile(source: MacMediaExportSource) async throws -> URL {
        let remoteURL = try makeRemoteURL(source.url)
        switch source.kind {
        case .image, .gif:
            let data = try await downloadOriginalImageData(
                url: remoteURL,
                customHeaders: source.customHeaders
            )
            let extensionName = imageFileExtension(
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
            let extensionName = videoFileExtension(from: remoteURL, response: response)
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
            return "\(sanitizeFileName(statusKey))_\(sanitizeFileName(userHandle)).\(extensionName)"
        }

        let originalName = url.deletingPathExtension().lastPathComponent
        let baseName = originalName.isEmpty ? "flare-media-\(UUID().uuidString)" : originalName
        return "\(sanitizeFileName(baseName)).\(extensionName)"
    }

    private static func imageFileExtension(url: URL, data: Data, fallback: String) -> String {
        let originalName = url.path
            .split(separator: "/")
            .last
            .map(String.init) ?? ""
        let lastDotIndex = originalName.lastIndex(of: ".")
        let lastAtIndex = originalName.lastIndex(of: "@")
        let separatorIndex = [lastDotIndex, lastAtIndex].compactMap { $0 }.max()

        if let separatorIndex {
            let nextIndex = originalName.index(after: separatorIndex)
            if nextIndex < originalName.endIndex {
                return String(originalName[nextIndex...])
            }
        }

        if data.starts(with: [0xFF, 0xD8, 0xFF]) {
            return "jpg"
        }
        if data.starts(with: [0x89, 0x50, 0x4E, 0x47]) {
            return "png"
        }
        if data.starts(with: [0x47, 0x49, 0x46]) {
            return "gif"
        }
        if data.starts(with: [0x52, 0x49, 0x46, 0x46]) {
            return "webp"
        }
        if data.starts(with: [0x49, 0x49, 0x2A, 0x00]) || data.starts(with: [0x4D, 0x4D, 0x00, 0x2A]) {
            return "tiff"
        }
        return fallback
    }

    private static func videoFileExtension(from url: URL, response: URLResponse?) -> String {
        let pathExtension = url.pathExtension.lowercased()
        if !pathExtension.isEmpty, pathExtension != "m3u8" {
            return pathExtension
        }

        switch response?.mimeType?.lowercased() {
        case "video/quicktime":
            return "mov"
        case "video/webm":
            return "webm"
        case "video/x-m4v":
            return "m4v"
        default:
            return "mp4"
        }
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

    private static func sanitizeFileName(_ value: String) -> String {
        let sanitized = value.map { character in
            if character.isASCII,
               character.isLetter || character.isNumber || character == "." || character == "_" || character == "-" {
                return character
            }
            return "_"
        }.map(String.init).joined()
        return sanitized.isEmpty ? "flare-media" : sanitized
    }

    private static func safeFileName(_ fileName: String, fallbackURL: URL, response: URLResponse?) -> String {
        let sourceName = fileName.trimmedNonEmpty
            ?? response?.suggestedFilename?.trimmedNonEmpty
            ?? fallbackURL.lastPathComponent.trimmedNonEmpty
            ?? "file"
        let safeName = sourceName.map { character -> Character in
            if character == "/" ||
                character == "\\" ||
                character.unicodeScalars.contains(where: { $0.value < 32 || $0.value == 127 }) {
                return "_"
            }
            return character
        }
        return String(safeName).trimmedNonEmpty ?? "file"
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

private extension String {
    var trimmedNonEmpty: String? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}
