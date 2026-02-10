import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

struct StatusActionsView: View {
    @Environment(\.appearanceSettings.postActionStyle) private var postActionStyle
    let data: [ActionMenu]
    let useText: Bool
    var allowSpacer: Bool = true

    var body: some View {
        if useText {
            ForEach(0..<data.count, id: \.self) { index in
                let item = data[index]
                StatusActionView(data: item, useText: true, isFixedWidth: false)
            }
        } else {
            HStack {
                ForEach(0..<data.count, id: \.self) { index in
                    let item = data[index]
                    if (index == data.count - 1 && postActionStyle == .leftAligned) ||
                        (postActionStyle == .rightAligned && index == 0) ||
                        (postActionStyle == .stretch && index != 0) {
                        if allowSpacer {
                            Spacer()
                        }
                    }
                    StatusActionView(data: item, useText: useText, isFixedWidth: index != data.count - 1)
                }
            }
            .backport
            .labelIconToTitleSpacing(4)
        }
    }
}

struct StatusActionView: View {
    @Environment(\.appearanceSettings.showNumbers) private var showNumbers
    @ScaledMetric(relativeTo: .footnote) var fontSize = 13
    let data: ActionMenu
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
                    if let text = group.displayItem.count?.humanized, showNumbers {
                        Label {
                            if let color = group.displayItem.color?.swiftColor {
                                Text(text)
                                    .foregroundStyle(color)
                                    .lineLimit(1)
                                    .frame(minWidth: isFixedWidth ? fontSize * 2.5 : nil, alignment: .leading)
                            } else {
                                Text(text)
                                    .lineLimit(1)
                                    .frame(minWidth: isFixedWidth ? fontSize * 2.5 : nil, alignment: .leading)
                            }
                        } icon: {
                            if let color = group.displayItem.color?.swiftColor {
                                StatusActionIcon(icon: group.displayItem.icon)
                                    .foregroundStyle(color)
                            } else {
                                StatusActionIcon(icon: group.displayItem.icon)
                            }
                        }
                    } else {
                        if let color = group.displayItem.color?.swiftColor {
                            StatusActionIcon(icon: group.displayItem.icon)
                                .foregroundStyle(color)
                                .frame(minWidth: fontSize * 1.5, minHeight: fontSize * 1.5)
                                .contentShape(Rectangle())
                        } else {
                            StatusActionIcon(icon: group.displayItem.icon)
                                .frame(minWidth: fontSize * 1.5, minHeight: fontSize * 1.5)
                                .contentShape(Rectangle())
                        }
                    }
                }
                .buttonStyle(.plain)
            }
        case .asyncActionMenuItem(let asyncItem):
            EmptyView()
//            AsyncStatusActionView(data: asyncItem)
        case .divider:
            Divider()
        }
    }
}

struct AsyncStatusActionView: View {
    let data: ActionMenuAsyncActionMenuItem
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
    @ScaledMetric(relativeTo: .footnote) var fontSize = 13
    let data: ActionMenuItem
    let useText: Bool
    let isFixedWidth: Bool
    var body: some View {
        Button(
            role: data.color?.role
        ) {
            if let onClicked = data.onClicked {
                onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
            }
        } label: {
            if useText, let text = data.text?.resolvedString {
                Label {
                    if let color = data.color?.swiftColor {
                        Text(text)
                            .foregroundStyle(color)
                            .frame(minWidth: isFixedWidth ? fontSize * 2.5 : nil, alignment: .leading)
                    } else {
                        Text(text)
                            .frame(minWidth: isFixedWidth ? fontSize * 2.5 : nil, alignment: .leading)
                    }
                } icon: {
                    if let color = data.color?.swiftColor {
                        StatusActionIcon(icon: data.icon)
                            .foregroundStyle(color)
                    } else {
                        StatusActionIcon(icon: data.icon)
                    }
                }
            } else if let text = data.count?.humanized, showNumbers {
                Label {
                    if let color = data.color?.swiftColor {
                        Text(text)
                            .foregroundStyle(color)
                            .frame(minWidth: isFixedWidth ? fontSize * 2.5 : nil, alignment: .leading)
                    } else {
                        Text(text)
                            .frame(minWidth: isFixedWidth ? fontSize * 2.5 : nil, alignment: .leading)
                    }
                } icon: {
                    if let color = data.color?.swiftColor {
                        StatusActionIcon(icon: data.icon)
                            .foregroundStyle(color)
                    } else {
                        StatusActionIcon(icon: data.icon)
                    }
                }
            } else {
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

extension ActionMenuItem.Color {
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

extension ActionMenuItemText {
    var resolvedString: LocalizedStringResource {
        switch onEnum(of: self) {
        case .raw(let raw):
            return LocalizedStringResource(stringLiteral: raw.text)
        case .localized(let localized):
            switch localized.type {
            case .like: return "like"
            case .unlike: return "unlike"
            case .retweet: return "retweet"
            case .unretweet: return "retweet_remove"
            case .reply: return "reply"
            case .comment: return "comment"
            case .quote: return "quote"
            case .bookmark: return "bookmark_add"
            case .unbookmark: return "bookmark_remove"
            case .more: return "more"
            case .delete: return "delete"
            case .report: return "report"
            case .react: return "reaction_add"
            case .share: return "share"
            case .fxShare: return "fx_share"
            case .unReact: return "reaction_remove"
            case .editUserList: return "edit_user_in_list"
            case .sendMessage: return "send_message"
            case .mute: return "mute"
            case .unMute: return "unmute"
            case .block: return "block"
            case .unBlock: return "unblock"
            case .blockWithHandleParameter: return "block_user_with_handle \(localized.parameters.first ?? "")"
            case .muteWithHandleParameter: return "mute_user_with_handle \(localized.parameters.first ?? "")"
            }
        }
    }
}

struct StatusActionIcon: View {
    let icon: ActionMenuItem.Icon?

    var body: some View {
        if let icon = icon {
            Image(icon.imageName)
        }
    }
}

extension ActionMenuItem.Icon {
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
        case .moreVerticel: return "fa-ellipsis-vertical"
        case .list: return "fa-list"
        case .chatMessage: return "fa-message"
        case .mute: return "fa-volume-xmark"
        case .unMute: return "fa-volume-xmark"
        case .block: return "fa-user-slash"
        case .unBlock: return "fa-user-slash"
        }
    }
}
