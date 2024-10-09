import SwiftUI
import MarkdownUI
import shared
import NetworkImage
import Awesome

struct CommonStatusComponent: View {
    @State private var showMedia: Bool = false
    @State private var expanded: Bool = false
    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings
    let data: UiTimelineItemContentStatus
    let onMediaClick: (Int, String?) -> Void
    let isDetail: Bool
    var body: some View {
        VStack(alignment: .leading) {
            HStack {
                if let user = data.user {
                    UserComponent(
                        user: user,
                        onUserClicked: {
                            user.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                        }
                    )
                }
                Spacer()
                HStack {
                    if let topEndContent = data.topEndContent {
                        switch onEnum(of: topEndContent) {
                        case .visibility(let data): StatusVisibilityComponent(visibility: data.visibility)
                        }
                    }
                    if !isDetail {
                        dateFormatter(data.createdAt)
                    }
                }
                .foregroundColor(.gray)
                .font(.caption)
            }
            if let aboveTextContent = data.aboveTextContent {
                switch onEnum(of: aboveTextContent) {
                case .replyTo(let data): Text(String(localized: "Reply to \(data.handle)"))
                        .font(.caption)
                        .opacity(0.6)
                }
                Spacer()
                    .frame(height: 4)
            }
            if let cwText = data.contentWarning, !cwText.isEmpty {
                Button(action: {
                    withAnimation {
                        expanded = !expanded
                    }
                }, label: {
                    Image(systemName: "exclamationmark.triangle")
                    Text(cwText)
                    Spacer()
                    if expanded {
                        Image(systemName: "arrowtriangle.down.circle.fill")
                    } else {
                        Image(systemName: "arrowtriangle.left.circle.fill")
                    }
                })
                .opacity(0.6)
                .buttonStyle(.plain)
                if expanded {
                    Spacer()
                        .frame(height: 8)
                }
            }
            if expanded || data.contentWarning == nil || data.contentWarning?.isEmpty == true {
                Markdown(data.content.markdown)
                    .font(.body)
                    .markdownInlineImageProvider(.emoji)
            }
            if !data.images.isEmpty {
                Spacer()
                    .frame(height: 8)
                if appSettings.appearanceSettings.showMedia || showMedia {
                    MediaComponent(
                        hideSensitive: data.sensitive && !appSettings.appearanceSettings.showSensitiveContent,
                        medias: data.images,
                        onMediaClick: onMediaClick,
                        sensitive: data.sensitive
                    )
                } else {
                    Button {
                        withAnimation {
                            showMedia = true
                        }
                    } label: {
                        Label(
                            title: { Text("status_display_media") },
                            icon: { Image(systemName: "photo") }
                        )
                    }
                    .buttonStyle(.borderless)
                }
            }
            if let card = data.card, appSettings.appearanceSettings.showLinkPreview {
                LinkPreview(card: card)
            }
            if !data.quote.isEmpty {
                Spacer()
                    .frame(height: 4)
                VStack {
                    ForEach(0..<data.quote.count, id: \.self) { index in
                        let quote = data.quote[index]
                        QuotedStatus(data: quote, onMediaClick: onMediaClick)
                        if index != data.quote.count - 1 {
                            Divider()
                        }
                    }
                }
#if os(iOS)
                .background(Color(UIColor.secondarySystemBackground))
#else
                .background(Color(NSColor.windowBackgroundColor))
#endif
                .cornerRadius(8)
            }
            if let bottomContent = data.bottomContent {
                switch onEnum(of: bottomContent) {
                case .reaction(let data):
                    ScrollView(.horizontal) {
                        LazyHStack {
                            ForEach(1...data.emojiReactions.count, id: \.self) { index in
                                let reaction = data.emojiReactions[index - 1]
                                Button(action: {
                                    reaction.onClicked()
                                }, label: {
                                    HStack {
                                        NetworkImage(url: URL(string: reaction.url))
                                        Text(reaction.humanizedCount)
                                    }
                                })
                                .buttonStyle(.borderless)
                            }
                        }
                    }
                }
            }
            if isDetail {
                Spacer()
                    .frame(height: 4)
                HStack {
                    Text(data.createdAt, style: .date)
                    Text(data.createdAt, style: .time)
                }
                .opacity(0.6)
            }
            Spacer()
                .frame(height: 8)
            if appSettings.appearanceSettings.showActions || isDetail, !data.actions.isEmpty {
                HStack {
                    ForEach(0..<data.actions.count, id: \.self) { actionIndex in
                        if actionIndex == data.actions.count - 1 {
                            Spacer()
                        }
                        let action = data.actions[actionIndex]
                        switch onEnum(of: action) {
                        case .group(let group): Menu {
                            ForEach(0..<group.actions.count, id: \.self) { subActionIndex in
                                let subAction = group.actions[subActionIndex]
                                if case .item(let item) = onEnum(of: subAction) {
                                    let role: ButtonRole? = if let colorData = item as? StatusActionItemColorized {
                                        switch colorData.color {
                                        case .red: .destructive
                                        case .primaryColor: nil
                                        case .contentColor: nil
                                        case .error: .destructive
                                        }
                                    } else {
                                        nil
                                    }
                                    Button(role: role, action: {
                                        if let clickable = item as? StatusActionItemClickable {
                                            clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                                        }
                                    }, label: {
                                        let text: LocalizedStringKey = switch onEnum(of: item) {
                                        case .bookmark(let data): data.bookmarked ? LocalizedStringKey("status_action_unbookmark") : LocalizedStringKey("status_action_bookmark")
                                        case .delete: LocalizedStringKey("status_action_delete")
                                        case .like(let data): data.liked ? LocalizedStringKey("status_action_unlike") : LocalizedStringKey("status_action_like")
                                        case .quote: LocalizedStringKey("status_action_quote")
                                        case .reaction: LocalizedStringKey("status_action_add_reaction")
                                        case .reply: LocalizedStringKey("status_action_reply")
                                        case .report: LocalizedStringKey("status_action_report")
                                        case .retweet(let data): data.retweeted ? LocalizedStringKey("status_action_unretweet") : LocalizedStringKey("status_action_retweet")
                                        case .more: LocalizedStringKey("status_action_more")
                                        }
                                        Label {
                                            Text(text)
                                        } icon: {
                                            StatusActionItemIcon(item: item)
                                        }
                                    })
                                }
                            }
                        } label: {
                            StatusActionLabel(item: group.displayItem)
                        }
                        .if(actionIndex != data.actions.count - 1) { view in
                            view
                                .frame(minWidth: 56.0, alignment: .leading)
                        }
                        .if(actionIndex == data.actions.count - 1) { view in
                            view
                                .frame(alignment: .center)
                        }
                        case .item(let item): Button(action: {
                            if let clickable = item as? StatusActionItemClickable {
                                clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                            }
                        }, label: {
                            StatusActionLabel(item: item)
                        })
                        .if(actionIndex != data.actions.count - 1) { view in
                            view
                                .frame(minWidth: 56.0, alignment: .leading)
                        }
                        .if(actionIndex == data.actions.count - 1) { view in
                            view
                                .frame(alignment: .center)
                        }
                        }
                    }
                }
                .labelStyle(CenteredLabelStyle())
                .buttonStyle(.borderless)
                .opacity(0.6)
                .if(!isDetail) { view in
                    view
                        .font(.caption)
                }
            }
        }.frame(alignment: .leading)
    }
}

