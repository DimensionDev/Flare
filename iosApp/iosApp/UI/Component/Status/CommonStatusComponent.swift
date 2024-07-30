import SwiftUI
import MarkdownUI
import shared
import NetworkImage

struct CommonStatusComponent: View {
    @State private var showMedia: Bool = false
    @State private var expanded: Bool = true
    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings
    let data: UiTimelineItemContentStatus
    let onMediaClick: (Int, String?) -> Void
    let isDetail: Bool = false

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
                        dateFormatter(Date(timeIntervalSince1970: .init(integerLiteral: data.createdAt)))
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
                .opacity(0.5)
                .buttonStyle(.plain)
                if expanded {
                    Spacer()
                        .frame(height: 8)
                }
            }
            if expanded {
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
                        onMediaClick: onMediaClick
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
                VStack {
                    ForEach(0...data.quote.count - 1, id: \.self) { index in
                        let quote = data.quote[index]
                        QuotedStatus(data: quote, onMediaClick: onMediaClick)
                        if index != data.quote.endIndex {
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

            if (isDetail) {
                Spacer()
                    .frame(height: 4)
                HStack {
                    Text(Date(timeIntervalSince1970: .init(data.createdAt)), style: .date)
                    Text(Date(timeIntervalSince1970: .init(data.createdAt)), style: .time)
                }
            }
            Spacer()
                .frame(height: 8)

            if appSettings.appearanceSettings.showActions || isDetail {
                HStack {
                    ForEach(1...data.actions.count, id: \.self) { actionIndex in
                        let action = data.actions[actionIndex - 1]
                        switch onEnum(of: action) {
                        case .group(let group): Menu {
                            ForEach(1...group.actions.count, id: \.self) { subActionIndex in
                                let subAction = group.actions[subActionIndex - 1]
                                if case .item(let item) = onEnum(of: subAction) {
                                    Button(action: {
                                        if let clickable = item as? StatusActionItemClickable {
                                            clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                                        }
                                    }, label: {
                                        let text = switch onEnum(of: item) {
                                        case .bookmark(let data): data.bookmarked ? "status_action_unbookmark" : "status_action_bookmark"
                                        case .delete: "status_action_delete"
                                        case .like(let data): data.liked ? "status_action_unlike" : "status_action_like"
                                        case .quote: "status_action_quote"
                                        case .reaction: "status_action_add_reaction"
                                        case .reply: "status_action_reply"
                                        case .report: "status_action_report"
                                        case .retweet(let data): data.retweeted ? "status_action_unretweet": "status_action_retweet"
                                        default: ""
                                        }
                                        let icon = switch onEnum(of: item) {
                                        case .bookmark(let data): data.bookmarked ? "bookmark.slash" : "bookmark"
                                        case .delete: "trash"
                                        case .like: "star"
                                        case .quote: "quote.bubble.fill"
                                        case .reaction(let data): data.reacted ? "" : ""
                                        case .reply: "arrowshape.turn.up.left"
                                        case .report: "exclamationmark.shield"
                                        case .retweet: "arrow.left.arrow.right"
                                        default: ""
                                        }
                                        let color: Color? = if case .colorized(let colorData) = onEnum(of: item) {
                                            switch colorData.color {
                                            case .red: Color.red
                                            case .primaryColor: Color.primary
                                            case .contentColor: Color(UIColor.label)
                                            case .error: Color.red
                                            }
                                        } else {
                                            nil
                                        }
                                        Label(text, systemImage: icon)
                                            .tint(color)
                                    })
                                }
                            }
                        } label: {
                            StatusActionLabel(item: group.displayItem)
                        }

                        case .item(let item): Button(action: {
                            if let clickable = item as? StatusActionItemClickable {
                                clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                            }
                        }, label: {
                            StatusActionLabel(item: item)
                        })
                        }
                    }
                }
                .if(!isDetail) { view in
                    view.opacity(0.6)
                }
                Spacer()
                    .frame(height: 4)
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
        let icon = switch onEnum(of: item) {
        case .bookmark(let data): data.bookmarked ? "bookmark.slash" : "bookmark"
        case .delete: "trash"
        case .like: "star"
        case .quote: "quote.bubble.fill"
        case .reaction(let data): data.reacted ? "" : ""
        case .reply: "arrowshape.turn.up.left"
        case .report: "exclamationmark.shield"
        case .retweet: "arrow.left.arrow.right"
        default: ""
        }
        let color: Color? = if case .colorized(let colorData) = onEnum(of: item) {
            switch colorData.color {
            case .red: Color.red
            case .primaryColor: Color.primary
            case .contentColor: Color(UIColor.label)
            case .error: Color.red
            }
        } else {
            nil
        }
        Label(text, systemImage: icon)
            .tint(color)
    }
}

struct StatusVisibilityComponent: View {
    let visibility: UiTimelineItemContentStatusTopEndContentVisibility.Type_
    var body: some View {
        switch visibility {
        case .public:
            Image(systemName: "globe")
        case .home:
            Image(systemName: "lock.open")
        case .followers:
            Image(systemName: "lock")
        case .specified:
            Image(systemName: "at")
        }
    }
}
