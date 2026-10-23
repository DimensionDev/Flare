import FlareAppleUI
import FlareAppleCore
import Foundation
import GSPlayer
import Kingfisher
import KotlinSharedUI
import Photos
import UIKit
import SwiftUI
import Drops

class MediaSaver: NSObject, UIDocumentPickerDelegate {
    private override init() {}
    
    static let shared = MediaSaver()
    private var pendingFileExports: [ObjectIdentifier: PendingFileExport] = [:]

    nonisolated static func showPreparingMedia() {
        showPreparingMediaNotification()
    }

    func showDownloadStarted() {
        showDownloadStarted(mediaType: .file)
    }

    func showBatchSaveResult(success: Bool) {
        showSaveResult(success: success, mediaType: .photoAlbum)
    }

    func saveUgoira(_ media: UiMediaUgoira, fileName: String, showsSaveResult: Bool = true, completion: (@Sendable (Bool) -> Void)? = nil) {
        UgoiraExporter.shared.save(media, fileName: fileName, write: { url in
            let success: Bool = await withCheckedContinuation { continuation in
                self.saveVideoFileToPhotos(url, showsSaveResult: showsSaveResult) { continuation.resume(returning: $0) }
            }
            if !success { throw CocoaError(.fileWriteUnknown) }
        }, completion: { success in completion?(success) })
    }

    func saveImage(
        media: any UiMedia,
        showsDownloadStarted: Bool = true,
        showsSaveResult: Bool = true,
        completion: (@Sendable (Bool) -> Void)? = nil
    ) {
        guard let remoteUrl = URL(string: media.urlForDownload) else {
            finishSave(success: false, mediaType: .image, showsSaveResult: showsSaveResult, completion: completion)
            return
        }
        if showsDownloadStarted {
            showDownloadStarted(mediaType: .image)
        }
        saveRemoteOriginalDataToPhotos(
            media: media,
            showsSaveResult: showsSaveResult,
            completion: completion
        )
    }

    func saveImage(_ image: UIImage) {
        saveImageToPhotos(image)
    }

    func saveVideo(
        media: any UiMedia,
        showsDownloadStarted: Bool = true,
        showsSaveResult: Bool = true,
        completion: (@Sendable (Bool) -> Void)? = nil
    ) {
        guard let remoteUrl = URL(string: media.urlForDownload),
              remoteUrl.pathExtension.lowercased() != "m3u8" else {
            finishSave(success: false, mediaType: .video, showsSaveResult: showsSaveResult, completion: completion)
            return
        }

        if showsDownloadStarted {
            showDownloadStarted(mediaType: .video)
        }

        if media.customHeaders?.isEmpty != false, let cachedFileURL = completeCachedVideoFileURL(for: remoteUrl) {
            saveVideoFileToPhotos(
                cachedFileURL,
                showsSaveResult: showsSaveResult,
                completion: completion
            )
            return
        }

        downloadRemoteVideoToPhotos(
            media: media,
            showsSaveResult: showsSaveResult,
            completion: completion
        )
    }

    func saveFile(
        media: any UiMedia,
        fileName: String,
        showsDownloadStarted: Bool = true,
        showsSaveResult: Bool = true,
        completion: (@Sendable (Bool) -> Void)? = nil
    ) {
        guard let remoteUrl = URL(string: media.urlForDownload) else {
            finishSave(success: false, mediaType: .file, showsSaveResult: showsSaveResult, completion: completion)
            return
        }

        if showsDownloadStarted {
            showDownloadStarted(mediaType: .file)
        }
        downloadRemoteFileForExport(
            media: media,
            fileName: fileName,
            showsSaveResult: showsSaveResult,
            completion: completion
        )
    }

    private func saveRemoteOriginalDataToPhotos(
        media: any UiMedia,
        showsSaveResult: Bool,
        completion: (@Sendable (Bool) -> Void)?
    ) {
        Task {
            do {
                let data = try await MediaTransfer.data(media)
                self.saveOriginalDataToPhotos(data, showsSaveResult: showsSaveResult, completion: completion)
            } catch {
                self.finishSave(success: false, mediaType: .image, showsSaveResult: showsSaveResult, completion: completion)
            }
        }
    }

    private func completeCachedVideoFileURL(for url: URL) -> URL? {
        let filePath = VideoCacheManager.cachedFilePath(for: url)
        guard FileManager.default.fileExists(atPath: filePath),
              CachedVideoConfiguration.load(forVideoFilePath: filePath)?.isComplete == true else {
            return nil
        }
        return URL(fileURLWithPath: filePath)
    }

