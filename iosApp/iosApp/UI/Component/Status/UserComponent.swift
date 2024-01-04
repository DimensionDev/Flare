import SwiftUI
import shared
import NetworkImage
import MarkdownUI

struct UserComponent: View {
    @Environment(\.openURL) private var openURL
    let user: UiUser
    var body: some View {
        HStack {
            Button(
                action: {
                    openURL(URL(string: AppDeepLink.Profile.shared.invoke(userKey: user.userKey))!)
                },
                label: {
                    NetworkImage(url: URL(string: user.avatarUrl)) { image in
                        image.resizable().scaledToFit()
                    }
                    .frame(width: 48, height: 48)
                    .clipShape(Circle())
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
                NetworkImage(url: URL(string: "")) { image in
                    image.resizable().scaledToFit()
                }
                .frame(width: 48, height: 48)
                .clipShape(Circle())
                VStack(alignment: .leading) {
                    Markdown("loading...")
                        .lineLimit(1)
                        .font(.headline)
                        .markdownInlineImageProvider(.emoji)
                    Text("loading...")
                        .lineLimit(1)
                        .font(.subheadline)
                        .foregroundColor(.gray)
                }
            }
            .redacted(reason: .placeholder)
        case .success(let success):
            let user = success.data
            HStack {
                NetworkImage(url: URL(string: user.avatarUrl)) { image in
                    image.resizable().scaledToFit()
                }
                .frame(width: 48, height: 48)
                .clipShape(Circle())
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
