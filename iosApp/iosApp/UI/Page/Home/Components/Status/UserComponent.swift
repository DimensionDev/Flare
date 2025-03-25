import MarkdownUI
import NetworkImage
import shared
import SwiftUI

struct UserComponent: View {
    let user: UiUserV2
    let topEndContent: UiTimelineItemContentStatusTopEndContent?
    // let onUserClicked: () -> Void

    // 添加路由器环境对象，使用可选类型
    @EnvironmentObject private var router: FlareRouter

    var body: some View {
        HStack {
            Button(
                action: {
                    print("📱 头像按钮被点击: \(user.handle)")
                    // 使用声明式导航替换KMP回调
                    router.navigate(to: .profile(
                        accountType: UserManager.shared.getCurrentAccount() ?? AccountTypeGuest(),
                        userKey: user.key
                    ))
                },
                label: {
                    UserAvatar(data: user.avatar, size: 44)
                }
            )
            .buttonStyle(.borderless)
            VStack(alignment: .leading, spacing: 2) {
                Markdown(user.name.markdown)
                    .lineLimit(1)
                    .font(.headline)
                    .markdownInlineImageProvider(.emoji)
                HStack {
                    Text(user.handle)
                        .lineLimit(1)
                        .font(.subheadline)
                        .foregroundColor(.gray)
                    // 设置 pawoo 用户的可见状态
                    if topEndContent != nil {
                        if let topEndContent {
                            switch onEnum(of: topEndContent) {
                            case let .visibility(data): StatusVisibilityComponent(visibility: data.visibility)
                                .foregroundColor(.gray)
                                .font(.caption)
                            }
                        }
                    }
                }
            }
            .padding(.bottom, 2)
        }
    }
}

struct AccountItem: View {
    let userState: UiState<UiUserV2>
    var supportingContent: (UiUserV2) -> AnyView = { user in
        AnyView(
            Text(user.handle)
                .lineLimit(1)
                .font(.subheadline)
                .opacity(0.5)
        )
    }

    var body: some View {
        switch onEnum(of: userState) {
        case .error:
            EmptyView()
        case .loading:
            HStack {
                userAvatarPlaceholder(size: 48)
                VStack(alignment: .leading) {
                    Markdown("loading")
                        .lineLimit(1)
                        .font(.headline)
                        .markdownInlineImageProvider(.emoji)
                    Text("loading")
                        .lineLimit(1)
                        .font(.subheadline)
                        .foregroundColor(.gray)
                }
            }
            .redacted(reason: .placeholder)
        case let .success(success):
            let user = success.data
            HStack {
                UserAvatar(data: user.avatar, size: 48)
                VStack(alignment: .leading) {
                    Markdown(user.name.markdown)
                        .lineLimit(1)
                        .font(.headline)
                        .markdownInlineImageProvider(.emoji)
                    supportingContent(user)
                }
            }
        }
    }
}
