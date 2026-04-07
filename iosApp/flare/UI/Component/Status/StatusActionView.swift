import SwiftUI
import KotlinSharedUI
import SwiftUIBackports
import UIKit

// MARK: - Top-level container
// Hoists @ScaledMetric, @Environment reads to a single place
// instead of duplicating them in every child view instance.

struct StatusActionsView: View {
    @Environment(\.appearanceSettings.postActionStyle) private var postActionStyle
    @Environment(\.appearanceSettings.showNumbers) private var showNumbers
    @Environment(\.openURL) private var openURL
    @ScaledMetric(relativeTo: .footnote) var fontSize = 13
    let data: [ActionMenu]
    let useText: Bool
    var allowSpacer: Bool = true

    var body: some View {
        if useText {
            ForEach(0..<data.count, id: \.self) { index in
                StatusActionView(
                    data: data[index],
                    useText: true,
                    isFixedWidth: false,
                    fontSize: fontSize,
                    showNumbers: showNumbers,
                    openURL: openURL
                )
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
                    StatusActionView(
                        data: item,
                        useText: useText,
                        isFixedWidth: index != data.count - 1,
                        fontSize: fontSize,
                        showNumbers: showNumbers,
                        openURL: openURL
                    )
                }
            }
            .backport
            .labelIconToTitleSpacing(4)
        }
    }
}

// MARK: - Single action (sealed class switch)
// No @Environment or @ScaledMetric — values passed from parent.

struct StatusActionView: View {
    let data: ActionMenu
    let useText: Bool
    let isFixedWidth: Bool
    let fontSize: CGFloat
    let showNumbers: Bool
    let openURL: OpenURLAction

    var body: some View {
        switch onEnum(of: data) {
        case .item(let item):
            StatusActionItemView(
                data: item,
                useText: useText,
                isFixedWidth: isFixedWidth,
                fontSize: fontSize,
                showNumbers: showNumbers,
                openURL: openURL
            )
        case .group(let group):
            if useText {
                Divider()
                ForEach(0..<group.actions.count, id: \.self) { index in
                    StatusActionView(
                        data: group.actions[index],
                        useText: true,
                        isFixedWidth: false,
                        fontSize: fontSize,
                        showNumbers: showNumbers,
                        openURL: openURL
                    )
                }
                Divider()
            } else {
                Menu {
                    ForEach(0..<group.actions.count, id: \.self) { index in
                        StatusActionView(
                            data: group.actions[index],
                            useText: true,
                            isFixedWidth: false,
                            fontSize: fontSize,
                            showNumbers: showNumbers,
                            openURL: openURL
                        )
                    }
                } label: {
                    if let text = group.displayItem.count?.humanized, showNumbers {
                        Label {
                            Text(text)
                                .lineLimit(1)
                                .frame(minWidth: isFixedWidth ? fontSize * 2.5 : nil, alignment: .leading)
                        } icon: {
                            StatusActionIcon(icon: group.displayItem.icon)
                        }
                    } else {
                        StatusActionIcon(icon: group.displayItem.icon)
                            .frame(minWidth: fontSize * 1.5, minHeight: fontSize * 1.5)
                            .contentShape(Rectangle())
                    }
                }
                .optionalForegroundStyle(group.displayItem.color?.swiftColor)
                .buttonStyle(.plain)
            }
        case .divider:
            Divider()
        }
    }
}

// MARK: - Leaf action item (Button)
// Computes display text once to avoid nested _ConditionalContent branches.

struct StatusActionItemView: View {
    let data: ActionMenu.Item
    let useText: Bool
    let isFixedWidth: Bool
    let fontSize: CGFloat
    let showNumbers: Bool
    let openURL: OpenURLAction

    private var resolvedText: Text? {
        if useText, let text = data.text?.resolvedString {
            return Text(text)
        } else if showNumbers, let count = data.count?.humanized {
            return Text(count)
        }
        return nil
    }

    private func triggerHapticFeedback() {
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
    }

    var body: some View {
        Button(role: data.color?.role) {
            triggerHapticFeedback()
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        } label: {
            if let text = resolvedText {
                Label {
                    text.frame(minWidth: isFixedWidth ? fontSize * 2.5 : nil, alignment: .leading)
                } icon: {
                    StatusActionIcon(icon: data.icon)
                }
            } else {
                StatusActionIcon(icon: data.icon)
            }
        }
        .optionalForegroundStyle(data.color?.swiftColor)
        .buttonStyle(.plain)
    }
}

// MARK: - Helpers

private extension View {
    @ViewBuilder
    func optionalForegroundStyle(_ color: Color?) -> some View {
        if let color {
            self.foregroundStyle(color)
        } else {
            self
        }
    }
}

extension ActionMenu.ItemColor {
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
            case .acceptFollowRequest: return "accept_follow_request"
            case .rejectFollowRequest: return "reject_follow_request"
            case .retryTranslation: return "Retry translation"
            case .translate: return "Translate"
            case .showOriginal: return "Show original"
            }
        }
    }
}

struct StatusActionIcon: View {
    let icon: UiIcon?

    var body: some View {
        if let icon = icon {
            icon.image
        }
    }
}

extension UiIcon {
    var image: Image {
        Image(imageName)
    }

    var imageName: String {
        switch self {
        case .home: return "fa-house"
        case .notification: return "fa-bell"
        case .search: return "fa-magnifying-glass"
        case .profile: return "fa-circle-user"
        case .settings: return "fa-gear"
        case .local: return "fa-users"
        case .world: return "fa-globe"
        case .featured: return "fa-rectangle-list"
        case .bookmark: return "fa-bookmark"
        case .unbookmark: return "fa-bookmark.fill"
        case .delete: return "fa-trash"
        case .like: return "fa-heart"
        case .unlike: return "fa-heart.fill"
        case .more: return "fa-ellipsis"
        case .quote: return "fa-reply"
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
        case .follow: return "fa-user-plus"
        case .favourite: return "fa-heart"
        case .mention: return "fa-at"
        case .poll: return "fa-square-poll-horizontal"
        case .edit: return "fa-pen"
        case .info: return "fa-circle-info"
        case .pin: return "fa-thumbtack"
        case .check: return "fa-check"
        case .feeds: return "fa-square-rss"
        case .messages: return "fa-message"
        case .rss: return "fa-square-rss"
        case .channel: return "fa-tv"
        case .heart: return "fa-heart"
        case .mastodon: return "fa-mastodon"
        case .misskey: return "fa-misskey"
        case .bluesky: return "fa-bluesky"
        case .nostr: return "fa-nostr"
        case .twitter: return "fa-x-twitter"
        case .x: return "fa-x-twitter"
        case .weibo: return "fa-weibo"
        }
    }
}
