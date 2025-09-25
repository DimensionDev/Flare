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
                    if let color = group.displayItem.color {
                        StatusActionIcon(item: group.displayItem)
                            .foregroundStyle(color)
                    } else {
                        StatusActionIcon(item: group.displayItem)
                    }
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
                                if let color = group.displayItem.color {
                                    Text(text)
                                        .foregroundStyle(color)
                                } else {
                                    Text(text)
                                }
                            }
                        }
                    } icon: {
                        if let color = group.displayItem.color {
                            StatusActionIcon(item: group.displayItem)
                                .foregroundStyle(color)
                        } else {
                            StatusActionIcon(item: group.displayItem)
                        }
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
                        if let color = data.color {
                            Text(data.textKey)
                                .foregroundStyle(color)
                        } else {
                            Text(data.textKey)
                        }
                    } else if let text = data.countText {
                        if let color = data.color {
                            Text(text)
                                .foregroundStyle(color)
                        } else {
                            Text(text)
                        }
                    }
                }
            } icon: {
                if let color = data.color {
                    StatusActionIcon(item: data)
                        .foregroundStyle(color)
                } else {
                    StatusActionIcon(item: data)
                }
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
    var color: Color? {
        if let colorized = self as? StatusActionItemColorized {
            switch colorized.color {
            case .contentColor: nil
            case .error: Color(.systemRed)
            case .primaryColor: Color.accentColor
            case .red: Color(.systemRed)
            }
        } else {
            nil
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

    var body: some View {
        Group {
            switch onEnum(of: item) {
            case .bookmark(let bookmarked):
                if bookmarked.bookmarked {
                    Image("fa-bookmark.fill")
                } else {
                    Image("fa-bookmark")
                }

            case .delete:
                Image("fa-trash")

            case .like(let liked):
                if liked.liked {
                    Image("fa-heart.fill")
                } else {
                    Image("fa-heart")
                }

            case .more:
                Image("fa-ellipsis")

            case .quote:
                Image("fa-quote-left")

            case .reaction(let reacted):
                if reacted.reacted {
                    Image("fa-minus")
                } else {
                    Image("fa-plus")
                }

            case .reply:
                Image("fa-reply")

            case .report:
                Image("fa-circle-info")

            case .retweet:
                Image("fa-retweet")

            case .comment:
                Image("fa-comment-dots")
            }
        }
    }
}
