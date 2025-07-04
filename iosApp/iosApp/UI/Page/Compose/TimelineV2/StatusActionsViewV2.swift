// import Awesome
// import Generated
// import JXPhotoBrowser
// import Kingfisher
// import MarkdownUI
// import os.log
// import SwiftDate
// import SwiftUI
// import UIKit
// import shared
//
// struct StatusActionsViewV2: View {
//    let viewModel: StatusViewModel
//    let appSettings: AppSettings
//    let openURL: OpenURLAction
//    let parentView: TimelineStatusViewV2
//
//    var body: some View {
//        Spacer().frame(height: 10)
//
//        if appSettings.appearanceSettings.showActions || viewModel.isDetailView, viewModel.hasActions {
//            let processedActions = viewModel.getProcessedActions()
//
//            HStack(spacing: 0) {
//                ForEach(0 ..< processedActions.mainActions.count, id: \.self) { actionIndex in
//                    let action = processedActions.mainActions[actionIndex]
//
//                    StatusActionButtonV2(
//                        action: action,
//                        isDetail: viewModel.isDetailView,
//                        openURL: openURL
//                    )
//                    .frame(maxWidth: .infinity)
//                    .padding(.horizontal, 10)
//                }
//
////                ShareButtonV2(content: viewModel.statusData, view: parentView)
////                    .frame(maxWidth: .infinity)
////                    .padding(.horizontal, 0)
//            }
//            .padding(.vertical, 6)
////            .labelStyle(CenteredLabelStyle())
//            .buttonStyle(.borderless)
//            .opacity(0.6)
//            .if(!viewModel.isDetailView) { view in
//                view.font(.caption)
//            }
//            .allowsHitTesting(true)
//            .contentShape(Rectangle())
//            .onTapGesture {}
//        }
//    }
// }
//
// struct StatusActionButtonV2: View {
//    let action: StatusAction
//    let isDetail: Bool
//    let openURL: OpenURLAction
//
//    var body: some View {
//        switch onEnum(of: action) {
//        case let .item(item):
//            Button(action: {
//                       handleItemAction(item)
//                   },
//                   label: {
//                       StatusActionLabelV2(item: item)
//                   })
//        case .asyncActionItem:
//            EmptyView()
//        case let .group(group):
//            EmptyView()
//            // menu给删了，代码留着吧
////            Menu {
////                // ForEach(0 ..< group.actions.count, id: \.self) { subActionIndex in
////                //     let subAction = group.actions[subActionIndex]
////                //     if case let .item(item) = onEnum(of: subAction) {
////                //         StatusActionMenuItem(item: item, openURL: openURL)
////                //     }
////                // }
////            } label: {
////                StatusActionLabel(item: group.displayItem)
////            }
//        }
//    }
//
//    private func handleItemAction(_ item: StatusActionItem) {
//        if let clickable = item as? StatusActionItemClickable {
//            os_log("[URL点击] 状态操作点击: %{public}@", log: .default, type: .debug, String(describing: type(of: item)))
//            clickable.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
//
//            if case .report = onEnum(of: item) {
//                ToastView(
//                    icon: UIImage(systemName: "flag.fill"),
//                    message: " report success"
//                ).show()
//            }
//        }
//    }
// }
//
//// bottom action
// struct StatusActionLabelV2: View {
//    let item: StatusActionItem
//    @Environment(\.colorScheme) var colorScheme
//    @Environment(FlareTheme.self) private var theme
//
//    var body: some View {
//        Label {
//            let textContent =
//                switch onEnum(of: item) {
//                case let .like(data):
//                    formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
//                case let .retweet(data):
//                    formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
//                case let .quote(data):
//                    formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
//                case let .reply(data):
//                    formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
//                case let .bookmark(data):
//                    formatCount(data.humanizedCount.isEmpty ? 0 : Int64(data.humanizedCount) ?? 0)
//                default: ""
//                }
//            Text(textContent).font(.system(size: 12))
//        } icon: {
//            switch onEnum(of: item) {
//            case let .bookmark(data):
//                if data.bookmarked {
//                    Image(asset: Asset.Image.Status.Toolbar.bookmarkFilled)
//                        .renderingMode(.template)
//                } else {
//                    Image(asset: Asset.Image.Status.Toolbar.bookmark)
//                        .renderingMode(.template)
//                }
//            case .delete:
//                Image(asset: Asset.Image.Status.Toolbar.delete)
//                    .renderingMode(.template)
//            case let .like(data):
//                if data.liked {
//                    Image(asset: Asset.Image.Status.Toolbar.favorite)
//                        .renderingMode(.template)
//                } else {
//                    Image(asset: Asset.Image.Status.Toolbar.favoriteBorder)
//                        .renderingMode(.template)
//                }
//            case .more:
//                Image(asset: Asset.Image.Status.more)
//                    .renderingMode(.template)
//                    .rotationEffect(.degrees(90))
//            case .quote:
//                Image(asset: Asset.Image.Status.Toolbar.quote)
//                    .renderingMode(.template)
//            case let .reaction(data):
//                if data.reacted {
//                    Awesome.Classic.Solid.minus.image
//                } else {
//                    Awesome.Classic.Solid.plus.image
//                }
//            case .reply:
//                Image(asset: Asset.Image.Status.Toolbar.chatBubbleOutline)
//                    .renderingMode(.template)
//            case .report:
//                Image(asset: Asset.Image.Status.Toolbar.flag)
//                    .renderingMode(.template)
//            case let .retweet(data):
//                if data.retweeted {
//                    Image(asset: Asset.Image.Status.Toolbar.repeat)
//                        .renderingMode(.template)
//                } else {
//                    Image(asset: Asset.Image.Status.Toolbar.repeat)
//                        .renderingMode(.template)
//                }
//            }
//        }
//        .foregroundStyle(theme.labelColor, theme.labelColor)
//    }
// }
//
//// TODO: 需要创建ShareButtonV2
////struct ShareButtonV2: View {
////    let content: UiTimelineItemContentStatus
////    let view: TimelineStatusViewV2
////
////    var body: some View {
////        // 暂时使用简化实现
////        Button(action: {
////            // TODO: 实现分享功能
////        }) {
////            Image(systemName: "square.and.arrow.up")
////                .renderingMode(.template)
////        }
////    }
////}
