import SwiftUI
import KotlinSharedUI

struct StatusActionsView: View {
    let data: [StatusAction]

    var body: some View {
        HStack {
            ForEach(0..<data.count) { index in
                let item = data[index]
                StatusActionView(data: item, useText: false, isFixedWidth: index != data.count - 1)
                    .if(index == data.count - 1) { view in
                        view.frame(maxWidth: .infinity, alignment: .trailing)
                    } else: { view in
                        view
                    }

            }
        }
        .labelIconToTitleSpacing(4)
    }
}

struct StatusActionView: View {
    let data: StatusAction
    let useText: Bool
    let isFixedWidth: Bool
    var body: some View {
        switch onEnum(of: data) {
        case .item(let item):
            StatusActionItemView(data: item, useText: useText, isFixedWidth: isFixedWidth)
        case .group(let group):
            Menu {
                ForEach(0..<group.actions.count) { index in
                    let item = group.actions[index]
                    StatusActionView(data: item, useText: true, isFixedWidth: false)
                }
            } label: {
                if !isFixedWidth && group.displayItem.countText == nil {
                    StatusActionIcon(item: group.displayItem, color: .init(group.displayItem.color))
                } else {
                    Label {
                        ZStack(
                            alignment: .leading
                        ) {
                            if isFixedWidth {
                                Text("0000")
                                    .opacity(0.0)
                            }
                            if let text = group.displayItem.countText {
                                Text(text)
                                    .foregroundStyle(group.displayItem.color)
                            }
                        }
                    } icon: {
                        StatusActionIcon(item: group.displayItem, color: .init(group.displayItem.color))
                    }
                }
            }
        case .asyncActionItem(let asyncItem):
            AsyncStatusActionView(data: asyncItem)
        }
    }
}

struct AsyncStatusActionView: View {
    let data: StatusActionAsyncActionItem
    var body: some View {
        Button {

        } label: {

        }
        .onAppear {
        }
    }
}

struct StatusActionItemView: View {
    @Environment(\.openURL) private var openURL
    let data: StatusActionItem
    let useText: Bool
    let isFixedWidth: Bool
    var body: some View {
        Button {
            if let clickable = data as? StatusActionItemClickable {
                clickable.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
            }
        } label: {
            Label {
                ZStack(
                    alignment: .leading
                ) {
                    if isFixedWidth {
                        Text("0000")
                            .opacity(0.0)
                    }
                    if useText {
                        Text(data.textKey)
                            .foregroundStyle(data.color)
                    } else if let text = data.countText {
                        Text(text)
                            .foregroundStyle(data.color)
                    }
                }
            } icon: {
                StatusActionIcon(item: data, color: UIColor(data.color))
            }
        }
        .sensoryFeedback(.success, trigger: data.color)
        .buttonStyle(.plain)
    }
}

extension StatusActionItem {
    var countText: String? {
        switch onEnum(of: self) {
        case .bookmark(let bookmark): bookmark.humanizedCount
        case .delete: nil
        case .like(let like): like.humanizedCount
        case .more: nil
        case .quote(let quote): quote.humanizedCount
        case .reaction: nil
        case .reply(let reply): reply.humanizedCount
        case .report: nil
        case .retweet(let retweet): retweet.humanizedCount
        case .comment(let comment): comment.humanizedCount
        }

    }
    var color: Color {
        if let colorized = self as? StatusActionItemColorized {
            switch colorized.color {
            case .contentColor: Color(.label)
            case .error: Color(.systemRed)
            case .primaryColor: Color.accentColor
            case .red: Color(.systemRed)
            }
        } else {
            Color(.label)
        }
    }

    var textKey: LocalizedStringResource {
        switch onEnum(of: self) {
        case .bookmark(let bookmarked):
            return bookmarked.bookmarked
                ? LocalizedStringResource("bookmark_remove")
                : LocalizedStringResource("bookmark_add")

        case .delete:
            return LocalizedStringResource("delete")

        case .like(let liked):
            return liked.liked
                ? LocalizedStringResource("unlike")
                : LocalizedStringResource("like")

        case .more:
            return LocalizedStringResource("more")

        case .quote:
            return LocalizedStringResource("quote")

        case .reaction(let reacted):
            return reacted.reacted
                ? LocalizedStringResource("reaction_remove")
                : LocalizedStringResource("reaction_add")

        case .reply:
            return LocalizedStringResource("reply")

        case .report:
            return LocalizedStringResource("report")

        case .retweet(let retweeted):
            return retweeted.retweeted
                ? LocalizedStringResource("retweet_remove")
                : LocalizedStringResource("retweet")
        case .comment:
            return LocalizedStringResource("comment")
        }
    }
}

struct StatusActionIcon: View {
    let item: StatusActionItem
    let color: UIColor

    var body: some View {
        Group {
            switch onEnum(of: item) {
            case .bookmark(let bookmarked):
                if bookmarked.bookmarked {
                    Image("fa-bookmark.fill")
                        .foregroundStyle(Color(color))
                } else {
                    Image("fa-bookmark")
                        .foregroundStyle(Color(color))
                }

            case .delete:
                Image("fa-trash")
                    .foregroundStyle(Color(color))

            case .like(let liked):
                if liked.liked {
                    Image("fa-heart.fill")
                        .foregroundStyle(Color(color))
                } else {
                    Image("fa-heart")
                        .foregroundStyle(Color(color))
                }

            case .more:
                Image("fa-ellipsis")
                    .foregroundStyle(Color(color))

            case .quote:
                Image("fa-quote-left")
                    .foregroundStyle(Color(color))

            case .reaction(let reacted):
                if reacted.reacted {
                    Image("fa-minus")
                        .foregroundStyle(Color(color))
                } else {
                    Image("fa-plus")
                        .foregroundStyle(Color(color))
                }

            case .reply:
                Image("fa-reply")
                    .foregroundStyle(Color(color))

            case .report:
                Image("fa-circle-info")
                    .foregroundStyle(Color(color))

            case .retweet:
                Image("fa-retweet")
                    .foregroundStyle(Color(color))

            case .comment:
                Image("fa-comment-dots")
                    .foregroundStyle(Color(color))
            }
        }
    }
}
