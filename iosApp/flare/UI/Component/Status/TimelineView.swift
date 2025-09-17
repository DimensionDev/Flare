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

struct TimelinePlaceholderView: View {
    var body: some View {
        VStack(
            alignment: .leading,
        ) {
            UserLoadingView()
            Text("Lorem ipsum dolor sit amet, consectetur adipiscing elit." +
                 "Pellentesque eget eros justo. Duis feugiat tortor sed lectus euismod iaculis. " +
                 "Donec aliquam sem dui, id facilisis velit luctus eget. Nam ac mattis sapien. " +
                 "Morbi ultrices diam at accumsan hendrerit. Donec vitae venenatis nulla. Nullam condimentum pharetra venenatis.")
        }
        .redacted(reason: .placeholder)
    }
}
