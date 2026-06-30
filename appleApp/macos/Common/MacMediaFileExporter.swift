import AppKit
import Combine
import FlareAppleCore
import Foundation
import Kingfisher
import KotlinSharedUI

enum MacMediaSaveLocationMode: String {
    case defaultDownloads
    case customDirectory
    case askEveryTime
}

struct MacMediaSaveLocationState {
    let mode: MacMediaSaveLocationMode
    let displayName: String
}

final class MacMediaSaveLocationStore: ObservableObject {
    static let shared = MacMediaSaveLocationStore()

    @Published private(set) var state: MacMediaSaveLocationState

    private let defaults: UserDefaults

    private init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        state = Self.readState(defaults: defaults)
    }

    func setDefaultDownloads() {
        defaults.set(MacMediaSaveLocationMode.defaultDownloads.rawValue, forKey: Keys.mode)
        defaults.removeObject(forKey: Keys.bookmarkData)
        defaults.removeObject(forKey: Keys.displayName)
        publishState()
    }

    func setAskEveryTime() {
        defaults.set(MacMediaSaveLocationMode.askEveryTime.rawValue, forKey: Keys.mode)
        publishState()
    }

    func setCustomDirectory(_ url: URL) throws {
        let bookmarkData = try url.bookmarkData(
            options: [.withSecurityScope],
            includingResourceValuesForKeys: nil,
            relativeTo: nil
        )
        defaults.set(MacMediaSaveLocationMode.customDirectory.rawValue, forKey: Keys.mode)
        defaults.set(bookmarkData, forKey: Keys.bookmarkData)
        defaults.set(url.path, forKey: Keys.displayName)
        publishState()
    }

    func resolveCustomDirectory() -> URL? {
        guard
            Self.readMode(defaults: defaults) == .customDirectory,
            let bookmarkData = defaults.data(forKey: Keys.bookmarkData)
        else {
            return nil
        }

        var isStale = false
        do {
            let url = try URL(
                resolvingBookmarkData: bookmarkData,
                options: [.withSecurityScope],
                relativeTo: nil,
                bookmarkDataIsStale: &isStale
            )
            guard !isStale else {
                setDefaultDownloads()
                return nil
            }

            var isDirectory: ObjCBool = false
            guard FileManager.default.fileExists(atPath: url.path, isDirectory: &isDirectory),
                  isDirectory.boolValue else {
                setDefaultDownloads()
                return nil
            }
            return url
        } catch {
            setDefaultDownloads()
            return nil
        }
    }

    private func publishState() {
        let newState = Self.readState(defaults: defaults)
        if Thread.isMainThread {
            state = newState
        } else {
            DispatchQueue.main.async { [weak self] in
                self?.state = newState
            }
        }
    }

    private static func readState(defaults: UserDefaults) -> MacMediaSaveLocationState {
        switch readMode(defaults: defaults) {
        case .defaultDownloads:
            return MacMediaSaveLocationState(mode: .defaultDownloads, displayName: "Downloads")
        case .customDirectory:
            return MacMediaSaveLocationState(
                mode: .customDirectory,
                displayName: defaults.string(forKey: Keys.displayName) ?? "Custom folder"
            )
        case .askEveryTime:
            return MacMediaSaveLocationState(mode: .askEveryTime, displayName: "Ask Every Time")
        }
    }

    private static func readMode(defaults: UserDefaults) -> MacMediaSaveLocationMode {
        defaults
            .string(forKey: Keys.mode)
            .flatMap(MacMediaSaveLocationMode.init(rawValue:))
            ?? .defaultDownloads
    }

    private enum Keys {
        static let mode = "mediaSaveLocation.mode"
        static let bookmarkData = "mediaSaveLocation.bookmarkData"
        static let displayName = "mediaSaveLocation.displayName"
    }
}

struct MacMediaShareContext: Hashable {
    let statusKey: String?
    let userHandle: String?
}

struct MacMediaExportSource: Identifiable, Hashable {
    enum Kind: String, Hashable {
        case image
        case gif
        case video
        case audio
    }

    let kind: Kind
    let url: String
    let customHeaders: [String: String]?
    let shareContext: MacMediaShareContext?

