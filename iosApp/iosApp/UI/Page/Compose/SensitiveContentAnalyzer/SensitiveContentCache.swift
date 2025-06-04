import Foundation

struct CacheItem {
    let url: String
    let isSensitive: Bool
    let timestamp: Date
}

class SensitiveContentCache {
    static let shared = SensitiveContentCache()

    private var cache: [CacheItem] = []
    private let maxSize = 200
    private let queue = DispatchQueue(label: "sensitive.content.cache", qos: .utility)

    private init() {}

    func get(for url: String) -> Bool? {
        queue.sync {
            cache.first { $0.url == url }?.isSensitive
        }
    }

    func set(url: String, isSensitive: Bool) {
        queue.async { [weak self] in
            guard let self else { return }

            cache.removeAll { $0.url == url }

            let newItem = CacheItem(url: url, isSensitive: isSensitive, timestamp: Date())
            cache.append(newItem)

            if cache.count > maxSize {
                cache.removeFirst()
            }
        }
    }
}
