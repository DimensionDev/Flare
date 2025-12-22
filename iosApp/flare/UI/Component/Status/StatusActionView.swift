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
                            if let color = group.displayItem.color?.swiftColor {
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
                                        if let color = group.displayItem.color?.swiftColor {
                                            Text(text)
                                                .foregroundStyle(color)
                                        } else {
                                            Text(text)
                                        }
                                    }
                                }
                                .lineLimit(1)
                            } icon: {
                                if let color = group.displayItem.color?.swiftColor {
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
            ShareLink(data.text?.resolvedString ?? NSLocalizedString("share", comment: ""), item: .init(string: shareContent)!)
        } else {
            Button(
                role: data.color?.role
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
                            if let color = data.color?.swiftColor {
                                Text(data.text?.resolvedString ?? NSLocalizedString("more", comment: ""))
                                    .foregroundStyle(color)
                            } else {
                                Text(data.text?.resolvedString ?? NSLocalizedString("more", comment: ""))
                            }
                        } else if let text = data.count?.humanized, showNumbers {
                            if let color = data.color?.swiftColor {
                                Text(text)
                                    .foregroundStyle(color)
                            } else {
                                Text(text)
                            }
                        }
                    }
                    .lineLimit(1)
                } icon: {
                    if let color = data.color?.swiftColor {
                        StatusActionIcon(icon: data.icon)
                            .foregroundStyle(color)
                    } else {
                        StatusActionIcon(icon: data.icon)
                    }
                }
            }
            .sensoryFeedback(.success, trigger: data.color?.swiftColor)
            .buttonStyle(.plain)
        }
    }
}

extension StatusActionItemColor {
    var swiftColor: Color? {
        switch self {
        case .red: return .red
        case .contentColor: return .primary
        case .primaryColor: return .accentColor
        default: return nil
        }
    }

    var role: ButtonRole? {
        switch self {
        case .red:
                .destructive
        case .primaryColor:
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
    var resolvedString: String {
        if let raw = self as? StatusActionItemTextRaw {
            return raw.text
        } else if let localized = self as? StatusActionItemTextLocalized {
            let key: String
            switch localized.type {
            case .like: key = "like"
            case .unlike: key = "unlike"
            case .retweet: key = "retweet"
            case .unretweet: key = "retweet_remove"
            case .reply: key = "reply"
            case .comment: key = "comment"
            case .quote: key = "quote"
            case .bookmark: key = "bookmark_add"
            case .unbookmark: key = "bookmark_remove"
            case .more: key = "more"
            case .delete: key = "delete"
            case .report: key = "report"
            case .react: key = "reaction_add"
            case .unreact: key = "reaction_remove"
            case .share: key = "share"
            case .fxShare: key = "fx_share"
            default: key = "more"
            }
            let format = NSLocalizedString(key, comment: "")
            if let args = localized.parameters as? [CVarArg], !args.isEmpty {
                return String(format: format, arguments: args)
            }
            return format
        }
        return NSLocalizedString("more", comment: "")
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
