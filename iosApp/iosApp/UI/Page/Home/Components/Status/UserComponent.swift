import MarkdownUI
import NetworkImage
import shared
import SwiftUI

struct UserComponent: View {
    let user: UiUserV2
    let topEndContent: UiTimelineItemContentStatusTopEndContent?

    @EnvironmentObject private var router: FlareRouter

    var body: some View {
        Button(
            action: {
                router.navigate(to: .profile(
                    accountType: UserManager.shared.getCurrentAccount() ?? AccountTypeGuest(),
                    userKey: user.key
                ))
            },
            label: {
                HStack {
                    UserAvatar(data: user.avatar, size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        if user.name.markdown.isEmpty {
                            Text(" ")
                                .lineLimit(1)
                                .font(.headline)
                        } else {
                            Markdown(user.name.markdown)
                                .lineLimit(1)
                                .font(.headline)
                                .markdownInlineImageProvider(.emoji)
                        }
                        HStack {
                            Text(user.handle)
                                .lineLimit(1)
                                .font(.subheadline)
                                .foregroundColor(.gray)
                            // 设置 pawoo 用户的可见状态 就是 后面的图标
                            if  topEndContent != nil {
                                if let topEndContent {
                                    switch onEnum(of: topEndContent) {
                                    case let .visibility(data):
                                        StatusVisibilityComponent(visibility: data.visibility)
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
        )
        .buttonStyle(.plain)
    }
}
