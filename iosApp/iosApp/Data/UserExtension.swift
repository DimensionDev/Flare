import shared
import SwiftUI
import WaterfallGrid

extension User {
    /// 从User创建MicroBlogKey
    func createMicroBlogKey() -> MicroBlogKey {
        let host = extractHostFromHandle(handle)
        return MicroBlogKey(id: key, host: host)
    }

    /// 从用户handle提取host信息
    private func extractHostFromHandle(_ handle: String) -> String {
        // handle格式通常是 @username@host 或 @username
        if handle.contains("@") {
            let components = handle.components(separatedBy: "@")
            if components.count >= 3 {
                // @username@host 格式
                return components[2]
            } else if components.count == 2 {
                // @username 格式，需要根据其他信息推断
                return "mastodon.social" // 默认值
            }
        }
        return "unknown.host"
    }
}
