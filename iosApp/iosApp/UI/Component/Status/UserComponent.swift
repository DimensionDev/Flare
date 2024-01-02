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
