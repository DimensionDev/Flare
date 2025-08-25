import Kingfisher
import os.log
import shared
import SwiftfulRouting
import SwiftUI

struct FlareDestinationView: View {
    let destination: FlareDestination
    let router: FlareRouter

    @Environment(FlareMenuState.self) private var menuState
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        let _ = os_log("[FlareDestinationView] Rendering destination: %{public}@, router: %{public}@, depth: %{public}d",
                       log: .default, type: .debug,
                       String(describing: destination),
                       String(describing: ObjectIdentifier(router)),
                       router.navigationDepth)

        Group {
            switch destination {
            case let .profile(accountType, userKey):
                ProfileSwiftUIViewV2(
                    accountType: accountType,
                    userKey: userKey
                )
                .environment(router)

            case let .profileWithNameAndHost(accountType, userName, host):
                ProfileWithUserNameScreen(
                    accountType: accountType,
                    userName: userName,
                    host: host
                )
                .environment(router)

            case let .statusDetail(accountType, statusKey):
                StatusDetailScreen(accountType: accountType, statusKey: statusKey)

            case let .statusDetailV2(accountType, statusKey, preloadItem):
                StatusDetailScreenV2(accountType: accountType, statusKey: statusKey, preloadItem: preloadItem)

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
            // case let .compose(accountType, status):
                // 这段代码不会被执行，因为路由会被FlareRouter拦截

            case let .lists(accountType):
                AllListsView(accountType: accountType)
                    .navigationTitle("Lists")
                    .navigationBarTitleDisplayMode(.inline)

            case let .feeds(accountType):
                AllFeedsView(accountType: accountType)
                    .navigationTitle("Feeds")
                    .navigationBarTitleDisplayMode(.inline)

            case let .feedDetail(accountType, list, defaultUser):
                FeedDetailView(
                    list: list,
                    accountType: accountType,
                    defaultUser: defaultUser
                )
                .navigationTitle(list.title)
                .navigationBarTitleDisplayMode(.inline)

            case let .listDetail(accountType, list, defaultUser):
                ListDetailView(
                    list: list,
                    accountType: accountType,
                    defaultUser: defaultUser
                )
                .navigationTitle(list.title)
                .navigationBarTitleDisplayMode(.inline)

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

            case let .messages(accountType):
                MessageScreen(accountType: accountType)
                    .environment(router)

            case let .download(accountType):
                DownloadManagerScreen(accountType: accountType)
                    .environment(router)
                    .environment(menuState)

            case let .instanceScreen(host, _):
                InstanceScreen(host: host)
                    .environment(router)
                    .environment(menuState)

            case let .podcastSheet(accountType, podcastId):
                PodcastSheetView(accountType: accountType, podcastId: podcastId)
                    .environment(router)
                    .environment(menuState)
                    .environment(\.appSettings, appSettings)

            case let .spaces(accountType):
                SpaceScreen(accountType: accountType)
                    .environment(router)
                    .environment(menuState)

            default:
                Text("page not found for destination: \(String(describing: destination))")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .environment(router)
        .environment(menuState)
        .background(theme.primaryBackgroundColor)
        .foregroundColor(theme.labelColor)
    }
}

struct AddReactionView: View {
    let accountType: AccountType
    let statusKey: MicroBlogKey

    @Environment(FlareRouter.self) private var router

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

            Text("authorize success")
                .font(.headline)

            Text("You can now close this window")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            Button(action: {
                dismiss()
            }) {
                Text("Ok")
                    .frame(width: 120)
                    .padding()
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }
            .padding(.top)

            Text("Powered by Flare")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        //  .background(Color(UIColor.systemBackground))
    }
}

struct DeleteStatusView: View {
    let accountType: AccountType
    let statusKey: MicroBlogKey
    @State private var presenter: DeleteStatusPresenter
    @State private var showConfirmation = true

    @Environment(FlareRouter.self) private var router

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
                        Text("Delete")
                            .font(.headline)
                            .padding(.top)

                        Text("Are you sure you want to delete ?")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)

                        Text("This action cannot be undone.")
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
                        Text("delete success")
                            .font(.headline)
                            .padding(.top)

                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 50))
                            .foregroundColor(.green)
                            .padding()

                        Text("deleted successfully")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)

                        Button(action: {
                            // 关闭对话框
                            router.dismissAll()
                        }) {
                            Text("return")
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
            // .background(Color(UIColor.systemBackground))
            .cornerRadius(16)
            .shadow(radius: 10)
        }
    }
}
