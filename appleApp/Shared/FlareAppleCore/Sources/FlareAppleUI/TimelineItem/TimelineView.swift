import SwiftUI
import KotlinSharedUI
import FlareAppleCore

public struct TimelineView: View {
    private let data: UiTimelineV2
    private let detailStatusKey: MicroBlogKey?
    private let showTranslate: Bool
    @Environment(\.timelineAppearance.fullWidthPost) private var fullWidthPost
    @ScaledMetric(relativeTo: .caption) private var iconSize: CGFloat = 15

    public init(data: UiTimelineV2, detailStatusKey: MicroBlogKey?, showTranslate: Bool = true) {
        self.data = data
        self.detailStatusKey = detailStatusKey
        self.showTranslate = showTranslate
    }

    public var body: some View {
        switch onEnum(of: data) {
        case .feed(let feed):
            FeedView(data: feed)
        case .post(let post):
            VStack {
                StatusView(data: post, isDetail: detailStatusKey == post.statusKey, showTranslate: showTranslate)
            }
        case .timelinePostItem(let item):
            let bodyPost = item.presentation.repost ?? item.post
            VStack {
                messageView(item.presentation.message, topMessageOnly: false)
                StatusView(
                    data: bodyPost,
                    isDetail: detailStatusKey == bodyPost.statusKey,
                    showTranslate: showTranslate,
                    inlineParents: Array(item.presentation.inlineParents),
                    quotes: Array(item.presentation.quotes)
                )
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

public extension TimelineView {
    init(data: UiTimelineV2) {
        self.init(data: data, detailStatusKey: nil)
    }
}

public struct TimelinePlaceholderView: View {
    public init() {}

    public var body: some View {
        VStack(
            alignment: .leading,
        ) {
            UserLoadingView()
            Text(
                verbatim: "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                    "Pellentesque eget eros justo. Duis feugiat tortor sed lectus euismod iaculis. " +
                    "Donec aliquam sem dui, id facilisis velit luctus eget. Nam ac mattis sapien. " +
                    "Morbi ultrices diam at accumsan hendrerit. Donec vitae venenatis nulla. Nullam condimentum pharetra venenatis."
            )
        }
        .redacted(reason: .placeholder)
    }
}
