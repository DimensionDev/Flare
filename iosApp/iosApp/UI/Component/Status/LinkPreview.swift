import SwiftUI
import shared

struct LinkPreview: View {
    let card: UiCard
    var body: some View {
        Link(destination: URL(string: card.url)!) {
            HStack {
                if let media = card.media {
                    MediaItemComponent(media: media)
                        .frame(width: 64, height: 64)
                }
                VStack(alignment: .leading) {
                    Text(card.title)
                    if let desc = card.description_ {
                        Text(desc)
                            .font(.caption)
                            .foregroundStyle(.gray)
                    }
                }
                .foregroundStyle(.foreground)
                Spacer()
            }
        }
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.gray)
                .opacity(0.5)
        )
    }
}
