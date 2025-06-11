
import SwiftUI
import TwitterText

class FlareTextCache {
    static let shared = FlareTextCache()
    private let cache = NSCache<NSString, NSAttributedString>()
    private let queue = DispatchQueue(label: "flare.text.cache", qos: .utility)

    private init() {
        cache.countLimit = 500
        cache.totalCostLimit = 50 * 1024 * 1024
    }

    func getCachedText(for key: String) -> NSAttributedString? {
        cache.object(forKey: NSString(string: key))
    }

    func setCachedText(_ attributedString: NSAttributedString, for key: String) {
        let cost = attributedString.length * 2
        cache.setObject(attributedString, forKey: NSString(string: key), cost: cost)
    }

    func generateCacheKey(text: String, markdownText: String, style: FlareTextStyle.Style, renderEngine _: RenderEngine) -> String {
        let styleHash = "\(style.font.pointSize)_\(style.textColor.description)"
        return "\(text.hashValue)_\(markdownText.hashValue)_\(styleHash)"
    }
}
