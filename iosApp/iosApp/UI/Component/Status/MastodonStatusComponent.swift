import SwiftUI
import MarkdownUI
import shared

struct MastodonStatusComponent: View {
    let content: String
    let avatar: String
    let name: String
    let handle: String
    var body: some View {
        VStack(alignment:.leading) {
            HStack {
                AsyncImage(url: URL(string: avatar)){ image in
                    image.image?.resizable().scaledToFit()
                }
                    .frame(width: 48, height: 48)
                    .clipShape(Circle())
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
    MastodonStatusComponent(content: "haha", avatar: "hah", name: "hahaname", handle: "haha.haha")
}