    init(
        kind: Kind,
        url: String,
        customHeaders: [String: String]?,
        shareContext: MacMediaShareContext?
    ) {
        self.kind = kind
        self.url = url
        self.customHeaders = customHeaders
        self.shareContext = shareContext
    }

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
        case .video, .audio:
            false
        }
    }

    init?(
        media: any UiMedia,
        shareContext: MacMediaShareContext?
    ) {
        switch onEnum(of: media) {
        case .image(let image):
            self.init(
                kind: .image,
                url: image.url,
                customHeaders: image.customHeaders,
                shareContext: shareContext
            )
        case .gif(let gif):
            self.init(
                kind: .gif,
                url: gif.url,
                customHeaders: gif.customHeaders,
                shareContext: shareContext
            )
        case .video(let video):
            self.init(
                kind: .video,
                url: video.url,
                customHeaders: video.customHeaders,
                shareContext: shareContext
            )
        case .audio(let audio):
            self.init(
                kind: .audio,
                url: audio.url,
                customHeaders: audio.customHeaders,
                shareContext: shareContext
            )
        }
    }
}

enum MacMediaFileExporter {
    static func makeShareFile(source: MacMediaExportSource) async throws -> URL {
        guard source.supportsSharing else {
            throw MacMediaExportError.unsupportedShare
        }
        if let cachedFileURL = try cachedShareFile(source: source) {
            return cachedFileURL
        }

        let notification = MacMediaExportNotification()
        notification.preparing()
        do {
            let fileURL = try await downloadFile(source: source)
            notification.finishProgress()
            return fileURL
        } catch {
            notification.error()
            throw error
        }
    }

    private static func cachedShareFile(source: MacMediaExportSource) throws -> URL? {
        let remoteURL = try makeRemoteURL(source.url)
        guard let cachedURL = KingfisherManager.shared.cache.cacheFileURLIfOnDisk(forKey: remoteURL.cacheKey) else {
            return nil
        }
        let data = try Data(contentsOf: cachedURL)
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
        let notification = MacMediaExportNotification()
        let defaultFileName =
            if let existingFileURL {
                existingFileURL.lastPathComponent
            } else {
                try defaultFileName(source: source)
            }

        if let customDirectoryURL = customDirectoryForAutomaticSave() {
            let didAccess = customDirectoryURL.startAccessingSecurityScopedResource()
            if didAccess {
                defer {
                    customDirectoryURL.stopAccessingSecurityScopedResource()
                }
                let destinationURL = customDirectoryURL.appendingPathComponent(
                    MediaFileNamePolicy.shared.safeLocalFileName(
                        value: defaultFileName,
                        fallback: "media"
                    )
                )
                notification.progress()
                do {
                    if let existingFileURL {
                        try copyReplacingItem(at: existingFileURL, to: destinationURL)
                    } else {
                        try await writeSource(source, to: destinationURL)
                    }
                    notification.success()
                    return true
                } catch {
                    notification.error()
                    throw error
                }
            } else {
                MacMediaSaveLocationStore.shared.setDefaultDownloads()
            }
        }

        guard let destinationURL = await selectDestinationURL(defaultFileName: defaultFileName) else {
            return false
        }

        notification.progress()
        do {
            if let existingFileURL {
                try copyReplacingItem(at: existingFileURL, to: destinationURL)
            } else {
                try await writeSource(source, to: destinationURL)
            }
            notification.success()
        } catch {
            notification.error()
            throw error
        }
        return true
    }

    static func saveAll(
        mediaByFileName: [String: any UiMedia]
    ) async throws -> MacMediaBatchExportResult {
        guard !mediaByFileName.isEmpty else {
            return MacMediaBatchExportResult(succeededFileNames: [], failedFileNames: [])
        }

        let notification = MacMediaExportNotification()
        var didUseCustomDirectory = false
        var destinationDirectoryURL: URL
        if let customDirectoryURL = customDirectoryForAutomaticSave() {
            destinationDirectoryURL = customDirectoryURL
            didUseCustomDirectory = true
        } else {
            guard let selectedDirectoryURL = await selectDestinationDirectoryURL() else {
                return MacMediaBatchExportResult(succeededFileNames: [], failedFileNames: [])
            }
            destinationDirectoryURL = selectedDirectoryURL
        }

        var didAccess = destinationDirectoryURL.startAccessingSecurityScopedResource()
        if !didAccess && didUseCustomDirectory {
            MacMediaSaveLocationStore.shared.setDefaultDownloads()
            guard let selectedDirectoryURL = await selectDestinationDirectoryURL() else {
                return MacMediaBatchExportResult(succeededFileNames: [], failedFileNames: [])
            }
            destinationDirectoryURL = selectedDirectoryURL
            didAccess = destinationDirectoryURL.startAccessingSecurityScopedResource()
        }
        defer {
            if didAccess {
                destinationDirectoryURL.stopAccessingSecurityScopedResource()
            }
        }

        notification.progress()
        var succeededFileNames: [String] = []
        var failedFileNames: [String] = []

        for (fileName, media) in mediaByFileName {
            do {
                guard let source = MacMediaExportSource(media: media, shareContext: nil) else {
                    failedFileNames.append(fileName)
                    continue
                }
                let destinationURL = destinationDirectoryURL.appendingPathComponent(
                    MediaFileNamePolicy.shared.safeLocalFileName(
                        value: fileName,
                        fallback: "media"
                    )
                )
                try await writeSource(source, to: destinationURL)
                succeededFileNames.append(fileName)
            } catch {
                failedFileNames.append(fileName)
            }
        }

        if failedFileNames.isEmpty {
            notification.success()
        } else {
            notification.error()
        }

        return MacMediaBatchExportResult(
            succeededFileNames: succeededFileNames,
            failedFileNames: failedFileNames
        )
    }

