import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log

// import MarkdownUI
import shared
import SwiftDate
import SwiftUI
import UIKit

// timeline tweet
struct CommonTimelineStatusComponent: View {
    @State private var showMedia: Bool = false
    @State private var expanded: Bool = false
    @State private var showShareMenu: Bool = false
    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings
    @EnvironmentObject private var router: FlareRouter
    @Environment(FlareTheme.self) private var theme

    let data: UiTimelineItemContentStatus
    let onMediaClick: (Int, UiMedia) -> Void
    let isDetail: Bool
    let enableTranslation: Bool

    init(data: UiTimelineItemContentStatus, onMediaClick: @escaping (Int, UiMedia) -> Void, isDetail: Bool, enableTranslation: Bool = true) {
        self.data = data
        self.onMediaClick = onMediaClick
        self.isDetail = isDetail
        self.enableTranslation = enableTranslation
    }

    private func showReportToast() {
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let window = windowScene.windows.first
        {
            let toastView = ToastView(
                icon: UIImage(systemName: "flag.fill"),
                message: String(localized: "report") + " success"
            )
            toastView.show()
        }
    }

    private func processActions() -> (mainActions: [StatusAction], moreActions: [StatusActionItem]) {
        var bottomMainActions: [StatusAction] = []
        var bottomMoreActions: [StatusActionItem] = []

        // 处理主要操作
        for action in data.actions {
            switch onEnum(of: action) {
            case let .item(item):
                // 所有非 More 的 item 都加入主操作
                if !(item is StatusActionItemMore) {
//                    if item is StatusActionItemReaction {
//                        // misskey 的+ emoji，先去掉
//                    } else {
                    bottomMainActions.append(action)
//                    }
                }
            case let .group(group):
                let displayItem = group.displayItem
                if (displayItem as? StatusActionItemMore) != nil {
                    // 只处理 More 菜单中的操作
                    for subAction in group.actions {
                        if case let .item(item) = onEnum(of: subAction) {
                            if item is StatusActionItemBookmark {
                                // 将书签添加到主操作
                                bottomMainActions.append(subAction)
                            } else {
                                // 其他操作添加到更多操作
                                // bottomMoreActions.append(item)
                            }
                        } else if subAction is StatusActionAsyncActionItem {}
                    }
                } else {
                    // 其他 group（比如转发组）保持原样
                    bottomMainActions.append(action)
                }
            case let .asyncActionItem(asyncItem):
                break
            }
        }

        return (bottomMainActions, bottomMoreActions)
    }

