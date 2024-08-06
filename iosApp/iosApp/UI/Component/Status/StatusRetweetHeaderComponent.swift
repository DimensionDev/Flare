import SwiftUI
import MarkdownUI
import shared

struct StatusRetweetHeaderComponent: View {
    let iconSystemName: String
    let nameMarkdown: String?
    let text: String
    var body: some View {
        HStack(alignment: .center) {
            Image(systemName: iconSystemName)
                .font(.system(size: 10))
                .frame(alignment: .center)
            Markdown {
                (nameMarkdown ?? "") + (nameMarkdown == nil ? "" : " ") + text
            }
            .frame(alignment: .center)
            .lineLimit(1)
            .markdownTextStyle(\.text) {
                FontSize(12)
            }
            .markdownInlineImageProvider(.emojiSmall)
            Spacer()
        }
        .foregroundColor(.primary)
        .opacity(0.6)
    }
}

#Preview {
    StatusRetweetHeaderComponent(
        iconSystemName: "house", nameMarkdown: "test", text: "test"
    )
}
