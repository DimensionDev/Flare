import SwiftUI
import KotlinSharedUI

struct TimelineView: View {
    let data: UiTimelineV2
    let detailStatusKey: MicroBlogKey?
    var showTranslate: Bool = true
    @Environment(\.appearanceSettings.fullWidthPost) private var fullWidthPost
    @ScaledMetric(relativeTo: .caption) var iconSize: CGFloat = 15

    var body: some View {
        switch onEnum(of: data) {
        case .feed(let feed):
            FeedView(data: feed)
        case .post(let post):
            VStack {
                messageView(post.message, topMessageOnly: false)
                StatusView(data: post, isDetail: detailStatusKey == post.statusKey, showTranslate: showTranslate)
            }
        case .user(let user):
            VStack {
                messageView(user.message, topMessageOnly: false)
                TimelineUserView(data: user)
            }
        case .userList(let userList):
            VStack {
                messageView(userList.message, topMessageOnly: false)
                UserListView(data: userList)
            }
        case .message(let message):
            messageView(message, topMessageOnly: true)
        }
    }

    @ViewBuilder
    private func messageView(_ message: UiTimelineV2.Message?, topMessageOnly: Bool) -> some View {
        if let message {
            StatusTopMessageView(topMessage: message)
                .if(!fullWidthPost && !topMessageOnly, transform: { view in
                    view.padding(.leading, 44 - iconSize)
                })
                .if(!topMessageOnly, transform: { view in
                    view.lineLimit(1)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                })
        }
    }
}

extension TimelineView {
    init(data: UiTimelineV2) {
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
