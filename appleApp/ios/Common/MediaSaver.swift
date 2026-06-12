import AppleFontAwesome
import Foundation
import GSPlayer
import Kingfisher
import Photos
import UIKit
import SwiftUI
import Drops

class MediaSaver: NSObject, UIDocumentPickerDelegate {
    private override init() {}
    
    static let shared = MediaSaver()
    private var pendingFileExportDirectories: [ObjectIdentifier: URL] = [:]
    
    func saveImage(url: String, customHeaders: [String: String]? = nil) {
        if let remoteUrl = URL(string: url) {
            saveRemoteOriginalDataToPhotos(from: remoteUrl, customHeaders: customHeaders)
        }
    }

    func saveVideo(url: String, customHeaders: [String: String]? = nil) {
        guard let remoteUrl = URL(string: url),
              remoteUrl.pathExtension.lowercased() != "m3u8" else {
            showSaveResult(success: false, mediaType: .video)
            return
        }

        if let cachedFileURL = completeCachedVideoFileURL(for: remoteUrl) {
            saveVideoFileToPhotos(cachedFileURL)
            return
        }

        showDownloadStarted(mediaType: .video)
        downloadRemoteVideoToPhotos(from: remoteUrl, customHeaders: customHeaders)
    }

    func saveFile(url: String, fileName: String, customHeaders: [String: String]? = nil) {
        guard let remoteUrl = URL(string: url) else {
            showSaveResult(success: false, mediaType: .file)
            return
        }

        showDownloadStarted(mediaType: .file)
        downloadRemoteFileForExport(from: remoteUrl, fileName: fileName, customHeaders: customHeaders)
    }

    private func saveRemoteOriginalDataToPhotos(from url: URL, customHeaders: [String: String]?) {
        KingfisherManager.shared.downloader.downloadImage(with: url, options: kingfisherOptions(customHeaders: customHeaders), progressBlock: nil) { result in
            switch result {
            case .success(let v):
                self.saveOriginalDataToPhotos(v.originalData)
            case .failure:
                self.showSaveResult(success: false)
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

    private func downloadRemoteVideoToPhotos(from url: URL, customHeaders: [String: String]?) {
        var request = URLRequest(url: url)
        customHeaders?.forEach { key, value in
            request.setValue(value, forHTTPHeaderField: key)
        }

        URLSession.shared.downloadTask(with: request) { temporaryURL, response, error in
            guard error == nil, let temporaryURL else {
                self.showSaveResult(success: false, mediaType: .video)
                return
            }

            let fileExtension = Self.videoFileExtension(from: url, response: response)
            let targetURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("flare-video-\(UUID().uuidString)")
                .appendingPathExtension(fileExtension)

            do {
                try FileManager.default.copyItem(at: temporaryURL, to: targetURL)
                self.saveVideoFileToPhotos(targetURL, removeWhenDone: true)
            } catch {
                self.showSaveResult(success: false, mediaType: .video)
            }
        }.resume()
    }

    private func downloadRemoteFileForExport(from url: URL, fileName: String, customHeaders: [String: String]?) {
        var request = URLRequest(url: url)
        customHeaders?.forEach { key, value in
            request.setValue(value, forHTTPHeaderField: key)
        }

        URLSession.shared.downloadTask(with: request) { temporaryURL, response, error in
            guard error == nil,
                  let temporaryURL,
                  Self.isSuccessfulHTTPResponse(response) else {
                self.showSaveResult(success: false, mediaType: .file)
                return
            }

            let exportDirectory = FileManager.default.temporaryDirectory
                .appendingPathComponent("flare-file-\(UUID().uuidString)", isDirectory: true)
            let exportURL = exportDirectory
                .appendingPathComponent(Self.safeFileName(fileName, fallbackURL: url, response: response))

            do {
                try FileManager.default.createDirectory(at: exportDirectory, withIntermediateDirectories: true)
                try FileManager.default.copyItem(at: temporaryURL, to: exportURL)
                self.presentFileExportPicker(fileURL: exportURL, cleanupDirectory: exportDirectory)
            } catch {
                try? FileManager.default.removeItem(at: exportDirectory)
                self.showSaveResult(success: false, mediaType: .file)
            }
        }.resume()
    }

    nonisolated private func saveOriginalDataToPhotos(_ data: Data) {
        PHPhotoLibrary.shared().performChanges {
            let request = PHAssetCreationRequest.forAsset()
            request.addResource(with: .photo, data: data, options: nil)
        } completionHandler: { success, error in
            self.showSaveResult(success: success && error == nil)
        }
    }

    nonisolated private func saveVideoFileToPhotos(_ fileURL: URL, removeWhenDone: Bool = false) {
        PHPhotoLibrary.shared().performChanges {
            let request = PHAssetCreationRequest.forAsset()
            request.addResource(with: .video, fileURL: fileURL, options: nil)
        } completionHandler: { success, error in
            if removeWhenDone {
                try? FileManager.default.removeItem(at: fileURL)
            }
            self.showSaveResult(success: success && error == nil, mediaType: .video)
        }
    }

    nonisolated private static func videoFileExtension(from url: URL, response: URLResponse?) -> String {
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

    nonisolated private static func isSuccessfulHTTPResponse(_ response: URLResponse?) -> Bool {
        guard let response = response as? HTTPURLResponse else {
            return true
        }
        return (200..<300).contains(response.statusCode)
    }

    nonisolated private static func safeFileName(_ fileName: String, fallbackURL: URL, response: URLResponse?) -> String {
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
        let value = String(safeName).trimmedNonEmpty
        return value ?? "file"
    }

    private func kingfisherOptions(customHeaders: [String: String]?) -> KingfisherOptionsInfo {
        guard let customHeaders, !customHeaders.isEmpty else {
            return []
        }
        return [.requestModifier(AnyModifier { request in
            var request = request
            for (key, value) in customHeaders {
                request.setValue(value, forHTTPHeaderField: key)
            }
            return request
        })]
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
                    icon: .faPhotoFilm
                )
            )
        }
    }

    nonisolated private func presentFileExportPicker(fileURL: URL, cleanupDirectory: URL) {
        Task { @MainActor in
            guard let presenter = Self.topViewController() else {
                try? FileManager.default.removeItem(at: cleanupDirectory)
                self.showSaveResult(success: false, mediaType: .file)
                return
            }

            let picker = UIDocumentPickerViewController(forExporting: [fileURL], asCopy: true)
            picker.delegate = self
            self.pendingFileExportDirectories[ObjectIdentifier(picker)] = cleanupDirectory
            presenter.present(picker, animated: true)
        }
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        cleanupFileExport(for: controller)
        showSaveResult(success: true, mediaType: .file)
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        cleanupFileExport(for: controller)
    }

    private func cleanupFileExport(for controller: UIDocumentPickerViewController) {
        let identifier = ObjectIdentifier(controller)
        guard let cleanupDirectory = pendingFileExportDirectories.removeValue(forKey: identifier) else {
            return
        }
        try? FileManager.default.removeItem(at: cleanupDirectory)
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

private enum MediaSaveType {
    case image
    case video
    case file

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
        }
    }

    var downloadStartedTitle: String {
        switch self {
        case .image:
            return .init(localized: "notification_download_started")
        case .video:
            return .init(localized: "notification_download_video_started")
        case .file:
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
