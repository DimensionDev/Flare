import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI

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
            toastView.show(in: window)
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
                    bottomMainActions.append(action)
                }
            case let .group(group):
                if let displayItem = group.displayItem as? StatusActionItemMore {
                    // 只处理 More 菜单中的操作
                    for subAction in group.actions {
                        if case let .item(item) = onEnum(of: subAction) {
                            if item is StatusActionItemBookmark {
                                // 将书签添加到主操作
                                bottomMainActions.append(subAction)
                            } else {
                                // 其他操作添加到更多操作
                                bottomMoreActions.append(item)
                            }
                        }
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
            Spacer()
                .frame(height: 2)
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
                VStack(alignment: .trailing, spacing: 1) {
                    // 更多按钮
                    if !processActions().moreActions.isEmpty {
                        Menu {
                            ForEach(0 ..< processActions().moreActions.count, id: \.self) { index in
                                let item = processActions().moreActions[index]
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
                                        // 如果是举报操作，显示 Toast
                                        if case .report = onEnum(of: item) {
                                            showReportToast()
                                        }
                                    }
                                }, label: {
                                    let text: LocalizedStringKey = switch onEnum(of: item) {
                                    case let .bookmark(data): data.bookmarked ? LocalizedStringKey("status_action_unbookmark") : LocalizedStringKey("status_action_bookmark")
                                    case .delete: LocalizedStringKey("status_action_delete")
                                    case let .like(data): data.liked ? LocalizedStringKey("status_action_unlike") : LocalizedStringKey("status_action_like")
                                    case .quote: LocalizedStringKey("quote")
                                    case .reaction: LocalizedStringKey("status_action_add_reaction")
                                    case .reply: LocalizedStringKey("status_action_reply")
                                    case .report: LocalizedStringKey("report")
                                    case let .retweet(data): data.retweeted ? LocalizedStringKey("retweet_remove") : LocalizedStringKey("retweet")
                                    case .more: LocalizedStringKey("status_action_more")
                                    }
                                    Label {
                                        Text(text)
                                    } icon: {
                                        StatusActionItemIcon(item: item)
                                    }
                                })
                            }
                        } label: {
                            Image(asset: Asset.Image.Status.more)
                                .renderingMode(.template)
                                .rotationEffect(.degrees(0))
                                .foregroundColor(.gray.opacity(0.6))
                                .modifier(SmallIconModifier())
                        }
                        .padding(.top, 0)
                    }

                    HStack(spacing: 10) {
                        if !isDetail {
                            dateFormatter(data.createdAt)
                                .foregroundColor(.gray)
                                .font(.caption)
                                .frame(minWidth: 80, alignment: .trailing)
                        }
                    }
                    .frame(minWidth: 80)
                }
                .padding(.bottom, 1)
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

            if let cwText = data.contentWarning, !cwText.raw.isEmpty {
                Button(action: {
                    withAnimation {
                        expanded = !expanded
                    }
                }, label: {
                    Image(systemName: "exclamationmark.triangle")
                    Markdown(cwText.markdown)
                        .font(.body)
                        .markdownInlineImageProvider(.emoji)
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
            if expanded || data.contentWarning == nil || data.contentWarning?.raw.isEmpty == true {
                Spacer()
                    .frame(height: 10)

                if !data.content.raw.isEmpty {
                    FlareText(data.content.raw)
                        .onLinkTap { url in
                            openURL(url)
                        }
                        .font(.system(size: 16))
                        .foregroundColor(Colors.Text.swiftUIPrimary)

                    // Add translation component
                    if appSettings.appearanceSettings.autoTranslate, enableTranslation {
                        TranslatableText(originalText: data.content.raw)
                    }
                } else {
                    // 如果内容为空，显示一个空的 Text
                    Text("")
                        .font(.system(size: 16))
                        .foregroundColor(Colors.Text.swiftUIPrimary)
                }
            }
            // media
            if !data.images.isEmpty {
                Spacer().frame(height: 8)

                // if appSettings.appearanceSettings.showMedia || showMedia {
                MediaComponent(
                    hideSensitive: data.sensitive && !appSettings.appearanceSettings.showSensitiveContent,
                    medias: data.images,
                    onMediaClick: handleMediaClick, // 打开预览
                    sensitive: data.sensitive
                )
                // } else {
                //    Button {
                //        withAnimation {
                //            showMedia = true
                //        }
                //    } label: {
                //        Label(
                //            title: { Text("status_display_media") },
                //            icon: { Image(systemName: "photo") }
                //        )
                //    }
                //    .buttonStyle(.borderless)
                // }
            }
            // link preview
            if let card = data.card, appSettings.appearanceSettings.showLinkPreview {
                LinkPreview(card: card)
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
                .frame(height: 10)

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
                            Button(action: {
                                if let clickable = item as? StatusActionItemClickable {
                                    clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                                    // 如果是举报操作，显示 Toast
                                    if case .report = onEnum(of: item) {
                                        showReportToast()
                                    }
                                }
                            }, label: {
                                StatusActionLabel(item: item)
                            })
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 10) // 添加水平内边距
                        case let .group(group):
                            Menu {
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
                                            case .quote: LocalizedStringKey("quote")
                                            case .reaction: LocalizedStringKey("status_action_add_reaction")
                                            case .reply: LocalizedStringKey("status_action_reply")
                                            case .report: LocalizedStringKey("report")
                                            case let .retweet(data): data.retweeted ? LocalizedStringKey("retweet_remove") : LocalizedStringKey("retweet")
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
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 10) // 添加水平内边距
                        }
                    }

                    // 使用新的 ShareButton
                    ShareButton(content: data.content.raw, view: self)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 0) // 添加水平内边距
                }
                .labelStyle(CenteredLabelStyle())
                .buttonStyle(.borderless)
                .opacity(0.6)
                .if(!isDetail) { view in
                    view.font(.caption)
                }
                .allowsHitTesting(true)
            }
            Spacer()
                .frame(height: 2)
        }.frame(alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture {
                if let tapLocation = (UIApplication.shared.windows.first?.hitTest(UIApplication.shared.windows.first?.convert(CGPoint(x: 0, y: 0), to: nil) ?? .zero, with: nil)) {
                    let bottomActionBarFrame = CGRect(x: 16, y: tapLocation.frame.height - 44, width: tapLocation.frame.width - 32, height: 44)
                    if !bottomActionBarFrame.contains(tapLocation.frame.origin) {
                        data.onClicked(ClickContext(launcher: AppleUriLauncher(openURL: openURL)))
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
                    .foregroundColor(Colors.State.swiftUIBookmarkActive)
            } else {
                Image(asset: Asset.Image.Status.Toolbar.bookmark)
                    .renderingMode(.template)
                    .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
            }
        case .delete:
            Image(asset: Asset.Image.Status.Toolbar.delete)
                .renderingMode(.template)
                .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
        case let .like(data):
            if data.liked {
                Image(asset: Asset.Image.Status.Toolbar.favorite)
                    .renderingMode(.template)
                    .foregroundColor(Colors.State.swiftUILikeActive)
            } else {
                Image(asset: Asset.Image.Status.Toolbar.favoriteBorder)
                    .renderingMode(.template)
                    .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
            }
        case .more:
            Image(asset: Asset.Image.Status.more)
                .renderingMode(.template)
                .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
                .rotationEffect(.degrees(90))
        case .quote:
            Image(asset: Asset.Image.Status.Toolbar.quote)
                .renderingMode(.template)
                .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
        case let .reaction(data):
            if data.reacted {
                Awesome.Classic.Solid.minus.image
                    .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
            } else {
                Awesome.Classic.Solid.plus.image
                    .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
            }
        case .reply:
            Image(asset: Asset.Image.Status.Toolbar.chatBubbleOutline)
                .renderingMode(.template)
                .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
        case .report:
            Image(asset: Asset.Image.Status.Toolbar.flag)
                .renderingMode(.template)
                .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
        case let .retweet(data):
            if data.retweeted {
                Image(asset: Asset.Image.Status.Toolbar.repeat)
                    .renderingMode(.template)
                    .foregroundColor(Colors.State.swiftUIRetweetActive)
            } else {
                Image(asset: Asset.Image.Status.Toolbar.repeat)
                    .renderingMode(.template)
                    .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
            }
        }
    }
}

