import shared
import SwiftUI
import WaterfallGrid

extension TimelineItem {
    func createMicroBlogKey() -> MicroBlogKey {
        let host = extractHostFromPlatformType(platformType)
        return MicroBlogKey(id: id, host: host)
    }

    private func extractHostFromPlatformType(_ platformType: String) -> String {
        switch platformType.lowercased() {
        case "mastodon":
            "mastodon.social"
        case "bluesky":
            "bsky.app"
        case "misskey":
            "misskey.io"
        case "xqt", "twitter":
            "x.com"
        case "vvo":
            "weibo.com"
        default:
            "unknown.host"
        }
    }
}
