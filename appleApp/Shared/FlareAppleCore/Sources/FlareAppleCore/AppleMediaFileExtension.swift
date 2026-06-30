import Foundation

public enum AppleMediaFileExtension {
    public nonisolated static func image(url: URL, data: Data, fallback: String = "jpg") -> String {
        if let extensionName = extensionFromPath(url.path) {
            return extensionName
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

    public nonisolated static func video(url: URL, response: URLResponse?, fallback: String = "mp4") -> String {
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
            return fallback
        }
    }

    private nonisolated static func extensionFromPath(_ path: String) -> String? {
        let originalName =
            path
                .split(separator: "/")
                .last
                .map(String.init) ?? ""
        let lastDotIndex = originalName.lastIndex(of: ".")
        let lastAtIndex = originalName.lastIndex(of: "@")
        let separatorIndex = [lastDotIndex, lastAtIndex].compactMap { $0 }.max()

        guard let separatorIndex else {
            return nil
        }
        let nextIndex = originalName.index(after: separatorIndex)
        guard nextIndex < originalName.endIndex else {
            return nil
        }
        return String(originalName[nextIndex...])
    }
}