// bottom action
struct StatusActionLabel: View {
    let item: StatusActionItem
    @Environment(\.colorScheme) var colorScheme

    var body: some View {
        let text = switch onEnum(of: item) {
        case let .like(data): formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
        case let .retweet(data): formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
        case let .quote(data): formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
        case let .reply(data): formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
        case let .bookmark(data): formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
        default: ""
        }

        let color = switch onEnum(of: item) {
        case let .retweet(data):
            data.retweeted ? Colors.State.swiftUIRetweetActive : (colorScheme == .dark ? Color.white : Color.black)
        case let .bookmark(data):
            data.bookmarked ? Colors.State.swiftUIBookmarkActive : (colorScheme == .dark ? Color.white : Color.black)
        case let .like(data):
            data.liked ? Colors.State.swiftUILikeActive : (colorScheme == .dark ? Color.white : Color.black)
        default:
            colorScheme == .dark ? Color.white : Color.black
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
        HStack(spacing: 4) {
            configuration.icon
            configuration.title
                .font(.system(size: 12))
            // Spacer() // 添加 Spacer 让内容靠左
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

// 添加一个新的 View 来处理分享按钮
struct ShareButton: View {
    @Environment(\.colorScheme) var colorScheme
    let content: String
    let view: CommonTimelineStatusComponent

    var body: some View {
        Menu {
            Button(action: {
                // 系统分享
                let activityVC = UIActivityViewController(activityItems: [content], applicationActivities: nil)
                if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                   let window = windowScene.windows.first,
                   let rootVC = window.rootViewController
                {
                    activityVC.popoverPresentationController?.sourceView = window
                    rootVC.present(activityVC, animated: true)
                }
            }) {
                Label {
                    Text("Share")
                } icon: {
                    Image(systemName: "square.and.arrow.up")
                }
            }

            Button(action: {
                // 截图分享
                if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                   let window = windowScene.windows.first
                {
                    let renderer = ImageRenderer(content: view)
                    if let uiImage = renderer.uiImage {
                        let activityVC = UIActivityViewController(activityItems: [uiImage], applicationActivities: nil)
                        if let rootVC = window.rootViewController {
                            activityVC.popoverPresentationController?.sourceView = window
                            rootVC.present(activityVC, animated: true)
                        }
                    }
                }
            }) {
                Label {
                    Text("Screenshot Share")
                } icon: {
                    Image(systemName: "camera")
                }
            }
        } label: {
            HStack {
                Spacer()
                Spacer()
                Label {
                    Text("") // 空文本，保持与其他按钮一致的结构
                } icon: {
                    Image(systemName: "square.and.arrow.up")
                        .imageScale(.medium)
                        .font(.system(size: 13))
                        .foregroundColor(colorScheme == .dark ? Color.white : Color.black)
                }
                Spacer()
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
    }
}