    var body: some View {
        VStack(alignment: .leading) {
            Spacer().frame(height: 2)

            HStack(alignment: .top) {
                HStack(alignment: .center, spacing: 1) {
                    if let user = data.user {
                        UserComponent(
                            user: user,
                            topEndContent: data.topEndContent as? UiTimelineItemContentStatusTopEndContent
                        ).id("UserComponent_\(user.key)")
                            .environmentObject(router)
                    }
                    Spacer()
                    // icon + time

                    // 更多按钮
                    // if !processActions().moreActions.isEmpty {
                    //     Menu {
                    //         ForEach(0 ..< processActions().moreActions.count, id: \.self) { index in
                    //             let item = processActions().moreActions[index]
                    //             let role: ButtonRole? =
                    //                 if let colorData = item as? StatusActionItemColorized {
                    //                     switch colorData.color {
                    //                     case .red: .destructive
                    //                     case .primaryColor: nil
                    //                     case .contentColor: nil
                    //                     case .error: .destructive
                    //                     }
                    //                 } else {
                    //                     nil
                    //                 }

                    //             Button(
                    //                 role: role,
                    //                 action: {
                    //                     if let clickable = item as? StatusActionItemClickable {
                    //                         clickable.onClicked(
                    //                             .init(launcher: AppleUriLauncher(openURL: openURL)))
                    //                         // 如果是举报操作，显示 Toast
                    //                         if case .report = onEnum(of: item) {
                    //                             showReportToast()
                    //                         }
                    //                     }
                    //                 },
                    //                 label: {
                    //                     let text: LocalizedStringKey =
                    //                         switch onEnum(of: item) {
                    //                         case let .bookmark(data):
                    //                             data.bookmarked
                    //                                 ? LocalizedStringKey("status_action_unbookmark")
                    //                                 : LocalizedStringKey("status_action_bookmark")
                    //                         case .delete: LocalizedStringKey("status_action_delete")
                    //                         case let .like(data):
                    //                             data.liked
                    //                                 ? LocalizedStringKey("status_action_unlike")
                    //                                 : LocalizedStringKey("status_action_like")
                    //                         case .quote: LocalizedStringKey("quote")
                    //                         case .reaction:
                    //                             LocalizedStringKey("status_action_add_reaction")
                    //                         case .reply: LocalizedStringKey("status_action_reply")
                    //                         case .report: LocalizedStringKey("report")
                    //                         case let .retweet(data):
                    //                             data.retweeted
                    //                                 ? LocalizedStringKey("retweet_remove")
                    //                                 : LocalizedStringKey("retweet")
                    //                         case .more: LocalizedStringKey("status_action_more")
                    //                         }
                    //                     Label {
                    //                         Text(text)
                    //                     } icon: {
                    //                         StatusActionItemIcon(item: item)
                    //                     }
                    //                 }
                    //             )
                    //         }
                    //     } label: {
                    //         Image(asset: Asset.Image.Status.more)
                    //             .renderingMode(.template)
                    //             .rotationEffect(.degrees(0))
                    //             .foregroundColor(theme.labelColor)
                    //             .modifier(SmallIconModifier())
                    //     }
                    //     .padding(.top, 0)
                    // }

                    if !isDetail {
                        dateFormatter(data.createdAt)
                            .foregroundColor(.gray)
                            .font(.caption)
                            .frame(minWidth: 80, alignment: .trailing)
                    }
                }
                .padding(.bottom, 1)
            }
            // reply
            if let aboveTextContent = data.aboveTextContent {
                switch onEnum(of: aboveTextContent) {
                case let .replyTo(data):
                    Text(String(localized: "Reply to \(data.handle)"))
                        .font(.caption)
                        .opacity(0.6)
                }
                Spacer()
                    .frame(height: 4)
            }

            // NSFW warning content
            if let cwText = data.contentWarning, !cwText.raw.isEmpty {
                Button(
                    action: {
                        withAnimation {
                            expanded = !expanded
                        }
                    },
                    label: {
                        Image(systemName: "exclamationmark.triangle")
                            .foregroundColor(theme.labelColor)

                        Markdown(cwText.markdown)
                            .font(.scaledBody)
                            .markdownInlineImageProvider(.emoji)
                        Spacer()
                        if expanded {
                            Image(systemName: "arrowtriangle.down.circle.fill")
                        } else {
                            Image(systemName: "arrowtriangle.left.circle.fill")
                        }
                    }
                )
                .opacity(0.6)
                .buttonStyle(.plain)
                if expanded {
                    Spacer()
                        .frame(height: 8)
                }
            }
            // tweet content
            if expanded || data.contentWarning == nil || data.contentWarning?.raw.isEmpty == true {
                Spacer()
                    .frame(height: 10)

                if !data.content.raw.isEmpty {
                    FlareText(data.content.raw, data.content.markdown, style: FlareTextStyle.Style(
                        font: Font.scaledBodyFont,
                        textColor: UIColor(theme.labelColor),
                        linkColor: UIColor(theme.tintColor),
                        mentionColor: UIColor(theme.tintColor),
                        hashtagColor: UIColor(theme.tintColor),
                        cashtagColor: UIColor(theme.tintColor)
                    ))
                    .onLinkTap { url in
                        openURL(url)
                    }
                    .lineSpacing(CGFloat(theme.lineSpacing))
                    .foregroundColor(theme.labelColor)

                    // Add translation component
                    if appSettings.appearanceSettings.autoTranslate, enableTranslation {
                        TranslatableText(originalText: data.content.raw)
                    }
                } else {
                    // 如果内容为空，显示一个空的 Text
                    Text("")
                        .font(.system(size: 16))
                        .foregroundColor(theme.labelColor)
                }
            }
            // media
            if !data.images.isEmpty {
                Spacer().frame(height: 8)

                MediaComponent(
                    hideSensitive: data.sensitive
                        && !appSettings.appearanceSettings.showSensitiveContent,
                    medias: data.images,
                    onMediaClick: handleMediaClick, // 打开预览
                    sensitive: data.sensitive
                )
            }

            if let card = data.card {
                if let url = URL(string: card.url), url.scheme == "flare", url.host?.lowercased() == "podcast" {
                    // podcast preview
                    PodcastPreview(card: card)
                        .onTapGesture {
                            handlePodcastCardTap(card: card)
                        }
                } else if appSettings.appearanceSettings.showLinkPreview { // Original LinkPreview condition
                    // link preview
                    LinkPreview(card: card)
                }
            }

            // quote tweet
            if !data.quote.isEmpty {
                Spacer()
                    .frame(height: 10)
                VStack {
                    ForEach(0 ..< data.quote.count, id: \.self) { index in
                        let quote = data.quote[index]
                        QuotedStatus(data: quote, onMediaClick: onMediaClick)
                            .foregroundColor(.gray)

                        if index != data.quote.count - 1 {
                            Divider()
                        }
                    }
                }
                .padding(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                )
                .cornerRadius(8)
                //               #if os(iOS)
                //                .background(Color(UIColor.secondarySystemBackground))
                //               #else
                //               .background(Color(NSColor.windowBackgroundColor))
                //               #endif
            }

            // misskey 的+ 的emojis
            if let bottomContent = data.bottomContent {
                switch onEnum(of: bottomContent) {
                case let .reaction(data):
                    ScrollView(.horizontal) {
                        LazyHStack {
                            if !data.emojiReactions.isEmpty {
                                ForEach(0 ..< data.emojiReactions.count, id: \.self) { index in
                                    let reaction = data.emojiReactions[index]
                                    Button(
                                        action: {
                                            reaction.onClicked()
                                        },
                                        label: {
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
                                        }
                                    )
                                    .buttonStyle(.borderless)
                                }
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

            Spacer().frame(height: 10)

            // bottom action
            if appSettings.appearanceSettings.showActions || isDetail, !data.actions.isEmpty {
                let processedActions = processActions()
                HStack(spacing: 0) {
                    // 显示主要操作
                    ForEach(0 ..< processedActions.mainActions.count, id: \.self) { actionIndex in
                        let action = processedActions.mainActions[actionIndex]
                        switch onEnum(of: action) {
                        case let .asyncActionItem(asyncItem): EmptyView()
                        case let .item(item):
                            Button(
                                action: {
                                    if let clickable = item as? StatusActionItemClickable {
                                        // 点赞 收藏
                                        os_log("[URL点击] 状态操作点击: %{public}@", log: .default, type: .debug, String(describing: type(of: item)))
                                        clickable.onClicked(
                                            .init(launcher: AppleUriLauncher(openURL: openURL)))
                                        // 如果是举报操作，显示 Toast
                                        if case .report = onEnum(of: item) {
                                            showReportToast()
                                        }
                                    }
                                },
                                label: {
                                    StatusActionLabel(item: item)
                                }
                            )
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 10) // 添加水平内边距
                        case let .group(group):
                            Menu {
                                ForEach(0 ..< group.actions.count, id: \.self) { subActionIndex in
                                    let subAction = group.actions[subActionIndex]
                                    if case let .item(item) = onEnum(of: subAction) {
                                        let role: ButtonRole? =
                                            if let colorData = item as? StatusActionItemColorized {
                                                switch colorData.color {
                                                case .red: .destructive
                                                case .primaryColor: nil
                                                case .contentColor: nil
                                                case .error: .destructive
                                                }
                                            } else {
                                                nil
                                            }
                                        Button(
                                            role: role,
                                            action: {
                                                if let clickable = item
                                                    as? StatusActionItemClickable
                                                {
                                                    clickable.onClicked(
                                                        .init(
                                                            launcher: AppleUriLauncher(
                                                                openURL: openURL)))
                                                }
                                            },
                                            label: {
                                                let text: LocalizedStringKey =
                                                    switch onEnum(of: item) {
                                                    case let .bookmark(data):
                                                        data.bookmarked
                                                            ? LocalizedStringKey(
                                                                "status_action_unbookmark")
                                                            : LocalizedStringKey(
                                                                "status_action_bookmark")
                                                    case .delete:
                                                        LocalizedStringKey("status_action_delete")
                                                    case let .like(data):
                                                        data.liked
                                                            ? LocalizedStringKey(
                                                                "status_action_unlike")
                                                            : LocalizedStringKey(
                                                                "status_action_like")
                                                    case .quote: LocalizedStringKey("quote")
                                                    case .reaction:
                                                        LocalizedStringKey(
                                                            "status_action_add_reaction")
                                                    case .reply:
                                                        LocalizedStringKey("status_action_reply")
                                                    case .report: LocalizedStringKey("report")
                                                    case let .retweet(data):
                                                        data.retweeted
                                                            ? LocalizedStringKey("retweet_remove")
                                                            : LocalizedStringKey("retweet")
                                                    case .more:
                                                        LocalizedStringKey("status_action_more")
                                                    }
                                                Label {
                                                    Text(text)
                                                } icon: {
                                                    StatusActionItemIcon(item: item)
                                                }
                                            }
                                        )
                                    }
                                }
                            } label: {
                                StatusActionLabel(item: group.displayItem)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 10)
                        }
                    }

                    // 使用新的 ShareButton
                    ShareButton(content: data, view: self)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 0)
                }
                .labelStyle(CenteredLabelStyle())
                .buttonStyle(.borderless)
                .opacity(0.6)
                .if(!isDetail) { view in
                    view.font(.caption)
                }
                .allowsHitTesting(true)
            }
            Spacer().frame(height: 2)
        }.frame(alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture {
                if let tapLocation =
                    (UIApplication.shared.windows.first?.hitTest(
                        UIApplication.shared.windows.first?.convert(CGPoint(x: 0, y: 0), to: nil)
                            ?? .zero, with: nil
                    ))
                {
                    let bottomActionBarFrame = CGRect(
                        x: 16, y: tapLocation.frame.height - 44,
                        width: tapLocation.frame.width - 32, height: 44
                    )
                    if !bottomActionBarFrame.contains(tapLocation.frame.origin) {
                        //  data.onClicked(ClickContext(launcher: AppleUriLauncher(openURL: openURL)))
                        router.navigate(to: .statusDetail(
                            accountType: UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest(),
                            statusKey: data.statusKey
                        ))
                    }
                }
            }
    }

    private func handleMediaClick(_ index: Int, _ media: UiMedia) {
        // Show preview
        PhotoBrowserManager.shared.showPhotoBrowser(
            media: media,
            images: data.images,
            initialIndex: index
        )
    }

    //  Podcast Card Tap
    private func handlePodcastCardTap(card: UiCard) {
        if let route = AppDeepLinkHelper().parse(url: card.url) as? AppleRoute.Podcast {
            print("Podcast Card Tapped, navigating via router to: podcastSheet(accountType: \(route.accountType), podcastId: \(route.id))")

            router.navigate(to: .podcastSheet(accountType: route.accountType, podcastId: route.id))
        } else {
            let parsedRoute = AppDeepLinkHelper().parse(url: card.url)
            print("Error: Could not parse Podcast URL from card: \(card.url). Parsed type: \(type(of: parsedRoute)) Optional value: \(parsedRoute)")
        }
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
    @Environment(\.colorScheme) var colorScheme

    var body: some View {
        switch onEnum(of: item) {
        case let .bookmark(data):
            if data.bookmarked {
                Image(asset: Asset.Image.Status.Toolbar.bookmarkFilled)
                    .renderingMode(.template)
//                    .foregroundColor(FColors.State.swiftUIBookmarkActive)
            } else {
                Image(asset: Asset.Image.Status.Toolbar.bookmark)
                    .renderingMode(.template)
//                    .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
            }
        case .delete:
            Image(asset: Asset.Image.Status.Toolbar.delete)
                .renderingMode(.template)
//                .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
        case let .like(data):
            if data.liked {
                Image(asset: Asset.Image.Status.Toolbar.favorite)
                    .renderingMode(.template)
//                    .foregroundColor(FColors.State.swiftUILikeActive)
            } else {
                Image(asset: Asset.Image.Status.Toolbar.favoriteBorder)
                    .renderingMode(.template)
//                    .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
            }
        case .more:
            Image(asset: Asset.Image.Status.more)
                .renderingMode(.template)
                //       .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
                .rotationEffect(.degrees(90))
        case .quote:
            Image(asset: Asset.Image.Status.Toolbar.quote)
                .renderingMode(.template)
        //     .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
        case let .reaction(data):
            if data.reacted {
                Awesome.Classic.Solid.minus.image
                //         .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
            } else {
                Awesome.Classic.Solid.plus.image
                //        .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
            }
        case .reply:
            Image(asset: Asset.Image.Status.Toolbar.chatBubbleOutline)
                .renderingMode(.template)
        //      .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
        case .report:
            Image(asset: Asset.Image.Status.Toolbar.flag)
                .renderingMode(.template)
        //   .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
        case let .retweet(data):
            if data.retweeted {
                Image(asset: Asset.Image.Status.Toolbar.repeat)
                    .renderingMode(.template)
//                    .foregroundColor(FColors.State.swiftUIRetweetActive)
            } else {
                Image(asset: Asset.Image.Status.Toolbar.repeat)
                    .renderingMode(.template)
//                    .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
            }
        }
    }
}

// bottom action
struct StatusActionLabel: View {
    let item: StatusActionItem
    @Environment(\.colorScheme) var colorScheme
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        let text =
            switch onEnum(of: item) {
            case let .like(data):
                formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
            case let .retweet(data):
                formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
            case let .quote(data):
                formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
            case let .reply(data):
                formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
            case let .bookmark(data):
                formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
            default: ""
            }

        // let color = Color.black
//            switch onEnum(of: item) {
//            case let .retweet(data):
//                data.retweeted
//                    ? FColors.State.swiftUIRetweetActive
//                    : (colorScheme == .dark ? Color.white : Color.black)
//            case let .bookmark(data):
//                data.bookmarked
//                    ? FColors.State.swiftUIBookmarkActive
//                    : (colorScheme == .dark ? Color.white : Color.black)
//            case let .like(data):
//                data.liked
//                    ? FColors.State.swiftUILikeActive
//                    : (colorScheme == .dark ? Color.white : Color.black)
//            default:
//                colorScheme == .dark ? Color.white : Color.black
//            }

        Label {
            Text(text)
        } icon: {
            StatusActionItemIcon(item: item)
        }
        .foregroundStyle(theme.labelColor, theme.labelColor)
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
    @Environment(FlareTheme.self) private var theme

    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 4) {
            configuration.icon.foregroundColor(theme.labelColor)
            configuration.title
                .font(.system(size: 12))
        }
        .frame(maxWidth: .infinity, alignment: .center)
    }
}

struct SmallIconModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .imageScale(.small)
            .scaleEffect(0.8)
            .frame(width: 24, height: 24)
    }
}
