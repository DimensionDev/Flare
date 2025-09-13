import SwiftUI
import KotlinSharedUI

struct TimelineView: View {
    let data: UiTimeline
    let detailStatusKey: MicroBlogKey? = nil
    
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
                    StatusView(data: status, isDetail: false)
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
