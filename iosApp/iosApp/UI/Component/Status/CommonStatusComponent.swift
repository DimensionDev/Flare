import Awesome
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import shared
import SwiftDate
import SwiftUI

// timeline tweet
struct CommonStatusComponent: View {
    @State private var showMedia: Bool = false
    @State private var expanded: Bool = false
    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings

    let data: UiTimelineItemContentStatus
    let onMediaClick: (Int, UiMedia) -> Void
    let isDetail: Bool

    var body: some View {
        VStack(alignment: .leading) {
            HStack(alignment: .top) {
                if let user = data.user {
                    UserComponent(
                        user: user,
                        topEndContent: data.topEndContent as? UiTimelineItemContentStatusTopEndContent,
                        onUserClicked: {
                            user.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                        }
                    ) 
                }
                Spacer()
                // icon + time
                VStack(alignment: .trailing, spacing: 2) {
                    HStack(spacing: 4) {
                        // if let topEndContent = data.topEndContent {
                        //     switch onEnum(of: topEndContent) {
                        //     case .visibility(let data): StatusVisibilityComponent(visibility: data.visibility)
                        //     }
                        // }
                        if !isDetail {
                            dateFormatter(data.createdAt)
                        }
                    }
                }
                .foregroundColor(.gray)
                .font(.caption)
            }
            // reply
            if let aboveTextContent = data.aboveTextContent {
                switch onEnum(of: aboveTextContent) {
                case let .replyTo(data): Text(String(localized: "Reply to \(data.handle)"))
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
            // tweet content
            if expanded || data.contentWarning == nil || data.contentWarning?.isEmpty == true {
                Markdown(data.content.markdown)
                    .font(.body)
                    .markdownInlineImageProvider(.emoji)
            }
            // media
            if !data.images.isEmpty {
                Spacer().frame(height: 8)
                
                // if appSettings.appearanceSettings.showMedia || showMedia {
                    MediaComponent(
                        hideSensitive: data.sensitive && !appSettings.appearanceSettings.showSensitiveContent,
                        medias: data.images,
                        onMediaClick: handleMediaClick, //打开预览
                        sensitive: data.sensitive
                    )
                // } else {
                //     Button {
                //         withAnimation {
                //             showMedia = true
                //         }
                //     } label: {
                //         Label(
                //             title: { Text("status_display_media") },
                //             icon: { Image(systemName: "photo") }
                //         )
                //     }
                //     .buttonStyle(.borderless)
                // }
            }
            // link preview
            if let card = data.card, appSettings.appearanceSettings.showLinkPreview {
                LinkPreview(card: card)
            }
            // quote tweet
            if !data.quote.isEmpty {
                Spacer()
                    .frame(height: 4)
                VStack {
                    ForEach(0 ..< data.quote.count, id: \.self) { index in
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
            //
            if let bottomContent = data.bottomContent {
                switch onEnum(of: bottomContent) {
                case let .reaction(data):
                    ScrollView(.horizontal) {
                        LazyHStack {
                            ForEach(1 ... data.emojiReactions.count, id: \.self) { index in
                                let reaction = data.emojiReactions[index - 1]
                                Button(action: {
                                    reaction.onClicked()
                                }, label: {
                                    HStack {
                                        if !reaction.url.isEmpty {
                                            KFImage(URL(string: reaction.url))
                                                .resizable()
                                                .scaledToFit()
                                        } else {
                                            Text(reaction.name)
                                        }
                                        Text(reaction.humanizedCount)
                                    }
                                })
                                .buttonStyle(.borderless)
                            }
                        }
                    }
                }
            }
            // if detail page
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

            // bottom action
            if appSettings.appearanceSettings.showActions || isDetail, !data.actions.isEmpty {
                HStack {
                    ForEach(0 ..< data.actions.count, id: \.self) { actionIndex in
                        if actionIndex == data.actions.count - 1 {
                            Spacer()
                        }
                        let action = data.actions[actionIndex]
                        switch onEnum(of: action) {
                        case let .group(group): Menu {
                                ForEach(0 ..< group.actions.count, id: \.self) { subActionIndex in
                                    let subAction = group.actions[subActionIndex]
                                    if case let .item(item) = onEnum(of: subAction) {
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
                                            case let .bookmark(data): data.bookmarked ? LocalizedStringKey("status_action_unbookmark") : LocalizedStringKey("status_action_bookmark")
                                            case .delete: LocalizedStringKey("status_action_delete")
                                            case let .like(data): data.liked ? LocalizedStringKey("status_action_unlike") : LocalizedStringKey("status_action_like")
                                            case .quote: LocalizedStringKey("status_action_quote")
                                            case .reaction: LocalizedStringKey("status_action_add_reaction")
                                            case .reply: LocalizedStringKey("status_action_reply")
                                            case .report: LocalizedStringKey("status_action_report")
                                            case let .retweet(data): data.retweeted ? LocalizedStringKey("status_action_unretweet") : LocalizedStringKey("status_action_retweet")
                                            case .more: LocalizedStringKey("status_action_more")
                                            }
                                            Label {
                                                Text(text)
                                            } icon: {
                                                // bottom action icon
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
                        case let .item(item): Button(action: {
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

    private func handleMediaClick(_ index: Int, _ media: UiMedia) {
        // Call original Kotlin callback
//        onMediaClick(index, media)

        // Log media URLs and index
        print("    ")
        print("    ")

        print("Clicked media URL: \(media.url)")
        print("All media URLs:")
        for (i, img) in data.images.enumerated() {
            print("[\(i)] \(img.url)")
        }
        print("Selected index: \(index)")
        print("     ")
        print("    ")

        // Show preview
        PhotoBrowserManager.shared.showPhotoBrowser(
            media: media,
            images: data.images,
            initialIndex: index
        )
    }
}

func dateFormatter(_ date: Date) -> some View {
    let dateInRegion = DateInRegion(date, region: .current)
    return Text(dateInRegion.toRelative(since: DateInRegion(Date(), region: .current)))
        .foregroundColor(.gray)
}

// bottom action icon image
struct StatusActionItemIcon: View {
    let item: StatusActionItem
    var body: some View {
        switch onEnum(of: item) {
        case let .bookmark(data):
            if data.bookmarked {
                Image(asset: Asset.Image.Status.Toolbar.bookmarkFilled)
            } else {
                Image(asset: Asset.Image.Status.Toolbar.bookmark)
            }
        case .delete:
            Image(asset: Asset.Image.Status.Toolbar.delete)
        case let .like(data):
            if data.liked {
                Image(asset: Asset.Image.Status.Toolbar.favorite)
            } else {
                Image(asset: Asset.Image.Status.Toolbar.favoriteBorder)
            }
        case .more:
            Image(asset: Asset.Image.Status.more).rotationEffect(.degrees(90))
        case .quote:
            Image(asset: Asset.Image.Status.Toolbar.quote)
        case let .reaction(data):
            if data.reacted {
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
        case .reply:
            Image(asset: Asset.Image.Status.Toolbar.chatBubbleOutline)
        case .report:
            Image(asset: Asset.Image.Status.Toolbar.flag)
        case let .retweet(data):
            if data.retweeted {
                Image(asset: Asset.Image.Status.Toolbar.repeat).foregroundColor(.init(.accentColor))
//                Awesome.Classic.Solid.retweet.image

            } else {
                Image(asset: Asset.Image.Status.Toolbar.repeat)
//                Awesome.Classic.Solid.retweet.image
                // #if os(macOS)
//                    .foregroundColor(.labelColor)
                // #elseif os(iOS)
//                    .foregroundColor(.label)
                // #endif
            }
        }
    }
}

// bottom action
struct StatusActionLabel: View {
    let item: StatusActionItem
    var body: some View {
        let text = switch onEnum(of: item) {
        case let .like(data): data.humanizedCount
        case let .retweet(data): data.humanizedCount
        case let .quote(data): data.humanizedCount
        case let .reply(data): data.humanizedCount
        case let .bookmark(data): data.humanizedCount
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
            // bottom action icon
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
            Awesome.Classic.Solid.globe.image.opacity(0.6)
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
