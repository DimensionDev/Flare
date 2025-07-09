import shared
import SwiftUI
import WaterfallGrid

extension TimelineItem {
    func createMicroBlogKey() -> MicroBlogKey {
           let host = extractHostFromPlatformType(self.platformType)
           return MicroBlogKey(id: self.id, host: host)
       }


       private func extractHostFromPlatformType(_ platformType: String) -> String {
           switch platformType.lowercased() {
           case "mastodon":
               return "mastodon.social"
           case "bluesky":
               return "bsky.app"
           case "misskey":
               return "misskey.io"
           case "xqt", "twitter":
               return "x.com"
           case "vvo":
               return "weibo.com"
           default:
               return "unknown.host"
           }
       }
}
