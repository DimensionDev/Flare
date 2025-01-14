import MarkdownUI
import NetworkImage
import shared
import SwiftUI

struct UserComponent: View {
    let user: UiUserV2
    let topEndContent: UiTimelineItemContentStatusTopEndContent?
    let onUserClicked: () -> Void

    var body: some View {
        HStack {
            Button(
                action: {
                    onUserClicked()
//                    openURL(URL(string: AppDeepLink.Profile.shared.invoke(userKey: user.userKey))!)
                },
                label: {
                    UserAvatar(data: user.avatar, size: 44)
                }
            )
            .buttonStyle(.borderless)
            VStack(alignment: .leading) {
                Markdown(user.name.markdown)
                    .lineLimit(1)
                    .font(.headline)
                    .markdownInlineImageProvider(.emoji)
                HStack{
                    Text(user.handle)
                        .lineLimit(1)
                        .font(.subheadline)
                        .foregroundColor(.gray)
                    // 设置 pawoo 用户的可见状态
                    if topEndContent != nil {
                        if let topEndContent =  topEndContent {
                            switch onEnum(of: topEndContent) {
                            case let .visibility(data): StatusVisibilityComponent(visibility: data.visibility).foregroundColor(.gray)
                            }
                        }
                    }
                }
            }
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