    static func saveRemoteFile(
        url: String,
        fileName: String,
        customHeaders: [String: String]?
    ) async throws -> Bool {
        let notification = MacMediaExportNotification()

        do {
            let remoteURL = try makeRemoteURL(url)
            let sourceName = fileName.trimmedNonEmpty
                ?? remoteURL.lastPathComponent.trimmedNonEmpty
                ?? "file"
            let defaultFileName = MediaFileNamePolicy.shared.safeDownloadFileName(
                value: sourceName,
                fallback: "file"
            )

            if let customDirectoryURL = customDirectoryForAutomaticSave() {
                let didAccess = customDirectoryURL.startAccessingSecurityScopedResource()
                if didAccess {
                    defer {
                        customDirectoryURL.stopAccessingSecurityScopedResource()
                    }
                    let destinationURL = customDirectoryURL.appendingPathComponent(defaultFileName)
                    notification.progress()
                    _ = try await writeRemoteFile(
                        url: remoteURL,
                        customHeaders: customHeaders,
                        to: destinationURL
                    )
                    notification.success()
                    return true
                } else {
                    MacMediaSaveLocationStore.shared.setDefaultDownloads()
                }
            }

            guard let destinationURL = await selectDestinationURL(
                defaultFileName: defaultFileName
            ) else {
                return false
            }

            notification.progress()
            _ = try await writeRemoteFile(
                url: remoteURL,
                customHeaders: customHeaders,
                to: destinationURL
            )
            notification.success()
            return true
        } catch {
            notification.error()
            throw error
        }
    }

