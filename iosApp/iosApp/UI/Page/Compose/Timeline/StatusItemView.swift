import shared
import SwiftUI

struct StatusItemView: View {
    @Environment(\.openURL) private var openURL
    @EnvironmentObject private var router: FlareRouter

    let data: UiTimeline
    let detailKey: MicroBlogKey?
    let enableTranslation: Bool

    init(data: UiTimeline, detailKey: MicroBlogKey?, enableTranslation: Bool = true) {
        self.data = data
        self.detailKey = detailKey
        self.enableTranslation = enableTranslation
    }

    private var stableID: String {
        "StatusItem_\(data.itemKey)_\(detailKey?.description ?? "none")"
    }

    var body: some View {
        VStack(spacing: 0) {
            if let topMessage = data.topMessage {
                Button(action: {
                    if let user = topMessage.user {
                        router.navigate(to: .profile(
                            accountType: UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest(),
                            userKey: user.key
                        ))
                    }
                }, label: {
                    StatusRetweetHeaderComponent(topMessage: topMessage).id("StatusRetweetHeaderComponent_\(topMessage.statusKey)")
                })
                .buttonStyle(.plain)
            }

            if let content = data.content {
                switch onEnum(of: content) {
                case let .status(data): Button(action: {
                        if detailKey != data.statusKey {
                            // data.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                            router.navigate(to: .statusDetail(
                                accountType: UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest(),
                                statusKey: data.statusKey
                            ))
                        }
                    }, label: {
                        CommonTimelineStatusComponent(
                            data: data,
                            onMediaClick: { index, _ in
                                // data.onMediaClicked(.init(launcher: AppleUriLauncher(openURL: openURL)), media, KotlinInt(integerLiteral: index))
                                router.navigate(to: .statusMedia(
                                    accountType: UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest(),
                                    statusKey: data.statusKey,
                                    index: index
                                ))
                            },
                            isDetail: detailKey == data.statusKey,
                            enableTranslation: enableTranslation
                        ).id("CommonTimelineStatusComponent_\(data.statusKey)")
                    })
                    .buttonStyle(.plain)
                case let .user(data):
                    HStack {
                        UserComponent(
                            user: data.value,
                            topEndContent: nil
                        ).id("UserComponent_\(data.value.key)")
                        Spacer()
                    }
                case let .userList(data):
                    HStack {
                        ForEach(data.users, id: \.key) { user in
                            UserAvatar(data: user.avatar, size: 48).id("UserAvatar_\(user.key)")
                        }
                    }
                case .feed: EmptyView()
                }
            }
        }
        .id(stableID)
    }
}
