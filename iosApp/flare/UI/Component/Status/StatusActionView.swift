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
                        if !isFixedWidth && group.displayItem.count == nil {
                            if let color = group.displayItem.icon?.color {
                                StatusActionIcon(icon: group.displayItem.icon)
                                    .foregroundStyle(color)
                            } else {
                                StatusActionIcon(icon: group.displayItem.icon)
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
                                    if let text = group.displayItem.count?.humanized, showNumbers {
                                        if let color = group.displayItem.icon?.color {
                                            Text(text)
                                                .foregroundStyle(color)
                                        } else {
                                            Text(text)
                                        }
                                    }
                                }
                                .lineLimit(1)
                            } icon: {
                                if let color = group.displayItem.icon?.color {
                                    StatusActionIcon(icon: group.displayItem.icon)
                                        .foregroundStyle(color)
                                } else {
                                    StatusActionIcon(icon: group.displayItem.icon)
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
        if let shareContent = data.shareContent {
            ShareLink(data.text?.localizedStringResource ?? LocalizedStringResource("share"), item: .init(string: shareContent)!)
        } else {
            Button(
                role: data.icon?.role
            ) {
                if let onClicked = data.onClicked {
                    onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
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
                            if let color = data.icon?.color {
                                Text(data.text?.localizedStringResource ?? LocalizedStringResource("more"))
                                    .foregroundStyle(color)
                            } else {
                                Text(data.text?.localizedStringResource ?? LocalizedStringResource("more"))
                            }
                        } else if let text = data.count?.humanized, showNumbers {
                            if let color = data.icon?.color {
                                Text(text)
                                    .foregroundStyle(color)
                            } else {
                                Text(text)
                            }
                        }
                    }
                    .lineLimit(1)
                } icon: {
                    if let color = data.icon?.color {
                        StatusActionIcon(icon: data.icon)
                            .foregroundStyle(color)
                    } else {
                        StatusActionIcon(icon: data.icon)
                    }
                }
            }
            .sensoryFeedback(.success, trigger: data.icon?.color)
            .buttonStyle(.plain)
        }
    }
}

extension StatusActionItemIcon {
    var color: Color? {
        switch self {
        case .unlike: Color(.systemRed)
        case .delete, .report: Color(.systemRed)
        case .unretweet: Color.accentColor
        default: nil
        }
    }
    
    var role: ButtonRole? {
        switch self {
        case .unlike, .delete, .report:
                .destructive
        case .unretweet:
            if #available(iOS 26.0, *) {
                    .confirm
            } else {
                nil
            }
        default:
            nil
        }
    }
}

extension StatusActionItemText {
    var localizedStringResource: LocalizedStringResource {
        if let raw = self as? StatusActionItemTextRaw {
            return LocalizedStringResource(stringLiteral: raw.text)
        } else if let localized = self as? StatusActionItemTextLocalized {
            switch localized.type {
            case .like: return LocalizedStringResource("like")
            case .unlike: return LocalizedStringResource("unlike")
            case .retweet: return LocalizedStringResource("retweet")
            case .unretweet: return LocalizedStringResource("retweet_remove")
            case .reply: return LocalizedStringResource("reply")
            case .comment: return LocalizedStringResource("comment")
            case .quote: return LocalizedStringResource("quote")
            case .bookmark: return LocalizedStringResource("bookmark_add")
            case .unbookmark: return LocalizedStringResource("bookmark_remove")
            case .more: return LocalizedStringResource("more")
            case .delete: return LocalizedStringResource("delete")
            case .report: return LocalizedStringResource("report")
            case .react: return LocalizedStringResource("reaction_add")
            case .unreact: return LocalizedStringResource("reaction_remove")
            case .share: return LocalizedStringResource("share")
            case .fxShare: return LocalizedStringResource("fx_share")
            default: return LocalizedStringResource("more")
            }
        }
        return LocalizedStringResource("more")
    }
}

struct StatusActionIcon: View {
    let icon: StatusActionItemIcon?

    var body: some View {
        if let icon = icon {
            Image(icon.imageName)
        }
    }
}

extension StatusActionItemIcon {
    var imageName: String {
        switch self {
        case .bookmark: return "fa-bookmark"
        case .unbookmark: return "fa-bookmark.fill"
        case .delete: return "fa-trash"
        case .like: return "fa-heart"
        case .unlike: return "fa-heart.fill"
        case .more: return "fa-ellipsis"
        case .quote: return "fa-quote-left"
        case .react: return "fa-plus"
        case .unReact: return "fa-minus"
        case .reply: return "fa-reply"
        case .report: return "fa-circle-info"
        case .retweet: return "fa-retweet"
        case .unretweet: return "fa-retweet"
        case .comment: return "fa-comment-dots"
        case .share: return "fa-share-nodes"
        case .fxShare: return "fa-share-nodes"
        default: return "fa-ellipsis"
        }
    }
}
