import Foundation
import Kingfisher

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
        let extensionName = fileExtension(url: imageURL, data: result.originalData)
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

    private static func fileExtension(url: URL, data: Data) -> String {
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
        return "jpg"
    }

    private static func makeFileName(
        url: URL,
        statusKey: String?,
        userHandle: String?,
        extensionName: String
    ) -> String {
        if let statusKey, let userHandle {
            return "\(sanitizeFileName(statusKey))_\(sanitizeFileName(userHandle)).\(extensionName)"
        }

        let originalName = url.deletingPathExtension().lastPathComponent
        let baseName = originalName.isEmpty ? "flare-share-\(UUID().uuidString)" : originalName
        return "\(sanitizeFileName(baseName)).\(extensionName)"
    }

    private static func sanitizeFileName(_ value: String) -> String {
        value.map { character in
            if character.isASCII,
               character.isLetter || character.isNumber || character == "." || character == "_" || character == "-" {
                return character
            }
            return "_"
        }.map(String.init).joined()
    }
}
