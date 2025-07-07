import SwiftUI
import WaterfallGrid
import shared


extension TimelineItem{
    func createMicroBlogKey(from item: TimelineItem) -> MicroBlogKey {
        let host = extractHostFromPlatformType(item.platformType)
        return MicroBlogKey(id: item.id, host: host)
    }
    
    func extractHostFromPlatformType(_ platformType: String) -> String {
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
