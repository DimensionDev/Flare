import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

struct StatusActionsView: View {
    @Environment(\.appearanceSettings.postActionStyle) private var postActionStyle
    let data: [StatusAction]
    let useText: Bool

    var body: some View {
        HStack {
            ForEach(0..<data.count, id: \.self) { index in
                let item = data[index]
                if (index == data.count - 1 && postActionStyle == .leftAligned) ||
                    (postActionStyle == .rightAligned && index == 0) ||
                    (postActionStyle == .stretch && index != 0) {
                    Spacer()
                }
                StatusActionView(data: item, useText: useText, isFixedWidth: index != data.count - 1)
            }
        }
        .backport
        .labelIconToTitleSpacing(4)
    }
}

struct StatusActionView: View {
    @Environment(\.appearanceSettings.showNumbers) private var showNumbers
    let data: StatusAction
    let useText: Bool
    let isFixedWidth: Bool
    var body: some View {
        switch onEnum(of: data) {
        case .item(let item):
            StatusActionItemView(data: item, useText: useText, isFixedWidth: isFixedWidth)
        case .group(let group):
            if useText {
                Divider()
                ForEach(0..<group.actions.count, id: \.self) { index in
                    let item = group.actions[index]
                    StatusActionView(data: item, useText: true, isFixedWidth: false)
                }
                Divider()
            } else {
                Menu {
                    ForEach(0..<group.actions.count, id: \.self) { index in
                        let item = group.actions[index]
                        StatusActionView(data: item, useText: true, isFixedWidth: false)
                    }
                } label: {
                    ZStack {
                        Text("0")
                            .hidden()
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
                                    if isFixedWidth, !useText {
                                        Text("0000")
                                            .hidden()
                                    }
                                    if let text = group.displayItem.countText, showNumbers {
                                        if let color = group.displayItem.color {
                                            Text(text)
                                                .foregroundStyle(color)
                                        } else {
                                            Text(text)
                                        }
                                    }
                                }
                                .lineLimit(1)
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
                }
            }
        case .asyncActionItem(let asyncItem):
            EmptyView()
//            AsyncStatusActionView(data: asyncItem)
        }
    }
}

struct AsyncStatusActionView: View {
    let data: StatusActionAsyncActionItem
    var body: some View {
        // TODO: Not supported yet
//        Button {
//
//        } label: {
//
//        }
//        .onAppear {
//        }
    }
}

struct StatusActionItemView: View {
    @Environment(\.appearanceSettings.showNumbers) private var showNumbers
    @Environment(\.openURL) private var openURL
    let data: StatusActionItem
    let useText: Bool
    let isFixedWidth: Bool
    var body: some View {
        if let shareable = data as? StatusActionItemShareable {
            ShareLink(data.textKey, item: .init(string: shareable.content)!)
        } else {
            Button(
                role: data.role,
            ) {
                if let clickable = data as? StatusActionItemClickable {
                    clickable.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                }
            } label: {
                Label {
                    ZStack(
                        alignment: .leading
                    ) {
                        if isFixedWidth, !useText {
                            Text("0000")
                                .hidden()
                        }
                        if useText {
                            if let color = data.color {
                                Text(data.textKey)
                                    .foregroundStyle(color)
                            } else {
                                Text(data.textKey)
                            }
                        } else if let text = data.countText, showNumbers {
                            if let color = data.color {
                                Text(text)
                                    .foregroundStyle(color)
                            } else {
                                Text(text)
                            }
                        }
                    }
                    .lineLimit(1)
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
}

extension StatusActionItem {
    var countText: String? {
        if let numbered = self as? StatusActionItemNumbered {
            return numbered.count.humanized
        } else {
            return nil
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
    var role: ButtonRole? {
        if let colorized = self as? StatusActionItemColorized {
            switch colorized.color {
            case .red:
                    .destructive
            case .error:
                    .destructive
            case .contentColor:
                    nil
            case .primaryColor:
                if #available(iOS 26.0, *) {
                    .confirm
                } else {
                    nil
                }
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
        case .share:
            return LocalizedStringResource("share")
        case .fxShare:
            return LocalizedStringResource("fx_share")
        }
    }
}

struct StatusActionIcon: View {
    let item: StatusActionItem

    var body: some View {
        Image(item.imageName)
    }
}

extension StatusActionItem {
    var imageName: String {
        switch onEnum(of: self) {
        case .bookmark(let bookmarked):
            return bookmarked.bookmarked ? "fa-bookmark.fill" : "fa-bookmark"
        case .delete:
            return "fa-trash"
        case .like(let liked):
            return liked.liked ? "fa-heart.fill" : "fa-heart"
        case .more:
            return "fa-ellipsis"
        case .quote:
            return "fa-quote-left"
        case .reaction(let reacted):
            return reacted.reacted ? "fa-minus" : "fa-plus"
        case .reply:
            return "fa-reply"
        case .report:
            return "fa-circle-info"
        case .retweet:
            return "fa-retweet"
        case .comment:
            return "fa-comment-dots"
        case .share:
            return "fa-share-nodes"
        case .fxShare:
            return "fa-share-nodes"
        }
    }
}
