import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import shared
import SwiftDate
import SwiftUI
import UIKit

struct StatusViewModel {
    let data: UiTimelineItemContentStatus
    let isDetail: Bool
    let enableTranslation: Bool

    init(data: UiTimelineItemContentStatus, isDetail: Bool, enableTranslation: Bool = true) {
        self.data = data
        self.isDetail = isDetail
        self.enableTranslation = enableTranslation
    }

    var statusData: UiTimelineItemContentStatus { data }
    var shouldShowTranslation: Bool { enableTranslation }
    var isDetailView: Bool { isDetail }

    var hasUser: Bool { data.user != nil }
    var hasAboveTextContent: Bool { data.aboveTextContent != nil }
    var hasContentWarning: Bool { data.contentWarning != nil && !data.contentWarning!.raw.isEmpty }
    var hasContent: Bool { !data.content.raw.isEmpty }
    var hasImages: Bool { !data.images.isEmpty }
    var hasCard: Bool { data.card != nil }
    var hasQuote: Bool { !data.quote.isEmpty }
    var hasBottomContent: Bool { data.bottomContent != nil }
    var hasActions: Bool { !data.actions.isEmpty }

    var isPodcastCard: Bool {
        guard let card = data.card,
              let url = URL(string: card.url) else { return false }
        return url.scheme == "flare" && url.host?.lowercased() == "podcast"
    }

    var shouldShowLinkPreview: Bool {
        guard let card = data.card else { return false }
        return !isPodcastCard && card.media != nil
    }

    func getProcessedActions() -> (mainActions: [StatusAction], moreActions: [StatusActionItem]) {
        ActionProcessor.processActions(data.actions)
    }

    func getFormattedDate() -> String {
        let dateInRegion = DateInRegion(data.createdAt, region: .current)
        return dateInRegion.toRelative(since: DateInRegion(Date(), region: .current))
    }
}

enum ActionProcessor {
    static func processActions(_ actions: [StatusAction]) -> (mainActions: [StatusAction], moreActions: [StatusActionItem]) {
        var bottomMainActions: [StatusAction] = []
        var bottomMoreActions: [StatusActionItem] = []

        for action in actions {
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
            case .asyncActionItem:
                break
            }
        }

        return (bottomMainActions, bottomMoreActions)
    }

    static func showReportToast() {
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
}

@Observable
class StatusContentViewModel {
    let content: UiRichText
    let isRTL: Bool

    init(content: UiRichText) {
        self.content = content
        isRTL = content.isRTL
    }

    var hasContent: Bool { !content.raw.isEmpty }
    var rawText: String { content.raw }
    var markdownText: String { content.markdown }
}

struct CommonTimelineStatusComponent: View {
    let data: UiTimelineItemContentStatus
    let isDetail: Bool
    let enableTranslation: Bool
    @State private var showMedia: Bool = false
    @State private var showShareMenu: Bool = false

    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings
    @EnvironmentObject private var router: FlareRouter
    @Environment(FlareTheme.self) private var theme

    let onMediaClick: (Int, UiMedia) -> Void

    init(data: UiTimelineItemContentStatus, onMediaClick: @escaping (Int, UiMedia) -> Void, isDetail: Bool, enableTranslation: Bool = true) {
        self.data = data
        self.isDetail = isDetail
        self.enableTranslation = enableTranslation
        self.onMediaClick = onMediaClick
    }

    //  每次都要算，性能堪忧，无解，后期想办法
    private var viewModel: StatusViewModel {
        StatusViewModel(data: data, isDetail: isDetail, enableTranslation: enableTranslation)
    }

    var body: some View {
        VStack(alignment: .leading) {
            Spacer().frame(height: 2)

            StatusHeaderView(viewModel: viewModel)

            StatusContentView(
                viewModel: viewModel,
                appSettings: appSettings,
                theme: theme,
                openURL: openURL,
                onMediaClick: onMediaClick,
                onPodcastCardTap: handlePodcastCardTap
            )

            StatusActionsView(
                viewModel: viewModel,
                appSettings: appSettings,
                openURL: openURL,
                parentView: self
            )

            // Spacer().frame(height: 3)
        }
        .frame(alignment: .leading)
        .contentShape(Rectangle())
        .onTapGesture {
            handleStatusTap()
        }
    }

