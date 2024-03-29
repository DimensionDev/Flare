import SwiftUI
import shared
import NetworkImage
import MarkdownUI

struct UserComponent: View {
    let user: UiUser
    let onUserClicked: () -> Void
    var body: some View {
        HStack {
            Button(
                action: {
                    onUserClicked()
//                    openURL(URL(string: AppDeepLink.Profile.shared.invoke(userKey: user.userKey))!)
                },
                label: {
                    UserAvatar(data: user.avatarUrl, size: 48)
                }
            )
            .buttonStyle(.borderless)
            VStack(alignment: .leading) {
                Markdown(user.extra.nameMarkdown)
                    .lineLimit(1)
                    .font(.headline)
                    .markdownInlineImageProvider(.emoji)
                Text(user.handle)
                    .lineLimit(1)
                    .font(.subheadline)
                    .foregroundColor(.gray)
            }
        }
    }
}

struct AccountItem: View {
    let userState: UiState<UiUser>
    var supportingContent: (UiUser) -> AnyView = { user in
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
        case .success(let success):
            let user = success.data
            HStack {
                UserAvatar(data: user.avatarUrl, size: 48)
                VStack(alignment: .leading) {
                    Markdown(user.extra.nameMarkdown)
                        .lineLimit(1)
                        .font(.headline)
                        .markdownInlineImageProvider(.emoji)
                    supportingContent(user)
                }
            }
        }
    }
}
