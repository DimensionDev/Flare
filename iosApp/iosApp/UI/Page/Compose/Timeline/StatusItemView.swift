import shared
import SwiftUI

struct StatusItemView: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme

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

    // 1. 如果未开启timeline隐藏 → 不隐藏
    // 2. 如果内容不敏感 → 不隐藏
    // 3. 如果有时间范围 → 只在时间范围内隐藏
    // 4. 如果没有时间范围但开启了隐藏 → 总是隐藏
    private var shouldHideInTimeline: Bool {
        let sensitiveSettings = appSettings.appearanceSettings.sensitiveContentSettings

        // 第一步：检查是否开启timeline隐藏功能
        guard sensitiveSettings.hideInTimeline else { return false }

        // 第二步：检查内容是否为敏感内容
        guard let content = data.content,
              case let .status(statusData) = onEnum(of: content),
              statusData.sensitive else { return false }

        // 第三步：根据时间范围设置决定是否隐藏
        if let timeRange = sensitiveSettings.timeRange {
            // 有时间范围：只在时间范围内隐藏
            return timeRange.isCurrentTimeInRange()
        } else {
            // 没有时间范围：总是隐藏敏感内容
            return true
        }
    }

    var body: some View {
        // 如果应该在 timeline 中隐藏敏感内容，返回空视图
        if shouldHideInTimeline {
            return AnyView(EmptyView())
        }

        return AnyView(
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
                    case let .status(data):
                        //  Button(action: {
                        //         if detailKey != data.statusKey {
                        //             // data.onClicked(.init(launcher: AppleUriLauncher(openURL: openURL)))
                        //             router.navigate(to: .statusDetailV2(
                        //                 accountType: UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest(),
                        //                 statusKey: data.statusKey
                        //             ))
                        //         }
                        //     }, label: {
                        TimelineStatusViewV2(
                            item: TimelineItem.from(self.data),
                            timelineViewModel: nil
//                                isDetail: detailKey == data.statusKey
                        )
                        .listStyle(.plain)
                        .listRowBackground(theme.primaryBackgroundColor)
                        .listRowInsets(EdgeInsets())
                        .listRowSeparator(.hidden)
                    // TimelineStatusView(
                    //     data: data,
                    //     onMediaClick: { index, _ in
                    //         // data.onMediaClicked(.init(launcher: AppleUriLauncher(openURL: openURL)), media, KotlinInt(integerLiteral: index))
                    //         router.navigate(to: .statusMedia(
                    //             accountType: UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest(),
                    //             statusKey: data.statusKey,
                    //             index: index
                    //         ))
                    //     },
                    //     isDetail: detailKey == data.statusKey,
                    //     enableTranslation: enableTranslation
                    // ).id("CommonTimelineStatusComponent_\(data.statusKey)")
                    // })
                    // .buttonStyle(.plain)
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
        )
    }
}