    private func downloadRemoteVideoToPhotos(
        media: any UiMedia,
        showsSaveResult: Bool,
        completion: (@Sendable (Bool) -> Void)?
    ) {
        Task {
            guard let url = URL(string: media.urlForDownload),
                  let (temporaryURL, response) = try? await MediaTransfer.download(media) else {
                self.finishSave(
                    success: false,
                    mediaType: .video,
                    showsSaveResult: showsSaveResult,
                    completion: completion
                )
                return
            }

            defer { try? FileManager.default.removeItem(at: temporaryURL) }
            let fileExtension = AppleMediaFileExtension.video(url: url, response: response)
            let targetURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("flare-video-\(UUID().uuidString)")
                .appendingPathExtension(fileExtension)

            do {
                try FileManager.default.copyItem(at: temporaryURL, to: targetURL)
                self.saveVideoFileToPhotos(
                    targetURL,
                    removeWhenDone: true,
                    showsSaveResult: showsSaveResult,
                    completion: completion
                )
            } catch {
                self.finishSave(
                    success: false,
                    mediaType: .video,
                    showsSaveResult: showsSaveResult,
                    completion: completion
                )
            }
        }
    }

    private func downloadRemoteFileForExport(
        media: any UiMedia,
        fileName: String,
        showsSaveResult: Bool,
        completion: (@Sendable (Bool) -> Void)?
    ) {
        Task {
            guard let url = URL(string: media.urlForDownload),
                  let (temporaryURL, response) = try? await MediaTransfer.download(media) else {
                self.finishSave(
                    success: false,
                    mediaType: .file,
                    showsSaveResult: showsSaveResult,
                    completion: completion
                )
                return
            }

            defer { try? FileManager.default.removeItem(at: temporaryURL) }
            let exportDirectory = FileManager.default.temporaryDirectory
                .appendingPathComponent("flare-file-\(UUID().uuidString)", isDirectory: true)
            let sourceName = fileName.trimmedNonEmpty
                ?? response.suggestedFilename?.trimmedNonEmpty
                ?? url.lastPathComponent.trimmedNonEmpty
                ?? "file"
            let exportURL = exportDirectory
                .appendingPathComponent(
                    MediaFileNamePolicy.shared.safeDownloadFileName(value: sourceName, fallback: "file")
                )

            do {
                try FileManager.default.createDirectory(at: exportDirectory, withIntermediateDirectories: true)
                try FileManager.default.copyItem(at: temporaryURL, to: exportURL)
                self.presentFileExportPicker(
                    fileURL: exportURL,
                    cleanupDirectory: exportDirectory,
                    showsSaveResult: showsSaveResult,
                    completion: completion
                )
            } catch {
                try? FileManager.default.removeItem(at: exportDirectory)
                self.finishSave(
                    success: false,
                    mediaType: .file,
                    showsSaveResult: showsSaveResult,
                    completion: completion
                )
            }
        }
    }

    nonisolated private func saveOriginalDataToPhotos(
        _ data: Data,
        showsSaveResult: Bool = true,
        completion: (@Sendable (Bool) -> Void)? = nil
    ) {
        PHPhotoLibrary.shared().performChanges {
            let request = PHAssetCreationRequest.forAsset()
            request.addResource(with: .photo, data: data, options: nil)
        } completionHandler: { success, error in
            self.finishSave(
                success: success && error == nil,
                mediaType: .image,
                showsSaveResult: showsSaveResult,
                completion: completion
            )
        }
    }

    nonisolated private func saveImageToPhotos(_ image: UIImage) {
        PHPhotoLibrary.shared().performChanges {
            PHAssetChangeRequest.creationRequestForAsset(from: image)
        } completionHandler: { success, error in
            self.showSaveResult(success: success && error == nil)
        }
    }

    nonisolated private func saveVideoFileToPhotos(
        _ fileURL: URL,
        removeWhenDone: Bool = false,
        showsSaveResult: Bool = true,
        completion: (@Sendable (Bool) -> Void)? = nil
    ) {
        PHPhotoLibrary.shared().performChanges {
            let request = PHAssetCreationRequest.forAsset()
            request.addResource(with: .video, fileURL: fileURL, options: nil)
        } completionHandler: { success, error in
            if removeWhenDone {
                try? FileManager.default.removeItem(at: fileURL)
            }
            self.finishSave(
                success: success && error == nil,
                mediaType: .video,
                showsSaveResult: showsSaveResult,
                completion: completion
            )
        }
    }

    nonisolated private func finishSave(
        success: Bool,
        mediaType: MediaSaveType = .image,
        showsSaveResult: Bool,
        completion: (@Sendable (Bool) -> Void)?
    ) {
        if showsSaveResult {
            showSaveResult(success: success, mediaType: mediaType)
        }
        completion?(success)
    }
    
    nonisolated private func showSaveResult(success: Bool, mediaType: MediaSaveType = .image) {
        DispatchQueue.main.async {
            Drops.show(
                .init(
                    title: mediaType.title(success: success),
                    icon: UIImage(fontAwesome: success ? .circleCheck : .circleExclamation)
                )
            )
        }
    }

    nonisolated private func showDownloadStarted(mediaType: MediaSaveType) {
        DispatchQueue.main.async {
            Drops.show(
                .init(
                    title: mediaType.downloadStartedTitle,
                    icon: .init(fontAwesome: FontAwesomeIcon.circleDown)
                )
            )
        }
    }

