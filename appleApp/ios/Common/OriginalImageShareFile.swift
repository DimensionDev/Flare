import FlareAppleCore
import Foundation
import Kingfisher
import KotlinSharedUI

struct OriginalImageShareFile {
    static func make(
        media: UiMediaImage,
        statusKey: String? = nil,
        userHandle: String? = nil,
        onPreparingNeeded: (@Sendable () -> Void)? = nil
    ) async throws -> URL {
        let imageURL = try makeImageURL(media.url)
        if let cachedFileURL = cachedImageFileURL(for: media.cacheKey) {
            let data = try Data(contentsOf: cachedFileURL)
            return try makeShareFile(
                data: data,
                url: imageURL,
                statusKey: statusKey,
                userHandle: userHandle
            )
        }

        onPreparingNeeded?()
        let data = try await MediaTransfer.data(media)
        return try makeShareFile(
            data: data,
            url: imageURL,
            statusKey: statusKey,
            userHandle: userHandle
        )
    }

    private static func cachedImageFileURL(for key: String) -> URL? {
        KingfisherManager.shared.cache.cacheFileURLIfOnDisk(forKey: key)
    }

    private static func makeShareFile(
        data: Data,
        url: URL,
        statusKey: String?,
        userHandle: String?
    ) throws -> URL {
        let extensionName = AppleMediaFileExtension.image(url: url, data: data)
        let fileName = makeFileName(
            url: url,
            statusKey: statusKey,
            userHandle: userHandle,
            extensionName: extensionName
        )
        let fileURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(fileName)
        try data.write(to: fileURL, options: .atomic)
        return fileURL
    }

    private static func makeImageURL(_ url: String) throws -> URL {
        guard let imageURL = URL(string: url) else {
            throw URLError(.badURL)
        }
        return imageURL
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