    private func handleStatusTap() {
        if let tapLocation = UIApplication.shared.windows.first?.hitTest(
            UIApplication.shared.windows.first?.convert(CGPoint(x: 0, y: 0), to: nil) ?? .zero,
            with: nil
        ) {
            let bottomActionBarFrame = CGRect(
                x: 16, y: tapLocation.frame.height - 44,
                width: tapLocation.frame.width - 32, height: 44
            )
            if !bottomActionBarFrame.contains(tapLocation.frame.origin) {
                router.navigate(to: .statusDetail(
                    accountType: UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest(),
                    statusKey: viewModel.statusData.statusKey
                ))
            }
        }
    }

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

struct StatusHeaderView: View {
    let viewModel: StatusViewModel
    @EnvironmentObject private var router: FlareRouter
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        HStack(alignment: .top) {
            HStack(alignment: .center, spacing: 1) {
                if viewModel.hasUser, let user = viewModel.statusData.user {
                    UserComponent(
                        user: user,
                        topEndContent: viewModel.statusData.topEndContent as? UiTimelineItemContentStatusTopEndContent
                    )
                    .id("UserComponent_\(user.key)")
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

                if !viewModel.isDetailView {
                    Text(viewModel.getFormattedDate())
                        .foregroundColor(.gray)
                        .font(.caption)
                        .frame(minWidth: 80, alignment: .trailing)
                }
            }
            .padding(.bottom, 1)
        }
        .allowsHitTesting(true)
        .contentShape(Rectangle())
        .onTapGesture {
            // 空的手势处理
        }
    }
}

struct StatusContentView: View {
    let viewModel: StatusViewModel
    let appSettings: AppSettings
    let theme: FlareTheme
    let openURL: OpenURLAction
    let onMediaClick: (Int, UiMedia) -> Void
    let onPodcastCardTap: (UiCard) -> Void

    var body: some View {
        VStack(alignment: .leading) {
            // Reply content
            if viewModel.hasAboveTextContent, let aboveTextContent = viewModel.statusData.aboveTextContent {
                StatusReplyView(aboveTextContent: aboveTextContent)
            }

            // Content warning
            if viewModel.hasContentWarning, let cwText = viewModel.statusData.contentWarning {
                StatusContentWarningView(contentWarning: cwText, theme: theme, openURL: openURL)
            }

            Spacer().frame(height: 10)

            // Main content
            StatusMainContentView(
                viewModel: viewModel,
                appSettings: appSettings,
                theme: theme,
                openURL: openURL
            )

            // Media
            if viewModel.hasImages {
                StatusMediaView(
                    viewModel: viewModel,
                    appSettings: appSettings,
                    onMediaClick: onMediaClick
                )
            }

            // Card (Podcast or Link Preview)
            if viewModel.hasCard, let card = viewModel.statusData.card {
                StatusCardView(
                    card: card,
                    viewModel: viewModel,
                    appSettings: appSettings,
                    onPodcastCardTap: onPodcastCardTap
                )
            }

            // Quote
            if viewModel.hasQuote {
                StatusQuoteView(quotes: viewModel.statusData.quote, onMediaClick: onMediaClick)
            }

            // misskey 的+ 的emojis
            if viewModel.hasBottomContent, let bottomContent = viewModel.statusData.bottomContent {
                StatusBottomContentView(bottomContent: bottomContent)
            }

            // Detail date
            if viewModel.isDetailView {
                StatusDetailDateView(createdAt: viewModel.statusData.createdAt)
            }
        }
    }
}

struct StatusReplyView: View {
    let aboveTextContent: UiTimelineItemContentStatusAboveTextContent

