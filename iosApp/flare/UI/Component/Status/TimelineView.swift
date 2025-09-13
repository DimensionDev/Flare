import SwiftUI
import KotlinSharedUI

struct TimelineView: View {
    let data: UiTimeline
    let detailStatusKey: MicroBlogKey?
    
    var body: some View {
        VStack {
            if let topMessage = data.topMessage {
                StatusTopMessageView(topMessage: topMessage)
            }
            if let content = data.content {
                switch onEnum(of: content) {
                case .feed(let feed):
                    FeedView(data: feed)
                case .status(let status):
                    StatusView(data: status, detailStatusKey: detailStatusKey)
                case .user(let user):
                    TimelineUserView(data: user)
                case .userList(let userList):
                    UserListView(data: userList)
                }
            }
        }
        .id(data.itemKey)
    }
}

extension TimelineView {
    init(data: UiTimeline) {
        self.data = data
        self.detailStatusKey = nil
    }
}
