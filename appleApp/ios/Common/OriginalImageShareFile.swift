import FlareAppleCore
import Foundation
import Kingfisher
import KotlinSharedUI

struct OriginalImageShareFile {
    static func make(
        url: String,
        customHeaders: [String: String]?,
        statusKey: String? = nil,
        userHandle: String? = nil
    ) async throws -> URL {
        let imageURL = try makeImageURL(url)
        let result = try await KingfisherManager.shared.downloader.downloadImage(
            with: imageURL,
            options: kingfisherOptions(customHeaders: customHeaders)
        )
        let extensionName = AppleMediaFileExtension.image(url: imageURL, data: result.originalData)
        let fileName = makeFileName(
            url: imageURL,
            statusKey: statusKey,
            userHandle: userHandle,
            extensionName: extensionName
        )
        let fileURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(fileName)
        try result.originalData.write(to: fileURL, options: .atomic)
        return fileURL
    }

    private static func makeImageURL(_ url: String) throws -> URL {
        guard let imageURL = URL(string: url) else {
            throw URLError(.badURL)
        }
        return imageURL
    }

    private static func kingfisherOptions(customHeaders: [String: String]?) -> KingfisherOptionsInfo {
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

    private static func makeFileName(
        url: URL,
        statusKey: String?,
        userHandle: String?,
        extensionName: String
    ) -> String {
        if let statusKey, let userHandle {
            let safeStatusKey = MediaFileNamePolicy.shared.sanitizeFileName(value: statusKey, fallback: "")
            let safeUserHandle = MediaFileNamePolicy.shared.sanitizeFileName(value: userHandle, fallback: "")
            return "\(safeStatusKey)_\(safeUserHandle).\(extensionName)"
        }

        let originalName = url.deletingPathExtension().lastPathComponent
        let baseName = originalName.isEmpty ? "flare-share-\(UUID().uuidString)" : originalName
        let safeBaseName = MediaFileNamePolicy.shared.sanitizeFileName(value: baseName, fallback: "")
        return "\(safeBaseName).\(extensionName)"
    }
}
