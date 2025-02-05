import Foundation

enum ResourceType {
    case image
    case video
    case gif
}

protocol MediaSaver {
    func saveToPhotos(url: URL, resourceType: ResourceType) async throws
}

// 默认实现
class DefaultMediaSaver: MediaSaver {
    static let shared = DefaultMediaSaver()

    func saveToPhotos(url _: URL, resourceType _: ResourceType) async throws {
        // TODO: 实现保存功能
    }
}