    var body: some View {
        switch onEnum(of: aboveTextContent) {
        case let .replyTo(data):
            Text(String(localized: "Reply to \(data.handle.removingHandleFirstPrefix("@"))"))
                .font(.caption)
                .opacity(0.6)
        }
        Spacer().frame(height: 4)
    }
}

struct StatusContentWarningView: View {
    let contentWarning: UiRichText
    let theme: FlareTheme
    let openURL: OpenURLAction

    var body: some View {
        Button(action: {
            // withAnimation {
            //     // expanded = !expanded
            // }
        }) {
            Image(systemName: "exclamationmark.triangle")
                .foregroundColor(theme.labelColor)

            FlareText(
                contentWarning.raw,
                contentWarning.markdown,
                style: FlareTextStyle.Style(
                    font: Font.scaledCaptionFont,
                    textColor: UIColor(.gray),
                    linkColor: UIColor(theme.tintColor),
                    mentionColor: UIColor(theme.tintColor),
                    hashtagColor: UIColor(theme.tintColor),
                    cashtagColor: UIColor(theme.tintColor)
                ),
                isRTL: contentWarning.isRTL
            )
            .onLinkTap { url in
                openURL(url)
            }
            .lineSpacing(CGFloat(theme.lineSpacing))
            .foregroundColor(theme.labelColor)
            // Markdown()
            //     .font(.caption2)
            //     .markdownInlineImageProvider(.emoji)
            // Spacer()
            // if expanded {
            //     Image(systemName: "arrowtriangle.down.circle.fill")
            // } else {
            //     Image(systemName: "arrowtriangle.left.circle.fill")
            // }
        }
        .opacity(0.6)
        .buttonStyle(.plain)
        // if expanded {
        //     Spacer()
        //         .frame(height: 8)
        // }
    }
}

struct StatusMainContentView: View {
    let viewModel: StatusViewModel
    let appSettings: AppSettings
    let theme: FlareTheme
    let openURL: OpenURLAction

    var body: some View {
        if viewModel.hasContent {
            let content = viewModel.statusData.content
            FlareText(
                content.raw,
                content.markdown,
                style: FlareTextStyle.Style(
                    font: Font.scaledBodyFont,
                    textColor: UIColor(theme.labelColor),
                    linkColor: UIColor(theme.tintColor),
                    mentionColor: UIColor(theme.tintColor),
                    hashtagColor: UIColor(theme.tintColor),
                    cashtagColor: UIColor(theme.tintColor)
                ),
                isRTL: content.isRTL
            )
            .onLinkTap { url in
                openURL(url)
            }
            .lineSpacing(CGFloat(theme.lineSpacing))
            .foregroundColor(theme.labelColor)

            if appSettings.appearanceSettings.autoTranslate, viewModel.shouldShowTranslation {
                TranslatableText(originalText: content.raw)
            }
        } else {
            Text("")
                .font(.system(size: 16))
                .foregroundColor(theme.labelColor)
        }
    }
}

struct StatusMediaView: View {
    let viewModel: StatusViewModel
    let appSettings: AppSettings
    let onMediaClick: (Int, UiMedia) -> Void

    var body: some View {
        Spacer().frame(height: 8)

        MediaComponent(
            hideSensitive: viewModel.statusData.sensitive && !appSettings.appearanceSettings.showSensitiveContent,
            medias: viewModel.statusData.images,
            onMediaClick: { index, media in
                PhotoBrowserManager.shared.showPhotoBrowser(
                    media: media,
                    images: viewModel.statusData.images,
                    initialIndex: index
                )
            },
            sensitive: viewModel.statusData.sensitive
        )
    }
}

struct StatusCardView: View {
    let card: UiCard
    let viewModel: StatusViewModel
    let appSettings: AppSettings
    let onPodcastCardTap: (UiCard) -> Void

    var body: some View {
        if viewModel.isPodcastCard {
            PodcastPreview(card: card)
                .onTapGesture {
                    onPodcastCardTap(card)
                }
        } else if appSettings.appearanceSettings.showLinkPreview, viewModel.shouldShowLinkPreview {
            LinkPreview(card: card)
        }
    }
}

struct StatusQuoteView: View {
    let quotes: [UiTimelineItemContentStatus]
    let onMediaClick: (Int, UiMedia) -> Void