    private static func writeSource(_ source: MacMediaExportSource, to destinationURL: URL) async throws {
        let remoteURL = try makeRemoteURL(source.url)
        switch source.kind {
        case .image, .gif:
            let data = try await downloadOriginalImageData(
                url: remoteURL,
                customHeaders: source.customHeaders
            )
            try writeData(data, to: destinationURL)

        case .video:
            guard remoteURL.pathExtension.lowercased() != "m3u8" else {
                throw MacMediaExportError.unsupportedStreamingVideo
            }
            _ = try await writeRemoteFile(
                url: remoteURL,
                customHeaders: source.customHeaders,
                to: destinationURL
            )

        case .audio:
            _ = try await writeRemoteFile(
                url: remoteURL,
                customHeaders: source.customHeaders,
                to: destinationURL
            )
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

        case .audio:
            let (downloadedURL, response) = try await downloadRemoteFile(
                url: remoteURL,
                customHeaders: source.customHeaders
            )
            guard isSuccessfulHTTPResponse(response) else {
                throw MacMediaExportError.downloadFailed
            }
            let fileURL = temporaryFileURL(
                url: remoteURL,
                shareContext: source.shareContext,
                extensionName: audioFileExtension(url: remoteURL, response: response)
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

    private static func writeRemoteFile(
        url: URL,
        customHeaders: [String: String]?,
        to destinationURL: URL
    ) async throws -> URLResponse {
        var request = URLRequest(url: url)
        customHeaders?.forEach { key, value in
            request.setValue(value, forHTTPHeaderField: key)
        }

        let (bytes, response) = try await URLSession.shared.bytes(for: request)
        guard isSuccessfulHTTPResponse(response) else {
            throw MacMediaExportError.downloadFailed
        }

        let didAccess = destinationURL.startAccessingSecurityScopedResource()
        defer {
            if didAccess {
                destinationURL.stopAccessingSecurityScopedResource()
            }
        }

        do {
            try prepareDestinationFileForWriting(destinationURL)
            let handle = try FileHandle(forWritingTo: destinationURL)
            defer {
                try? handle.close()
            }

            var buffer = Data()
            buffer.reserveCapacity(64 * 1024)
            for try await byte in bytes {
                buffer.append(byte)
                if buffer.count >= 64 * 1024 {
                    try handle.write(contentsOf: buffer)
                    buffer.removeAll(keepingCapacity: true)
                }
            }
            if !buffer.isEmpty {
                try handle.write(contentsOf: buffer)
            }
            return response
        } catch {
            try? FileManager.default.removeItem(at: destinationURL)
            throw error
        }
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

    private static func defaultFileName(source: MacMediaExportSource) throws -> String {
        let remoteURL = try makeRemoteURL(source.url)
        let extensionName =
            switch source.kind {
            case .image:
                AppleMediaFileExtension.image(url: remoteURL, fallback: "jpg")
            case .gif:
                AppleMediaFileExtension.image(url: remoteURL, fallback: "gif")
            case .video:
                AppleMediaFileExtension.video(url: remoteURL, response: nil)
            case .audio:
                audioFileExtension(url: remoteURL, response: nil)
            }
        return fileName(
            url: remoteURL,
            shareContext: source.shareContext,
            extensionName: extensionName
        )
    }

    private static func isSuccessfulHTTPResponse(_ response: URLResponse?) -> Bool {
        guard let response = response as? HTTPURLResponse else {
            return true
        }
        return (200..<300).contains(response.statusCode)
    }

    private static func audioFileExtension(url: URL, response: URLResponse?, fallback: String = "mp3") -> String {
        let pathExtension = url.pathExtension.lowercased()
        if !pathExtension.isEmpty {
            return pathExtension
        }

        switch response?.mimeType?.lowercased() {
        case "audio/aac":
            return "aac"
        case "audio/mp4", "audio/x-m4a":
            return "m4a"
        case "audio/mpeg":
            return "mp3"
        case "audio/ogg":
            return "ogg"
        case "audio/wav", "audio/x-wav":
            return "wav"
        case "audio/webm":
            return "webm"
        default:
            return fallback
        }
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

    private static func writeData(_ data: Data, to destinationURL: URL) throws {
        let didAccess = destinationURL.startAccessingSecurityScopedResource()
        defer {
            if didAccess {
                destinationURL.stopAccessingSecurityScopedResource()
            }
        }

        try prepareDestinationFileForWriting(destinationURL)
        do {
            try data.write(to: destinationURL)
        } catch {
            try? FileManager.default.removeItem(at: destinationURL)
            throw error
        }
    }

    private static func prepareDestinationFileForWriting(_ destinationURL: URL) throws {
        let fileManager = FileManager.default
        if fileManager.fileExists(atPath: destinationURL.path) {
            try fileManager.removeItem(at: destinationURL)
        }
        guard fileManager.createFile(atPath: destinationURL.path, contents: nil) else {
            throw MacMediaExportError.writeFailed
        }
    }

    private static func customDirectoryForAutomaticSave() -> URL? {
        guard MacMediaSaveLocationStore.shared.state.mode == .customDirectory else {
            return nil
        }
        return MacMediaSaveLocationStore.shared.resolveCustomDirectory()
    }

    private static var downloadsDirectoryURL: URL? {
        FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first
    }

    private static func selectDestinationURL(defaultFileName: String) async -> URL? {
        await withCheckedContinuation { continuation in
            let panel = NSSavePanel()
            panel.canCreateDirectories = true
            panel.isExtensionHidden = false
            panel.nameFieldStringValue = defaultFileName
            panel.directoryURL = downloadsDirectoryURL
            panel.begin { response in
                continuation.resume(returning: response == .OK ? panel.url : nil)
            }
        }
    }

    private static func selectDestinationDirectoryURL() async -> URL? {
        await withCheckedContinuation { continuation in
            let panel = NSOpenPanel()
            panel.canChooseFiles = false
            panel.canChooseDirectories = true
            panel.canCreateDirectories = true
            panel.allowsMultipleSelection = false
            panel.directoryURL = downloadsDirectoryURL
            panel.begin { response in
                continuation.resume(returning: response == .OK ? panel.url : nil)
            }
        }
    }
}

struct MacMediaBatchExportResult {
    let succeededFileNames: [String]
    let failedFileNames: [String]

    var totalCount: Int {
        succeededFileNames.count + failedFileNames.count
    }

    var isSuccess: Bool {
        failedFileNames.isEmpty
    }
}

private enum MacMediaExportError: LocalizedError {
    case invalidURL
    case unsupportedShare
    case unsupportedStreamingVideo
    case downloadFailed
    case writeFailed
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
        case .writeFailed:
            String(localized: "Failed to write media")
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

    func preparing() {
        SwiftInAppNotification.shared.notifyProgress(
            identifier: identifier,
            title: preparingTitle
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

    private var preparingTitle: String {
        String(localized: "notification_prepare_media_started", defaultValue: "Preparing media", bundle: .main)
    }

    private var successTitle: String {
        String(localized: "notification_save_file_success", defaultValue: "Saved", bundle: .main)
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
