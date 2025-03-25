import Kingfisher
import shared
import SwiftfulRouting
import SwiftUI

struct FlareDestinationView: View {
    let destination: FlareDestination

    @ObservedObject var appState: FlareAppState

    var body: some View {
        switch destination {
        case let .profile(accountType, userKey):
            ProfileTabScreen(
                accountType: accountType,
                userKey: userKey,
                toProfileMedia: { _ in
//                    print("查看用户媒体: \(userKey.description)")
                }
            )

        case let .profileWithNameAndHost(accountType, userName, host):
            ProfileWithUserNameScreen(
                accountType: accountType,
                userName: userName,
                host: host,
                toProfileMedia: { _ in
                    // 在这里处理媒体查看导航
//                    print("查看用户媒体: \(userKey.description)")
                }
            )

        case let .statusDetail(accountType, statusKey):
            StatusDetailScreen(accountType: accountType, statusKey: statusKey)

        case let .search(accountType, keyword):
            SearchScreen(
                accountType: accountType,
                initialQuery: keyword
            )

        case let .statusMedia(accountType, statusKey, index):
            StatusMediaScreen(
                accountType: accountType,
                statusKey: statusKey,
                index: Int32(index),
                dismiss: {}
            )

        case let .compose(accountType, status):
            NewComposeView(accountType: accountType, status: status)

        case let .addReaction(accountType, statusKey):
            AddReactionView(accountType: accountType, statusKey: statusKey)

 //        case .blueskyReportStatus(let accountType, let statusKey):
//            BlueskyReportStatusView(accountType: accountType, statusKey: statusKey)
//
//        case .mastodonReportStatus(let accountType, let statusKey, let userKey):
//            MastodonReportStatusView(accountType: accountType, statusKey: statusKey, userKey: userKey)
//
//        case .misskeyReportStatus(let accountType, let statusKey, let userKey):
//            MisskeyReportStatusView(accountType: accountType, statusKey: statusKey, userKey: userKey)

        case let .vvoStatusDetail(accountType, statusKey):
            VVOStatusDetailView(accountType: accountType, statusKey: statusKey)

        case let .vvoCommentDetail(accountType, statusKey):
            VVOCommentDetailView(accountType: accountType, statusKey: statusKey)

//        case .vvoReplyToComment(let accountType, let replyTo, let rootId):
//            VVOReplyToCommentView(accountType: accountType, replyTo: replyTo, rootId: rootId)

//        case let .rawImage(url):
//            RawImageView(url: url)

        case let .callback(type):
            CallbackView(type: type)

        case let .deleteStatus(accountType, statusKey):
            DeleteStatusView(accountType: accountType, statusKey: statusKey)

        // 默认情况
        default:
            Text("页面未找到")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

struct NewComposeView: View {
    let accountType: AccountType
    let status: FlareComposeStatus?

    @EnvironmentObject private var router: FlareRouter

    var body: some View {
        ComposeScreen(
            onBack: {
                router.dismissSheet()
            },
            accountType: accountType,
            status: convertToSharedComposeStatus(status)
        )
    }

    private func convertToSharedComposeStatus(_ status: FlareComposeStatus?) -> shared.ComposeStatus? {
        guard let status else { return nil }

        switch status {
        case let .reply(statusKey):
            return shared.ComposeStatusReply(statusKey: statusKey)
        case let .quote(statusKey):
            return shared.ComposeStatusQuote(statusKey: statusKey)
        case let .vvoComment(statusKey, rootId):
            return shared.ComposeStatusVVOComment(statusKey: statusKey, rootId: rootId)
        }
    }
}

struct AddReactionView: View {
    let accountType: AccountType
    let statusKey: MicroBlogKey

    @EnvironmentObject private var router: FlareRouter

    var body: some View {
        AddReactionSheet(
            accountType: accountType,
            statusKey: statusKey,
            onBack: {
                router.dismissSheet()
            }
        )
    }
}

struct VVOStatusDetailView: View {
    let accountType: AccountType
    let statusKey: MicroBlogKey

    var body: some View {
        VVOStatusDetailScreen(accountType: accountType, statusKey: statusKey)
    }
}

struct VVOCommentDetailView: View {
    let accountType: AccountType
    let statusKey: MicroBlogKey

    var body: some View {
        VVOCommentScreen(accountType: accountType, commentKey: statusKey)
    }
}

struct CallbackView: View {
    let type: FlareDestination.CallbackType
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 60))
                .foregroundColor(.green)
                .padding()

            Text("授权成功")
                .font(.headline)

            Text("您的账号已成功授权")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            Button(action: {
                dismiss()
            }) {
                Text("完成")
                    .frame(width: 120)
                    .padding()
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }
            .padding(.top)

            Text("您现在可以使用该账号在应用中发布内容和互动")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(UIColor.systemBackground))
    }
}

struct DeleteStatusView: View {
    let accountType: AccountType
    let statusKey: MicroBlogKey
    @State private var presenter: DeleteStatusPresenter
    @State private var showConfirmation = true

    @EnvironmentObject private var router: FlareRouter

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.accountType = accountType
        self.statusKey = statusKey
        _presenter = State(initialValue: .init(accountType: accountType, statusKey: statusKey))
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            VStack(spacing: 16) {
                if showConfirmation {
                    VStack(spacing: 16) {
                        Text("确认删除")
                            .font(.headline)
                            .padding(.top)

                        Text("您确定要删除这条内容吗？")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)

                        Text("此操作无法撤销")
                            .font(.caption)
                            .foregroundColor(.red)

                        HStack(spacing: 16) {
                            Button(action: {
                                // 关闭对话框
                                router.dismissAll()
                            }) {
                                Text("Cancel")
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .background(Color.secondary.opacity(0.2))
                                    .foregroundColor(.primary)
                                    .cornerRadius(8)
                            }

                            Button(action: {
                                state.delete()
                                showConfirmation = false
                            }) {
                                Text("Delete")
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .background(Color.red)
                                    .foregroundColor(.white)
                                    .cornerRadius(8)
                            }
                        }
                        .padding()
                    }
                } else {
                    VStack(spacing: 16) {
                        Text("删除成功")
                            .font(.headline)
                            .padding(.top)

                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 50))
                            .foregroundColor(.green)
                            .padding()

                        Text("内容已成功删除")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)

                        Button(action: {
                            // 关闭对话框
                            router.dismissAll()
                        }) {
                            Text("返回")
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.accentColor)
                                .foregroundColor(.white)
                                .cornerRadius(8)
                        }
                        .padding()
                    }
                }
            }
            .frame(maxWidth: 300)
            .padding()
            .background(Color(UIColor.systemBackground))
            .cornerRadius(16)
            .shadow(radius: 10)
        }
    }
}