    var body: some View {
        Spacer().frame(height: 10)

        VStack {
            ForEach(0 ..< quotes.count, id: \.self) { index in
                let quote = quotes[index]
                QuotedStatus(data: quote, onMediaClick: onMediaClick)
                    .foregroundColor(.gray)

                if index != quotes.count - 1 {
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
    }
}

struct StatusBottomContentView: View {
    let bottomContent: UiTimelineItemContentStatusBottomContent

    var body: some View {
        switch onEnum(of: bottomContent) {
        case let .reaction(data):
            ScrollView(.horizontal) {
                LazyHStack {
                    if !data.emojiReactions.isEmpty {
                        ForEach(0 ..< data.emojiReactions.count, id: \.self) { index in
                            let reaction = data.emojiReactions[index]
                            Button(action: {
                                reaction.onClicked()
                            }) {
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
                            .buttonStyle(.borderless)
                        }
                    }
                }
            }
        }
    }
}

struct StatusDetailDateView: View {
    let createdAt: Date

    var body: some View {
        Spacer().frame(height: 4)
        HStack {
            Text(createdAt, style: .date)
            Text(createdAt, style: .time)
        }
        .opacity(0.6)
    }
}

struct StatusActionsView: View {
    let viewModel: StatusViewModel
    let appSettings: AppSettings
    let openURL: OpenURLAction
    let parentView: CommonTimelineStatusComponent

    var body: some View {
        Spacer().frame(height: 10)

        if appSettings.appearanceSettings.showActions || viewModel.isDetailView, viewModel.hasActions {
            let processedActions = viewModel.getProcessedActions()

            HStack(spacing: 0) {
                ForEach(0 ..< processedActions.mainActions.count, id: \.self) { actionIndex in
                    let action = processedActions.mainActions[actionIndex]

                    StatusActionButton(
                        action: action,
                        isDetail: viewModel.isDetailView,
                        openURL: openURL
                    )
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 10)
                }

                ShareButton(content: viewModel.statusData, view: parentView)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 0)
            }
            .padding(.vertical, 6)
            .labelStyle(CenteredLabelStyle())
            .buttonStyle(.borderless)
            .opacity(0.6)
            .if(!viewModel.isDetailView) { view in
                view.font(.caption)
            }
            .allowsHitTesting(true)
            .contentShape(Rectangle())
            .onTapGesture {}
        }
    }
}

struct StatusActionButton: View {
    let action: StatusAction
    let isDetail: Bool
    let openURL: OpenURLAction

    var body: some View {
        switch onEnum(of: action) {
        case .asyncActionItem:
            EmptyView()
        case let .item(item):
            Button(action: {
                       handleItemAction(item)
                   },
                   label: {
                       StatusActionLabel(item: item)
                   })
        case let .group(group):
            Menu {
                ForEach(0 ..< group.actions.count, id: \.self) { subActionIndex in
                    let subAction = group.actions[subActionIndex]
                    if case let .item(item) = onEnum(of: subAction) {
                        StatusActionMenuItem(item: item, openURL: openURL)
                    }
                }
            } label: {
                StatusActionLabel(item: group.displayItem)
            }
        }
    }

    private func handleItemAction(_ item: StatusActionItem) {
        if let clickable = item as? StatusActionItemClickable {
            os_log("[URL点击] 状态操作点击: %{public}@", log: .default, type: .debug, String(describing: type(of: item)))
            clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))

            if case .report = onEnum(of: item) {
                ActionProcessor.showReportToast()
            }
        }
    }
}

struct StatusActionMenuItem: View {
    let item: StatusActionItem
    let openURL: OpenURLAction

    var body: some View {
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
                if let clickable = item as? StatusActionItemClickable {
                    clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                }
            },
            label: {
                let text: LocalizedStringKey =
                    switch onEnum(of: item) {
                    case let .bookmark(data):
                        data.bookmarked
                            ? LocalizedStringKey("status_action_unbookmark")
                            : LocalizedStringKey("status_action_bookmark")
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
