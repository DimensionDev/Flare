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

struct StatusActionsView: View {
    let viewModel: StatusViewModel
    let appSettings: AppSettings
    let openURL: OpenURLAction
    let parentView: TimelineStatusView

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
//            .labelStyle(CenteredLabelStyle())
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
        case let .item(item):
            Button(action: {
                       handleItemAction(item)
                   },
                   label: {
                       StatusActionLabel(item: item)
                   })
        case .asyncActionItem:
            EmptyView()
        case let .group(group):
            EmptyView()
            // menu给删了，代码留着吧
//            Menu {
//                // ForEach(0 ..< group.actions.count, id: \.self) { subActionIndex in
//                //     let subAction = group.actions[subActionIndex]
//                //     if case let .item(item) = onEnum(of: subAction) {
//                //         StatusActionMenuItem(item: item, openURL: openURL)
//                //     }
//                // }
//            } label: {
//                StatusActionLabel(item: group.displayItem)
//            }
        }
    }

    private func handleItemAction(_ item: StatusActionItem) {
        if let clickable = item as? StatusActionItemClickable {
            os_log("[URL点击] 状态操作点击: %{public}@", log: .default, type: .debug, String(describing: type(of: item)))
            clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))

            if case .report = onEnum(of: item) {
                ToastView(
                    icon: UIImage(systemName: "flag.fill"),
                    message: " report success"
                ).show()
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
        Label {
            let textContent =
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
            Text(textContent).font(.system(size: 12))
        } icon: {
            switch onEnum(of: item) {
            case let .bookmark(data):
                if data.bookmarked {
                    Image(asset: Asset.Image.Status.Toolbar.bookmarkFilled)
                        .renderingMode(.template)
                } else {
                    Image(asset: Asset.Image.Status.Toolbar.bookmark)
                        .renderingMode(.template)
                }
            case .delete:
                Image(asset: Asset.Image.Status.Toolbar.delete)
                    .renderingMode(.template)
            case let .like(data):
                if data.liked {
                    Image(asset: Asset.Image.Status.Toolbar.favorite)
                        .renderingMode(.template)
                } else {
                    Image(asset: Asset.Image.Status.Toolbar.favoriteBorder)
                        .renderingMode(.template)
                }
            case .more:
                Image(asset: Asset.Image.Status.more)
                    .renderingMode(.template)
                    .rotationEffect(.degrees(90))
            case .quote:
                Image(asset: Asset.Image.Status.Toolbar.quote)
                    .renderingMode(.template)
            case let .reaction(data):
                if data.reacted {
                    Awesome.Classic.Solid.minus.image
                } else {
                    Awesome.Classic.Solid.plus.image
                }
            case .reply:
                Image(asset: Asset.Image.Status.Toolbar.chatBubbleOutline)
                    .renderingMode(.template)
            case .report:
                Image(asset: Asset.Image.Status.Toolbar.flag)
                    .renderingMode(.template)
            case let .retweet(data):
                if data.retweeted {
                    Image(asset: Asset.Image.Status.Toolbar.repeat)
                        .renderingMode(.template)
                } else {
                    Image(asset: Asset.Image.Status.Toolbar.repeat)
                        .renderingMode(.template)
                }
            }
        }
        .foregroundStyle(theme.labelColor, theme.labelColor)
    }
}

// struct CenteredLabelStyle: LabelStyle {
//     @Environment(FlareTheme.self) private var theme

//     func makeBody(configuration: Configuration) -> some View {
//         HStack(spacing: 4) {
//             configuration.icon.foregroundColor(theme.labelColor)
//             configuration.title
//                 .font(.system(size: 12))
//         }
//         .frame(maxWidth: .infinity, alignment: .center)
//     }
// }

// struct StatusActionMenuItem: View {
//     let item: StatusActionItem
//     let openURL: OpenURLAction
//     @Environment(\.colorScheme) var colorScheme // Add if not present, for icon logic

//     var body: some View {
//         let role: ButtonRole? =
//             if let colorData = item as? StatusActionItemColorized {
//                 switch colorData.color {
//                 case .red: .destructive
//                 case .primaryColor: nil
//                 case .contentColor: nil
//                 case .error: .destructive
//                 }
//             } else {
//                 nil
//             }

//         Button(
//             role: role,
//             action: {
//                 if let clickable = item as? StatusActionItemClickable {
//                     clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
//                 }
//             },
//             label: {
//                 let text: LocalizedStringKey =
//                     switch onEnum(of: item) {
//                     case let .bookmark(data):
//                         data.bookmarked
//                             ? LocalizedStringKey("status_action_unbookmark")
//                             : LocalizedStringKey("status_action_bookmark")
//                     case .delete:
//                         LocalizedStringKey("status_action_delete")
//                     case let .like(data):
//                         data.liked
//                             ? LocalizedStringKey(
//                                 "status_action_unlike")
//                             : LocalizedStringKey(
//                                 "status_action_like")
//                     case .quote: LocalizedStringKey("quote")
//                     case .reaction:
//                         LocalizedStringKey(
//                             "status_action_add_reaction")
//                     case .reply:
//                         LocalizedStringKey("status_action_reply")
//                     case .report: LocalizedStringKey("report")
//                     case let .retweet(data):
//                         data.retweeted
//                             ? LocalizedStringKey("retweet_remove")
//                             : LocalizedStringKey("retweet")
//                     case .more:
//                         LocalizedStringKey("status_action_more")
//                     }
//                 Label {
//                     Text(text)
//                 } icon: {
//                     // Inlined icon logic for StatusActionMenuItem
//                     switch onEnum(of: item) {
//                         case let .bookmark(data):
//                             if data.bookmarked {
//                                 Image(asset: Asset.Image.Status.Toolbar.bookmarkFilled)
//                                     .renderingMode(.template)
//                             } else {
//                                 Image(asset: Asset.Image.Status.Toolbar.bookmark)
//                                     .renderingMode(.template)
//                             }
//                         case .delete:
//                             Image(asset: Asset.Image.Status.Toolbar.delete)
//                                 .renderingMode(.template)
//                         case let .like(data):
//                             if data.liked {
//                                 Image(asset: Asset.Image.Status.Toolbar.favorite)
//                                     .renderingMode(.template)
//                             } else {
//                                 Image(asset: Asset.Image.Status.Toolbar.favoriteBorder)
//                                     .renderingMode(.template)
//                             }
//                         case .more:
//                             Image(asset: Asset.Image.Status.more)
//                                 .renderingMode(.template)
//                                 .rotationEffect(.degrees(90))
//                         case .quote:
//                             Image(asset: Asset.Image.Status.Toolbar.quote)
//                                 .renderingMode(.template)
//                         case let .reaction(data):
//                             if data.reacted {
//                                 Awesome.Classic.Solid.minus.image
//                             } else {
//                                 Awesome.Classic.Solid.plus.image
//                             }
//                         case .reply:
//                             Image(asset: Asset.Image.Status.Toolbar.chatBubbleOutline)
//                                 .renderingMode(.template)
//                         case .report:
//                             Image(asset: Asset.Image.Status.Toolbar.flag)
//                                 .renderingMode(.template)
//                         case let .retweet(data):
//                             if data.retweeted {
//                                 Image(asset: Asset.Image.Status.Toolbar.repeat)
//                                     .renderingMode(.template)
//                             } else {
//                                 Image(asset: Asset.Image.Status.Toolbar.repeat)
//                                     .renderingMode(.template)
//                             }
//                     }
//                 }
//             }
//         )
//     }
// }
