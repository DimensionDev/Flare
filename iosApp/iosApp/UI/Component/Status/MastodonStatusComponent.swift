import SwiftUI
import MarkdownUI
import shared

struct MastodonStatusComponent: View {
    @Environment(\.openURL) private var openURL
    let content: String
    let avatar: String
    let name: String
    let handle: String
    let userKey: MicroBlogKey
    var body: some View {
        VStack(alignment:.leading) {
            HStack {
                Button(
                    action: {
                        openURL.callAsFunction(URL(string: AppDeepLink.Profile.shared.invoke(userKey: userKey))!)
                    }
                ) {
                    AsyncImage(url: URL(string: avatar)){ image in
                        image.image?.resizable().scaledToFit()
                    }
                        .frame(width: 48, height: 48)
                        .clipShape(Circle())
                }
                VStack(alignment: .leading) {
                    Markdown(name)
                        .font(.headline)
                        .markdownInlineImageProvider(.emoji)
                    Text(handle)
                        .font(.subheadline)
                        .foregroundColor(.gray)
                }
            }
            Markdown(content)
                .font(.body)
                .markdownInlineImageProvider(.emoji)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    MastodonStatusComponent(content: "haha", avatar: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg", name: "hahaname", handle: "haha.haha", userKey: MicroBlogKey(id: "", host: ""))
}
