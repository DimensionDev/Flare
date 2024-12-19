import Awesome
import JXPhotoBrowser
import Kingfisher
// import MarkdownUI
import shared
import SwiftDate
import SwiftUI
import UIKit

// timeline tweet
struct CommonTimelineStatusComponent: View {
    @State private var showMedia: Bool = false
    @State private var expanded: Bool = false
    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings

    let data: UiTimelineItemContentStatus
    let onMediaClick: (Int, UiMedia) -> Void
    let isDetail: Bool
    
    private func showReportToast() {
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let window = windowScene.windows.first {
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
            case .item(let item):
                // 所有非 More 的 item 都加入主操作
                if !(item is StatusActionItemMore) {
                    bottomMainActions.append(action)
                }
            case .group(let group):
                if let displayItem = group.displayItem as? StatusActionItemMore {
                    // 只处理 More 菜单中的操作
                    for subAction in group.actions {
                        if case .item(let item) = onEnum(of: subAction) {
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
                Spacer()
                    .frame(height: 10)
                
                if !data.content.raw.isEmpty {
                    FlareText(data.content.raw)
                        .onLinkTap { url in
                            openURL(url)
                        }
                        .font(.system(size: 16))
                        .foregroundColor(Colors.Text.swiftUIPrimary)
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
//                #if os(iOS)
//                 .background(Color(UIColor.secondarySystemBackground))
//                #else
//                .background(Color(NSColor.windowBackgroundColor))
//                #endif
               
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
                HStack {
                    // 显示主要操作
                    ForEach(0 ..< processedActions.mainActions.count, id: \.self) { actionIndex in
                        let action = processedActions.mainActions[actionIndex]
                        switch onEnum(of: action) {
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
                                }
                            } label: {
                                StatusActionLabel(item: group.displayItem)
                            }
                        }
                        
                        if actionIndex < processedActions.mainActions.count - 1 {
                            Spacer()
                        }
                    }
                    
                    Spacer()
                        .frame(width: 32)  // 主要操作和 More 按钮之间的间距
                    
                    // 显示更多操作菜单
                    if !processedActions.moreActions.isEmpty {
                        Menu {
                            ForEach(0 ..< processedActions.moreActions.count, id: \.self) { index in
                                let item = processedActions.moreActions[index]
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
                                .rotationEffect(.degrees(90)) 
                                .frame(width: 35, alignment: .trailing)  // 增加宽度并靠右对齐
                        }
                    }
                }
                .padding(.horizontal, 16)
                .labelStyle(CenteredLabelStyle())
                .buttonStyle(.borderless)
                .opacity(0.6)
                .if(!isDetail) { view in
                    view
                        .font(.caption)
                }
            }
              Spacer()
                .frame(height: 2)
        }.frame(alignment: .leading)
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
                .frame(alignment: .center)
            configuration.title
                .frame(alignment: .center)
                .font(.system(size: 12))
        }
    }
}

func formatCount(_ count: Int64) -> String {
    if count == 0 {
        return ""
    }
    if count < 1000 {
        return "\(count)"
    }
    if count < 1000000 {
        let k = Double(count) / 1000.0
        return String(format: "%.1fK", k).replacingOccurrences(of: ".0", with: "")
    }
    let m = Double(count) / 1000000.0
    return String(format: "%.1fM", m).replacingOccurrences(of: ".0", with: "")
}