func dateFormatter(_ date: Date) -> some View {
    let now = Date()
    let oneDayAgo = Calendar.current.date(byAdding: .day, value: -1, to: now)!
    if date > oneDayAgo {
        // If the date is within the last day, use the .timer style
        return Text(date, style: .relative)
    } else {
        // Otherwise, use the .dateTime style
        return Text(date, style: .date)
    }
}

struct StatusActionItemIcon: View {
    let item: StatusActionItem
    var body: some View {
        switch onEnum(of: item) {
        case .bookmark(let data):
            if (data.bookmarked) {
                Awesome.Classic.Solid.bookmark.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
            } else {
                Awesome.Classic.Regular.bookmark.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
            }
        case .delete(_):
            Awesome.Classic.Solid.trash.image
        case .like(let data):
            if (data.liked) {
                Awesome.Classic.Solid.heart.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
            } else {
                Awesome.Classic.Regular.heart.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
            }
        case .more(_):
            Awesome.Classic.Solid.ellipsis.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
        case .quote(_):
            Awesome.Classic.Solid.quoteLeft.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
        case .reaction(let data):
            if (data.reacted) {
                Awesome.Classic.Solid.minus.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
            } else {
                Awesome.Classic.Solid.plus.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
            }
        case .reply(_):
            Awesome.Classic.Solid.reply.image
#if os(macOS)
                .foregroundColor(.labelColor)
#elseif os(iOS)
                .foregroundColor(.label)
#endif
        case .report(_):
            Awesome.Classic.Solid.circleInfo.image
#if os(macOS)
                .foregroundColor(.labelColor)
#elseif os(iOS)
                .foregroundColor(.label)
#endif
        case .retweet(let data):
            if (data.retweeted) {
                Awesome.Classic.Solid.retweet.image
                    .foregroundColor(.init(.accentColor))
            } else {
                Awesome.Classic.Solid.retweet.image
#if os(macOS)
                    .foregroundColor(.labelColor)
#elseif os(iOS)
                    .foregroundColor(.label)
#endif
            }
        }
    }
}

struct StatusActionLabel: View {
    let item: StatusActionItem
    var body: some View {
        let text = switch onEnum(of: item) {
        case .like(let data): data.humanizedCount
        case .retweet(let data): data.humanizedCount
        case .quote(let data): data.humanizedCount
        case .reply(let data): data.humanizedCount
        case .bookmark(let data): data.humanizedCount
        default: ""
        }
        let color = if let colorData = item as? StatusActionItemColorized {
            switch colorData.color {
            case .red: Color.red
            case .primaryColor: Color.accentColor
            case .contentColor:
#if os(iOS)
                Color(UIColor.label)
#elseif os(macOS)
                Color(NSColor.labelColor)
#endif
            case .error: Color.red
            }
        } else {
#if os(iOS)
            Color(UIColor.label)
#elseif os(macOS)
            Color(NSColor.labelColor)
#endif
        }
        Label {
            Text(text)
        } icon: {
            StatusActionItemIcon(item: item)
        }
        .foregroundStyle(color, color)
    }
}

struct StatusVisibilityComponent: View {
    let visibility: UiTimelineItemContentStatusTopEndContentVisibility.Type_
    var body: some View {
        switch visibility {
        case .public:
            Awesome.Classic.Solid.globe.image
        case .home:
            Awesome.Classic.Solid.lockOpen.image
        case .followers:
            Awesome.Classic.Solid.lock.image
        case .specified:
            Awesome.Classic.Solid.at.image
        }
    }
}

struct CenteredLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack {
            configuration.icon.frame(alignment: .center)
            configuration.title.frame(alignment: .center)
        }
    }
}