    nonisolated private static func showPreparingMediaNotification() {
        DispatchQueue.main.async {
            Drops.show(
                .init(
                    title: String(localized: "notification_prepare_media_started", defaultValue: "Preparing media"),
                    icon: .init(fontAwesome: FontAwesomeIcon.circleDown)
                )
            )
        }
    }

    nonisolated private func presentFileExportPicker(
        fileURL: URL,
        cleanupDirectory: URL,
        showsSaveResult: Bool,
        completion: (@Sendable (Bool) -> Void)?
    ) {
        Task { @MainActor in
            guard let presenter = Self.topViewController() else {
                try? FileManager.default.removeItem(at: cleanupDirectory)
                self.finishSave(
                    success: false,
                    mediaType: .file,
                    showsSaveResult: showsSaveResult,
                    completion: completion
                )
                return
            }

            let picker = UIDocumentPickerViewController(forExporting: [fileURL], asCopy: true)
            picker.delegate = self
            self.pendingFileExports[ObjectIdentifier(picker)] =
                PendingFileExport(
                    cleanupDirectory: cleanupDirectory,
                    showsSaveResult: showsSaveResult,
                    completion: completion
                )
            presenter.present(picker, animated: true)
        }
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        let pendingExport = cleanupFileExport(for: controller)
        finishSave(
            success: true,
            mediaType: .file,
            showsSaveResult: pendingExport?.showsSaveResult ?? true,
            completion: pendingExport?.completion
        )
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        let pendingExport = cleanupFileExport(for: controller)
        pendingExport?.completion?(false)
    }

    private func cleanupFileExport(for controller: UIDocumentPickerViewController) -> PendingFileExport? {
        let identifier = ObjectIdentifier(controller)
        guard let pendingExport = pendingFileExports.removeValue(forKey: identifier) else {
            return nil
        }
        try? FileManager.default.removeItem(at: pendingExport.cleanupDirectory)
        return pendingExport
    }

    private static func topViewController() -> UIViewController? {
        let rootViewController = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }?
            .windows
            .first { $0.isKeyWindow }?
            .rootViewController
        return topViewController(from: rootViewController)
    }

    private static func topViewController(from viewController: UIViewController?) -> UIViewController? {
        if let navigationController = viewController as? UINavigationController {
            return topViewController(from: navigationController.visibleViewController)
        }
        if let tabBarController = viewController as? UITabBarController {
            return topViewController(from: tabBarController.selectedViewController)
        }
        if let presentedViewController = viewController?.presentedViewController {
            return topViewController(from: presentedViewController)
        }
        return viewController
    }
}

private struct PendingFileExport {
    let cleanupDirectory: URL
    let showsSaveResult: Bool
    let completion: (@Sendable (Bool) -> Void)?
}

private enum MediaSaveType {
    case image
    case video
    case file
    case photoAlbum

    func title(success: Bool) -> String {
        switch self {
        case .image:
            return .init(localized: success ? "notification_save_image_success" : "notification_save_image_error")
        case .video:
            return .init(localized: success ? "notification_save_video_success" : "notification_save_video_error")
        case .file:
            if success {
                return String(localized: "notification_save_file_success", defaultValue: "Saved to Files")
            }
            return String(localized: "notification_save_file_error", defaultValue: "Failed to save file")
        case .photoAlbum:
            if success {
                return String(localized: "notification_save_photo_album_success", defaultValue: "Saved to Photos")
            }
            return String(localized: "notification_save_file_error", defaultValue: "Failed to save file")
        }
    }

    var downloadStartedTitle: String {
        switch self {
        case .image:
            return String(localized: "notification_download_file_started", defaultValue: "Download started")
        case .video:
            return .init(localized: "notification_download_video_started")
        case .file:
            return String(localized: "notification_download_file_started", defaultValue: "Download started")
        case .photoAlbum:
            return String(localized: "notification_download_file_started", defaultValue: "Download started")
        }
    }
}

private extension String {
    nonisolated var trimmedNonEmpty: String? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}

private struct CachedVideoConfiguration: Decodable {
    let info: CachedVideoInfo?
    let fragments: [CachedVideoFragment]

    static func load(forVideoFilePath filePath: String) -> CachedVideoConfiguration? {
        let configurationURL = URL(fileURLWithPath: filePath).appendingPathExtension("cfg")
        guard let data = try? Data(contentsOf: configurationURL) else {
            return nil
        }
        return try? JSONDecoder().decode(CachedVideoConfiguration.self, from: data)
    }

    var isComplete: Bool {
        guard let contentLength = info?.contentLength, contentLength > 0 else {
            return false
        }

        let sortedFragments = fragments.sorted { $0.location < $1.location }
        var coveredUpperBound = 0

        for fragment in sortedFragments {
            if fragment.location > coveredUpperBound {
                return false
            }
            coveredUpperBound = max(coveredUpperBound, fragment.location + fragment.length)
            if coveredUpperBound >= contentLength {
                return true
            }
        }

        return false
    }
}

private struct CachedVideoInfo: Decodable {
    let contentLength: Int
}

private struct CachedVideoFragment: Decodable {
    let location: Int
    let length: Int
}
