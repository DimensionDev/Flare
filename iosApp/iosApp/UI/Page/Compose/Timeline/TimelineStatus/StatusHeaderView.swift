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

struct StatusHeaderView: View {
    let viewModel: StatusViewModel
    @Environment(FlareRouter.self) private var router
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
                    .environment(router)
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
